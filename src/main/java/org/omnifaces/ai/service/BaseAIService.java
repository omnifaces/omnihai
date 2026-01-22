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
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.ChatOptions;
import org.omnifaces.ai.ModerationOptions;
import org.omnifaces.ai.ModerationResult;
import org.omnifaces.ai.exception.AIApiResponseException;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.helper.TextHelper;
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

    private static final int DEFAULT_REASONING_TOKENS = 500;
    private static final int DEFAULT_DETECTION_TOKENS = 100;
    private static final int DEFAULT_WORDS_PER_KEYPOINT = 25;
    private static final int DEFAULT_WORDS_PER_MODERATE_CONTENT_CATEGORY = 10;
    private static final double DEFAULT_TEXT_ANALYSIS_TEMPERATURE = 0.5;
    private static final double DEFAULT_TRANSLATE_TEMPERATURE = 0.3;
    private static final double DEFAULT_DETECT_LANGUAGE_TEMPERATURE = 0.0;
    private static final double DEFAULT_MODERATE_CONTENT_TEMPERATURE = 0.1;

    /** The shared HTTP client for API requests. */
    protected static final AIApiClient API_CLIENT = AIApiClient.newInstance(DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);

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


    // Text Analysis Implementation (delegates to chat) ---------------------------------------------------------------

    @Override
    public CompletableFuture<String> summarizeAsync(String text, int maxWords) throws AIException {
        if (isBlank(text)) {
            throw new IllegalArgumentException("Text cannot be blank");
        }

        var options = ChatOptions.newBuilder()
            .systemPrompt(buildSummarizePrompt(maxWords))
            .temperature(DEFAULT_TEXT_ANALYSIS_TEMPERATURE)
            .maxTokens(estimateSummarizeMaxTokens(maxWords))
            .build();

        return chatAsync(text, options);
    }

    /**
     * Builds the system prompt for {@link #summarizeAsync(String, int)}.
     * You can override this method to customize the prompt.
     *
     * @param maxWords Maximum words in summary.
     * @return The system prompt.
     */
    protected String buildSummarizePrompt(int maxWords) {
        return """
            You are a professional summarizer.
            Summarize the provided text in at most %d words.
            Rules:
            - Provide one coherent summary.
            Output format:
            - Plain text summary only.
            - No explanations, no notes, no extra text, no markdown formatting.
        """.formatted(maxWords);
    }

    /**
     * Estimates the maximum number of tokens for {@link #summarizeAsync(String, int)}.
     * You can override this method to customize the estimation.
     *
     * @param maxWords Maximum words in summary.
     * @return Estimated maximum number of tokens.
     */
    protected int estimateSummarizeMaxTokens(int maxWords) {
        return DEFAULT_REASONING_TOKENS + (int) Math.ceil(maxWords * getEstimatedTokensPerWord());
    }

    @Override
    public CompletableFuture<List<String>> extractKeyPointsAsync(String text, int maxPoints) throws AIException {
        if (isBlank(text)) {
            throw new IllegalArgumentException("Text cannot be blank");
        }

        var options = ChatOptions.newBuilder()
            .systemPrompt(buildExtractKeyPointsPrompt(maxPoints))
            .temperature(DEFAULT_TEXT_ANALYSIS_TEMPERATURE)
            .maxTokens(estimateExtractKeyPointsMaxTokens(maxPoints))
            .build();

        return chatAsync(text, options).thenApply(response -> Arrays.asList(response.split("\n")).stream().map(line -> line.strip()).filter(not(TextHelper::isBlank)).toList());
    }

    /**
     * Builds the system prompt for {@link #extractKeyPointsAsync(String, int)}.
     * You can override this method to customize the prompt.
     *
     * @param maxPoints Maximum number of key points.
     * @return The system prompt.
     */
    protected String buildExtractKeyPointsPrompt(int maxPoints) {
        return """
            You are an expert at extracting key points.
            Extract the %d most important key points from the provided text.
            Each key point can have at most %d words.
            Output format:
            - One key point per line.
            - No numbering, no bullets, no dashes, no explanations, no notes, no extra text, no markdown formatting.
        """.formatted(maxPoints, DEFAULT_WORDS_PER_KEYPOINT);
    }

    /**
     * Estimates the maximum number of tokens for {@link #extractKeyPointsAsync(String, int)}.
     * You can override this method to customize the estimation.
     *
     * @param maxPoints Maximum number of key points.
     * @return Estimated maximum number of tokens.
     */
    protected int estimateExtractKeyPointsMaxTokens(int maxPoints) {
        return DEFAULT_REASONING_TOKENS + (int) Math.ceil(maxPoints * DEFAULT_WORDS_PER_KEYPOINT * getEstimatedTokensPerWord());
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
            .systemPrompt(buildTranslatePrompt(sourceLang, targetLang))
            .temperature(DEFAULT_TRANSLATE_TEMPERATURE)
            .maxTokens(estimateTranslateMaxTokens(text))
            .build();

        return chatAsync(text, options);
    }

    /**
     * Builds the system prompt for {@link #translateAsync(String, String, String)}.
     * You can override this method to customize the prompt.
     *
     * @param sourceLang Source language ISO 639-1 code, or {@code null} for auto-detection.
     * @param targetLang Target language ISO 639-1 code.
     * @return The system prompt.
     */
    protected String buildTranslatePrompt(String sourceLang, String targetLang) {
        var sourcePrompt = sourceLang == null
                ? "Detect the source language automatically."
                : "Translate from ISO 639-1 code '%s'".formatted(sourceLang.toLowerCase());
        return """
            You are a professional translator.
            %s
            Translate to ISO 639-1 code '%s'.
            Rules for every input:
            - Preserve ALL placeholders (#{...}, ${...}, {{...}}, etc) EXACTLY as-is.
            Rules if the input is parseable as HTML/XML:
            - Preserve ALL <script> tags (<script>...</script>) EXACTLY as-is.
            - Preserve ALL HTML/XML attribute values (style="...", class="...", id="...", data-*, etc.) EXACTLY as-is.
            Output format:
            - Only the translated input.
            - No explanations, no notes, no extra text, no markdown formatting.
            - Keep exact same line breaks, spacing and structure where possible.
        """.formatted(sourcePrompt, targetLang.toLowerCase());
    }

    /**
     * Estimates the maximum number of tokens for {@link #translateAsync(String, String, String)}.
     * You can override this method to customize the estimation.
     *
     * @param text The text to translate.
     * @return Estimated maximum number of tokens.
     */
    protected int estimateTranslateMaxTokens(String text) {
        return DEFAULT_REASONING_TOKENS + (int) Math.ceil(text.split("\\s+").length * getEstimatedTokensPerWord());
    }

    @Override
    public CompletableFuture<String> detectLanguageAsync(String text) throws AIException {
        if (isBlank(text)) {
            throw new IllegalArgumentException("Text cannot be blank");
        }

        var options = ChatOptions.newBuilder()
            .systemPrompt(buildDetectLanguagePrompt())
            .temperature(DEFAULT_DETECT_LANGUAGE_TEMPERATURE)
            .maxTokens(estimateDetectLanguageMaxTokens())
            .build();

        return chatAsync(text, options).thenApply(response -> {
            if (isBlank(response)) {
                throw new AIApiResponseException("Response is empty", response);
            }

            return response.strip().toLowerCase().replaceAll("[^a-z]", "");
        });
    }

    /**
     * Builds the system prompt for {@link #detectLanguageAsync(String)}.
     * You can override this method to customize the prompt.
     *
     * @return The system prompt.
     */
    protected String buildDetectLanguagePrompt() {
        return """
            You are a language detection expert.
            Determine the language of the provided text.
            Output format:
            - Only the ISO 639-1 two-letter code of the main language (e.g. en, fr, es, zh).
            - No explanations, no notes, no extra text, no markdown formatting.
        """;
    }

    /**
     * Estimates the maximum number of tokens for {@link #detectLanguageAsync(String)}.
     * You can override this method to customize the estimation.
     *
     * @return Estimated maximum number of tokens.
     */
    protected int estimateDetectLanguageMaxTokens() {
        return DEFAULT_DETECTION_TOKENS + (int) Math.ceil(2 * getEstimatedTokensPerWord());
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
            .systemPrompt(buildModerateContentPrompt(options))
            .temperature(DEFAULT_MODERATE_CONTENT_TEMPERATURE)
            .maxTokens(estimateModerateContentMaxTokens(options))
            .build();

        return chatAsync(content, chatOptions).thenApply(response -> parseModerationResult(response, options));
    }

    /**
     * Builds the system prompt for {@link #moderateContentAsync(String, ModerationOptions)}.
     * You can override this method to customize the prompt.
     *
     * @param options Moderation options containing categories and threshold.
     * @return The system prompt.
     */
    protected String buildModerateContentPrompt(ModerationOptions options) {
        var scores = Json.createObjectBuilder();
        options.getCategories().forEach(category -> scores.add(category, 0.0));
        var scoresTemplateString = Json.createObjectBuilder().add("scores", scores).build().toString();
        return """
            You are a strict content moderation model whose only task is to evaluate safety violations.
            Categories to evaluate:
            %s
            Rules:
            - 0.0: no violation at all
            - 0.1 - 0.3: mild / subtle undertone / possible concern
            - 0.4 - 0.6: moderate / clear but not extreme
            - 0.7 - 0.9: strong violation
            - 1.0: extremely severe / blatant / dangerous content
            Think carefully step by step:
            1. Read the whole message
            2. For each category, decide whether it applies
            3. Assign a score using the scale above
            4. Be objective; do not over-react to fictional, humorous, historical, or artistic context unless it clearly promotes harm
            JSON template:
            %s
            Output format:
            - Return ONLY valid JSON using the JSON template with scores substituted.
            - No explanations, no notes, no extra text, no markdown formatting.
        """.formatted(String.join(", ", options.getCategories()), scoresTemplateString);
    }

    /**
     * Estimates the maximum number of tokens for {@link #moderateContentAsync(String, ModerationOptions)}.
     * You can override this method to customize the estimation.
     *
     * @param options Moderation options containing categories and threshold.
     * @return Estimated maximum number of tokens.
     */
    protected int estimateModerateContentMaxTokens(ModerationOptions options) {
        return DEFAULT_REASONING_TOKENS + (int) Math.ceil(options.getCategories().size() * DEFAULT_WORDS_PER_MODERATE_CONTENT_CATEGORY * getEstimatedTokensPerWord());
    }

    /**
     * Parses the moderation result from JSON response.
     * You can override this method to customize the parsing.
     *
     * @param responseBody The JSON response from the AI model containing moderation scores.
     * @param options The moderation options containing categories and threshold.
     * @return The parsed moderation result.
     * @throws AIApiResponseException If the response cannot be parsed as JSON.
     */
    protected ModerationResult parseModerationResult(String responseBody, ModerationOptions options) throws AIApiResponseException {
        var responseJson = parseJson(responseBody);
        var scores = new TreeMap<String, Double>();
        var flagged = false;

        if (responseJson.containsKey("scores")) {
            var categoryScores = responseJson.getJsonObject("scores");
            for (String category : options.getCategories()) {
                if (categoryScores.containsKey(category)) {
                    double score = categoryScores.getJsonNumber(category).doubleValue();
                    scores.put(category, score);
                    if (score > options.getThreshold()) {
                        flagged = true;
                    }
                }
            }
        }

        return new ModerationResult(flagged, scores);
    }


    // Image Analysis Implementation (delegates to analyzeImage) ------------------------------------------------------

    /**
     * Builds the system prompt for {@link #analyzeImageAsync(byte[], String)}.
     * You can override this method to customize the prompt.
     *
     * @param prompt User-provided prompt, or {@code null} for default.
     * @return The system prompt.
     */
    protected String buildAnalyzeImagePrompt(String prompt) {
        return isBlank(prompt) ? """
            You are an expert at analyzing images.
            Describe this image in detail.
            Rules:
            - Focus on: main subject, key actions/details, visual style if relevant, and intended purpose.
            Output format:
            - Plain text description only.
            - No explanations, no notes, no extra text, no markdown formatting.
        """ : prompt;
    }

    @Override
    public CompletableFuture<String> generateAltTextAsync(byte[] image) throws AIException {
        return analyzeImageAsync(image, buildGenerateAltTextPrompt());
    }

    /**
     * Builds the system prompt for {@link #generateAltTextAsync(byte[])}.
     * You can override this method to customize the prompt.
     *
     * @return The system prompt.
     */
    protected String buildGenerateAltTextPrompt() {
        return """
            You are an expert at writing web alt text.
            Write concise, descriptive alt text for the image in at most 2 sentences.
            Each sentence can have at most %d words.
            Rules:
            - Focus on: main subject, key actions/details, visual style if relevant, and intended purpose.
            - Do not include phrases like "image of" unless necessary.
            Output format:
            - Plain text description only.
            - No explanations, no notes, no extra text, no markdown formatting.
        """.formatted(DEFAULT_WORDS_PER_KEYPOINT);
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
    protected abstract List<String> getResponseImageContentPaths();
}
