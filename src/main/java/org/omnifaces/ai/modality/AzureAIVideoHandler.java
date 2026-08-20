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
import static org.omnifaces.ai.model.GeneratedVideo.Status.COMPLETED;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.model.GenerateVideoOptions;
import org.omnifaces.ai.model.GeneratedVideo;
import org.omnifaces.ai.service.AzureAIService;

/**
 * Default video handler for Azure OpenAI service.
 * <p>
 * Azure OpenAI keys the download by the id of the generation rather than by the id of the job, and takes the frame size as a separate width and height. It
 * offers no aspect ratio of its own, so an unset size falls back to the size which the aspect ratio amounts to.
 *
 * @author Bauke Scholtz
 * @since 1.7
 * @see AzureAIService
 */
public class AzureAIVideoHandler extends DefaultAIVideoHandler {

    private static final long serialVersionUID = 1L;

    private static final String SIZE_SEPARATOR = "x";
    private static final String GENERATION_ID_PATH = "generations[0].id";

    /**
     * Constructs a new instance of this AI handler.
     */
    public AzureAIVideoHandler() {
        //
    }

    /**
     * @see <a href="https://learn.microsoft.com/en-us/azure/ai-foundry/openai/video-generation-quickstart">API Reference</a>
     */
    @Override
    public JsonObject buildGenerateVideoPayload(AIService service, String prompt, GenerateVideoOptions options) {
        var payload = Json.createObjectBuilder()
            .add("model", service.getModelName())
            .add("prompt", prompt);

        findSize(options).map(size -> size.split(SIZE_SEPARATOR, 2))
            .ifPresent(size -> payload.add("width", Integer.parseInt(size[0])).add("height", Integer.parseInt(size[1])));

        if (!options.useDefaultSeconds()) {
            payload.add("n_seconds", options.getSeconds());
        }

        return payload.build();
    }

    @Override
    public GeneratedVideo.Job parseSubmittedVideo(JsonObject responseJson) throws AIResponseException {
        var id = findFirstNonBlankByPath(responseJson, "id").orElseThrow(() -> new AIResponseException("No video generation job id found", responseJson));
        return parseGeneratedVideo(responseJson, id);
    }

    @Override
    public GeneratedVideo.Job parseGeneratedVideo(JsonObject responseJson, String jobId) throws AIResponseException {
        var status = findFirstNonBlankByPath(responseJson, "status")
            .orElseThrow(() -> new AIResponseException("No video generation job status found", responseJson));
        var parsedStatus = parseVideoStatus(status, responseJson);
        var generationId = findFirstNonBlankByPath(responseJson, GENERATION_ID_PATH);

        if (parsedStatus == COMPLETED && generationId.isEmpty()) {
            throw new AIResponseException("No video generation id found at path " + GENERATION_ID_PATH, responseJson);
        }

        var failureReason = findFirstNonBlankByPath(responseJson, "failure_reason").or(() -> findFirstNonBlankByPath(responseJson, "error.message"))
            .orElse(null);
        return new GeneratedVideo.Job(jobId, parsedStatus, null, generationId.map(AzureAIService::getVideoGenerationContentPath).orElse(null), failureReason);
    }

}
