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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.exception.AIHttpException;

class AIHttpClientTest {

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

        var exception = assertThrows(AIHttpException.class, () -> AIHttpClient.decompressDownloadIfNeeded(newResponse(302, body)));

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
