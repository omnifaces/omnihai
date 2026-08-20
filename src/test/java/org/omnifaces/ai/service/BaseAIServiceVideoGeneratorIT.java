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

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.GenerateVideoOptions;

/**
 * Base class for IT on video-generator-related methods of AI service.
 *
 * NOTE: this is a separate class from {@link BaseAIServiceVideoHandlerIT} because video generation requires a different model than video analysis.
 */
abstract class BaseAIServiceVideoGeneratorIT extends AIServiceIT {

    private static final String PROMPT = "Willemstad, Curacao";
    private static final int MINIMUM_VIDEO_SIZE = 1024;

    /** Bounds the test above what the library itself waits, so that its own deadline is what fails a job which never finishes. */
    private static final int MAX_WAIT_MINUTES = GenerateVideoOptions.DEFAULT_MAX_WAIT_MINUTES + 1;

    /** The options to generate with; kept at the shortest and smallest the provider allows, as every run costs money and minutes. */
    protected GenerateVideoOptions getOptions() {
        return GenerateVideoOptions.DEFAULT;
    }

    @Test
    void generateVideo_returnsAPendingHandleWhichAnotherRequestCanRevive() {
        var video = service.generateVideo(PROMPT, getOptions());

        assertNotNull(video.jobId(), "the job id is what a later request polls by");
        assertFalse(video.status().isTerminal(), "the job cannot be done yet, but was " + video.status());
        assertThrows(IllegalStateException.class, () -> video.writeTo(OutputStream.nullOutputStream()), "an unfinished job has nothing to write");

        var revived = service.findGeneratedVideo(video.jobId()).refresh();

        assertEquals(video.jobId(), revived.jobId());
        assertNotNull(revived.status(), "the revived job must be pollable by its id alone");
    }

    @Test
    void generateVideoAsync_waitsForTheVideoAndWritesIt() throws Exception {
        var targetDir = Path.of(System.getProperty("user.dir"), "target", "omnihai-video-generator-test-results");
        targetDir.toFile().mkdirs();
        var path = targetDir.resolve(getClass().getSimpleName() + ".mp4"); // Not created up front, so a failed run leaves nothing behind.

        service.generateVideoAsync(PROMPT, path, getOptions()).get(MAX_WAIT_MINUTES, MINUTES);
        log("saved in " + path);

        var size = Files.size(path);
        assertTrue(size > MINIMUM_VIDEO_SIZE, "Generated video should not be near-empty, but was " + size + " bytes");
        assertTrue(MimeType.guessMimeType(Files.readAllBytes(path)).isVideo(), "Mime type is video");
    }

}
