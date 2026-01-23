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

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;
import static org.omnifaces.ai.helper.ImageHelper.toImageDataUri;
import static org.omnifaces.ai.helper.JsonHelper.extractByPath;
import static org.omnifaces.ai.helper.JsonHelper.isEmpty;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;
import static org.omnifaces.ai.helper.TextHelper.isBlank;
import static org.omnifaces.ai.model.Sse.Event.Type.DATA;
import static org.omnifaces.ai.model.Sse.Event.Type.EVENT;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIApiResponseException;
import org.omnifaces.ai.exception.AIApiTokenLimitExceededException;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.GenerateImageOptions;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.ModerationOptions.Category;
import org.omnifaces.ai.model.ModerationResult;
import org.omnifaces.ai.model.Sse.Event;

/**
 * AI service implementation using OpenAI API.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 *     <li>provider: {@link AIProvider#OPENAI}</li>
 *     <li>apiKey: your OpenAI API key</li>
 * </ul>
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional.
 * See {@link AIProvider#OPENAI} for defaults.
 * <ul>
 *     <li>model: the model to use</li>
 *     <li>endpoint: the API endpoint URL</li>
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider#OPENAI
 * @see AIConfig
 * @see BaseAIService
 * @see AIService
 * @see <a href="https://platform.openai.com/docs/api-reference">API Reference</a>
 */
public class OpenAIService extends BaseAIService {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(OpenAIService.class.getPackageName());

    private static final AIModelVersion GPT_4 = AIModelVersion.of("gpt", 4);
    private static final AIModelVersion GPT_5 = AIModelVersion.of("gpt", 5);
    private static final AIModelVersion DALL_E = AIModelVersion.of("dall-e");

    /**
     * Constructs an OpenAI service with the specified configuration.
     *
     * @param config the AI configuration
     * @see AIConfig
     */
    public OpenAIService(AIConfig config) {
        super(config);
    }

    @Override
    public boolean supportsModality(AIModality modality) {
        var currentModelVersion = getModelVersion();
        var fullModelName = getModelName().toLowerCase();

        return switch (modality) {
            case IMAGE_ANALYSIS -> currentModelVersion.gte(GPT_4) || fullModelName.contains("vision");
            case IMAGE_GENERATION -> currentModelVersion.gte(DALL_E) || fullModelName.contains("image");
            case AUDIO_ANALYSIS -> currentModelVersion.gte(GPT_4) || fullModelName.contains("transcribe");
            case AUDIO_GENERATION -> currentModelVersion.gte(GPT_4) || fullModelName.contains("tts");
            case VIDEO_ANALYSIS -> currentModelVersion.gte(GPT_5);
            case VIDEO_GENERATION -> false;
        };
    }

    /**
     * Returns whether this service supports native moderation for the given categories.
     * When {@code true}, {@link #moderateContent(String, ModerationOptions)} will use OpenAI's moderation API.
     * When {@code false}, it falls back to the chat-based moderation in {@link BaseAIService}.
     *
     * @param categories The moderation categories to check.
     * @return {@code true} if all categories are supported by OpenAI's moderation API.
     */
    protected boolean supportsOpenAIModerationCapability(Set<String> categories) {
        return categories.stream().allMatch(Category.OPENAI_SUPPORTED_CATEGORY_NAMES::contains);
    }

    /**
     * Returns whether this OpenAI based service implementation supports the new /responses API as replacement for the legacy /chat/completions API.
     * The default implementation returns true if {@link #getModelVersion()} is at least {@code gpt-4}.
     * @return Whether this OpenAI based service implementation supports the new /responses API as replacement for the legacy /chat/completions API.
     */
    protected boolean supportsResponsesApi() {
        return getModelVersion().gte(GPT_4);
    }

    @Override
    protected boolean supportsStreaming() {
        return supportsResponsesApi();
    }

    /**
     * Returns only authorization bearer header with API key as value.
     */
    @Override
    protected Map<String, String> getRequestHeaders() {
        return Map.of("Authorization", "Bearer ".concat(apiKey));
    }

