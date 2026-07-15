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

import java.util.HashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.ReasoningEffort;
import org.omnifaces.ai.service.MistralAIService;

/**
 * Default text handler for Mistral AI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see MistralAIService
 */
public class MistralAITextHandler extends OpenAITextHandler {

    private static final long serialVersionUID = 1L;

    @Override
    protected Map<String, String> getFileUploadMetadata(AIService service, Attachment file) {
        var metadata = new HashMap<>(super.getFileUploadMetadata(service, file));
        metadata.put("purpose", "ocr");
        return metadata;
    }

    /**
     * Mistral (2603) only supports NONE or HIGH.
     */
    @Override
    protected ReasoningEffort getEffectiveReasoningEffort(AIService service, ChatOptions options) {
        if (!service.supportsReasoningEffort()) {
            return ReasoningEffort.NONE;
        }

        return switch (options.getReasoningEffort()) {
            case AUTO, NONE, LOW -> ReasoningEffort.NONE;
            default -> ReasoningEffort.HIGH;
        };
    }

    /**
     * Mistral treats an absent {@code reasoning_effort} as no reasoning, so {@link ReasoningEffort#NONE} is redundant with the default and is omitted; only the
     * explicit {@code high} value is emitted.
     */
    @Override
    protected void addReasoningEffort(AIService service, JsonObjectBuilder payload, ReasoningEffort effort, boolean supportsResponsesApi) {
        if (effort != ReasoningEffort.NONE) {
            super.addReasoningEffort(service, payload, effort, supportsResponsesApi);
        }
    }

    /**
     * Newer Mistral chat models reference an uploaded document by a signed {@code document_url} rather than the bare {@code file_id} content block that legacy
     * dated models expect.
     */
    @Override
    protected void addUploadedFileContent(AIService service, JsonArrayBuilder content, String fileId, boolean supportsResponsesApi) {
        var mistral = (MistralAIService) service;

        if (mistral.supportsSignedUrl()) {
            content.add(
                Json.createObjectBuilder()
                    .add("type", "document_url")
                    .add("document_url", mistral.getSignedUrl(fileId))
            );
        }
        else {
            super.addUploadedFileContent(service, content, fileId, supportsResponsesApi);
        }
    }

}
