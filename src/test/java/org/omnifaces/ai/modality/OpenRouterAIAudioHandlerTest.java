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
import static org.omnifaces.ai.AIProvider.OPENROUTER;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.GenerateAudioOptions;

class OpenRouterAIAudioHandlerTest {

    private final OpenRouterAIAudioHandler handler = new OpenRouterAIAudioHandler();

    private static InputStream openAudioStream() {
        return OpenRouterAIAudioHandlerTest.class.getResourceAsStream("/openrouter-audio-stream.sse");
    }

    // =================================================================================================================
    // Request payload
    // =================================================================================================================

    @Test
    void buildGenerateAudioPayload_streamsPcmAndReadsTheTextVerbatim() {
        var payload = handler.buildGenerateAudioPayload(newService(), "Hello world", GenerateAudioOptions.DEFAULT);

        assertTrue(payload.getBoolean("stream"), "audio output requires streaming");
        assertEquals("pcm16", payload.getJsonObject("audio").getString("format"), "streaming supports pcm16 only");
        assertEquals("alloy", payload.getJsonObject("audio").getString("voice"));
        assertEquals("[\"text\",\"audio\"]", payload.getJsonArray("modalities").toString());

        var messages = payload.getJsonArray("messages");
        assertEquals("system", messages.getJsonObject(0).getString("role"));
        assertEquals("user", messages.getJsonObject(1).getString("role"));
        assertEquals("Hello world", messages.getJsonObject(1).getString("content"), "the spoken text is the user message");
    }

    @Test
    void buildGenerateAudioPayload_customVoice() {
        var options = GenerateAudioOptions.newBuilder().voice("verse").outputFormat("mp3").build();
        var payload = handler.buildGenerateAudioPayload(newService(), "Hello world", options);

        assertEquals("verse", payload.getJsonObject("audio").getString("voice"));
        assertEquals("pcm16", payload.getJsonObject("audio").getString("format"), "the requested format has no say here");
    }

    // =================================================================================================================
    // Response parsing
    // =================================================================================================================

    @Test
    void parseAudioContent_assemblesTheChunksIntoAWav() throws IOException {
        var audioContent = handler.parseAudioContent(openAudioStream()).readAllBytes();

        assertTrue(MimeType.guessMimeType(audioContent).isAudio(), "assembled content must be recognized as audio");
        assertEquals("wav", MimeType.guessMimeType(audioContent).extension());
        assertEquals(140, audioContent.length, "44 byte header plus both decoded chunks");
    }

    @Test
    void parseAudioContent_inMemoryAndViaTempFile_yieldTheSameAudio() throws IOException {
        var inMemory = handler.parseAudioContentInMemory(openAudioStream()).readAllBytes();
        var viaTempFile = handler.parseAudioContentViaTempFile(openAudioStream()).readAllBytes();

        assertArrayEquals(inMemory, viaTempFile, "both collectors must yield the same audio");
    }

    @Test
    void parseAudioContent_overriddenContentPath_isUsed() throws IOException {
        var handlerWithOwnPath = new OpenRouterAIAudioHandler() {

            private static final long serialVersionUID = 1L;

            @Override
            protected List<String> getAudioResponseContentPaths() {
                return List.of("choices[0].delta.speech.bytes");
            }

        };
        var responseStream = new ByteArrayInputStream(
            "data: {\"choices\":[{\"delta\":{\"speech\":{\"bytes\":\"AAAAAAAAAAA=\"}}}]}\n\ndata: [DONE]\n".getBytes(UTF_8)
        );

        var audioContent = handlerWithOwnPath.parseAudioContent(responseStream).readAllBytes();

        assertEquals(44 + 8, audioContent.length, "the retargeted path must be the one which is read");
    }

    @Test
    void parseAudioContent_base64SplitMidUnit_isDecodedWhole() throws IOException {
        var base64 = Base64.getEncoder().encodeToString("Hello world!".getBytes(UTF_8)); // 16 chars, so every split below lands mid-unit.
        var responseStream = newEventStream(newAudioEvent(base64.substring(0, 6)), newAudioEvent(base64.substring(6)));

        var audioContent = handler.parseAudioContent(responseStream).readAllBytes();

        assertEquals("Hello world!", new String(audioContent, 44, audioContent.length - 44, UTF_8), "a unit split across events must survive");
    }

