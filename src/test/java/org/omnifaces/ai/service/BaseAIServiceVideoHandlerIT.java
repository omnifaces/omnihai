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
package org.omnifaces.ai.service;

import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.model.AnalyzeVideoOptions;
import org.opentest4j.TestAbortedException;

/**
 * Base class for IT on video-analysis-related methods of AI service.
 *
 * The test video shows a red frame during the first five seconds and a blue frame during the last five seconds.
 *
 * NOTE: this is a separate class from {@link BaseAIServiceImageHandlerIT} because video analysis might require a different model than image analysis.
 */
abstract class BaseAIServiceVideoHandlerIT extends AIServiceIT {

    private static final String COLORS_PROMPT = "Which colors are shown in this video? Answer with the color names only.";
    private static final String SINGLE_COLOR_PROMPT = "Which single color fills this video? Answer with exactly one word.";

    /**
     * Whether the AI provider honors {@link AnalyzeVideoOptions}. Providers taking a video as an opaque data URI have nowhere to put the sampling rate and the
     * clip offsets, and analyze the whole video regardless.
     *
     * @return Whether the AI provider honors the video analysis options.
     */
    protected boolean supportsVideoSampling() {
        return true;
    }

    @Test
    void analyzeVideoFromBytes() {
        var response = service.analyzeVideo(readAllBytes("/redblue.mp4"), COLORS_PROMPT);
        log(response);
        assertAll(
            () -> assertTrue(response.toLowerCase().contains("red"), "response must contain 'red'"),
            () -> assertTrue(response.toLowerCase().contains("blue"), "response must contain 'blue'")
        );
    }

    @Test
    void analyzeVideoFromPath() {
        var response = service.analyzeVideo(getPath("/redblue.mp4"), COLORS_PROMPT);
        log(response);
        assertAll(
            () -> assertTrue(response.toLowerCase().contains("red"), "response must contain 'red'"),
            () -> assertTrue(response.toLowerCase().contains("blue"), "response must contain 'blue'")
        );
    }

    @Test
    void analyzeVideoFromStartOffsetSkipsPrecedingClip() {
        if (!supportsVideoSampling()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var options = AnalyzeVideoOptions.newBuilder().startOffset(ofSeconds(6)).build();
        var response = service.analyzeVideo(getPath("/redblue.mp4"), SINGLE_COLOR_PROMPT, options);
        log(response);
        assertAll(
            () -> assertTrue(response.toLowerCase().contains("blue"), "response must contain 'blue'"),
            () -> assertFalse(response.toLowerCase().contains("red"), "response may not contain 'red'")
        );
    }

}
