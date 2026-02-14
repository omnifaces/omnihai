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

import static java.net.http.HttpClient.newBuilder;
import static java.net.http.HttpResponse.BodyHandlers.ofInputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.delayedExecutor;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.function.Function.identity;
import static java.util.logging.Level.FINER;
import static java.util.stream.Stream.iterate;
import static org.omnifaces.ai.exception.AIHttpException.fromStatusCode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import jakarta.json.JsonObject;

import org.omnifaces.ai.OmniHai;
import org.omnifaces.ai.exception.AIBadRequestException;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.exception.AIHttpException;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.Sse.Event;
import org.omnifaces.ai.model.Sse.Event.Type;

/**
 * HTTP client utility for {@link BaseAIService} implementations.
 * <p>
 * This utility wraps {@link java.net.http.HttpClient} to provide a simple API interface for all {@link BaseAIService} implementations.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
final class AIHttpClient {

    private static final Logger logger = Logger.getLogger(AIHttpClient.class.getPackageName());
    private static final AtomicInteger requestCounter = new AtomicInteger();

    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String DELETE = "DELETE";

    private static final String APPLICATION_JSON = "application/json";
    private static final String EVENT_STREAM = "text/event-stream";
    private static final String MULTIPART_FORM_DATA = "multipart/form-data";

    /** Default max retries: {@value} */
    public static final int MAX_RETRIES = 3;
    /** Initial retry backoff time: {@value}ms (increases exponentially on every retry) */
    public static final long INITIAL_BACKOFF_MS = 1000;

    private final HttpClient client;
    private final Duration requestTimeout;
    private final String userAgent;
    private final String multipartBoundaryPrefix;
    final String uploadedFileNamePrefix;

    private AIHttpClient(HttpClient client, Duration requestTimeout) {
        this.client = client;
        this.requestTimeout = requestTimeout;
        this.userAgent = OmniHai.userAgent();
        this.multipartBoundaryPrefix = OmniHai.name() + "Boundary";
        uploadedFileNamePrefix = OmniHai.name() + ".";
    }

    /**
     * Creates a new HTTP client instance with custom timeouts.
     *
     * @param connectTimeout The connection timeout
     * @param requestTimeout The request timeout
     * @return A new HTTP client instance
     */
    public static AIHttpClient newInstance(Duration connectTimeout, Duration requestTimeout) {
        return new AIHttpClient(newBuilder().connectTimeout(connectTimeout).build(), requestTimeout);
    }

    /**
     * Sends a GET request for the specified {@link BaseAIService}.
     * Will retry at most {@value #MAX_RETRIES} times in case of a connection error with exponentially incremental backoff of {@value #INITIAL_BACKOFF_MS}ms.
     *
     * @param service The {@link BaseAIService} to extract URI and headers from.
     * @param path the API path
     * @return The response body as a string
     * @throws AIHttpException if the request fails
     * @since 1.1
     */
    public CompletableFuture<String> get(BaseAIService service, String path) throws AIHttpException {
        return sendWithRetryAsync(service, path, GET, newRequest(service, path, GET, null, APPLICATION_JSON, BodyPublishers.noBody()));
    }

    /**
     * Sends a POST request for the specified {@link BaseAIService} with JSON payload and returns response as string.
     * Will retry at most {@value #MAX_RETRIES} times in case of a connection error with exponentially incremental backoff of {@value #INITIAL_BACKOFF_MS}ms.
     *
     * @param service The {@link BaseAIService} to extract URI and headers from.
     * @param path the API path
     * @param payload The JSON payload
     * @return The response body as a string
     * @throws AIHttpException if the request fails
     */
    public CompletableFuture<String> post(BaseAIService service, String path, JsonObject payload) throws AIHttpException {
        return sendWithRetryAsync(service, path, payload, newJsonRequest(service, path, payload, APPLICATION_JSON));
    }

    /**
     * Sends a POST request for the specified {@link BaseAIService} with raw payload and returns response as string.
     * Will retry at most {@value #MAX_RETRIES} times in case of a connection error with exponentially incremental backoff of {@value #INITIAL_BACKOFF_MS}ms.
     *
     * @param service The {@link BaseAIService} to extract URI and headers from.
     * @param path the API path
     * @param attachment The raw payload
     * @return The response body as a string
     * @throws AIHttpException if the request fails
     * @since 1.1
     */
    public CompletableFuture<String> post(BaseAIService service, String path, Attachment attachment) throws AIHttpException {
        return sendWithRetryAsync(service, path, attachment, newRequest(service, path, POST, attachment.mimeType().value(), APPLICATION_JSON, BodyPublishers.ofByteArray(attachment.content())));
    }

    /**
     * Sends a POST request for the specified {@link BaseAIService} with JSON payload and returns response as stream.
     * Will retry at most {@value #MAX_RETRIES} times in case of a connection error with exponentially incremental backoff of {@value #INITIAL_BACKOFF_MS}ms.
     *
     * @param service The {@link BaseAIService} to extract URI and headers from.
     * @param path the API path
     * @param payload The JSON payload
     * @return The response as a {@link HttpResponse} with an {@link InputStream} body.
     * @throws AIHttpException if the request fails
     * @since 1.2
     */
    public CompletableFuture<HttpResponse<InputStream>> stream(BaseAIService service, String path, JsonObject payload) throws AIHttpException {
        return sendWithRetryAsync(service, path, payload, newJsonRequest(service, path, payload, APPLICATION_JSON), identity());
    }

    /**
     * Sends a STREAM (SSE) request for the specified {@link BaseAIService}.
     * Will retry at most {@value #MAX_RETRIES} times in case of a connection error with exponentially incremental backoff of {@value #INITIAL_BACKOFF_MS}ms.
     *
     * @param service The {@link BaseAIService} to extract URI and headers from.
     * @param path the API path
     * @param payload The request payload
     * @param eventProcessor The stream event processor.
     * @return A future that completes when stream ends or fails.
     * @throws AIHttpException if the request fails
     */
    public CompletableFuture<Void> stream(BaseAIService service, String path, JsonObject payload, Predicate<Event> eventProcessor) throws AIHttpException {
        final int requestId = logRequest(service, path, payload);
        return sendWithRetryAsync(requestId, newJsonRequest(service, path, payload, EVENT_STREAM), r -> { consumeEventStream(requestId, r, eventProcessor); return null; }, 0);
    }

    /**
     * Sends a UPLOAD (multipart/form-data) request for the specified {@link BaseAIService}.
     * Will retry at most {@value #MAX_RETRIES} times in case of a connection error with exponentially incremental backoff of {@value #INITIAL_BACKOFF_MS}ms.
     *
     * @param service The {@link BaseAIService} to extract URI and headers from.
     * @param path the API path
     * @param attachment The file attachment to upload
     * @return The response body as a string
     * @throws AIHttpException if the request fails
     */
    public CompletableFuture<String> upload(BaseAIService service, String path, Attachment attachment) throws AIHttpException {
        return sendWithRetryAsync(service, path, attachment, newUploadRequest(service, path, attachment, APPLICATION_JSON));
    }

    /**
     * Sends a DELETE request for the specified {@link BaseAIService}.
     * Will retry at most {@value #MAX_RETRIES} times in case of a connection error with exponentially incremental backoff of {@value #INITIAL_BACKOFF_MS}ms.
     *
     * @param service The {@link BaseAIService} to extract URI and headers from.
     * @param path the API path
     * @return The response body as a string
     * @throws AIHttpException if the request fails
     * @since 1.1
     */
    public CompletableFuture<String> delete(BaseAIService service, String path) throws AIHttpException {
        return sendWithRetryAsync(service, path, DELETE, newRequest(service, path, DELETE, null, APPLICATION_JSON, BodyPublishers.noBody()));
    }

    private CompletableFuture<String> sendWithRetryAsync(BaseAIService service, String path, Object payload, HttpRequest request) {
        return sendWithRetryAsync(service, path, payload, request, AIHttpClient::readBody);
    }

    private <R> CompletableFuture<R> sendWithRetryAsync(BaseAIService service, String path, Object payload, HttpRequest request, Function<HttpResponse<InputStream>, R> bodyExtractor) {
        final int requestId = logRequest(service, path, payload);
        return sendWithRetryAsync(requestId, request, bodyExtractor, 0).thenApply(response -> {
            logger.log(FINER, () -> "Response for #" + requestId + ": " + response);
            return response;
        });
    }

    private static int logRequest(BaseAIService service, String path, Object payload) {
        if (logger.isLoggable(FINER)) {
            int requestId = requestCounter.incrementAndGet();
            var uriWithoutQueryString = service.resolveURI(path).toString().split("\\?", 2)[0]; // Because query string may contain API key (e.g. Google AI).
            logger.log(FINER, () -> "Request #" + requestId + " to " + uriWithoutQueryString + ": " + payload.toString());
            return requestId;
        }
        else {
            return 0;
        }
    }

    private HttpRequest newJsonRequest(BaseAIService service, String path, JsonObject payload, String accept) {
        return newRequest(service, path, POST, APPLICATION_JSON, accept, BodyPublishers.ofString(payload.toString()));
    }

    private HttpRequest newUploadRequest(BaseAIService service, String path, Attachment attachment, String accept) {
        var multipart = new MultipartBodyPublisher(attachment);
        return newRequest(service, path, POST, MULTIPART_FORM_DATA + "; boundary=" + multipart.boundary, accept, multipart.body);
    }

    private HttpRequest newRequest(BaseAIService service, String path, String method, String contentType, String accept, BodyPublisher body) {
        var builder = HttpRequest.newBuilder(service.resolveURI(path)).timeout(requestTimeout).method(method, body);
        builder.header("User-Agent", userAgent);
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        builder.header("Accept", accept);
        builder.header("Accept-Encoding", "gzip");
        service.getRequestHeaders().forEach(builder::header);
        return builder.build();
    }

    private <R> CompletableFuture<R> sendWithRetryAsync(int requestId, HttpRequest request, Function<HttpResponse<InputStream>, R> bodyExtractor, int attempt) {
        return withRetry(() -> client.sendAsync(request, ofInputStream()).thenCompose(response -> handleResponse(requestId, request, response, bodyExtractor)), attempt);
    }

    private static <R, T> CompletableFuture<R> handleResponse(int requestId, HttpRequest request, HttpResponse<T> response, Function<HttpResponse<T>, R> bodyExtractor) {
        logger.log(FINER, () -> "Response headers for #" + requestId + ": " + response.headers().map());
        var statusCode = response.statusCode();

        if (statusCode >= AIBadRequestException.STATUS_CODE) {
            return failedFuture(fromStatusCode(request.uri(), statusCode, String.valueOf(bodyExtractor.apply(response))));
        }

        return completedFuture(bodyExtractor.apply(response));
    }

    private static CompletableFuture<Void> consumeEventStream(int requestId, HttpResponse<InputStream> response, Predicate<Event> eventProcessor) {
        var future = new CompletableFuture<Void>();
        runAsync(() -> processEvents(requestId, response, future, eventProcessor));
        return future;
    }

    private static void processEvents(int requestId, HttpResponse<InputStream> response, CompletableFuture<Void> future, Predicate<Event> eventProcessor) {
        try (var reader = new BufferedReader(new InputStreamReader(decompressIfNeeded(response), UTF_8))) {
            var dataBuffer = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {

                if (line.isBlank()) {
                    if (!processDataEvent(requestId, dataBuffer, eventProcessor)) {
                        break;
                    }

                    continue;
                }

                line = line.strip();

                if (line.startsWith(":")) {
                    continue;
                }

                var event = createEvent(line, requestId);

                if (event == null) {
                    continue;
                }

                if (event.type() == Type.DATA) {
                    if (!dataBuffer.isEmpty()) {
                        dataBuffer.append('\n');
                    }

                    dataBuffer.append(line.substring(5).strip());
                }
                else if (!processDataEvent(requestId, dataBuffer, eventProcessor) || !eventProcessor.test(event)) {
                    break;
                }
            }

            processDataEvent(requestId, dataBuffer, eventProcessor);
            future.complete(null);
        }
        catch (Exception e) {
            future.completeExceptionally(e);
        }
    }

    private static Event createEvent(String line, int requestId) {
        Event event = null;

        if (line.startsWith("id:")) {
            event = new Event(Type.ID, line.substring(3).strip());
        }
        if (line.startsWith("event:")) {
            event = new Event(Type.EVENT, line.substring(6).strip());
        }
        if (line.startsWith("data:")) {
            event = new Event(Type.DATA, line.substring(5).strip());
        }

        if (event == null) {
            logger.log(FINER, () -> "Ignoring unknown SSE line for #" + requestId + ": " + line);
        }
        else if (event.type() != Type.DATA) { // Final data event is logged in processDataEvent.
            logEvent(requestId, event);
        }

        return event;
    }

    private static boolean processDataEvent(int requestId, StringBuilder dataBuffer, Predicate<Event> eventProcessor) {
        if (!dataBuffer.isEmpty()) {
            var event = new Event(Type.DATA, dataBuffer.toString());
            dataBuffer.setLength(0);
            logEvent(requestId, event);

            if (!eventProcessor.test(event)) {
                return false;
            }
        }

        return true;
    }

    private static void logEvent(int requestId, Event event) {
        if (logger.isLoggable(FINER)) {
            final var eventString = event.toString();
            logger.log(FINER, () -> "SSE event for #" + requestId + ": " + eventString);
        }
    }

    private static InputStream decompressIfNeeded(HttpResponse<InputStream> response) throws IOException {
        var encoding = response.headers().firstValue("Content-Encoding").orElse("");
        return "gzip".equalsIgnoreCase(encoding) ? new GZIPInputStream(response.body()) : response.body();
    }

    private static String readBody(HttpResponse<InputStream> response) {
        try (var is = decompressIfNeeded(response)) {
            return new String(is.readAllBytes(), UTF_8);
        }
        catch (IOException e) {
            throw new AIException("Cannot read response body", e);
        }
    }

    private static <R> CompletableFuture<R> withRetry(Supplier<CompletableFuture<R>> action, int attempt) {
        return action.get().exceptionallyCompose(throwable -> handleFailureWithRetry(action, attempt, throwable));
    }

    private static <R> CompletableFuture<R> handleFailureWithRetry(Supplier<CompletableFuture<R>> action, int attempt, Throwable throwable) {
        var cause = throwable instanceof CompletionException ce ? ce.getCause() : throwable;

        if (cause instanceof AIException) {
            return failedFuture(cause);
        }

        if (attempt >= MAX_RETRIES - 1 || !isRetryable(cause)) {
            return failedFuture(new AIHttpException("Request failed (" + attempt + " retries)", cause));
        }

        return supplyAsync(() -> withRetry(action, attempt + 1), delayedExecutor(INITIAL_BACKOFF_MS * (1L << attempt), MILLISECONDS)).thenCompose(identity());
    }

    /**
     * Determines whether a failed request should be retried based on the exception.
     * <p>
     * Retryable errors are transient connection issues indicated by an {@link IOException} which is either an instance
     * of {@link ConnectException} or has a message containing "timed", "terminated", "reset", "refused", or "goaway"
     * anywhere in the cause chain.
     *
     * @param throwable The exception to check.
     * @return {@code true} if the error is transient and the request should be retried.
     */
    static boolean isRetryable(Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        return iterate(throwable, Objects::nonNull, Throwable::getCause)
            .filter(IOException.class::isInstance)
            .findFirst()
            .map(ioException -> ioException instanceof java.net.ConnectException
                || iterate(ioException, Objects::nonNull, Throwable::getCause)
                      .map(Throwable::getMessage)
                      .filter(Objects::nonNull)
                      .map(String::toLowerCase)
                      .anyMatch(msg -> msg.contains("timed")
                              || msg.contains("terminated")
                              || msg.contains("reset")
                              || msg.contains("refused")
                              || msg.contains("goaway"))
            )
            .orElse(false);
    }

    private class MultipartBodyPublisher {

        private final BodyPublisher body;
        private final String boundary;

        private MultipartBodyPublisher(Attachment attachment) {
            this.boundary = multipartBoundaryPrefix + UUID.randomUUID();

            try {
                var textPartBuilder = new StringBuilder();

                for (var entry : attachment.metadata().entrySet()) {
                    appendTextPart(textPartBuilder, entry.getKey(), entry.getValue());
                }

                prependFilePart(textPartBuilder, "file", uploadedFileNamePrefix + attachment.fileName(), attachment.mimeType().value());

                this.body = BodyPublishers.concat(
                    BodyPublishers.ofString(textPartBuilder.toString(), UTF_8),
                    (attachment.source() != null) ? BodyPublishers.ofFile(attachment.source()) : BodyPublishers.ofByteArray(attachment.content()),
                    BodyPublishers.ofString(closeFilePart(), UTF_8)
                );
            }
            catch (IOException e) {
                throw new AIException("Cannot prepare multipart/form-data request for " + attachment, e);
            }
        }

        private void appendTextPart(StringBuilder sb, String name, String value) {
            appendLine(sb, "--" + boundary);
            appendLine(sb, "Content-Disposition: form-data; name=\"" + name + "\"\r\n");
            appendLine(sb, value);
        }

        private void prependFilePart(StringBuilder sb, String name, String filename, String contentType) {
            appendLine(sb, "--" + boundary);
            appendLine(sb, "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"");
            appendLine(sb, "Content-Type: " + contentType + "\r\n");
        }

        private String closeFilePart() {
            var sb = new StringBuilder();
            appendLine(sb, "");
            appendLine(sb, "--" + boundary + "--");
            return sb.toString();
        }

        private static void appendLine(StringBuilder sb, String line) {
            sb.append(line + "\r\n");
        }
    }
}
