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

import static java.util.Objects.requireNonNull;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.omnifaces.ai.exception.AIStreamAbortedException;

/**
 * A {@link Consumer} that can be told to discard everything it consumed so far and start over.
 * <p>
 * A push-based operation such as {@code chatStream(…)} delivers its result incrementally. When a resilience decorator such as {@link RetryingAIService} or
 * {@link FailoverAIService} re-attempts such an operation, the new attempt replays the result from the start, which would leave a plain {@link Consumer}
 * holding a duplicated prefix. Because a plain {@link Consumer} offers no channel to signal that, those decorators refuse to re-attempt an operation that
 * already emitted an item, and fail with {@link AIStreamAbortedException} instead.
 * <p>
 * Passing a {@code ResettableConsumer} opts into re-attempting: {@link #onReset(Throwable, int)} is invoked immediately before each new attempt, giving the
 * consumer the chance to discard what it accumulated.
 * <p>
 * Usage example:
 *
 * <pre>
 *
 * var response = new StringBuilder();
 * service.chatStream(
 *     message, ResettableConsumer.of(
 *         token -&gt; response.append(token),
 *         (cause, attempt) -&gt; response.setLength(0)
 *     )
 * );
 * </pre>
 *
 * @author Bauke Scholtz
 * @since 1.5
 * @param <T> The consumed item type.
 * @see InterceptingAIServiceWrapper
 */
public interface ResettableConsumer<T> extends Consumer<T> {

    /**
     * Invoked immediately before an operation is re-attempted, signaling that every item consumed so far is stale and must be discarded.
     *
     * @param cause The failure that triggered the re-attempt.
     * @param attempt The number of the attempt about to start, always greater than 1.
     */
    void onReset(Throwable cause, int attempt);

    /**
     * Composes a {@code ResettableConsumer} from an item handler and a reset handler.
     *
     * @param <T> The consumed item type.
     * @param onAccept Invoked for every consumed item.
     * @param resetHandler Invoked with the failure cause and the number of the attempt about to start, whenever the operation is re-attempted.
     * @return A ResettableConsumer delegating to the given handlers.
     * @throws NullPointerException if onAccept or resetHandler is {@code null}.
     */
    static <T> ResettableConsumer<T> of(Consumer<T> onAccept, BiConsumer<Throwable, Integer> resetHandler) {
        requireNonNull(onAccept, "onAccept");
        requireNonNull(resetHandler, "resetHandler");

        return new ResettableConsumer<>() {

            @Override
            public void accept(T item) {
                onAccept.accept(item);
            }

            @Override
            public void onReset(Throwable cause, int attempt) {
                resetHandler.accept(cause, attempt);
            }

        };
    }

}
