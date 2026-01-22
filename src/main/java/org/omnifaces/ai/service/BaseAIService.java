/*
 * Copyright OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.ai.service;

import static java.util.Collections.emptyMap;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Predicate.not;
import static org.omnifaces.ai.AIConfig.PROPERTY_API_KEY;
import static org.omnifaces.ai.AIConfig.PROPERTY_ENDPOINT;
import static org.omnifaces.ai.AIConfig.PROPERTY_MODEL;
import static org.omnifaces.ai.helper.JsonHelper.extractByPath;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;
import static org.omnifaces.ai.helper.TextHelper.isBlank;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

import jakarta.json.JsonObject;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIApiResponseException;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.helper.TextHelper;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.GenerateImageOptions;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.ModerationResult;
import org.omnifaces.ai.model.Sse.Event;
import org.omnifaces.ai.service.modality.DefaultImageAnalyzer;
import org.omnifaces.ai.service.modality.DefaultTextAnalyzer;
import org.omnifaces.ai.service.modality.ImageAnalyzer;
import org.omnifaces.ai.service.modality.TextAnalyzer;

/**
 * Base class for AI service implementations providing common API functionality.
 *
 * @see AIConfig
 * @see AIService
 * @author Bauke Scholtz
 * @since 1.0
 */
public abstract class BaseAIService implements AIService {

    private static final long serialVersionUID = 1L;

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    /** The shared HTTP client for API requests. */
    static final AIApiClient API_CLIENT = AIApiClient.newInstance(DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);

    /** The AI provider for this service. */
    protected final AIProvider provider;
    /** The API key for authentication. */
    protected final String apiKey;
    /** The AI model name. */
    protected final String model;
    /** The API endpoint URI. */
    protected final URI endpoint;
    /** The AI chat prompt. */
    protected final String prompt;

    /** The text analyzer. */
    protected final TextAnalyzer textAnalyzer;
    /** The image analyzer. */
    protected final ImageAnalyzer imageAnalyzer;

    /**
     * Constructs an AI service with the specified configuration.
     *
     * @param config The AI configuration containing provider, API key, model, and endpoint settings.
     * @throws IllegalArgumentException if the provider in the config doesn't match this service class.
     * @throws IllegalStateException If a required configuration property is missing.
     */
    protected BaseAIService(AIConfig config) {
        this.provider = config.resolveProvider();

        if (provider.getServiceClass() != null && !provider.getServiceClass().isInstance(this)) {
            throw new IllegalArgumentException("Wrong AI provider for this service class");
        }

        this.apiKey = provider.isApiKeyRequired() ? config.require(PROPERTY_API_KEY) : config.apiKey();
        this.model = ofNullable(config.model()).or(() -> ofNullable(provider.getDefaultModel())).orElseGet(() -> config.require(PROPERTY_MODEL));
        this.endpoint = URI.create(ensureTrailingSlash(ofNullable(config.endpoint()).or(() -> ofNullable(provider.getDefaultEndpoint())).orElseGet(() -> config.require(PROPERTY_ENDPOINT))));
        this.prompt = config.prompt();

        this.textAnalyzer = new DefaultTextAnalyzer();
        this.imageAnalyzer = new DefaultImageAnalyzer();
    }

