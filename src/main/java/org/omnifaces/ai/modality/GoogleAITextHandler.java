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

import static java.lang.String.format;
import static org.omnifaces.ai.helper.JsonHelper.findByPath;
import static org.omnifaces.ai.helper.TextHelper.isBlank;
import static org.omnifaces.ai.model.AnalyzeVideoOptions.DEFAULT_FPS;
import static org.omnifaces.ai.model.Sse.Event.Type.DATA;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AITokenLimitExceededException;
import org.omnifaces.ai.model.AnalyzeVideoOptions;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.ReasoningEffort;
import org.omnifaces.ai.model.ChatUsage;
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
        var contents = Json.createArrayBuilder();
        buildChatPayloadTools(service, payload, options);
        // Google API doesn't support user_location in payload.
        buildChatPayloadSystemPrompt(service, payload, appendWebSearchLocationToPromptIfNecessary(options));
        buildChatPayloadHistoryMessages(service, contents, input);
        buildChatPayloadUserContent(service, contents, input, options);
        payload.add("contents", contents);
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
     * @see <a href="https://ai.google.dev/gemini-api/docs/google-search">Web Search Tool Reference</a>
     */
    protected void buildChatPayloadTools(AIService service, JsonObjectBuilder payload, ChatOptions options) {
        if (options.useWebSearch()) {
            payload.add(
                "tools", Json.createArrayBuilder()
                    .add(
                        Json.createObjectBuilder()
                            .add("google_search", Json.createObjectBuilder().build()).build()
                    )
            );
        }
    }

    /**
     * Add system prompt to the payload as a {@code system_instruction} field.
     *
     * @param service The visiting AI service.
     * @param payload The payload builder.
     * @param options The chat options.
     * @since 1.2
     */
    protected void buildChatPayloadSystemPrompt(AIService service, JsonObjectBuilder payload, ChatOptions options) {
        if (!isBlank(options.getSystemPrompt())) {
            payload.add(
                "system_instruction", Json.createObjectBuilder()
                    .add(
                        "parts", Json.createArrayBuilder()
                            .add(
                                Json.createObjectBuilder()
                                    .add("text", options.getSystemPrompt())
                            )
                    )
            );
        }
    }

    /**
     * Add conversation history messages to the contents array.
     *
     * @param service The visiting AI service.
     * @param contents The contents array builder.
     * @param input The chat input.
     * @since 1.2
     */
    protected void buildChatPayloadHistoryMessages(AIService service, JsonArrayBuilder contents, ChatInput input) {
        for (var historyMessage : input.getHistory()) {
            var parts = Json.createArrayBuilder();

            for (var uploadedFile : historyMessage.uploadedFiles()) {
                var part = Json.createObjectBuilder()
                    .add(
                        "file_data", Json.createObjectBuilder()
                            .add("mime_type", uploadedFile.mimeType().value())
                            .add("file_uri", uploadedFile.id())
                    );

                buildVideoMetadata(uploadedFile.videoOptions()).ifPresent(videoMetadata -> part.add("video_metadata", videoMetadata));
                parts.add(part);
            }

            parts.add(
                Json.createObjectBuilder()
                    .add("text", historyMessage.content())
            );

            contents.add(
                Json.createObjectBuilder()
                    .add("role", historyMessage.role() == Role.USER ? "user" : "model")
                    .add("parts", parts)
            );
        }
    }

    /**
     * Add user content (images, files, and text message) to the contents array.
     *
     * @param service The visiting AI service.
     * @param contents The contents array builder.
     * @param input The chat input.
     * @param options The chat options.
     * @since 1.2
     */
    protected void buildChatPayloadUserContent(AIService service, JsonArrayBuilder contents, ChatInput input, ChatOptions options) {
        var parts = Json.createArrayBuilder();

        for (var image : input.getImages()) {
            parts.add(
                Json.createObjectBuilder()
                    .add(
                        "inline_data", Json.createObjectBuilder()
                            .add("mime_type", image.mimeType().value())
                            .add("data", image.toBase64())
                    )
            );
        }

        if (!input.getFiles().isEmpty()) {
            checkSupportsFileAttachments(service);

            for (var file : input.getFiles()) {
                var fileId = service.upload(file, options);
                var part = Json.createObjectBuilder()
                    .add(
                        "file_data", Json.createObjectBuilder()
                            .add("mime_type", file.mimeType().value())
                            .add("file_uri", fileId)
                    );

                buildVideoMetadata(file).ifPresent(videoMetadata -> part.add("video_metadata", videoMetadata));
                parts.add(part);
            }
        }

        parts.add(
            Json.createObjectBuilder()
                .add("text", input.getMessage())
        );

        contents.add(
            Json.createObjectBuilder()
                .add("role", "user")
                .add("parts", parts)
        );
    }

    /**
     * Builds the video metadata of the given video attachment, which instructs at which rate to sample frames and which clip of the video to analyze.
     *
     * @param file The file attachment to build the video metadata for.
     * @return The video metadata, or empty if the attachment carries no video options.
     * @since 1.7
     * @see <a href="https://ai.google.dev/gemini-api/docs/video-understanding">API Reference</a>
     */
    protected Optional<JsonObjectBuilder> buildVideoMetadata(Attachment file) {
        return buildVideoMetadata(file.videoOptions());
    }

    /**
     * Builds the video metadata of the given video analysis options, which instructs at which rate to sample frames and which clip of the video to analyze.
     *
     * @param videoOptions The video analysis options, which may be {@code null}.
     * @return The video metadata, or empty if there are no options to state.
     * @since 1.7
     */
    protected Optional<JsonObjectBuilder> buildVideoMetadata(AnalyzeVideoOptions videoOptions) {
        if (videoOptions == null || videoOptions.isDefault()) {
            return Optional.empty();
        }

        var videoMetadata = Json.createObjectBuilder();

        if (videoOptions.getFps() != DEFAULT_FPS) {
            videoMetadata.add("fps", videoOptions.getFps());
        }

        if (videoOptions.getStartOffset() != null) {
            videoMetadata.add("start_offset", toOffset(videoOptions.getStartOffset()));
        }

        if (videoOptions.getEndOffset() != null) {
            videoMetadata.add("end_offset", toOffset(videoOptions.getEndOffset()));
        }

        return Optional.of(videoMetadata);
    }

    private static String toOffset(Duration offset) {
        var millis = offset.toMillisPart();
        return millis == 0 ? (offset.toSeconds() + "s") : format("%d.%03ds", offset.toSeconds(), millis);
    }

    /**
     * Add generation config (temperature, maxTokens, topP, structured output) to the payload.
     *
     * @param service The visiting AI service.
     * @param payload The payload builder.
     * @param options The chat options.
     * @param streaming Whether streaming is enabled (unused for Google AI, streaming is URL-based).
     * @since 1.2
     */
    protected void buildChatPayloadGenerationConfig(AIService service, JsonObjectBuilder payload, ChatOptions options, boolean streaming) {
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

        var effort = getEffectiveReasoningEffort(service, options);

        if (effort != ReasoningEffort.AUTO) {
            generationConfig.add("thinkingConfig", Json.createObjectBuilder().add("thinkingLevel", effort.name().toLowerCase()));
        }

        payload.add("generationConfig", generationConfig);
    }

    /**
     * Returns the reasoning effort the server will actually apply, after accounting for model capability and implicit defaults. As of now, Gemini 3+ only
     * supports AUTO, LOW, MEDIUM, HIGH.
     *
     * @param service The visiting AI service.
     * @param options The chat options.
     * @return The reasoning effort the server will effectively apply; never {@code null}.
     * @since 1.4
     * @see <a href="https://ai.google.dev/gemini-api/docs/thinking">Gemini Thinking API Reference</a>
     */
    protected ReasoningEffort getEffectiveReasoningEffort(AIService service, ChatOptions options) {
        if (!service.supportsReasoningEffort()) {
            return ReasoningEffort.AUTO;
        }

        return switch (options.getReasoningEffort()) {
            case NONE -> ReasoningEffort.AUTO;
            case XHIGH -> ReasoningEffort.HIGH;
            default -> options.getReasoningEffort();
        };
    }

    @Override
    public List<String> getChatResponseContentPaths() {
        return List.of("candidates[0].content.parts[0].text");
    }

    @Override
    public List<String> getChatUsageInputTokensPaths() {
        return List.of("usageMetadata.promptTokenCount");
    }

    @Override
    public List<String> getChatUsageOutputTokensPaths() {
        return List.of("usageMetadata.candidatesTokenCount");
    }

    @Override
    public List<String> getChatUsageReasoningTokensPaths() {
        return List.of("usageMetadata.thoughtsTokenCount");
    }

    @Override
    public List<String> getChatUsageCachedInputTokensPaths() {
        return List.of("usageMetadata.cachedContentTokenCount");
    }

    @Override
    public ChatUsage parseChatUsage(JsonObject responseJson) {
        var usage = super.parseChatUsage(responseJson);

        if (usage == null) {
            return null;
        }

        // In contrary to e.g. OpenAI, Google AI doesn't include reasoning tokens in output (candidates) tokens, so we need to recalculate.
        var reasoningTokens = usage.reasoningTokens() == -1 ? 0 : usage.reasoningTokens();
        var adjustedOutput = usage.outputTokens() == -1 ? reasoningTokens : usage.outputTokens() + reasoningTokens;
        return new ChatUsage(usage.inputTokens(), adjustedOutput, reasoningTokens, usage.cachedInputTokens());
    }

    @Override
    public List<String> getFileResponseIdPaths() {
        return List.of("file.uri");
    }

    @Override
    public boolean processChatStreamEvent(AIService service, ChatOptions options, Event event, Consumer<String> onToken) {
        if (event.type() == DATA) {
            return tryParseEventDataJson(event.value(), json -> {
                findByPath(json, "candidates[0].content.parts[0].text").ifPresent(onToken);

                if (!options.isDefault() && json.containsKey("usageMetadata")) {
                    options.recordUsage(parseChatUsage(json));
                }
                var finishReason = findByPath(json, "candidates[0].finishReason");

                if (finishReason.filter("STOP"::equals).isPresent()) {
                    return false;
                }

                finishReason.filter("MAX_TOKENS"::equals).ifPresent(__ -> {
                    throw new AITokenLimitExceededException();
                });
                return true;
            });
        }

        return true;
    }

}
