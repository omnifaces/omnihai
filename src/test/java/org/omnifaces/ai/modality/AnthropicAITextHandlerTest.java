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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.omnifaces.ai.AIProvider.ANTHROPIC;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.exception.AITokenLimitExceededException;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatInput.UploadedFile;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.Location;
import org.omnifaces.ai.model.ChatOptions.ReasoningEffort;
import org.omnifaces.ai.model.Sse.Event;
import org.omnifaces.ai.model.Sse.Event.Type;

/**
 * Anthropic states the answer length as a required field, carries the conversation as typed content blocks, and configures thinking one of two ways depending
 * on whether the model still accepts a token budget. The stream reports the token counts across two separate events, the second of which states the output
 * count alone.
 */
class AnthropicAITextHandlerTest {

    private static final String CLAUDE_2 = "claude-2.1";
    private static final String CLAUDE_3 = "claude-3-haiku-20240307";
    private static final String CLAUDE_3_7 = "claude-3-7-sonnet-20250219";
    private static final String CLAUDE_SONNET_4_5 = "claude-sonnet-4-5";
    private static final String CLAUDE_OPUS_4_7 = "claude-opus-4-7";

    private final AnthropicAITextHandler handler = new AnthropicAITextHandler();

    // =================================================================================================================
    // The shape of the request
    // =================================================================================================================

    @Test
    void buildChatPayload_namesTheModelAndCarriesTheMessage() {
        var payload = payload(CLAUDE_SONNET_4_5, ChatInput.newBuilder().message("Hello").build(), ChatOptions.DEFAULT);

        assertEquals(CLAUDE_SONNET_4_5, payload.getString("model"));
        var content = payload.getJsonArray("messages").getJsonObject(0).getJsonArray("content");
        assertEquals("text", content.getJsonObject(0).getString("type"));
        assertEquals("Hello", content.getJsonObject(0).getString("text"));
        assertEquals("user", payload.getJsonArray("messages").getJsonObject(0).getString("role"));
    }

    /**
     * Anthropic rejects a request which does not state how long the answer may be, so a default per model tier stands in when the caller states none.
     */
    @Test
    void buildChatPayload_withoutAStatedLimit_appliesTheDefaultOfTheModelTier() {
        assertTrue(payload(CLAUDE_2, ChatOptions.DEFAULT).getInt("max_tokens") > 0);
        assertTrue(payload(CLAUDE_SONNET_4_5, ChatOptions.DEFAULT).getInt("max_tokens") > payload(CLAUDE_2, ChatOptions.DEFAULT).getInt("max_tokens"));
    }

    @Test
    void buildChatPayload_withAStatedLimit_carriesItAsItIs() {
        assertEquals(512, payload(CLAUDE_SONNET_4_5, ChatOptions.newBuilder().maxTokens(512).build()).getInt("max_tokens"));
    }

    @Test
    void buildChatPayload_withoutASystemPrompt_statesNone() {
        assertFalse(payload(CLAUDE_SONNET_4_5, ChatOptions.DEFAULT).containsKey("system"));
    }

    @Test
    void buildChatPayload_withASystemPrompt_carriesItAsATopLevelField() {
        var options = ChatOptions.newBuilder().systemPrompt("You are terse.").build();

        assertEquals("You are terse.", payload(CLAUDE_SONNET_4_5, options).getString("system"));
    }

