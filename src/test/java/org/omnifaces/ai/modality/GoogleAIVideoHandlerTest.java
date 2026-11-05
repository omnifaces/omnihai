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
import static org.omnifaces.ai.AIProvider.GOOGLE;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.model.GenerateVideoOptions;
import org.omnifaces.ai.model.VideoGeneration.Status;

class GoogleAIVideoHandlerTest {

    private static final String MODEL = "gemini-omni-1.1-flash";
    private static final String INTERACTION_ID = "v1_abc123";
    private static final String VIDEO_DATA = "AAAAIGZ0eXBpc29t";
    private static final String USER_INPUT_STEP = "{\"type\":\"user_input\",\"content\":[{\"type\":\"text\",\"text\":\"Willemstad\"}]}";

    private final GoogleAIVideoHandler handler = new GoogleAIVideoHandler();

    @Test
    void buildGenerateVideoPayload_submitsTheModelAndPromptAsABackgroundInteraction() {
        var payload = handler.buildGenerateVideoPayload(newService(), "A cat playing piano", GenerateVideoOptions.DEFAULT);

        assertEquals(MODEL, payload.getString("model"), "an interaction does not carry the model in its path");
        assertEquals("A cat playing piano", payload.getString("input"));
        assertTrue(payload.getBoolean("background"), "a video takes longer than a request may, so it must be polled");
        assertEquals("video", payload.getJsonObject("response_format").getString("type"));
        assertEquals("16:9", payload.getJsonObject("response_format").getString("aspect_ratio"));
        assertFalse(payload.getJsonObject("response_format").containsKey("resolution"));
    }

    @Test
    void buildGenerateVideoPayload_statesTheResolutionButNotTheDuration() {
        var options = GenerateVideoOptions.newBuilder().resolution("720p").seconds(8).build();
        var payload = handler.buildGenerateVideoPayload(newService(), "A cat playing piano", options);

        assertEquals("720p", payload.getJsonObject("response_format").getString("resolution"));
        assertEquals(Set.of("model", "input", "background", "response_format"), payload.keySet(), "Gemini Omni has no duration option for a new video");
        assertEquals(Set.of("type", "aspect_ratio", "resolution"), payload.getJsonObject("response_format").keySet());
    }

    @Test
    void parseSubmittedVideo_pollsByTheInteractionId() {
        var job = handler.parseSubmittedVideo(parseJson("{\"id\":\"" + INTERACTION_ID + "\",\"status\":\"in_progress\",\"object\":\"interaction\"}"));

        assertEquals(INTERACTION_ID, job.id());
        assertNull(job.pollPath(), "the poll path is derived from the interaction id");
        assertEquals(Status.RUNNING, job.status());
    }

    @Test
    void parseSubmittedVideo_withoutId_throwsException() {
        var responseJson = parseJson("{\"status\":\"in_progress\"}");

        assertThrows(AIResponseException.class, () -> handler.parseSubmittedVideo(responseJson));
    }

    @Test
    void parseVideoGeneration_withoutStatus_throwsException() {
        var responseJson = parseJson("{\"id\":\"" + INTERACTION_ID + "\"}");

        assertThrows(AIResponseException.class, () -> handler.parseVideoGeneration(responseJson, INTERACTION_ID));
    }

    @Test
    void parseVideoGeneration_whenIncomplete_isFailedWithTheFirstError() {
        var responseJson = "{\"status\":\"incomplete\",\"errors\":[{\"message\":\"Output truncated\"}]}";
        var job = handler.parseVideoGeneration(parseJson(responseJson), INTERACTION_ID);

        assertEquals(Status.FAILED, job.status());
        assertEquals("Output truncated", job.failureReason());
    }

    @Test
    void parseVideoGeneration_withUnknownStatus_leavesTheStepsOutOfTheMessage() {
        var responseJson = parseJson(
            "{\"status\":\"requires_continuation\",\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"video\",\"data\":\"" + VIDEO_DATA + "\"}]}]}"
        );

        var exception = assertThrows(AIResponseException.class, () -> handler.parseVideoGeneration(responseJson, INTERACTION_ID));

        assertFalse(exception.getMessage().contains(VIDEO_DATA), exception.getMessage());
    }

    @Test
    void parseVideoGeneration_withUnknownStatus_throwsException() {
        var responseJson = parseJson("{\"id\":\"" + INTERACTION_ID + "\",\"status\":\"exploded\"}");

        assertThrows(AIResponseException.class, () -> handler.parseVideoGeneration(responseJson, INTERACTION_ID));
    }

