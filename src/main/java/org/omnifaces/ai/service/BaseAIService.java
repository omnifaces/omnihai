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

import java.io.StringReader;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.ChatOptions;
import org.omnifaces.ai.ModerationOptions;
import org.omnifaces.ai.ModerationResult;
import org.omnifaces.ai.exception.AIApiResponseException;
import org.omnifaces.ai.exception.AIException;

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
    private static final int DEFAULT_DETECTION_TOKENS = 50;
    private static final int DEFAULT_WORDS_PER_KEYPOINT = 25;
    private static final int DEFAULT_WORDS_PER_MODERATE_CONTENT_CATEGORY = 10;
    private static final double DEFAULT_TEXT_ANALYSIS_TEMPERATURE = 0.5;
    private static final double DEFAULT_TRANSLATE_TEMPERATURE = 0.3;
    private static final double DEFAULT_DETECT_LANGUAGE_TEMPERATURE = 0.0;
    private static final double DEFAULT_MODERATE_CONTENT_TEMPERATURE = 0.1;

    protected static final AIApiClient API_CLIENT = AIApiClient.newInstance(DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);

    protected final AIProvider provider;
    protected final String apiKey;
    protected final String model;
    protected final URI endpoint;
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
        this.model = ofNullable(config.model()).orElseGet(provider::getDefaultModel);
        this.endpoint = URI.create(ensureTrailingSlash(ofNullable(config.endpoint()).orElseGet(provider::getDefaultEndpoint)));
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

        return chatAsync(text, options).thenApply(response -> Arrays.asList(response.split("\n")).stream().map(line -> line.strip()).filter(not(BaseAIService::isBlank)).toList());
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
        var prompt = new StringBuilder("You are a professional translator.\n");

        if (sourceLang == null) {
            prompt.append("Detect the source language automatically.\n");
            prompt.append("Translate ");
        }
        else {
            prompt.append("Translate from ISO 639-1 code '").append(sourceLang.toLowerCase()).append("' ");
        }

        prompt.append("to ISO 639-1 code '").append(targetLang.toLowerCase()).append("'.\n");
        prompt.append("""
            Rules:
            - Preserve ALL markup (XML/HTML tags, JSON keys/values) and placeholders (#{...}, ${...}, {{...}}) exactly.
            - Keep original line breaks and overall layout as close as possible.
            Output format:
            - Only the translated text.
            - No explanations, no notes, no extra text, no markdown formatting.
        """);

        return prompt.toString();
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
                throw new AIApiResponseException("Response is empty");
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
        return """
            You are a content moderation system.
            Analyze the provided content.
            Categories: %s.
            Rules:
            - Score each category from 0.0 (none) to 1.0 (very strong).
            - Collect the scores in a JSON format like this example: `{"scores": {"hate": 0.05, "violence": 0.12, ...}}`
            Output format:
            - Only the scores in JSON format.
            - No explanations, no notes, no extra text, no markdown formatting.
        """.formatted(String.join(", " + options.getCategories()));
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
        var scores = new HashMap<String, Double>();
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
            Write concise, descriptive alt text for the image (1-2 sentences, 10-30 words).
            Rules:
            - Focus on: main subject, key actions/details, visual style if relevant, and intended purpose.
            - Do not include phrases like "image of" unless necessary.
            Output format:
            - Plain text description only.
            - No explanations, no notes, no extra text, no markdown formatting.
        """;
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
     * Send POST request to API at given path with given payload via {@link AIApiClient#post(BaseAIService, String, String)} along with request headers obtained
     * from {@link #getRequestHeaders()}, and extract message content from the POST response with help of {@link #extractMessageContent(String)}.
     * @param path API path, relative to {@link #endpoint}.
     * @param payload POST request payload, usually a JSON object with instructions.
     * @return The message content of the POST request.
     * @throws AIException if anything fails during the process.
     */
    protected CompletableFuture<String> asyncPostAndExtractMessageContent(String path, String payload) throws AIException {
        return API_CLIENT.post(this, path, payload).thenApply(this::extractMessageContent);
    }

    /**
     * Send POST request to API at given path with given payload via {@link AIApiClient#post(BaseAIService, String, String)} along with request headers obtained
     * from {@link #getRequestHeaders()}, and extract image content from the POST response with help of {@link #extractImageContent(String)}.
     * @param path API path, relative to {@link #endpoint}.
     * @param payload POST request payload, usually a JSON object with instructions.
     * @return The image content of the POST request.
     * @throws AIException if anything fails during the process.
     */
    protected CompletableFuture<byte[]> asyncPostAndExtractImageContent(String path, String payload) throws AIException {
        return API_CLIENT.post(this, path, payload).thenApply(this::extractImageContent);
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

        throw new AIApiResponseException("No message content found at paths " + messageContentPaths);
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
                    throw new AIApiResponseException("Cannot Base64-decode image:" + imageContent, e);
                }
            }
        }

        throw new AIApiResponseException("No image content found at paths " + imageContentPaths);
    }

    private JsonObject parseResponseBodyAndCheckErrorMessages(String responseBody) throws AIApiResponseException {
        var responseJson = parseJson(responseBody);

        for (var errorMessagePath : getResponseErrorMessagePaths()) {
            var errorMessage = extractByPath(responseJson, errorMessagePath);

            if (!isBlank(errorMessage)) {
                throw new AIApiResponseException(errorMessage);
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


    // Text/JSON Helper Methods ---------------------------------------------------------------------------------------

    /**
     * Returns whether the given string is null or blank.
     * @param string String to check.
     * @return Whether the given string is null or blank.
     */
    protected static boolean isBlank(String string) {
        return string == null || string.isBlank();
    }

    /**
     * Returns whether the given JSON value is empty. Supports currently only {@link JsonObject}, {@link JsonArray} and {@link JsonString}.
     * @param value JSON value to check.
     * @return Whether the given JSON value is empty.
     */
    protected static boolean isEmpty(JsonValue value) {
        if (value == null) {
            return true;
        }
        else if (value instanceof JsonObject object) {
            return object.isEmpty();
        }
        else if (value instanceof JsonArray array) {
            return array.isEmpty();
        }
        else if (value instanceof JsonString string) {
            return isBlank(string.getString());
        }
        else {
            throw new UnsupportedOperationException("Not implemented yet, just add a new else-if block here");
        }
    }

    /**
     * Parses given string to a {@link JsonObject}.
     *
     * @param json The JSON string to parse.
     * @return The parsed JSON object.
     * @throws AIApiResponseException If the string cannot be parsed as JSON.
     */
    protected static JsonObject parseJson(String json) throws AIApiResponseException {
        var sanitizedJson = json.substring(json.indexOf('{'), json.lastIndexOf('}') + 1); // Some APIs stubbornly put JSON in markdown formatting like ```json\n{...}\n```.

        try (var reader = Json.createReader(new StringReader(sanitizedJson))) {
            return reader.readObject();
        }
        catch (Exception e) {
            throw new AIApiResponseException("Cannot parse json " + json, e);
        }
    }

    /**
     * Extracts a string value from a JSON object using a dot-separated path.
     * <p>
     * Supports array indexing with bracket notation, e.g. {@code "choices[0].message.content"}.
     *
     * @param root The JSON object to extract from.
     * @param path The dot-separated path to the value, with optional array indices in brackets.
     * @return The stripped string value at the path, or {@code null} if the path doesn't exist, is null, or is empty.
     */
    protected static String extractByPath(JsonObject root, String path) {
        JsonValue current = root;

        for (var segment : path.split("\\.")) {
            if (!(current instanceof JsonObject || current instanceof JsonArray)) {
                return null;
            }

            var startBracket = segment.indexOf('[');

            if (startBracket >= 0) {
                var key = segment.substring(0, startBracket);
                var endBracket = segment.indexOf(']', startBracket);
                var index = Integer.parseInt(segment.substring(startBracket + 1, endBracket));
                var jsonObject = current.asJsonObject();

                if (!jsonObject.containsKey(key) || jsonObject.isNull(key)) {
                    return null;
                }

                var array = jsonObject.getJsonArray(key);

                if (array == null || index < 0 || index >= array.size()) {
                    return null;
                }

                current = array.get(index);
            }
            else {
                var jsonObject = current.asJsonObject();

                if (!jsonObject.containsKey(segment) || jsonObject.isNull(segment)) {
                    return null;
                }

                current = jsonObject.get(segment);
            }
        }

        if (current == null) {
            return null;
        }

        var string = current instanceof JsonString jsonString ? jsonString.getString() : current.toString();
        return isBlank(string) ? null : string.strip();
    }


    // Image Helper Methods (copied from OmniFaces GraphicResource) ---------------------------------------------------

    private static final Map<String, String> MIME_TYPES_BY_BASE64_HEADER = Map.of(
        "UklGR", "image/webp",
        "/9j/", "image/jpeg",
        "iVBORw", "image/png",
        "R0lGOD", "image/gif",
        "AAABAA", "image/x-icon",
        "PD94bW", "image/svg+xml",
        "Qk0", "image/bmp",
        "SUkqAA", "image/tiff",
        "TU0AKg", "image/tiff"
    );

    private static final String DEFAULT_MIME_TYPE = "image";

    /**
     * Encodes the given bytes to a Base64 string.
     *
     * @param bytes The bytes to encode.
     * @return The Base64 encoded string.
     */
    protected static String encodeBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Guesses the MIME type of an image based on its Base64 encoded header.
     *
     * @param base64 The Base64 encoded image string.
     * @return The guessed MIME type, or {@value DEFAULT_MIME_TYPE} if unknown.
     */
    protected static String guessMimeType(String base64) {
        for (var contentTypeByBase64Header : MIME_TYPES_BY_BASE64_HEADER.entrySet()) {
            if (base64.startsWith(contentTypeByBase64Header.getKey())) {
                return contentTypeByBase64Header.getValue();
            }
        }

        return DEFAULT_MIME_TYPE;
    }

    /**
     * Converts the given image bytes to a data URI.
     *
     * @param image The image bytes.
     * @return The data URI string in the format {@code data:<mime-type>;base64,<data>}.
     */
    protected static String toDataUri(byte[] image) {
        var base64 = encodeBase64(image);
        return "data:" + guessMimeType(base64) + ";base64," + base64;
    }
}
