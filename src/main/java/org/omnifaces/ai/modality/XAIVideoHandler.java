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

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.model.GenerateVideoOptions;
import org.omnifaces.ai.model.GeneratedVideo;
import org.omnifaces.ai.service.XAIService;

/**
 * Default video handler for xAI service.
 * <p>
 * The submit response states the job id alone, and the poll response states the video as a temporary URL on a separate host, which the AI service downloads
 * without the API key.
 *
 * @author Bauke Scholtz
 * @since 1.7
 * @see XAIService
 */
public class XAIVideoHandler extends DefaultAIVideoHandler {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new instance of this AI handler.
     */
    public XAIVideoHandler() {
        //
    }

    /**
     * @see <a href="https://docs.x.ai/developers/model-capabilities/video/generation">API Reference</a>
     */
    @Override
    public JsonObject buildGenerateVideoPayload(AIService service, String prompt, GenerateVideoOptions options) {
        var payload = Json.createObjectBuilder()
            .add("model", service.getModelName())
            .add("prompt", prompt)
            .add("aspect_ratio", options.getAspectRatio());

        if (!options.useDefaultResolution()) {
            payload.add("resolution", options.getResolution());
        }

        if (!options.useDefaultSeconds()) {
            payload.add("duration", options.getSeconds());
        }

        return payload.build();
    }

    @Override
    public GeneratedVideo.Job parseSubmittedVideo(JsonObject responseJson) throws AIResponseException {
        var id = findFirstNonBlankByPath(responseJson, "request_id")
            .orElseThrow(() -> new AIResponseException("No video generation job id found", responseJson));
        return GeneratedVideo.Job.pending(id, null);
    }

    @Override
    public GeneratedVideo.Job parseGeneratedVideo(JsonObject responseJson, String jobId) throws AIResponseException {
        var status = findFirstNonBlankByPath(responseJson, "status")
            .orElseThrow(() -> new AIResponseException("No video generation job status found", responseJson));
        return new GeneratedVideo.Job(
            jobId, parseVideoStatus(status, responseJson), null, findFirstNonBlankByPath(responseJson, "video.url").orElse(null),
            findFirstNonBlankByPath(responseJson, "error.message").orElse(null)
        );
    }

}
