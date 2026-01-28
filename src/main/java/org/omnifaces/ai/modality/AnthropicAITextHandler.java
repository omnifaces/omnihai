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

import static java.util.Optional.ofNullable;
import static java.util.logging.Level.FINE;
import static org.omnifaces.ai.helper.ImageHelper.guessImageMediaType;
import static org.omnifaces.ai.helper.ImageHelper.toImageBase64;
import static org.omnifaces.ai.helper.TextHelper.isBlank;
import static org.omnifaces.ai.model.Sse.Event.Type.DATA;
import static org.omnifaces.ai.model.Sse.Event.Type.EVENT;

import java.util.List;
import java.util.function.Consumer;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.exception.AITokenLimitExceededException;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.Sse.Event;
import org.omnifaces.ai.service.AnthropicAIService;

/**
 * Default text handler for Anthropic AI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AnthropicAIService
 */
public class AnthropicAITextHandler extends BaseAITextHandler {

    private static final long serialVersionUID = 1L;

    private static final AIModelVersion CLAUDE_3 = AIModelVersion.of("claude", 3);

    private static final int DEFAULT_MAX_TOKENS_CLAUDE_3_0 = 4096;
    private static final int DEFAULT_MAX_TOKENS_CLAUDE_3_X = 8192;

    @Override
    public JsonObject buildChatPayload(AIService service, ChatInput input, ChatOptions options, boolean streaming) {
        var payload = Json.createObjectBuilder()
            .add("model", service.getModelName())
            .add("max_tokens", ofNullable(options.getMaxTokens()).orElseGet(() -> service.getModelVersion().lte(CLAUDE_3) ? DEFAULT_MAX_TOKENS_CLAUDE_3_0 : DEFAULT_MAX_TOKENS_CLAUDE_3_X));

        if (!isBlank(options.getSystemPrompt())) {
            payload.add("system", options.getSystemPrompt());
        }

        var content = Json.createArrayBuilder();

        for (var image : input.getImages()) {
            var base64 = toImageBase64(image);

            content.add(Json.createObjectBuilder()
                .add("type", "image")
                .add("source", Json.createObjectBuilder()
                    .add("type", "base64")
                    .add("media_type", guessImageMediaType(base64))
                    .add("data", base64)));
        }

        content.add(Json.createObjectBuilder()
            .add("type", "text")
            .add("text", input.getMessage()));

        payload.add("messages", Json.createArrayBuilder()
            .add(Json.createObjectBuilder()
                .add("role", "user")
                .add("content", content)));

        if (streaming) {
            if (!service.supportsStreaming()) {
                throw new IllegalStateException();
            }

            payload.add("stream", true);
        }

        if (options.getTemperature() != 0.7) {
            payload.add("temperature", options.getTemperature());
        }

        if (options.getTopP() != 1.0) {
            payload.add("top_p", options.getTopP());
        }

        return payload.build();
    }

    @Override
    public List<String> getChatResponseContentPaths() {
        return List.of("content[0].text");
    }

    @Override
    public boolean processChatStreamEvent(AIService service, Event event, Consumer<String> onToken) {
        logger.log(FINE, event::toString);

        if (event.type() == EVENT) {
            if ("max_tokens".equals(event.value())) {
                throw new AITokenLimitExceededException();
            }

            return !"message_stop".equals(event.value()) && !"content_block_stop".equals(event.value());
        }
        else if (event.type() == DATA) {
            return tryParseEventDataJson(event.value(), json -> {
                var type = json.getString("type", null);

                if ("content_block_delta".equals(type)) {
                    var token = json.getJsonObject("delta").getString("text", "");

                    if (!token.isEmpty()) { // Do not use isBlank! Whitespace can be a valid token.
                        onToken.accept(token);
                    }
                }
                else if ("error".equals(type)) {
                    throw new AIResponseException("Error event returned", event.value());
                }

                return true;
            });
        }

        return true;
    }
}
