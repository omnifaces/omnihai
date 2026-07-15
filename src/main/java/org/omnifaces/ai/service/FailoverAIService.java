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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Predicate;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIStreamAbortedException;

/**
 * {@link AIService} decorator that fails over to one or more alternate services when the primary fails.
 * <p>
 * Every operation &mdash; chat, image, audio, moderation, etc., in both synchronous and asynchronous form &mdash; is first attempted on the primary service. If
 * it fails with a {@link #DEFAULT_FAILOVER_ON failover-eligible} error, the same operation is retried on the next service in the fallback chain, and so on
 * until one succeeds or the chain is exhausted, in which case the last failure is propagated. Errors that are not failover-eligible (e.g. a bad request or
 * authentication failure) are propagated immediately without trying a fallback, since another provider would fail the same way.
 * <p>
 * Usage example:
 *
 * <pre>
 *
 * var service = new FailoverAIService(openai, anthropic, google);
 * </pre>
 *
 * Decorators compose, so failover can be combined with {@link RetryingAIService} to retry each provider before switching:
 *
 * <pre>
 *
 * var service = new FailoverAIService(new RetryingAIService(openai), new RetryingAIService(anthropic));
 * </pre>
 *
 * <strong>Streaming:</strong> {@code chatStream} fails over only while it is safe to do so. A provider that fails before emitting any token is transparently
 * replaced by the next one. A provider that fails after emitting one or more tokens cannot be replaced without replaying them, so the operation fails with a
 * terminal {@link AIStreamAbortedException} instead of silently leaving the consumer with a duplicated prefix. To opt into failing over a partially consumed
 * stream, pass a {@link ResettableConsumer} as token consumer: it is notified via {@link ResettableConsumer#onReset(Throwable, int)} before the next provider
 * starts, so it can discard what it accumulated.
 *
 * @author Bauke Scholtz
 * @since 1.5
 * @see InterceptingAIServiceWrapper
 * @see RetryingAIService
 */
public class FailoverAIService extends InterceptingAIServiceWrapper {

    private static final long serialVersionUID = 1L;

    /**
     * The default predicate deciding whether a failure warrants failing over to the next service: rate limiting (HTTP 429), service unavailability (HTTP 503),
     * and transient I/O errors. This is the same condition as {@link RetryingAIService#DEFAULT_RETRY_ON}, since a provider that is deterministically rejecting
     * a request (e.g. a bad request or authentication failure) would be rejected the same way by an alternate provider.
     */
    public static final Predicate<Throwable> DEFAULT_FAILOVER_ON = RetryingAIService.DEFAULT_RETRY_ON;

    /** The primary service followed by the ordered fallback chain. */
    private final List<AIService> services;
    /** The predicate deciding whether a failure warrants failing over to the next service. */
    private final Predicate<Throwable> failoverOn;

    /**
     * Creates a failover decorator with the given primary service and ordered fallback chain, using {@link #DEFAULT_FAILOVER_ON}.
     *
     * @param primary The primary service, tried first, must not be {@code null}.
     * @param fallbacks The ordered fallback services, tried in turn when the preceding service fails, none {@code null}.
     * @throws NullPointerException if primary, fallbacks, or any fallback element is {@code null}.
     */
    public FailoverAIService(AIService primary, AIService... fallbacks) {
        this(newBuilder(primary).fallbacks(fallbacks));
    }

    private FailoverAIService(Builder builder) {
        super(builder.primary);
        var services = new ArrayList<AIService>(builder.fallbacks.size() + 1);
        services.add(builder.primary);
        services.addAll(builder.fallbacks);
        this.services = List.copyOf(services);
        this.failoverOn = builder.failoverOn;
    }

    /**
     * Returns a new builder for a failover decorator with the given primary service.
     *
     * @param primary The primary service, tried first, must not be {@code null}.
     * @return A new builder.
     * @throws NullPointerException if primary is {@code null}.
     */
    public static Builder newBuilder(AIService primary) {
        return new Builder(primary);
    }

    @Override
    protected <R> R intercept(Function<AIService, R> operation) {
        RuntimeException failure = null;

        for (var service : services) {
            try {
                return operation.apply(service);
            }
            catch (RuntimeException e) {
                if (isUnrecoverable(e) || !failoverOn.test(unwrap(e))) {
                    throw e;
                }

                failure = e;
            }
        }

        throw failure;
    }

    @Override
    protected <R> CompletableFuture<R> interceptAsync(Function<AIService, CompletableFuture<R>> operation) {
        return attemptAsync(operation, 0);
    }

    private <R> CompletableFuture<R> attemptAsync(Function<AIService, CompletableFuture<R>> operation, int index) {
        CompletableFuture<R> future;

        try {
            future = operation.apply(services.get(index));
        }
        catch (RuntimeException e) {
            future = CompletableFuture.failedFuture(e);
        }

        return future.exceptionallyCompose(error -> {
            if (isUnrecoverable(error) || index + 1 >= services.size() || !failoverOn.test(unwrap(error))) {
                return CompletableFuture.failedFuture(error);
            }

            return attemptAsync(operation, index + 1);
        });
    }

    /**
     * Builder for a {@link FailoverAIService} with a customizable fallback chain and failover condition.
     */
    public static class Builder {

        private final AIService primary;
        private final List<AIService> fallbacks = new ArrayList<>();
        private Predicate<Throwable> failoverOn = DEFAULT_FAILOVER_ON;

        private Builder(AIService primary) {
            this.primary = requireNonNull(primary, "primary");
        }

        /**
         * Appends a fallback service to the chain. Fallbacks are tried in the order added.
         *
         * @param fallback The fallback service, must not be {@code null}.
         * @return This builder instance for chaining.
         * @throws NullPointerException if fallback is {@code null}.
         */
        public Builder fallback(AIService fallback) {
            fallbacks.add(requireNonNull(fallback, "fallback"));
            return this;
        }

        /**
         * Appends fallback services to the chain. Fallbacks are tried in the order added.
         *
         * @param fallbacks The fallback services, none {@code null}.
         * @return This builder instance for chaining.
         * @throws NullPointerException if fallbacks or any element is {@code null}.
         */
        public Builder fallbacks(AIService... fallbacks) {
            for (var fallback : requireNonNull(fallbacks, "fallbacks")) {
                fallback(fallback);
            }

            return this;
        }

        /**
         * Sets the predicate deciding whether a failure warrants failing over to the next service. Defaults to {@link FailoverAIService#DEFAULT_FAILOVER_ON}.
         *
         * @param failoverOn The predicate, receiving the failure cause (unwrapped from any {@link CompletionException}).
         * @return This builder instance for chaining.
         * @throws NullPointerException if failoverOn is {@code null}.
         */
        public Builder failoverOn(Predicate<Throwable> failoverOn) {
            this.failoverOn = requireNonNull(failoverOn, "failoverOn");
            return this;
        }

        /**
         * Builds the failover decorator.
         *
         * @return A new {@link FailoverAIService}.
         */
        public FailoverAIService build() {
            return new FailoverAIService(this);
        }

    }

}
