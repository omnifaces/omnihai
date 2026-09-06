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
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;

import javax.imageio.ImageIO;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.AnalyzeVideoOptions;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatInput.Message;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatInput.UploadedFile;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.GenerateAudioOptions;
import org.omnifaces.ai.model.GenerateImageOptions;
import org.omnifaces.ai.model.Sse.Event;
import org.omnifaces.ai.model.Sse.Event.Type;

/**
 * What a service does around a request: which endpoint each operation addresses, what it records in memory, and what it makes of the answer. The request itself
 * is answered by the test rather than sent, so that the flow around it is what is under test.
 */
class BaseAIServiceRequestFlowTest {

    private static final byte[] PNG = newImage("PNG");
    private static final byte[] WAV = "RIFF....WAVEfmt ".getBytes(UTF_8);
    private static final byte[] MP4 = { 0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '2', 0, 0, 0, 0 };

    @TempDir
    private Path tempDir;

    // =================================================================================================================
    // Streaming a chat
    // =================================================================================================================

    /**
     * A provider which serves no stream says so rather than addressing a streaming endpoint it does not have.
     */
    @Test
    void chatStream_providerWhichServesNoStream_namesTheService() {
        var service = new FlowAIService(CustomAIService.newConfigWithPayloadBuildingHandlers());

        var exception = assertThrows(
            UnsupportedOperationException.class, () -> service.chatStream("Hello there.", token -> {
            })
        );

        assertTrue(exception.getMessage().contains(service.getName()), exception.getMessage());
    }

    @Test
    void chatStream_addressesTheStreamingEndpoint() {
        var service = streaming();

        service.chatStream("Hello there.", token -> {
        }).join();

        assertEquals(service.getChatPath(true), service.lastPath);
    }

    /**
     * Memory carries the exchange, so a stream records what it emitted as one assistant message once it completed.
     */
    @Test
    void chatStream_withMemory_recordsTheWholeAnswerItEmitted() {
        var service = streaming("Hello", " there.");
        var options = ChatOptions.newBuilder().withMemory(50).build();
        var tokens = new StringBuilder();

        service.chatStream(newInput("Hi."), options, tokens::append).join();

        assertEquals("Hello there.", tokens.toString());
        assertEquals(List.of("Hi.", "Hello there."), options.getHistory().stream().map(Message::content).toList());
    }

    @Test
    void chatStream_withoutMemory_recordsNothing() {
        var service = streaming("Hello");
        var options = ChatOptions.DEFAULT;
        var tokens = new StringBuilder();

        service.chatStream(newInput("Hi."), options, tokens::append).join();

        assertEquals("Hello", tokens.toString());
        assertThrows(IllegalStateException.class, options::getHistory, "a stream without memory records nothing to replay");
    }

    /**
     * A stream fails on another thread than the caller's, so the failure carries the caller's own stack trace to be traceable at all.
     */
    @Test
    void chatStream_whichFails_carriesTheCallerStackTrace() {
        var service = streaming();
        service.streamFailure = new IllegalStateException("connection dropped");

        var future = service.chatStream("Hello there.", token -> {
        });

        var cause = assertThrows(CompletionException.class, future::join).getCause();
        assertInstanceOf(AIException.class, cause);
        assertTrue(
            List.of(cause.getStackTrace()).stream().anyMatch(element -> element.getMethodName().startsWith("chatStream_whichFails")),
            "the failure must be traceable back to the caller"
        );
    }

    // =================================================================================================================
    // Chatting
    // =================================================================================================================

    /**
     * Memory carries the exchange, so the question is recorded before the payload is built and the answer once it arrived.
     */
    @Test
    void chatAsync_withMemory_recordsBothSidesOfTheExchange() {
        var service = streaming();
        service.chatAnswer = "Hello there.";
        var options = ChatOptions.newBuilder().withMemory(50).build();

        assertEquals("Hello there.", service.chatAsync(newInput("Hi."), options).join());
        assertEquals(List.of("Hi.", "Hello there."), options.getHistory().stream().map(Message::content).toList());
        assertEquals(service.getChatPath(false), service.lastPath);
    }

    @Test
    void chatAsync_withoutMemory_recordsNothing() {
        var service = streaming();
        service.chatAnswer = "Hello there.";
        var options = ChatOptions.DEFAULT;

        assertEquals("Hello there.", service.chatAsync(newInput("Hi."), options).join());
        assertThrows(IllegalStateException.class, options::getHistory, "a chat without memory records nothing to replay");
    }

    /**
     * The synchronous overloads wait on the asynchronous ones, so a failure arrives as an AI failure rather than as the wrapper the waiting produced.
     */
    @Test
    void chat_whichFails_isAnsweredAsAnAiFailure() {
        var service = streaming();
        service.chatFailure = new IllegalStateException("provider refused");

        assertThrows(AIException.class, () -> service.chat("Hi."));
    }

