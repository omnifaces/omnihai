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
package org.omnifaces.ai.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.junit.jupiter.api.Test;

/**
 * A failed HTTP response is answered as the most specific exception the status code names, so that a caller can catch the one case it wants to handle, such as
 * a rate limit worth retrying, rather than inspecting a status code itself.
 */
class AIHttpExceptionTest {

    private static final URI URI_WITHOUT_QUERY = URI.create("https://example.org/v1/chat");
    private static final String RESPONSE_BODY = "the response body";

    @Test
    void fromStatusCode_badRequest() {
        assertInstanceOf(AIBadRequestException.class, AIHttpException.fromStatusCode(URI_WITHOUT_QUERY, 400, RESPONSE_BODY));
    }

    @Test
    void fromStatusCode_authentication() {
        assertInstanceOf(AIAuthenticationException.class, AIHttpException.fromStatusCode(URI_WITHOUT_QUERY, 401, RESPONSE_BODY));
    }

    @Test
    void fromStatusCode_paymentRequired() {
        assertInstanceOf(AIPaymentRequiredException.class, AIHttpException.fromStatusCode(URI_WITHOUT_QUERY, 402, RESPONSE_BODY));
    }

    @Test
    void fromStatusCode_authorization() {
        assertInstanceOf(AIAuthorizationException.class, AIHttpException.fromStatusCode(URI_WITHOUT_QUERY, 403, RESPONSE_BODY));
    }

    @Test
    void fromStatusCode_endpointNotFound() {
        assertInstanceOf(AIEndpointNotFoundException.class, AIHttpException.fromStatusCode(URI_WITHOUT_QUERY, 404, RESPONSE_BODY));
    }

    @Test
    void fromStatusCode_rateLimitExceeded() {
        assertInstanceOf(AIRateLimitExceededException.class, AIHttpException.fromStatusCode(URI_WITHOUT_QUERY, 429, RESPONSE_BODY));
    }

    @Test
    void fromStatusCode_serviceUnavailable() {
        assertInstanceOf(AIServiceUnavailableException.class, AIHttpException.fromStatusCode(URI_WITHOUT_QUERY, 503, RESPONSE_BODY));
    }

    /**
     * A status code with no type of its own is still answered as an exception carrying the code, as the caller has nothing else to inspect it with.
     */
    @Test
    void fromStatusCode_unmappedStatusCode_isTheGenericOne() {
        var exception = AIHttpException.fromStatusCode(URI_WITHOUT_QUERY, 418, RESPONSE_BODY);

        assertEquals(AIHttpException.class, exception.getClass());
        assertEquals(418, exception.getStatusCode());
    }

    @Test
    void fromStatusCode_carriesTheRequestDetails() {
        var exception = AIHttpException.fromStatusCode(URI_WITHOUT_QUERY, 429, RESPONSE_BODY);

        assertEquals(URI_WITHOUT_QUERY, exception.getUri());
        assertEquals(429, exception.getStatusCode());
        assertEquals(RESPONSE_BODY, exception.getResponseBody());
    }

    /**
     * Several providers carry the API key in the query string, so the message names the path alone: an exception message travels into logs and issue reports.
     */
    @Test
    void getMessage_omitsTheQueryStringOfTheUri() {
        var exception = new AIHttpException(URI.create("https://example.org/v1/chat?key=super-secret"), 400, RESPONSE_BODY);

        assertFalse(exception.getMessage().contains("super-secret"), "the query string may not travel into a log");
        assertTrue(exception.getMessage().contains("https://example.org/v1/chat"));
    }

    @Test
    void getMessage_namesTheStatusCodeAndTheResponseBody() {
        var exception = new AIHttpException(URI_WITHOUT_QUERY, 400, RESPONSE_BODY);

        assertEquals("HTTP 400 at " + URI_WITHOUT_QUERY + ": " + RESPONSE_BODY, exception.getMessage());
    }

    /**
     * A request which threw instead of answering has no response to report, so the exception carries the cause and leaves the response details empty.
     */
    @Test
    void constructedFromACause_carriesNoResponseDetails() {
        var cause = new IllegalStateException("connection reset");
        var exception = new AIHttpException("Request failed", cause);

        assertSame(cause, exception.getCause());
        assertEquals("Request failed", exception.getMessage());
        assertNull(exception.getUri());
        assertNull(exception.getResponseBody());
        assertEquals(0, exception.getStatusCode());
    }

}
