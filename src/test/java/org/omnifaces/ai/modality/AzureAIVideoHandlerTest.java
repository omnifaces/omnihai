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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.omnifaces.ai.AIProvider.AZURE;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.model.GenerateVideoOptions;
import org.omnifaces.ai.model.GeneratedVideo.Status;
import org.omnifaces.ai.service.AzureAIService;

class AzureAIVideoHandlerTest {

    private final AzureAIVideoHandler handler = new AzureAIVideoHandler();

    @Test
    void buildGenerateVideoPayload_defaults_stateTheLandscapeFrameSizeTheAspectRatioAmountsTo() {
        var payload = handler.buildGenerateVideoPayload(newService(), "A cat playing piano", GenerateVideoOptions.DEFAULT);

        assertEquals("sora-2", payload.getString("model"));
        assertEquals(1280, payload.getInt("width"), "Azure OpenAI takes no aspect ratio, so the default 16:9 must reach it as a landscape frame size");
        assertEquals(720, payload.getInt("height"));
        assertFalse(payload.containsKey("n_seconds"));
    }

    @Test
    void buildGenerateVideoPayload_portraitAspectRatio_statesThePortraitFrameSize() {
        var options = GenerateVideoOptions.newBuilder().aspectRatio("9:16").build();
        var payload = handler.buildGenerateVideoPayload(newService(), "A cat playing piano", options);

        assertEquals(720, payload.getInt("width"));
        assertEquals(1280, payload.getInt("height"));
    }

    @Test
    void buildGenerateVideoPayload_squareAspectRatio_omitsTheFrameSize() {
        var options = GenerateVideoOptions.newBuilder().aspectRatio("1:1").build();
        var payload = handler.buildGenerateVideoPayload(newService(), "A cat playing piano", options);

        assertFalse(payload.containsKey("width"), "Azure OpenAI is left to apply its own frame size for a square aspect ratio");
    }

    @Test
    void buildGenerateVideoPayload_splitsSizeIntoWidthAndHeight() {
        var options = GenerateVideoOptions.newBuilder().size("1920x1080").seconds(10).build();
        var payload = handler.buildGenerateVideoPayload(newService(), "A cat playing piano", options);

        assertEquals(1920, payload.getInt("width"));
        assertEquals(1080, payload.getInt("height"));
        assertEquals(10, payload.getInt("n_seconds"), "Azure OpenAI takes the duration under its own name");
    }

    @Test
    void parseGeneratedVideo_whenSucceeded_keysTheDownloadByTheGenerationId() {
        var responseJson = "{\"id\":\"task_1\",\"status\":\"succeeded\",\"generations\":[{\"id\":\"gen_2\"}]}";
        var job = handler.parseGeneratedVideo(parseJson(responseJson), "task_1");

        assertEquals(Status.COMPLETED, job.status());
        assertEquals("video/generations/gen_2/content/video?api-version=preview", job.contentPath(), "keyed by the generation, not by the job");
    }

    @Test
    void parseGeneratedVideo_whenSucceededWithoutGeneration_throwsException() {
        assertThrows(AIResponseException.class, () -> handler.parseGeneratedVideo(parseJson("{\"status\":\"succeeded\"}"), "task_1"));
    }

    @Test
    void parseGeneratedVideo_whenPreprocessing_isPending() {
        assertEquals(Status.PENDING, handler.parseGeneratedVideo(parseJson("{\"status\":\"preprocessing\"}"), "task_1").status());
    }

    @Test
    void parseGeneratedVideo_whenCancelled_isFailed() {
        assertEquals(Status.FAILED, handler.parseGeneratedVideo(parseJson("{\"status\":\"cancelled\"}"), "task_1").status());
    }

    @Test
    void parseGeneratedVideo_takesTheFailureReason() {
        var job = handler.parseGeneratedVideo(parseJson("{\"status\":\"failed\",\"failure_reason\":\"content_filter\"}"), "task_1");

        assertEquals("content_filter", job.failureReason());
    }

    @Test
    void parseSubmittedVideo_takesIdAndStatus() {
        var job = handler.parseSubmittedVideo(parseJson("{\"id\":\"task_1\",\"status\":\"queued\"}"));

        assertEquals("task_1", job.id());
        assertEquals(Status.PENDING, job.status());
    }

    private static AIService newService() {
        return AIConfig.of(AZURE, "test-api-key").withProperty(AzureAIService.OPTION_AZURE_RESOURCE, "test-resource").withModel("sora-2").createService();
    }

}