    @Test
    void chat_answersWhatTheAiSaid() {
        var service = streaming();
        service.chatAnswer = "Hello there.";

        assertEquals("Hello there.", service.chat("Hi."));
    }

    /**
     * A caller which states no prompt of its own gets the one the image handler states, as the AI would otherwise be asked nothing at all.
     */
    @Test
    void analyzeImageAsync_withoutAPrompt_asksWhatTheImageHandlerAsks() {
        var service = streaming();
        service.chatAnswer = "A red pixel.";

        assertEquals("A red pixel.", service.analyzeImageAsync(PNG, null).join());
    }

    // =================================================================================================================
    // Uploading an attachment
    // =================================================================================================================

    @Test
    void upload_addressesTheFilesEndpointAndAnswersTheFileId() {
        var service = streaming();

        assertEquals("file-1", service.upload(newPngAttachment(), ChatOptions.DEFAULT));
        assertEquals(service.getFilesPath(), service.lastPath);
    }

    /**
     * Memory carries the uploaded file against the message it was attached to, so a following turn references it rather than uploading it again.
     */
    @Test
    void upload_withMemory_recordsTheUploadedFileAgainstTheQuestion() {
        var options = ChatOptions.newBuilder().withMemory(50).build();
        options.recordMessage(Role.USER, "What is this?");

        streaming().upload(newPngAttachment(), options);

        assertEquals(List.of("file-1"), options.getHistory().get(0).uploadedFiles().stream().map(UploadedFile::id).toList());
    }

    @Test
    void upload_whichFails_isAnsweredAsAnAiFailure() {
        var service = streaming();
        service.uploadFailure = new IllegalStateException("upload refused");
        var attachment = newPngAttachment();

        assertThrows(AIException.class, () -> service.upload(attachment, ChatOptions.DEFAULT));
    }

    // =================================================================================================================
    // Analyzing what is attached
    // =================================================================================================================

    @Test
    void analyzeImageAsync_addressesTheChatEndpointAndAnswersWhatTheAiSaid() {
        var service = streaming();
        service.chatAnswer = "A red pixel.";

        assertEquals("A red pixel.", service.analyzeImageAsync(PNG, "What is this?").join());
        assertEquals(service.getChatPath(false), service.lastPath);
    }

    @Test
    void analyzeImageAsync_fromAPath_readsTheFile() throws IOException {
        var image = Files.write(tempDir.resolve("a.png"), PNG);
        var service = streaming();
        service.chatAnswer = "A red pixel.";

        assertEquals("A red pixel.", service.analyzeImageAsync(image, "What is this?").join());
    }

    @Test
    void generateAltTextAsync_asksForAltTextWithoutTheCallerStatingAPrompt() {
        var service = streaming();
        service.chatAnswer = "A red pixel.";

        assertEquals("A red pixel.", service.generateAltTextAsync(PNG).join());
        assertEquals("A red pixel.", service.generateAltTextAsync(writeToTempDir("b.png", PNG)).join());
    }

    @Test
    void transcribeAsync_addressesTheChatEndpointWhenTheProviderHasNoTranscriptionEndpoint() {
        var service = streaming();
        service.chatAnswer = "Hello there.";

        assertEquals("Hello there.", service.transcribeAsync(WAV).join());
        assertEquals("Hello there.", service.transcribeAsync(writeToTempDir("a.wav", WAV)).join());
        assertEquals(service.getChatPath(false), service.lastPath);
    }

    @Test
    void analyzeVideoAsync_addressesTheChatEndpointAndAnswersWhatTheAiSaid() {
        var service = streaming();
        service.chatAnswer = "A blank frame.";

        assertEquals("A blank frame.", service.analyzeVideoAsync(MP4, "What happens?", AnalyzeVideoOptions.DEFAULT).join());
        assertEquals("A blank frame.", service.analyzeVideoAsync(writeToTempDir("a.mp4", MP4), null, AnalyzeVideoOptions.DEFAULT).join());
    }

    // =================================================================================================================
    // Generating content
    // =================================================================================================================

    @Test
    void generateImageAsync_addressesTheImageEndpointAndAnswersTheContent() {
        var service = streaming();

        assertEquals("image", new String(service.generateImageAsync("A red pixel.", GenerateImageOptions.DEFAULT).join(), UTF_8));
        assertEquals(service.getGenerateImagePath(), service.lastPath);
    }

    @Test
    void generateImageAsync_withoutAPrompt_isRefused() {
        var service = streaming();

        assertThrows(IllegalArgumentException.class, () -> service.generateImageAsync(" ", GenerateImageOptions.DEFAULT));
    }