    @Test
    void parseVideoGeneration_whenFailed_statesTheFirstError() {
        var responseJson = "{\"status\":\"failed\",\"steps\":[" + USER_INPUT_STEP
            + "],\"errors\":[{\"message\":\"Quota exceeded\",\"code\":\"RESOURCE_EXHAUSTED\"}]}";
        var job = handler.parseVideoGeneration(parseJson(responseJson), INTERACTION_ID);

        assertEquals(Status.FAILED, job.status());
        assertEquals("Quota exceeded", job.failureReason());
    }

    @Test
    void parseVideoGeneration_whenCompleted_carriesTheInlineVideoAsDataUri() {
        var responseJson = "{\"status\":\"completed\",\"steps\":[" + USER_INPUT_STEP
            + ",{\"type\":\"thought\",\"content\":[{\"type\":\"thought\",\"text\":\"...\"}]}"
            + ",{\"type\":\"model_output\",\"content\":[{\"type\":\"video\",\"mime_type\":\"video/webm\",\"data\":\"" + VIDEO_DATA + "\"}]}]}";
        var job = handler.parseVideoGeneration(parseJson(responseJson), INTERACTION_ID);

        assertEquals(Status.COMPLETED, job.status());
        assertEquals("data:video/webm;base64," + VIDEO_DATA, job.contentPath());
    }

    @Test
    void parseVideoGeneration_whenCompletedWithoutMimeType_assumesMp4() {
        var responseJson = "{\"status\":\"completed\",\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"video\",\"data\":\"" + VIDEO_DATA
            + "\"}]}]}";
        var job = handler.parseVideoGeneration(parseJson(responseJson), INTERACTION_ID);

        assertEquals("data:video/mp4;base64," + VIDEO_DATA, job.contentPath());
    }

    @Test
    void parseVideoGeneration_whenCompletedWithoutVideo_throwsException() {
        var responseJson = parseJson("{\"status\":\"completed\",\"steps\":[" + USER_INPUT_STEP + "]}");

        assertThrows(AIResponseException.class, () -> handler.parseVideoGeneration(responseJson, INTERACTION_ID));
    }

    @Test
    void parseVideoGeneration_whenCompletedWithoutInlineData_throwsException() {
        var responseJson = parseJson(
            "{\"status\":\"completed\",\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"video\",\"uri\":\"files/x\"}]}]}"
        );

        assertThrows(AIResponseException.class, () -> handler.parseVideoGeneration(responseJson, INTERACTION_ID));
    }

    @Test
    void parseVideoGeneration_whenCompletedWithMalformedSteps_throwsException() {
        var stepsWhichIsNoArray = parseJson("{\"status\":\"completed\",\"steps\":\"x\"}");
        var stepWhichIsNoObjectAndContentWhichIsNoArray = parseJson("{\"status\":\"completed\",\"steps\":[1,{\"type\":\"model_output\",\"content\":{}}]}");

        assertThrows(AIResponseException.class, () -> handler.parseVideoGeneration(stepsWhichIsNoArray, INTERACTION_ID));
        assertThrows(AIResponseException.class, () -> handler.parseVideoGeneration(stepWhichIsNoObjectAndContentWhichIsNoArray, INTERACTION_ID));
    }

    /**
     * The content of a step which is not where the video is expected may still hold the whole video, which must never end up in an exception message.
     */
    @Test
    void parseVideoGeneration_whenCompletedWithVideoElsewhere_describesTheStepsWithoutTheirContent() {
        var responseJson = parseJson(
            "{\"status\":\"completed\",\"steps\":[" + USER_INPUT_STEP
                + ",{\"type\":\"output\",\"content\":[{\"type\":\"video\",\"data\":\"" + VIDEO_DATA + "\"}]},7]}"
        );

        var exception = assertThrows(AIResponseException.class, () -> handler.parseVideoGeneration(responseJson, INTERACTION_ID));

        assertFalse(exception.getMessage().contains(VIDEO_DATA), exception.getMessage());
        assertTrue(exception.getMessage().contains("steps=[user_input[text], output[video], ?]"), exception.getMessage());
    }

    private static AIService newService() {
        return AIConfig.of(GOOGLE, "test-api-key").withModel(MODEL).createService();
    }

}
