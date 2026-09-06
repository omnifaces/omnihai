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
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.ALL;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.omnifaces.ai.AIProvider.META;
import static org.omnifaces.ai.AIProvider.OPENAI;
import static org.omnifaces.ai.service.BaseAIService.HTTP_CLIENT;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.DeliberateFailures;
import org.omnifaces.ai.exception.AIHttpException;
import org.omnifaces.ai.exception.AIRateLimitExceededException;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.Sse.Event;
import org.omnifaces.ai.model.Sse.Event.Type;

class AIHttpClientTest {

    private static final int BODY_TIMEOUT_SECONDS = 5;

    // =================================================================================================================
    // isRetryable - null / non-IOException
    // =================================================================================================================

    @Test
    void isRetryable_null_returnsFalse() {
        assertFalse(AIHttpClient.isRetryable(null));
    }

    @Test
    void isRetryable_nonIOException_returnsFalse() {
        assertFalse(AIHttpClient.isRetryable(new RuntimeException("something failed")));
    }

    @Test
    void isRetryable_ioExceptionWithoutRetryableMessage_returnsFalse() {
        assertFalse(AIHttpClient.isRetryable(new IOException("something else")));
    }

    // =================================================================================================================
    // isRetryable - ConnectException (always retryable)
    // =================================================================================================================

    @Test
    void isRetryable_connectException_returnsTrue() {
        assertTrue(AIHttpClient.isRetryable(new ConnectException("Connection refused")));
    }

    @Test
    void isRetryable_connectExceptionWithUnexpectedMessage_returnsTrue() {
        assertTrue(AIHttpClient.isRetryable(new ConnectException("some unexpected message")));
    }

    @Test
    void isRetryable_connectExceptionWrappedInRuntimeException_returnsTrue() {
        assertTrue(AIHttpClient.isRetryable(new RuntimeException(new ConnectException("Connection refused"))));
    }

    // =================================================================================================================
    // isRetryable - message-based matching
    // =================================================================================================================

    @Test
    void isRetryable_timedOut_returnsTrue() {
        assertTrue(AIHttpClient.isRetryable(new IOException("request timed out")));
    }

    @Test
    void isRetryable_terminated_returnsTrue() {
        assertTrue(AIHttpClient.isRetryable(new IOException("connection was terminated")));
    }

    @Test
    void isRetryable_reset_returnsTrue() {
        assertTrue(AIHttpClient.isRetryable(new IOException("connection reset by peer")));
    }

    @Test
    void isRetryable_refused_returnsTrue() {
        assertTrue(AIHttpClient.isRetryable(new IOException("connection refused")));
    }

    @Test
    void isRetryable_goaway_returnsTrue() {
        assertTrue(AIHttpClient.isRetryable(new IOException("received goaway")));
    }

    @Test
    void isRetryable_messageMatchIsCaseInsensitive() {
        assertTrue(AIHttpClient.isRetryable(new IOException("Connection RESET")));
    }

    // =================================================================================================================
    // isRetryable - nested cause chain
    // =================================================================================================================

    @Test
    void isRetryable_retryableMessageInNestedCause_returnsTrue() {
        assertTrue(AIHttpClient.isRetryable(new IOException("wrapper", new IOException("connection reset"))));
    }

    @Test
    void isRetryable_ioExceptionWrappedInRuntimeException_returnsTrue() {
        assertTrue(AIHttpClient.isRetryable(new RuntimeException(new IOException("request timed out"))));
    }

    @Test
    void isRetryable_nonRetryableMessageInAllCauses_returnsFalse() {
        assertFalse(AIHttpClient.isRetryable(new IOException("outer", new IOException("inner"))));
    }

    // =================================================================================================================
    // newRequest - authorization headers
    // =================================================================================================================

    @Test
    void newRequest_onEndpointHost_carriesTheAuthorization() {
        var request = newRequest("videos/video_123/content");

        assertEquals("https://api.openai.com/v1/videos/video_123/content", request.uri().toString());
        assertTrue(request.headers().firstValue("Authorization").isPresent(), "a request to the AI provider's own endpoint needs the API key");
    }

    @Test
    void newRequest_onForeignHost_withholdsTheAuthorization() {
        var request = newRequest("https://vidgen.x.ai/abc/video.mp4");

        assertEquals("https://vidgen.x.ai/abc/video.mp4", request.uri().toString());
        assertFalse(request.headers().firstValue("Authorization").isPresent(), "a pre-signed URI hosted elsewhere must not receive the API key");
    }

    private static HttpRequest newRequest(String path) {
        var service = (BaseAIService) AIConfig.of(OPENAI, "test-api-key").createService();
        return HTTP_CLIENT.newRequest(service, path, "GET", null, "*/*", BodyPublishers.noBody());
    }