    /**
     * If {@link #supportsResponsesApi()} then returns {@code responses} else {@code chat/completions}.
     */
    @Override
    protected String getChatPath(boolean streaming) {
        return supportsResponsesApi() ? "responses" : "chat/completions";
    }

    /**
     * Returns {@code image/generations}.
     */
    @Override
    protected String getGenerateImagePath() {
        return "images/generations";
    }

    @Override
    protected String buildChatPayload(String message, ChatOptions options, boolean streaming) {
        if (isBlank(message)) {
            throw new IllegalArgumentException("Message cannot be blank");
        }

        var currentModelVersion = getModelVersion();
        var supportsResponsesApi = supportsResponsesApi();
        var payload = Json.createObjectBuilder()
            .add("model", model)
            .add(supportsResponsesApi ? "max_output_tokens" : currentModelVersion.gte(GPT_5) ? "max_completion_tokens" : "max_tokens", options.getMaxTokens());

        var input = Json.createArrayBuilder();

        if (!isBlank(options.getSystemPrompt())) {
            if (supportsResponsesApi) {
                payload.add("instructions", options.getSystemPrompt());
            }
            else {
                input.add(Json.createObjectBuilder()
                        .add("role", "system")
                        .add("content", options.getSystemPrompt()));
            }
        }

        input.add(Json.createObjectBuilder()
            .add("role", "user")
            .add("content", message));

        payload.add(supportsResponsesApi ? "input" : "messages", input);

        if (streaming) {
            if (!supportsStreaming()) {
                throw new IllegalStateException();
            }

            payload.add("stream", true);
        }

        if (currentModelVersion.ne(GPT_5)) {
            payload.add("temperature", options.getTemperature());
        }

        if (options.getTopP() != 1.0) {
            payload.add("top_p", options.getTopP());
        }

        return payload.build().toString();
    }

    @Override
    protected boolean processChatStreamEvent(Event event, Consumer<String> onToken) {
        var supportsResponsesApi = supportsResponsesApi();
        logger.log(FINE, () -> event + " (" + supportsResponsesApi + ")");

        if (supportsResponsesApi) {
            return processChatStreamEventWithResponsesApi(event, onToken);
        }
        else {
            return processChatStreamEventWithChatCompletionsApi(event, onToken);
        }
    }

    /**
     * Process chat stream event with {@code responses} API.
     * @param event Stream event.
     * @param onToken Callback receiving each stream data chunk (often one word/token/line).
     * @return {@code true} to continue processing the stream, or {@code false} when end of stream is reached.
     */
    protected boolean processChatStreamEventWithResponsesApi(Event event, Consumer<String> onToken) {
        if (event.type() == EVENT) {
            if ("response.completed".equals(event.value())) {
                return true;
            }

            if ("response.incomplete".equals(event.value())) {
                throw new AIApiTokenLimitExceededException();
            }
        }
        else if (event.type() == DATA && (event.value().contains("response.output_text.delta") || event.value().contains("response.failed"))) { // Cheap pre-filter before expensive parse because OpenAI returns pretty a lot of events.
            JsonObject json;

            try {
                json = parseJson(event.value());
            }
            catch (Exception e) {
                logger.log(WARNING, e, () -> "Skipping unparseable stream event data: " + event.value());
                return true;
            }

            var type = json.getString("type", null);

            if ("response.output_text.delta".equals(type)) {
                var token = json.getString("delta", "");

                if (!token.isEmpty()) { // Do not use isBlank! Whitespace can be a valid token.
                    onToken.accept(token);
                }
            }
            else if ("response.failed".equals(type)) {
                throw new AIApiResponseException("Error event returned", event.value());
            }
        }

        return true;
    }

