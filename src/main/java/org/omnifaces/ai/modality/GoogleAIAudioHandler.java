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
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.omnifaces.ai.helper.FileHelper.cleanupFiles;
import static org.omnifaces.ai.helper.FileHelper.closeQuietly;
import static org.omnifaces.ai.helper.FileHelper.newDeleteOnCloseInputStream;
import static org.omnifaces.ai.helper.FileHelper.tempFilesSupported;
import static org.omnifaces.ai.helper.JsonHelper.checkErrors;
import static org.omnifaces.ai.helper.JsonHelper.findFirstNonBlankByPaths;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;
import static org.omnifaces.ai.helper.JsonHelper.streamByPath;
import static org.omnifaces.ai.modality.DefaultAITextHandler.DEFAULT_ERROR_MESSAGE_PATHS;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.OmniHai;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.helper.FileHelper;
import org.omnifaces.ai.model.GenerateAudioOptions;
import org.omnifaces.ai.service.GoogleAIService;

/**
 * Default audio handler for Google AI service.
 *
 * @author Bauke Scholtz
 * @since 1.2
 * @see GoogleAIService
 */
public class GoogleAIAudioHandler extends DefaultAIAudioHandler {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new instance of this AI handler.
     */
    public GoogleAIAudioHandler() {
        //
    }

    private static final String DEFAULT_VOICE = "Kore";

    /**
     * @see <a href="https://ai.google.dev/gemini-api/docs/speech-generation">API Reference</a>
     */
    @Override
    public JsonObject buildGenerateAudioPayload(AIService service, String text, GenerateAudioOptions options) {
        var voiceName = options.useDefaultVoice() ? DEFAULT_VOICE : options.getVoice();

        return Json.createObjectBuilder()
            .add(
                "contents", Json.createArrayBuilder()
                    .add(
                        Json.createObjectBuilder()
                            .add(
                                "parts", Json.createArrayBuilder()
                                    .add(
                                        Json.createObjectBuilder()
                                            .add("text", text)
                                    )
                            )
                    )
            )
            .add(
                "generationConfig", Json.createObjectBuilder()
                    .add("responseModalities", Json.createArrayBuilder().add("AUDIO"))
                    .add(
                        "speechConfig", Json.createObjectBuilder()
                            .add(
                                "voiceConfig", Json.createObjectBuilder()
                                    .add(
                                        "prebuiltVoiceConfig", Json.createObjectBuilder()
                                            .add("voiceName", voiceName)
                                    )
                            )
                    )
            )
            .build();
    }

    /**
     * In Google AI, the response body represents a JSON with audio content as Base64-encoded PCM file.
     * <p>
     * If {@link FileHelper#tempFilesSupported()} returns {@code true}, then the current implementation will parse via temp files else it will parse fully in
     * memory.
     */
    @Override
    public InputStream parseAudioContent(InputStream responseStream) throws AIResponseException {
        try {
            if (getAudioResponseContentPaths().isEmpty()) {
                throw new IllegalStateException("getAudioResponseContentPaths() may not return an empty list");
            }

            return tempFilesSupported() ? parseAudioContentViaTempFiles(responseStream) : parseAudioContentInMemory(responseStream);
        }
        finally {
            closeQuietly(responseStream);
        }
    }

    InputStream parseAudioContentInMemory(InputStream responseStream) throws AIResponseException {
        String responseBody;

        try {
            responseBody = new String(responseStream.readAllBytes(), UTF_8);
        }
        catch (IOException e) {
            throw new AIResponseException("Cannot parse response body as string", responseStream, e);
        }

        var responseJson = parseJson(responseBody);
        checkErrors(responseJson, getAudioResponseErrorMessagePaths());
        var paths = getAudioResponseContentPaths();
        var audioContentBase64 = findFirstNonBlankByPaths(responseJson, paths)
            .orElseThrow(() -> new AIResponseException("No audio content found at paths " + paths, responseBody));

        try {
            var audioContent = Base64.getDecoder().decode(audioContentBase64);
            return new SequenceInputStream(new ByteArrayInputStream(createWavHeader(audioContent.length)), new ByteArrayInputStream(audioContent));
        }
        catch (Exception e) {
            throw new AIResponseException("Cannot parse audio content", responseBody, e);
        }
    }