    @Test
    void parseAudioContent_dataFieldWithoutSpace_isRead() throws IOException {
        var base64 = Base64.getEncoder().encodeToString(new byte[8]);
        var responseStream = new ByteArrayInputStream(("data:" + newAudioEvent(base64) + "\n\ndata:[DONE]\n").getBytes(UTF_8));

        var audioContent = handler.parseAudioContent(responseStream).readAllBytes();

        assertEquals(44 + 8, audioContent.length, "the space after the field name is optional");
    }

    @Test
    void parseAudioContent_errorEvent_reportsTheProviderMessage() {
        var responseStream = newEventStream("{\"error\":{\"message\":\"Upstream provider is on fire\"}}");

        var exception = assertThrows(AIResponseException.class, () -> handler.parseAudioContent(responseStream));
        assertTrue(exception.getMessage().startsWith("Upstream provider is on fire"), "a mid-stream failure must surface the provider's own message");
    }

    private static String newAudioEvent(String base64) {
        return "{\"choices\":[{\"delta\":{\"audio\":{\"data\":\"" + base64 + "\"}}}]}";
    }

    private static InputStream newEventStream(String... events) {
        var stream = new StringBuilder();

        for (var event : events) {
            stream.append("data: ").append(event).append("\n\n");
        }

        return new ByteArrayInputStream(stream.append("data: [DONE]\n").toString().getBytes(UTF_8));
    }

    @Test
    void parseAudioContent_withoutAudioDelta_throwsException() {
        var responseStream = new ByteArrayInputStream("data: {\"choices\":[{\"delta\":{\"content\":\"\"}}]}\n\ndata: [DONE]\n".getBytes(UTF_8));

        assertThrows(AIResponseException.class, () -> handler.parseAudioContent(responseStream));
    }

    private static AIService newService() {
        return AIConfig.of(OPENROUTER, "test-api-key").withModel("openai/gpt-audio-mini").createService();
    }

    /**
     * The end marker and a data line carrying nothing are both skipped rather than collected as audio.
     */
    @Test
    void parseAudioContent_endMarkerAndEmptyDataLines_areSkipped() throws IOException {
        var base64 = Base64.getEncoder().encodeToString(new byte[8]);
        var responseStream = new ByteArrayInputStream(
            ("data:\n\ndata: " + newAudioEvent(base64) + "\n\ndata: [DONE]\n\ndata: " + newAudioEvent(base64) + "\n").getBytes(UTF_8)
        );

        var audioContent = handler.parseAudioContent(responseStream).readAllBytes();

        assertEquals(44 + 16, audioContent.length, "the two audio events are collected and nothing else is");
    }

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

    /**
     * A handler which states no path to the audio has nothing to collect, and says which method to implement.
     */
    @Test
    void parseAudioContent_withoutAnyStatedPath_namesTheMethod() {
        var pathless = new OpenRouterAIAudioHandler() {

            @Override
            protected List<String> getAudioResponseContentPaths() {
                return List.of();
            }

        };
        var responseStream = new ByteArrayInputStream("data: [DONE]\n".getBytes(UTF_8));

        assertThrows(IllegalStateException.class, () -> pathless.parseAudioContent(responseStream));
    }

    /**
     * Content which does not decode is reported as an unusable answer whichever route collected it.
     */
    @Test
    void parseAudioContent_contentWhichDoesNotDecode_saysSo() {
        assertThrows(AIResponseException.class, () -> handler.parseAudioContentInMemory(undecodableStream()));
        assertThrows(AIResponseException.class, () -> handler.parseAudioContentViaTempFile(undecodableStream()));
    }

    /**
     * A chunk which cannot be written to the temp file is reported as the write failure it is, rather than as the failure to read the response which the catch
     * around the collecting travels through.
     */
    @Test
    void writeUnchecked_chunkWhichCannotBeWritten_saysItCouldNotWriteIt() {
        var full = new OutputStream() {

            @Override
            public void write(int b) throws IOException {
                throw new IOException("no space left on device");
            }

        };

        var exception = assertThrows(UncheckedIOException.class, () -> OpenRouterAIAudioHandler.writeUnchecked(full, new byte[] { 1, 2, 3 }));

        assertTrue(exception.getMessage().contains("temp file"), exception.getMessage());
    }

    private static ByteArrayInputStream undecodableStream() {
        return new ByteArrayInputStream("data: {\"choices\":[{\"delta\":{\"audio\":{\"data\":\"!!!\"}}}]}\n\n".getBytes(UTF_8));
    }

}
