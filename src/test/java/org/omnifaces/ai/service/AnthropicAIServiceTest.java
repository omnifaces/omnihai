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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIModality.IMAGE_ANALYSIS;
import static org.omnifaces.ai.AIModality.IMAGE_GENERATION;
import static org.omnifaces.ai.AIModality.VIDEO_ANALYSIS;
import static org.omnifaces.ai.AIProvider.ANTHROPIC;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;

/**
 * Anthropic gates nearly every capability on the model version, and the request headers carry a beta flag per capability which is gated that way, so the
 * headers follow the configured model rather than a fixed set.
 */
class AnthropicAIServiceTest {

    private static final String API_KEY = "test-api-key";
    private static final String CLAUDE_2 = "claude-2.1";
    private static final String CLAUDE_3 = "claude-3-haiku-20240307";
    private static final String CLAUDE_3_7 = "claude-3-7-sonnet-20250219";
    private static final String CLAUDE_SONNET_4_5 = "claude-sonnet-4-5";
    private static final String CLAUDE_OPUS_4_7 = "claude-opus-4-7";

    @Test
    void supportsModality_servesImageAnalysisAlone() {
        var service = newService(CLAUDE_SONNET_4_5);

        assertTrue(service.supportsModality(IMAGE_ANALYSIS));
        assertFalse(service.supportsModality(IMAGE_GENERATION));
        assertFalse(service.supportsModality(VIDEO_ANALYSIS));
    }

    @Test
    void supportsStreamingAndFileAttachments_areGatedAtClaude3() {
        assertFalse(newService(CLAUDE_2).supportsStreaming());
        assertFalse(newService(CLAUDE_2).supportsFileAttachments());
        assertTrue(newService(CLAUDE_3).supportsStreaming());
        assertTrue(newService(CLAUDE_3).supportsFileAttachments());
    }

    @Test
    void supportsReasoningEffort_isGatedAtClaude37() {
        assertFalse(newService(CLAUDE_3).supportsReasoningEffort());
        assertTrue(newService(CLAUDE_3_7).supportsReasoningEffort());
    }

    @Test
    void supportsStructuredOutput_isGatedPerModelFamily() {
        assertFalse(newService(CLAUDE_3_7).supportsStructuredOutput());
        assertTrue(newService(CLAUDE_SONNET_4_5).supportsStructuredOutput());
    }

    @Test
    void supportsWebSearch_isGatedAtClaude4() {
        assertFalse(newService(CLAUDE_3_7).supportsWebSearch());
        assertTrue(newService(CLAUDE_SONNET_4_5).supportsWebSearch());
    }

    /**
     * The newest models reject the sampling parameters, so they are published as unsupported from Opus 4.7 and Claude 5 on.
     */
    @Test
    void supportsSamplingParameters_stopsAtTheNewestModels() {
        assertTrue(newService(CLAUDE_SONNET_4_5).supportsSamplingParameters());
        assertFalse(newService(CLAUDE_OPUS_4_7).supportsSamplingParameters());
    }

    @Test
    void getRequestHeaders_alwaysCarryTheApiKeyAndTheApiVersion() {
        var headers = newService(CLAUDE_2).getRequestHeaders();

        assertEquals(API_KEY, headers.get("x-api-key"));
        assertEquals("2023-06-01", headers.get("anthropic-version"));
    }

    /**
     * A model which serves neither the files API nor structured output must not announce a beta it cannot honor.
     */
    @Test
    void getRequestHeaders_modelWithoutBetaCapabilities_carryNoBetaHeader() {
        assertFalse(newService(CLAUDE_2).getRequestHeaders().containsKey("anthropic-beta"));
    }

    @Test
    void getRequestHeaders_modelWithFileAttachmentsAlone_carriesTheFilesBetaAlone() {
        assertEquals("files-api-2025-04-14", newService(CLAUDE_3).getRequestHeaders().get("anthropic-beta"));
    }

    @Test
    void getRequestHeaders_modelWithBothCapabilities_carriesBothBetas() {
        assertEquals(
            "files-api-2025-04-14,structured-outputs-2025-11-13", newService(CLAUDE_SONNET_4_5).getRequestHeaders().get("anthropic-beta")
        );
    }

    @Test
    void getChatPathAndFilesPath_areTheMessagesAndFilesEndpoints() {
        var service = newService(CLAUDE_SONNET_4_5);

        assertEquals("messages", service.getChatPath(false));
        assertEquals("messages", service.getChatPath(true));
        assertEquals("files", service.getFilesPath());
    }

    private static AnthropicAIService newService(String model) {
        return (AnthropicAIService) AIConfig.of(ANTHROPIC, API_KEY).withModel(model).createService();
    }

    /**
     * Anthropic states an uploaded file under names of its own, which the shared upload parsing is told about here.
     */
    @Test
    void getUploadedFileJsonStructure_namesThePropertiesAnthropicStatesThemUnder() {
        var structure = newService(CLAUDE_SONNET_4_5).getUploadedFileJsonStructure();

        assertEquals("data", structure.filesArrayProperty());
        assertEquals("filename", structure.fileNameProperty());
        assertEquals("id", structure.fileIdProperty());
        assertEquals("created_at", structure.createdAtProperty());
    }

}