    @Test
    void generateAudioAsync_answersTheWholeStreamAsBytes() {
        var service = streaming();

        assertEquals("audio", new String(service.generateAudioAsync("Hello there.", GenerateAudioOptions.DEFAULT).join(), UTF_8));
        assertEquals(service.getGenerateAudioPath(), service.lastPath);
    }

    @Test
    void generateAudioAsync_toAPath_writesTheStreamToIt() throws IOException {
        var path = tempDir.resolve("out.mp3");

        streaming().generateAudioAsync("Hello there.", path, GenerateAudioOptions.DEFAULT).join();

        assertEquals("audio", Files.readString(path));
    }

    @Test
    void generateAudioAsync_toAPathWhichCannotBeWritten_saysSo() {
        var service = streaming();
        var path = tempDir.resolve("missing").resolve("out.mp3");

        var future = service.generateAudioAsync("Hello there.", path, GenerateAudioOptions.DEFAULT);

        assertTrue(assertThrows(CompletionException.class, future::join).getMessage().contains(path.toString()));
    }

    @Test
    void generateAudioAsync_streamWhichCannotBeRead_saysSo() {
        var service = streaming();
        service.audioStream = new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("stream broke");
            }

        };

        var future = service.generateAudioAsync("Hello there.", GenerateAudioOptions.DEFAULT);

        assertInstanceOf(UncheckedIOException.class, assertThrows(CompletionException.class, future::join).getCause());
    }

    private Path writeToTempDir(String name, byte[] content) {
        try {
            return Files.write(tempDir.resolve(name), content);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ChatInput newInput(String message) {
        return ChatInput.newBuilder().message(message).build();
    }

    private static Attachment newPngAttachment() {
        return new Attachment(PNG, MimeType.guessMimeType(PNG), "a.png");
    }

    private static FlowAIService streaming(String... tokens) {
        var service = new FlowAIService(CustomAIService.newConfigWithPayloadBuildingHandlers());
        service.streaming = true;
        service.tokens = List.of(tokens);
        return service;
    }

    private static byte[] newImage(String format) {
        try {
            var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, 0xFF0000);
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(image, format, bytes);
            return bytes.toByteArray();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A provider which answers every request itself, recording which endpoint it was addressed on.
     */
    private static final class FlowAIService extends CustomAIService {

        private static final long serialVersionUID = 1L;

        private transient boolean streaming;
        private transient List<String> tokens = List.of();
        private transient String lastPath;
        private transient String chatAnswer = "answer";
        private transient RuntimeException chatFailure;
        private transient RuntimeException streamFailure;
        private transient RuntimeException uploadFailure;
        private transient InputStream audioStream;

        private FlowAIService(AIConfig config) {
            super(config);
        }

        @Override
        public boolean supportsStreaming() {
            return streaming;
        }

        @Override
        public boolean supportsModality(AIModality modality) {
            return true;
        }

        @Override
        public boolean supportsFileAttachments() {
            return true;
        }

        @Override
        protected String getFilesPath() {
            return "files";
        }

        @Override
        protected CompletableFuture<Void> asyncPostAndProcessStreamEvents(String path, JsonObject payload, Predicate<Event> eventProcessor) {
            lastPath = path;

            if (streamFailure != null) {
                return failedFuture(streamFailure);
            }

            tokens.forEach(
                token -> eventProcessor
                    .test(new Event(Type.DATA, "{\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"" + token + "\"}}]}"))
            );
            return completedFuture(null);
        }

        @Override
        protected CompletableFuture<String> asyncPostAndParseChatResponse(String path, JsonObject payload, ChatOptions options) {
            lastPath = path;
            return chatFailure != null ? failedFuture(chatFailure) : completedFuture(chatAnswer);
        }

        @Override
        protected CompletableFuture<String> asyncUploadAndParseFileIdResponse(String path, Attachment attachment) {
            lastPath = path;
            return uploadFailure != null ? failedFuture(uploadFailure) : completedFuture("file-1");
        }

        @Override
        protected void awaitUploadedFile(Attachment attachment, String fileId) {
            // The provider of this test hands out a file which is ready at once.
        }

        @Override
        protected CompletableFuture<byte[]> asyncPostAndParseImageContent(String path, JsonObject payload) {
            lastPath = path;
            return completedFuture("image".getBytes(UTF_8));
        }

        @Override
        protected CompletableFuture<InputStream> asyncPostAndStreamAudioContent(String path, JsonObject payload) {
            lastPath = path;
            return completedFuture(audioStream != null ? audioStream : new ByteArrayInputStream("audio".getBytes(UTF_8)));
        }

    }

}
