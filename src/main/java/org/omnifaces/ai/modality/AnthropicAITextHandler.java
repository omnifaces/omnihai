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
import static java.util.stream.Collectors.joining;
import static org.omnifaces.ai.helper.JsonHelper.addStrictAdditionalProperties;
import static org.omnifaces.ai.helper.JsonHelper.checkErrors;
import static org.omnifaces.ai.helper.JsonHelper.findAllByPath;
import static org.omnifaces.ai.helper.JsonHelper.findFirstByPath;
import static org.omnifaces.ai.helper.TextHelper.isBlank;
import static org.omnifaces.ai.modality.OpenAITextHandler.buildUserLocation;
import static org.omnifaces.ai.model.Sse.Event.Type.DATA;
import static org.omnifaces.ai.model.Sse.Event.Type.EVENT;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.exception.AITokenLimitExceededException;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.ReasoningEffort;
import org.omnifaces.ai.model.ChatUsage;
import org.omnifaces.ai.model.Sse.Event;
import org.omnifaces.ai.service.AnthropicAIService;

/**
 * Default text handler for Anthropic AI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AnthropicAIService
 */
public class AnthropicAITextHandler extends DefaultAITextHandler {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new instance of this AI handler.
     */
    public AnthropicAITextHandler() {
        //
    }

    private static final AIModelVersion CLAUDE_3 = AIModelVersion.of("claude", 3);
    private static final AIModelVersion CLAUDE_4_6 = AIModelVersion.of("claude", 4, 6);

    private static final int DEFAULT_MAX_TOKENS_CLAUDE_3_0 = 4096;
    private static final int DEFAULT_MAX_TOKENS_CLAUDE_3_X = 8192;

    /**
     * @see <a href="https://platform.claude.com/docs/en/api/messages">API Reference</a>
     */
    @Override
    public JsonObject buildChatPayload(AIService service, ChatInput input, ChatOptions options, boolean streaming) {
        var payload = Json.createObjectBuilder()
            .add("model", service.getModelName())
            .add("max_tokens", computeEffectiveMaxTokens(service, options)); // Required!
        var messages = Json.createArrayBuilder();
        buildChatPayloadTools(service, payload, options);
        // Anthropic API ignores user_location when running web search tool. The user_location field is still sent for forward-compatibility
        // (may be respected in future model versions).
        buildChatPayloadSystemPrompt(service, payload, appendWebSearchLocationToPromptIfNecessary(options));
        buildChatPayloadHistoryMessages(service, messages, input);
        buildChatPayloadUserContent(service, messages, input, options);
        payload.add("messages", messages);
        buildChatPayloadGenerationConfig(service, payload, options, streaming);
        return payload.build();
    }

    /**
     * Add tools to the payload as a top-level {@code tools} field.
     *
     * @param service The visiting AI service.
     * @param payload The payload builder.
     * @param options The chat options.
     * @since 1.3
     * @see <a href="https://platform.claude.com/docs/en/agents-and-tools/tool-use/web-search-tool">Web Search Tool Reference</a>
     */
    protected void buildChatPayloadTools(AIService service, JsonObjectBuilder payload, ChatOptions options) {
        if (options.useWebSearch()) {
            var webSearchTool = Json.createObjectBuilder()
                .add("type", service.getModelVersion().gte(CLAUDE_4_6) ? "web_search_20260209" : "web_search_20250305")
                .add("name", "web_search");

            buildUserLocation(options.getWebSearchLocation()).ifPresent(userLocation -> webSearchTool.add("user_location", userLocation));

            payload.add("tools", Json.createArrayBuilder().add(webSearchTool.build()));
        }
    }

    /**
     * Add system prompt to the payload as a top-level {@code system} field.
     *
     * @param service The visiting AI service.
     * @param payload The payload builder.
     * @param options The chat options.
     * @since 1.2
     */
    protected void buildChatPayloadSystemPrompt(AIService service, JsonObjectBuilder payload, ChatOptions options) {
        if (!isBlank(options.getSystemPrompt())) {
            payload.add("system", options.getSystemPrompt());
        }
    }

