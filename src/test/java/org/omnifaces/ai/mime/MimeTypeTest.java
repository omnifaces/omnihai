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
package org.omnifaces.ai.mime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A MIME type is recognized from the leading bytes of the content, so a file is read only as far as those reach. A type the library does not enumerate is still
 * answered rather than rejected, with an extension derived from its subtype, as a provider may serve one this version does not know yet.
 */
class MimeTypeTest {

    @TempDir
    private Path tempDir;

    @Test
    void guessMimeType_path_readsTheLeadingBytesOfTheFile() throws IOException {
        var file = Files.write(tempDir.resolve("image.png"), new byte[] { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10 });

        assertEquals("image/png", MimeType.guessMimeType(file).value());
    }

    @Test
    void guessMimeType_unreadablePath_namesTheFileItCouldNotRead() {
        var file = tempDir.resolve("does-not-exist.png");

        var exception = assertThrows(UncheckedIOException.class, () -> MimeType.guessMimeType(file));
        assertTrue(exception.getMessage().contains("does-not-exist.png"));
    }

    @Test
    void isImage_followsTheTypeHalfOfTheMimeType() {
        assertTrue(MimeType.of("image/png").isImage());
        assertFalse(MimeType.of("application/pdf").isImage());
    }

    /**
     * A type this version does not enumerate is answered with the subtype as its extension, so that an attachment of it still gets a plausible file name.
     */
    @Test
    void of_unknownMimeType_derivesItsExtensionFromTheSubtype() {
        assertEquals("x-custom", MimeType.of("application/x-custom").extension());
    }

    @Test
    void of_unknownMimeTypeWithParameters_derivesTheExtensionWithoutThem() {
        assertEquals("x-custom", MimeType.of("application/x-custom; charset=utf-8").extension());
    }

    @Test
    void of_mimeTypeWithoutASubtype_derivesTheExtensionFromTheWholeValue() {
        assertEquals("custom", MimeType.of("custom").extension());
    }

    @Test
    void guessMimeType_textWithAByteOrderMark_looksPastIt() {
        var json = "﻿{\"key\":\"value\"}".getBytes(UTF_8);

        assertEquals("application/json", MimeType.guessMimeType(json).value());
    }

}
