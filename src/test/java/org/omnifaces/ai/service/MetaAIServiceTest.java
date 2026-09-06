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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIModality.AUDIO_ANALYSIS;
import static org.omnifaces.ai.AIModality.AUDIO_GENERATION;
import static org.omnifaces.ai.AIModality.IMAGE_ANALYSIS;
import static org.omnifaces.ai.AIModality.IMAGE_GENERATION;
import static org.omnifaces.ai.AIModality.VIDEO_GENERATION;
import static org.omnifaces.ai.AIProvider.META;

import java.net.URI;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.helper.JsonHelper;

/**
 * Meta AI serves transcription on a dedicated model and everything else on the chat models, so the modalities it publishes follow the configured model name.
 * The transcribe request it addresses the ASR endpoint with is built here as well, as that endpoint rejects a multipart whose two parts are named otherwise.
 */
class MetaAIServiceTest {

    private static final String API_KEY = "test-api-key";
    private static final String TRANSCRIBE_MODEL = "muse-voice-transcribe-1.0";
    private static final String CHAT_MODEL = "muse-spark-1.2";

    /** The magic bytes of a WAV, which is what {@link MetaAIService#newTranscribeAttachment(byte[])} receives once the audio handler converted the audio. */
    private static final byte[] WAV_CONTENT = "RIFF....WAVEfmt ".getBytes(US_ASCII);

    @Test
    void supportsModality_transcribeModel_servesAudioAnalysisAlone() {
        var service = newService(TRANSCRIBE_MODEL);

        assertTrue(service.supportsModality(AUDIO_ANALYSIS));
        assertFalse(service.supportsModality(IMAGE_ANALYSIS), "Muse Voice Transcribe takes audio input alone");
    }

    @Test
    void supportsModality_chatModel_servesImageAnalysis() {
        var service = newService(CHAT_MODEL);

        assertFalse(service.supportsModality(AUDIO_ANALYSIS), "Muse Spark takes no audio input");
        assertTrue(service.supportsModality(IMAGE_ANALYSIS));
    }

    // =================================================================================================================
    // Transcribe request
    // =================================================================================================================

    @Test
    void getTranscribePath_isTheAsrEndpointOfTheConfiguredEndpoint() {
        var service = newService(TRANSCRIBE_MODEL);

        assertEquals("asr/transcribe", service.getTranscribePath());
        assertEquals(URI.create("https://api.meta.ai/v1/asr/transcribe"), service.resolveURI(service.getTranscribePath()));
    }

    @Test
    void newTranscribeAttachment_carriesTheHandshakeAsTheRequestPart() {
        var attachment = newService(TRANSCRIBE_MODEL).newTranscribeAttachment(WAV_CONTENT);

        var handshake = JsonHelper.parseJson(attachment.metadata().get("request"));
        assertEquals(TRANSCRIBE_MODEL, handshake.getString("model"), "the handshake states the model, as the path carries none");
        assertEquals("WAV", handshake.getString("audioEncoding"));
        assertEquals("audio/wav", attachment.mimeType().value(), "the file part must announce the type of the converted content");
    }

    @Test
    void transcribeFilePart_isNamedAsTheAsrEndpointExpects() {
        assertEquals("audio", MetaAIService.TRANSCRIBE_FILE_PART_NAME, "the endpoint rejects the request when the audio arrives under another part name");
    }

    @Test
    void transcribeAsync_unreadablePath_namesTheFileItCouldNotRead() {
        var service = newService(TRANSCRIBE_MODEL);
        var audio = Path.of("does-not-exist.wav");

        var exception = assertThrows(AIException.class, () -> service.transcribeAsync(audio));
        assertTrue(exception.getMessage().contains("does-not-exist.wav"), "an unreadable path may not reach the endpoint as an empty request");
    }

    private static MetaAIService newService(String model) {
        return (MetaAIService) AIConfig.of(META, API_KEY).withModel(model).createService();
    }

    // =================================================================================================================
    // Capabilities
    // =================================================================================================================

    @Test
    void supportsModality_servesNoGeneration() {
        var service = newService(CHAT_MODEL);

        assertFalse(service.supportsModality(IMAGE_GENERATION));
        assertFalse(service.supportsModality(AUDIO_GENERATION));
        assertFalse(service.supportsModality(VIDEO_GENERATION));
    }

    @Test
    void capabilities_whichAreApiBoundRatherThanVersionBound_areServedWhateverTheModel() {
        var service = newService(CHAT_MODEL);

        assertTrue(service.supportsFileAttachments());
        assertTrue(service.supportsStructuredOutput());
        assertTrue(service.supportsReasoningEffort());
        assertTrue(service.supportsOpenAIResponsesApi());
    }

    /**
     * Meta AI serves neither of these OpenAI endpoints. Transcription is served by the ASR endpoint which {@link MetaAIService#transcribeAsync(byte[])}
     * addresses whatever this answers.
     */
    @Test
    void moderationAndTranscription_areNotServedByAnOpenAICompatibleEndpoint() {
        var service = newService(TRANSCRIBE_MODEL);

        assertFalse(service.supportsOpenAIModerationCapability(Set.of("hate")));
        assertFalse(service.supportsOpenAITranscriptionCapability());
    }

}
