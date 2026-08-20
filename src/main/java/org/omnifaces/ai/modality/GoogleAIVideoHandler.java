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

import static org.omnifaces.ai.helper.JsonHelper.findFirstNonBlankByPath;
import static org.omnifaces.ai.model.VideoGeneration.Status.COMPLETED;
import static org.omnifaces.ai.model.VideoGeneration.Status.FAILED;
import static org.omnifaces.ai.model.VideoGeneration.Status.RUNNING;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.model.GenerateVideoOptions;
import org.omnifaces.ai.model.VideoGeneration;
import org.omnifaces.ai.service.GoogleAIService;

/**
 * Default video handler for Google AI service.
 * <p>
 * Veo is submitted as a long running operation rather than as a video job: the submit response names the operation to poll, and the poll response states
 * completion as a {@code done} flag rather than as a status word. The generated video is hosted on a URI which the poll response states.
 *
 * @author Bauke Scholtz
 * @since 1.7
 * @see GoogleAIService
 */
public class GoogleAIVideoHandler extends DefaultAIVideoHandler {

    private static final long serialVersionUID = 1L;

    private static final String VIDEO_URI_PATH = "response.generateVideoResponse.generatedSamples[0].video.uri";

    /**
     * Constructs a new instance of this AI handler.
     */
    public GoogleAIVideoHandler() {
        //
    }

    /**
     * @see <a href="https://ai.google.dev/gemini-api/docs/veo">API Reference</a>
     */
    @Override
    public JsonObject buildGenerateVideoPayload(AIService service, String prompt, GenerateVideoOptions options) {
        var parameters = Json.createObjectBuilder().add("aspectRatio", options.getAspectRatio());

        if (!options.useDefaultResolution()) {
            parameters.add("resolution", options.getResolution());
        }

        if (!options.useDefaultSeconds()) {
            parameters.add("durationSeconds", options.getSeconds());
        }

        return Json.createObjectBuilder()
            .add("instances", Json.createArrayBuilder().add(Json.createObjectBuilder().add("prompt", prompt)))
            .add("parameters", parameters)
            .build();
    }

    @Override
    public VideoGeneration.Job parseSubmittedVideo(JsonObject responseJson) throws AIResponseException {
        var name = findFirstNonBlankByPath(responseJson, "name")
            .orElseThrow(() -> new AIResponseException("No video generation operation name found", responseJson));
        return VideoGeneration.Job.pending(name, name);
    }

    @Override
    public VideoGeneration.Job parseVideoGeneration(JsonObject responseJson, String jobId) throws AIResponseException {
        var failureReason = findFirstNonBlankByPath(responseJson, "error.message").orElse(null);

        if (failureReason != null) {
            return new VideoGeneration.Job(jobId, FAILED, jobId, null, failureReason);
        }

        if (!responseJson.getBoolean("done", false)) {
            return new VideoGeneration.Job(jobId, RUNNING, jobId, null, null);
        }

        var uri = findFirstNonBlankByPath(responseJson, VIDEO_URI_PATH)
            .orElseThrow(() -> new AIResponseException("No generated video URI found at path " + VIDEO_URI_PATH, responseJson));
        return new VideoGeneration.Job(jobId, COMPLETED, jobId, uri, null);
    }

}
