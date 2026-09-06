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

import static java.lang.System.arraycopy;
import static java.nio.file.Files.newByteChannel;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Objects.requireNonNull;
import static javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED;
import static org.omnifaces.ai.helper.FileHelper.cleanupFiles;
import static org.omnifaces.ai.helper.FileHelper.newTempFile;
import static org.omnifaces.ai.helper.JsonHelper.checkErrors;
import static org.omnifaces.ai.helper.JsonHelper.findFirstNonBlankByPaths;
import static org.omnifaces.ai.modality.DefaultAITextHandler.DEFAULT_ERROR_MESSAGE_PATHS;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.service.MetaAIService;

/**
 * Default audio handler for Meta AI service.
 * <p>
 * Meta AI transcribes via its own ASR endpoint rather than via an OpenAI compatible one: the request is a multipart of a JSON handshake and a WAV file, and the
 * transcription comes back as {@code transcript}. The endpoint accepts a mono 16-bit PCM WAV at 16 or 24 kHz alone, so the audio is converted to
 * {@value DefaultAIAudioHandler#PCM_WAV_SAMPLE_RATE} Hz beforehand.
 *
 * @author Bauke Scholtz
 * @since 1.7.1
 * @see MetaAIService
 * @see <a href="https://dev.meta.ai/docs/speech-to-text">API Reference</a>
 */
public class MetaAIAudioHandler extends DefaultAIAudioHandler {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new instance of this AI handler.
     */
    public MetaAIAudioHandler() {
        //
    }

    /** The transcription mode which segments the audio into turns without attributing them to a speaker. */
    private static final String TRANSCRIBE_MODE = "ENDPOINTING";
    private static final String TRANSCRIBE_AUDIO_ENCODING = "WAV";
    private static final List<String> TRANSCRIPT_PATHS = List.of("transcript");

    private static final AudioFormat SUPPORTED_AUDIO_FORMAT = new AudioFormat(
        PCM_WAV_SAMPLE_RATE, PCM_WAV_BITS_PER_SAMPLE, PCM_WAV_CHANNELS, true, false
    );

    private static final String ERROR_UNSUPPORTED_AUDIO = "Cannot convert audio%s to a WAV which Meta AI accepts; the Java Sound API reads WAV, AIFF and AU.";

    @Override
    public JsonObject buildTranscribePayload(AIService service) {
        return Json.createObjectBuilder()
            .add("model", service.getModelName())
            .add("mode", TRANSCRIBE_MODE)
            .add("audioEncoding", TRANSCRIBE_AUDIO_ENCODING)
            .build();
    }

    /**
     * Converts the audio to a mono 16-bit PCM WAV at {@value DefaultAIAudioHandler#PCM_WAV_SAMPLE_RATE} Hz, which is one of the two sample rates the ASR
     * endpoint accepts. An audio file at another sample rate is answered with an HTTP 503 stating that the backend is unavailable.
     * <p>
     * The conversion runs in two steps because the Java Sound API resamples PCM alone: a companded encoding such as the µ-law of a telephony recording is first
     * decoded to PCM at its own sample rate and only then resampled.
     */
    @Override
    public byte[] buildTranscribeContent(byte[] audio) {
        requireNonNull(audio, "audio");

        try (
            var source = AudioSystem.getAudioInputStream(new BufferedInputStream(new ByteArrayInputStream(audio)));
            var decoded = AudioSystem.getAudioInputStream(PCM_SIGNED, source);
            var converted = AudioSystem.getAudioInputStream(SUPPORTED_AUDIO_FORMAT, decoded)
        ) {
            return toWav(converted.readAllBytes());
        }
        catch (Exception e) {
            throw new AIException(ERROR_UNSUPPORTED_AUDIO.formatted(""), e);
        }
    }

    /**
     * Converts the audio at the given source into a temporary WAV of the same shape, reading the source and writing the result a buffer at a time so that
     * neither is held in memory as a whole. The header states the length of the converted audio, which is only known once it is written, so it is written a
     * second time over the placeholder the file starts with.
     */
    @Override
    public Path buildTranscribeContent(Path audio) {
        requireNonNull(audio, "audio");
        Path converted = null;

        try {
            converted = newTempFile("meta-asr", "wav");

            try (
                var source = AudioSystem.getAudioInputStream(audio.toFile());
                var decoded = AudioSystem.getAudioInputStream(PCM_SIGNED, source);
                var pcm = AudioSystem.getAudioInputStream(SUPPORTED_AUDIO_FORMAT, decoded);
                var channel = newByteChannel(converted, WRITE)
            ) {
                channel.write(ByteBuffer.wrap(createWavHeader(0)));
                var pcmContentLength = pcm.transferTo(Channels.newOutputStream(channel));
                channel.position(0);
                channel.write(ByteBuffer.wrap(createWavHeader(pcmContentLength)));
            }

            return converted;
        }
        catch (Exception e) {
            cleanupFiles(converted);
            throw new AIException(ERROR_UNSUPPORTED_AUDIO.formatted(" " + audio), e);
        }
    }

    private static byte[] toWav(byte[] pcmContent) {
        var header = createWavHeader(pcmContent.length);
        var wav = new byte[header.length + pcmContent.length];
        arraycopy(header, 0, wav, 0, header.length);
        arraycopy(pcmContent, 0, wav, header.length, pcmContent.length);
        return wav;
    }

    @Override
    public String parseTranscribeResponse(JsonObject responseJson) throws AIResponseException {
        checkErrors(responseJson, DEFAULT_ERROR_MESSAGE_PATHS);
        return findFirstNonBlankByPaths(responseJson, TRANSCRIPT_PATHS)
            .orElseThrow(() -> new AIResponseException("No transcription text found at paths " + TRANSCRIPT_PATHS, responseJson));
    }

}
