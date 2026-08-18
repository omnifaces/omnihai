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

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omnifaces.ai.model.ChatInput.Attachment;

class GoogleAIServiceTest {

    private static final long MEGABYTE = 1024L * 1024L;

    @TempDir
    private Path tempDir;

    /** Returns an attachment of the given size, backed by a sparse file so that a large size costs neither heap nor disk. */
    private Attachment newAttachment(long size) {
        var file = tempDir.resolve(size + ".mp4");

        try (var sparseFile = new RandomAccessFile(file.toFile(), "rw")) {
            sparseFile.setLength(size);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new Attachment(file);
    }

    @Test
    void maxProcessingTime_smallFile_isFlooredAtOneMinute() {
        assertEquals(ofMinutes(1), GoogleAIService.maxProcessingTime(newAttachment(1024)));
        assertEquals(ofMinutes(1), GoogleAIService.maxProcessingTime(newAttachment(29 * MEGABYTE)));
    }

    @Test
    void maxProcessingTime_mediumFile_scalesWithSize() {
        assertEquals(ofSeconds(200), GoogleAIService.maxProcessingTime(newAttachment(100 * MEGABYTE)));
    }

    @Test
    void maxProcessingTime_largeFile_isCappedAtFifteenMinutes() {
        assertEquals(ofMinutes(15), GoogleAIService.maxProcessingTime(newAttachment(500 * MEGABYTE)));
    }

}
