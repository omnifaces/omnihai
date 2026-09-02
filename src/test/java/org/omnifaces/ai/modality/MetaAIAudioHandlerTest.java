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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIProvider.META;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.mime.MimeType;

/**
 * The Meta ASR endpoint takes a mono 16-bit PCM WAV at 16 or 24 kHz alone, and answers an audio file at any other sample rate with an HTTP 503 rather than a
 * rejection, so the conversion which this handler performs is what keeps that outage from reaching the caller.
 */
class MetaAIAudioHandlerTest {

    private static final String MODEL_NAME = "muse-voice-transcribe-1.0";
    private static final int WAV_HEADER_LENGTH = 44;
    private static final int SUPPORTED_SAMPLE_RATE = 24000;
    private static final int SUPPORTED_CHANNELS = 1;
    private static final int SUPPORTED_BITS_PER_SAMPLE = 16;
    private static final int SOURCE_SAMPLE_RATE = 8000;
    private static final double SOURCE_DURATION_MS = 1000;
    private static final double DURATION_TOLERANCE_MS = 5;

    private final MetaAIAudioHandler handler = new MetaAIAudioHandler();

    // =================================================================================================================
    // Request payload
    // =================================================================================================================

    @Test
    void buildTranscribePayload_statesTheModelAndTheWavHandshake() {
        var payload = handler.buildTranscribePayload(newService());

        assertEquals(MODEL_NAME, payload.getString("model"));
        assertEquals("WAV", payload.getString("audioEncoding"), "the endpoint requires the encoding of the audio part");
        assertEquals("ENDPOINTING", payload.getString("mode"), "a mode is optional but the transcript degrades without one");
    }

    // =================================================================================================================
    // Audio conversion
    // =================================================================================================================

    @Test
    void buildTranscribeContent_convertsToASupportedWav() throws IOException, UnsupportedAudioFileException {
        var content = handler.buildTranscribeContent(readAllBytes("/helloworld.wav"));

        assertEquals("wav", MimeType.guessMimeType(content).extension(), "the audio part must remain a WAV file");

        var format = AudioSystem.getAudioInputStream(new ByteArrayInputStream(content)).getFormat();
        assertEquals(SUPPORTED_SAMPLE_RATE, format.getSampleRate(), "the endpoint accepts 16 or 24 kHz, and this is the rate the handler converts to");
        assertEquals(SUPPORTED_CHANNELS, format.getChannels());
        assertEquals(SUPPORTED_BITS_PER_SAMPLE, format.getSampleSizeInBits());
    }

    @Test
    void buildTranscribeContent_companded_isDecodedBeforeItIsResampled() throws IOException, UnsupportedAudioFileException {
        var content = handler.buildTranscribeContent(newWav(new AudioFormat(Encoding.ULAW, SOURCE_SAMPLE_RATE, 8, 1, 1, SOURCE_SAMPLE_RATE, false)));

        var converted = AudioSystem.getAudioInputStream(new ByteArrayInputStream(content));
        assertEquals(Encoding.PCM_SIGNED, converted.getFormat().getEncoding());
        assertEquals(SOURCE_DURATION_MS, durationMs(converted), DURATION_TOLERANCE_MS, "a µ-law source decoded wrong shifts the duration");
    }

    @Test
    void buildTranscribeContent_stereo_isDownmixedToOneChannel() throws IOException, UnsupportedAudioFileException {
        var content = handler.buildTranscribeContent(newWav(new AudioFormat(SOURCE_SAMPLE_RATE, SUPPORTED_BITS_PER_SAMPLE, 2, true, false)));

        var converted = AudioSystem.getAudioInputStream(new ByteArrayInputStream(content));
        assertEquals(SUPPORTED_CHANNELS, converted.getFormat().getChannels());
        assertEquals(SOURCE_DURATION_MS, durationMs(converted), DURATION_TOLERANCE_MS, "a stereo source downmixed wrong doubles the frame count");
    }

    @Test
    void buildTranscribeContent_headerDescribesTheConvertedPcm() throws IOException, UnsupportedAudioFileException {
        var audio = readAllBytes("/helloworld.wav");

        var source = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audio));
        var converted = AudioSystem.getAudioInputStream(new ByteArrayInputStream(handler.buildTranscribeContent(audio)));

        assertEquals(
            durationMs(source), durationMs(converted), DURATION_TOLERANCE_MS, "the sample rate in the header must be the one the PCM was resampled to"
        );
    }

    @Test
    void buildTranscribeContent_statesTheConvertedLengthInTheHeader() {
        var content = handler.buildTranscribeContent(readAllBytes("/helloworld.wav"));
        var header = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(content.length - WAV_HEADER_LENGTH, header.getInt(40), "the data size must count the converted PCM alone");
        assertEquals(content.length - 8, header.getInt(4), "the RIFF size must count everything after itself");
    }

    @Test
    void buildTranscribeContent_unreadableAudio_throwsException() {
        var mp3 = "ID3".getBytes(UTF_8);

        var exception = assertThrows(AIException.class, () -> handler.buildTranscribeContent(mp3));
        assertTrue(exception.getMessage().contains("WAV"), "the failure must name the formats which are accepted");
    }

    // =================================================================================================================
    // Response parsing
    // =================================================================================================================

    @Test
    void parseTranscribeResponse_readsTheTranscript() {
        var responseJson = parseJson("{\"sessionId\":\"1\",\"transcript\":\"Hello world.\",\"turns\":[{\"transcript\":\"Hello world.\"}]}");

        assertEquals("Hello world.", handler.parseTranscribeResponse(responseJson), "the whole transcript is the one outside the turns");
    }

    @Test
    void parseTranscribeResponse_errorObject_reportsTheProviderMessage() {
        var responseJson = parseJson("{\"error\":{\"message\":\"The backend is temporarily unavailable.\"}}");

        var exception = assertThrows(AIResponseException.class, () -> handler.parseTranscribeResponse(responseJson));
        assertTrue(exception.getMessage().startsWith("The backend is temporarily unavailable."), "a failure must surface the provider's own message");
    }

    @Test
    void parseTranscribeResponse_withoutTranscript_throwsException() {
        var responseJson = parseJson("{\"sessionId\":\"1\",\"audioDurationMs\":1280,\"turns\":[]}");

        assertThrows(AIResponseException.class, () -> handler.parseTranscribeResponse(responseJson));
    }

    /** Returns a 1000 ms WAV in the given format, at a sample rate which the ASR endpoint does not accept. */
    private static byte[] newWav(AudioFormat format) {
        var frames = (int) (SOURCE_DURATION_MS * format.getSampleRate() / 1000);
        var wav = new ByteArrayOutputStream();

        try {
            var pcm = new ByteArrayInputStream(new byte[frames * format.getFrameSize()]);
            AudioSystem.write(new AudioInputStream(pcm, format, frames), Type.WAVE, wav);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return wav.toByteArray();
    }

    private static double durationMs(AudioInputStream audio) {
        return audio.getFrameLength() * 1000d / audio.getFormat().getSampleRate();
    }

    private static byte[] readAllBytes(String resource) {
        try (var stream = MetaAIAudioHandlerTest.class.getResourceAsStream(resource)) {
            return stream.readAllBytes();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static AIService newService() {
        return AIConfig.of(META, "test-api-key").withModel(MODEL_NAME).createService();
    }

}
