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

import static org.omnifaces.ai.helper.JsonHelper.addStrictAdditionalProperties;
import static org.omnifaces.ai.helper.JsonHelper.findFirstByPath;
import static org.omnifaces.ai.helper.TextHelper.isBlank;
import static org.omnifaces.ai.model.Sse.Event.Type.DATA;
import static org.omnifaces.ai.model.Sse.Event.Type.EVENT;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.exception.AITokenLimitExceededException;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.Location;
import org.omnifaces.ai.model.ChatOptions.ReasoningEffort;
import org.omnifaces.ai.model.Sse.Event;
import org.omnifaces.ai.service.OpenAIService;

/**
 * Default text handler for OpenAI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see OpenAIService
 */
public class OpenAITextHandler extends DefaultAITextHandler {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new instance of this AI handler.
     */
    public OpenAITextHandler() {
        //
    }

    private static final AIModelVersion GPT_5 = AIModelVersion.of("gpt", 5);
    private static final AIModelVersion GPT_5_1 = AIModelVersion.of("gpt", 5, 1);

    /**
     * @see <a href="https://developers.openai.com/api/reference/chat-completions/overview">Chat Completions API Reference</a>
     * @see <a href="https://developers.openai.com/api/reference/resources/responses/methods/create">Responses API Reference</a>
     */
    @Override
    public JsonObject buildChatPayload(AIService service, ChatInput input, ChatOptions options, boolean streaming) {
        var supportsResponsesApi = supportsResponsesApi(service);
        var systemPromptOptions = supportsWebSearchUserLocation(service) ? options : appendWebSearchLocationToPromptIfNecessary(options);
        var messages = Json.createArrayBuilder();
        var payload = Json.createObjectBuilder()
            .add("model", service.getModelName());

        if (supportsResponsesApi) {
            buildChatPayloadToolsWithResponsesApi(service, payload, options);
            buildChatPayloadSystemPromptWithResponsesApi(service, payload, systemPromptOptions);
            buildChatPayloadHistoryMessagesWithResponsesApi(service, messages, input);
            buildChatPayloadUserContentWithResponsesApi(service, messages, input, options);
            payload.add("input", messages);
            buildChatPayloadGenerationConfigWithResponsesApi(service, payload, options, streaming);
        }
        else {
            if (options.useWebSearch()) {
                checkSupportsWebSearch(service);
            }

            buildChatPayloadSystemPromptWithChatCompletionsApi(service, messages, systemPromptOptions);
            buildChatPayloadHistoryMessagesWithChatCompletionsApi(service, messages, input);
            buildChatPayloadUserContentWithChatCompletionsApi(service, messages, input, options);
            payload.add("messages", messages);
            buildChatPayloadGenerationConfigWithChatCompletionsApi(service, payload, options, streaming);
        }

        return payload.build();
    }

    /**
     * Add tools to payload as top-level {@code tools} field for Responses API.
     *
     * @param service The visiting AI service.
     * @param payload The payload builder.
     * @param options The chat options.
     * @since 1.3
     * @see <a href="https://developers.openai.com/api/docs/guides/tools-web-search/">Web Search Tool Reference</a>
     */
    protected void buildChatPayloadToolsWithResponsesApi(AIService service, JsonObjectBuilder payload, ChatOptions options) {
        if (options.useWebSearch()) {
            var webSearchTool = Json.createObjectBuilder().add("type", getWebSearchToolName());
            var userLocation = buildUserLocation(options.getWebSearchLocation());

            if (userLocation != null) {
                webSearchTool.add("user_location", userLocation);
            }

            if (supportsToolChoiceRequired(service)) {
                payload.add("tool_choice", "required");
            }

            payload.add("tools", Json.createArrayBuilder().add(webSearchTool.build()));
        }
    }

    /**
     * Add system prompt to payload as top-level {@code instructions} field for Responses API.
     *
     * @param service The visiting AI service.
     * @param payload The payload builder.
     * @param options The chat options.
     * @since 1.2
     */
    protected void buildChatPayloadSystemPromptWithResponsesApi(AIService service, JsonObjectBuilder payload, ChatOptions options) {
        if (!isBlank(options.getSystemPrompt())) {
            payload.add("instructions", options.getSystemPrompt());
        }
    }

    /**
     * Add system prompt to messages array as {@code system} role for Chat Completions API.
     *
     * @param service The visiting AI service.
     * @param messages The messages array builder.
     * @param options The chat options.
     * @since 1.2
     */
    protected void buildChatPayloadSystemPromptWithChatCompletionsApi(AIService service, JsonArrayBuilder messages, ChatOptions options) {
        if (!isBlank(options.getSystemPrompt())) {
            messages.add(
                Json.createObjectBuilder()
                    .add("role", "system")
                    .add("content", options.getSystemPrompt())
            );
        }
    }

