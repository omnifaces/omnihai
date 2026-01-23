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

/**
 * Exception thrown when an AI API response is problematic.
 * <p>
 * This includes the following cases:
 * <ul>
 * <li>The response body cannot be parsed as JSON.
 * <li>The response JSON contains an error object reported by the API.
 * <li>The response JSON is missing expected content.
 * </ul>
 * <p>
 * This differs from {@link AIHttpException} which represents HTTP-level errors (4xx/5xx status codes).
 * {@code AIResponseException} is thrown when the HTTP request succeeded but the response content is unusable.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public class AIResponseException extends AIException {

    private static final long serialVersionUID = 1L;

    /** The HTTP response body. */
    private final String responseBody;

    /**
     * Constructs a new API response exception with the specified message and HTTP response body.
     *
     * @param message The detail message.
     * @param responseBody The HTTP response body.
     */
    public AIResponseException(String message, String responseBody) {
        this(message, responseBody, null);
    }

    /**
     * Constructs a new API response exception with the specified message, HTTP response body and cause.
     *
     * @param message The detail message.
     * @param responseBody The HTTP response body.
     * @param cause The cause of this exception.
     */
    public AIResponseException(String message, String responseBody, Throwable cause) {
        super(message + ": " + responseBody, cause);
        this.responseBody = responseBody;
    }

    /**
     * Returns The HTTP response body.
     * @return The HTTP response body.
     */
    public String getResponseBody() {
        return responseBody;
    }
}