    // =================================================================================================================
    // decompressDownloadIfNeeded - redirects and errors
    // =================================================================================================================

    @Test
    void download_ofARedirect_throwsAndClosesTheBody() {
        var body = new ClosingStream(new byte[0]);
        var response = newResponse(302, body);

        var exception = assertThrows(AIHttpException.class, () -> AIHttpClient.decompressDownloadIfNeeded(response));

        assertEquals(302, exception.getStatusCode(), "a redirect is not content, whatever its body says");
        assertTrue(body.closed, "the response body of a redirect may not be left open");
    }

    /** Stream which records that it was closed, as a Mockito spy on one leaves its buffer uninitialized. */
    private static final class ClosingStream extends ByteArrayInputStream {

        private boolean closed;

        private ClosingStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

    }

    @Test
    void download_ofASuccess_handsBackTheBody() {
        var body = new ByteArrayInputStream(new byte[] { 'x' });

        assertSame(body, AIHttpClient.decompressDownloadIfNeeded(newResponse(200, body)));
    }

    @Test
    void client_neverFollowsRedirects() {
        assertEquals(
            Redirect.NEVER, BaseAIService.HTTP_CLIENT.client.followRedirects(),
            "the JDK carries the authorization header across hosts when it follows a redirect"
        );
    }

    // =================================================================================================================
    // Multipart upload
    // =================================================================================================================

    @Test
    void newUploadRequest_namesTheFilePartAsTheAIProviderExpects() {
        var request = newUploadRequest("audio");

        assertTrue(readBody(request).contains("name=\"audio\"; filename="), "an AI provider expecting another name than the default must get it");
    }

    @Test
    void newUploadRequest_byDefaultNamesTheFilePartFile() {
        var request = newUploadRequest(AIHttpClient.DEFAULT_FILE_PART_NAME);

        assertTrue(readBody(request).contains("name=\"file\"; filename="));
    }

    @Test
    void newUploadRequest_carriesTheMetadataAsPartsOfTheirOwn() {
        var request = newUploadRequest("audio");

        assertTrue(readBody(request).contains("name=\"request\""), "metadata may not end up as a parameter of the file part");
    }

    private static HttpRequest newUploadRequest(String filePartName) {
        var service = (BaseAIService) AIConfig.of(META, "test-api-key").createService();
        var attachment = new Attachment(new byte[] { 'x' }, MimeType.of("audio/wav"), "audio.wav", Map.of("request", "{}"));
        return HTTP_CLIENT.newUploadRequest(service, "asr/transcribe", attachment, filePartName, "application/json");
    }

    private static String readBody(HttpRequest request) {
        var body = new StringBuilder();
        var completed = new CompletableFuture<String>();

        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                body.append(UTF_8.decode(item));
            }

