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
package org.omnifaces.ai;

import java.io.Serializable;

import jakarta.json.JsonObject;

import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.modality.DefaultAIVideoHandler;
import org.omnifaces.ai.model.GenerateVideoOptions;
import org.omnifaces.ai.model.GeneratedVideo;

/**
 * Handler for video-based AI operations.
 * <p>
 * Covers:
 * <ul>
 * <li>detailed video analysis / description / VQA</li>
 * <li>video generation</li>
 * </ul>
 * <p>
 * The frame sampling rate and the clip offsets of {@link org.omnifaces.ai.model.AnalyzeVideoOptions} are carried by the attachment itself, and are therefore
 * rendered by the {@link AITextHandler} which builds the content parts of the chat payload.
 * <p>
 * The implementations must be stateless and able to be {@code jakarta.enterprise.context.ApplicationScoped}.
 *
 * @author Bauke Scholtz
 * @since 1.7
 * @see AIService
 * @see DefaultAIVideoHandler
 */
public interface AIVideoHandler extends Serializable {

    /**
     * Builds the default system prompt to use when no custom user prompt is provided to {@link AIService#analyzeVideo(byte[], String)} or any of its overloads.
     *
     * @return The general-purpose video analysis prompt.
     */
    String buildAnalyzeVideoPrompt();

    /**
     * Builds the JSON request payload for all generate video operations.
     *
     * @implNote The default implementation throws UnsupportedOperationException.
     * @param service The visiting AI service.
     * @param prompt The video generation prompt.
     * @param options The video generation options.
     * @return The JSON request payload.
     */
    default JsonObject buildGenerateVideoPayload(AIService service, String prompt, GenerateVideoOptions options) {
        throw new UnsupportedOperationException(
            "Please implement buildGenerateVideoPayload(AIService service, String prompt, GenerateVideoOptions options) for this AI provider"
        );
    }

    /**
     * Parses the newly submitted video generation job from the API response JSON of the generate video operation. The returned job must state the id which the
     * AI provider assigned to it, and may state the path to poll it at when the AI provider names its own.
     *
     * @implNote The default implementation throws UnsupportedOperationException.
     * @param responseJson The API response JSON.
     * @return The submitted video generation job.
     * @throws AIResponseException If the response JSON contains an error object, or is missing the job id.
     */
    default GeneratedVideo.Job parseSubmittedVideo(JsonObject responseJson) throws AIResponseException {
        throw new UnsupportedOperationException("Please implement parseSubmittedVideo(JsonObject responseJson) for this AI provider");
    }

    /**
     * Parses the current state of a video generation job from the API response JSON of the poll operation. The job id is supplied separately, as not every AI
     * provider repeats it in the poll response.
     *
     * @implNote The default implementation throws UnsupportedOperationException.
     * @param responseJson The API response JSON.
     * @param jobId The id of the polled job.
     * @return The current state of the video generation job.
     * @throws AIResponseException If the response JSON contains an error object, or is missing the job status.
     */
    default GeneratedVideo.Job parseGeneratedVideo(JsonObject responseJson, String jobId) throws AIResponseException {
        throw new UnsupportedOperationException("Please implement parseGeneratedVideo(JsonObject responseJson, String jobId) for this AI provider");
    }

}
