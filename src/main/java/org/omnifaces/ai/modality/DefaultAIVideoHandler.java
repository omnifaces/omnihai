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

import java.util.Locale;
import java.util.Optional;

import jakarta.json.JsonObject;

import org.omnifaces.ai.AIVideoHandler;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.model.GenerateVideoOptions;
import org.omnifaces.ai.model.GeneratedVideo.Status;

/**
 * Default video handler, holding the provider-independent video analysis prompt and job status vocabulary.
 * <p>
 * This class is intended as a fallback when no provider-specific implementation is available.
 *
 * @author Bauke Scholtz
 * @since 1.7
 * @see AIVideoHandler
 */
public class DefaultAIVideoHandler implements AIVideoHandler {

    private static final long serialVersionUID = 1L;

    /** The 720p size of a portrait video, for an AI provider which takes a pixel size but no aspect ratio. */
    private static final String PORTRAIT_SIZE = "720x1280";

    /** The 720p size of a landscape video, for an AI provider which takes a pixel size but no aspect ratio. */
    private static final String LANDSCAPE_SIZE = "1280x720";

    /**
     * Constructs a new instance of this AI handler.
     */
    public DefaultAIVideoHandler() {
        //
    }

    @Override
    public String buildAnalyzeVideoPrompt() {
        return """
                You are an expert at analyzing videos.
                Describe this video in detail.
                Rules:
                - Focus on: main subject, what happens over time, spoken content if any, visual style if relevant, and intended purpose.
                - Refer to a moment in the video by its timestamp.
                Output format:
                - Plain text description only.
                - No explanations, no notes, no extra text, no markdown formatting.
            """;
    }

    /**
     * Returns the size to generate at for an AI provider which takes a pixel size but no aspect ratio of its own: the stated size, or the 720p size which the
     * stated aspect ratio amounts to, or empty for a square aspect ratio, which such an AI provider generally offers no size for.
     *
     * @param options The video generation options.
     * @return The size to generate at, or empty to leave the choice to the AI provider.
     * @since 1.7
     */
    protected static Optional<String> findSize(GenerateVideoOptions options) {
        if (!options.useDefaultSize()) {
            return Optional.of(options.getSize());
        }

        if (options.isPortrait()) {
            return Optional.of(PORTRAIT_SIZE);
        }

        return options.isLandscape() ? Optional.of(LANDSCAPE_SIZE) : Optional.empty();
    }

    /**
     * Maps the job status word of a video generation API onto the library's own status. The recognized words span every supported AI provider, as none of them
     * uses a word for a status another one means differently.
     *
     * @param status The job status word as stated by the AI provider.
     * @param responseJson The API response JSON it was stated in, for error reporting.
     * @return The matching status.
     * @throws AIResponseException If the status word is not recognized.
     * @since 1.7
     */
    protected static Status parseVideoStatus(String status, JsonObject responseJson) throws AIResponseException {
        return switch (status == null ? "" : status.toLowerCase(Locale.ROOT)) {
            case "queued", "pending", "preprocessing", "not_started" -> Status.PENDING;
            case "in_progress", "running", "processing" -> Status.RUNNING;
            case "completed", "succeeded", "success", "done" -> Status.COMPLETED;
            case "failed", "cancelled", "canceled" -> Status.FAILED;
            case "expired" -> Status.EXPIRED;
            default -> throw new AIResponseException("Unknown video generation job status " + status, responseJson);
        };
    }

}
