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
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.function.Function.identity;

import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.exception.AIRateLimitExceededException;
import org.omnifaces.ai.exception.AIServiceUnavailableException;
import org.omnifaces.ai.exception.AIStreamAbortedException;

/**
 * {@link AIService} decorator that transparently retries failed operations with exponential backoff.
 * <p>
 * Every operation on the wrapped service &mdash; chat, image, audio, moderation, etc., in both synchronous and asynchronous form &mdash; is retried when it
 * fails with a {@link #DEFAULT_RETRY_ON retryable} error, up to a configurable number of attempts. Backoff grows exponentially between attempts and is
 * optionally randomized with full jitter to avoid thundering-herd retries. Asynchronous retries are scheduled without blocking a thread.
 * <p>
 * Usage example:
 *
 * <pre>
 *
 * var resilient = new RetryingAIService(service); // 3 attempts, sensible defaults
 *
 * var custom = RetryingAIService.newBuilder(service)
 *     .maxAttempts(5)
 *     .initialBackoff(Duration.ofSeconds(1))
 *     .maxBackoff(Duration.ofSeconds(20))
 *     .maxDuration(Duration.ofMinutes(1))
 *     .build();
 * </pre>
 *
 * Decorators compose, so retry can be combined with {@link FailoverAIService}:
 *
 * <pre>
 *
 * var service = new RetryingAIService(new FailoverAIService(primary, backup));
 * </pre>
 *
 * <strong>Streaming:</strong> {@code chatStream} is retried only while it is safe to do so. An attempt that fails before emitting any token is retried
 * transparently. An attempt that fails after emitting one or more tokens cannot be restarted without replaying them, so it fails with a terminal
 * {@link AIStreamAbortedException} instead of silently leaving the consumer with a duplicated prefix. To opt into restarting a partially consumed stream, pass
 * a {@link ResettableConsumer} as token consumer: it is notified via {@link ResettableConsumer#onReset(Throwable, int)} before each new attempt, so it can
 * discard what it accumulated.
 * <p>
 * <strong>Note:</strong> the bundled provider services already retry <em>transient I/O errors</em> at the HTTP layer, so for that category alone the attempts
 * of both layers compound. Rate limiting (HTTP 429) and service unavailability (HTTP 503) are not retried there, and are attempted exactly
 * {@link Builder#maxAttempts(int)} times.
 *
 * @author Bauke Scholtz
 * @since 1.5
 * @see InterceptingAIServiceWrapper
 * @see FailoverAIService
 */
public class RetryingAIService extends InterceptingAIServiceWrapper {

    private static final long serialVersionUID = 1L;

    /** The default maximum number of attempts (initial attempt plus retries): {@value}. */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    /** The default backoff before the first retry. */
    public static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(500);

    /** The default cap on the backoff between retries. */
    public static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(30);

    /** The default multiplier applied to the backoff after each attempt: {@value}. */
    public static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;

    /**
     * The default predicate deciding whether a failure is worth retrying: rate limiting (HTTP 429), service unavailability (HTTP 503), and transient I/O
     * errors. Deterministic client errors (HTTP 4xx other than 429), budget caps, and token limits are not retried.
     * <p>
     * Exposed so a custom policy can extend the default rather than restate it, e.g. to additionally retry a provider-specific condition:
     *
     * <pre>
     *
     * var service = RetryingAIService.newBuilder(wrapped)
     *     .retryOn(RetryingAIService.DEFAULT_RETRY_ON.or(failure -&gt; failure instanceof MyTransientException))
     *     .build();
     * </pre>
     */
    public static final Predicate<Throwable> DEFAULT_RETRY_ON = new DefaultRetryPredicate();

    /** The maximum number of attempts, counting the initial attempt plus retries. */
    private final int maxAttempts;
    /** The backoff before the first retry. */
    private final Duration initialBackoff;
    /** The cap on the backoff between retries. */
    private final Duration maxBackoff;
    /** The multiplier applied to the backoff after each attempt. */
    private final double backoffMultiplier;
    /** Whether to apply full jitter to each backoff. */
    private final boolean jitter;
    /** The overall time budget across all attempts, or {@code null} for unlimited. */
    private final Duration maxDuration;
    /** The predicate deciding whether a failure is worth retrying. */
    private final Predicate<Throwable> retryOn;

