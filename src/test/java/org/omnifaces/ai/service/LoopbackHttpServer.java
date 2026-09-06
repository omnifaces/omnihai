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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * An HTTP server on the loopback interface which answers what a test scripted it with, and records what it was asked.
 * <p>
 * Answers are served in the order they were scripted; the last one keeps being served once they run out, so a test which cares about one answer alone scripts
 * one. A test which scripts nothing is answered an empty JSON object.
 */
final class LoopbackHttpServer implements AutoCloseable {

    /**
     * What the server answers: the status, the headers beside the length, the body, and the length it announces, which is the body's own unless a test states a
     * longer one to cut the answer short.
     */
    record Answer(int status, Map<String, String> headers, byte[] body, int declaredLength) {

        Answer(int status, Map<String, String> headers, byte[] body) {
            this(status, headers, body, body.length);
        }

        /**
         * An answer which announces more than it carries, which is what a connection dropped halfway looks like.
         */
        static Answer ofCutOffJson() {
            return new Answer(200, Map.of("Content-Type", "application/json"), "{\"id\":".getBytes(UTF_8), 1024);
        }

        static Answer ofJson(String json) {
            return new Answer(200, Map.of("Content-Type", "application/json"), json.getBytes(UTF_8));
        }

        static Answer ofContent(String contentType, byte[] content) {
            return new Answer(200, Map.of("Content-Type", contentType), content);
        }

        static Answer ofGzippedJson(String json) {
            return new Answer(200, Map.of("Content-Type", "application/json", "Content-Encoding", "gzip"), gzip(json.getBytes(UTF_8)));
        }

        /**
         * An answer which announces gzip but carries something else, which is what a proxy mangling the body looks like.
         */
        static Answer ofBrokenGzip() {
            return new Answer(200, Map.of("Content-Type", "application/json", "Content-Encoding", "gzip"), "not gzipped at all".getBytes(UTF_8));
        }

        static Answer ofStatus(int status, String json) {
            return new Answer(status, Map.of("Content-Type", "application/json"), json.getBytes(UTF_8));
        }

        static Answer ofEvents(String... events) {
            return new Answer(200, Map.of("Content-Type", "text/event-stream"), String.join("", events).getBytes(UTF_8));
        }

    }

    /** What the server was asked. */
    record Request(String method, String path, String query, Map<String, List<String>> headers, byte[] body) {

        String bodyAsString() {
            return new String(body, UTF_8);
        }

    }

    private final HttpServer server;
    private final Queue<Answer> answers = new ArrayDeque<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger answered = new AtomicInteger();
    private Answer lastAnswer = Answer.ofJson("{}");

    static LoopbackHttpServer start() {
        try {
            return new LoopbackHttpServer(HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private LoopbackHttpServer(HttpServer server) {
        this.server = server;
        server.createContext("/", this::handle);
        server.start();
    }

    /** The endpoint to configure a service with, which every path of a test resolves against. */
    String endpoint() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/v1/";
    }

    LoopbackHttpServer answer(Answer... scripted) {
        answers.addAll(List.of(scripted));
        return this;
    }

    Request lastRequest() {
        return requests.get(requests.size() - 1);
    }

    int requestCount() {
        return requests.size();
    }

    /**
     * Waits until the server was asked the given number of times, so that a test can assert on a request which was sent beside the call rather than by it.
     */
    void awaitRequests(int count) {
        var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();

        while (answered.get() < count) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Expected " + count + " requests but answered " + answered.get());
            }

            Thread.onSpinWait();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            // Recorded on arrival, so that a caller which was answered can read what it was answered about.
            requests.add(
                new Request(
                    exchange.getRequestMethod(), exchange.getRequestURI().getPath(), exchange.getRequestURI().getQuery(),
                    Map.copyOf(exchange.getRequestHeaders()), exchange.getRequestBody().readAllBytes()
                )
            );

            var answer = answers.isEmpty() ? lastAnswer : answers.poll();
            lastAnswer = answer;
            answer.headers().forEach(exchange.getResponseHeaders()::set);
            exchange.sendResponseHeaders(answer.status(), answer.body().length == 0 ? -1 : answer.declaredLength());
            exchange.getResponseBody().write(answer.body());
            exchange.getResponseBody().flush();

            // Counted once served, so that a test waiting on a request sent beside its call knows the caller was served rather than merely heard.
            answered.incrementAndGet();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static byte[] gzip(byte[] content) {
        var bytes = new ByteArrayOutputStream();

        try (var gzip = new GZIPOutputStream(bytes)) {
            gzip.write(content);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return bytes.toByteArray();
    }

}
