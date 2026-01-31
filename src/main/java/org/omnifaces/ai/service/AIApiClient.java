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
import static java.net.http.HttpResponse.BodyHandlers.ofLines;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.delayedExecutor;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.function.Function.identity;
import static java.util.logging.Level.FINE;
import static java.util.stream.Collectors.joining;
import static org.omnifaces.ai.exception.AIHttpException.fromStatusCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

import jakarta.json.JsonObject;

import org.omnifaces.ai.exception.AIBadRequestException;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.exception.AIHttpException;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.Sse.Event;
import org.omnifaces.ai.model.Sse.Event.Type;

/**
 * API client utility for {@link BaseAIService} implementations.
 * <p>
 * This utility wraps {@link java.net.http.HttpClient} to provide a simple API interface for all {@link BaseAIService} implementations.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
final class AIApiClient {

    private static final Logger logger = Logger.getLogger(AIApiClient.class.getPackageName());

    private static final String APPLICATION_JSON = "application/json";
    private static final String EVENT_STREAM = "text/event-stream";
    private static final String MULTIPART_FORM_DATA = "multipart/form-data";

    /** The User-Agent header: {@value} */
    public static final String USER_AGENT = "OmniHai 1.0 (https://github.com/omnifaces/omnihai)";
    /** Default max retries: {@value} */
    public static final int MAX_RETRIES = 3;
    /** Initial retry backoff time: {@value}ms (increases exponentially on every retry) */
    public static final long INITIAL_BACKOFF_MS = 1000;

    private final HttpClient client;
    private final Duration requestTimeout;

    private AIApiClient(HttpClient client, Duration requestTimeout) {
        this.client = client;
        this.requestTimeout = requestTimeout;
    }

    /**
     * Creates a new API client instance with custom timeouts.
     *
     * @param connectTimeout The connection timeout
     * @param requestTimeout The request timeout
     * @return A new API client instance
     */
    public static AIApiClient newInstance(Duration connectTimeout, Duration requestTimeout) {
        return new AIApiClient(newBuilder().connectTimeout(connectTimeout).build(), requestTimeout);
    }

    /**
     * Sends a POST request for the specified {@link BaseAIService}.
     * Will retry at most {@value #MAX_RETRIES} times in case of a connection error with exponentially incremental backoff of {@value #INITIAL_BACKOFF_MS}ms.
     *
     * @param service The {@link BaseAIService} to extract URI and headers from.
     * @param path the API path
     * @param payload The request payload
     * @return The response body as a string
     * @throws AIHttpException if the request fails
     */
    public CompletableFuture<String> post(BaseAIService service, String path, JsonObject payload) throws AIHttpException {
        return sendWithRetryAsync(newJsonRequest(service, path, payload, APPLICATION_JSON), 0);
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
        return streamWithRetryAsync(newJsonRequest(service, path, payload, EVENT_STREAM), eventProcessor, 0);
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
        return sendWithRetryAsync(newUploadRequest(service, path, attachment, APPLICATION_JSON), 0);
    }

    private HttpRequest newJsonRequest(BaseAIService service, String path, JsonObject payload, String accept) {
        return newRequest(service, path, APPLICATION_JSON, accept, BodyPublishers.ofString(payload.toString()));
    }

    private HttpRequest newUploadRequest(BaseAIService service, String path, Attachment attachment, String accept) {
        var multipart = MultipartBodyPublisher.of(attachment);
        return newRequest(service, path, MULTIPART_FORM_DATA + "; boundary=" + multipart.boundary, accept, multipart.body);
    }

    private HttpRequest newRequest(BaseAIService service, String path, String contentType, String accept, BodyPublisher body) {
        var builder = HttpRequest.newBuilder(service.resolveURI(path)).timeout(requestTimeout).POST(body);
        builder.header("User-Agent", USER_AGENT);
        builder.header("Content-Type", contentType);
        builder.header("Accept", accept);
        service.getRequestHeaders().forEach(builder::header);
        return builder.build();
    }

    private CompletableFuture<String> sendWithRetryAsync(HttpRequest request, int attempt) {
        return withRetry(() -> client.sendAsync(request, ofString()).thenCompose(response -> handleResponse(request, response, HttpResponse::body, r -> completedFuture(r.body()))), attempt);
    }

    private CompletableFuture<Void> streamWithRetryAsync(HttpRequest request, Predicate<Event> eventProcessor, int attempt) {
        return withRetry(() -> client.sendAsync(request, ofLines()).thenCompose(response -> handleResponse(request, response, r -> r.body().collect(joining("\n")), r -> consumeEventStream(r, eventProcessor))), attempt);
    }

    private static <R, T> CompletableFuture<R> handleResponse(HttpRequest request, HttpResponse<T> response, Function<HttpResponse<T>, String> bodyExtractor, Function<HttpResponse<T>, CompletableFuture<R>> successHandler) {
        var statusCode = response.statusCode();

        if (statusCode >= AIBadRequestException.STATUS_CODE) {
            return failedFuture(fromStatusCode(request.uri(), statusCode, bodyExtractor.apply(response)));
        }

        return successHandler.apply(response);
    }

    private static CompletableFuture<Void> consumeEventStream(HttpResponse<Stream<String>> response, Predicate<Event> eventProcessor) {
        var future = new CompletableFuture<Void>();
        runAsync(() -> processEvents(response, future, eventProcessor));
        return future;
    }

    private static void processEvents(HttpResponse<Stream<String>> response, CompletableFuture<Void> future, Predicate<Event> eventProcessor) {
        try {
            var lines = response.body().iterator();
            var dataBuffer = new StringBuilder();

            while (lines.hasNext()) {
                var line = lines.next();

                if (line.isBlank()) {
                    if (!processDataEvent(dataBuffer, eventProcessor)) {
                        break;
                    }

                    continue;
                }

                line = line.strip();

                if (line.startsWith(":")) {
                    continue;
                }

                var event = createEvent(line);

                if (event == null) {
                    continue;
                }

                if (event.type() == Type.DATA) {
                    if (!dataBuffer.isEmpty()) {
                        dataBuffer.append('\n');
                    }

                    dataBuffer.append(line.substring(5).strip());
                }
                else if (!processDataEvent(dataBuffer, eventProcessor) || !eventProcessor.test(event)) {
                    break;
                }
            }

            processDataEvent(dataBuffer, eventProcessor);
            future.complete(null);
        }
        catch (Exception e) {
            future.completeExceptionally(e);
        }
    }

    private static boolean processDataEvent(StringBuilder dataBuffer, Predicate<Event> eventProcessor) {
        if (!dataBuffer.isEmpty()) {
            var data = dataBuffer.toString();
            dataBuffer.setLength(0);

            if (!eventProcessor.test(new Event(Type.DATA, data))) {
                return false;
            }
        }

        return true;
    }

    private static Event createEvent(String line) {
        if (line.startsWith("id:")) {
            var id = line.substring(3).strip();
            return new Event(Type.ID, id);
        }
        else if (line.startsWith("event:")) {
            var event = line.substring(6).strip();
            return new Event(Type.EVENT, event);
        }
        else if (line.startsWith("data:")) {
            var data = line.substring(5).strip();
            return new Event(Type.DATA, data);
        }
        else {
            logger.log(FINE, () -> "Ignoring unknown SSE line: " + line);
            return null;
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
     * Retryable errors are transient connection issues indicated by an {@link IOException}
     * with a message containing "timed", "terminated", "reset", "refused", or "goaway" anywhere in the cause chain.
     *
     * @param throwable The exception to check.
     * @return {@code true} if the error is transient and the request should be retried.
     */
    private static boolean isRetryable(Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        return Stream.iterate(throwable, Objects::nonNull, Throwable::getCause)
            .filter(IOException.class::isInstance)
            .findFirst()
            .map(ioException ->
                Stream.iterate(ioException, Objects::nonNull, Throwable::getCause)
                      .map(Throwable::getMessage)
                      .filter(Objects::nonNull)
                      .map(String::toLowerCase)
                      .anyMatch(msg -> msg.contains("timed") || msg.contains("terminated") || msg.contains("reset") || msg.contains("refused") || msg.contains("goaway"))
            )
            .orElse(false);
    }

    private static class MultipartBodyPublisher {
        private final BodyPublisher body;
        private final String boundary;

        private MultipartBodyPublisher(Attachment attachment) {
            this.boundary = "----OmniHaiBoundary" + System.currentTimeMillis();

            try (var os = new ByteArrayOutputStream()) {
                writeTextPart(os, "purpose", "assistants");
                writeFilePart(os, "file", attachment.fileName(), attachment.mimeType().value(), attachment.content());
                writeLine(os, "--" + boundary + "--");
                body = HttpRequest.BodyPublishers.ofByteArray(os.toByteArray());
            }
            catch (IOException e) {
                throw new AIException("Cannot prepare multipart/form-data request for " + attachment.fileName(), e);
            }
        }

        public static MultipartBodyPublisher of(Attachment attachment) {
            return new MultipartBodyPublisher(attachment);
        }

        private void writeTextPart(OutputStream os, String name, String value) throws IOException {
            writeLine(os, "--" + boundary);
            writeLine(os, "Content-Disposition: form-data; name=\"" + name + "\"\r\n");
            writeLine(os, value);
        }

        private void writeFilePart(OutputStream os, String name, String filename, String contentType, byte[] data) throws IOException {
            writeLine(os, "--" + boundary);
            writeLine(os, "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"");
            writeLine(os, "Content-Type: " + contentType + "\r\n");
            os.write(data);
            writeLine(os, "");
        }

        private static void writeLine(OutputStream os, String line) throws IOException {
            os.write((line + "\r\n").getBytes(UTF_8));
        }
    }
}
