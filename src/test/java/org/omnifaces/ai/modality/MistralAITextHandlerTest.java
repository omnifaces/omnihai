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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.omnifaces.ai.AIProvider.MISTRAL;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.ReasoningEffort;
import org.omnifaces.ai.service.MistralAIService;

/**
 * Mistral offers thinking as on or off rather than as a scale, and treats an unstated effort as off, so only the explicit high value is worth sending.
 */
class MistralAITextHandlerTest {

    private static final String REASONING_MODEL = "mistral-medium-3-5";
    private static final String PLAIN_MODEL = "mistral-medium-2508";

    private final MistralAITextHandler handler = new MistralAITextHandler();

    @Test
    void getEffectiveReasoningEffort_collapsesEveryLevelToOffOrHigh() {
        assertEquals(ReasoningEffort.NONE, effectiveEffort(REASONING_MODEL, ReasoningEffort.AUTO));
        assertEquals(ReasoningEffort.NONE, effectiveEffort(REASONING_MODEL, ReasoningEffort.NONE));
        assertEquals(ReasoningEffort.NONE, effectiveEffort(REASONING_MODEL, ReasoningEffort.LOW));
        assertEquals(ReasoningEffort.HIGH, effectiveEffort(REASONING_MODEL, ReasoningEffort.MEDIUM));
        assertEquals(ReasoningEffort.HIGH, effectiveEffort(REASONING_MODEL, ReasoningEffort.HIGH));
        assertEquals(ReasoningEffort.HIGH, effectiveEffort(REASONING_MODEL, ReasoningEffort.XHIGH));
    }

    @Test
    void getEffectiveReasoningEffort_onAModelWhichCannotThink_isOff() {
        assertEquals(ReasoningEffort.NONE, effectiveEffort(PLAIN_MODEL, ReasoningEffort.HIGH));
    }

    /**
     * An absent field already means no reasoning, so stating it would say nothing the provider does not already assume.
     */
    @Test
    void buildChatPayload_reasoningOff_omitsTheField() {
        assertFalse(payload(REASONING_MODEL, ReasoningEffort.LOW).containsKey("reasoning_effort"));
    }

    @Test
    void buildChatPayload_reasoningOn_statesIt() {
        assertEquals("high", payload(REASONING_MODEL, ReasoningEffort.HIGH).getString("reasoning_effort"));
    }

    private ReasoningEffort effectiveEffort(String model, ReasoningEffort requested) {
        return handler.getEffectiveReasoningEffort(newService(model), ChatOptions.newBuilder().reasoningEffort(requested).build());
    }

    private JsonObject payload(String model, ReasoningEffort effort) {
        return handler.buildChatPayload(
            newService(model), ChatInput.newBuilder().message("Hello").build(), ChatOptions.newBuilder().reasoningEffort(effort).build(), false
        );
    }

    private static AIService newService(String model) {
        return AIConfig.of(MISTRAL, "test-api-key").withModel(model).createService();
    }

    // =================================================================================================================
    // Uploaded documents
    // =================================================================================================================

    /**
     * The newer models reference an uploaded document by a short-lived signed URL; the legacy dated ones take the bare file id the OpenAI base states.
     */
    @Test
    void buildChatPayload_uploadedDocumentOnANewerModel_isReferencedByASignedUrl() {
        var content = uploadedDocumentContent(true);

        assertEquals("document_url", content.getJsonObject(0).getString("type"));
        assertEquals("https://example.org/signed", content.getJsonObject(0).getString("document_url"));
    }

    @Test
    void buildChatPayload_uploadedDocumentOnALegacyModel_isReferencedByItsFileId() {
        var content = uploadedDocumentContent(false);

        assertEquals("file", content.getJsonObject(0).getString("type"));
        assertEquals("file-1", content.getJsonObject(0).getString("file_id"));
    }

    /**
     * Mistral reads an uploaded document with its own OCR pipeline, which the upload has to be filed under.
     */
    @Test
    void buildChatPayload_uploadedDocument_isFiledForOcr() {
        var service = uploadingService(false);
        var input = ChatInput.newBuilder().message("Read it").attach(pdf()).build();

        handler.buildChatPayload(service, input, ChatOptions.DEFAULT, false);

        verify(service).upload(any(), any());
        assertEquals("ocr", handler.getFileUploadMetadata(service, new Attachment(pdf(), MimeType.of("application/pdf"), "test.pdf")).get("purpose"));
    }

    private JsonArray uploadedDocumentContent(boolean signedUrl) {
        var service = uploadingService(signedUrl);
        var input = ChatInput.newBuilder().message("Read it").attach(pdf()).build();

        return handler.buildChatPayload(service, input, ChatOptions.DEFAULT, false).getJsonArray("messages").getJsonObject(0).getJsonArray("content");
    }

    /**
     * Stands in for a Mistral service which accepts an upload and, on the newer models, hands out a signed URL for it.
     */
    private static MistralAIService uploadingService(boolean signedUrl) {
        var service = mock(MistralAIService.class);
        when(service.getModelName()).thenReturn(signedUrl ? "mistral-medium-3-5" : "mistral-medium-2508");
        when(service.getModelVersion()).thenReturn(AIModelVersion.of("mistral", signedUrl ? 3 : 2508, signedUrl ? 5 : 0));
        when(service.supportsFileAttachments()).thenReturn(true);
        when(service.supportsOpenAIFilesApi()).thenReturn(true);
        when(service.supportsSignedUrl()).thenReturn(signedUrl);
        when(service.getSignedUrl(any())).thenReturn("https://example.org/signed");
        when(service.upload(any(), any())).thenReturn("file-1");
        return service;
    }

    private static byte[] pdf() {
        return "%PDF-1.4\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

}
