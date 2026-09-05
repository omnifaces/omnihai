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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIProvider.META;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIStrategy;
import org.omnifaces.ai.OmniHai;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.modality.MetaAIAudioHandler;
import org.omnifaces.ai.modality.OpenAIImageHandler;
import org.omnifaces.ai.modality.OpenAITextHandler;
import org.omnifaces.ai.service.LoopbackHttpServer.Answer;

/**
 * Meta AI serves transcription on an ASR endpoint of its own, which accepts one audio format alone, so the audio is converted before it travels. The provider
 * is an HTTP server on the loopback interface.
 */
class MetaAIServiceLoopbackTest {

    /** A WAV at a sample rate the ASR endpoint does not accept, so that the conversion is what makes it acceptable. */
    private static final byte[] WAV = newWav(44100);

    private LoopbackHttpServer server;
    private MetaAIService service;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void startServer() {
        server = LoopbackHttpServer.start();
        service = newService(MetaAIAudioHandler.class);
    }

    private MetaAIService newService(Class<? extends MetaAIAudioHandler> audioHandler) {
        return new MetaAIService(
            AIConfig.of(META, "test-api-key").withModel("muse-voice-transcribe-1.0").withEndpoint(server.endpoint())
                .withStrategy(AIStrategy.of(OpenAITextHandler.class, OpenAIImageHandler.class, audioHandler))
        );
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void transcribeAsync_addressesTheAsrEndpointWithTheConvertedAudio() {
        server.answer(Answer.ofJson("{\"transcript\":\"Hello there.\"}"));

        assertEquals("Hello there.", service.transcribeAsync(WAV).join());
        assertEquals("/v1/asr/transcribe", server.lastRequest().path());
        assertTrue(server.lastRequest().bodyAsString().contains("name=\"request\""), "the ASR endpoint rejects a multipart whose parts are named otherwise");
    }

    @Test
    void transcribeAsync_fromAPath_readsTheFileAndConvertsIt() throws IOException {
        server.answer(Answer.ofJson("{\"transcript\":\"Hello there.\"}"));

        assertEquals("Hello there.", service.transcribeAsync(Files.write(tempDir.resolve("a.wav"), WAV)).join());
    }

    @Test
    void transcribeAsync_pathWhichCannotBeRead_namesTheFile() throws IOException {
        var missing = tempDir.resolve("missing.wav");
        var before = countTempWavs();

        assertTrue(assertThrows(AIException.class, () -> service.transcribeAsync(missing)).getMessage().contains("missing.wav"));
        assertEquals(before, countTempWavs(), "a conversion which failed leaves no temporary file behind either");
    }

    /**
     * The converted audio travels from a temporary file rather than from memory, and that file is gone once the request is done, whether it succeeded or not.
     */
    @Test
    void transcribeAsync_fromAPath_leavesNoTemporaryFileBehind() throws IOException {
        server.answer(Answer.ofJson("{\"transcript\":\"Hello there.\"}"));
        var before = countTempWavs();

        service.transcribeAsync(Files.write(tempDir.resolve("kept.wav"), WAV)).join();

        assertEquals(before, countTempWavs());
        assertTrue(Files.exists(tempDir.resolve("kept.wav")), "the audio the caller passed in is not the library's to delete");
    }

    @Test
    void transcribeAsync_requestWhichFails_leavesNoTemporaryFileBehind() throws IOException {
        server.answer(Answer.ofStatus(500, "{\"error\":{\"message\":\"the endpoint broke\"}}"));
        var before = countTempWavs();
        var audio = Files.write(tempDir.resolve("failing.wav"), WAV);

        assertThrows(CompletionException.class, () -> service.transcribeAsync(audio).join());
        assertEquals(before, countTempWavs());
    }

    private static long countTempWavs() throws IOException {
        try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(file -> file.getFileName().toString().startsWith(OmniHai.name() + "-meta-asr-")).count();
        }
    }

    /**
     * A runtime which permits no temporary file converts the audio in memory instead, which costs memory rather than the transcription.
     */
    @Test
    void transcribeInMemoryAsync_convertsWithoutATemporaryFile() throws IOException {
        server.answer(Answer.ofJson("{\"transcript\":\"Hello there.\"}"));
        var before = countTempWavs();

        assertEquals("Hello there.", service.transcribeInMemoryAsync(Files.write(tempDir.resolve("memory.wav"), WAV)).join());
        assertEquals(before, countTempWavs());
    }

    @Test
    void transcribeInMemoryAsync_pathWhichCannotBeRead_namesTheFile() {
        var missing = tempDir.resolve("missing.wav");

        assertTrue(assertThrows(AIException.class, () -> service.transcribeInMemoryAsync(missing)).getMessage().contains("missing.wav"));
    }

    /**
     * A handler which answers the audio unchanged hands back the caller's own file, which is therefore not the library's to delete.
     */
    @Test
    void transcribeAsync_handlerWhichConvertsNothing_leavesTheCallersFileAlone() throws IOException {
        server.answer(Answer.ofJson("{\"transcript\":\"Hello there.\"}"));
        var audio = Files.write(tempDir.resolve("unconverted.wav"), WAV);
        var service = newService(PassThroughAudioHandler.class);

        assertEquals("Hello there.", service.transcribeAsync(audio).join());
        assertTrue(Files.exists(audio));
    }

    /** A handler which takes the audio as it is, which is what a provider accepting every format would have. */
    public static class PassThroughAudioHandler extends MetaAIAudioHandler {

        private static final long serialVersionUID = 1L;

        @Override
        public Path buildTranscribeContent(Path audio) {
            return audio;
        }

    }

    /**
     * Builds a mono 16-bit PCM WAV of a tenth of a second at the given sample rate.
     */
    private static byte[] newWav(int sampleRate) {
        var format = new AudioFormat(sampleRate, 16, 1, true, false);
        var frames = sampleRate / 10;
        var pcm = new byte[frames * 2];
        var bytes = new ByteArrayOutputStream();

        try (var audio = new AudioInputStream(new ByteArrayInputStream(pcm), format, frames)) {
            AudioSystem.write(audio, Type.WAVE, bytes);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return bytes.toByteArray();
    }

}