    /**
     * Add conversation history messages to the input array for Responses API.
     *
     * @param service The visiting AI service.
     * @param messages The messages array builder.
     * @param input The chat input.
     * @since 1.2
     */
    protected void buildChatPayloadHistoryMessagesWithResponsesApi(AIService service, JsonArrayBuilder messages, ChatInput input) {
        addHistoryMessages(service, messages, input, true);
    }

    /**
     * Add conversation history messages to the messages array for Chat Completions API.
     *
     * @param service The visiting AI service.
     * @param messages The messages array builder.
     * @param input The chat input.
     * @since 1.2
     */
    protected void buildChatPayloadHistoryMessagesWithChatCompletionsApi(AIService service, JsonArrayBuilder messages, ChatInput input) {
        addHistoryMessages(service, messages, input, false);
    }

    private void addHistoryMessages(AIService service, JsonArrayBuilder messages, ChatInput input, boolean supportsResponsesApi) {
        for (var historyMessage : input.getHistory()) {
            if (supportsFilesApi(service)) {
                var content = Json.createArrayBuilder();
                for (var uploadedFile : historyMessage.uploadedFiles()) {
                    addUploadedFileContent(service, content, uploadedFile.id(), supportsResponsesApi);
                }
                content.add(
                    Json.createObjectBuilder()
                        .add("type", supportsResponsesApi ? (historyMessage.role() == Role.USER) ? "input_text" : "output_text" : "text")
                        .add("text", historyMessage.content())
                );
                messages.add(
                    Json.createObjectBuilder()
                        .add("role", historyMessage.role() == Role.USER ? "user" : "assistant")
                        .add("content", content)
                );
            }
            else {
                messages.add(
                    Json.createObjectBuilder()
                        .add("role", historyMessage.role() == Role.USER ? "user" : "assistant")
                        .add("content", historyMessage.content())
                );
            }
        }
    }

    /**
     * Adds a content block referencing an already-uploaded file by id. Providers that do not accept a bare {@code file_id} content block can override this to
     * emit their own shape (e.g. a signed {@code document_url}).
     *
     * @param service The visiting AI service.
     * @param content The content array builder to add the block to.
     * @param fileId The uploaded file id.
     * @param supportsResponsesApi Whether the Responses API is active.
     * @since 1.5
     */
    protected void addUploadedFileContent(AIService service, JsonArrayBuilder content, String fileId, boolean supportsResponsesApi) {
        content.add(
            Json.createObjectBuilder()
                .add("type", supportsResponsesApi ? "input_file" : "file")
                .add("file_id", fileId)
        );
    }

    /**
     * Add the content part of a file attachment which is inlined in the payload rather than uploaded to a files API.
     *
     * @param service The visiting AI service.
     * @param content The content array builder.
     * @param file The file attachment to inline.
     * @param supportsResponsesApi Whether the Responses API is used.
     * @since 1.7
     */
    protected void addInlineFileContent(AIService service, JsonArrayBuilder content, Attachment file, boolean supportsResponsesApi) {
        if (supportsResponsesApi) {
            content.add(
                Json.createObjectBuilder()
                    .add("type", "input_file")
                    .add("filename", file.fileName())
                    .add("file_data", file.toDataUri())
            );
        }
        else {
            content.add(
                Json.createObjectBuilder()
                    .add("type", "file")
                    .add(
                        "file", Json.createObjectBuilder()
                            .add("filename", file.fileName())
                            .add("file_data", file.toDataUri())
                    )
            );
        }
    }

    /**
     * Add user content (images, audio files, other files, and text message) to the input array for Responses API.
     *
     * @param service The visiting AI service.
     * @param messages The messages array builder.
     * @param input The chat input.
     * @param options The chat options.
     * @since 1.2
     */
    protected void buildChatPayloadUserContentWithResponsesApi(AIService service, JsonArrayBuilder messages, ChatInput input, ChatOptions options) {
        addUserContent(service, messages, input, options, file -> getFileUploadMetadata(service, file), true);
    }

    /**
     * Add user content (images, audio files, other files, and text message) to the messages array for Chat Completions API.
     *
     * @param service The visiting AI service.
     * @param messages The messages array builder.
     * @param input The chat input.
     * @param options The chat options.
     * @since 1.2
     */
    protected void buildChatPayloadUserContentWithChatCompletionsApi(AIService service, JsonArrayBuilder messages, ChatInput input, ChatOptions options) {
        addUserContent(service, messages, input, options, file -> getFileUploadMetadata(service, file), false);
    }

