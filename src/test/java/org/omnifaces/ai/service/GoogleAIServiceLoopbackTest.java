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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIProvider.GOOGLE;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.service.LoopbackHttpServer.Answer;

/**
 * Google AI processes an uploaded file asynchronously and rejects a chat request referencing one which is not active yet, so an upload is followed by polling
 * until it is. The provider is an HTTP server on the loopback interface.
 */
class GoogleAIServiceLoopbackTest {

    private static final String UPLOADED = "{\"file\":{\"name\":\"files/abc123\",\"uri\":\"https://example.org/files/abc123\"}}";

    private LoopbackHttpServer server;
    private GoogleAIService service;

    @BeforeEach
    void startServer() {
        server = LoopbackHttpServer.start();
        service = new GoogleAIService(AIConfig.of(GOOGLE, "test-api-key").withModel("gemini-3.7-flash").withEndpoint(server.endpoint()));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    /**
     * A file which is active on the first poll blocks the caller no longer than that one request.
     */
    @Test
    void upload_fileWhichIsActiveAtOnce_answersItsIdWithoutWaiting() {
        server.answer(Answer.ofJson(UPLOADED), Answer.ofJson("{\"state\":\"ACTIVE\"}"));

        assertEquals("https://example.org/files/abc123", service.upload(newAttachment(), ChatOptions.DEFAULT));
        assertEquals("/v1/files/abc123", server.lastRequest().path());
    }

    /**
     * A file the provider states no state for is taken as ready, as a provider which does not process uploads states none.
     */
    @Test
    void upload_providerWhichStatesNoState_takesTheFileAsReady() {
        server.answer(Answer.ofJson(UPLOADED), Answer.ofJson("{}"));

        assertEquals("https://example.org/files/abc123", service.upload(newAttachment(), ChatOptions.DEFAULT));
    }

    @Test
    void upload_fileWhichFailsToProcess_reportsWhyItFailed() {
        server.answer(Answer.ofJson(UPLOADED), Answer.ofJson("{\"state\":\"FAILED\",\"error\":{\"message\":\"corrupt video\"}}"));

        var attachment = newAttachment();

        assertTrue(assertThrows(AIException.class, () -> service.upload(attachment, ChatOptions.DEFAULT)).getMessage().contains("corrupt video"));
    }

    /**
     * A file which is still being processed is polled again after a wait, and the wait grows per poll so that a long extraction costs few requests.
     */
    @Test
    void upload_fileWhichIsStillProcessing_isPolledUntilItIsActive() {
        server.answer(Answer.ofJson(UPLOADED), Answer.ofJson("{\"state\":\"PROCESSING\"}"), Answer.ofJson("{\"state\":\"ACTIVE\"}"));

        assertEquals("https://example.org/files/abc123", service.upload(newAttachment(), ChatOptions.DEFAULT));
        assertEquals(3, server.requestCount(), "the upload, the poll which found it processing, and the poll which found it active");
    }

    /**
     * A caller being interrupted while waiting on the file stops waiting, and the interrupt is passed on rather than swallowed.
     */
    @Test
    void upload_callerInterruptedWhileWaiting_givesUpAndKeepsTheInterrupt() {
        server.answer(Answer.ofJson(UPLOADED), Answer.ofJson("{\"state\":\"PROCESSING\"}"));
        var attachment = newAttachment();

        Thread.currentThread().interrupt();

        try {
            assertTrue(assertThrows(AIException.class, () -> service.upload(attachment, ChatOptions.DEFAULT)).getMessage().contains("Interrupted"));
            assertTrue(Thread.currentThread().isInterrupted());
        }
        finally {
            Thread.interrupted();
        }
    }

    /**
     * An id which is not a file path is one no poll can address, so it is answered as it is.
     */
    @Test
    void upload_idWhichIsNoFilePath_isNotPolled() {
        server.answer(Answer.ofJson("{\"file\":{\"name\":\"abc123\",\"uri\":\"https://example.org/upload/abc123\"}}"));

        assertEquals("https://example.org/upload/abc123", service.upload(newAttachment(), ChatOptions.DEFAULT));
        assertEquals(1, server.requestCount(), "there is no file path to poll");
    }

    private static Attachment newAttachment() {
        return new Attachment("the file".getBytes(UTF_8), MimeType.of("text/plain"), "a.txt");
    }

}