    /**
     * Add conversation history messages to the messages array.
     *
     * @param service The visiting AI service.
     * @param messages The messages array builder.
     * @param input The chat input.
     * @since 1.2
     */
    protected void buildChatPayloadHistoryMessages(AIService service, JsonArrayBuilder messages, ChatInput input) {
        for (var historyMessage : input.getHistory()) {
            messages.add(
                Json.createObjectBuilder()
                    .add("role", historyMessage.role() == Role.USER ? "user" : "assistant")
                    .add(
                        "content", Json.createArrayBuilder()
                            .add(
                                Json.createObjectBuilder()
                                    .add("type", "text")
                                    .add("text", historyMessage.content())
                            )
                    )
            );
        }
    }

    /**
     * Add user content (images, files, and text message) to the messages array.
     *
     * @param service The visiting AI service.
     * @param messages The messages array builder.
     * @param input The chat input.
     * @param options The chat options.
     * @since 1.2
     */
    protected void buildChatPayloadUserContent(AIService service, JsonArrayBuilder messages, ChatInput input, ChatOptions options) {
        var content = Json.createArrayBuilder();

        for (var image : input.getImages()) {
            content.add(
                Json.createObjectBuilder()
                    .add("type", "image")
                    .add(
                        "source", Json.createObjectBuilder()
                            .add("type", "base64")
                            .add("media_type", image.mimeType().value())
                            .add("data", image.toBase64())
                    )
            );
        }

        if (!input.getFiles().isEmpty()) {
            checkSupportsFileAttachments(service);

            for (var file : input.getFiles()) {
                var fileId = service.upload(file, options);

                content.add(
                    Json.createObjectBuilder()
                        .add("type", "document")
                        .add(
                            "source", Json.createObjectBuilder()
                                .add("type", "file")
                                .add("file_id", fileId)
                        )
                );
            }
        }

        var text = Json.createObjectBuilder()
            .add("type", "text")
            .add("text", input.getMessage());

        if (options.hasMemory()) {
            // https://docs.claude.com/en/docs/build-with-claude/prompt-caching
            text.add("cache_control", Json.createObjectBuilder().add("type", "ephemeral"));
        }

        content.add(text);

        messages.add(
            Json.createObjectBuilder()
                .add("role", "user")
                .add("content", content)
        );
    }

    /**
     * Add generation config (streaming, temperature, topP, structured output) to the payload.
     *
     * @param service The visiting AI service.
     * @param payload The payload builder.
     * @param options The chat options.
     * @param streaming Whether streaming is enabled.
     * @since 1.2
     */
    protected void buildChatPayloadGenerationConfig(AIService service, JsonObjectBuilder payload, ChatOptions options, boolean streaming) {
        if (streaming) {
            checkSupportsStreaming(service);
            payload.add("stream", true);
        }

        if (usesAdaptiveThinking(service)) {
            buildAdaptiveThinkingConfig(service, payload, options);
        }
        else {
            buildLegacyThinkingConfig(service, payload, options);
        }

        if (options.getJsonSchema() != null) {
            checkSupportsStructuredOutput(service);
            payload.add(
                "output_format", Json.createObjectBuilder()
                    .add("type", "json_schema")
                    .add("schema", addStrictAdditionalProperties(options.getJsonSchema()))
            );
        }
    }

    /**
     * Whether the model uses adaptive thinking (with {@code output_config.effort}) instead of the legacy fixed {@code thinking.budget_tokens}. This is the same
     * model generation (Opus 4.7+, Sonnet 5+, Fable 5+) that dropped sampling parameters, so it keys off {@link AIService#supportsSamplingParameters()}: those
     * models reject both {@code thinking.type.enabled} and {@code temperature}. Older models (Opus 4.6, Sonnet 4.6 and below) keep the legacy behavior.
     *
     * @param service The visiting AI service.
     * @return Whether the model expects adaptive thinking rather than an explicit thinking budget.
     * @since 1.5
     */
    protected boolean usesAdaptiveThinking(AIService service) {
        return !service.supportsSamplingParameters();
    }

