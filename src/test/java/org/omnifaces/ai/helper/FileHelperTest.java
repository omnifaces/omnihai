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
package org.omnifaces.ai.helper;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

class FileHelperTest {

    private static final Logger HELPER_LOGGER = Logger.getLogger(FileHelper.class.getPackageName());

    private Level savedLevel;

    private Path directory;

    @BeforeEach
    void createDirectory(TestInfo testInfo) throws IOException {
        directory = Files.createTempDirectory("omnihai-file-helper-");

        if (testInfo.getTestMethod().filter(method -> method.isAnnotationPresent(WithFinestLogging.class)).isPresent()) {
            savedLevel = HELPER_LOGGER.getLevel();
            HELPER_LOGGER.setLevel(Level.ALL);
        }
    }

    @AfterEach
    void deleteDirectory() throws IOException {
        if (HELPER_LOGGER.getLevel() == Level.ALL) {
            HELPER_LOGGER.setLevel(savedLevel);
        }

        directory.toFile().setWritable(true);

        try (var files = Files.list(directory)) {
            for (var file : files.toList()) {
                Files.deleteIfExists(file);
            }
        }

        Files.deleteIfExists(directory);
    }

    @Test
    void requireWritableFile_aNewFileInAWritableDirectory_isAccepted() {
        var path = directory.resolve("video.mp4");

        assertSame(path, FileHelper.requireWritableFile(path));
    }

    @Test
    void requireWritableFile_anExistingReadOnlyFile_isAccepted() throws IOException {
        var path = Files.writeString(directory.resolve("video.mp4"), "previous");
        path.toFile().setWritable(false);

        assertSame(path, FileHelper.requireWritableFile(path), "the write renames over the target, so the target's own mode does not govern");
    }

    @Test
    void requireWritableFile_null_isRejected() {
        assertThrows(NullPointerException.class, () -> FileHelper.requireWritableFile(null));
    }

    @Test
    void requireWritableFile_aFileSystemRoot_isRejected() {
        var root = Path.of("/");

        assertThrows(IllegalArgumentException.class, () -> FileHelper.requireWritableFile(root));
    }

    @Test
    void requireWritableFile_anExistingDirectory_isRejected() {
        var exception = assertThrows(IllegalArgumentException.class, () -> FileHelper.requireWritableFile(directory));

        assertEquals("Path must denote a file, but was " + directory, exception.getMessage());
    }

    @Test
    void requireWritableFile_inAMissingDirectory_isRejected() {
        var path = directory.resolve("missing").resolve("video.mp4");

        var exception = assertThrows(IllegalArgumentException.class, () -> FileHelper.requireWritableFile(path));

        assertEquals("Directory of path does not exist: " + path, exception.getMessage());
    }

    @Test
    void requireWritableFile_inAReadOnlyDirectory_isRejected() {
        directory.toFile().setWritable(false);
        assumeFalse(Files.isWritable(directory), "the permission bits must bite for this to prove anything, which they do not for a superuser");
        var path = directory.resolve("video.mp4");

        var exception = assertThrows(IllegalArgumentException.class, () -> FileHelper.requireWritableFile(path));

        assertEquals("Directory of path is not writable: " + path, exception.getMessage());
    }

    // =================================================================================================================
    // newOffsetInputStream
    // =================================================================================================================

    /**
     * A value inside a large JSON response is streamed out of the file by its byte range, so the stream ends at the range rather than at the file.
     */
    @Test
    void newOffsetInputStream_readsTheRangeAndStopsThere() throws IOException {
        var file = Files.write(directory.resolve("content.bin"), "0123456789".getBytes(UTF_8));

        try (var stream = FileHelper.newOffsetInputStream(file, 2, 5)) {
            assertEquals("234", new String(stream.readAllBytes(), UTF_8));
            assertEquals(-1, stream.read());
        }
    }