    /**
     * The conversation so far is replayed as typed content blocks, with the assistant's turns marked as its own.
     */
    @Test
    void buildChatPayload_withHistory_replaysBothRoles() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "What is 2+2?");
        options.recordMessage(Role.ASSISTANT, "4");
        var input = ChatInput.newBuilder().message("And 3+3?").build().withHistory(options.getHistory());

        var messages = payload(CLAUDE_SONNET_4_5, input, options).getJsonArray("messages");

        assertEquals("user", messages.getJsonObject(0).getString("role"));
        assertEquals("What is 2+2?", messages.getJsonObject(0).getJsonArray("content").getJsonObject(0).getString("text"));
        assertEquals("assistant", messages.getJsonObject(1).getString("role"));
        assertEquals("4", messages.getJsonObject(1).getJsonArray("content").getJsonObject(0).getString("text"));
        assertEquals("And 3+3?", messages.getJsonObject(2).getJsonArray("content").getJsonObject(0).getString("text"));
    }

    /**
     * A file which an earlier turn uploaded is referenced again when that turn is replayed, as the provider keeps no state of the conversation itself.
     */
    @Test
    void buildChatPayload_withHistoryCarryingAnUploadedFile_replaysTheFileReference() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "Extract the contents of this PDF.");
        options.recordUploadedFile(new UploadedFile("file-1", MimeType.of("application/pdf")));
        options.recordMessage(Role.ASSISTANT, "Dummy PDF file");
        var input = ChatInput.newBuilder().message("How many pages does it have?").build().withHistory(options.getHistory());

        var content = payload(CLAUDE_SONNET_4_5, input, options).getJsonArray("messages").getJsonObject(0).getJsonArray("content");

        assertEquals("document", content.getJsonObject(0).getString("type"));
        assertEquals("file-1", content.getJsonObject(0).getJsonObject("source").getString("file_id"));
        assertEquals("Extract the contents of this PDF.", content.getJsonObject(1).getString("text"));
    }

    /**
     * An image travels inline as base64 next to the message it belongs to.
     */
    @Test
    void buildChatPayload_withAnImage_carriesItAsAnInlineSource() {
        var input = ChatInput.newBuilder().message("What is this?").attach(png()).build();

        var image = payload(CLAUDE_SONNET_4_5, input, ChatOptions.DEFAULT).getJsonArray("messages").getJsonObject(0).getJsonArray("content").getJsonObject(0);

        assertEquals("image", image.getString("type"));
        assertEquals("base64", image.getJsonObject("source").getString("type"));
        assertEquals("image/png", image.getJsonObject("source").getString("media_type"));
        assertFalse(image.getJsonObject("source").getString("data").isEmpty());
    }

    /**
     * A conversation replays the same prefix on every turn, so the message is marked for caching to keep the provider from charging for it again.
     */
    @Test
    void buildChatPayload_withMemory_marksTheMessageAsCacheable() {
        var options = ChatOptions.newBuilder().withMemory().build();

        var text = payload(CLAUDE_SONNET_4_5, ChatInput.newBuilder().message("Hello").build(), options)
            .getJsonArray("messages").getJsonObject(0).getJsonArray("content").getJsonObject(0);

        assertEquals("ephemeral", text.getJsonObject("cache_control").getString("type"));
    }

    @Test
    void buildChatPayload_streaming_statesIt() {
        assertTrue(handler.buildChatPayload(newService(CLAUDE_SONNET_4_5), input(), ChatOptions.DEFAULT, true).getBoolean("stream"));
    }

    @Test
    void buildChatPayload_streamingOnAModelWhichCannot_isRejected() {
        var service = newService(CLAUDE_2);
        var input = input();

        assertThrows(UnsupportedOperationException.class, () -> handler.buildChatPayload(service, input, ChatOptions.DEFAULT, true));
    }

    // =================================================================================================================
    // Web search
    // =================================================================================================================

    @Test
    void buildChatPayload_withWebSearch_offersTheToolOfTheModelGeneration() {
        var options = ChatOptions.newBuilder().webSearch(Location.GLOBAL).build();

        assertEquals("web_search_20250305", payload(CLAUDE_SONNET_4_5, options).getJsonArray("tools").getJsonObject(0).getString("type"));
        assertEquals("web_search", payload(CLAUDE_SONNET_4_5, options).getJsonArray("tools").getJsonObject(0).getString("name"));
    }

    @Test
    void buildChatPayload_withWebSearchOnANewerModel_offersTheNewerTool() {
        var options = ChatOptions.newBuilder().webSearch(Location.GLOBAL).build();

        assertEquals("web_search_20260209", payload(CLAUDE_OPUS_4_7, options).getJsonArray("tools").getJsonObject(0).getString("type"));
    }

    @Test
    void buildChatPayload_withWebSearchForALocation_statesTheLocation() {
        var options = ChatOptions.newBuilder().webSearch(new Location("US", null, "Miami")).build();

        assertEquals("Miami", payload(CLAUDE_SONNET_4_5, options).getJsonArray("tools").getJsonObject(0).getJsonObject("user_location").getString("city"));
    }

    // =================================================================================================================
    // Thinking
    // =================================================================================================================

    /**
     * The models which still accept a token budget get one as a fraction of the answer length, rising with the requested effort.
     */
    @Test
    void buildChatPayload_reasoningEffortOnAModelTakingABudget_statesTheBudget() {
        var low = payload(CLAUDE_3_7, ChatOptions.newBuilder().maxTokens(1000).reasoningEffort(ReasoningEffort.LOW).build());
        var high = payload(CLAUDE_3_7, ChatOptions.newBuilder().maxTokens(1000).reasoningEffort(ReasoningEffort.HIGH).build());

        assertEquals("enabled", low.getJsonObject("thinking").getString("type"));
        assertEquals(200, low.getJsonObject("thinking").getInt("budget_tokens"));
        assertEquals(800, high.getJsonObject("thinking").getInt("budget_tokens"));
    }

    /**
     * A budget of nothing is no thinking at all, and those models take the sampling parameters instead.
     */
    @Test
    void buildChatPayload_withoutThinking_statesTheSamplingParametersInstead() {
        var payload = payload(CLAUDE_3_7, ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.NONE).topP(0.5).build());

        assertFalse(payload.containsKey("thinking"));
        assertEquals(0.5, payload.getJsonNumber("top_p").doubleValue());
        assertTrue(payload.containsKey("temperature"));
    }

    @Test
    void buildChatPayload_withoutThinkingAndDefaultTopP_omitsIt() {
        assertFalse(payload(CLAUDE_3_7, ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.NONE).build()).containsKey("top_p"));
    }

    /**
     * The newest models reject a token budget and the sampling parameters alike, and take a named effort instead.
     */
    @Test
    void buildChatPayload_reasoningEffortOnAModelTakingAnEffort_statesTheEffort() {
        var payload = payload(CLAUDE_OPUS_4_7, ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.MEDIUM).build());

        assertEquals("adaptive", payload.getJsonObject("thinking").getString("type"));
        assertEquals("medium", payload.getJsonObject("output_config").getString("effort"));
        assertFalse(payload.containsKey("temperature"));
    }

    @Test
    void buildChatPayload_thinkingDisabledOnAModelTakingAnEffort_statesItDisabled() {
        var payload = payload(CLAUDE_OPUS_4_7, ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.NONE).build());

        assertEquals("disabled", payload.getJsonObject("thinking").getString("type"));
    }

    /**
     * An unstated effort leaves the model to its own default rather than choosing one on its behalf.
     */
    @Test
    void buildChatPayload_effortLeftToTheModel_statesNoThinkingAtAll() {
        assertFalse(payload(CLAUDE_OPUS_4_7, ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.AUTO).build()).containsKey("thinking"));
    }

    @Test
    void buildChatPayload_reasoningEffortOnAModelWhichCannotThink_statesNone() {
        assertFalse(payload(CLAUDE_3, ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.HIGH).build()).containsKey("thinking"));
    }

    // =================================================================================================================
    // Structured output
    // =================================================================================================================

    @Test
    void buildChatPayload_withAJsonSchema_statesTheOutputFormat() {
        var options = ChatOptions.newBuilder().jsonSchema(parseJson("{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"}}}")).build();

        var outputFormat = payload(CLAUDE_SONNET_4_5, options).getJsonObject("output_format");

        assertEquals("json_schema", outputFormat.getString("type"));
        assertFalse(outputFormat.getJsonObject("schema").getBoolean("additionalProperties"));
    }

    @Test
    void buildChatPayload_withAJsonSchemaOnAModelWhichCannot_isRejected() {
        var service = newService(CLAUDE_3);
        var input = input();
        var options = ChatOptions.newBuilder().jsonSchema(parseJson("{\"type\":\"object\"}")).build();

        assertThrows(UnsupportedOperationException.class, () -> handler.buildChatPayload(service, input, options, false));
    }

    // =================================================================================================================
    // The shape of the answer
    // =================================================================================================================

    @Test
    void parseChatResponse_plainAnswer_isTheText() {
        assertEquals("4", handler.parseChatResponse(parseJson("{\"content\":[{\"type\":\"text\",\"text\":\"4\"}]}")));
    }

    /**
     * A turn which searched the web carries the tool calls between the text blocks, and a newer model may lead with a thinking block, so every text block of
     * the turn is joined rather than the first one taken.
     */
    @Test
    void parseChatResponse_turnWhichUsedATool_joinsEveryTextBlock() {
        var response = parseJson("""
            {"content":[
              {"type":"thinking","thinking":"Let me look that up."},
              {"type":"server_tool_use","name":"web_search"},
              {"type":"text","text":"Miami is 82F"},
              {"type":"text","text":" and sunny."}]}
            """);

        assertEquals("Miami is 82F and sunny.", handler.parseChatResponse(response));
    }

    // =================================================================================================================
    // The stream
    // =================================================================================================================

    @Test
    void processChatStreamEvent_contentDelta_emitsTheToken() {
        var tokens = new ArrayList<String>();

        assertTrue(process(data("{\"type\":\"content_block_delta\",\"delta\":{\"text\":\"Hi\"}}"), ChatOptions.DEFAULT, tokens::add));
        assertEquals(java.util.List.of("Hi"), tokens);
    }

    @Test
    void processChatStreamEvent_emptyDelta_emitsNothing() {
        var tokens = new ArrayList<String>();

        process(data("{\"type\":\"content_block_delta\",\"delta\":{\"text\":\"\"}}"), ChatOptions.DEFAULT, tokens::add);

        assertTrue(tokens.isEmpty());
    }

    @Test
    void processChatStreamEvent_endOfMessage_endsTheStream() {
        assertFalse(process(new Event(Type.EVENT, "message_stop"), ChatOptions.DEFAULT, token -> {
            /* no token here */ }));
        assertFalse(process(new Event(Type.EVENT, "content_block_stop"), ChatOptions.DEFAULT, token -> {
            /* no token here */ }));
        assertTrue(process(new Event(Type.EVENT, "content_block_delta"), ChatOptions.DEFAULT, token -> {
            /* no token here */ }));
    }

    @Test
    void processChatStreamEvent_tokenLimitReached_throws() {
        var event = new Event(Type.EVENT, "max_tokens");

        assertThrows(AITokenLimitExceededException.class, () -> process(event, ChatOptions.DEFAULT, token -> {
            /* no token here */ }));
    }

    @Test
    void processChatStreamEvent_anythingElse_continuesTheStream() {
        assertTrue(process(new Event(Type.ID, "msg-1"), ChatOptions.DEFAULT, token -> {
            /* no token here */ }));
    }

    /**
     * The stream reports the input counts once at the start and the output count again at the end, so the later record carries the earlier counts forward
     * rather than reporting them as unknown.
     */
    @Test
    void processChatStreamEvent_usageEvents_carryTheInputCountsForward() {
        var options = ChatOptions.newBuilder().build();

        process(data("{\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":10,\"output_tokens\":1}}}"), options, token -> {
            /* none */ });
        process(data("{\"type\":\"message_delta\",\"usage\":{\"output_tokens\":42}}"), options, token -> {
            /* none */ });

        assertEquals(10, options.getLastUsage().inputTokens());
        assertEquals(42, options.getLastUsage().outputTokens());
    }

    private boolean process(Event event, ChatOptions options, java.util.function.Consumer<String> onToken) {
        return handler.processChatStreamEvent(newService(CLAUDE_SONNET_4_5), options, event, onToken);
    }

    private static Event data(String json) {
        return new Event(Type.DATA, json);
    }

    private JsonObject payload(String model, ChatOptions options) {
        return payload(model, input(), options);
    }

    private JsonObject payload(String model, ChatInput input, ChatOptions options) {
        return handler.buildChatPayload(newService(model), input, options, false);
    }

    private static ChatInput input() {
        return ChatInput.newBuilder().message("Hello").build();
    }

    private static byte[] png() {
        try {
            var output = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "PNG", output);
            return output.toByteArray();
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static AIService newService(String model) {
        return AIConfig.of(ANTHROPIC, "test-api-key").withModel(model).createService();
    }

    // =================================================================================================================
    // File attachments
    // =================================================================================================================

    /**
     * Anthropic references an uploaded document by the id the upload answered with, rather than carrying it inline again.
     */
    @Test
    void buildChatPayload_withAFile_referencesTheUploadedDocument() {
        var service = uploadingService();
        var input = ChatInput.newBuilder().message("Read it").attach(pdf()).build();

        var content = handler.buildChatPayload(service, input, ChatOptions.DEFAULT, false).getJsonArray("messages").getJsonObject(0)
            .getJsonArray("content");

        assertEquals("document", content.getJsonObject(0).getString("type"));
        assertEquals("file", content.getJsonObject(0).getJsonObject("source").getString("type"));
        assertEquals("file-1", content.getJsonObject(0).getJsonObject("source").getString("file_id"));
    }

    @Test
    void buildChatPayload_withAFileOnAModelWhichCannot_isRejected() {
        var service = newService(CLAUDE_2);
        var input = ChatInput.newBuilder().message("Read it").attach(pdf()).build();

        assertThrows(UnsupportedOperationException.class, () -> handler.buildChatPayload(service, input, ChatOptions.DEFAULT, false));
    }

    // =================================================================================================================
    // Thinking budget ratios
    // =================================================================================================================

    /**
     * The budget rises with the effort, and the two levels which mean no thinking allocate nothing.
     */
    @Test
    void toBudgetRatio_risesWithTheEffortAndIsZeroForNoThinking() {
        assertEquals(0.0, handler.toBudgetRatio(ReasoningEffort.AUTO));
        assertEquals(0.0, handler.toBudgetRatio(ReasoningEffort.NONE));
        assertEquals(0.20, handler.toBudgetRatio(ReasoningEffort.LOW));
        assertEquals(0.50, handler.toBudgetRatio(ReasoningEffort.MEDIUM));
        assertEquals(0.80, handler.toBudgetRatio(ReasoningEffort.HIGH));
        assertEquals(0.95, handler.toBudgetRatio(ReasoningEffort.XHIGH));
    }

    @Test
    void buildChatPayload_everyNamedEffortOnAModelTakingAnEffort_isStated() {
        assertEquals("low", effortOf(ReasoningEffort.LOW));
        assertEquals("high", effortOf(ReasoningEffort.HIGH));
        assertEquals("xhigh", effortOf(ReasoningEffort.XHIGH));
    }

    // =================================================================================================================
    // Answers and events which state something is wrong
    // =================================================================================================================

    @Test
    void parseChatResponse_toolTurnStatingAnError_reportsTheError() {
        var response = parseJson("""
            {"error":{"message":"overloaded"},"content":[{"type":"server_tool_use","name":"web_search"}]}
            """);

        var exception = assertThrows(AIResponseException.class, () -> handler.parseChatResponse(response));
        assertTrue(exception.getMessage().contains("overloaded"), exception.getMessage());
    }

    @Test
    void processChatStreamEvent_errorEvent_throws() {
        var event = data("{\"type\":\"error\"}");

        assertThrows(AIResponseException.class, () -> process(event, ChatOptions.DEFAULT, token -> {
            /* no token here */ }));
    }

    /**
     * A usage event which reports the output count before any input count was seen has nothing to carry forward.
     */
    @Test
    void processChatStreamEvent_outputCountWithoutAPriorRecord_statesTheInputAsUnknown() {
        var options = ChatOptions.newBuilder().build();

        process(data("{\"type\":\"message_delta\",\"usage\":{\"output_tokens\":42}}"), options, token -> {
            /* no token here */ });

        assertEquals(-1, options.getLastUsage().inputTokens());
        assertEquals(42, options.getLastUsage().outputTokens());
    }

    private String effortOf(ReasoningEffort effort) {
        return payload(CLAUDE_OPUS_4_7, ChatOptions.newBuilder().reasoningEffort(effort).build()).getJsonObject("output_config").getString("effort");
    }

    /**
     * Stands in for a service which accepts an upload and answers with the id it filed it under.
     */
    private static AIService uploadingService() {
        var service = newService(CLAUDE_SONNET_4_5);
        var uploading = mock(AIService.class);
        when(uploading.getModelName()).thenReturn(service.getModelName());
        when(uploading.getModelVersion()).thenReturn(service.getModelVersion());
        when(uploading.supportsFileAttachments()).thenReturn(true);
        when(uploading.supportsSamplingParameters()).thenReturn(true);
        when(uploading.upload(any(), any())).thenReturn("file-1");
        return uploading;
    }

    private static byte[] pdf() {
        return "%PDF-1.4\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    /**
     * A shared default options instance records nothing, so a usage event on one passes without being recorded.
     */
    @Test
    void processChatStreamEvent_usageEventOnADefaultOptionsInstance_recordsNothing() {
        assertTrue(process(data("{\"type\":\"message_start\",\"message\":{}}"), ChatOptions.DEFAULT, token -> {
            /* no token here */ }));
    }

    @Test
    void processChatStreamEvent_eventOfAnotherType_passesWithoutRecording() {
        var options = ChatOptions.newBuilder().build();

        assertTrue(process(data("{\"type\":\"content_block_start\"}"), options, token -> {
            /* no token here */ }));
        assertNull(options.getLastUsage());
    }

}
