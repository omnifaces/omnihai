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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.mime.MimeType;

class ImageHelperTest {

    // =================================================================================================================
    // Test isSupportedAsImageAttachment
    // =================================================================================================================

    @Test
    void isSupportedAsImageAttachment_jpeg_shouldBeTrue() {
        var content = new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, 0, 0, 0, 0};
        var mimeType = MimeType.guessMimeType(content);
        assertTrue(ImageHelper.isSupportedAsImageAttachment(mimeType));
    }

    @Test
    void isSupportedAsImageAttachment_png_shouldBeTrue() {
        var content = new byte[]{(byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        var mimeType = MimeType.guessMimeType(content);
        assertTrue(ImageHelper.isSupportedAsImageAttachment(mimeType));
    }

    @Test
    void isSupportedAsImageAttachment_gif_shouldBeTrue() {
        var content = new byte[]{'G', 'I', 'F', '8', '9', 'a', 0, 0};
        var mimeType = MimeType.guessMimeType(content);
        assertTrue(ImageHelper.isSupportedAsImageAttachment(mimeType));
    }

    @Test
    void isSupportedAsImageAttachment_bmp_shouldBeTrue() {
        var content = new byte[]{'B', 'M', 0, 0, 0, 0, 0, 0};
        var mimeType = MimeType.guessMimeType(content);
        assertTrue(ImageHelper.isSupportedAsImageAttachment(mimeType));
    }

    @Test
    void isSupportedAsImageAttachment_webp_shouldBeTrue() {
        var content = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        var mimeType = MimeType.guessMimeType(content);
        assertTrue(ImageHelper.isSupportedAsImageAttachment(mimeType));
    }

    @Test
    void isSupportedAsImageAttachment_svg_shouldBeTrue() {
        var content = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(UTF_8);
        var mimeType = MimeType.guessMimeType(content);
        assertTrue(ImageHelper.isSupportedAsImageAttachment(mimeType));
    }

    @Test
    void isSupportedAsImageAttachment_ico_shouldBeFalse() {
        var content = new byte[]{0x00, 0x00, 0x01, 0x00, 0, 0, 0, 0};
        var mimeType = MimeType.guessMimeType(content);
        assertFalse(ImageHelper.isSupportedAsImageAttachment(mimeType));
    }

    @Test
    void isSupportedAsImageAttachment_heic_shouldBeFalse() {
        var content = new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0};
        var mimeType = MimeType.guessMimeType(content);
        assertFalse(ImageHelper.isSupportedAsImageAttachment(mimeType));
    }

    @Test
    void isSupportedAsImageAttachment_tiff_shouldBeFalse() {
        var content = new byte[]{'I', 'I', '*', 0, 0, 0, 0, 0};
        var mimeType = MimeType.guessMimeType(content);
        assertFalse(ImageHelper.isSupportedAsImageAttachment(mimeType));
    }

    @Test
    void isSupportedAsImageAttachment_nonImageMimeType_shouldBeFalse() {
        var content = new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '4'};
        var mimeType = MimeType.guessMimeType(content);
        assertFalse(ImageHelper.isSupportedAsImageAttachment(mimeType));
    }

    // =================================================================================================================
    // Test sanitizeImageAttachment
    // =================================================================================================================

    @Test
    void sanitizeImageAttachment_unsupportedFormat_shouldThrow() {
        var content = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
        assertThrows(AIException.class, () -> ImageHelper.sanitizeImageAttachment(content));
    }

    @Test
    void sanitizeImageAttachment_ico_shouldThrow() {
        var content = new byte[]{0x00, 0x00, 0x01, 0x00, 0, 0, 0, 0};
        assertThrows(AIException.class, () -> ImageHelper.sanitizeImageAttachment(content));
    }

    @Test
    void sanitizeImageAttachment_heic_shouldThrow() {
        var content = new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0};
        assertThrows(AIException.class, () -> ImageHelper.sanitizeImageAttachment(content));
    }
}
