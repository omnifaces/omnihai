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

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AITokenLimitExceededException;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.AnalyzeVideoOptions;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatInput.UploadedFile;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.Location;
import org.omnifaces.ai.model.ChatOptions.ReasoningEffort;
import org.omnifaces.ai.model.Sse.Event;
import org.omnifaces.ai.model.Sse.Event.Type;

class GoogleAITextHandlerTest {

    private static final MimeType TEST_MP4 = MimeType.of("video/mp4");

    private final GoogleAITextHandler handler = new GoogleAITextHandler();

    private static Attachment newVideo() {
        return new Attachment(new byte[] { 1, 2, 3 }, MimeType.of("video/mp4"), "video.mp4");
    }

    @Test
    void buildVideoMetadata_absent_whenNoVideoOptions() {
        assertTrue(handler.buildVideoMetadata(newVideo()).isEmpty());
    }

    @Test
    void buildVideoMetadata_absent_whenDefaultVideoOptions() {
        assertTrue(handler.buildVideoMetadata(newVideo().withVideoOptions(AnalyzeVideoOptions.DEFAULT)).isEmpty());
    }

    @Test
    void buildVideoMetadata_containsOnlyTheOptionsWhichAreSet() {
        var video = newVideo().withVideoOptions(AnalyzeVideoOptions.newBuilder().fps(0.5).build());

        var videoMetadata = handler.buildVideoMetadata(video).orElseThrow().build();

        assertEquals(1, videoMetadata.size());
        assertEquals(0.5, videoMetadata.getJsonNumber("fps").doubleValue());
    }

    @Test
    void buildVideoMetadata_rendersWholeSecondOffsetWithoutFraction() {
        var video = newVideo().withVideoOptions(AnalyzeVideoOptions.newBuilder().startOffset(ofSeconds(30)).endOffset(ofSeconds(90)).build());

        var videoMetadata = handler.buildVideoMetadata(video).orElseThrow().build();

        assertEquals("30s", videoMetadata.getString("start_offset"));
        assertEquals("90s", videoMetadata.getString("end_offset"));
    }

    @Test
    void buildVideoMetadata_fromReplayedUploadedFile_isRendered() {
        var videoOptions = AnalyzeVideoOptions.newBuilder().fps(2).build();

        var videoMetadata = handler.buildVideoMetadata(videoOptions).orElseThrow().build();

        assertEquals(2.0, videoMetadata.getJsonNumber("fps").doubleValue(), "a file replayed from history keeps its sampling rate");
    }

    @Test
    void buildVideoMetadata_fromReplayedUploadedFileWithoutOptions_isAbsent() {
        assertTrue(handler.buildVideoMetadata((AnalyzeVideoOptions) null).isEmpty());
    }

    @Test
    void buildVideoMetadata_rendersSubSecondOffsetAsFraction() {
        var video = newVideo().withVideoOptions(AnalyzeVideoOptions.newBuilder().startOffset(ofMillis(1500)).build());

        var videoMetadata = handler.buildVideoMetadata(video).orElseThrow().build();

        assertEquals("1.500s", videoMetadata.getString("start_offset"));
    }

    // =================================================================================================================
    // The shape of the request
    // =================================================================================================================

    @Test
    void buildChatPayload_carriesTheMessageAsAUserPart() {
        var content = payload(ChatOptions.DEFAULT).getJsonArray("contents").getJsonObject(0);

        assertEquals("user", content.getString("role"));
        assertEquals("Hello", content.getJsonArray("parts").getJsonObject(0).getString("text"));
    }

    @Test
    void buildChatPayload_withoutASystemPrompt_statesNone() {
        assertFalse(payload(ChatOptions.DEFAULT).containsKey("system_instruction"));
    }

    /**
     * Google states the system prompt beside the conversation rather than as a turn within it.
     */
    @Test
    void buildChatPayload_withASystemPrompt_statesItBesideTheConversation() {
        var options = ChatOptions.newBuilder().systemPrompt("You are terse.").build();

        var instruction = payload(options).getJsonObject("system_instruction");

        assertEquals("You are terse.", instruction.getJsonArray("parts").getJsonObject(0).getString("text"));
    }