    private void addUserContent(
        AIService service, JsonArrayBuilder messages, ChatInput input, ChatOptions options, Function<Attachment, Map<String, String>> getFileUploadMetadata,
        boolean supportsResponsesApi
    )
    {
        var audioFiles = input.getFiles().stream().filter(attachment -> attachment.mimeType().isAudio()).toList();
        var remainingFiles = input.getFiles().stream().filter(attachment -> !attachment.mimeType().isAudio()).toList();
        var content = Json.createArrayBuilder();

        for (var image : input.getImages()) {
            var img = Json.createObjectBuilder().add("type", supportsResponsesApi ? "input_image" : "image_url");

            if (supportsResponsesApi) {
                img.add("image_url", image.toDataUri());
            }
            else {
                img.add("image_url", Json.createObjectBuilder().add("url", image.toDataUri()));
            }

            content.add(img);
        }

        for (var audioFile : audioFiles) {
            content.add(
                Json.createObjectBuilder().add("type", "input_audio")
                    .add(
                        "input_audio", Json.createObjectBuilder()
                            .add(supportsResponsesApi ? "audio_base64" : "data", audioFile.toBase64())
                            .add("format", audioFile.mimeType().extension())
                    )
            );
        }

        if (!remainingFiles.isEmpty()) {
            checkSupportsFileAttachments(service);

            for (var file : remainingFiles) {
                if (supportsFilesApi(service)) {
                    var fileId = service.upload(file.withMetadata(getFileUploadMetadata.apply(file)), options);
                    addUploadedFileContent(service, content, fileId, supportsResponsesApi);
                }
                else {
                    addInlineFileContent(service, content, file, supportsResponsesApi);
                }
            }
        }

        content.add(
            Json.createObjectBuilder()
                .add("type", supportsResponsesApi ? "input_text" : "text")
                .add("text", input.getMessage())
        );

        messages.add(
            Json.createObjectBuilder()
                .add("role", "user")
                .add("content", content)
        );
    }

    /**
     * Add generation config (max tokens, streaming, temperature, topP, structured output) to the payload for Responses API.
     *
     * @param service The visiting AI service.
     * @param payload The payload builder.
     * @param options The chat options.
     * @param streaming Whether streaming is enabled.
     * @since 1.2
     */
    protected void buildChatPayloadGenerationConfigWithResponsesApi(AIService service, JsonObjectBuilder payload, ChatOptions options, boolean streaming) {
        addGenerationConfig(service, payload, options, streaming, true);
    }

    /**
     * Add generation config (max tokens, streaming, temperature, topP, structured output) to the payload for Chat Completions API.
     *
     * @param service The visiting AI service.
     * @param payload The payload builder.
     * @param options The chat options.
     * @param streaming Whether streaming is enabled.
     * @since 1.2
     */
    protected void buildChatPayloadGenerationConfigWithChatCompletionsApi(
        AIService service, JsonObjectBuilder payload, ChatOptions options, boolean streaming
    )
    {
        addGenerationConfig(service, payload, options, streaming, false);
    }

    private void addGenerationConfig(
        AIService service, JsonObjectBuilder payload, ChatOptions options, boolean streaming, boolean supportsResponsesApi
    )
    {
        if (options.getMaxTokens() != null) {
            if (supportsResponsesApi) {
                payload.add("max_output_tokens", options.getMaxTokens());
            }
            else {
                var maxTokensField = service.getModelVersion().gte(GPT_5) ? "max_completion_tokens" : "max_tokens";
                payload.add(maxTokensField, options.getMaxTokens());
            }
        }

        if (streaming) {
            checkSupportsStreaming(service);
            payload.add("stream", true);

            if (!supportsResponsesApi) {
                payload.add("stream_options", Json.createObjectBuilder().add("include_usage", true));
            }
        }

        var effectiveReasoningEffort = getEffectiveReasoningEffort(service, options);

        if (service.supportsReasoningEffort() && effectiveReasoningEffort != ReasoningEffort.AUTO) {
            addReasoningEffort(service, payload, effectiveReasoningEffort, supportsResponsesApi);
        }

        if (effectiveReasoningEffort == ReasoningEffort.NONE || effectiveReasoningEffort == ReasoningEffort.AUTO) {
            payload.add("temperature", options.getTemperature());
        }

        if (options.getTopP() != 1.0) {
            payload.add("top_p", options.getTopP());
        }

        if (options.getJsonSchema() != null) {
            checkSupportsStructuredOutput(service);

            if (supportsResponsesApi) {
                var strictSchema = Json.createObjectBuilder()
                    .add("name", "response_schema")
                    .add("strict", true)
                    .add("schema", addStrictAdditionalProperties(options.getJsonSchema()));

                var format = Json.createObjectBuilder().add("type", "json_schema");
                strictSchema.build().forEach(format::add);
                payload.add("text", Json.createObjectBuilder().add("format", format));
            }
            else {
                payload.add(
                    "response_format", Json.createObjectBuilder()
                        .add("type", "json_schema")
                        .add(
                            "json_schema", Json.createObjectBuilder()
                                .add("name", "response_schema")
                                .add("strict", true)
                                .add("schema", addStrictAdditionalProperties(options.getJsonSchema()))
                        )
                );
            }
        }
    }