    /**
     * Adds adaptive thinking config for models that reject {@code thinking.budget_tokens}. Maps the requested reasoning effort to {@code output_config.effort};
     * {@link ReasoningEffort#NONE} disables thinking, {@link ReasoningEffort#AUTO} omits the field so the model applies its own default. Sampling parameters
     * are intentionally not emitted, as these models reject them.
     *
     * @param service The visiting AI service.
     * @param payload The payload builder.
     * @param options The chat options.
     * @since 1.5
     */
    protected void buildAdaptiveThinkingConfig(AIService service, JsonObjectBuilder payload, ChatOptions options) {
        var effort = getEffectiveReasoningEffort(service, options);

        switch (effort) {
            case NONE -> payload.add("thinking", Json.createObjectBuilder().add("type", "disabled"));
            case LOW, MEDIUM, HIGH, XHIGH -> {
                payload.add("thinking", Json.createObjectBuilder().add("type", "adaptive"));
                payload.add("output_config", Json.createObjectBuilder().add("effort", effort.name().toLowerCase(Locale.ROOT)));
            }
            default -> {
                /* AUTO, and any level added later: omit thinking, so that the model applies its own adaptive default. */ }
        }
    }

    /**
     * Adds legacy thinking config for models that still accept {@code thinking.budget_tokens} (Opus 4.6, Sonnet 4.6 and below). Falls back to sampling
     * parameters ({@code temperature}, {@code top_p}) when no thinking budget applies; these models accept them.
     *
     * @param service The visiting AI service.
     * @param payload The payload builder.
     * @param options The chat options.
     * @since 1.5
     */
    protected void buildLegacyThinkingConfig(AIService service, JsonObjectBuilder payload, ChatOptions options) {
        var thinkingBudget = resolveThinkingBudget(service, options);

        if (thinkingBudget > 0) {
            payload.add(
                "thinking", Json.createObjectBuilder()
                    .add("type", "enabled")
                    .add("budget_tokens", thinkingBudget)
            );
        }
        else {
            payload.add("temperature", options.getTemperature());

            if (options.getTopP() != 1.0) {
                payload.add("top_p", options.getTopP());
            }
        }
    }

    /**
     * Returns the reasoning effort the server will effectively apply, after accounting for model capability. Anthropic natively supports all levels (including
     * {@code xhigh}), so no capping is needed; an unsupported model resolves to {@link ReasoningEffort#NONE}.
     *
     * @param service The visiting AI service.
     * @param options The chat options.
     * @return The reasoning effort the server will effectively apply; never {@code null}.
     * @since 1.5
     */
    protected ReasoningEffort getEffectiveReasoningEffort(AIService service, ChatOptions options) {
        return service.supportsReasoningEffort() ? options.getReasoningEffort() : ReasoningEffort.NONE;
    }

    /**
     * Computes the {@code max_tokens} value to send to the Anthropic API. Applies the model-tier default when the caller did not set one.
     *
     * @param service The visiting AI service.
     * @param options The chat options.
     * @return The effective {@code max_tokens} to send.
     * @since 1.4
     */
    protected int computeEffectiveMaxTokens(AIService service, ChatOptions options) {
        return ofNullable(options.getMaxTokens())
            .orElseGet(() -> service.getModelVersion().lte(CLAUDE_3) ? DEFAULT_MAX_TOKENS_CLAUDE_3_0 : DEFAULT_MAX_TOKENS_CLAUDE_3_X);
    }

    /**
     * Resolves the effective thinking budget for the given service and options as a fraction of {@link #computeEffectiveMaxTokens(AIService, ChatOptions)},
     * using the ratio returned by {@link #toBudgetRatio(ReasoningEffort)}. Returns {@code 0} when the service does not
     * {@link AIService#supportsReasoningEffort() support reasoning effort} or when the requested effort maps to no thinking.
     *
     * @param service The visiting AI service.
     * @param options The chat options.
     * @return The effective {@code budget_tokens}, or {@code 0} when thinking should not be emitted.
     * @since 1.4
     */
    protected int resolveThinkingBudget(AIService service, ChatOptions options) {
        return (int) (computeEffectiveMaxTokens(service, options) * toBudgetRatio(getEffectiveReasoningEffort(service, options)));
    }

