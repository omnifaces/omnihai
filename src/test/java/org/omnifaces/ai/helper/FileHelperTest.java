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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileHelperTest {

    private Path directory;

    @BeforeEach
    void createDirectory() throws IOException {
        directory = Files.createTempDirectory("omnihai-file-helper-");
    }

    @AfterEach
    void deleteDirectory() throws IOException {
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

}
