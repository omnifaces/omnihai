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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.omnifaces.ai.service.BaseAIService.HTTP_CLIENT;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.DeliberateFailures;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.GenerateAudioOptions;
import org.omnifaces.ai.model.GenerateImageOptions;
import org.omnifaces.ai.model.GenerateVideoOptions;
import org.omnifaces.ai.model.VideoGeneration.Job;
import org.omnifaces.ai.model.VideoGeneration.Status;
import org.omnifaces.ai.service.LoopbackHttpServer.Answer;

/**
 * What a service sends and what it makes of the answer, over an HTTP server on the loopback interface rather than over a seam. This covers the request paths a
 * test double would otherwise replace: the chat, the upload with its stale-file cleanup, the generation endpoints and the video generation job cycle.
 */
class BaseAIServiceLoopbackTest {

    private static final String CHAT_ANSWER = "{\"choices\":[{\"message\":{\"content\":\"Hello there.\"}}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2}}";

    private LoopbackHttpServer server;
    private CustomAIService service;

    /**
     * The cleanup of stale uploaded files reports a failure at WARNING with the stack trace, which two of these tests provoke on purpose.
     */
    @BeforeAll
    static void dropTheWarningsOfTheDeliberatelyFailingCleanup() {
        DeliberateFailures.dropMessagesContaining(BaseAIService.class.getPackageName(), "Failed to list files for cleanup", "Failed to cleanup file");
    }