    /**
     * Creates a retrying decorator around the given service using the default retry policy: {@value #DEFAULT_MAX_ATTEMPTS} attempts, exponential backoff from
     * {@link #DEFAULT_INITIAL_BACKOFF} capped at {@link #DEFAULT_MAX_BACKOFF} with full jitter, retrying on {@link #DEFAULT_RETRY_ON}.
     *
     * @param wrapped The service to wrap, must not be {@code null}.
     * @throws NullPointerException if wrapped is {@code null}.
     */
    public RetryingAIService(AIService wrapped) {
        this(newBuilder(wrapped));
    }

    private RetryingAIService(Builder builder) {
        super(builder.wrapped);
        this.maxAttempts = builder.maxAttempts;
        this.initialBackoff = builder.initialBackoff;
        this.maxBackoff = builder.maxBackoff;
        this.backoffMultiplier = builder.backoffMultiplier;
        this.jitter = builder.jitter;
        this.maxDuration = builder.maxDuration;
        this.retryOn = builder.retryOn;
    }

    /**
     * Returns a new builder for a retrying decorator around the given service.
     *
     * @param wrapped The service to wrap, must not be {@code null}.
     * @return A new builder.
     * @throws NullPointerException if wrapped is {@code null}.
     */
    public static Builder newBuilder(AIService wrapped) {
        return new Builder(wrapped);
    }

    @Override
    protected <R> R intercept(Function<AIService, R> operation) {
        var startNanos = System.nanoTime();

        for (var attempt = 1;; attempt++) {
            try {
                return operation.apply(getWrapped());
            }
            catch (RuntimeException e) {
                if (!shouldRetry(e, attempt, startNanos)) {
                    throw e;
                }

                sleep(backoffMillis(attempt));
            }
        }
    }

    @Override
    protected <R> CompletableFuture<R> interceptAsync(Function<AIService, CompletableFuture<R>> operation) {
        return attemptAsync(operation, 1, System.nanoTime());
    }

    private <R> CompletableFuture<R> attemptAsync(Function<AIService, CompletableFuture<R>> operation, int attempt, long startNanos) {
        CompletableFuture<R> future;

        try {
            future = operation.apply(getWrapped());
        }
        catch (RuntimeException e) {
            future = CompletableFuture.failedFuture(e);
        }

        return future.exceptionallyCompose(error -> {
            if (!shouldRetry(error, attempt, startNanos)) {
                return CompletableFuture.failedFuture(error);
            }

            var delayed = CompletableFuture.delayedExecutor(backoffMillis(attempt), MILLISECONDS);
            return CompletableFuture.supplyAsync(() -> attemptAsync(operation, attempt + 1, startNanos), delayed).thenCompose(identity());
        });
    }

    private boolean shouldRetry(Throwable throwable, int attempt, long startNanos) {
        if (isUnrecoverable(throwable)) {
            return false;
        }

        if (attempt >= maxAttempts) {
            return false;
        }

        if (maxDuration != null && System.nanoTime() - startNanos >= maxDuration.toNanos()) {
            return false;
        }

        return retryOn.test(unwrap(throwable));
    }

