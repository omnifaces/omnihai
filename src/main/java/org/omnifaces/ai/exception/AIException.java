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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Base exception for all AI service-related errors.
 * <p>
 * This is the root of the OmniHai exception hierarchy:
 * <ul>
 * <li>{@link AIHttpException} - HTTP-level errors (4xx/5xx status codes)
 * <li>{@link AIResponseException} - Response content errors (parsing, missing content)
 * <li>{@link AITokenLimitExceededException} - Token limit exceeded error
 * <li>{@link AIBudgetExceededException} - Cumulative cost cap exceeded
 * <li>{@link AIStreamAbortedException} - Stream failed after already emitting tokens
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIHttpException
 * @see AIResponseException
 * @see AITokenLimitExceededException
 * @see AIBudgetExceededException
 * @see AIStreamAbortedException
 */
public class AIException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Unwraps an {@link AIException} from an exception propagated to the caller thread by an async operation, such as a {@link CompletionException} or a
     * {@link CancellationException}.
     * <p>
     * If the cause is already an {@code AIException}, it is returned directly. Otherwise, a new {@code AIException} wrapping the cause is created. Then a
     * suppressed exception is added, preserving the stack trace of the current thread.
     *
     * @param exception The exception from an async operation.
     * @return The unwrapped or newly created AI exception.
     */
    public static AIException asyncRequestFailed(Throwable exception) {
        return asyncRequestFailed(exception, new Exception("Caller stack trace"));
    }

    /**
     * Unwraps an {@link AIException} from an exception thrown during async operation and adds given exception as suppressed one.
     * <p>
     * If the cause is already an {@code AIException}, it is returned directly. Otherwise, a new {@code AIException} wrapping the cause is created.
     *
     * @param exception The exception from an async operation.
     * @param suppressed The suppressed exception, preserving the stack trace of the thread wherein the async operation was initiated.
     * @return The unwrapped or newly created AI exception.
     */
    public static AIException asyncRequestFailed(Throwable exception, Exception suppressed) {
        var cause = unwrapAsyncExceptionCause(exception);
        var aiException = cause instanceof AIException casted ? casted : new AIException("Async request failed", cause);
        aiException.addSuppressed(suppressed);
        return aiException;
    }

    /**
     * Returns the cause which the given async exception wraps, or the exception itself when it wraps none. Only the exceptions which exist to wrap another are
     * unwrapped; a {@link CancellationException} carries no cause and is therefore itself the most it can state.
     */
    private static Throwable unwrapAsyncExceptionCause(Throwable exception) {
        var cause = exception instanceof CompletionException || exception instanceof ExecutionException ? exception.getCause() : null;
        return cause != null ? cause : exception;
    }

    /**
     * Constructs a new AI exception with the specified message.
     *
     * @param message The detail message.
     */
    public AIException(String message) {
        super(message);
    }

    /**
     * Constructs a new AI exception with the specified message and cause.
     *
     * @param message The detail message.
     * @param cause The cause of this exception.
     */
    public AIException(String message, Throwable cause) {
        super(message, cause);
    }

}
