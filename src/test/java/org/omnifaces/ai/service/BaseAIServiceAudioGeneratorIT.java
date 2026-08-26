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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.mime.MimeType;

/**
 * Base class for IT on audio-generator-related methods of AI service.
 *
 * NOTE: this is a separate class from {@link BaseAIServiceAudioHandlerIT} because audio generation might require a different model than even audio
 * transcription.
 */
abstract class BaseAIServiceAudioGeneratorIT extends AIServiceIT {

    /**
     * The model which reads the generated audio back. A text to speech model cannot transcribe, so this is a second model next to {@link #getModel()}. Defaults
     * to the provider's own default model, which every provider offering speech generation also transcribes with.
     *
     * @return The model which transcribes the generated audio.
     */
    protected String getTranscriptionModel() {
        return getProvider().getDefaultModel();
    }

    @Test
    void generateAudio() throws Exception {
        var response = service.generateAudio("Dayana, feliz dia del amor, te amo");
        assertTrue(response.length > 0, "Audio response should not be empty");
        var mimeType = MimeType.guessMimeType(response);
        var targetDir = Path.of(System.getProperty("user.dir"), "target", "omnihai-audio-generator-test-results");
        targetDir.toFile().mkdirs();
        var tempFilePath = Files.createTempFile(targetDir, getClass().getSimpleName() + "-", "." + mimeType.extension());
        Files.write(tempFilePath, response);
        log("saved in " + tempFilePath.toString());

        assertTrue(mimeType.isAudio(), "Mime type is audio");

        var transcription = createService(getTranscriptionModel()).transcribe(response).toLowerCase();
        log("transcribed as " + transcription);
        assertAll(
            () -> assertTrue(transcription.contains("dayana"), "the generated audio must contain 'Dayana', case insensitive: " + transcription),
            () -> assertTrue(
                transcription.contains("feliz día del amor"),
                "the generated audio must contain 'Feliz día del amor', case insensitive: " + transcription
            ),
            () -> assertTrue(transcription.contains("te amo"), "the generated audio must contain 'Te amo', case insensitive: " + transcription)
        );
    }

}
