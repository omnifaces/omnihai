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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AudioVideoMimeTypeDetectorTest {

    // =================================================================================================================
    // Test guessAudioVideoMimeType - AAC detection
    // =================================================================================================================

    @Test
    void guessAudioVideoMimeType_aac_mpeg4() {
        var content = new byte[]{(byte)0xFF, (byte)0xF1, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("audio/aac", result.get().value());
        assertEquals("aac", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_aac_mpeg2() {
        var content = new byte[]{(byte)0xFF, (byte)0xF9, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("audio/aac", result.get().value());
        assertEquals("aac", result.get().extension());
    }

    // =================================================================================================================
    // Test guessAudioVideoMimeType - MP3 detection
    // =================================================================================================================

    @Test
    void guessAudioVideoMimeType_mp3_withId3Tag() {
        var content = new byte[]{'I', 'D', '3', 0x04, 0x00, 0x00, 0x00, 0x00};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("mp3", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_mp3_frameSyncword() {
        var content = new byte[]{(byte)0xFF, (byte)0xFB, (byte)0x90, 0x00, 0x00, 0x00, 0x00, 0x00};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("mp3", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_mp3_frameSyncword_edgeCase() {
        var content = new byte[]{(byte)0xFF, (byte)0xE0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("mp3", result.get().extension());
    }

    // =================================================================================================================
    // Test guessAudioVideoMimeType - other audio formats
    // =================================================================================================================

    @Test
    void guessAudioVideoMimeType_aiff() {
        var content = new byte[]{'F', 'O', 'R', 'M', 0x00, 0x00, 0x00, 0x00, 'A', 'I', 'F', 'F'};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("audio/x-aiff", result.get().value());
        assertEquals("aif", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_flac() {
        var content = new byte[]{'f', 'L', 'a', 'C', 0x00, 0x00, 0x00, 0x00};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("flac", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_ogg() {
        var content = new byte[]{'O', 'g', 'g', 'S', 0x00, 0x00, 0x00, 0x00};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("ogg", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_wav() {
        var content = new byte[]{'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'A', 'V', 'E'};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("wav", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_m4a() {
        var content = new byte[]{0x00, 0x00, 0x00, 0x00, 'f', 't', 'y', 'p', 'M', '4', 'A', ' '};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("m4a", result.get().extension());
    }

    // =================================================================================================================
    // Test guessAudioVideoMimeType - video formats
    // =================================================================================================================

    @Test
    void guessAudioVideoMimeType_mkv() {
        var content = new byte[]{0x1A, 'E', (byte)0xDF, (byte)0xA3, 0x00, 0x00, 0x00, 0x00};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("mkv", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_webm() {
        var header = new byte[]{0x1A, 'E', (byte)0xDF, (byte)0xA3};
        var webmMarker = "some data webm more data".getBytes();
        var content = new byte[header.length + webmMarker.length];
        System.arraycopy(header, 0, content, 0, header.length);
        System.arraycopy(webmMarker, 0, content, header.length, webmMarker.length);

        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("webm", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_avi() {
        var content = new byte[]{'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'A', 'V', 'I', ' '};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("avi", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_mov() {
        var content = new byte[]{0x00, 0x00, 0x00, 0x00, 'f', 't', 'y', 'p', 'q', 't', ' ', ' '};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("mov", result.get().extension());
    }

    // =================================================================================================================
    // Test guessAudioVideoMimeType - MP4 brands
    // =================================================================================================================

    @Test
    void guessAudioVideoMimeType_mp4_isom() {
        var content = new byte[]{0x00, 0x00, 0x00, 0x00, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("mp4", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_mp4_iso2() {
        var content = new byte[]{0x00, 0x00, 0x00, 0x00, 'f', 't', 'y', 'p', 'i', 's', 'o', '2'};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("mp4", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_mp4_mp41() {
        var content = new byte[]{0x00, 0x00, 0x00, 0x00, 'f', 't', 'y', 'p', 'm', 'p', '4', '1'};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("mp4", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_mp4_mp42() {
        var content = new byte[]{0x00, 0x00, 0x00, 0x00, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("mp4", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_mp4_avc1() {
        var content = new byte[]{0x00, 0x00, 0x00, 0x00, 'f', 't', 'y', 'p', 'a', 'v', 'c', '1'};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("mp4", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_mp4_3gp4() {
        var content = new byte[]{0x00, 0x00, 0x00, 0x00, 'f', 't', 'y', 'p', '3', 'g', 'p', '4'};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("mp4", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_mp4_f4v() {
        var content = new byte[]{0x00, 0x00, 0x00, 0x00, 'f', 't', 'y', 'p', 'f', '4', 'v', ' '};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("mp4", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_mp4_kddi() {
        var content = new byte[]{0x00, 0x00, 0x00, 0x00, 'f', 't', 'y', 'p', 'k', 'd', 'd', 'i'};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertTrue(result.isPresent());
        assertEquals("mp4", result.get().extension());
    }

    @Test
    void guessAudioVideoMimeType_mp4_unknownBrand_shouldReturnEmpty() {
        var content = new byte[]{0x00, 0x00, 0x00, 0x00, 'f', 't', 'y', 'p', 'X', 'X', 'X', 'X'};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertFalse(result.isPresent());
    }

    // =================================================================================================================
    // Test guessAudioVideoMimeType - edge cases
    // =================================================================================================================

    @Test
    void guessAudioVideoMimeType_null_shouldReturnEmpty() {
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(null);
        assertFalse(result.isPresent());
    }

    @Test
    void guessAudioVideoMimeType_empty_shouldReturnEmpty() {
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(new byte[0]);
        assertFalse(result.isPresent());
    }

    @Test
    void guessAudioVideoMimeType_tooShort_shouldReturnEmpty() {
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06});
        assertFalse(result.isPresent());
    }

    @Test
    void guessAudioVideoMimeType_unknownFormat_shouldReturnEmpty() {
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07});
        assertFalse(result.isPresent());
    }

    @Test
    void guessAudioVideoMimeType_riffWithoutSubmagic_shouldReturnEmpty() {
        var content = new byte[]{'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'X', 'X', 'X', 'X'};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertFalse(result.isPresent());
    }

    @Test
    void guessAudioVideoMimeType_notMp3FrameSync_shouldReturnEmpty() {
        var content = new byte[]{(byte)0xFF, (byte)0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        var result = AudioVideoMimeTypeDetector.guessAudioVideoMimeType(content);
        assertFalse(result.isPresent());
    }

    // =================================================================================================================
    // Test startsWith helper
    // =================================================================================================================

    @Test
    void startsWith_matchAtOffset0_shouldReturnTrue() {
        var content = new byte[]{'A', 'B', 'C', 'D'};
        assertTrue(AudioVideoMimeTypeDetector.startsWith(content, 0, new byte[]{'A', 'B'}));
    }

    @Test
    void startsWith_matchAtOffset2_shouldReturnTrue() {
        var content = new byte[]{'A', 'B', 'C', 'D'};
        assertTrue(AudioVideoMimeTypeDetector.startsWith(content, 2, new byte[]{'C', 'D'}));
    }

    @Test
    void startsWith_noMatch_shouldReturnFalse() {
        var content = new byte[]{'A', 'B', 'C', 'D'};
        assertFalse(AudioVideoMimeTypeDetector.startsWith(content, 0, new byte[]{'X', 'Y'}));
    }

    @Test
    void startsWith_contentTooShort_shouldReturnFalse() {
        var content = new byte[]{'A', 'B'};
        assertFalse(AudioVideoMimeTypeDetector.startsWith(content, 0, new byte[]{'A', 'B', 'C', 'D'}));
    }

    @Test
    void startsWith_offsetTooLarge_shouldReturnFalse() {
        var content = new byte[]{'A', 'B', 'C', 'D'};
        assertFalse(AudioVideoMimeTypeDetector.startsWith(content, 10, new byte[]{'A'}));
    }
}
