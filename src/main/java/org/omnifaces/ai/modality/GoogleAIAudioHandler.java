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
import static org.omnifaces.ai.helper.JsonHelper.findNonBlankByPath;
import static org.omnifaces.ai.helper.JsonHelper.parseAndCheckErrors;
import static org.omnifaces.ai.mime.MimeType.guessMimeType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Base64;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
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
     * @see <a href="https://ai.google.dev/gemini-api/docs/speech-generation">API Reference</a>
     */
    @Override
    public JsonObject buildGenerateAudioPayload(AIService service, String text, GenerateAudioOptions options) {
        var voiceName = options.useDefaultVoice() ? "Kore" : options.getVoice();

        return Json.createObjectBuilder()
            .add("contents", Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                    .add("parts", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                            .add("text", text)))))
            .add("generationConfig", Json.createObjectBuilder()
                .add("responseModalities", Json.createArrayBuilder().add("AUDIO"))
                .add("speechConfig", Json.createObjectBuilder()
                    .add("voiceConfig", Json.createObjectBuilder()
                        .add("prebuiltVoiceConfig", Json.createObjectBuilder()
                            .add("voiceName", voiceName))))
            )
            .build();
    }

    /**
     * In Google AI, the response body represents a JSON with audio content as Base64-encoded PCM file.
     */
    @Override
    public InputStream parseAudioContent(InputStream responseBody) throws AIResponseException {
        String responseBodyAsString;

        try {
            responseBodyAsString = new String(responseBody.readAllBytes(), UTF_8);
        }
        catch (IOException e) {
            throw new AIResponseException("Cannot parse response body as string", responseBody.toString(), e);
        }

        var responseJson = parseAndCheckErrors(responseBodyAsString, List.of("errors"));
        var audioContentPath = "candidates[0].content.parts[0].inlineData.data";
        var audioContentBase64 = findNonBlankByPath(responseJson, audioContentPath).orElseThrow(() -> new AIResponseException("No audio content found at path " + audioContentPath, responseBodyAsString));

        try {
            var audioContent = Base64.getDecoder().decode(audioContentBase64);

            if (!guessMimeType(audioContent).isAudio()) {
                return new SequenceInputStream(new ByteArrayInputStream(createWavHeader(audioContent.length)), new ByteArrayInputStream(audioContent));
            }
            else {
                return new ByteArrayInputStream(audioContent);
            }
        }
        catch (Exception e) {
            throw new AIResponseException("Cannot Base64-decode audio", responseBodyAsString, e);
        }
    }

    private static final int DEFAULT_GEMINI_AUDIO_WAV_SAMPLE_RATE = 24000;
    private static final int DEFAULT_GEMINI_AUDIO_WAV_CHANNELS = 1;
    private static final int DEFAULT_GEMINI_AUDIO_WAV_BITS_PER_SAMPLE = 16;

    /**
     * Gemini returns a PCM file which is basically a WAV without 44-byte magic header. We need to manually add that header.
     * Technical reason is, Gemini supports streaming JSON output, and therefore content length is unknown beforehand.
     * OmniHai doesn't yet support parsing streaming JSON, OmniHai only supports SSE, so this could be a future improvement.
     * @see <a href="https://www.mmsp.ece.mcgill.ca/Documents/AudioFormats/WAVE/WAVE.html">WAV spec</a>
     */
    private static byte[] createWavHeader(long pcmContentLength) {
        var header = new byte[44];
        var totalDataLength = pcmContentLength + 36;
        var byteRate = (long) DEFAULT_GEMINI_AUDIO_WAV_SAMPLE_RATE * DEFAULT_GEMINI_AUDIO_WAV_CHANNELS * DEFAULT_GEMINI_AUDIO_WAV_BITS_PER_SAMPLE / 8;

        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLength & 0xff);
        header[5] = (byte) ((totalDataLength >> 8) & 0xff);
        header[6] = (byte) ((totalDataLength >> 16) & 0xff);
        header[7] = (byte) ((totalDataLength >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16;
        header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1;
        header[21] = 0;
        header[22] = (byte) DEFAULT_GEMINI_AUDIO_WAV_CHANNELS;
        header[23] = 0;
        header[24] = (byte) (DEFAULT_GEMINI_AUDIO_WAV_SAMPLE_RATE & 0xff);
        header[25] = (byte) ((DEFAULT_GEMINI_AUDIO_WAV_SAMPLE_RATE >> 8) & 0xff);
        header[26] = (byte) ((DEFAULT_GEMINI_AUDIO_WAV_SAMPLE_RATE >> 16) & 0xff);
        header[27] = (byte) ((DEFAULT_GEMINI_AUDIO_WAV_SAMPLE_RATE >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (DEFAULT_GEMINI_AUDIO_WAV_CHANNELS * DEFAULT_GEMINI_AUDIO_WAV_BITS_PER_SAMPLE / 8);
        header[33] = 0;
        header[34] = (byte) DEFAULT_GEMINI_AUDIO_WAV_BITS_PER_SAMPLE;
        header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (pcmContentLength & 0xff);
        header[41] = (byte) ((pcmContentLength >> 8) & 0xff);
        header[42] = (byte) ((pcmContentLength >> 16) & 0xff);
        header[43] = (byte) ((pcmContentLength >> 24) & 0xff);

        return header;
    }
}
