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

import static org.omnifaces.ai.helper.ImageHelper.toImageBase64;
import static org.omnifaces.ai.helper.TextHelper.isBlank;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import jakarta.json.Json;

import org.omnifaces.ai.AICapability;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.ChatOptions;
import org.omnifaces.ai.GenerateImageOptions;
import org.omnifaces.ai.exception.AIException;

/**
 * AI service implementation using Ollama API for local/self-hosted models.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 *     <li>provider: {@link AIProvider#OLLAMA}</li>
 * </ul>
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional.
 * See {@link AIProvider#OLLAMA} for defaults.
 * <ul>
 *     <li>model: the model to use</li>
 *     <li>endpoint: the API endpoint URL</li>
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider#OLLAMA
 * @see AIConfig
 * @see BaseAIService
 * @see AIService
 * @see <a href="https://docs.ollama.com/api/introduction">API Reference</a>.
 */
public class OllamaAIService extends BaseAIService {

    private static final long serialVersionUID = 1L;

    private static final AIModelVersion LLAMA_4 = AIModelVersion.of("llama", 4);

    /**
     * Constructs an Ollama service with the specified configuration.
     *
     * @param config the AI configuration
     * @see AIConfig
     */
    public OllamaAIService(AIConfig config) {
        super(config);
    }

    @Override
    public boolean supportsCapability(AICapability capability) {
        var currentModelVersion = getModelVersion();
        var fullModelName = getModelName().toLowerCase();

        return switch (capability) {
            case TEXT_ANALYSIS, TEXT_GENERATION -> true;
            case IMAGE_ANALYSIS -> currentModelVersion.gte(LLAMA_4) || fullModelName.contains("vision") || fullModelName.contains("llava") || fullModelName.contains("gemma");
            default -> false;
        };
    }

    @Override
    public CompletableFuture<String> chatAsync(String message, ChatOptions options) throws AIException {
        return asyncPostAndExtractMessageContent("api/chat", buildChatPayload(message, options));
    }

    @Override
    public CompletableFuture<String> analyzeImageAsync(byte[] image, String prompt) throws AIException {
        return asyncPostAndExtractMessageContent("api/chat", buildVisionPayload(image, buildAnalyzeImagePrompt(prompt)));
    }

    @Override
    public CompletableFuture<byte[]> generateImageAsync(String prompt, GenerateImageOptions options) throws AIException {
        throw new UnsupportedOperationException();
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

        var optionsBuilder = Json.createObjectBuilder()
            .add("temperature", options.getTemperature())
            .add("num_predict", options.getMaxTokens());

        if (options.getTopP() != 1.0) {
            optionsBuilder.add("top_p", options.getTopP());
        }

        if (options.getFrequencyPenalty() != 0.0) {
            optionsBuilder.add("frequency_penalty", options.getFrequencyPenalty());
        }

        if (options.getPresencePenalty() != 0.0) {
            optionsBuilder.add("presence_penalty", options.getPresencePenalty());
        }

        return Json.createObjectBuilder()
            .add("model", model)
            .add("messages", messages)
            .add("options", optionsBuilder)
            .add("stream", false)
            .build()
            .toString();
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
                    .add("content", prompt)
                    .add("images", Json.createArrayBuilder()
                        .add(toImageBase64(image)))))
            .add("stream", false)
            .build()
            .toString();
    }

    @Override
    protected List<String> getResponseMessageContentPaths() {
        return List.of("message.content", "response");
    }

    @Override
    protected List<String> getResponseImageContentPaths() {
        throw new UnsupportedOperationException();
    }
}