    @BeforeEach
    void startServer() {
        server = LoopbackHttpServer.start();
        service = new CustomAIService(newConfig());
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    // =================================================================================================================
    // Chatting
    // =================================================================================================================

    @Test
    void chatAsync_addressesTheChatEndpointAndReadsTheAnswer() {
        server.answer(Answer.ofJson(CHAT_ANSWER));

        assertEquals("Hello there.", service.chatAsync(ChatInput.newBuilder().message("Hi.").build(), ChatOptions.DEFAULT).join());
        assertEquals("/v1/chat", server.lastRequest().path());
    }

    /**
     * Options which account for usage record what the answer states, so a caller can hold the cost of the conversation it is running.
     */
    @Test
    void chatAsync_optionsWhichAccountForUsage_recordWhatTheAnswerStates() {
        server.answer(Answer.ofJson(CHAT_ANSWER));
        var options = ChatOptions.newBuilder().withMemory(50).build();

        service.chatAsync(ChatInput.newBuilder().message("Hi.").build(), options).join();

        assertEquals(3, options.getLastUsage().inputTokens());
    }

    @Test
    void chatStream_dispatchesEveryTokenTheProviderSent() {
        server.answer(
            Answer.ofEvents(
                "data: {\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n",
                "data: {\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\" there.\"}}]}\n\n"
            )
        );
        var tokens = new StringBuilder();

        streaming().chatStream(ChatInput.newBuilder().message("Hi.").build(), ChatOptions.DEFAULT, tokens::append).join();

        assertEquals("Hello there.", tokens.toString());
    }

    // =================================================================================================================
    // Uploading
    // =================================================================================================================

    @Test
    void upload_answersTheFileIdTheProviderStated() {
        server.answer(Answer.ofJson("{\"id\":\"file-1\"}"));

        assertEquals("file-1", uploading().upload(newAttachment(), ChatOptions.DEFAULT));
        assertEquals("/v1/files", server.lastRequest().path());
    }

    /**
     * A provider whose file listing this library can read has its own leftovers deleted after an upload, so a file nobody claimed does not pile up forever. A
     * file which this library did not upload, and one which is not old enough yet, are both left alone.
     */
    @Test
    void upload_providerWhoseListingIsReadable_deletesTheStaleFilesItUploadedItself() {
        var stale = Instant.now().minus(3, ChronoUnit.DAYS).getEpochSecond();
        var fresh = Instant.now().getEpochSecond();
        server.answer(
            Answer.ofJson("{\"id\":\"file-1\"}"),
            Answer.ofJson(
                "{\"data\":["
                    + "{\"id\":\"file-2\",\"filename\":\"" + HTTP_CLIENT.uploadedFileNamePrefix + "old.txt\",\"created_at\":" + stale + "},"
                    + "{\"id\":\"file-3\",\"filename\":\"" + HTTP_CLIENT.uploadedFileNamePrefix + "new.txt\",\"created_at\":" + fresh + "},"
                    + "{\"id\":\"file-4\",\"filename\":\"someone-elses.txt\",\"created_at\":" + stale + "}"
                    + "]}"
            ),
            Answer.ofJson("{\"deleted\":true}")
        );

        assertEquals("file-1", cleaningUp().upload(newAttachment(), ChatOptions.DEFAULT));

        server.awaitRequests(3);
        assertEquals("/v1/files/file-2", server.lastRequest().path(), "the stale file this library uploaded is the only one deleted");
    }

    /**
     * The cleanup runs beside the upload, so a listing which cannot be read costs the caller nothing.
     */
    @Test
    void upload_listingWhichCannotBeRead_doesNotFailTheUpload() {
        server.answer(Answer.ofJson("{\"id\":\"file-1\"}"), Answer.ofStatus(500, "{\"error\":{\"message\":\"listing broke\"}}"));

        assertEquals("file-1", cleaningUp().upload(newAttachment(), ChatOptions.DEFAULT));

        server.awaitRequests(2);
    }

    @Test
    void upload_emptyListing_deletesNothing() {
        server.answer(Answer.ofJson("{\"id\":\"file-1\"}"), Answer.ofJson("{\"data\":[]}"));

        cleaningUp().upload(newAttachment(), ChatOptions.DEFAULT);

        server.awaitRequests(2);
        assertEquals(2, server.requestCount(), "an empty listing gives nothing to delete");
    }

    /**
     * The cleanup is best effort: a listing without files at all, an entry stating no id, and a delete the provider refuses all leave the upload untouched.
     */
    @Test
    void upload_listingWhichCannotBeActedOn_leavesTheUploadUntouched() {
        server.answer(Answer.ofJson("{\"id\":\"file-1\"}"), Answer.ofJson("{}"));

        assertEquals("file-1", cleaningUp().upload(newAttachment(), ChatOptions.DEFAULT));

        server.awaitRequests(2);
    }

    @Test
    void upload_staleFileWhoseDeleteIsRefused_leavesTheUploadUntouched() {
        var stale = Instant.now().minus(3, ChronoUnit.DAYS).getEpochSecond();
        server.answer(
            Answer.ofJson("{\"id\":\"file-1\"}"),
            Answer.ofJson(
                "{\"data\":["
                    + "{\"filename\":\"" + HTTP_CLIENT.uploadedFileNamePrefix + "nameless.txt\",\"created_at\":" + stale + "},"
                    + "{\"id\":\"file-9\",\"filename\":\"" + HTTP_CLIENT.uploadedFileNamePrefix + "timeless.txt\"},"
                    + "{\"id\":\"file-2\",\"filename\":\"" + HTTP_CLIENT.uploadedFileNamePrefix + "old.txt\",\"created_at\":" + stale + "}"
                    + "]}"
            ),
            Answer.ofStatus(500, "{\"error\":{\"message\":\"delete broke\"}}")
        );

        assertEquals("file-1", cleaningUp().upload(newAttachment(), ChatOptions.DEFAULT));

        server.awaitRequests(3);
    }

    @Test
    void upload_whichTheProviderRefuses_isAnsweredAsAnAiFailure() {
        server.answer(Answer.ofStatus(400, "{\"error\":{\"message\":\"too large\"}}"));

        var attachment = newAttachment();
        var uploading = uploading();

        assertThrows(AIException.class, () -> uploading.upload(attachment, ChatOptions.DEFAULT));
    }

    // =================================================================================================================
    // Generating
    // =================================================================================================================

    @Test
    void generateImageAsync_addressesTheImageEndpointAndAnswersTheContent() {
        server.answer(Answer.ofJson("{\"data\":[{\"b64_json\":\"" + base64("the image") + "\"}]}"));

        assertEquals("the image", new String(service.generateImageAsync("A red pixel.", GenerateImageOptions.DEFAULT).join(), UTF_8));
    }

    @Test
    void generateAudioAsync_answersTheStreamedContent() {
        server.answer(Answer.ofContent("audio/mpeg", "the audio".getBytes(UTF_8)));

        assertEquals("the audio", new String(service.generateAudioAsync("Hello there.", GenerateAudioOptions.DEFAULT).join(), UTF_8));
    }

    // =================================================================================================================
    // Generating a video, which is a job rather than a request
    // =================================================================================================================

    @Test
    void generateVideo_answersTheSubmittedJob() {
        server.answer(Answer.ofJson("{\"id\":\"job-1\",\"status\":\"queued\"}"));

        var generation = service.generateVideo("A red pixel.", GenerateVideoOptions.DEFAULT);

        assertEquals("job-1", generation.jobId());
        assertEquals(Status.PENDING, generation.status());
        assertEquals("/v1/videos", server.lastRequest().path());
    }

    @Test
    void pollVideo_addressesTheJobPathAndAnswersItsState() {
        server.answer(Answer.ofJson("{\"id\":\"job-1\",\"status\":\"queued\"}"), Answer.ofJson("{\"status\":\"in_progress\"}"));

        service.generateVideo("A red pixel.", GenerateVideoOptions.DEFAULT);
        var job = service.pollVideo(Job.pending("job-1", null));

        assertEquals(Status.RUNNING, job.status());
        assertEquals("/v1/videos/job-1", server.lastRequest().path());
    }

    @Test
    void generateVideoAsync_waitsUntilTheJobIsDone() {
        server.answer(
            Answer.ofJson("{\"id\":\"job-1\",\"status\":\"queued\"}"),
            Answer.ofJson("{\"status\":\"in_progress\"}"),
            Answer.ofJson("{\"status\":\"completed\",\"unsigned_urls\":[\"videos/job-1/content\"]}")
        );

        var generation = service.generateVideoAsync("A red pixel.", fastPolling()).join();

        assertEquals(Status.COMPLETED, generation.status());
    }

    @Test
    void generateVideoAsync_whichTheProviderRefuses_failsTheJob() {
        server.answer(Answer.ofStatus(400, "{\"error\":{\"message\":\"prompt refused\"}}"));

        var future = service.generateVideoAsync("A red pixel.", fastPolling());

        assertNotNull(assertThrows(Exception.class, future::join));
    }

    @Test
    void downloadVideo_answersTheContentOfTheCompletedJob() throws IOException {
        server.answer(
            Answer.ofJson("{\"id\":\"job-1\",\"status\":\"queued\"}"),
            Answer.ofContent("video/mp4", "the video".getBytes(UTF_8))
        );

        service.generateVideo("A red pixel.", GenerateVideoOptions.DEFAULT);

        try (var content = service.downloadVideo(Job.pending("job-1", null))) {
            assertEquals("the video", new String(content.readAllBytes(), UTF_8));
        }

        assertEquals("/v1/videos/job-1/content", server.lastRequest().path());
    }

    @Test
    void awaitVideoCompletion_answersTheTerminalState() {
        server.answer(Answer.ofJson("{\"status\":\"completed\",\"unsigned_urls\":[\"videos/job-1/content\"]}"));

        var job = service.awaitVideoCompletion(Job.pending("job-1", null), fastPolling()).join();

        assertEquals(Status.COMPLETED, job.status());
    }

    /**
     * A job which the provider fails is answered as a failure rather than as a completed job with nothing in it.
     */
    @Test
    void generateVideoAsync_jobWhichFailsWhilePolling_failsTheFuture() {
        server.answer(
            Answer.ofJson("{\"id\":\"job-1\",\"status\":\"queued\"}"),
            Answer.ofStatus(500, "{\"error\":{\"message\":\"the job broke\"}}")
        );

        var future = service.generateVideoAsync("A red pixel.", fastPolling());

        assertNotNull(assertThrows(Exception.class, future::join));
    }

    /**
     * The synchronous overloads wait on the asynchronous ones, so a failure arrives as an AI failure rather than as the wrapper the waiting produced.
     */
    @Test
    void pollVideo_whichTheProviderRefuses_isAnsweredAsAnAiFailure() {
        server.answer(Answer.ofStatus(404, "{\"error\":{\"message\":\"no such job\"}}"));

        var job = Job.pending("job-1", null);

        assertThrows(AIException.class, () -> service.pollVideo(job));
    }

    /**
     * An operation which states no options of its own accounts for no usage either, as there is nothing holding the total.
     */
    @Test
    void analyzeImageAsync_addressesTheChatEndpointWithoutAccountingForUsage() {
        server.answer(Answer.ofJson(CHAT_ANSWER));

        assertEquals("Hello there.", service.analyzeImageAsync(PNG, "What is this?").join());
        assertEquals("/v1/chat", server.lastRequest().path());
    }

    private static GenerateVideoOptions fastPolling() {
        return GenerateVideoOptions.newBuilder().pollInterval(Duration.ofMillis(10)).maxWait(Duration.ofSeconds(10)).build();
    }

    private static final byte[] PNG = newPng();

    private static byte[] newPng() {
        try {
            var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", bytes);
            return bytes.toByteArray();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Attachment newAttachment() {
        return new Attachment("the file".getBytes(UTF_8), MimeType.of("text/plain"), "a.txt");
    }

    private static String base64(String content) {
        return Base64.getEncoder().encodeToString(content.getBytes(UTF_8));
    }

    private AIConfig newConfig() {
        return CustomAIService.newConfigWithPayloadBuildingHandlers().withEndpoint(server.endpoint());
    }

    private CustomAIService streaming() {
        return new CustomAIService(newConfig()) {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean supportsStreaming() {
                return true;
            }

        };
    }

    /** A provider which serves a files endpoint but no listing this library knows the shape of, so nothing is cleaned up after an upload. */
    private CustomAIService uploading() {
        return new CustomAIService(newConfig()) {

            private static final long serialVersionUID = 1L;

            @Override
            protected String getFilesPath() {
                return "files";
            }

        };
    }

    /** A provider whose uploaded file listing this library knows the shape of, which is what turns the stale file cleanup on. */
    private CustomAIService cleaningUp() {
        return new CustomAIService(newConfig()) {

            private static final long serialVersionUID = 1L;

            @Override
            protected String getFilesPath() {
                return "files";
            }

            @Override
            protected UploadedFileJsonStructure getUploadedFileJsonStructure() {
                return new UploadedFileJsonStructure("data", "filename", "id", "created_at");
            }

        };
    }

}
