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
import static org.omnifaces.ai.AIProvider.GOOGLE;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.model.GenerateVideoOptions;
import org.omnifaces.ai.model.GeneratedVideo.Status;

class GoogleAIVideoHandlerTest {

    private static final String OPERATION_NAME = "models/veo-3.1-generate-preview/operations/abc123";
    private static final String VIDEO_URI = "https://generativelanguage.googleapis.com/v1beta/files/xyz:download?alt=media";

    private final GoogleAIVideoHandler handler = new GoogleAIVideoHandler();

    @Test
    void buildGenerateVideoPayload_wrapsThePromptInAnInstance() {
        var payload = handler.buildGenerateVideoPayload(newService(), "A cat playing piano", GenerateVideoOptions.DEFAULT);

        assertEquals("A cat playing piano", payload.getJsonArray("instances").getJsonObject(0).getString("prompt"));
        assertEquals("16:9", payload.getJsonObject("parameters").getString("aspectRatio"));
        assertFalse(payload.getJsonObject("parameters").containsKey("resolution"));
        assertFalse(payload.getJsonObject("parameters").containsKey("durationSeconds"));
    }

    @Test
    void buildGenerateVideoPayload_statesParametersInCamelCase() {
        var options = GenerateVideoOptions.newBuilder().resolution("720p").seconds(8).build();
        var parameters = handler.buildGenerateVideoPayload(newService(), "A cat playing piano", options).getJsonObject("parameters");

        assertEquals("720p", parameters.getString("resolution"));
        assertEquals(8, parameters.getInt("durationSeconds"));
    }

    @Test
    void parseSubmittedVideo_pollsTheOperationItNames() {
        var job = handler.parseSubmittedVideo(parseJson("{\"name\":\"" + OPERATION_NAME + "\"}"));

        assertEquals(OPERATION_NAME, job.id());
        assertEquals(OPERATION_NAME, job.pollPath(), "the operation name is the poll path");
        assertEquals(Status.PENDING, job.status());
    }

    @Test
    void parseSubmittedVideo_withoutName_throwsException() {
        assertThrows(AIResponseException.class, () -> handler.parseSubmittedVideo(parseJson("{\"id\":\"nope\"}")));
    }

    @Test
    void parseGeneratedVideo_whenNotDone_isRunning() {
        var job = handler.parseGeneratedVideo(parseJson("{\"name\":\"" + OPERATION_NAME + "\"}"), OPERATION_NAME);

        assertEquals(Status.RUNNING, job.status(), "an operation states completion as a flag rather than as a status word");
    }

    @Test
    void parseGeneratedVideo_whenDone_takesTheVideoUri() {
        var responseJson = "{\"done\":true,\"response\":{\"generateVideoResponse\":{\"generatedSamples\":[{\"video\":{\"uri\":\"" + VIDEO_URI + "\"}}]}}}";
        var job = handler.parseGeneratedVideo(parseJson(responseJson), OPERATION_NAME);

        assertEquals(Status.COMPLETED, job.status());
        assertEquals(VIDEO_URI, job.contentPath());
    }

    @Test
    void parseGeneratedVideo_whenDoneWithoutUri_throwsException() {
        assertThrows(AIResponseException.class, () -> handler.parseGeneratedVideo(parseJson("{\"done\":true,\"response\":{}}"), OPERATION_NAME));
    }

    @Test
    void parseGeneratedVideo_whenErrored_isFailed() {
        var job = handler.parseGeneratedVideo(parseJson("{\"error\":{\"code\":3,\"message\":\"Prompt rejected\"}}"), OPERATION_NAME);

        assertEquals(Status.FAILED, job.status());
        assertEquals("Prompt rejected", job.failureReason());
    }

    private static AIService newService() {
        return AIConfig.of(GOOGLE, "test-api-key").withModel("veo-3.1-generate-preview").createService();
    }

}
