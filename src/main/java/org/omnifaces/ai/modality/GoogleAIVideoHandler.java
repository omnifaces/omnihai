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

import static org.omnifaces.ai.helper.DataUriHelper.toDataUri;
import static org.omnifaces.ai.helper.JsonHelper.findFirstNonBlankByPath;
import static org.omnifaces.ai.model.VideoGeneration.Status.COMPLETED;

import java.util.StringJoiner;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.model.GenerateVideoOptions;
import org.omnifaces.ai.model.VideoGeneration;
import org.omnifaces.ai.service.GoogleAIService;

/**
 * Default video handler for Google AI service.
 * <p>
 * Gemini Omni is submitted as a background interaction: the submit response states the interaction id to poll, and the poll response states the status as a
 * word. The poll response of a completed interaction carries the generated video inline as Base64, which is handed out as a {@code data:} URI.
 * <p>
 * Gemini Omni has no duration option for a new video, so {@link GenerateVideoOptions#getSeconds()} is ignored, and {@link GenerateVideoOptions#getSize()}
 * reaches the request only through its aspect ratio.
 *
 * @author Bauke Scholtz
 * @since 1.7
 * @see GoogleAIService
 */
public class GoogleAIVideoHandler extends DefaultAIVideoHandler {

    private static final long serialVersionUID = 1L;

    private static final String VIDEO_TYPE = "video";
    private static final String MODEL_OUTPUT_STEP_TYPE = "model_output";
    private static final String DEFAULT_VIDEO_MIME_TYPE = "video/mp4";
    private static final String FAILURE_REASON_PATH = "errors[0].message";

    /**
     * Constructs a new instance of this AI handler.
     */
    public GoogleAIVideoHandler() {
        //
    }

    /**
     * @see <a href="https://ai.google.dev/gemini-api/docs/omni">API Reference</a>
     */
    @Override
    public JsonObject buildGenerateVideoPayload(AIService service, String prompt, GenerateVideoOptions options) {
        var responseFormat = Json.createObjectBuilder().add("type", VIDEO_TYPE).add("aspect_ratio", options.getAspectRatio());

        if (!options.useDefaultResolution()) {
            responseFormat.add("resolution", options.getResolution());
        }

        return Json.createObjectBuilder()
            .add("model", service.getModelName())
            .add("input", prompt)
            .add("background", true)
            .add("response_format", responseFormat)
            .build();
    }

    @Override
    public VideoGeneration.Job parseSubmittedVideo(JsonObject responseJson) throws AIResponseException {
        var id = findFirstNonBlankByPath(responseJson, "id")
            .orElseThrow(() -> new AIResponseException("No video generation interaction id found", responseJson));
        return parseVideoGeneration(responseJson, id);
    }

    @Override
    public VideoGeneration.Job parseVideoGeneration(JsonObject responseJson, String jobId) throws AIResponseException {
        var status = parseVideoStatus(Json.createObjectBuilder(responseJson).remove("steps").build());

        if (status != COMPLETED) {
            return new VideoGeneration.Job(jobId, status, null, null, findFirstNonBlankByPath(responseJson, FAILURE_REASON_PATH).orElse(null));
        }

        var video = findGeneratedVideo(responseJson);
        var data = findFirstNonBlankByPath(video, "data")
            .orElseThrow(() -> new AIResponseException("No inline generated video data found", describeSteps(responseJson)));
        var dataUri = toDataUri(findFirstNonBlankByPath(video, "mime_type").orElse(DEFAULT_VIDEO_MIME_TYPE), data);
        return new VideoGeneration.Job(jobId, COMPLETED, null, dataUri, null);
    }

    private static JsonObject findGeneratedVideo(JsonObject responseJson) throws AIResponseException {
        for (var step : arrayOf(responseJson, "steps")) {
            if (step instanceof JsonObject stepObject && MODEL_OUTPUT_STEP_TYPE.equals(stepObject.getString("type", null))) {
                for (var content : arrayOf(stepObject, "content")) {
                    if (content instanceof JsonObject contentObject && VIDEO_TYPE.equals(contentObject.getString("type", null))) {
                        return contentObject;
                    }
                }
            }
        }

        throw new AIResponseException("No generated video found in the interaction steps", describeSteps(responseJson));
    }

    /**
     * Describes the steps of a completed interaction by the types of their content alone, as the content of an unexpected step may hold the whole video.
     */
    private static String describeSteps(JsonObject responseJson) {
        var steps = new StringJoiner(", ", "[", "]");

        for (var step : arrayOf(responseJson, "steps")) {
            var contents = new StringJoiner(", ", "[", "]");

            if (step instanceof JsonObject stepObject) {
                for (var content : arrayOf(stepObject, "content")) {
                    contents.add(content instanceof JsonObject contentObject ? contentObject.getString("type", "?") : "?");
                }

                steps.add(stepObject.getString("type", "?") + contents);
            }
            else {
                steps.add("?");
            }
        }

        return "steps=" + steps;
    }

    private static JsonArray arrayOf(JsonObject object, String key) {
        return object.get(key) instanceof JsonArray array ? array : JsonValue.EMPTY_JSON_ARRAY;
    }

}