    /**
     * NOTE: full streaming approach is not possible for following reasons:
     * <ol>
     * <li>InputStream can be read only once but we need to check errors as well.</li>
     * <li>JSON-B JsonParser doesn't support getInputStream() of value; it only supports getString(), getInt(), etc.</li>
     * <li>Potential corner cases in JSON format (esp. undefined white space between key and value) which require offset based file channel access.</li>
     * <li>The SSE endpoint is no way around it either: it answers text prompts in deltas, but a speech prompt in one event holding the whole audio, so there
     * would be nothing to collect incrementally, unlike the chunked stream which {@link OpenRouterAIAudioHandler} collects.</li>
     * </ol>
     * Hence, temp file approach is best for now (without resorting to yet another JSON library such as Jackson and/or a specialized/unreusable impl of JSON
     * parsing). In long term we could perhaps make Jackson an optional dependency and prefer over temp files.
     */
    InputStream parseAudioContentViaTempFiles(InputStream responseStream) throws AIResponseException {
        Path responseJsonTempFile = null;
        Path audioContentTempFile = null;

        try {
            var source = responseJsonTempFile = Files.createTempFile(OmniHai.name() + "-gemini-audio-response-", ".json");
            Files.copy(responseStream, responseJsonTempFile, REPLACE_EXISTING);
            checkErrors(responseJsonTempFile, getAudioResponseErrorMessagePaths());
            var paths = getAudioResponseContentPaths();

            try (
                var audioContent = Base64.getDecoder().wrap(
                    paths.stream()
                        .map(path -> streamByPath(source, path))
                        .filter(Objects::nonNull).findFirst()
                        .orElseThrow(() -> new AIResponseException("No audio content found at paths " + paths, source))
                )
            ) {
                audioContentTempFile = Files.createTempFile(OmniHai.name() + "-gemini-audio-content-", ".pcm");
                Files.copy(audioContent, audioContentTempFile, REPLACE_EXISTING);
            }

            var stream = new SequenceInputStream(
                new ByteArrayInputStream(createWavHeader(Files.size(audioContentTempFile))), newDeleteOnCloseInputStream(audioContentTempFile)
            );
            cleanupFiles(responseJsonTempFile);
            return stream;
        }
        catch (AIResponseException e) {
            cleanupFiles(responseJsonTempFile, audioContentTempFile);
            throw e;
        }
        catch (Exception e) {
            cleanupFiles(audioContentTempFile);
            throw new AIResponseException("Cannot parse audio content; temp file left", responseJsonTempFile, e);
        }
    }

    /**
     * Returns all possible paths to the error message in the JSON response parsed by {@link #parseAudioContent(InputStream)}. The first path that matches a
     * value in the JSON response will be used; remaining paths are ignored.
     *
     * @implNote The default implementation returns {@link DefaultAITextHandler#DEFAULT_ERROR_MESSAGE_PATHS}.
     * @return all possible paths to the error message in the JSON response.
     */
    protected List<String> getAudioResponseErrorMessagePaths() {
        return DEFAULT_ERROR_MESSAGE_PATHS;
    }

    /**
     * Returns all possible paths to the audio content in the JSON response parsed by {@link #parseAudioContent(InputStream)}. May not be empty. The first path
     * that matches a value in the JSON response will be used; remaining paths are ignored.
     *
     * @implNote The default implementation returns {@code "candidates[0].content.parts[0].inlineData.data"}.
     * @return all possible paths to the audio content in the JSON response.
     */
    protected List<String> getAudioResponseContentPaths() {
        return List.of("candidates[0].content.parts[0].inlineData.data");
    }

}
