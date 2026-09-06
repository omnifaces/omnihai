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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

class AIExceptionTest {

    @Test
    void tokenLimitExceeded_namesTheLimitItRanInto() {
        assertEquals("max tokens reached", new AITokenLimitExceededException().getMessage());
    }

    /**
     * The tool the AI wanted to call next is carried separately from the message, so that a caller can raise the limit for that tool alone.
     */
    @Test
    void toolIteration_carriesTheToolItWantedToCallNext() {
        var exception = new AIToolIterationException(3, "getOrderStatus");

        assertEquals("getOrderStatus", exception.getRequestedTool());
        assertTrue(exception.getMessage().contains("3"));
        assertTrue(exception.getMessage().contains("getOrderStatus"));
    }

    @Test
    void asyncRequestFailed_unwrapsTheCompletionException() {
        var cause = new AIException("Endpoint unreachable");

        var unwrapped = AIException.asyncRequestFailed(new CompletionException(cause));

        assertSame(cause, unwrapped, "an AI exception is handed back rather than wrapped again");
    }

    @Test
    void asyncRequestFailed_wrapsAForeignCause() {
        var cause = new IllegalStateException("boom");

        var wrapped = AIException.asyncRequestFailed(new CompletionException(cause));

        assertSame(cause, wrapped.getCause());
        assertEquals("Async request failed", wrapped.getMessage());
    }

    @Test
    void asyncRequestFailed_preservesTheCallerStackTrace() {
        var suppressed = AIException.asyncRequestFailed(new CompletionException(new AIException("Endpoint unreachable"))).getSuppressed();

        assertEquals(1, suppressed.length, "the stack trace of the waiting thread is otherwise lost");
        assertEquals("Caller stack trace", suppressed[0].getMessage());
    }

    @Test
    void asyncRequestFailed_unwrapsTheExecutionException() {
        var cause = new AIException("Endpoint unreachable");

        var unwrapped = AIException.asyncRequestFailed(new ExecutionException(cause));

        assertSame(cause, unwrapped, "the sibling wrapper of CompletionException wraps just the same");
    }

    @Test
    void asyncRequestFailed_wrapsACauselessCompletionException() {
        var causeless = new CompletionException("nothing wrapped", null);

        var wrapped = AIException.asyncRequestFailed(causeless);

        assertSame(causeless, wrapped.getCause(), "unwrapping nothing may not leave the AI exception without a cause");
    }

    @Test
    void asyncRequestFailed_doesNotUnwrapACancellation() {
        var cancellation = new CancellationException("canceled");

        var wrapped = AIException.asyncRequestFailed(cancellation);

        assertSame(cancellation, wrapped.getCause(), "a cancellation carries no cause, so it is itself the most it can state");
        assertEquals(1, wrapped.getSuppressed().length, "a canceled wait surfaces as an AI exception like any other async failure");
    }

}
