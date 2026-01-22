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

import static org.omnifaces.ai.helper.ImageHelper.guessImageMediaType;
import static org.omnifaces.ai.helper.ImageHelper.toImageBase64;
import static org.omnifaces.ai.helper.TextHelper.isBlank;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import jakarta.json.Json;

import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.model.ChatOptions;

/**
 * AI service implementation using Anthropic API.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 *     <li>provider: {@link AIProvider#ANTHROPIC}</li>
 *     <li>apiKey: your Anthropic API key</li>
 * </ul>
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional.
 * See {@link AIProvider#ANTHROPIC} for defaults.
 * <ul>
 *     <li>model: the model to use</li>
 *     <li>endpoint: the API endpoint URL</li>
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider#ANTHROPIC
 * @see AIConfig
 * @see BaseAIService
 * @see AIService
 * @see <a href="https://platform.claude.com/docs/en/api/overview">API Reference</a>
 */
public class AnthropicAIService extends BaseAIService {

    private static final long serialVersionUID = 1L;

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /**
     * Constructs an Anthropic service with the specified configuration.
     *
     * @param config the AI configuration
     * @see AIConfig
     */
    public AnthropicAIService(AIConfig config) {
        super(config);
    }

    @Override
    public boolean supportsModality(AIModality modality) {
        return switch (modality) {
            case IMAGE_ANALYSIS -> true;
            default -> false;
        };
    }

    @Override
    protected Map<String, String> getRequestHeaders() {
        return Map.of("x-api-key", apiKey, "anthropic-version", ANTHROPIC_VERSION);
    }

    @Override
    public CompletableFuture<String> chatAsync(String message, ChatOptions options) throws AIException {
        return asyncPostAndExtractMessageContent("messages", buildChatPayload(message, options));
    }

    @Override
    public CompletableFuture<String> analyzeImageAsync(byte[] image, String prompt) throws AIException {
        return asyncPostAndExtractMessageContent("messages", buildVisionPayload(image, buildAnalyzeImagePrompt(prompt)));
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

        var payload = Json.createObjectBuilder()
            .add("model", model)
            .add("max_tokens", options.getMaxTokens());

        if (!isBlank(options.getSystemPrompt())) {
            payload.add("system", options.getSystemPrompt());
        }

        payload.add("messages", Json.createArrayBuilder()
            .add(Json.createObjectBuilder()
                .add("role", "user")
                .add("content", message)));

        if (options.getTemperature() != 0.7) {
            payload.add("temperature", options.getTemperature());
        }

        if (options.getTopP() != 1.0) {
            payload.add("top_p", options.getTopP());
        }

        return payload.build().toString();
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

        var base64 = toImageBase64(image);
        return Json.createObjectBuilder()
            .add("model", model)
            .add("max_tokens", 1000)
            .add("messages", Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                    .add("role", "user")
                    .add("content", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                            .add("type", "image")
                            .add("source", Json.createObjectBuilder()
                                .add("type", "base64")
                                .add("media_type", guessImageMediaType(base64))
                                .add("data", base64)))
                        .add(Json.createObjectBuilder()
                            .add("type", "text")
                            .add("text", prompt)))))
            .build()
            .toString();
    }

    @Override
    protected List<String> getResponseMessageContentPaths() {
        return List.of("content[0].text");
    }

    @Override
    protected List<String> getResponseImageContentPaths() {
        throw new UnsupportedOperationException();
    }
}