            @Override
            public void onError(Throwable throwable) {
                completed.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                completed.complete(body.toString());
            }

        });

        return completed.orTimeout(BODY_TIMEOUT_SECONDS, SECONDS).join();
    }

    // =================================================================================================================
    // The server-sent event stream
    // =================================================================================================================

    /**
     * A named event is dispatched at once, while a data event accumulates until a blank line closes it, as one event may span several data lines.
     */
    @Test
    void processEvents_dispatchesEachEventInOrder() {
        var events = collect("""
            id: msg-1

            event: delta

            data: first

            data: second

            """);

        assertEquals(List.of("ID=msg-1", "EVENT=delta", "DATA=first", "DATA=second"), events);
    }

    @Test
    void processEvents_dataSpanningSeveralLines_arrivesAsOneEvent() {
        assertEquals(List.of("DATA=first\nsecond"), collect("data: first\ndata: second\n\n"));
    }

    /**
     * A named event closes whatever data was pending, so the two never arrive in the wrong order.
     */
    @Test
    void processEvents_dataFollowedByANamedEvent_flushesTheDataFirst() {
        assertEquals(List.of("DATA=pending", "EVENT=done"), collect("data: pending\nevent: done\n\n"));
    }

    /**
     * A stream which ends without its closing blank line still yields what it had buffered.
     */
    @Test
    void processEvents_streamEndingWithoutABlankLine_stillYieldsTheLastEvent() {
        assertEquals(List.of("DATA=last"), collect("data: last"));
    }

    /**
     * A line opening with a colon is a comment the protocol allows, and a line naming no field at all is not ours to interpret.
     */
    @Test
    void processEvents_commentsAndUnknownLines_areIgnored() {
        assertEquals(List.of("DATA=kept"), collect(": keep-alive\nunknown line\ndata: kept\n\n"));
    }

    @Test
    void processEvents_blankStream_yieldsNothing() {
        assertTrue(collect("").isEmpty());
    }

    /**
     * A processor which has seen enough stops the reading rather than draining the rest of the stream.
     */
    @Test
    void processEvents_processorWhichStops_endsTheStream() {
        var events = new ArrayList<String>();
        var future = new CompletableFuture<Void>();

        AIHttpClient.processEvents(1, newResponse(200, stream("data: first\n\ndata: second\n\n")), future, event -> {
            events.add(event.value());
            return false;
        });

        assertEquals(List.of("first"), events);
        assertTrue(future.isDone());
    }

    @Test
    void processEvents_unreadableStream_completesTheFutureExceptionally() {
        var future = new CompletableFuture<Void>();
        var body = new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("connection reset");
            }

        };

        AIHttpClient.processEvents(1, newResponse(200, body), future, event -> true);

        assertTrue(future.isCompletedExceptionally());
    }

    private static List<String> collect(String stream) {
        var events = new ArrayList<String>();
        var future = new CompletableFuture<Void>();

        AIHttpClient.processEvents(1, newResponse(200, stream(stream)), future, event -> {
            events.add(event.type() + "=" + event.value());
            return true;
        });

        assertTrue(future.isDone());
        return events;
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(UTF_8));
    }

    // =================================================================================================================
    // Turning a response into a result
    // =================================================================================================================

    /**
     * A status the provider answers with an error is turned into the exception naming that error, so the caller never sees a body it cannot use.
     */
    @Test
    void handleResponse_errorStatus_answersTheMatchingException() {
        var response = newResponse(429, stream("rate limited"));
        var future = AIHttpClient.handleResponse(1, newRequest(), response, ignored -> completedFuture("unreachable"));

        assertTrue(future.isCompletedExceptionally());
        assertInstanceOf(AIRateLimitExceededException.class, assertThrows(CompletionException.class, future::join).getCause());
    }

    @Test
    void handleResponse_successStatus_isHandedToTheHandler() {
        var response = newResponse(200, stream("body"));

        assertEquals("handled", AIHttpClient.handleResponse(1, newRequest(), response, ignored -> completedFuture("handled")).join());
    }

    // =================================================================================================================
    // Retrying a failed request
    // =================================================================================================================

    /**
     * A failure the provider itself stated is final, so it is answered as it is rather than tried again.
     */
    @Test
    void handleFailureWithRetry_failureStatedByTheProvider_isNotRetried() {
        var attempts = new AtomicInteger();
        var cause = new AIRateLimitExceededException(URI.create("https://example.org"), "rate limited");

        var future = AIHttpClient.handleFailureWithRetry(1, () -> {
            attempts.incrementAndGet();
            return completedFuture("retried");
        }, 0, new CompletionException(cause));

        assertTrue(future.isCompletedExceptionally());
        assertEquals(0, attempts.get());
    }

    @Test
    void handleFailureWithRetry_afterTheLastAttempt_saysHowOftenItTried() {
        var future = AIHttpClient.handleFailureWithRetry(
            1, () -> completedFuture("retried"), AIHttpClient.MAX_RETRIES, new IOException("connection reset")
        );

        var exception = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(AIHttpException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("retries"), exception.getCause().getMessage());
    }

    /**
     * A failure which is nobody's fault is tried again, so a connection which dropped once does not fail the call.
     */
    @Test
    void handleFailureWithRetry_failureWorthRetrying_triesAgain() {
        var attempts = new AtomicInteger();

        var result = AIHttpClient.handleFailureWithRetry(1, () -> {
            attempts.incrementAndGet();
            return completedFuture("retried");
        }, 0, new IOException("connection reset")).join();

        assertEquals("retried", result);
        assertEquals(1, attempts.get());
    }

    @Test
    void handleFailureWithRetry_failureWhichIsNotWorthRetrying_isAnsweredAsItIs() {
        var future = AIHttpClient.handleFailureWithRetry(1, () -> completedFuture("retried"), 0, new IllegalStateException("broken"));

        assertTrue(future.isCompletedExceptionally());
    }

    /**
     * A retry costs the caller another full request timeout, so it names the request and the delay rather than passing unseen.
     */
    @Test
    @ResourceLock(DeliberateFailures.LOGGING_STATE)
    void handleFailureWithRetry_failureWorthRetrying_announcesTheNextAttempt() {
        var records = new ArrayList<LogRecord>();

        whileLoggingAt(
            WARNING, records, () -> AIHttpClient.handleFailureWithRetry(42, () -> completedFuture("retried"), 0, new IOException("connection reset"))
        );

        assertEquals(1, records.size());
        assertEquals(WARNING, records.get(0).getLevel());
        var message = records.get(0).getMessage();
        assertTrue(message.contains("#42"), message);
        assertTrue(message.contains("connection reset"), message);
        assertTrue(message.contains(AIHttpClient.INITIAL_BACKOFF_MS + "ms"), message);
    }

    // =================================================================================================================
    // Logging a request
    // =================================================================================================================

    /**
     * The URI is logged without its query string, as a provider may carry the API key there.
     */
    @Test
    @ResourceLock(DeliberateFailures.LOGGING_STATE)
    void logRequest_whenFinerIsOn_logsTheUriWithoutItsQueryString() {
        var service = (BaseAIService) AIConfig.of(OPENAI, "test-api-key").createService();
        var records = new ArrayList<LogRecord>();

        var requestId = whileLoggingAt(FINER, records, () -> AIHttpClient.logRequest(service, "responses?key=secret", "the-payload"));

        assertTrue(requestId > 0);
        assertEquals(1, records.size());
        var message = records.get(0).getMessage();
        assertTrue(message.contains("Request #" + requestId), message);
        assertTrue(message.contains("/responses"), message);
        assertTrue(message.contains("the-payload"), message);
        assertFalse(message.contains("secret"), message);
    }

    /**
     * Building the message costs more than it is worth when nobody reads it, so a request nobody logs gets no identity either.
     */
    @Test
    @ResourceLock(DeliberateFailures.LOGGING_STATE)
    void logRequest_whenFinerIsOff_logsNothingAndHasNoIdentity() {
        var service = (BaseAIService) AIConfig.of(OPENAI, "test-api-key").createService();
        var records = new ArrayList<LogRecord>();

        var requestId = whileLoggingAt(INFO, records, () -> AIHttpClient.logRequest(service, "responses", "the-payload"));

        assertEquals(0, requestId);
        assertTrue(records.isEmpty());
    }

    private static <R> R whileLoggingAt(Level level, List<LogRecord> records, Supplier<R> action) {
        var logger = Logger.getLogger(AIHttpClient.class.getPackageName());
        var originalLevel = logger.getLevel();
        var handler = new Handler() {

            @Override
            public void publish(LogRecord logRecord) {
                records.add(logRecord);
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
        logger.setLevel(level);

        try {
            return action.get();
        }
        finally {
            logger.setLevel(originalLevel);
            logger.removeHandler(handler);
        }
    }

    /**
     * A stream which is being followed at FINER states every event it dispatched and every line it could make nothing of, so that a provider sending an
     * unexpected shape can be traced from the log alone.
     */
    @Test
    @ResourceLock(DeliberateFailures.LOGGING_STATE)
    void processEvents_whenFinerIsOn_logsEveryEventAndEveryLineItIgnored() {
        var records = new ArrayList<LogRecord>();
        var future = new CompletableFuture<Void>();
        var response = newResponse(200, stream("what: is this\ndata: first\n\n"));

        whileLoggingAt(FINER, records, () -> {
            AIHttpClient.processEvents(1, response, future, event -> true);
            return null;
        });

        var messages = records.stream().map(LogRecord::getMessage).toList();
        assertTrue(messages.stream().anyMatch(message -> message.contains("Ignoring unknown SSE line")), messages.toString());
        assertTrue(messages.stream().anyMatch(message -> message.contains("SSE event for #1")), messages.toString());
    }

    /**
     * A processor which stops on a buffered data event stops the stream there, rather than being asked about the event which flushed it.
     */
    @Test
    void processEvents_processorWhichStopsOnBufferedData_isNotAskedAboutTheEventWhichFlushedIt() {
        var events = new ArrayList<Event>();
        var future = new CompletableFuture<Void>();

        AIHttpClient.processEvents(1, newResponse(200, stream("data: first\nevent: done\n\n")), future, event -> {
            events.add(event);
            return false;
        });

        assertEquals(1, events.size(), "the event which flushed the data is never dispatched");
        assertEquals("first", events.get(0).value());
    }

    /**
     * The event which flushed a buffered data event is dispatched after it, and stops the stream on its own verdict.
     */
    @Test
    void processEvents_processorWhichStopsOnTheEventFlushingTheData_dispatchesTheDataFirst() {
        var events = new ArrayList<Event>();
        var future = new CompletableFuture<Void>();

        AIHttpClient.processEvents(1, newResponse(200, stream("data: first\nevent: done\n\ndata: second\n\n")), future, event -> {
            events.add(event);
            return event.type() != Type.EVENT;
        });

        assertEquals(2, events.size());
        assertEquals("done", events.get(1).value());
    }

    private static HttpRequest newRequest() {
        return HttpRequest.newBuilder(URI.create("https://example.org/v1/chat")).GET().build();
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> newResponse(int statusCode, InputStream body) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
        when(response.uri()).thenReturn(URI.create("https://vidgen.x.ai/abc/video.mp4"));
        return response;
    }

}
