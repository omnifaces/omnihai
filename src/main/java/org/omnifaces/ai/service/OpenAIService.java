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

import static org.omnifaces.ai.helper.ImageHelper.toImageDataUri;
import static org.omnifaces.ai.helper.JsonHelper.isEmpty;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;
import static org.omnifaces.ai.helper.TextHelper.isBlank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import jakarta.json.Json;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.ChatOptions;
import org.omnifaces.ai.GenerateImageOptions;
import org.omnifaces.ai.ModerationOptions;
import org.omnifaces.ai.ModerationOptions.Category;
import org.omnifaces.ai.ModerationResult;
import org.omnifaces.ai.exception.AIApiResponseException;
import org.omnifaces.ai.exception.AIException;

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

    private static final AIModelVersion GPT_5 = AIModelVersion.of("gpt", 5);

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
    protected Map<String, String> getRequestHeaders() {
        return Map.of("Authorization", "Bearer ".concat(apiKey));
    }

    @Override
    public CompletableFuture<String> chatAsync(String message, ChatOptions options) throws AIException {
        return asyncPostAndExtractMessageContent("chat/completions", buildChatPayload(message, options));
    }

    @Override
    public CompletableFuture<ModerationResult> moderateContentAsync(String content, ModerationOptions options) throws AIException {
        if (supportsModerationCapability(options.getCategories())) {
            var jsonPayload = Json.createObjectBuilder().add("input", content).build().toString();
            return API_CLIENT.post(this, "moderations", jsonPayload).thenApply(response -> parseOpenAIModerationResult(response, options));
        }
        else {
            return super.moderateContentAsync(content, options);
        }
    }

    @Override
    public CompletableFuture<String> analyzeImageAsync(byte[] image, String prompt) throws AIException {
        return asyncPostAndExtractMessageContent("chat/completions", buildVisionPayload(image, buildAnalyzeImagePrompt(prompt)));
    }

    @Override
    public CompletableFuture<byte[]> generateImageAsync(String prompt, GenerateImageOptions options) throws AIException {
        return asyncPostAndExtractImageContent("images/generations", buildGenerateImagePayload(prompt, options));
    }

    /**
     * Builds the JSON request payload for {@link #chatAsync(String, ChatOptions)}.
     * You can override this method to customize the payload.
     *
     * @param message The user message.
     * @param options The chat options.
     * @return The JSON request payload.
     */
    protected String buildChatPayload(String message, ChatOptions options) {
        if (isBlank(message)) {
            throw new IllegalArgumentException("Message cannot be blank");
        }

        var messages = Json.createArrayBuilder();

        if (!isBlank(options.getSystemPrompt())) {
            messages.add(Json.createObjectBuilder()
                .add("role", "system")
                .add("content", options.getSystemPrompt()));
        }

        messages.add(Json.createObjectBuilder()
            .add("role", "user")
            .add("content", message));

        var currentModelVersion = getAIModelVersion();
        var optionsBuilder = Json.createObjectBuilder()
            .add("model", model)
            .add("messages", messages)
            .add(currentModelVersion.gte(GPT_5) ? "max_completion_tokens" : "max_tokens", options.getMaxTokens());

        if (currentModelVersion.ne(GPT_5)) {
            optionsBuilder.add("temperature", options.getTemperature());
        }

        if (options.getTopP() != 1.0) {
            optionsBuilder.add("top_p", options.getTopP());
        }

        if (options.getFrequencyPenalty() != 0.0) {
            optionsBuilder.add("frequency_penalty", options.getFrequencyPenalty());
        }

        if (options.getPresencePenalty() != 0.0) {
            optionsBuilder.add("presence_penalty", options.getPresencePenalty());
        }

        return optionsBuilder.build().toString();
    }

    /**
     * Builds the JSON request payload for {@link #analyzeImageAsync(byte[], String)}.
     * You can override this method to customize the payload.
     *
     * @param image The image bytes.
     * @param prompt The analysis prompt.
     * @return The JSON request payload.
     */
    protected String buildVisionPayload(byte[] image, String prompt) {
        if (isBlank(prompt)) {
            throw new IllegalArgumentException("Prompt cannot be blank");
        }

        return Json.createObjectBuilder()
            .add("model", model)
            .add("messages", Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                    .add("role", "user")
                    .add("content", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                            .add("type", "text")
                            .add("text", prompt))
                        .add(Json.createObjectBuilder()
                            .add("type", "image_url")
                            .add("image_url", Json.createObjectBuilder()
                                .add("url", toImageDataUri(image)))))))
            .build()
            .toString();
    }

    /**
     * Builds the JSON request payload for {@link #generateImageAsync(String, GenerateImageOptions)}.
     * You can override this method to customize the payload.
     *
     * @param prompt The image generation prompt.
     * @param options The image generation options.
     * @return The JSON request payload.
     */
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
            .add("style", options.getStyle())
            .add("output_format", options.getOutputFormat())
            .add("response_format", "b64_json")
            .build()
            .toString();
    }

    @Override
    protected List<String> getResponseMessageContentPaths() {
        return List.of("choices[0].message.content");
    }

    @Override
    protected List<String> getResponseImageContentPaths() {
        return List.of("data[0].b64_json");
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
            throw new AIApiResponseException("Response is empty");
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

    /**
     * Returns whether this service supports native moderation for the given categories.
     * When {@code true}, {@link #moderateContent(String, ModerationOptions)} will use OpenAI's moderation API.
     * When {@code false}, it falls back to the chat-based moderation in {@link BaseAIService}.
     *
     * @param categories The moderation categories to check.
     * @return {@code true} if all categories are supported by OpenAI's moderation API.
     */
    protected boolean supportsModerationCapability(Set<String> categories) {
        return categories.stream().allMatch(Category.OPENAI_SUPPORTED_CATEGORY_NAMES::contains);
    }
}
