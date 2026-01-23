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

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;
import static org.omnifaces.ai.helper.JsonHelper.extractByPath;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;
import static org.omnifaces.ai.helper.TextHelper.isBlank;
import static org.omnifaces.ai.model.Sse.Event.Type.DATA;
import static org.omnifaces.ai.model.Sse.Event.Type.EVENT;

import java.util.function.Consumer;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.exception.AITokenLimitExceededException;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.Sse.Event;
import org.omnifaces.ai.service.OpenAIService;

/**
 * Default text handler for OpenAI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public class OpenAITextHandler extends BaseAITextHandler {

    private static final AIModelVersion GPT_5 = AIModelVersion.of("gpt", 5);

    @Override
    public JsonObject buildChatPayload(AIService service, String message, ChatOptions options, boolean streaming) {
        if (isBlank(message)) {
            throw new IllegalArgumentException("Message cannot be blank");
        }

        var currentModelVersion = service.getModelVersion();
        var supportsResponsesApi = supportsResponsesApi(service);
        var payload = Json.createObjectBuilder()
            .add("model", service.getModelName());

        if (options.getMaxTokens() != null) {
            payload.add(supportsResponsesApi ? "max_output_tokens" : currentModelVersion.gte(GPT_5) ? "max_completion_tokens" : "max_tokens", options.getMaxTokens());
        }

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
            if (!service.supportsStreaming()) {
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

        return payload.build();
    }


    @Override
    public boolean processChatStreamEvent(AIService service, Event event, Consumer<String> onToken) {
        var supportsResponsesApi = supportsResponsesApi(service);
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
                throw new AITokenLimitExceededException();
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
                throw new AIResponseException("Error event returned", event.value());
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
                return tryParseEventDataJson(event.value(), json -> {
                    if ("chat.completion.chunk".equals(json.getString("object", null))) {
                        var token = extractByPath(json, "choices[0].delta.content");

                        if (token != null && !token.isEmpty()) { // Do not use isBlank! Whitespace can be a valid token.
                            onToken.accept(token);
                        }

                        var finishReason = extractByPath(json, "choices[*].finish_reason");

                        if ("length".equals(finishReason)) {
                            throw new AITokenLimitExceededException();
                        }
                    }

                    return true;
                });
            }
        }

        return true;
    }

    private static boolean supportsResponsesApi(AIService service) {
        return service instanceof OpenAIService openai && openai.supportsResponsesApi();
    }
}