    /**
     * Process chat stream event with {@code chat/completions} API.
     * @param event Stream event.
     * @param onToken Callback receiving each stream data chunk (often one word/token/line).
     * @return {@code true} to continue processing the stream, or {@code false} when end of stream is reached.
     */
    protected boolean processChatStreamEventWithChatCompletionsApi(Event event, Consumer<String> onToken) {
        if (event.type() == DATA) {
            if ("DONE".equalsIgnoreCase(event.value())) {
                return false;
            }
            else if (event.value().contains("chat.completion.chunk")) { // Cheap pre-filter before expensive parse.
                return tryParseEventDataJson(event.value(), logger, json -> {
                    if ("chat.completion.chunk".equals(json.getString("object", null))) {
                        var token = extractByPath(json, "choices[0].delta.content");

                        if (token != null && !token.isEmpty()) { // Do not use isBlank! Whitespace can be a valid token.
                            onToken.accept(token);
                        }

                        var finishReason = extractByPath(json, "choices[*].finish_reason");

                        if ("length".equals(finishReason)) {
                            throw new AIApiTokenLimitExceededException();
                        }
                    }

                    return true;
                });
            }
        }

        return true;
    }

    @Override
    public CompletableFuture<ModerationResult> moderateContentAsync(String content, ModerationOptions options) throws AIException {
        if (supportsOpenAIModerationCapability(options.getCategories())) {
            var jsonPayload = Json.createObjectBuilder().add("input", content).build().toString();
            return API_CLIENT.post(this, "moderations", jsonPayload).thenApply(response -> parseOpenAIModerationResult(response, options));
        }
        else {
            return super.moderateContentAsync(content, options);
        }
    }

    @Override
    protected String buildVisionPayload(byte[] image, String prompt) {
        if (isBlank(prompt)) {
            throw new IllegalArgumentException("Prompt cannot be blank");
        }

        var imageJson = Json.createObjectBuilder()
            .add("type", supportsResponsesApi() ? "input_image" : "image_url");

        if (supportsResponsesApi()) {
            imageJson.add("image_url", toImageDataUri(image));
        }
        else {
            imageJson.add("image_url", Json.createObjectBuilder().add("url", toImageDataUri(image)));
        }

        return Json.createObjectBuilder()
            .add("model", model)
            .add(supportsResponsesApi() ? "input" : "messages", Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                    .add("role", "user")
                    .add("content", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                            .add("type", supportsResponsesApi() ? "input_text" : "text")
                            .add("text", prompt))
                        .add(imageJson))))
            .build()
            .toString();
    }

    @Override
    protected String buildGenerateImagePayload(String prompt, GenerateImageOptions options) {
        if (isBlank(prompt)) {
            throw new IllegalArgumentException("Prompt cannot be blank");
        }

        return Json.createObjectBuilder()
            .add("model", model)
            .add("prompt", prompt)
            .add("n", 1)
            .add("size", options.getSize())
            .add("quality", options.getQuality())
            .add("output_format", options.getOutputFormat())
            .build()
            .toString();
    }

    @Override
    protected List<String> getResponseMessageContentPaths() {
        return List.of("output[*].content[*].text", "choices[0].message.content");
    }

    @Override
    protected List<String> getResponseImageContentPaths() {
        return List.of("output[*].content[*].image_base64", "data[0].b64_json");
    }

    /**
     * Parses the moderation result from OpenAI's moderation API response.
     *
     * @param responseBody The JSON response from OpenAI's moderation API.
     * @param options The moderation options containing categories and threshold.
     * @return The parsed moderation result.
     * @throws AIApiResponseException If the response cannot be parsed as JSON or is missing moderation results.
     */
    protected ModerationResult parseOpenAIModerationResult(String responseBody, ModerationOptions options) throws AIApiResponseException {
        var responseJson = parseJson(responseBody);
        var results = responseJson.getJsonArray("results");

        if (isEmpty(results)) {
            throw new AIApiResponseException("Response is empty", responseBody);
        }

        var result = results.getJsonObject(0);
        var scores = new HashMap<String, Double>();
        var flagged = false;

        if (result.containsKey("category_scores")) {
            var categoryScores = result.getJsonObject("category_scores");

            for (var category : categoryScores.keySet()) {
                if (options.getCategories().contains(category.split("/", 2)[0])) {
                    var score = categoryScores.getJsonNumber(category).doubleValue();
                    scores.put(category, score);

                    if (score > options.getThreshold()) {
                        flagged = true;
                    }
                }
            }
        }

        return new ModerationResult(flagged, scores);
    }
}
