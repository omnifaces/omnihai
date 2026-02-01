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

import static org.omnifaces.ai.helper.JsonHelper.replaceField;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.service.OpenRouterAIService;

/**
 * Default text handler for OpenRouter AI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see OpenRouterAIService
 */
public class OpenRouterAITextHandler extends OpenAITextHandler {

    private static final long serialVersionUID = 1L;

    @Override
    public JsonObject buildChatPayload(AIService service, ChatInput input, ChatOptions options, boolean streaming) {
        var files = input.getFiles();
        var payload = super.buildChatPayload(service, input.withoutFiles(), options, streaming);

        if (files.isEmpty()) {
            return payload;
        }

        var messages = payload.getJsonArray("messages");
        var lastMessage = messages.getJsonObject(messages.size() - 1);
        var newContent = buildContentWithPdfAttachmentsIfAny(service, files, lastMessage.getJsonArray("content"));
        var newLastMessage = replaceField(lastMessage, "content", newContent.build());
        var newMessages = Json.createArrayBuilder();

        for (int i = 0; i < messages.size() - 1; i++) {
            newMessages.add(messages.get(i));
        }

        return replaceField(payload, "messages", newMessages.add(newLastMessage).build()).build();
    }

    private static JsonArrayBuilder buildContentWithPdfAttachmentsIfAny(AIService service, List<Attachment> files, JsonArray originalContent) {
        var content = Json.createArrayBuilder();

        for (var file : files) {
            if (!"pdf".equals(file.mimeType().extension())) {
                throw new UnsupportedOperationException("Only PDF is supported in file upload by " + service.getName());
            }

            content.add(Json.createObjectBuilder()
                .add("type", "file")
                .add("file", Json.createObjectBuilder()
                    .add("filename", file.fileName())
                    .add("file_data", file.toDataUri())));
        }

        originalContent.forEach(content::add);

        return content;
    }
}
