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

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.ChatOptions;

/**
 * Default text handler for Ollama AI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public class OllamaAITextHandler extends BaseAITextHandler {

    @Override
    public JsonObject buildChatPayload(AIService service, String message, ChatOptions options, boolean streaming) {
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
            .add("temperature", options.getTemperature());

        if (options.getMaxTokens() != null) {
            optionsBuilder.add("num_predict", options.getMaxTokens());
        }

        if (options.getTopP() != 1.0) {
            optionsBuilder.add("top_p", options.getTopP());
        }

        return Json.createObjectBuilder()
            .add("model", service.getModelName())
            .add("messages", messages)
            .add("options", optionsBuilder)
            .add("stream", false)
            .build();
    }
}
