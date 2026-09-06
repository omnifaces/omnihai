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
import static org.omnifaces.ai.helper.FileHelper.cleanupFiles;
import static org.omnifaces.ai.helper.FileHelper.closeQuietly;
import static org.omnifaces.ai.helper.FileHelper.newDeleteOnCloseInputStream;
import static org.omnifaces.ai.helper.FileHelper.newTempFile;
import static org.omnifaces.ai.helper.FileHelper.tempFilesSupported;
import static org.omnifaces.ai.helper.JsonHelper.checkErrors;
import static org.omnifaces.ai.helper.JsonHelper.findFirstNonBlankByPaths;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.helper.FileHelper;
import org.omnifaces.ai.model.GenerateAudioOptions;
import org.omnifaces.ai.service.OpenRouterAIService;

/**
 * Default audio handler for OpenRouter AI service.
 * <p>
 * OpenRouter has no dedicated speech endpoint: an audio capable model emits audio as a second modality of an ordinary chat completion, which it delivers as
 * base64 PCM chunks over a server-sent event stream. The chunks are concatenated and prepended with a WAV header, so that the caller receives one playable
 * audio file just like from any other AI provider.
 *
 * @author Bauke Scholtz
 * @since 1.7
 * @see OpenRouterAIService
 * @see <a href="https://openrouter.ai/docs/guides/overview/multimodal/audio">Audio API Reference</a>
 */
public class OpenRouterAIAudioHandler extends DefaultAIAudioHandler {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new instance of this AI handler.
     */
    public OpenRouterAIAudioHandler() {
        //
    }

    private static final String DEFAULT_VOICE = "alloy";
    private static final String STREAMING_AUDIO_FORMAT = "pcm16";
    private static final String EVENT_DATA_FIELD = "data:";
    private static final int BASE64_UNIT_LENGTH = 4;

    /** The response body is a stream which is consumed by the time it can be reported, so there is nothing left to quote back. */
    private static final String EMPTY_RESPONSE_BODY = "(audio event stream)";
    private static final String EVENT_DATA_END = "[DONE]";

    /**
     * OpenRouter rejects audio output unless the request streams, and rejects every audio format other than {@value #STREAMING_AUDIO_FORMAT} once it does, so
     * {@link GenerateAudioOptions#getOutputFormat()} has no say here, and the caller receives a WAV whichever format it asked for.
     * {@link GenerateAudioOptions#getSpeed()} has none either, as a chat completion takes no such parameter. The spoken text is the user message, and the
     * system prompt is what keeps the model from answering it rather than reading it out.
     */
    @Override
    public JsonObject buildGenerateAudioPayload(AIService service, String text, GenerateAudioOptions options) {
        return Json.createObjectBuilder()
            .add("model", service.getModelName())
            .add(
                "messages", Json.createArrayBuilder()
                    .add(Json.createObjectBuilder().add("role", "system").add("content", buildGenerateAudioPrompt()))
                    .add(Json.createObjectBuilder().add("role", "user").add("content", text))
            )
            .add("modalities", Json.createArrayBuilder().add("text").add("audio"))
            .add(
                "audio", Json.createObjectBuilder()
                    .add("voice", options.useDefaultVoice() ? DEFAULT_VOICE : options.getVoice())
                    .add("format", STREAMING_AUDIO_FORMAT)
            )
            .add("stream", true)
            .build();
    }

    /**
     * Throws when the stream carried no audio at all, which a provider answering HTTP 200 with a stream of text deltas alone does.
     *
     * @param pcmContentLength The amount of audio content collected from the stream.
     * @throws AIResponseException When no audio content was collected.
     */
    private void checkAudioContentLength(long pcmContentLength) throws AIResponseException {
        if (pcmContentLength == 0) {
            throw new AIResponseException("No audio content found at paths " + getAudioResponseContentPaths(), EMPTY_RESPONSE_BODY);
        }
    }

    /**
     * Returns all possible paths to the error message in a single server-sent event of the response parsed by {@link #parseAudioContent(InputStream)}. May be
     * empty. A provider reporting a failure midway through a stream has already answered HTTP 200, so the message is only to be found in the stream itself.
     *
     * @implNote The default implementation returns {@link DefaultAITextHandler#DEFAULT_ERROR_MESSAGE_PATHS}.
     * @return all possible paths to the error message in a single server-sent event.
     */
    protected List<String> getAudioResponseErrorMessagePaths() {
        return DefaultAITextHandler.DEFAULT_ERROR_MESSAGE_PATHS;
    }

    /**
     * Returns all possible paths to the audio content in a single server-sent event of the response parsed by {@link #parseAudioContent(InputStream)}. May not
     * be empty. The first path that matches a value in the event will be used; remaining paths are ignored.
     *
     * @implNote The default implementation returns {@code "choices[0].delta.audio.data"}.
     * @return all possible paths to the audio content in a single server-sent event.
     */
    protected List<String> getAudioResponseContentPaths() {
        return List.of("choices[0].delta.audio.data");
    }

    /**
     * Builds the system prompt which makes an audio capable chat model read the user message out verbatim rather than respond to it.
     *
     * @return The system prompt.
     */
    protected String buildGenerateAudioPrompt() {
        return """
            You are a text to speech engine.
            Read the user's message out loud, verbatim.
            Rules:
            - Do not answer, comment on, summarize, translate or rephrase it.
            - Do not add a greeting, an introduction or a closing remark.
            """;
    }