    /**
     * Google names the assistant's own turns "model" rather than "assistant".
     */
    @Test
    void buildChatPayload_withHistory_namesTheAssistantTurnsModel() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "What is 2+2?");
        options.recordMessage(Role.ASSISTANT, "4");

        var contents = payload(ChatInput.newBuilder().message("And 3+3?").build().withHistory(options.getHistory()), options).getJsonArray("contents");

        assertEquals("user", contents.getJsonObject(0).getString("role"));
        assertEquals("model", contents.getJsonObject(1).getString("role"));
        assertEquals("4", contents.getJsonObject(1).getJsonArray("parts").getJsonObject(0).getString("text"));
    }

    /**
     * A file uploaded on an earlier turn is referenced again by its URI, so the provider need not be sent the content twice.
     */
    @Test
    void buildChatPayload_withAnUploadedFileInHistory_referencesItAgain() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "Describe it");
        options.recordUploadedFile(new UploadedFile("files/abc", TEST_MP4, AnalyzeVideoOptions.newBuilder().fps(2).build()));

        var parts = payload(ChatInput.newBuilder().message("And now?").build().withHistory(options.getHistory()), options)
            .getJsonArray("contents").getJsonObject(0).getJsonArray("parts");

        assertEquals("files/abc", parts.getJsonObject(0).getJsonObject("file_data").getString("file_uri"));
        assertEquals(2.0, parts.getJsonObject(0).getJsonObject("video_metadata").getJsonNumber("fps").doubleValue());
    }

    @Test
    void buildChatPayload_withAnImage_carriesItInline() {
        var input = ChatInput.newBuilder().message("What is this?").attach(png()).build();

        var part = payload(input, ChatOptions.DEFAULT).getJsonArray("contents").getJsonObject(0).getJsonArray("parts").getJsonObject(0);

        assertEquals("image/png", part.getJsonObject("inline_data").getString("mime_type"));
        assertFalse(part.getJsonObject("inline_data").getString("data").isEmpty());
    }

    @Test
    void buildChatPayload_withWebSearch_offersTheSearchTool() {
        var options = ChatOptions.newBuilder().webSearch(Location.GLOBAL).build();

        assertTrue(payload(options).getJsonArray("tools").getJsonObject(0).containsKey("google_search"));
    }

    // =================================================================================================================
    // Generation config
    // =================================================================================================================

    @Test
    void buildChatPayload_alwaysStatesTheTemperature() {
        assertTrue(payload(ChatOptions.DEFAULT).getJsonObject("generationConfig").containsKey("temperature"));
    }

    @Test
    void buildChatPayload_withALimitAndATopP_statesBoth() {
        var options = ChatOptions.newBuilder().maxTokens(256).topP(0.5).build();

        var config = payload(options).getJsonObject("generationConfig");

        assertEquals(256, config.getInt("maxOutputTokens"));
        assertEquals(0.5, config.getJsonNumber("topP").doubleValue());
    }

    @Test
    void buildChatPayload_withoutALimitOrATopP_statesNeither() {
        var config = payload(ChatOptions.DEFAULT).getJsonObject("generationConfig");

        assertFalse(config.containsKey("maxOutputTokens"));
        assertFalse(config.containsKey("topP"));
    }

    @Test
    void buildChatPayload_withAJsonSchema_asksForJsonBack() {
        var options = ChatOptions.newBuilder().jsonSchema(parseJson("{\"type\":\"object\"}")).build();

        var config = payload(options).getJsonObject("generationConfig");

        assertEquals("application/json", config.getString("responseMimeType"));
        assertTrue(config.containsKey("responseSchema"));
    }

    /**
     * Gemini states thinking as a level rather than a budget, and offers no level beyond high, so a higher request is capped rather than refused.
     */
    @Test
    void buildChatPayload_reasoningEffort_isStatedAsALevelAndCappedAtHigh() {
        assertEquals("low", thinkingLevel(ReasoningEffort.LOW));
        assertEquals("high", thinkingLevel(ReasoningEffort.HIGH));
        assertEquals("high", thinkingLevel(ReasoningEffort.XHIGH));
    }

    /**
     * Gemini always thinks; asking it not to leaves the level unstated so the model applies its own.
     */
    @Test
    void buildChatPayload_thinkingUnstatedOrDisabled_leavesTheLevelToTheModel() {
        assertFalse(
            payload(ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.AUTO).build()).getJsonObject("generationConfig")
                .containsKey("thinkingConfig")
        );
        assertFalse(
            payload(ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.NONE).build()).getJsonObject("generationConfig")
                .containsKey("thinkingConfig")
        );
    }

    // =================================================================================================================
    // Usage
    // =================================================================================================================

    /**
     * Google reports the thinking tokens beside the answer tokens rather than within them, so they are added up to state one output count.
     */
    @Test
    void parseChatUsage_addsTheThinkingTokensToTheOutputTokens() {
        var usage = handler.parseChatUsage(parseJson("""
            {"usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":20,"thoughtsTokenCount":5}}
            """));

        assertEquals(10, usage.inputTokens());
        assertEquals(25, usage.outputTokens());
        assertEquals(5, usage.reasoningTokens());
    }

    @Test
    void parseChatUsage_withoutAnyUsage_statesNone() {
        assertNull(handler.parseChatUsage(parseJson("{}")));
    }

    // =================================================================================================================
    // The stream
    // =================================================================================================================

    @Test
    void processChatStreamEvent_emitsTheTokenAndStopsAtTheEnd() {
        var tokens = new ArrayList<String>();

        assertTrue(process("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hi\"}]}}]}", ChatOptions.DEFAULT, tokens::add));
        assertFalse(process("{\"candidates\":[{\"finishReason\":\"STOP\"}]}", ChatOptions.DEFAULT, tokens::add));
        assertEquals(List.of("Hi"), tokens);
    }

    @Test
    void processChatStreamEvent_tokenLimitReached_throws() {
        assertThrows(
            AITokenLimitExceededException.class,
            () -> process("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}", ChatOptions.DEFAULT, token -> {
                /* no token here */ }
            )
        );
    }

    @Test
    void processChatStreamEvent_usageMetadata_isRecorded() {
        var options = ChatOptions.newBuilder().build();

        process("{\"usageMetadata\":{\"promptTokenCount\":3,\"candidatesTokenCount\":4}}", options, token -> {
            /* no token here */ });

        assertEquals(3, options.getLastUsage().inputTokens());
    }

    private boolean process(String json, ChatOptions options, Consumer<String> onToken) {
        return handler.processChatStreamEvent(newService(), options, new Event(Type.DATA, json), onToken);
    }

    private String thinkingLevel(ReasoningEffort effort) {
        return payload(ChatOptions.newBuilder().reasoningEffort(effort).build()).getJsonObject("generationConfig").getJsonObject("thinkingConfig")
            .getString("thinkingLevel");
    }

    private JsonObject payload(ChatOptions options) {
        return payload(ChatInput.newBuilder().message("Hello").build(), options);
    }

    private JsonObject payload(ChatInput input, ChatOptions options) {
        return handler.buildChatPayload(newService(), input, options, false);
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

    private static AIService newService() {
        return AIConfig.of(AIProvider.GOOGLE, "test-api-key").withModel("gemini-3-pro-preview").createService();
    }

    // =================================================================================================================
    // File attachments
    // =================================================================================================================

    /**
     * Google references an uploaded file by the URI the upload answered with, and states the sampling options beside it when the file is a video.
     */
    @Test
    void buildChatPayload_withAVideoFile_referencesItAndStatesTheVideoOptions() {
        var service = uploadingService();
        var video = new Attachment(new byte[] { 1, 2, 3 }, TEST_MP4, "clip.mp4")
            .withVideoOptions(AnalyzeVideoOptions.newBuilder().fps(2).build());
        var input = ChatInput.newBuilder().message("Describe it").attach(video).build();

        var parts = handler.buildChatPayload(service, input, ChatOptions.DEFAULT, false).getJsonArray("contents").getJsonObject(0).getJsonArray("parts");

        assertEquals("files/abc", parts.getJsonObject(0).getJsonObject("file_data").getString("file_uri"));
        assertEquals("video/mp4", parts.getJsonObject(0).getJsonObject("file_data").getString("mime_type"));
        assertEquals(2.0, parts.getJsonObject(0).getJsonObject("video_metadata").getJsonNumber("fps").doubleValue());
    }

    @Test
    void buildChatPayload_withAFileOnAServiceWhichCannot_isRejected() {
        var service = mock(AIService.class);
        when(service.getModelName()).thenReturn("gemini-3-pro-preview");
        var input = ChatInput.newBuilder().message("Read it").attach(new Attachment(new byte[] { 1, 2, 3 }, TEST_MP4, "clip.mp4")).build();

        assertThrows(UnsupportedOperationException.class, () -> handler.buildChatPayload(service, input, ChatOptions.DEFAULT, false));
    }

    // =================================================================================================================
    // Thinking and usage on models which state less
    // =================================================================================================================

    @Test
    void getEffectiveReasoningEffort_onAModelWhichCannotThink_isLeftToTheModel() {
        var service = mock(AIService.class);

        assertEquals(
            ReasoningEffort.AUTO, handler.getEffectiveReasoningEffort(service, ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.HIGH).build())
        );
    }

    /**
     * A turn which reports thinking tokens but no answer tokens has the thinking as its whole output.
     */
    @Test
    void parseChatUsage_withoutAnswerTokens_countsTheThinkingAsTheOutput() {
        var usage = handler.parseChatUsage(parseJson("{\"usageMetadata\":{\"promptTokenCount\":10,\"thoughtsTokenCount\":5}}"));

        assertEquals(5, usage.outputTokens());
    }

    @Test
    void processChatStreamEvent_eventWhichIsNotData_continuesTheStream() {
        assertTrue(handler.processChatStreamEvent(newService(), ChatOptions.DEFAULT, new Event(Type.EVENT, "message"), token -> {
            /* none */ }));
    }

    @Test
    void processChatStreamEvent_onADefaultOptionsInstance_recordsNoUsage() {
        assertTrue(process("{\"usageMetadata\":{\"promptTokenCount\":3}}", ChatOptions.DEFAULT, token -> {
            /* none */ }));
    }

    @Test
    void processChatStreamEvent_withoutAFinishReason_continuesTheStream() {
        assertTrue(process("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hi\"}]}}]}", ChatOptions.newBuilder().build(), token -> {
            /* none */ }));
    }

    /**
     * Stands in for a service which accepts an upload and answers with the URI it filed it under.
     */
    private static AIService uploadingService() {
        var service = mock(AIService.class);
        when(service.getModelName()).thenReturn("gemini-3-pro-preview");
        when(service.supportsFileAttachments()).thenReturn(true);
        when(service.upload(any(), any())).thenReturn("files/abc");
        return service;
    }

}