    @Test
    void newOffsetInputStream_availableCountsTheRemainderOfTheRange() throws IOException {
        var file = Files.write(directory.resolve("content.bin"), "0123456789".getBytes(UTF_8));

        try (var stream = FileHelper.newOffsetInputStream(file, 2, 5)) {
            assertEquals(3, stream.available());
            assertEquals('2', stream.read());
            assertEquals(2, stream.available());
        }
    }

    @Test
    void newOffsetInputStream_skipStopsAtTheEndOfTheRange() throws IOException {
        var file = Files.write(directory.resolve("content.bin"), "0123456789".getBytes(UTF_8));

        try (var stream = FileHelper.newOffsetInputStream(file, 2, 5)) {
            assertEquals(0, stream.skip(0), "skipping nothing skips nothing");
            assertEquals(3, stream.skip(100), "a skip past the range stops at the range");
            assertEquals(0, stream.skip(1), "an exhausted stream skips nothing");
            assertEquals(-1, stream.read());
        }
    }

    @Test
    void newOffsetInputStream_negativeStartOffset_isRejected() throws IOException {
        var file = Files.write(directory.resolve("content.bin"), "0123456789".getBytes(UTF_8));

        assertThrows(IllegalArgumentException.class, () -> FileHelper.newOffsetInputStream(file, -1, 5));
    }

    @Test
    void newOffsetInputStream_endOffsetNotBeyondTheStart_isRejected() throws IOException {
        var file = Files.write(directory.resolve("content.bin"), "0123456789".getBytes(UTF_8));

        assertThrows(IllegalArgumentException.class, () -> FileHelper.newOffsetInputStream(file, 5, 5));
    }

    @Test
    void newOffsetInputStream_endOffsetBeyondTheFile_isRejected() throws IOException {
        var file = Files.write(directory.resolve("content.bin"), "0123456789".getBytes(UTF_8));

        assertThrows(IllegalArgumentException.class, () -> FileHelper.newOffsetInputStream(file, 0, 11));
    }

    @Test
    void newOffsetInputStream_null_isRejected() {
        assertThrows(NullPointerException.class, () -> FileHelper.newOffsetInputStream(null, 0, 1));
    }

    // =================================================================================================================
    // cleanupFiles / closeQuietly
    // =================================================================================================================

    @Test
    void cleanupFiles_deletesWhatItCanAndIgnoresTheRest() throws IOException {
        var file = Files.write(directory.resolve("content.bin"), new byte[] { 1 });

        FileHelper.cleanupFiles(file, directory.resolve("never-existed.bin"), null);

        assertFalse(Files.exists(file));
    }

    /**
     * A path which cannot be deleted now is registered for deletion at exit instead, as cleanup may not throw and may not leave the file behind either.
     */
    @Test
    @WithFinestLogging
    void cleanupFiles_pathWhichCannotBeDeleted_isDeferredWithoutThrowing() throws IOException {
        var nested = Files.createDirectory(directory.resolve("nested"));
        Files.write(nested.resolve("occupant.bin"), new byte[] { 1 });

        assertDoesNotThrow(() -> FileHelper.cleanupFiles(nested));
        assertTrue(Files.exists(nested), "a non-empty directory cannot be deleted now");

        Files.delete(nested.resolve("occupant.bin"));
        Files.delete(nested);
    }

    /**
     * Cleanup runs where a failure must not mask the exception which caused the cleanup, so nothing it does throws.
     */
    @Test
    @WithFinestLogging
    void closeQuietly_swallowsAFailingCloseAndANullResource() {
        assertDoesNotThrow(() -> FileHelper.closeQuietly(null));
        assertDoesNotThrow(() -> FileHelper.closeQuietly(() -> {
            throw new IOException("cannot close");
        }));
    }

    @Test
    void tempFilesSupported_isAnsweredWithoutThrowing() {
        assertDoesNotThrow(FileHelper::tempFilesSupported);
    }

    /**
     * The cleanup logging states the path it could not deal with, and is only assembled when someone is listening for it.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface WithFinestLogging {
    }

}
