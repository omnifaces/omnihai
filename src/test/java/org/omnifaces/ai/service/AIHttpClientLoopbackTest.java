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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.Level.ALL;
import static java.util.logging.Level.FINER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.service.BaseAIService.HTTP_CLIENT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.exception.AIRateLimitExceededException;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.Sse.Event;
import org.omnifaces.ai.service.LoopbackHttpServer.Answer;

/**
 * What actually travels over the wire: which method and path each call addresses, which headers and body it carries, and what it makes of the answer. The
 * provider is an HTTP server on the loopback interface, so the whole request and response path runs without a network of its own.
 */
class AIHttpClientLoopbackTest {

    private static final JsonObject PAYLOAD = Json.createObjectBuilder().add("model", "custom-1").build();

    private LoopbackHttpServer server;
    private BaseAIService service;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void startServer() {
        server = LoopbackHttpServer.start();
        service = new CustomAIService(CustomAIService.newConfig().withEndpoint(server.endpoint()));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    // =================================================================================================================
    // Reading
    // =================================================================================================================

    @Test
    void get_addressesThePathAndParsesTheAnswer() {
        server.answer(Answer.ofJson("{\"id\":\"file-1\"}"));

        assertEquals("file-1", HTTP_CLIENT.get(service, "files/file-1").join().getString("id"));
        assertEquals("GET", server.lastRequest().method());
        assertEquals("/v1/files/file-1", server.lastRequest().path());
    }

    @Test
    void download_answersTheContentAsItIs() throws IOException {
        server.answer(Answer.ofContent("video/mp4", "the video".getBytes(UTF_8)));

        try (var content = HTTP_CLIENT.download(service, "files/file-1/content").join()) {
            assertEquals("the video", new String(content.readAllBytes(), UTF_8));
        }
    }

    /**
     * A provider which compresses its answer states so, and the content is what the caller gets rather than the compressed bytes.
     */
    @Test
    void postStream_gzippedAnswer_isDecompressed() throws IOException {
        server.answer(Answer.ofGzippedJson("{\"id\":\"file-1\"}"));

        try (var content = HTTP_CLIENT.stream(service, "responses", PAYLOAD).join()) {
            assertEquals("{\"id\":\"file-1\"}", new String(content.readAllBytes(), UTF_8));
        }
    }

    @Test
    void postStream_answerWhichIsNotTheCompressionItAnnounces_saysSo() {
        server.answer(Answer.ofBrokenGzip());

        var future = HTTP_CLIENT.stream(service, "responses", PAYLOAD);

        assertTrue(assertThrows(CompletionException.class, future::join).getCause().getMessage().contains("decompress"));
    }

    // =================================================================================================================
    // Writing
    // =================================================================================================================

    @Test
    void post_carriesThePayloadAsJson() {
        server.answer(Answer.ofJson("{\"id\":\"resp-1\"}"));

        assertEquals("resp-1", HTTP_CLIENT.post(service, "responses", PAYLOAD).join().getString("id"));
        assertEquals("POST", server.lastRequest().method());
        assertEquals(PAYLOAD.toString(), server.lastRequest().bodyAsString());
        assertEquals(List.of("application/json"), server.lastRequest().headers().get("Content-type"));
    }

    @Test
    void post_attachmentOfBytes_carriesItAsItsOwnType() {
        server.answer(Answer.ofJson("{\"text\":\"hello\"}"));

        HTTP_CLIENT.post(service, "audio/transcriptions", new Attachment("the audio".getBytes(UTF_8), MimeType.of("audio/wav"), "a.wav")).join();

        assertEquals("the audio", server.lastRequest().bodyAsString());
        assertEquals(List.of("audio/wav"), server.lastRequest().headers().get("Content-type"));
    }

    @Test
    void post_attachmentOfAFile_carriesTheFileContent() throws IOException {
        server.answer(Answer.ofJson("{\"text\":\"hello\"}"));

        HTTP_CLIENT.post(service, "audio/transcriptions", new Attachment(writeTempFile("a.txt", "the file"))).join();

        assertEquals("the file", server.lastRequest().bodyAsString());
    }

    @Test
    void post_attachmentWhoseFileIsGone_namesTheAttachment() throws IOException {
        var attachment = new Attachment(writeTempFile("a.txt", "the file"));
        Files.delete(attachment.source());

        assertThrows(AIException.class, () -> HTTP_CLIENT.post(service, "audio/transcriptions", attachment));
    }

    @Test
    void delete_addressesThePathWithTheDeleteMethod() {
        server.answer(Answer.ofJson("{\"deleted\":true}"));

        assertTrue(HTTP_CLIENT.delete(service, "files/file-1").join().getBoolean("deleted"));
        assertEquals("DELETE", server.lastRequest().method());
    }

    // =================================================================================================================
    // Uploading
    // =================================================================================================================

    @Test
    void upload_sendsAMultipartCarryingTheFileAndItsName() {
        server.answer(Answer.ofJson("{\"id\":\"file-1\"}"));

        assertEquals(
            "file-1", HTTP_CLIENT.upload(service, "files", new Attachment("the file".getBytes(UTF_8), MimeType.of("text/plain"), "a.txt")).join()
                .getString("id")
        );

        var request = server.lastRequest();
        assertTrue(request.headers().get("Content-type").get(0).startsWith("multipart/form-data; boundary="), request.headers().toString());
        assertTrue(request.bodyAsString().contains("filename=\"" + HTTP_CLIENT.uploadedFileNamePrefix + "a.txt\""), request.bodyAsString());
        assertTrue(request.bodyAsString().contains("the file"), request.bodyAsString());
    }

    @Test
    void upload_underAPartNameOfItsOwn_namesThatPart() throws IOException {
        server.answer(Answer.ofJson("{\"id\":\"file-1\"}"));

        HTTP_CLIENT.upload(service, "asr/transcribe", new Attachment(writeTempFile("a.wav", "the audio")), "audio").join();

        assertTrue(server.lastRequest().bodyAsString().contains("name=\"audio\""), server.lastRequest().bodyAsString());
    }

    @Test
    void upload_whoseFileIsGone_namesTheAttachment() throws IOException {
        var attachment = new Attachment(writeTempFile("a.wav", "the audio"));
        Files.delete(attachment.source());

        assertThrows(AIException.class, () -> HTTP_CLIENT.upload(service, "files", attachment));
    }

    // =================================================================================================================
    // Streaming
    // =================================================================================================================

    @Test
    void stream_dispatchesEveryEventTheProviderSent() {
        server.answer(Answer.ofEvents("data: first\n\n", "data: second\n\n"));
        var events = new ArrayList<String>();

        HTTP_CLIENT.stream(service, "responses", PAYLOAD, event -> events.add(event.value())).join();

        assertEquals(List.of("first", "second"), events);
        assertEquals(List.of("text/event-stream"), server.lastRequest().headers().get("Accept"));
    }

    @Test
    void stream_processorWhichStops_endsTheStream() {
        server.answer(Answer.ofEvents("data: first\n\n", "data: second\n\n"));
        var events = new ArrayList<Event>();

        HTTP_CLIENT.stream(service, "responses", PAYLOAD, event -> events.add(event) && false).join();

        assertEquals(1, events.size());
    }

    // =================================================================================================================
    // Failures the provider states
    // =================================================================================================================

    @Test
    void statusTheProviderRefusesWith_namesWhatItRefused() {
        server.answer(Answer.ofStatus(429, "{\"error\":{\"message\":\"rate limited\"}}"));

        var future = HTTP_CLIENT.post(service, "responses", PAYLOAD);

        assertInstanceOf(AIRateLimitExceededException.class, assertThrows(CompletionException.class, future::join).getCause());
    }

    @Test
    void answerWhichIsCutOffHalfway_saysSo() {
        server.answer(Answer.ofCutOffJson());

        var future = HTTP_CLIENT.post(service, "responses", PAYLOAD);

        assertTrue(assertThrows(CompletionException.class, future::join).getCause().getMessage().contains("read response body"));
    }

    /**
     * A request being followed at FINER states the answer and the headers it came with, so a provider answering an unexpected shape can be traced from the log.
     */
    @Test
    void whenFinerIsOn_logsTheAnswerAndItsHeaders() {
        server.answer(Answer.ofJson("{\"id\":\"resp-1\"}"));
        var records = new ArrayList<LogRecord>();
        var logger = Logger.getLogger(AIHttpClient.class.getPackageName());
        var handler = new Handler() {

            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
                /* Nothing to flush, the records are kept in memory. */ }

            @Override
            public void close() {
                /* Nothing to close, the records are kept in memory. */ }

        };

        handler.setLevel(ALL);
        logger.addHandler(handler);
        var originalLevel = logger.getLevel();
        logger.setLevel(FINER);

        try {
            HTTP_CLIENT.post(service, "responses", PAYLOAD).join();
        }
        finally {
            logger.setLevel(originalLevel);
            logger.removeHandler(handler);
        }

        var messages = records.stream().map(LogRecord::getMessage).toList();
        assertTrue(messages.stream().anyMatch(message -> message.startsWith("Request #")), messages.toString());
        assertTrue(messages.stream().anyMatch(message -> message.startsWith("Response headers for #")), messages.toString());
        assertTrue(messages.stream().anyMatch(message -> message.startsWith("Response for #")), messages.toString());
    }

    private Path writeTempFile(String name, String content) throws IOException {
        return Files.writeString(tempDir.resolve(name), content);
    }

}
