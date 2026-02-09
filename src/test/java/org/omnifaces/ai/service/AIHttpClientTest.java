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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ConnectException;

import org.junit.jupiter.api.Test;

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
}
