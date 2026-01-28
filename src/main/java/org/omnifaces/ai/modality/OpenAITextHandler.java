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
import static org.omnifaces.ai.helper.ImageHelper.toImageDataUri;
import static org.omnifaces.ai.helper.JsonHelper.extractByPath;
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
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.Sse.Event;
import org.omnifaces.ai.service.OpenAIService;

/**
 * Default text handler for OpenAI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see OpenAIService
 */
public class OpenAITextHandler extends BaseAITextHandler {

    private static final long serialVersionUID = 1L;

    private static final AIModelVersion GPT_5 = AIModelVersion.of("gpt", 5);

    @Override
    public JsonObject buildChatPayload(AIService service, ChatInput input, ChatOptions options, boolean streaming) {
        var currentModelVersion = service.getModelVersion();
        var supportsResponsesApi = supportsResponsesApi(service);
        var payload = Json.createObjectBuilder()
            .add("model", service.getModelName());

        if (options.getMaxTokens() != null) {
            payload.add(supportsResponsesApi ? "max_output_tokens" : currentModelVersion.gte(GPT_5) ? "max_completion_tokens" : "max_tokens", options.getMaxTokens());
        }

        var message = Json.createArrayBuilder();

        if (!isBlank(options.getSystemPrompt())) {
            if (supportsResponsesApi) {
                payload.add("instructions", options.getSystemPrompt());
            }
            else {
                message.add(Json.createObjectBuilder()
                    .add("role", "system")
                    .add("content", options.getSystemPrompt()));
            }
        }

        var content = Json.createArrayBuilder();

        for (var image : input.getImages()) {
            var img = Json.createObjectBuilder().add("type", supportsResponsesApi ? "input_image" : "image_url");

            if (supportsResponsesApi) {
                img.add("image_url", toImageDataUri(image));
            }
            else {
                img.add("image_url", Json.createObjectBuilder().add("url", toImageDataUri(image)));
            }

            content.add(img);
        }

        content.add(Json.createObjectBuilder()
            .add("type", supportsResponsesApi ? "input_text" : "text")
            .add("text", input.getMessage()));

        message.add(Json.createObjectBuilder()
            .add("role", "user")
            .add("content", content));

        payload.add(supportsResponsesApi ? "input" : "messages", message);

        if (streaming) {
            if (!service.supportsStreaming()) {
                throw new UnsupportedOperationException("service.supportsStreaming() returned false, so ...");
            }

            payload.add("stream", true);
        }

        if (currentModelVersion.ne(GPT_5)) {
            payload.add("temperature", options.getTemperature());
        }

        if (options.getTopP() != 1.0) {
            payload.add("top_p", options.getTopP());
        }

        if (options.getJsonSchema() != null) {
            if (!service.supportsStructuredOutput()) {
                throw new UnsupportedOperationException("service.supportsStructuredOutput() returned false, so ...");
            }

            if (supportsResponsesApi) {
                var format = Json.createObjectBuilder().add("type", "json_schema");
                options.getJsonSchema().forEach(format::add);
                payload.add("text", Json.createObjectBuilder().add("format", format));
            }
            else {
                payload.add("response_format", Json.createObjectBuilder()
                    .add("type", "json_schema")
                    .add("json_schema", options.getJsonSchema()));
            }
        }

        return payload.build();
    }

    @Override
    public JsonObject buildModerationJsonSchema(ModerationOptions options) {
        var baseSchema = super.buildModerationJsonSchema(options);
        var scoresSchema = baseSchema.getJsonObject("properties").getJsonObject("scores");

        var strictScoresSchema = Json.createObjectBuilder(scoresSchema)
            .add("additionalProperties", false);

        var strictSchema = Json.createObjectBuilder()
            .add("type", "object")
            .add("properties", Json.createObjectBuilder().add("scores", strictScoresSchema))
            .add("required", baseSchema.getJsonArray("required"))
            .add("additionalProperties", false);

        return Json.createObjectBuilder()
            .add("name", "moderation_result")
            .add("strict", true)
            .add("schema", strictSchema)
            .build();
    }

    @Override
    public List<String> getChatResponseContentPaths() {
        return List.of("output[*].content[*].text", "choices[0].message.content");
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
            return tryParseEventDataJson(event.value(), json -> {
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

                return true;
            });
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

                        var finishReason = extractByPath(json, "choices[0].finish_reason");

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
        return service instanceof OpenAIService openai && openai.supportsOpenAIResponsesApi();
    }
}
