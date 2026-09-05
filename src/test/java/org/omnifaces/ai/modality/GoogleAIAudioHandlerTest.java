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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.mime.MimeType;

/**
 * Gemini text-to-speech answers with the audio content as Base64-encoded PCM in a JSON body, which the handler must turn into a playable WAV.
 */
class GoogleAIAudioHandlerTest {

    private static final int PCM_CONTENT_LENGTH = 320;

    private final GoogleAIAudioHandler handler = new GoogleAIAudioHandler();

    /** A response carrying {@value #PCM_CONTENT_LENGTH} bytes of PCM, which is a tenth of a second of silence at the rate every current model emits. */
    private static InputStream newAudioResponse() {
        var pcmContent = Base64.getEncoder().encodeToString(new byte[PCM_CONTENT_LENGTH]);
        var responseJson = "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/L16\",\"data\":\"" + pcmContent + "\"}}]}}]}";
        return new ByteArrayInputStream(responseJson.getBytes(UTF_8));
    }

    @Test
    void parseAudioContent_wrapsThePcmInAWav() throws IOException {
        var audioContent = handler.parseAudioContent(newAudioResponse()).readAllBytes();

        assertTrue(MimeType.guessMimeType(audioContent).isAudio(), "parsed content must be recognized as audio");
        assertEquals("wav", MimeType.guessMimeType(audioContent).extension());
        assertEquals(44 + PCM_CONTENT_LENGTH, audioContent.length, "44 byte header plus the decoded PCM");
    }

    @Test
    void parseAudioContent_inMemoryAndViaTempFiles_yieldTheSameAudio() throws IOException {
        var inMemory = handler.parseAudioContentInMemory(newAudioResponse()).readAllBytes();
        var viaTempFiles = handler.parseAudioContentViaTempFiles(newAudioResponse()).readAllBytes();

        assertArrayEquals(inMemory, viaTempFiles, "both collectors must yield the same audio");
    }

    @Test
    void parseAudioContent_withoutAudioContent_throwsException() {
        var responseStream = new ByteArrayInputStream("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"nope\"}]}}]}".getBytes(UTF_8));

        assertThrows(AIResponseException.class, () -> handler.parseAudioContent(responseStream));
    }

    /**
     * A response body which cannot be read is reported as an unusable answer rather than as an empty one.
     */
    @Test
    void parseAudioContent_unreadableStream_saysSo() {
        var responseStream = new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("connection reset");
            }

        };

        assertThrows(AIResponseException.class, () -> handler.parseAudioContentInMemory(responseStream));
    }

    @Test
    void parseAudioContent_contentWhichDoesNotDecode_saysSo() {
        var responseStream = new ByteArrayInputStream(
            "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"data\":\"!!!not base64!!!\"}}]}}]}".getBytes(UTF_8)
        );

        assertThrows(AIResponseException.class, () -> handler.parseAudioContentInMemory(responseStream));
    }

    /**
     * A handler which states no path to the audio has nothing to read, and says which method to implement.
     */
    @Test
    void parseAudioContent_withoutAnyStatedPath_namesTheMethod() {
        var pathless = new GoogleAIAudioHandler() {

            @Override
            protected List<String> getAudioResponseContentPaths() {
                return List.of();
            }

        };
        var responseStream = new ByteArrayInputStream("{}".getBytes(UTF_8));

        assertThrows(IllegalStateException.class, () -> pathless.parseAudioContent(responseStream));
    }

    @Test
    void parseAudioContent_answerWithoutAudio_saysWhereItLooked() {
        var responseStream = new ByteArrayInputStream("{\"candidates\":[]}".getBytes(UTF_8));

        var exception = assertThrows(AIResponseException.class, () -> handler.parseAudioContentInMemory(responseStream));
        assertTrue(exception.getMessage().contains("No audio content found"), exception.getMessage());
    }

    /**
     * The temp file route leaves what it wrote behind when it fails, so the content can still be looked at rather than being lost with the error.
     */
    @Test
    void parseAudioContentViaTempFiles_contentWhichDoesNotDecode_saysTheTempFileIsLeft() {
        var responseStream = new ByteArrayInputStream(
            "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"data\":\"!!!\"}}]}}]}".getBytes(UTF_8)
        );

        var exception = assertThrows(AIResponseException.class, () -> handler.parseAudioContentViaTempFiles(responseStream));
        assertTrue(exception.getMessage().contains("temp file left"), exception.getMessage());
    }

}