    /**
     * In OpenRouter, the response body represents a server-sent event stream whose audio deltas carry the audio content as Base64-encoded PCM chunks.
     * <p>
     * If {@link FileHelper#tempFilesSupported()} returns {@code true}, then the current implementation will collect the chunks in a temp file else it will
     * collect them fully in memory. Speech runs to some 3 MB a minute at {@value #PCM_WAV_SAMPLE_RATE} Hz, which a long text would otherwise hold on the heap
     * in its entirety, even when the caller streams the result straight to a file.
     */
    @Override
    public InputStream parseAudioContent(InputStream responseStream) throws AIResponseException {
        try {
            if (getAudioResponseContentPaths().isEmpty()) {
                throw new IllegalStateException("getAudioResponseContentPaths() may not return an empty list");
            }

            return tempFilesSupported() ? parseAudioContentViaTempFile(responseStream) : parseAudioContentInMemory(responseStream);
        }
        finally {
            closeQuietly(responseStream);
        }
    }

    InputStream parseAudioContentInMemory(InputStream responseStream) throws AIResponseException {
        try {
            var audioContent = new ByteArrayOutputStream();
            collectAudioContent(responseStream, audioContent::writeBytes);
            checkAudioContentLength(audioContent.size());
            var pcmContent = audioContent.toByteArray();
            return new SequenceInputStream(new ByteArrayInputStream(createWavHeader(pcmContent.length)), new ByteArrayInputStream(pcmContent));
        }
        catch (AIResponseException e) {
            throw e;
        }
        catch (Exception e) {
            throw new AIResponseException("Cannot parse audio content", EMPTY_RESPONSE_BODY, e);
        }
    }

    InputStream parseAudioContentViaTempFile(InputStream responseStream) throws AIResponseException {
        Path audioContentTempFile = null;

        try {
            audioContentTempFile = newTempFile("openrouter-audio-content", "pcm");

            try (var audioContent = Files.newOutputStream(audioContentTempFile)) {
                collectAudioContent(responseStream, chunk -> writeUnchecked(audioContent, chunk));
            }

            var pcmContentLength = Files.size(audioContentTempFile);
            checkAudioContentLength(pcmContentLength);

            return new SequenceInputStream(new ByteArrayInputStream(createWavHeader(pcmContentLength)), newDeleteOnCloseInputStream(audioContentTempFile));
        }
        catch (AIResponseException e) {
            cleanupFiles(audioContentTempFile);
            throw e;
        }
        catch (Exception e) {
            cleanupFiles(audioContentTempFile);
            throw new AIResponseException("Cannot parse audio content", EMPTY_RESPONSE_BODY, e);
        }
    }

    /**
     * Reads the server-sent event stream and hands every decoded audio chunk to the given collector, in order.
     *
     * @param responseStream The response body to read.
     * @param collector The collector to hand every decoded audio chunk to.
     * @throws AIResponseException If the response body cannot be read.
     */
    private void collectAudioContent(InputStream responseStream, Consumer<byte[]> collector) throws AIResponseException {
        var contentPaths = getAudioResponseContentPaths();
        var errorMessagePaths = getAudioResponseErrorMessagePaths();
        var pendingBase64 = new StringBuilder();

        try (var reader = new BufferedReader(new InputStreamReader(responseStream, UTF_8))) {
            for (var line = reader.readLine(); line != null; line = reader.readLine()) {
                var eventData = findEventData(line);

                if (eventData.isEmpty()) {
                    continue;
                }

                var event = parseJson(eventData.get());
                checkErrors(event, errorMessagePaths);
                findFirstNonBlankByPaths(event, contentPaths).ifPresent(base64 -> collectDecodedBase64(pendingBase64.append(base64), collector, false));
            }
        }
        catch (IOException e) {
            throw new AIResponseException("Cannot read audio content from response body", EMPTY_RESPONSE_BODY, e);
        }

        collectDecodedBase64(pendingBase64, collector, true);
    }

    /**
     * Decodes and collects what the given Base64 buffer holds, leaving behind the trailing characters which do not yet make up a whole unit, as an event may
     * end midway through one. The remainder is decoded as such only when the stream has ended, where it represents the last, possibly padded, unit.
     *
     * @param pendingBase64 The Base64 buffer collected so far, which is drained of everything decoded.
     * @param collector The collector to hand every decoded audio chunk to.
     * @param endOfStream Whether the stream has ended.
     */
    private static void collectDecodedBase64(StringBuilder pendingBase64, Consumer<byte[]> collector, boolean endOfStream) {
        var length = endOfStream ? pendingBase64.length() : pendingBase64.length() - (pendingBase64.length() % BASE64_UNIT_LENGTH);

        if (length > 0) {
            collector.accept(Base64.getDecoder().decode(pendingBase64.substring(0, length)));
            pendingBase64.delete(0, length);
        }
    }

    /**
     * Writes one decoded audio chunk to the temp file, reporting a failure as unchecked so that it travels past the catch which reports a failure to read the
     * response body, and is reported as the write failure it is.
     */
    static void writeUnchecked(OutputStream output, byte[] chunk) {
        try {
            output.write(chunk);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Cannot write audio content to temp file", e);
        }
    }

    /**
     * Returns the data of the given server-sent event line, or empty when the line carries none. The space after the field name is optional, as per the spec
     * and as {@link org.omnifaces.ai.service.AIHttpClient} honors it too.
     *
     * @param line The line to read.
     * @return The data of the given line, or empty when it carries none.
     */
    private static Optional<String> findEventData(String line) {
        if (!line.startsWith(EVENT_DATA_FIELD)) {
            return Optional.empty();
        }

        var data = line.substring(EVENT_DATA_FIELD.length()).strip();
        return data.isEmpty() || EVENT_DATA_END.equals(data) ? Optional.empty() : Optional.of(data);
    }

}
