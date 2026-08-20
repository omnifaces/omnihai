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
import static org.omnifaces.ai.AIProvider.XAI;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.model.GenerateVideoOptions;
import org.omnifaces.ai.model.VideoGeneration.Status;

class XAIVideoHandlerTest {

    private final XAIVideoHandler handler = new XAIVideoHandler();

    @Test
    void buildGenerateVideoPayload_defaults_stateAspectRatioOnly() {
        var payload = handler.buildGenerateVideoPayload(newService(), "A cat playing piano", GenerateVideoOptions.DEFAULT);

        assertEquals("grok-imagine-video-1.5", payload.getString("model"));
        assertEquals("A cat playing piano", payload.getString("prompt"));
        assertEquals("16:9", payload.getString("aspect_ratio"));
        assertFalse(payload.containsKey("resolution"));
        assertFalse(payload.containsKey("duration"));
    }

    @Test
    void buildGenerateVideoPayload_statesDurationAsNumber() {
        var options = GenerateVideoOptions.newBuilder().aspectRatio("9:16").resolution("720p").seconds(8).build();
        var payload = handler.buildGenerateVideoPayload(newService(), "A cat playing piano", options);

        assertEquals("9:16", payload.getString("aspect_ratio"));
        assertEquals("720p", payload.getString("resolution"));
        assertEquals(8, payload.getInt("duration"), "xAI takes the duration as a number under its own name");
    }

    @Test
    void parseSubmittedVideo_takesTheRequestId() {
        var job = handler.parseSubmittedVideo(parseJson("{\"request_id\":\"d97415a1-5796-b7ec-379f-4e6819e08fdf\"}"));

        assertEquals("d97415a1-5796-b7ec-379f-4e6819e08fdf", job.id());
        assertEquals(Status.PENDING, job.status(), "the submit response states no status of its own");
    }

    @Test
    void parseSubmittedVideo_withoutRequestId_throwsException() {
        assertThrows(AIResponseException.class, () -> handler.parseSubmittedVideo(parseJson("{\"id\":\"nope\"}")));
    }

    @Test
    void parseVideoGeneration_whenDone_takesTheTemporaryUrl() {
        var responseJson = "{\"status\":\"done\",\"video\":{\"url\":\"https://vidgen.x.ai/abc/video.mp4\",\"duration\":8}}";
        var job = handler.parseVideoGeneration(parseJson(responseJson), "req-1");

        assertEquals(Status.COMPLETED, job.status());
        assertEquals("https://vidgen.x.ai/abc/video.mp4", job.contentPath(), "the video is hosted outside the API endpoint");
    }

    @Test
    void parseVideoGeneration_whenPending_hasNoContentYet() {
        var job = handler.parseVideoGeneration(parseJson("{\"status\":\"pending\"}"), "req-1");

        assertEquals(Status.PENDING, job.status());
        assertNull(job.contentPath());
    }

    @Test
    void parseVideoGeneration_whenExpired_isTerminal() {
        assertEquals(Status.EXPIRED, handler.parseVideoGeneration(parseJson("{\"status\":\"expired\"}"), "req-1").status());
    }

    @Test
    void parseVideoGeneration_whenFailed_takesTheErrorMessage() {
        var responseJson = "{\"status\":\"failed\",\"error\":{\"code\":\"invalid_argument\",\"message\":\"Prompt cannot be empty.\"}}";
        var job = handler.parseVideoGeneration(parseJson(responseJson), "req-1");

        assertEquals(Status.FAILED, job.status());
        assertEquals("Prompt cannot be empty.", job.failureReason());
    }

    private static AIService newService() {
        return AIConfig.of(XAI, "test-api-key").withModel("grok-imagine-video-1.5").createService();
    }

}
