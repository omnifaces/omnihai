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
package org.omnifaces.ai;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.helper.FileHelper;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.modality.GoogleAIAudioHandler;
import org.omnifaces.ai.modality.OpenRouterAIAudioHandler;

/**
 * What the library does on a runtime which permits no temporary file, such as a container with a read-only file system: it collects in memory instead, which
 * costs memory rather than the answer. This runs with the temporary directory pointed at a path which does not exist.
 */
class NoTempFilesTest {

    /** A tenth of a second of silence, which is what a current model emits at the rate these handlers assume. */
    private static final int PCM_CONTENT_LENGTH = 3200;

    @Test
    void tempFilesSupported_temporaryDirectoryWhichIsNotThere_saysSoRatherThanThrowing() {
        assertFalse(FileHelper.tempFilesSupported(), "this test class runs to prove what happens when it is false");
    }

    @Test
    void newTempFile_temporaryDirectoryWhichIsNotThere_saysWhyItCannot() {
        assertThrowsIOException(() -> FileHelper.newTempFile("probe", "tmp"));
    }

    @Test
    void googleAudio_isCollectedInMemory() throws IOException {
        var audioContent = new GoogleAIAudioHandler().parseAudioContent(newGoogleAudioResponse()).readAllBytes();

        assertEquals("wav", MimeType.guessMimeType(audioContent).extension());
        assertEquals(44 + PCM_CONTENT_LENGTH, audioContent.length, "44 byte header plus the decoded PCM");
    }

    @Test
    void openRouterAudio_isCollectedInMemory() throws IOException {
        var audioContent = new OpenRouterAIAudioHandler().parseAudioContent(newOpenRouterAudioResponse()).readAllBytes();

        assertEquals("wav", MimeType.guessMimeType(audioContent).extension());
        assertTrue(audioContent.length > 44, "the header must be followed by the audio it announces");
    }

    private static InputStream newGoogleAudioResponse() {
        var pcmContent = Base64.getEncoder().encodeToString(new byte[PCM_CONTENT_LENGTH]);
        var responseJson = "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/L16\",\"data\":\"" + pcmContent + "\"}}]}}]}";
        return new ByteArrayInputStream(responseJson.getBytes(UTF_8));
    }

    private static InputStream newOpenRouterAudioResponse() {
        return NoTempFilesTest.class.getResourceAsStream("/openrouter-audio-stream.sse");
    }

    private static void assertThrowsIOException(ThrowingCall call) {
        try {
            call.run();
        }
        catch (IOException expected) {
            return;
        }

        throw new AssertionError("Expected an IOException, as the temporary directory is not there");
    }

    private interface ThrowingCall {

        void run() throws IOException;

    }

}
