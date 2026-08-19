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
package org.omnifaces.ai.modality;

import static org.omnifaces.ai.helper.TextHelper.isBlank;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.service.OllamaAIService;

/**
 * Default text handler for Ollama AI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see OllamaAIService
 */
public class OllamaAITextHandler extends DefaultAITextHandler {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new instance of this AI handler.
     */
    public OllamaAITextHandler() {
        //
    }

    /**
     * @see <a href="https://ollama.readthedocs.io/en/api/#generate-a-chat-completion">API Reference</a>
     */
    @Override
    public JsonObject buildChatPayload(AIService service, ChatInput input, ChatOptions options, boolean streaming) {
        var messages = Json.createArrayBuilder();
        buildChatPayloadSystemPrompt(service, messages, options);
        buildChatPayloadHistoryMessages(service, messages, input);
        buildChatPayloadUserContent(service, messages, input);
        var payload = Json.createObjectBuilder()
            .add("model", service.getModelName())
            .add("messages", messages)
            .add("stream", false);
        buildChatPayloadGenerationConfig(service, payload, options);

        return payload.build();
    }

    /**
     * Add system prompt to the messages array as a {@code system} role message.
     *
     * @param service The visiting AI service.
     * @param messages The messages array builder.
     * @param options The chat options.
     */
    protected void buildChatPayloadSystemPrompt(AIService service, JsonArrayBuilder messages, ChatOptions options) {
        if (!isBlank(options.getSystemPrompt())) {
            messages.add(
                Json.createObjectBuilder()
                    .add("role", "system")
                    .add("content", options.getSystemPrompt())
            );
        }
    }

    /**
     * Add conversation history messages to the messages array.
     *
     * @param service The visiting AI service.
     * @param messages The messages array builder.
     * @param input The chat input.
     */
    protected void buildChatPayloadHistoryMessages(AIService service, JsonArrayBuilder messages, ChatInput input) {
        for (var historyMessage : input.getHistory()) {
            messages.add(
                Json.createObjectBuilder()
                    .add("role", historyMessage.role() == Role.USER ? "user" : "assistant")
                    .add("content", historyMessage.content())
            );
        }
    }

    /**
     * Add user content (images and text message) to the messages array.
     *
     * @param service The visiting AI service.
     * @param messages The messages array builder.
     * @param input The chat input.
     */
    protected void buildChatPayloadUserContent(AIService service, JsonArrayBuilder messages, ChatInput input) {
        var message = Json.createObjectBuilder().add("role", "user");

        if (!input.getImages().isEmpty()) {
            var images = Json.createArrayBuilder();

            for (var image : input.getImages()) {
                images.add(image.toBase64());
            }

            message.add("images", images);
        }

        messages.add(
            message
                .add("content", input.getMessage())
        );
    }

    /**
     * Add generation config (temperature, maxTokens, topP, structured output) to the payload.
     *
     * @param service The visiting AI service.
     * @param payload The payload builder.
     * @param options The chat options.
     */
    protected void buildChatPayloadGenerationConfig(AIService service, JsonObjectBuilder payload, ChatOptions options) {
        var optionsBuilder = Json.createObjectBuilder()
            .add("temperature", options.getTemperature());

        if (options.getMaxTokens() != null) {
            optionsBuilder.add("num_predict", options.getMaxTokens());
        }

        if (options.getTopP() != 1.0) {
            optionsBuilder.add("top_p", options.getTopP());
        }

        payload.add("options", optionsBuilder);

        if (options.getJsonSchema() != null) {
            payload.add("format", options.getJsonSchema());
        }
    }

    @Override
    public List<String> getChatResponseContentPaths() {
        return List.of("message.content", "response");
    }

    @Override
    public List<String> getChatUsageInputTokensPaths() {
        return List.of("prompt_eval_count");
    }

    @Override
    public List<String> getChatUsageOutputTokensPaths() {
        return List.of("eval_count");
    }

}