    /**
     * Returns the reasoning effort the server will actually apply, after accounting for model capability and implicit defaults.
     *
     * @param service The visiting AI service.
     * @param options The chat options.
     * @return The reasoning effort the server will effectively apply; never {@code null}.
     * @since 1.4
     */
    protected ReasoningEffort getEffectiveReasoningEffort(AIService service, ChatOptions options) {
        if (!service.supportsReasoningEffort()) {
            return ReasoningEffort.NONE;
        }

        var effort = options.getReasoningEffort();

        if (effort == ReasoningEffort.XHIGH && !supportsReasoningEffortXhigh(service)) {
            return ReasoningEffort.HIGH;
        }

        if (supportsReasoningEffortNone(service)) {
            return effort == ReasoningEffort.AUTO ? ReasoningEffort.NONE : effort;
        }

        return switch (effort) {
            case AUTO -> ReasoningEffort.MEDIUM;
            case NONE -> ReasoningEffort.LOW;
            default -> effort;
        };
    }

    /**
     * Emits the reasoning effort on the payload in the correct shape for the active API variant: {@code reasoning.effort} for the Responses API, or
     * {@code reasoning_effort} top-level for the Chat Completions API.
     *
     * @param service The visiting AI service.
     * @param payload The payload builder.
     * @param effort The reasoning effort to emit (already known to be non-{@link ReasoningEffort#AUTO}).
     * @param supportsResponsesApi Whether the Responses API is active.
     * @since 1.4
     */
    protected void addReasoningEffort(AIService service, JsonObjectBuilder payload, ReasoningEffort effort, boolean supportsResponsesApi) {
        var value = effort.name().toLowerCase(Locale.ROOT);

        if (supportsResponsesApi) {
            payload.add("reasoning", Json.createObjectBuilder().add("effort", value));
        }
        else {
            payload.add("reasoning_effort", value);
        }
    }

    /**
     * Returns file upload metadata. This basically represents additional form data during file upload request.
     *
     * @param service The visiting AI service.
     * @param file The file to upload.
     * @return File upload metadata.
     */
    protected Map<String, String> getFileUploadMetadata(AIService service, Attachment file) {
        var purpose = service.getModelVersion().lt(GPT_5) ? "assistants" : "user_data";
        return Map.of("purpose", purpose, "expires_after[anchor]", "created_at", "expires_after[seconds]", String.valueOf(TimeUnit.DAYS.toSeconds(2)));
    }

    @Override
    public List<String> getChatResponseContentPaths() {
        return List.of("output[*].content[*].text", "choices[0].message.content"); // Responses API; Completions API
    }

    @Override
    public List<String> getChatUsageInputTokensPaths() {
        return List.of("usage.input_tokens", "usage.prompt_tokens"); // Responses API; Completions API
    }

    @Override
    public List<String> getChatUsageOutputTokensPaths() {
        return List.of("usage.output_tokens", "usage.completion_tokens"); // Responses API; Completions API
    }

    @Override
    public List<String> getChatUsageReasoningTokensPaths() {
        return List.of("usage.output_tokens_details.reasoning_tokens", "usage.completion_tokens_details.reasoning_tokens"); // Responses API; Completions API
    }

    @Override
    public List<String> getChatUsageCachedInputTokensPaths() {
        return List.of("usage.input_tokens_details.cached_tokens", "usage.prompt_tokens_details.cached_tokens"); // Responses API; Completions API
    }

    @Override
    public boolean processChatStreamEvent(AIService service, ChatOptions options, Event event, Consumer<String> onToken) {
        if (supportsResponsesApi(service)) {
            return processChatStreamEventWithResponsesApi(options, event, onToken);
        }
        else {
            return processChatStreamEventWithChatCompletionsApi(options, event, onToken);
        }
    }