    @Override
    public String getProviderName() {
        return provider.getName();
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public String getChatPrompt() {
        return prompt;
    }


    // Chat Implementation --------------------------------------------------------------------------------------------

    /**
     * Returns the path of the chat endpoint. E.g. {@code chat/completions} or {@code responses}.
     * @return the path of the chat endpoint.
     */
    protected abstract String getChatPath();

    /**
     * Returns whether this AI service implementation supports chat streaming.
     * The default implementation returns false.
     * @return Whether this AI service implementation supports chat streaming.
     */
    protected boolean supportsStreaming() {
        return false;
    }

    /**
     * Builds the JSON request payload for all chat operations.
     *
     * @param message The user message.
     * @param options The chat options.
     * @param streaming Whether to stream the chat response.
     * @return The JSON request payload.
     */
    protected abstract String buildChatPayload(String message, ChatOptions options, boolean streaming);

    @Override
    public CompletableFuture<String> chatAsync(String message, ChatOptions options) throws AIException {
        return asyncPostAndExtractMessageContent(getChatPath(), buildChatPayload(message, options, false).toString());
    }

    @Override
    public CompletableFuture<Void> chatStream(String message, ChatOptions options, Consumer<String> onToken) {
        if (!supportsStreaming()) {
            throw new UnsupportedOperationException("supportsStreaming() returned false, so ...");
        }

        return asyncPostAndProcessStreamEvents(getChatPath(), buildChatPayload(message, options, true), event -> processStreamEvent(event, onToken));
    }

    /**
     * Processes each stream event for {@link #chatStream(String, ChatOptions, Consumer)}.
     *
     * @param event Stream event.
     * @param onToken Callback receiving each stream data chunk (often one word/token/line).
     * @return {@code true} to continue processing the stream, or {@code false} when end of stream is reached.
     */
    protected boolean processStreamEvent(Event event, Consumer<String> onToken) {
        throw new UnsupportedOperationException("Please implement processStreamEvent(Event event, Consumer<String> onToken) method in class " + getClass().getSimpleName());
    }


    // Text Analysis Implementation (delegates to chat) ---------------------------------------------------------------

    @Override
    public CompletableFuture<String> summarizeAsync(String text, int maxWords) throws AIException {
        if (isBlank(text)) {
            throw new IllegalArgumentException("Text cannot be blank");
        }

        var options = ChatOptions.newBuilder()
            .systemPrompt(textAnalyzer.buildSummarizePrompt(maxWords))
            .temperature(textAnalyzer.getDefaultCreativeTemperature())
            .maxTokens(textAnalyzer.estimateSummarizeMaxTokens(maxWords, getEstimatedTokensPerWord()))
            .build();

        return chatAsync(text, options);
    }

    @Override
    public CompletableFuture<List<String>> extractKeyPointsAsync(String text, int maxPoints) throws AIException {
        if (isBlank(text)) {
            throw new IllegalArgumentException("Text cannot be blank");
        }

        var options = ChatOptions.newBuilder()
            .systemPrompt(textAnalyzer.buildExtractKeyPointsPrompt(maxPoints))
            .temperature(textAnalyzer.getDefaultCreativeTemperature())
            .maxTokens(textAnalyzer.estimateExtractKeyPointsMaxTokens(maxPoints, getEstimatedTokensPerWord()))
            .build();

        return chatAsync(text, options).thenApply(response -> Arrays.asList(response.split("\n")).stream().map(String::strip).filter(not(TextHelper::isBlank)).toList());
    }


    // Text Translation Implementation (delegates to chat) ------------------------------------------------------------

    @Override
    public CompletableFuture<String> translateAsync(String text, String sourceLang, String targetLang) throws AIException {
        if (isBlank(text)) {
            throw new IllegalArgumentException("Text cannot be blank");
        }

        if (isBlank(targetLang)) {
            throw new IllegalArgumentException("Target language cannot be blank");
        }

        var options = ChatOptions.newBuilder()
            .systemPrompt(textAnalyzer.buildTranslatePrompt(sourceLang, targetLang))
            .temperature(0.1)
            .maxTokens(textAnalyzer.estimateTranslateMaxTokens(text, getEstimatedTokensPerWord()))
            .build();

        return chatAsync(text, options);
    }

    @Override
    public CompletableFuture<String> detectLanguageAsync(String text) throws AIException {
        if (isBlank(text)) {
            throw new IllegalArgumentException("Text cannot be blank");
        }

        var options = ChatOptions.newBuilder()
            .systemPrompt(textAnalyzer.buildDetectLanguagePrompt())
            .temperature(0.0)
            .maxTokens(textAnalyzer.estimateDetectLanguageMaxTokens(getEstimatedTokensPerWord()))
            .build();

        return chatAsync(text, options).thenApply(response -> {
            if (isBlank(response)) {
                throw new AIApiResponseException("Response is empty", response);
            }

            return response.strip().toLowerCase().replaceAll("[^a-z]", "");
        });
    }


    // Text Moderation Implementation (delegates to chat) -------------------------------------------------------------

    @Override
    public CompletableFuture<ModerationResult> moderateContentAsync(String content, ModerationOptions options) throws AIException {
        if (isBlank(content)) {
            throw new IllegalArgumentException("Content cannot be blank");
        }

        if (options.getCategories().isEmpty()) {
            return completedFuture(ModerationResult.SAFE);
        }

        var chatOptions = ChatOptions.newBuilder()
            .systemPrompt(textAnalyzer.buildModerateContentPrompt(options))
            .temperature(0.1)
            .maxTokens(textAnalyzer.estimateModerateContentMaxTokens(options, getEstimatedTokensPerWord()))
            .build();

        return chatAsync(content, chatOptions).thenApply(response -> textAnalyzer.parseModerationResult(response, options));
    }


    // Image Analysis Implementation (delegates to analyzeImage) ------------------------------------------------------

    /**
     * Builds the JSON request payload for all vision operations.
     * @param image The image bytes.
     * @param prompt The analysis prompt.
     * @return The JSON request payload.
     */
    protected abstract String buildVisionPayload(byte[] image, String prompt);

    @Override
    public CompletableFuture<String> analyzeImageAsync(byte[] image, String prompt) throws AIException {
        return asyncPostAndExtractMessageContent(getChatPath(), buildVisionPayload(image, isBlank(prompt) ? imageAnalyzer.buildAnalyzeImagePrompt() : prompt));
    }

    @Override
    public CompletableFuture<String> generateAltTextAsync(byte[] image) throws AIException {
        return analyzeImageAsync(image, imageAnalyzer.buildGenerateAltTextPrompt());
    }

    /**
     * Returns the path of the image generation endpoint. E.g. {@code images/generations}.
     * The default implementation delegates to {@link #getChatPath()}.
     * @return the path of the image generation endpoint.
     */
    protected String getGenerateImagePath() {
        return getChatPath();
    }

    /**
     * Builds the JSON request payload for all generate image operations.
     * @param prompt The image generation prompt.
     * @param options The image generation options.
     * @return The JSON request payload.
     */
    protected String buildGenerateImagePayload(String prompt, GenerateImageOptions options) {
        throw new UnsupportedOperationException("Please implement buildGenerateImagePayload(String prompt, GenerateImageOptions options) method in class " + getClass().getSimpleName());
    }

    @Override
    public CompletableFuture<byte[]> generateImageAsync(String prompt, GenerateImageOptions options) throws AIException {
        return asyncPostAndExtractImageContent(getGenerateImagePath(), buildGenerateImagePayload(prompt, options));
    }

    // HTTP Helper Methods --------------------------------------------------------------------------------------------

    private static String ensureTrailingSlash(String uri) {
        return uri.endsWith("/") ? uri : uri.concat("/");
    }

    /**
     * Returns additional request headers to use at {@link #asyncPostAndExtractMessageContent(String, String)}, e.g. authorization or version headers.
     * The default implementation returns an empty map.
     * @return Additional request headers to use at {@link #asyncPostAndExtractMessageContent(String, String)}.
     */
    protected Map<String, String> getRequestHeaders() {
        return emptyMap();
    }

    /**
     * Resolve URI of the given path based on endpoint URI.
     * @param path The path to resolve the URI for based on endpoint URI.
     * @return Resolved URI of the given path based on endpoint URI.
     */
    protected URI resolveURI(String path) {
        return endpoint.resolve(path);
    }

    /**
     * Send POST request to API at given path with given payload along with request headers obtained from {@link #getRequestHeaders()}, and extract
     * message content from the POST response with help of {@link #extractMessageContent(String)}.
     * @param path API path, relative to {@link #endpoint}.
     * @param payload POST request payload, usually a JSON object with instructions.
     * @return The message content of the POST request.
     * @throws AIException if anything fails during the process.
     */
    protected CompletableFuture<String> asyncPostAndExtractMessageContent(String path, String payload) throws AIException {
        return API_CLIENT.post(this, path, payload).thenApply(this::extractMessageContent);
    }

    /**
     * Send POST request to API at given path with given payload along with request headers obtained from {@link #getRequestHeaders()}, and extract
     * image content from the POST response with help of {@link #extractImageContent(String)}.
     * @param path API path, relative to {@link #endpoint}.
     * @param payload POST request payload, usually a JSON object with instructions.
     * @return The image content of the POST request.
     * @throws AIException if anything fails during the process.
     */
    protected CompletableFuture<byte[]> asyncPostAndExtractImageContent(String path, String payload) throws AIException {
        return API_CLIENT.post(this, path, payload).thenApply(this::extractImageContent);
    }

    /**
     * Send SSE request to API at given path with given payload along with request headers obtained from {@link #getRequestHeaders()}, and process
     * each reveived stream event using supplied {@code eventProcessor}.
     * @param path API path, relative to {@link #endpoint}.
     * @param payload Initial SSE POST request payload, usually a JSON object with instructions.
     * @param eventProcessor Callback invoked for each stream event; it must return {@code true} to continue processing the stream, or {@code false} to stop processing the stream.
     * @return A future that completes when stream ends normally, is stopped by the processor, or fails exceptionally.
     * @throws AIException if anything fails during the process.
     */
    protected CompletableFuture<Void> asyncPostAndProcessStreamEvents(String path, String payload, Predicate<Event> eventProcessor) throws AIException {
        return API_CLIENT.stream(this, path, payload, eventProcessor);
    }

    /**
     * Extracts message content from the API response body. Used by {@link #asyncPostAndExtractMessageContent(String, String)}.
     * <p>
     * The default implementation parses the response as JSON, checks for error objects at {@link #getResponseErrorMessagePaths()},
     * and extracts message content at {@link #getResponseMessageContentPaths()}.
     *
     * @param responseBody The API response body, usually a JSON object with the AI response, along with some meta data.
     * @return The extracted message content from the API response body.
     * @throws AIApiResponseException If the response cannot be parsed as JSON, contains an error object, or is missing expected message content.
     */
    protected String extractMessageContent(String responseBody) throws AIApiResponseException {
        var responseJson = parseResponseBodyAndCheckErrorMessages(responseBody);
        var messageContentPaths = getResponseMessageContentPaths();

        if (messageContentPaths.isEmpty()) {
            throw new IllegalStateException("getResponseMessageContentPaths() may not return an empty list");
        }

        for (var messageContentPath : messageContentPaths) {
            var messageContent = extractByPath(responseJson, messageContentPath);

            if (!isBlank(messageContent)) {
                return messageContent;
            }
        }

        throw new AIApiResponseException("No message content found at paths " + messageContentPaths, responseBody);
    }

    /**
     * Extracts image content from the API response body. Used by {@link #asyncPostAndExtractImageContent(String, String)}.
     * <p>
     * The default implementation parses the response as JSON, checks for error objects at {@link #getResponseErrorMessagePaths()},
     * and extracts image content at {@link #getResponseImageContentPaths()}.
     *
     * @param responseBody The API response body, usually a JSON object with the AI response, along with some meta data.
     * @return The extracted image content from the API response body.
     * @throws AIApiResponseException If the response cannot be parsed as JSON, contains an error object, or is missing expected image content.
     */
    protected byte[] extractImageContent(String responseBody) throws AIApiResponseException {
        var responseJson = parseResponseBodyAndCheckErrorMessages(responseBody);
        var imageContentPaths = getResponseImageContentPaths();

        if (imageContentPaths.isEmpty()) {
            throw new IllegalStateException("getResponseImageContentPaths() may not return an empty list");
        }

        for (var imageContentPath : imageContentPaths) {
            var imageContent = extractByPath(responseJson, imageContentPath);

            if (!isBlank(imageContent)) {
                try {
                    return Base64.getDecoder().decode(imageContent);
                }
                catch (Exception e) {
                    throw new AIApiResponseException("Cannot Base64-decode image", responseBody, e);
                }
            }
        }

        throw new AIApiResponseException("No image content found at paths " + imageContentPaths, responseBody);
    }

    private JsonObject parseResponseBodyAndCheckErrorMessages(String responseBody) throws AIApiResponseException {
        var responseJson = parseJson(responseBody);

        for (var errorMessagePath : getResponseErrorMessagePaths()) {
            var errorMessage = extractByPath(responseJson, errorMessagePath);

            if (!isBlank(errorMessage)) {
                throw new AIApiResponseException(errorMessage, responseBody);
            }
        }

        return responseJson;
    }

    /**
     * Returns all possible paths to the error message in the JSON response of {@link #asyncPostAndExtractMessageContent(String, String)} and
     * {@link #asyncPostAndExtractImageContent(String, String)}. May be empty.
     * Used by {@link #extractMessageContent(String)} and {@link #extractImageContent(String)}.
     * The first path that matches a value in the JSON response will be used; remaining paths are ignored.
     * The default implementation returns {@code "error.message"} and {@code "error"}.
     * @return all possible paths to the error message in the JSON response of {@link #asyncPostAndExtractMessageContent(String, String)} and
     * {@link #asyncPostAndExtractImageContent(String, String)}.
     */
    protected List<String> getResponseErrorMessagePaths() {
        return List.of("error.message", "error");
    }

    /**
     * Returns all possible paths to the message content in the JSON response of {@link #asyncPostAndExtractMessageContent(String, String)}.
     * May not be empty. Used by {@link #extractMessageContent(String)}.
     * The first path that matches a value in the JSON response will be used; remaining paths are ignored.
     * @return all possible paths to the message content in the JSON response of {@link #asyncPostAndExtractMessageContent(String, String)}.
     */
    protected abstract List<String> getResponseMessageContentPaths();

    /**
     * Returns all possible paths to the image content in the JSON response of {@link #asyncPostAndExtractImageContent(String, String)}.
     * May not be empty. Used by {@link #extractImageContent(String)}.
     * The first path that matches a value in the JSON response will be used; remaining paths are ignored.
     * @return all possible paths to the image content in the JSON response of {@link #asyncPostAndExtractImageContent(String, String)}.
     */
    protected List<String> getResponseImageContentPaths() {
        throw new UnsupportedOperationException("Please implement getResponseImageContentPaths() method in class " + getClass().getSimpleName());
    }
}
