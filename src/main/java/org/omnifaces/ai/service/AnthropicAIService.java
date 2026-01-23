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

import static java.util.logging.Level.FINE;
import static org.omnifaces.ai.helper.ImageHelper.guessImageMediaType;
import static org.omnifaces.ai.helper.ImageHelper.toImageBase64;
import static org.omnifaces.ai.helper.TextHelper.isBlank;
import static org.omnifaces.ai.model.Sse.Event.Type.DATA;
import static org.omnifaces.ai.model.Sse.Event.Type.EVENT;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import jakarta.json.Json;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIApiResponseException;
import org.omnifaces.ai.exception.AIApiTokenLimitExceededException;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.Sse.Event;

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
    private static final Logger logger = Logger.getLogger(AnthropicAIService.class.getPackageName());

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final AIModelVersion CLAUDE_3 = AIModelVersion.of("claude", 3);

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
    protected boolean supportsStreaming() {
        return getModelVersion().gte(CLAUDE_3);
    }

    @Override
    protected Map<String, String> getRequestHeaders() {
        return Map.of("x-api-key", apiKey, "anthropic-version", ANTHROPIC_VERSION);
    }

    @Override
    protected String getChatPath(boolean streaming) {
        return "messages";
    }

    @Override
    protected String buildChatPayload(String message, ChatOptions options, boolean streaming) {
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

        if (streaming) {
            if (!supportsStreaming()) {
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

        return payload.build().toString();
    }

    @Override
    protected boolean processChatStreamEvent(Event event, Consumer<String> onToken) {
        logger.log(FINE, event::toString);

        if (event.type() == EVENT) {
            if ("max_tokens".equals(event.value())) {
                throw new AIApiTokenLimitExceededException();
            }

            return !"message_stop".equals(event.value()) && !"content_block_stop".equals(event.value());
        }
        else if (event.type() == DATA) {
            return tryParseEventDataJson(event.value(), logger, json -> {
                var type = json.getString("type", null);

                if ("content_block_delta".equals(type)) {
                    var token = json.getJsonObject("delta").getString("text", "");

                    if (!token.isEmpty()) { // Do not use isBlank! Whitespace can be a valid token.
                        onToken.accept(token);
                    }
                }
                else if ("error".equals(type)) {
                    throw new AIApiResponseException("Error event returned", event.value());
                }

                return true;
            });
        }

        return true;
    }

    @Override
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
}