    /**
     * Process chat stream event with {@code responses} API.
     *
     * @param options The chat options.
     * @param event Stream event.
     * @param onToken Callback receiving each stream data chunk (often one word/token/line).
     * @return {@code true} to continue processing the stream, or {@code false} when end of stream is reached.
     */
    protected boolean processChatStreamEventWithResponsesApi(ChatOptions options, Event event, Consumer<String> onToken) {
        if (event.type() == EVENT) {
            if ("response.completed".equals(event.value())) {
                return true;
            }

            if ("response.incomplete".equals(event.value())) {
                throw new AITokenLimitExceededException();
            }
        }
        else if (
            event.type() == DATA && (event.value().contains("response.output_text.delta") || event.value().contains("response.failed")
                || event.value().contains("response.completed"))
        ) { // Cheap pre-filter before expensive parse because OpenAI returns pretty a lot of events.
            return tryParseEventDataJson(event.value(), json -> {
                var type = json.getString("type", null);

                if ("response.output_text.delta".equals(type)) {
                    var token = json.getString("delta", "");

                    if (!token.isEmpty()) { // Do not use isBlank! Whitespace can be a valid token.
                        onToken.accept(token);
                    }
                }
                else if (!options.isDefault() && "response.completed".equals(type)) {
                    options.recordUsage(parseChatUsage(json.getJsonObject("response")));
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
     *
     * @param options The chat options.
     * @param event Stream event.
     * @param onToken Callback receiving each stream data chunk (often one word/token/line).
     * @return {@code true} to continue processing the stream, or {@code false} when end of stream is reached.
     */
    protected boolean processChatStreamEventWithChatCompletionsApi(ChatOptions options, Event event, Consumer<String> onToken) {
        if (event.type() == DATA) {
            if ("DONE".equalsIgnoreCase(event.value())) {
                return false;
            }
            else if (event.value().contains("chat.completion.chunk")) { // Cheap pre-filter before expensive parse.
                return tryParseEventDataJson(event.value(), json -> {
                    if ("chat.completion.chunk".equals(json.getString("object", null))) {
                        findFirstByPath(json, "choices[0].delta.content").ifPresent(onToken);
                        findFirstByPath(json, "choices[0].finish_reason").filter("length"::equals).ifPresent(__ -> {
                            throw new AITokenLimitExceededException();
                        });

                        if (!options.isDefault() && json.containsKey("usage")) {
                            options.recordUsage(parseChatUsage(json));
                        }
                    }

                    return true;
                });
            }
        }

        return true;
    }

    /**
     * Returns web search tool name.
     *
     * @return Web search tool name.
     * @since 1.3
     */
    protected String getWebSearchToolName() {
        return "web_search";
    }

    private static boolean supportsResponsesApi(AIService service) {
        return service instanceof OpenAIService openai && openai.supportsOpenAIResponsesApi();
    }

    private static boolean supportsFilesApi(AIService service) {
        return service instanceof OpenAIService openai && openai.supportsOpenAIFilesApi();
    }

    private static boolean supportsToolChoiceRequired(AIService service) {
        return service instanceof OpenAIService openai && openai.supportsOpenAIToolChoiceRequired();
    }

    private static boolean supportsWebSearchUserLocation(AIService service) {
        return service instanceof OpenAIService openai && openai.supportsOpenAIWebSearchUserLocation();
    }

    private static boolean supportsReasoningEffortNone(AIService service) {
        return service instanceof OpenAIService openai && openai.supportsOpenAIReasoningEffortNone();
    }

    private static boolean supportsReasoningEffortXhigh(AIService service) {
        return service instanceof OpenAIService && service.getModelVersion().gte(GPT_5_1)
            && service.getModelName().toLowerCase(Locale.ROOT).contains("codex-max");
    }

    /**
     * Builds an OpenAI-compatible {@code user_location} object from the given {@link Location}. Returns {@code null} if the location is
     * {@link Location#isGlobal() global}.
     *
     * @param location The location to build the user location object from.
     * @return An OpenAI-compatible {@code user_location} JSON object, or {@code null} if the location is global.
     * @since 1.3
     */
    static JsonObject buildUserLocation(Location location) {
        if (!location.isGlobal()) {
            var userLocation = Json.createObjectBuilder().add("type", "approximate");

            if (location.country() != null) {
                userLocation.add("country", location.country());
            }

            if (location.region() != null) {
                userLocation.add("region", location.region());
            }

            if (location.city() != null) {
                userLocation.add("city", location.city());
            }

            return userLocation.build();
        }

        return null;
    }

}
