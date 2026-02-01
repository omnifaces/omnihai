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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ImageMimeTypeDetectorTest {

    // =================================================================================================================
    // Test guessImageMimeType - basic format detection
    // =================================================================================================================

    @Test
    void guessImageMimeType_jpeg() {
        var content = new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, 0, 0, 0, 0};
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("jpg", result.get().extension());
    }

    @Test
    void guessImageMimeType_png() {
        var content = new byte[]{(byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("png", result.get().extension());
    }

    @Test
    void guessImageMimeType_gif() {
        var content = new byte[]{'G', 'I', 'F', '8', '9', 'a', 0, 0};
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("gif", result.get().extension());
    }

    @Test
    void guessImageMimeType_bmp() {
        var content = new byte[]{'B', 'M', 0, 0, 0, 0, 0, 0};
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("bmp", result.get().extension());
    }

    @Test
    void guessImageMimeType_webp() {
        var content = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("webp", result.get().extension());
    }

    @Test
    void guessImageMimeType_ico() {
        var content = new byte[]{0x00, 0x00, 0x01, 0x00, 0, 0, 0, 0};
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("ico", result.get().extension());
    }

    @Test
    void guessImageMimeType_heic() {
        var content = new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0};
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("heic", result.get().extension());
    }

    @Test
    void guessImageMimeType_heif_mif1() {
        var content = new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p', 'm', 'i', 'f', '1', 0, 0, 0, 0};
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("heif", result.get().extension());
    }

    @Test
    void guessImageMimeType_jxl() {
        var content = new byte[]{(byte)0xFF, 0x0A, 0, 0, 0, 0, 0, 0};
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("jxl", result.get().extension());
    }

    @Test
    void guessImageMimeType_jxl_codestream() {
        var content = new byte[]{'J', 'X', 'L', ' ', 0, 0, 0, 0};
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("jxl", result.get().extension());
    }

    @Test
    void guessImageMimeType_tiff_littleEndian() {
        var content = new byte[]{'I', 'I', '*', 0, 0, 0, 0, 0};
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("tiff", result.get().extension());
    }

    @Test
    void guessImageMimeType_tiff_bigEndian() {
        var content = new byte[]{'M', 'M', 0, '*', 0, 0, 0, 0};
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("tiff", result.get().extension());
    }

    // =================================================================================================================
    // Test guessImageMimeType - SVG detection (special case)
    // =================================================================================================================

    @Test
    void guessImageMimeType_svg_direct() {
        var content = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(UTF_8);
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("svg", result.get().extension());
    }

    @Test
    void guessImageMimeType_svg_withXmlDeclaration() {
        var content = "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(UTF_8);
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("svg", result.get().extension());
    }

    @Test
    void guessImageMimeType_svg_withNamespace() {
        var content = "<?xml version=\"1.0\"?><root xmlns=\"http://www.w3.org/2000/svg\"></root>".getBytes(UTF_8);
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("svg", result.get().extension());
    }

    // =================================================================================================================
    // Test guessImageMimeType - edge cases
    // =================================================================================================================

    @Test
    void guessImageMimeType_null_shouldReturnEmpty() {
        var result = ImageMimeTypeDetector.guessImageMimeType(null);
        assertFalse(result.isPresent());
    }

    @Test
    void guessImageMimeType_empty_shouldReturnEmpty() {
        var result = ImageMimeTypeDetector.guessImageMimeType(new byte[0]);
        assertFalse(result.isPresent());
    }

    @Test
    void guessImageMimeType_tooShort_shouldReturnEmpty() {
        var result = ImageMimeTypeDetector.guessImageMimeType(new byte[]{0x00, 0x01, 0x02});
        assertFalse(result.isPresent());
    }

    @Test
    void guessImageMimeType_unknownFormat_shouldReturnEmpty() {
        var result = ImageMimeTypeDetector.guessImageMimeType(new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07});
        assertFalse(result.isPresent());
    }

    @Test
    void guessImageMimeType_riffWithoutWebp_shouldReturnEmpty() {
        var content = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'X', 'X', 'X', 'X'};
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertFalse(result.isPresent());
    }

    @Test
    void guessImageMimeType_ftypWithoutHeic_shouldReturnEmpty() {
        var content = new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p', 'X', 'X', 'X', 'X', 0, 0, 0, 0};
        var result = ImageMimeTypeDetector.guessImageMimeType(content);
        assertFalse(result.isPresent());
    }
}
