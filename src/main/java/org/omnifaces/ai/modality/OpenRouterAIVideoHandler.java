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
import org.omnifaces.ai.model.VideoGeneration;
import org.omnifaces.ai.service.OpenRouterAIService;

/**
 * Default video handler for OpenRouter service.
 * <p>
 * OpenRouter names its own poll URL in the submit response and its own content URL in the poll response, both of which are absolute and take precedence over
 * the paths the AI service would derive from the job id.
 *
 * @author Bauke Scholtz
 * @since 1.7
 * @see OpenRouterAIService
 */
public class OpenRouterAIVideoHandler extends DefaultAIVideoHandler {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new instance of this AI handler.
     */
    public OpenRouterAIVideoHandler() {
        //
    }

    /**
     * @see <a href="https://openrouter.ai/docs/guides/overview/multimodal/video-generation">API Reference</a>
     */
    @Override
    public JsonObject buildGenerateVideoPayload(AIService service, String prompt, GenerateVideoOptions options) {
        var payload = Json.createObjectBuilder()
            .add("model", service.getModelName())
            .add("prompt", prompt)
            .add("aspect_ratio", options.getAspectRatio());

        if (!options.useDefaultSize()) {
            payload.add("size", options.getSize());
        }

        if (!options.useDefaultResolution()) {
            payload.add("resolution", options.getResolution());
        }

        if (!options.useDefaultSeconds()) {
            payload.add("duration", options.getSeconds());
        }

        return payload.build();
    }

    @Override
    public VideoGeneration.Job parseSubmittedVideo(JsonObject responseJson) throws AIResponseException {
        var id = findFirstNonBlankByPath(responseJson, "id").orElseThrow(() -> new AIResponseException("No video generation job id found", responseJson));
        var status = findFirstNonBlankByPath(responseJson, "status")
            .orElseThrow(() -> new AIResponseException("No video generation job status found", responseJson));
        return new VideoGeneration.Job(
            id, parseVideoStatus(status, responseJson), findFirstNonBlankByPath(responseJson, "polling_url").orElse(null), null, null
        );
    }

    @Override
    public VideoGeneration.Job parseVideoGeneration(JsonObject responseJson, String jobId) throws AIResponseException {
        var status = findFirstNonBlankByPath(responseJson, "status")
            .orElseThrow(() -> new AIResponseException("No video generation job status found", responseJson));
        return new VideoGeneration.Job(
            jobId, parseVideoStatus(status, responseJson), null, findFirstNonBlankByPath(responseJson, "unsigned_urls[0]").orElse(null),
            findFirstNonBlankByPath(responseJson, "error.message").orElse(null)
        );
    }

}
