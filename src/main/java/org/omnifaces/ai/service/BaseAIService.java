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
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Predicate.not;
import static org.omnifaces.ai.AIConfig.PROPERTY_API_KEY;
import static org.omnifaces.ai.AIConfig.PROPERTY_ENDPOINT;
import static org.omnifaces.ai.AIConfig.PROPERTY_MODEL;
import static org.omnifaces.ai.helper.TextHelper.isBlank;
import static org.omnifaces.ai.helper.TextHelper.requireNonBlank;
import static org.omnifaces.ai.model.ChatOptions.DETERMINISTIC;
import static org.omnifaces.ai.model.ChatOptions.DETERMINISTIC_TEMPERATURE;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Predicate;

import jakarta.json.JsonObject;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIImageHandler;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.AITextHandler;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.helper.TextHelper;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.GenerateImageOptions;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.ModerationResult;
import org.omnifaces.ai.model.Sse.Event;

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
    /** The AI text handler for this service. */
    protected final AITextHandler textHandler;
    /** The AI image handler for this service. */
    protected final AIImageHandler imageHandler;

    /**
     * Constructs an AI service with the specified configuration.
     *
     * @param config The AI configuration containing provider, API key, model, endpoint, prompt, and strategy settings.
     * @throws NullPointerException when config is null.
     * @throws IllegalArgumentException if the provider in the config doesn't match this service class, or if a handler class is unspecified.
     * @throws IllegalStateException If a required configuration property is missing, or if a handler class cannot be instantiated.
     */
    protected BaseAIService(AIConfig config) {
        this.provider = requireNonNull(config, "config").resolveProvider();

        if (provider.getServiceClass() != null && !provider.getServiceClass().isInstance(this)) {
            throw new IllegalArgumentException("Wrong AI provider for this service class");
        }

        this.apiKey = provider.isApiKeyRequired() ? config.require(PROPERTY_API_KEY) : config.apiKey();
        this.model = ofNullable(config.model()).or(() -> ofNullable(provider.getDefaultModel())).orElseGet(() -> config.require(PROPERTY_MODEL));
        this.endpoint = URI.create(ensureTrailingSlash(ofNullable(config.endpoint()).or(() -> ofNullable(provider.getDefaultEndpoint())).orElseGet(() -> config.require(PROPERTY_ENDPOINT))));
        this.prompt = config.prompt();
        this.textHandler = createHandler(config.strategy().textHandler(), provider.getDefaultTextHandler(), "text");
        this.imageHandler = createHandler(config.strategy().imageHandler(), provider.getDefaultImageHandler(), "image");
    }

    private static <T> T createHandler(Class<? extends T> configuredHandler, Class<? extends T> defaultHandler, String handlerName) {
        var handlerClass = configuredHandler == null ? defaultHandler : configuredHandler;

        if (handlerClass == null) {
            throw new IllegalArgumentException("No " + handlerName + " handler configured. Custom providers must supply handlers via AIConfig.withStrategy(AIStrategy).");
        }

        try {
            return handlerClass.getDeclaredConstructor().newInstance();
        }
        catch (Exception e) {
            throw new IllegalStateException("Cannot instantiate " + handlerName + " handler " + handlerClass.getName(), e);
        }
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
     * @param streaming Whether this is for chat streaming endpoint.
     * @return the path of the chat endpoint.
     */
    protected abstract String getChatPath(boolean streaming);

    @Override
    public CompletableFuture<String> chatAsync(ChatInput input, ChatOptions options) throws AIException {
        return asyncPostAndParseChatResponse(getChatPath(false), textHandler.buildChatPayload(this, input, options, false));
    }

    @Override
    public CompletableFuture<Void> chatStream(ChatInput input, ChatOptions options, Consumer<String> onToken) {
        if (!supportsStreaming()) {
            throw new UnsupportedOperationException("Streaming is not supported by " + getName());
        }

        var neededForStackTrace = new Exception("Async chat streaming failed");

        return asyncPostAndProcessStreamEvents(getChatPath(true), textHandler.buildChatPayload(this, input, options, true), event -> textHandler.processChatStreamEvent(this, event, onToken)).handle((result, exception) -> {
            if (exception == null) {
                return result;
            }

            throw AIException.asyncRequestFailed(exception, neededForStackTrace);
        });
    }


    // Files Implementation (for attaching files to chat) -------------------------------------------------------------

    /**
     * Returns the path of the files endpoint. E.g. {@code files}.
     * @return the path of the files endpoint.
     * @throws UnsupportedOperationException If file upload operation is not supported.
     */
    protected abstract String getFilesPath();

    @Override
    public String upload(Attachment attachment) throws AIException {
        try {
            return asyncUploadAndParseFileIdResponse(getFilesPath(), attachment).join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
    }


    // Text Analysis Implementation (delegates to chat) ---------------------------------------------------------------

    @Override
    public CompletableFuture<String> summarizeAsync(String text, int maxWords) throws AIException {
        var options = ChatOptions.newBuilder()
            .systemPrompt(textHandler.buildSummarizePrompt(maxWords))
            .temperature(textHandler.getDefaultCreativeTemperature())
            .build();

        return chatAsync(requireNonBlank(text, "text"), options);
    }

    @Override
    public CompletableFuture<List<String>> extractKeyPointsAsync(String text, int maxPoints) throws AIException {
        var options = ChatOptions.newBuilder()
            .systemPrompt(textHandler.buildExtractKeyPointsPrompt(maxPoints))
            .temperature(textHandler.getDefaultCreativeTemperature())
            .build();

        return chatAsync(requireNonBlank(text, "text"), options).thenApply(response -> Arrays.asList(response.split("\n")).stream().map(String::strip).filter(not(TextHelper::isBlank)).toList());
    }


    // Text Translation Implementation (delegates to chat) ------------------------------------------------------------

    @Override
    public CompletableFuture<String> detectLanguageAsync(String text) throws AIException {
        var options = ChatOptions.newBuilder()
            .systemPrompt(textHandler.buildDetectLanguagePrompt())
            .temperature(DETERMINISTIC_TEMPERATURE)
            .build();

        return chatAsync(requireNonBlank(text, "text"), options).thenApply(response -> {
            if (isBlank(response)) {
                throw new AIResponseException("Response is empty", response);
            }

            return response.strip().toLowerCase().replaceAll("[^a-z]", "");
        });
    }

    @Override
    public CompletableFuture<String> translateAsync(String text, String sourceLang, String targetLang) throws AIException {
        var options = ChatOptions.newBuilder()
            .systemPrompt(textHandler.buildTranslatePrompt(sourceLang, requireNonBlank(targetLang, "target language")))
            .temperature(DETERMINISTIC_TEMPERATURE)
            .build();

        return chatAsync(requireNonBlank(text, "text"), options);
    }


    // Text Proofreading Implementation (delegates to chat) -----------------------------------------------------------

    @Override
    public CompletableFuture<String> proofreadAsync(String text) throws AIException {
        var options = ChatOptions.newBuilder()
            .systemPrompt(textHandler.buildProofreadPrompt())
            .temperature(DETERMINISTIC_TEMPERATURE)
            .build();

        return chatAsync(requireNonBlank(text, "text"), options);
    }


    // Text Moderation Implementation (delegates to chat) -------------------------------------------------------------

    @Override
    public CompletableFuture<ModerationResult> moderateContentAsync(String content, ModerationOptions options) throws AIException {
        if (options.getCategories().isEmpty()) {
            return completedFuture(ModerationResult.SAFE);
        }

        var chatOptions = ChatOptions.newBuilder()
            .systemPrompt(textHandler.buildModerationPrompt(options))
            .jsonSchema(textHandler.buildModerationJsonSchema(options))
            .temperature(DETERMINISTIC_TEMPERATURE)
            .build();

        return chatAsync(requireNonBlank(content, "content"), chatOptions).thenApply(response -> textHandler.parseModerationResult(response, options));
    }


    // Image Analysis Implementation (delegates to chat) --------------------------------------------------------------

    @Override
    public CompletableFuture<String> analyzeImageAsync(byte[] image, String prompt) throws AIException {
        var input = ChatInput.newBuilder().message(isBlank(prompt) ? imageHandler.buildAnalyzeImagePrompt() : prompt).attach(image).build();
        return asyncPostAndParseChatResponse(getChatPath(false), textHandler.buildChatPayload(this, input, DETERMINISTIC, false));
    }

    @Override
    public CompletableFuture<String> generateAltTextAsync(byte[] image) throws AIException {
        return analyzeImageAsync(image, imageHandler.buildGenerateAltTextPrompt());
    }


    // Image Generator Implementation ---------------------------------------------------------------------------------

    /**
     * Returns the path of the image generation endpoint. E.g. {@code images/generations}.
     * The default implementation delegates to {@link #getChatPath(boolean)}
     * @return the path of the image generation endpoint.
     */
    protected String getGenerateImagePath() {
        return getChatPath(false);
    }

    @Override
    public CompletableFuture<byte[]> generateImageAsync(String prompt, GenerateImageOptions options) throws AIException {
        return asyncPostAndParseImageContent(getGenerateImagePath(), imageHandler.buildGenerateImagePayload(this, requireNonBlank(prompt, "prompt"), options));
    }

    // HTTP Helper Methods --------------------------------------------------------------------------------------------

    private static String ensureTrailingSlash(String uri) {
        return uri.endsWith("/") ? uri : uri.concat("/");
    }

    /**
     * Returns additional request headers to use at {@link #asyncPostAndParseChatResponse(String, JsonObject)}, e.g. authorization or version headers.
     * These headers are added on top of the default request headers: {@code User-Agent}, {@code Content-Type} and {@code Accept}.
     * The default implementation returns an empty map.
     * @return Additional request headers to use at {@link #asyncPostAndParseChatResponse(String, JsonObject)}.
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
     * Send POST request to API at given path with given payload along with request headers obtained from {@link #getRequestHeaders()}, and parse
     * chat response from the POST response with help of {@link AITextHandler#parseChatResponse(String)}.
     * @param path API path, relative to {@link #endpoint}.
     * @param payload POST request payload.
     * @return The message content of the POST request.
     * @throws AIException if anything fails during the process.
     */
    protected CompletableFuture<String> asyncPostAndParseChatResponse(String path, JsonObject payload) throws AIException {
        return API_CLIENT.post(this, path, payload).thenApply(textHandler::parseChatResponse);
    }

    /**
     * Upload file attachment to API at given path along with request headers obtained from {@link #getRequestHeaders()},
     * and parse file ID from the response with help of {@link AITextHandler#parseFileResponse(String)}.
     *
     * @param path API path, relative to {@link #endpoint}.
     * @param attachment The file attachment to upload.
     * @return A CompletableFuture containing the file ID from the upload response.
     * @throws AIException if anything fails during the process.
     */
    protected CompletableFuture<String> asyncUploadAndParseFileIdResponse(String path, Attachment attachment) throws AIException {
        return API_CLIENT.upload(this, path, attachment).thenApply(textHandler::parseFileResponse);
    }

    /**
     * Send POST request to API at given path with given payload along with request headers obtained from {@link #getRequestHeaders()}, and parse
     * image content from the POST response with help of {@link AIImageHandler#parseImageContent(String)}.
     * @param path API path, relative to {@link #endpoint}.
     * @param payload POST request payload.
     * @return The image content of the POST request.
     * @throws AIException if anything fails during the process.
     */
    protected CompletableFuture<byte[]> asyncPostAndParseImageContent(String path, JsonObject payload) throws AIException {
        return API_CLIENT.post(this, path, payload).thenApply(imageHandler::parseImageContent);
    }

    /**
     * Send SSE request to API at given path with given payload along with request headers obtained from {@link #getRequestHeaders()}, and process
     * each reveived stream event using supplied {@code eventProcessor}.
     * @param path API path, relative to {@link #endpoint}.
     * @param payload Initial SSE POST request payload.
     * @param eventProcessor Callback invoked for each stream event; it must return {@code true} to continue processing the stream, or {@code false} to stop processing the stream.
     * @return A future that completes when stream ends normally, is stopped by the processor, or fails exceptionally.
     * @throws AIException if anything fails during the process.
     */
    protected CompletableFuture<Void> asyncPostAndProcessStreamEvents(String path, JsonObject payload, Predicate<Event> eventProcessor) throws AIException {
        return API_CLIENT.stream(this, path, payload, eventProcessor);
    }
}
