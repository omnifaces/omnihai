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

import java.net.URI;

/**
 * Exception thrown when an AI API request fails with an HTTP error status code.
 * <p>
 * This exception and its subclasses represent HTTP-level errors (4xx/5xx):
 * <ul>
 * <li>{@link AIBadRequestException} - 400 Bad Request
 * <li>{@link AIAuthenticationException} - 401 Unauthorized
 * <li>{@link AIAuthorizationException} - 403 Forbidden
 * <li>{@link AIEndpointNotFoundException} - 404 Not Found
 * <li>{@link AIRateLimitExceededException} - 429 Too Many Requests
 * <li>{@link AIServiceUnavailableException} - 503 Service Unavailable
 * </ul>
 * <p>
 * Use {@link #fromStatusCode(URI, int, String)} to create the appropriate subclass based on HTTP status code.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIResponseException
 */
public class AIHttpException extends AIException {

    private static final long serialVersionUID = 1L;

    /** The HTTP request URI. */
    private final URI uri;

    /** The HTTP status code. */
    private final int statusCode;

    /** The HTTP response body. */
    private final String responseBody;

    /**
     * Creates and returns the most specific {@link AIHttpException} subclass that matches the given HTTP status code.
     * <p>
     * If no specific exception type is defined for the status code, a generic {@link AIHttpException} is returned.
     * The created exception includes the request URI, status code, and response body (when available) to help with debugging.
     *
     * @param uri The URI of the HTTP request that caused the error (used in exception messages).
     * @param statusCode The HTTP status code returned by the server.
     * @param responseBody The response body (may be {@code null} or empty).
     * @return a subclass of {@link AIHttpException} matching the status code, or a generic {@link AIHttpException}.
     */
    public static AIHttpException fromStatusCode(URI uri, int statusCode, String responseBody) {
        return switch (statusCode) {
            case AIBadRequestException.STATUS_CODE -> new AIBadRequestException(uri, responseBody);
            case AIAuthenticationException.STATUS_CODE -> new AIAuthenticationException(uri, responseBody);
            case AIAuthorizationException.STATUS_CODE -> new AIAuthorizationException(uri, responseBody);
            case AIEndpointNotFoundException.STATUS_CODE -> new AIEndpointNotFoundException(uri, responseBody);
            case AIRateLimitExceededException.STATUS_CODE -> new AIRateLimitExceededException(uri, responseBody);
            case AIServiceUnavailableException.STATUS_CODE -> new AIServiceUnavailableException(uri, responseBody);
            default -> new AIHttpException(uri, statusCode, responseBody);
        };
    }

    /**
     * Constructs a new API exception with the specified URI, status code, and response body.
     *
     * @param uri The HTTP request URI.
     * @param statusCode The HTTP status code.
     * @param responseBody The HTTP response body.
     */
    public AIHttpException(URI uri, int statusCode, String responseBody) {
        super("HTTP " + statusCode + " at " + URI.create(uri.toString().split("\\?", 2)[0]) + ": " + responseBody);
        this.uri = uri;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /**
     * Constructs a new API exception with the specified message, and cause. Use this when request threw an exception instead of returning a response.
     *
     * @param message The detail message.
     * @param cause The cause of this exception.
     */
    public AIHttpException(String message, Throwable cause) {
        super(message, cause);
        this.uri = null;
        this.statusCode = 0;
        this.responseBody = null;
    }

    /**
     * Returns the HTTP request URI.
     * @return The HTTP request URI.
     */
    public URI getUri() {
        return uri;
    }

    /**
     * Returns the HTTP status code.
     * @return The HTTP status code.
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns the HTTP response body.
     * @return The HTTP response body.
     */
    public String getResponseBody() {
        return responseBody;
    }
}