    /**
     * Returns the fraction of {@code max_tokens} to allocate as thinking budget for the given reasoning effort.
     * <ul>
     * <li>{@link ReasoningEffort#AUTO} / {@link ReasoningEffort#NONE}: {@code 0.0} (no thinking)</li>
     * <li>{@link ReasoningEffort#LOW}: {@code 0.20}</li>
     * <li>{@link ReasoningEffort#MEDIUM}: {@code 0.50}</li>
     * <li>{@link ReasoningEffort#HIGH}: {@code 0.80}</li>
     * <li>{@link ReasoningEffort#XHIGH}: {@code 0.95}</li>
     * </ul>
     *
     * @param effort The reasoning effort.
     * @return The fraction of {@code max_tokens} to use for thinking; between {@code 0.0} and {@code 1.0}.
     * @since 1.4
     */
    protected double toBudgetRatio(ReasoningEffort effort) {
        return switch (effort) {
            case AUTO, NONE -> 0.0;
            case LOW -> 0.20;
            case MEDIUM -> 0.50;
            case HIGH -> 0.80;
            case XHIGH -> 0.95;
        };
    }

    @Override
    public List<String> getChatResponseContentPaths() {
        return List.of("content[*].text");
    }

    @Override
    public List<String> getChatUsageInputTokensPaths() {
        return List.of("usage.input_tokens");
    }

    @Override
    public List<String> getChatUsageOutputTokensPaths() {
        return List.of("usage.output_tokens");
    }

    @Override
    public List<String> getChatUsageCachedInputTokensPaths() {
        return List.of("usage.cache_read_input_tokens");
    }

    /**
     * An override which joins potentially multiple chat response parts into a single string.
     */
    @Override
    public String parseChatResponse(JsonObject responseJson) throws AIResponseException {
        if (findAllByPath(responseJson, "content[*].type").contains("server_tool_use")) { // Not just content[0]; newer models may lead with a thinking block.
            checkErrors(responseJson, getTextResponseErrorMessagePaths());
            var messageContentPaths = getChatResponseContentPaths();

            if (messageContentPaths.isEmpty()) {
                throw new IllegalStateException("getChatResponseContentPaths() may not return an empty list");
            }

            return findAllByPath(responseJson, getChatResponseContentPaths().get(0)).stream().collect(joining());
        }
        else {
            return super.parseChatResponse(responseJson);
        }
    }

    @Override
    public boolean processChatStreamEvent(AIService service, ChatOptions options, Event event, Consumer<String> onToken) {
        if (event.type() == EVENT) {
            return processEventName(event);
        }

        if (event.type() == DATA) {
            return tryParseEventDataJson(event.value(), json -> processEventData(options, event, json, onToken));
        }

        return true;
    }

    /**
     * Answers whether the stream continues after the given named event, throwing when it reports the token limit.
     */
    private static boolean processEventName(Event event) {
        if ("max_tokens".equals(event.value())) {
            throw new AITokenLimitExceededException();
        }

        return !"message_stop".equals(event.value()) && !"content_block_stop".equals(event.value());
    }

    /**
     * Emits the token carried by the given data event, or records the usage it carries, answering whether the stream continues.
     */
    private boolean processEventData(ChatOptions options, Event event, JsonObject json, Consumer<String> onToken) {
        var type = json.getString("type", null);

        if ("content_block_delta".equals(type)) {
            var token = json.getJsonObject("delta").getString("text", "");

            if (!token.isEmpty()) { // Do not use isBlank! Whitespace can be a valid token.
                onToken.accept(token);
            }
        }
        else if (!options.isDefault()) {
            recordUsage(options, type, json);
        }
        else if ("error".equals(type)) {
            throw new AIResponseException("Error event returned", event.value());
        }

        return true;
    }

    /**
     * Records the usage carried by a message start or message delta event, the latter of which reports output tokens alone and therefore carries the input
     * counts of the prior record forward.
     */
    private void recordUsage(ChatOptions options, String type, JsonObject json) {
        if ("message_start".equals(type)) {
            options.recordUsage(parseChatUsage(json.getJsonObject("message")));
        }
        else if ("message_delta".equals(type)) {
            findFirstByPath(json, getChatUsageOutputTokensPaths().get(0)).ifPresent(outputTokens -> {
                var prior = options.getLastUsage();
                options.recordUsage(
                    new ChatUsage(
                        prior != null ? prior.inputTokens() : -1,
                        Integer.parseInt(outputTokens),
                        -1,
                        prior != null ? prior.cachedInputTokens() : -1
                    )
                );
            });
        }
    }

}