    private long backoffMillis(int attempt) {
        var exponential = initialBackoff.toMillis() * Math.pow(backoffMultiplier, attempt - 1D);
        var capped = (long) Math.min(exponential, maxBackoff.toMillis());
        return jitter ? ThreadLocalRandom.current().nextLong(capped + 1) : capped;
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }

        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AIException("Retry backoff interrupted", e);
        }
    }

    /**
     * The {@link #DEFAULT_RETRY_ON} predicate, as a named {@link Serializable} class so that a {@link RetryingAIService} configured with the default policy
     * serializes cleanly.
     */
    private static final class DefaultRetryPredicate implements Predicate<Throwable>, Serializable {

        private static final long serialVersionUID = 1L;

        @Override
        public boolean test(Throwable throwable) {
            var cause = unwrap(throwable);
            return cause instanceof AIRateLimitExceededException
                || cause instanceof AIServiceUnavailableException
                || hasCause(cause, IOException.class);
        }

        private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
            for (var current = throwable; current != null; current = current.getCause()) {
                if (type.isInstance(current)) {
                    return true;
                }

                if (current == current.getCause()) {
                    break;
                }
            }

            return false;
        }

    }

    /**
     * Builder for a {@link RetryingAIService} with a customizable retry policy.
     */
    public static class Builder {

        private final AIService wrapped;
        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private Duration initialBackoff = DEFAULT_INITIAL_BACKOFF;
        private Duration maxBackoff = DEFAULT_MAX_BACKOFF;
        private double backoffMultiplier = DEFAULT_BACKOFF_MULTIPLIER;
        private boolean jitter = true;
        private Duration maxDuration;
        private Predicate<Throwable> retryOn = DEFAULT_RETRY_ON;

        private Builder(AIService wrapped) {
            this.wrapped = requireNonNull(wrapped, "wrapped");
        }

        /**
         * Sets the maximum number of attempts, counting the initial attempt plus retries. Defaults to {@value RetryingAIService#DEFAULT_MAX_ATTEMPTS}.
         *
         * @param maxAttempts The maximum number of attempts, must be at least 1.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException if maxAttempts is less than 1.
         */
        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be at least 1");
            }

            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * Sets the backoff before the first retry, from which subsequent backoffs grow by {@link #backoffMultiplier(double)}. Defaults to
         * {@link RetryingAIService#DEFAULT_INITIAL_BACKOFF}.
         *
         * @param initialBackoff The initial backoff, must not be negative.
         * @return This builder instance for chaining.
         * @throws NullPointerException if initialBackoff is {@code null}.
         * @throws IllegalArgumentException if initialBackoff is negative.
         */
        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = requireNonNegative(initialBackoff, "initialBackoff");
            return this;
        }

        /**
         * Sets the cap on the backoff between retries. Defaults to {@link RetryingAIService#DEFAULT_MAX_BACKOFF}.
         *
         * @param maxBackoff The maximum backoff, must not be negative.
         * @return This builder instance for chaining.
         * @throws NullPointerException if maxBackoff is {@code null}.
         * @throws IllegalArgumentException if maxBackoff is negative.
         */
        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = requireNonNegative(maxBackoff, "maxBackoff");
            return this;
        }

        /**
         * Sets the multiplier applied to the backoff after each attempt. Defaults to {@value RetryingAIService#DEFAULT_BACKOFF_MULTIPLIER}.
         *
         * @param backoffMultiplier The backoff multiplier, must be at least 1.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException if backoffMultiplier is less than 1.
         */
        public Builder backoffMultiplier(double backoffMultiplier) {
            if (backoffMultiplier < 1) {
                throw new IllegalArgumentException("backoffMultiplier must be at least 1");
            }

            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        /**
         * Sets whether to apply full jitter, randomizing each backoff uniformly between zero and its computed value. Defaults to {@code true}.
         *
         * @param jitter Whether to apply full jitter.
         * @return This builder instance for chaining.
         */
        public Builder jitter(boolean jitter) {
            this.jitter = jitter;
            return this;
        }

        /**
         * Sets an overall time budget across all attempts, after which no further retry is scheduled. Defaults to unlimited.
         *
         * @param maxDuration The maximum total duration, or {@code null} for unlimited.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException if maxDuration is negative.
         */
        public Builder maxDuration(Duration maxDuration) {
            this.maxDuration = maxDuration == null ? null : requireNonNegative(maxDuration, "maxDuration");
            return this;
        }

        /**
         * Sets the predicate deciding whether a failure is worth retrying. Defaults to {@link RetryingAIService#DEFAULT_RETRY_ON}.
         * <p>
         * A service which lives in a passivating scope needs a serializable predicate; the default one is, a lambda is not.
         *
         * @param retryOn The predicate, receiving the failure cause (unwrapped from any {@link CompletionException}).
         * @return This builder instance for chaining.
         * @throws NullPointerException if retryOn is {@code null}.
         */
        public Builder retryOn(Predicate<Throwable> retryOn) {
            this.retryOn = requireNonNull(retryOn, "retryOn");
            return this;
        }

        /**
         * Builds the retrying decorator.
         *
         * @return A new {@link RetryingAIService}.
         */
        public RetryingAIService build() {
            return new RetryingAIService(this);
        }

        private static Duration requireNonNegative(Duration duration, String name) {
            requireNonNull(duration, name);

            if (duration.isNegative()) {
                throw new IllegalArgumentException(name + " must not be negative");
            }

            return duration;
        }

    }

}
