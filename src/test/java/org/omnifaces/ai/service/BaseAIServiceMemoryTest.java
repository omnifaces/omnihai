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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.exception.AIServiceUnavailableException;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatOptions;

/**
 * Validates the conversation-memory recording contract of {@link BaseAIService}:
 * <ul>
 * <li>a successful turn records exactly one user message and one assistant message</li>
 * <li>the request payload carries the preceding history plus the current user message exactly once, never twice</li>
 * <li>the user message is recorded before the payload is built, so file attachments uploaded while building it anchor to that message</li>
 * </ul>
 */
class BaseAIServiceMemoryTest {

    private static final URI ENDPOINT = URI.create("https://example.invalid");

    @Test
    void successRecordsUserAndAssistantMessageExactlyOnce() {
        var service = new StubAIService();
        service.enqueueSuccess("ok");

        var options = ChatOptions.newBuilder().withMemory().build();

        assertEquals("ok", service.chat("hi", options));

        var history = options.getHistory();
        assertEquals(2, history.size());
        assertEquals(Role.USER, history.get(0).role());
        assertEquals("hi", history.get(0).content());
        assertEquals(Role.ASSISTANT, history.get(1).role());
        assertEquals("ok", history.get(1).content());
    }

    @Test
    void payloadCarriesCurrentUserMessageExactlyOnce() {
        var service = new StubAIService();
        var options = ChatOptions.newBuilder().withMemory().build();

        service.enqueueSuccess("first reply");
        service.chat("first question", options);

        service.enqueueSuccess("second reply");
        service.chat("second question", options);

        var payload = service.getLastPayload().toString();
        assertEquals(1, countOccurrences(payload, "second question")); // as the current message, not also as history
        assertEquals(1, countOccurrences(payload, "first question")); // carried once, as history
    }

    @Test
    void uploadedFileAnchorsToCurrentUserMessage() throws IOException {
        var attachment = Files.createTempFile("attachment", ".pdf");
        Files.write(attachment, "%PDF-1.4".getBytes());

        var service = new StubAIService();
        service.enqueueSuccess("ok");

        var options = ChatOptions.newBuilder().withMemory().build();
        var input = ChatInput.newBuilder().message("summarize this").attach(attachment).build();

        assertEquals("ok", service.chat(input, options));

        var userMessage = options.getHistory().get(0);
        assertEquals(Role.USER, userMessage.role());
        assertEquals("summarize this", userMessage.content());
        assertEquals(1, userMessage.uploadedFiles().size()); // upload() anchors the file id to the user message of the current turn
        assertEquals("file-123", userMessage.uploadedFiles().get(0).id());
    }

    @Test
    void retriedRequestRecordsUserMessageExactlyOnce() {
        var service = new StubAIService();
        service.enqueueFailure();
        service.enqueueFailure();
        service.enqueueSuccess("ok");

        var options = ChatOptions.newBuilder().withMemory().build();

        assertEquals("ok", resilient(service).chat("retried question", options));

        var history = options.getHistory();
        assertEquals(2, history.size()); // one turn, not one user message per attempt
        assertEquals(Role.USER, history.get(0).role());
        assertEquals("retried question", history.get(0).content());
        assertEquals(Role.ASSISTANT, history.get(1).role());
        assertEquals(3, service.getPayloads().size()); // three attempts

        for (var payload : service.getPayloads()) {
            assertEquals(1, countOccurrences(payload.toString(), "retried question")); // never resent as both history and current message
        }
    }

    @Test
    void retriedRequestAnchorsUploadedFileExactlyOnce() throws IOException {
        var attachment = Files.createTempFile("attachment", ".pdf");
        Files.write(attachment, "%PDF-1.4".getBytes());

        var service = new StubAIService();
        service.enqueueFailure();
        service.enqueueSuccess("ok");

        var options = ChatOptions.newBuilder().withMemory().build();
        var input = ChatInput.newBuilder().message("summarize this").attach(attachment).build();

        assertEquals("ok", resilient(service).chat(input, options));

        var history = options.getHistory();
        assertEquals(2, history.size()); // the failed attempt's user message was replaced, not appended

        var userMessage = history.get(0);
        assertEquals(1, userMessage.uploadedFiles().size()); // the failed attempt's file reference was discarded, not accumulated
        assertEquals("file-123", userMessage.uploadedFiles().get(0).id());
    }

    @Test
    void seededTrailingUserMessageWithDifferentContentIsNotReplaced() {
        var service = new StubAIService();
        service.enqueueSuccess("ok");

        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "seeded question");

        assertEquals("ok", service.chat("new question", options));

        var history = options.getHistory();
        assertEquals(3, history.size());
        assertEquals("seeded question", history.get(0).content());
        assertEquals("new question", history.get(1).content());
        assertEquals(Role.ASSISTANT, history.get(2).role());

        var payload = service.getLastPayload().toString();
        assertEquals(1, countOccurrences(payload, "seeded question")); // carried as history, not dropped as if it were the current message
        assertEquals(1, countOccurrences(payload, "new question"));
    }

    private static RetryingAIService resilient(StubAIService service) {
        return RetryingAIService.newBuilder(service).initialBackoff(Duration.ZERO).maxBackoff(Duration.ZERO).jitter(false).build();
    }

    private static int countOccurrences(String haystack, String needle) {
        var count = 0;

        for (var index = haystack.indexOf(needle); index >= 0; index = haystack.indexOf(needle, index + needle.length())) {
            count++;
        }

        return count;
    }

    /**
     * A {@link BaseAIService} whose HTTP round trips are replaced by canned responses, recording every payload it was asked to send.
     */
    private static final class StubAIService extends OpenAIService {

        private static final long serialVersionUID = 1L;

        private final transient Queue<CompletableFuture<String>> responses = new ArrayDeque<>();
        private final transient List<JsonObject> payloads = new ArrayList<>();

        private StubAIService() {
            super(AIConfig.of(AIProvider.OPENAI, "test-api-key"));
        }

        private void enqueueSuccess(String response) {
            responses.add(CompletableFuture.completedFuture(response));
        }

        private void enqueueFailure() {
            responses.add(CompletableFuture.failedFuture(new AIServiceUnavailableException(ENDPOINT, "unavailable")));
        }

        private List<JsonObject> getPayloads() {
            return payloads;
        }

        private JsonObject getLastPayload() {
            return payloads.get(payloads.size() - 1);
        }

        @Override
        CompletableFuture<JsonObject> asyncUpload(String path, Attachment attachment) {
            return CompletableFuture.completedFuture(Json.createObjectBuilder().add("id", "file-123").build());
        }

        @Override
        protected UploadedFileJsonStructure getUploadedFileJsonStructure() {
            return null; // Keeps upload() from spawning its background stale-file cleanup, which would hit the real files endpoint.
        }

        @Override
        protected CompletableFuture<String> asyncPostAndParseChatResponse(String path, JsonObject payload, ChatOptions options) {
            payloads.add(payload);
            return responses.remove();
        }

    }

}
