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

import static org.omnifaces.ai.helper.JsonHelper.findByPath;
import static org.omnifaces.ai.helper.TextHelper.isBlank;
import static org.omnifaces.ai.model.Sse.Event.Type.DATA;

import java.util.List;
import java.util.function.Consumer;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AITokenLimitExceededException;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.Sse.Event;
import org.omnifaces.ai.service.GoogleAIService;

/**
 * Default text handler for Google AI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see GoogleAIService
 */
public class GoogleAITextHandler extends DefaultAITextHandler {

    private static final long serialVersionUID = 1L;

    /**
     * @see <a href="https://ai.google.dev/gemini-api/docs/text-generation">API Reference</a>
     */
    @Override
    public JsonObject buildChatPayload(AIService service, ChatInput input, ChatOptions options, boolean streaming) {
        var payload = Json.createObjectBuilder();

        if (!isBlank(options.getSystemPrompt())) {
            payload.add("system_instruction", Json.createObjectBuilder()
                .add("parts", Json.createArrayBuilder()
                    .add(Json.createObjectBuilder()
                        .add("text", options.getSystemPrompt()))));
        }

        var contents = Json.createArrayBuilder();

        for (var historyMessage : input.getHistory()) {
            var parts = Json.createArrayBuilder();

            for (var uploadedFile : historyMessage.uploadedFiles()) {
                parts.add(Json.createObjectBuilder()
                    .add("file_data", Json.createObjectBuilder()
                        .add("mime_type", uploadedFile.mimeType().value())
                        .add("file_uri", uploadedFile.id())));
            }

            parts.add(Json.createObjectBuilder()
                .add("text", historyMessage.content()));

            contents.add(Json.createObjectBuilder()
                .add("role", historyMessage.role() == Role.USER ? "user" : "model")
                .add("parts", parts));
        }

        var parts = Json.createArrayBuilder();

        for (var image : input.getImages()) {
            parts.add(Json.createObjectBuilder()
                .add("inline_data", Json.createObjectBuilder()
                    .add("mime_type", image.mimeType().value())
                    .add("data", image.toBase64())));
        }

        if (!input.getFiles().isEmpty()) {
            checkSupportsFileAttachments(service);

            for (var file : input.getFiles()) {
                var fileId = service.upload(file);

                if (options.hasMemory()) {
                    options.recordUploadedFile(fileId, file.mimeType());
                }

                parts.add(Json.createObjectBuilder()
                    .add("file_data", Json.createObjectBuilder()
                        .add("mime_type", file.mimeType().value())
                        .add("file_uri", fileId)));
            }
        }

        parts.add(Json.createObjectBuilder()
            .add("text", input.getMessage()));

        contents.add(Json.createObjectBuilder()
            .add("role", "user")
            .add("parts", parts));

        payload.add("contents", contents);

        var generationConfig = Json.createObjectBuilder()
            .add("temperature", options.getTemperature());

        if (options.getMaxTokens() != null) {
            generationConfig.add("maxOutputTokens", options.getMaxTokens());
        }

        if (options.getTopP() != 1.0) {
            generationConfig.add("topP", options.getTopP());
        }

        if (options.getJsonSchema() != null) {
            checkSupportsStructuredOutput(service);
            generationConfig
                .add("responseMimeType", "application/json")
                .add("responseSchema", options.getJsonSchema());
        }

        return payload
            .add("generationConfig", generationConfig)
            .build();
    }

    @Override
    public List<String> getChatResponseContentPaths() {
        return List.of("candidates[0].content.parts[0].text");
    }

    @Override
    public List<String> getFileResponseIdPaths() {
        return List.of("file.uri");
    }

    @Override
    public boolean processChatStreamEvent(AIService service, Event event, Consumer<String> onToken) {
        if (event.type() == DATA) {
            return tryParseEventDataJson(event.value(), json -> {
                findByPath(json, "candidates[0].content.parts[0].text").ifPresent(onToken);
                var finishReason = findByPath(json, "candidates[0].finishReason");

                if (finishReason.filter("STOP"::equals).isPresent()) {
                    return false;
                }

                finishReason.filter("MAX_TOKENS"::equals).ifPresent(__ -> { throw new AITokenLimitExceededException(); });
                return true;
            });
        }

        return true;
    }
}
