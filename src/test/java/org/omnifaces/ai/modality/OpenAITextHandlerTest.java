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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.omnifaces.ai.AIProvider.AZURE;
import static org.omnifaces.ai.AIProvider.META;
import static org.omnifaces.ai.AIProvider.OPENAI;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIProvider;
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
import org.omnifaces.ai.service.AzureAIService;
import org.omnifaces.ai.service.OpenAIService;

class OpenAITextHandlerTest {

    private static final Location MIAMI = new Location("US", null, "Miami");

    private static final JsonObject SCHEMA = parseJson("{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"}}}");

    private final OpenAITextHandler handler = new OpenAITextHandler();

    /**
     * A Responses API turn which searched the web announces each lookup in a message of its own before answering in the last one.
     */
    private static final String AGENTIC_RESPONSE = """
        {"output": [
            {"type": "reasoning", "summary": [{"type": "summary_text", "text": "The user wants the weather."}]},
            {"type": "message", "content": [{"type": "output_text", "text": "I'll check the current weather for Miami."}]},
            {"type": "web_search_call", "status": "completed"},
            {"type": "message", "content": [{"type": "output_text", "text": "Fetching detailed current conditions."}]},
            {"type": "web_search_call", "status": "completed"},
            {"type": "message", "content": [{"type": "output_text", "text": "Miami is 82F with a high of 88 and a low of 75."}]}
        ]}
        """;

    @Test
    void parseChatResponse_agenticTurn_answersWithTheLastMessage() {
        assertEquals("Miami is 82F with a high of 88 and a low of 75.", handler.parseChatResponse(parseJson(AGENTIC_RESPONSE)));
    }

    @Test
    void parseChatResponse_singleMessage_answersWithIt() {
        var responseJson = parseJson("{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"The one answer\"}]}]}");

        assertEquals("The one answer", handler.parseChatResponse(responseJson));
    }

    @Test
    void parseChatResponse_completionsApi_answersWithTheMessageContent() {
        var responseJson = parseJson("{\"choices\":[{\"message\":{\"content\":\"The completions answer\"}}]}");

        assertEquals("The completions answer", handler.parseChatResponse(responseJson));
    }

    // =================================================================================================================
    // Reasoning effort
    // =================================================================================================================

    @Test
    void getEffectiveReasoningEffort_modelRejectingNone_fallsBackToTheLowestItAccepts() {
        assertEquals(ReasoningEffort.LOW, effectiveReasoningEffort(newService(META, "muse-spark-1.2"), ReasoningEffort.NONE));
        assertEquals(ReasoningEffort.LOW, effectiveReasoningEffort(newService(OPENAI, "gpt-5"), ReasoningEffort.NONE));
    }

    @Test
    void getEffectiveReasoningEffort_modelRejectingNone_appliesTheProviderDefaultOnAuto() {
        assertEquals(ReasoningEffort.MEDIUM, effectiveReasoningEffort(newService(META, "muse-spark-1.2"), ReasoningEffort.AUTO));
        assertEquals(ReasoningEffort.MEDIUM, effectiveReasoningEffort(newService(OPENAI, "gpt-5"), ReasoningEffort.AUTO));
    }

    @Test
    void getEffectiveReasoningEffort_modelAcceptingNone_appliesItAsRequested() {
        assertEquals(ReasoningEffort.NONE, effectiveReasoningEffort(newService(), ReasoningEffort.NONE));
    }

    // =================================================================================================================
    // Tool choice
    // =================================================================================================================

    @Test
    void buildChatPayloadToolsWithResponsesApi_modelRejectingRequired_leavesTheToolChoiceToTheModel() {
        assertFalse(webSearchPayload(newService(META, "muse-spark-1.2")).containsKey("tool_choice"));
    }

    @Test
    void buildChatPayloadToolsWithResponsesApi_modelAcceptingRequired_forcesTheSearch() {
        assertEquals("required", webSearchPayload(newService()).getString("tool_choice"));
    }

    @Test
    void buildChatPayloadToolsWithResponsesApi_always_offersTheWebSearchTool() {
        assertTrue(webSearchPayload(newService(META, "muse-spark-1.2")).containsKey("tools"));
        assertTrue(webSearchPayload(newService()).containsKey("tools"));
    }

    // =================================================================================================================
    // Web search location
    // =================================================================================================================

    @Test
    void buildChatPayload_modelIgnoringUserLocation_repeatsTheLocationInTheInstructions() {
        var payload = webSearchPayload(newService(META, "muse-spark-1.2"), MIAMI);

        assertEquals("Search within Miami, US", payload.getString("instructions"));
    }

    @Test
    void buildChatPayload_modelHonoringUserLocation_leavesTheInstructionsAlone() {
        var payload = webSearchPayload(newService(), MIAMI);

        assertFalse(payload.containsKey("instructions"));
    }

    @Test
    void buildChatPayload_globalLocation_leavesTheInstructionsAlone() {
        var payload = webSearchPayload(newService(META, "muse-spark-1.2"), Location.GLOBAL);

        assertFalse(payload.containsKey("instructions"));
    }

    // =================================================================================================================
    // Locale independence
    // =================================================================================================================

    @Test
    void addReasoningEffort_underADottedIlessLocale_staysAscii() {
        var service = newService();
        var payload = Json.createObjectBuilder();
        var defaultLocale = Locale.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));

            handler.addReasoningEffort(service, payload, ReasoningEffort.HIGH, false);

            assertEquals("high", payload.build().getString("reasoning_effort"), "an AI provider takes its own enum value, not the default locale's rendering");
        }
        finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    void addReasoningEffort_underADottedIlessLocale_staysAsciiOnTheResponsesApi() {
        var service = newService();
        var payload = Json.createObjectBuilder();
        var defaultLocale = Locale.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));

            handler.addReasoningEffort(service, payload, ReasoningEffort.XHIGH, true);

            assertEquals("xhigh", payload.build().getJsonObject("reasoning").getString("effort"), "XHIGH is the value a dotted-I-less locale mangles worst");
        }
        finally {
            Locale.setDefault(defaultLocale);
        }
    }

    private ReasoningEffort effectiveReasoningEffort(AIService service, ReasoningEffort requested) {
        return handler.getEffectiveReasoningEffort(service, ChatOptions.newBuilder().reasoningEffort(requested).build());
    }

    private JsonObject webSearchPayload(AIService service) {
        var payload = Json.createObjectBuilder();
        handler.buildChatPayloadToolsWithResponsesApi(service, payload, ChatOptions.newBuilder().webSearch().build());
        return payload.build();
    }

    private JsonObject webSearchPayload(AIService service, Location location) {
        return handler.buildChatPayload(
            service, ChatInput.newBuilder().message("What is the current weather?").build(), ChatOptions.newBuilder().webSearch(location).build(), false
        );
    }

    private static AIService newService() {
        return newService(OPENAI, "gpt-5.6-terra");
    }

    private static AIService newService(AIProvider provider, String model) {
        return AIConfig.of(provider, "test-api-key").withModel(model).createService();
    }

    // =================================================================================================================
    // Attachments
    // =================================================================================================================

    /**
     * A model without a files API takes the file inline, and the two APIs name that block differently.
     */
    @Test
    void buildChatPayload_fileOnAModelWithoutAFilesApi_travelsInline() {
        var input = ChatInput.newBuilder().message("Read it").attach(pdf()).build();

        var content = payload(legacyService(), input, ChatOptions.DEFAULT).getJsonArray("messages").getJsonObject(0)
            .getJsonArray("content");

        assertEquals("file", content.getJsonObject(0).getString("type"));
        assertEquals("file1.pdf", content.getJsonObject(0).getJsonObject("file").getString("filename"));
        assertTrue(content.getJsonObject(0).getJsonObject("file").getString("file_data").startsWith("data:application/pdf;base64,"));
    }

    @Test
    void buildChatPayload_imageOnTheChatCompletionsApi_isNestedInAUrlObject() {
        var input = ChatInput.newBuilder().message("What is this?").attach(png()).build();

        var content = payload(legacyService(), input, ChatOptions.DEFAULT).getJsonArray("messages").getJsonObject(0)
            .getJsonArray("content");

        assertEquals("image_url", content.getJsonObject(0).getString("type"));
        assertTrue(content.getJsonObject(0).getJsonObject("image_url").getString("url").startsWith("data:image/png;base64,"));
    }

    /**
     * Audio takes a block of its own with the format named beside it, as the provider decodes it rather than reading a data URI.
     */
    @Test
    void buildChatPayload_audioFile_travelsAsItsOwnBlock() {
        var input = ChatInput.newBuilder().message("Transcribe it").attach(wav()).build();

        var content = payload(legacyService(), input, ChatOptions.DEFAULT).getJsonArray("messages").getJsonObject(0)
            .getJsonArray("content");

        assertEquals("input_audio", content.getJsonObject(0).getString("type"));
        assertEquals("wav", content.getJsonObject(0).getJsonObject("input_audio").getString("format"));
        assertFalse(content.getJsonObject(0).getJsonObject("input_audio").getString("data").isEmpty());
    }

    // =================================================================================================================
    // Generation config
    // =================================================================================================================

    /**
     * The Chat Completions API renamed the token cap at GPT-5, so the name follows the model rather than the API.
     */
    @Test
    void buildChatPayload_tokenLimit_isNamedAfterTheModelGeneration() {
        var options = ChatOptions.newBuilder().maxTokens(256).build();

        assertEquals(256, payload(legacyService(), options).getInt("max_tokens"));
        assertEquals(256, payload(newService(), options).getInt("max_output_tokens"));
    }

    @Test
    void buildChatPayload_withoutATokenLimit_statesNone() {
        var payload = payload(newService(), ChatOptions.DEFAULT);

        assertFalse(payload.containsKey("max_output_tokens"));
        assertFalse(payload.containsKey("max_tokens"));
    }

    @Test
    void buildChatPayload_withATopP_statesIt() {
        assertEquals(0.5, payload(newService(), ChatOptions.newBuilder().topP(0.5).build()).getJsonNumber("top_p").doubleValue());
    }

    /**
     * The Chat Completions API reports the usage of a stream only when asked to.
     */
    @Test
    void buildChatPayload_streamingOnTheChatCompletionsApi_asksForTheUsageToo() {
        var service = legacyService();
        var payload = handler.buildChatPayload(service, ChatInput.newBuilder().message("Hello").build(), ChatOptions.DEFAULT, true);

        assertTrue(payload.getBoolean("stream"));
        assertTrue(payload.getJsonObject("stream_options").getBoolean("include_usage"));
    }

    @Test
    void buildChatPayload_streamingOnTheResponsesApi_needsNoUsageOption() {
        var payload = handler.buildChatPayload(newService(), ChatInput.newBuilder().message("Hello").build(), ChatOptions.DEFAULT, true);

        assertTrue(payload.getBoolean("stream"));
        assertFalse(payload.containsKey("stream_options"));
    }

    // =================================================================================================================
    // Structured output
    // =================================================================================================================

    /**
     * The Responses API takes the schema flattened into a text format block; the Chat Completions API takes it nested in a response format block.
     */
    @Test
    void buildChatPayload_withAJsonSchemaOnTheResponsesApi_statesItAsATextFormat() {
        var options = ChatOptions.newBuilder().jsonSchema(SCHEMA).build();

        var format = payload(newService(), options).getJsonObject("text").getJsonObject("format");

        assertEquals("json_schema", format.getString("type"));
        assertEquals("response_schema", format.getString("name"));
        assertTrue(format.getBoolean("strict"));
        assertFalse(format.getJsonObject("schema").getBoolean("additionalProperties"));
    }

    @Test
    void buildChatPayload_withAJsonSchemaOnTheChatCompletionsApi_statesItAsAResponseFormat() {
        var options = ChatOptions.newBuilder().jsonSchema(SCHEMA).build();

        var jsonSchema = payload(legacyService(), options).getJsonObject("response_format").getJsonObject("json_schema");

        assertEquals("response_schema", jsonSchema.getString("name"));
        assertTrue(jsonSchema.getBoolean("strict"));
        assertFalse(jsonSchema.getJsonObject("schema").getBoolean("additionalProperties"));
    }

    @Test
    void buildChatPayload_withoutAJsonSchema_statesNoFormat() {
        var payload = payload(newService(), ChatOptions.DEFAULT);

        assertFalse(payload.containsKey("text"));
        assertFalse(payload.containsKey("response_format"));
    }

    // =================================================================================================================
    // History
    // =================================================================================================================

    /**
     * The Responses API names a text block after the side of the conversation it came from.
     */
    @Test
    void buildChatPayload_historyOnTheResponsesApi_namesTheBlockPerRole() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "What is 2+2?");
        options.recordMessage(Role.ASSISTANT, "4");
        var input = ChatInput.newBuilder().message("And 3+3?").build().withHistory(options.getHistory());

        var messages = handler.buildChatPayload(newService(), input, options, false).getJsonArray("input");

        assertEquals("input_text", messages.getJsonObject(0).getJsonArray("content").getJsonObject(0).getString("type"));
        assertEquals("output_text", messages.getJsonObject(1).getJsonArray("content").getJsonObject(0).getString("type"));
    }

    /**
     * A model without a files API carries the history as plain text, as it has no content blocks to list files in.
     */
    @Test
    void buildChatPayload_historyOnAModelWithoutAFilesApi_isPlainText() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "What is 2+2?");
        var input = ChatInput.newBuilder().message("And 3+3?").build().withHistory(options.getHistory());

        var messages = handler.buildChatPayload(legacyService(), input, options, false).getJsonArray("messages");

        assertEquals("What is 2+2?", messages.getJsonObject(0).getString("content"));
    }

    // =================================================================================================================
    // The stream
    // =================================================================================================================

    @Test
    void processChatStreamEvent_responsesApiDelta_emitsTheToken() {
        var tokens = new ArrayList<String>();

        assertTrue(process(newService(), data("{\"type\":\"response.output_text.delta\",\"delta\":\"Hi\"}"), ChatOptions.DEFAULT, tokens::add));
        assertEquals(List.of("Hi"), tokens);
    }

    @Test
    void processChatStreamEvent_responsesApiUnrelatedEvent_isNotEvenParsed() {
        assertTrue(process(newService(), data("{\"type\":\"response.output_item.added\"}"), ChatOptions.DEFAULT, token -> {
            /* none */ }));
    }

    @Test
    void processChatStreamEvent_responsesApiIncomplete_throws() {
        var event = new Event(Type.EVENT, "response.incomplete");

        assertThrows(AITokenLimitExceededException.class, () -> process(newService(), event, ChatOptions.DEFAULT, token -> {
            /* none */ }));
    }

    @Test
    void processChatStreamEvent_responsesApiFailure_throws() {
        var event = data("{\"type\":\"response.failed\"}");

        assertThrows(AIResponseException.class, () -> process(newService(), event, ChatOptions.DEFAULT, token -> {
            /* none */ }));
    }

    @Test
    void processChatStreamEvent_responsesApiCompleted_recordsTheUsage() {
        var options = ChatOptions.newBuilder().build();

        process(newService(), data("""
            {"type":"response.completed","response":{"usage":{"input_tokens":3,"output_tokens":4}}}
            """), options, token -> {
            /* none */ });

        assertEquals(3, options.getLastUsage().inputTokens());
    }

    @Test
    void processChatStreamEvent_chatCompletionsChunk_emitsTheTokenAndEndsOnDone() {
        var service = legacyService();
        var tokens = new ArrayList<String>();

        assertTrue(process(service, data("""
            {"object":"chat.completion.chunk","choices":[{"delta":{"content":"Hi"}}]}
            """), ChatOptions.DEFAULT, tokens::add));
        assertFalse(process(service, data("DONE"), ChatOptions.DEFAULT, tokens::add));
        assertEquals(List.of("Hi"), tokens);
    }

    @Test
    void processChatStreamEvent_chatCompletionsTokenLimitReached_throws() {
        var service = legacyService();
        var event = data("{\"object\":\"chat.completion.chunk\",\"choices\":[{\"finish_reason\":\"length\"}]}");

        assertThrows(AITokenLimitExceededException.class, () -> process(service, event, ChatOptions.DEFAULT, token -> {
            /* none */ }));
    }

    @Test
    void processChatStreamEvent_chatCompletionsUsage_isRecorded() {
        var options = ChatOptions.newBuilder().build();

        process(legacyService(), data("""
            {"object":"chat.completion.chunk","choices":[],"usage":{"prompt_tokens":3,"completion_tokens":4}}
            """), options, token -> {
            /* none */ });

        assertEquals(3, options.getLastUsage().inputTokens());
    }

    private boolean process(AIService service, Event event, ChatOptions options, java.util.function.Consumer<String> onToken) {
        return handler.processChatStreamEvent(service, options, event, onToken);
    }

    private static Event data(String value) {
        return new Event(Type.DATA, value);
    }

    private JsonObject payload(AIService service, ChatOptions options) {
        return payload(service, ChatInput.newBuilder().message("Hello").build(), options);
    }

    private JsonObject payload(AIService service, ChatInput input, ChatOptions options) {
        return handler.buildChatPayload(service, input, options, false);
    }

    /** A provider which serves the Chat Completions API rather than the Responses API, and takes its files inline. */
    private static AIService legacyService() {
        return newService(AIProvider.OPENROUTER, "openai/gpt-4o");
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

    private static byte[] pdf() {
        return "%PDF-1.4\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static byte[] wav() {
        var wav = new byte[64];
        System.arraycopy("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, wav, 0, 4);
        System.arraycopy("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, wav, 8, 4);
        return wav;
    }

    // =================================================================================================================
    // Files on a provider which offers a files API
    // =================================================================================================================

    /**
     * A provider with a files API uploads the file first and then references the id it answered with, rather than carrying the bytes in the turn.
     */
    @Test
    void buildChatPayload_fileOnAProviderWithAFilesApi_referencesTheUploadedId() {
        var service = uploadingService();
        var input = ChatInput.newBuilder().message("Read it").attach(pdf()).build();

        var content = handler.buildChatPayload(service, input, ChatOptions.DEFAULT, false).getJsonArray("input").getJsonObject(0).getJsonArray("content");

        assertEquals("input_file", content.getJsonObject(0).getString("type"));
        assertEquals("file-1", content.getJsonObject(0).getString("file_id"));
    }

    /**
     * A file uploaded on an earlier turn is referenced again rather than uploaded a second time.
     */
    @Test
    void buildChatPayload_uploadedFileInHistory_isReferencedAgain() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "Read it");
        options.recordUploadedFile(new UploadedFile("file-1", MimeType.of("application/pdf")));
        var input = ChatInput.newBuilder().message("And now?").build().withHistory(options.getHistory());

        var content = handler.buildChatPayload(uploadingService(), input, options, false).getJsonArray("input").getJsonObject(0).getJsonArray("content");

        assertEquals("input_file", content.getJsonObject(0).getString("type"));
        assertEquals("file-1", content.getJsonObject(0).getString("file_id"));
    }

    // =================================================================================================================
    // Reasoning effort per model family
    // =================================================================================================================

    /**
     * A model which cannot think answers with no effort at all, whatever the caller asked for.
     */
    @Test
    void getEffectiveReasoningEffort_onAModelWhichCannotThink_isNone() {
        assertEquals(ReasoningEffort.NONE, effectiveReasoningEffort(newService(OPENAI, "gpt-3.5-turbo"), ReasoningEffort.HIGH));
    }

    /**
     * Only the codex-max models take the highest level, so a request for it is capped on every other one.
     */
    @Test
    void getEffectiveReasoningEffort_highestLevel_isCappedExceptOnTheModelsWhichTakeIt() {
        assertEquals(ReasoningEffort.HIGH, effectiveReasoningEffort(newService(), ReasoningEffort.XHIGH));
        assertEquals(ReasoningEffort.XHIGH, effectiveReasoningEffort(newService(OPENAI, "gpt-5.1-codex-max"), ReasoningEffort.XHIGH));
    }

    private static AIService uploadingService() {
        var service = newService();
        var uploading = mock(OpenAIService.class);
        when(uploading.getModelName()).thenReturn(service.getModelName());
        when(uploading.getModelVersion()).thenReturn(service.getModelVersion());
        when(uploading.supportsFileAttachments()).thenReturn(true);
        when(uploading.supportsOpenAIResponsesApi()).thenReturn(true);
        when(uploading.supportsOpenAIFilesApi()).thenReturn(true);
        when(uploading.upload(any(), any())).thenReturn("file-1");
        return uploading;
    }

    // =================================================================================================================
    // The two API variants, each with and without a files API
    // =================================================================================================================

    /**
     * A Responses API provider without a files API carries the file inline under the block that API names, rather than uploading it first.
     */
    @Test
    void buildChatPayload_responsesApiWithoutAFilesApi_carriesTheFileInline() {
        var input = ChatInput.newBuilder().message("Read it").attach(pdf()).build();

        var content = handler.buildChatPayload(openAIService(true, false), input, ChatOptions.DEFAULT, false).getJsonArray("input").getJsonObject(0)
            .getJsonArray("content");

        assertEquals("input_file", content.getJsonObject(0).getString("type"));
        assertEquals("file1.pdf", content.getJsonObject(0).getString("filename"));
        assertTrue(content.getJsonObject(0).getString("file_data").startsWith("data:application/pdf;base64,"));
    }

    @Test
    void buildChatPayload_responsesApiImageAndAudio_useTheBlocksThatApiNames() {
        var input = ChatInput.newBuilder().message("What is this?").attach(png()).attach(wav()).build();

        var content = handler.buildChatPayload(openAIService(true, false), input, ChatOptions.DEFAULT, false).getJsonArray("input").getJsonObject(0)
            .getJsonArray("content");

        assertEquals("input_image", content.getJsonObject(0).getString("type"));
        assertTrue(content.getJsonObject(0).getString("image_url").startsWith("data:image/png;base64,"));
        assertEquals("input_audio", content.getJsonObject(1).getString("type"));
        assertTrue(content.getJsonObject(1).getJsonObject("input_audio").containsKey("audio_base64"));
    }

    /**
     * A Chat Completions provider with a files API uploads the file and then references it, and names its history blocks plainly.
     */
    @Test
    void buildChatPayload_chatCompletionsApiWithAFilesApi_referencesTheUploadAndNamesHistoryPlainly() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "Read it");
        options.recordUploadedFile(new UploadedFile("file-1", MimeType.of("application/pdf")));
        var input = ChatInput.newBuilder().message("And now?").build().withHistory(options.getHistory());

        var messages = handler.buildChatPayload(openAIService(false, true), input, options, false).getJsonArray("messages");

        var history = messages.getJsonObject(0).getJsonArray("content");
        assertEquals("file", history.getJsonObject(0).getString("type"));
        assertEquals("text", history.getJsonObject(1).getString("type"));
    }

    /**
     * The Chat Completions API renamed the token cap at GPT-5, so a newer model on that API states it under the newer name.
     */
    @Test
    void buildChatPayload_tokenLimitOnANewerChatCompletionsModel_usesTheNewerName() {
        var service = newService(AIProvider.OPENROUTER, "openai/gpt-5");
        var payload = handler.buildChatPayload(
            service, ChatInput.newBuilder().message("Hello").build(), ChatOptions.newBuilder().maxTokens(256).build(), false
        );

        assertEquals(256, payload.getInt("max_completion_tokens"));
    }

    // =================================================================================================================
    // Services which are not OpenAI based at all
    // =================================================================================================================

    /**
     * The OpenAI specific capabilities are asked of the service itself, so a service which is not OpenAI based offers none of them.
     */
    @Test
    void buildChatPayload_serviceWhichIsNotOpenAIBased_offersNoneOfTheOpenAICapabilities() {
        var service = mock(AIService.class);
        when(service.getModelName()).thenReturn("some-model");
        when(service.supportsReasoningEffort()).thenReturn(true);

        var payload = handler.buildChatPayload(
            service, ChatInput.newBuilder().message("Hello").build(), ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.XHIGH).build(), false
        );

        assertEquals("high", payload.getString("reasoning_effort"), "the highest level is capped, as only the codex models take it");
    }

    @Test
    void getEffectiveReasoningEffort_onAServiceWhichStatesNoNoneLevel_keepsTheNamedLevels() {
        var service = mock(AIService.class);
        when(service.supportsReasoningEffort()).thenReturn(true);

        assertEquals(ReasoningEffort.MEDIUM, effectiveReasoningEffort(service, ReasoningEffort.AUTO));
        assertEquals(ReasoningEffort.LOW, effectiveReasoningEffort(service, ReasoningEffort.NONE));
        assertEquals(ReasoningEffort.HIGH, effectiveReasoningEffort(service, ReasoningEffort.HIGH));
    }

    // =================================================================================================================
    // Stream events which say nothing
    // =================================================================================================================

    @Test
    void processChatStreamEvent_responsesApiEventWhichIsNotTheEnd_continuesTheStream() {
        assertTrue(process(newService(), new Event(Type.EVENT, "response.created"), ChatOptions.DEFAULT, token -> {
            /* none */ }));
    }

    @Test
    void processChatStreamEvent_responsesApiEmptyDelta_emitsNothing() {
        var tokens = new java.util.ArrayList<String>();

        process(newService(), data("{\"type\":\"response.output_text.delta\",\"delta\":\"\"}"), ChatOptions.DEFAULT, tokens::add);

        assertTrue(tokens.isEmpty());
    }

    @Test
    void processChatStreamEvent_responsesApiCompletedOnDefaultOptions_recordsNothing() {
        assertTrue(process(newService(), data("{\"type\":\"response.completed\",\"response\":{}}"), ChatOptions.DEFAULT, token -> {
            /* none */ }));
    }

    @Test
    void processChatStreamEvent_chatCompletionsNamedEvent_continuesTheStream() {
        assertTrue(process(legacyService(), new Event(Type.EVENT, "message"), ChatOptions.DEFAULT, token -> {
            /* none */ }));
    }

    @Test
    void processChatStreamEvent_chatCompletionsUnrelatedData_isNotEvenParsed() {
        assertTrue(process(legacyService(), data("{\"object\":\"something.else\"}"), ChatOptions.DEFAULT, token -> {
            /* none */ }));
    }

    @Test
    void processChatStreamEvent_chatCompletionsChunkOfAnotherObject_isIgnored() {
        assertTrue(process(legacyService(), data("{\"object\":\"chat.completion\",\"chat.completion.chunk\":1}"), ChatOptions.DEFAULT, token -> {
            /* none */ }));
    }

    @Test
    void processChatStreamEvent_chatCompletionsUsageOnDefaultOptions_recordsNothing() {
        assertTrue(process(legacyService(), data("""
            {"object":"chat.completion.chunk","choices":[],"usage":{"prompt_tokens":3}}
            """), ChatOptions.DEFAULT, token -> {
            /* none */ }));
    }

    /**
     * Stands in for an OpenAI based service which serves the given combination of APIs.
     */
    private static AIService openAIService(boolean responsesApi, boolean filesApi) {
        var service = mock(OpenAIService.class);
        when(service.getModelName()).thenReturn("gpt-5.6-terra");
        when(service.getModelVersion()).thenReturn(AIModelVersion.of("gpt", 5, 6));
        when(service.supportsFileAttachments()).thenReturn(true);
        when(service.supportsOpenAIResponsesApi()).thenReturn(responsesApi);
        when(service.supportsOpenAIFilesApi()).thenReturn(filesApi);
        when(service.upload(any(), any())).thenReturn("file-1");
        return service;
    }

    /**
     * A Chat Completions provider is asked whether it can search the web before the request is built, so an unsupported search is refused up front.
     */
    @Test
    void buildChatPayload_webSearchOnAChatCompletionsProviderWhichCannot_isRejected() {
        var service = mock(AIService.class);
        when(service.getModelName()).thenReturn("some-model");
        var input = ChatInput.newBuilder().message("What is the weather?").build();
        var options = ChatOptions.newBuilder().webSearch(MIAMI).build();

        var exception = assertThrows(UnsupportedOperationException.class, () -> handler.buildChatPayload(service, input, options, false));
        assertTrue(exception.getMessage().contains("Web search"), exception.getMessage());
    }

    /**
     * OpenAI renamed the purpose an upload is filed under at GPT-5, so it follows the model rather than being fixed.
     */
    @Test
    void getFileUploadMetadata_purpose_followsTheModelGeneration() {
        var older = mock(OpenAIService.class);
        when(older.getModelVersion()).thenReturn(AIModelVersion.of("gpt", 4));
        var newer = mock(OpenAIService.class);
        when(newer.getModelVersion()).thenReturn(AIModelVersion.of("gpt", 5));
        var file = new ChatInput.Attachment(pdf(), MimeType.of("application/pdf"), "test.pdf");

        assertEquals("assistants", handler.getFileUploadMetadata(older, file).get("purpose"));
        assertEquals("user_data", handler.getFileUploadMetadata(newer, file).get("purpose"));
    }

    @Test
    void processChatStreamEvent_responsesApiEventCarryingNoUsage_recordsNothing() {
        var options = ChatOptions.newBuilder().build();

        assertTrue(process(newService(), data("{\"type\":\"response.in_progress\",\"response.completed\":1}"), options, token -> {
            /* none */ }));
        assertNull(options.getLastUsage());
    }

    @Test
    void processChatStreamEvent_chatCompletionsChunkWithoutUsage_recordsNothing() {
        var options = ChatOptions.newBuilder().build();

        process(legacyService(), data("""
            {"object":"chat.completion.chunk","choices":[{"delta":{"content":"Hi"}}]}
            """), options, token -> {
            /* none */ });

        assertNull(options.getLastUsage());
    }

    /**
     * A service which is not OpenAI based offers no files API either, so its files travel inline rather than being uploaded first.
     */
    @Test
    void buildChatPayload_fileOnAServiceWhichIsNotOpenAIBased_travelsInline() {
        var service = mock(AIService.class);
        when(service.getModelName()).thenReturn("some-model");
        when(service.supportsFileAttachments()).thenReturn(true);
        var input = ChatInput.newBuilder().message("Read it").attach(pdf()).build();

        var content = handler.buildChatPayload(service, input, ChatOptions.DEFAULT, false).getJsonArray("messages").getJsonObject(0).getJsonArray("content");

        assertEquals("file", content.getJsonObject(0).getString("type"));
        verify(service, never()).upload(any(), any());
    }

    /**
     * The highest level arrived with GPT-5.1, so a model below it is capped even when its name would otherwise qualify.
     */
    @Test
    void getEffectiveReasoningEffort_highestLevelOnAModelBelowTheFloor_isCapped() {
        var service = mock(OpenAIService.class);
        when(service.getModelName()).thenReturn("gpt-4o-codex-max");
        when(service.getModelVersion()).thenReturn(AIModelVersion.of("gpt", 4));
        when(service.supportsReasoningEffort()).thenReturn(true);

        assertEquals(ReasoningEffort.HIGH, effectiveReasoningEffort(service, ReasoningEffort.XHIGH));
    }

    /**
     * The chat completions API offers no web search tool at all, so a model addressed over it is refused the request rather than answered without searching.
     */
    @Test
    void buildChatPayload_webSearchOnAModelWithoutTheResponsesApi_isRefused() {
        var service = (OpenAIService) AIConfig.of(OPENAI, "test-api-key").withModel("gpt-3.5-turbo").createService();
        var input = ChatInput.newBuilder().message("What is the news?").build();
        var options = ChatOptions.DEFAULT.withWebSearch(Location.GLOBAL);
        var handler = new OpenAITextHandler();

        assertThrows(UnsupportedOperationException.class, () -> handler.buildChatPayload(service, input, options, false));
    }

    /**
     * Azure serves web search whatever the deployment, including one addressed over the chat completions API, so the request is built rather than refused.
     */
    @Test
    void buildChatPayload_webSearchOnAzureWithoutTheResponsesApi_isBuilt() {
        var service = (OpenAIService) AIConfig.of(AZURE, "test-api-key").withModel("gpt-3.5-turbo")
            .withProperty(AzureAIService.OPTION_AZURE_RESOURCE, "my-resource").createService();
        var input = ChatInput.newBuilder().message("What is the news?").build();
        var options = ChatOptions.DEFAULT.withWebSearch(Location.GLOBAL);

        assertNotNull(new AzureAITextHandler().buildChatPayload(service, input, options, false));
    }

}
