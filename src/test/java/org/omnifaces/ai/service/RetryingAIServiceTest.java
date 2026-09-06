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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIBadRequestException;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.exception.AIRateLimitExceededException;
import org.omnifaces.ai.exception.AIServiceUnavailableException;
import org.omnifaces.ai.exception.AIStreamAbortedException;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatOptions;

class RetryingAIServiceTest {

    private static final URI ENDPOINT = URI.create("https://example.invalid");
    private static final ChatInput INPUT = ChatInput.newBuilder().message("hi").build();
    private static final ChatOptions OPTIONS = ChatOptions.DEFAULT;

    @Test
    void syncRetriesUntilSuccess() {
        var wrapped = mock(AIService.class);
        when(wrapped.chat("hi")).thenThrow(unavailable()).thenThrow(unavailable()).thenReturn("ok");

        var service = fast(wrapped).build();

        assertEquals("ok", service.chat("hi"));
        verify(wrapped, times(3)).chat("hi");
    }

    @Test
    void syncStopsAfterMaxAttempts() {
        var wrapped = mock(AIService.class);
        when(wrapped.chat("hi")).thenThrow(unavailable());

        var service = fast(wrapped).maxAttempts(2).build();

        assertThrows(AIServiceUnavailableException.class, () -> service.chat("hi"));
        verify(wrapped, times(2)).chat("hi");
    }

    @Test
    void syncDoesNotRetryNonRetryable() {
        var wrapped = mock(AIService.class);
        when(wrapped.chat("hi")).thenThrow(new AIBadRequestException(ENDPOINT, "bad"));

        var service = fast(wrapped).build();

        assertThrows(AIBadRequestException.class, () -> service.chat("hi"));
        verify(wrapped, times(1)).chat("hi");
    }

    @Test
    void asyncRetriesUntilSuccess() throws Exception {
        var wrapped = mock(AIService.class);
        when(wrapped.chatAsync(any(ChatInput.class), any(ChatOptions.class)))
            .thenReturn(CompletableFuture.failedFuture(unavailable()))
            .thenReturn(CompletableFuture.failedFuture(unavailable()))
            .thenReturn(CompletableFuture.completedFuture("ok"));

        var service = fast(wrapped).build();

        assertEquals("ok", service.chatAsync(INPUT, OPTIONS).get());
        verify(wrapped, times(3)).chatAsync(any(ChatInput.class), any(ChatOptions.class));
    }

    @Test
    void asyncStopsAfterMaxAttempts() {
        var wrapped = mock(AIService.class);
        when(wrapped.chatAsync(any(ChatInput.class), any(ChatOptions.class))).thenReturn(CompletableFuture.failedFuture(unavailable()));

        var service = fast(wrapped).maxAttempts(2).build();
        var future = service.chatAsync(INPUT, OPTIONS);

        var exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(chainContains(exception, AIServiceUnavailableException.class));
        verify(wrapped, times(2)).chatAsync(any(ChatInput.class), any(ChatOptions.class));
    }

    @Test
    void streamRetriesWhenNoTokenWasEmitted() throws Exception {
        var wrapped = mock(AIService.class);
        when(wrapped.chatStream(any(ChatInput.class), any(ChatOptions.class), any()))
            .thenReturn(CompletableFuture.failedFuture(unavailable()))
            .thenReturn(CompletableFuture.completedFuture(null));

        var service = fast(wrapped).build();
        var tokens = new StringBuilder();

        service.chatStream(INPUT, OPTIONS, tokens::append).get();

        assertEquals("", tokens.toString());
        verify(wrapped, times(2)).chatStream(any(ChatInput.class), any(ChatOptions.class), any());
    }

    @Test
    void streamAbortsWhenTokenAlreadyEmittedAndConsumerIsNotResettable() {
        var wrapped = mock(AIService.class);
        when(wrapped.chatStream(any(ChatInput.class), any(ChatOptions.class), any())).thenAnswer(emitThenFail("partial"));

        var service = fast(wrapped).build();
        var tokens = new StringBuilder();
        var future = service.chatStream(INPUT, OPTIONS, tokens::append);

        var exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(chainContains(exception, AIStreamAbortedException.class));
        assertTrue(chainContains(exception, AIServiceUnavailableException.class)); // original failure preserved as cause
        assertEquals("partial", tokens.toString());
        verify(wrapped, times(1)).chatStream(any(ChatInput.class), any(ChatOptions.class), any()); // no retry
    }

    @Test
    void streamRestartsAndResetsWhenConsumerIsResettable() throws Exception {
        var wrapped = mock(AIService.class);
        when(wrapped.chatStream(any(ChatInput.class), any(ChatOptions.class), any()))
            .thenAnswer(emitThenFail("a"))
            .thenAnswer(invocation -> {
                Consumer<String> onToken = invocation.getArgument(2);
                onToken.accept("b");
                onToken.accept("c");
                return CompletableFuture.completedFuture(null);
            });

        var service = fast(wrapped).build();
        var tokens = new StringBuilder();
        var resets = new ArrayList<Integer>();
        var consumer = ResettableConsumer.<String>of(tokens::append, (cause, attempt) -> {
            resets.add(attempt);
            tokens.setLength(0);
        });

        service.chatStream(INPUT, OPTIONS, consumer).get();

        assertEquals("bc", tokens.toString()); // the stale prefix "a" was discarded on reset
        assertEquals(List.of(2), resets);
        verify(wrapped, times(2)).chatStream(any(ChatInput.class), any(ChatOptions.class), any());
    }

    /**
     * An attempt can fail before it returns a future at all, e.g. when building the payload uploads an attachment and that upload fails. The reset handler must
     * still be told what went wrong, per {@link ResettableConsumer#onReset(Throwable, int)}.
     */
    @Test
    void streamResetReceivesCauseOfSynchronouslyFailedAttempt() throws Exception {
        var wrapped = mock(AIService.class);
        when(wrapped.chatStream(any(ChatInput.class), any(ChatOptions.class), any()))
            .thenThrow(unavailable())
            .thenReturn(CompletableFuture.completedFuture(null));

        var causes = new ArrayList<Throwable>();
        var consumer = ResettableConsumer.<String>of(token -> {
        }, (cause, attempt) -> causes.add(cause));

        fast(wrapped).build().chatStream(INPUT, OPTIONS, consumer).get();

        assertEquals(1, causes.size());
        assertInstanceOf(AIServiceUnavailableException.class, causes.get(0));
    }

    private static Answer<CompletableFuture<Void>> emitThenFail(String token) {
        return invocation -> {
            Consumer<String> onToken = invocation.getArgument(2);
            onToken.accept(token);
            return CompletableFuture.failedFuture(unavailable());
        };
    }

    /**
     * The default predicate is a named class rather than a lambda, so that a service configured with the default policy can be passivated by a container.
     */
    @Test
    @SuppressWarnings("unchecked")
    void defaultRetryPredicateSurvivesSerialization() throws Exception {
        var bytes = new ByteArrayOutputStream();

        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(RetryingAIService.DEFAULT_RETRY_ON);
        }

        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var predicate = (Predicate<Throwable>) in.readObject();

            assertTrue(predicate.test(unavailable()));
            assertFalse(predicate.test(new AIBadRequestException(ENDPOINT, "bad")));
        }
    }

    private static RetryingAIService.Builder fast(AIService wrapped) {
        return RetryingAIService.newBuilder(wrapped).initialBackoff(Duration.ZERO).maxBackoff(Duration.ZERO).jitter(false);
    }

    private static AIServiceUnavailableException unavailable() {
        return new AIServiceUnavailableException(ENDPOINT, "unavailable");
    }

    private static boolean chainContains(Throwable throwable, Class<? extends Throwable> type) {
        for (var current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }

        return false;
    }

    // =================================================================================================================
    // Builder validation
    // =================================================================================================================

    /**
     * The attempt count includes the first try, so a count below one would mean not calling the AI at all.
     */
    @Test
    void maxAttempts_belowOne_isRejected() {
        var builder = RetryingAIService.newBuilder(mock(AIService.class));

        assertThrows(IllegalArgumentException.class, () -> builder.maxAttempts(0));
    }

    /**
     * A multiplier below one would shorten each wait rather than lengthen it, which is the opposite of backing off.
     */
    @Test
    void backoffMultiplier_belowOne_isRejected() {
        var builder = RetryingAIService.newBuilder(mock(AIService.class));

        assertThrows(IllegalArgumentException.class, () -> builder.backoffMultiplier(0.5));
        assertDoesNotThrow(() -> builder.backoffMultiplier(1));
    }

    @Test
    void maxDuration_negative_isRejected() {
        var builder = RetryingAIService.newBuilder(mock(AIService.class));

        var negative = Duration.ofSeconds(-1);

        assertThrows(IllegalArgumentException.class, () -> builder.maxDuration(negative));
    }

    /**
     * An unlimited budget is stated by leaving it out rather than by a number standing in for forever.
     */
    @Test
    void maxDuration_null_meansUnlimited() {
        assertDoesNotThrow(() -> RetryingAIService.newBuilder(mock(AIService.class)).maxDuration(null));
    }

    @Test
    void retryOn_null_isRejected() {
        var builder = RetryingAIService.newBuilder(mock(AIService.class));

        assertThrows(NullPointerException.class, () -> builder.retryOn(null));
    }

    /**
     * A budget which has run out stops the retrying even when attempts are left, as the caller is already waiting longer than it allowed.
     */
    @Test
    void retrying_pastTheTimeBudget_stopsEvenWithAttemptsLeft() {
        var wrapped = mock(AIService.class);
        when(wrapped.chat("hi")).thenThrow(unavailable());

        var service = fast(wrapped).maxAttempts(5).maxDuration(Duration.ZERO).build();

        assertThrows(AIServiceUnavailableException.class, () -> service.chat("hi"));
        verify(wrapped, times(1)).chat("hi");
    }

    // =================================================================================================================
    // Backing off between attempts
    // =================================================================================================================

    /**
     * A budget which still has room lets the retrying go on, so a limit is a ceiling rather than a switch which turns retrying off.
     */
    @Test
    void retrying_withinTheTimeBudget_keepsGoing() {
        var wrapped = mock(AIService.class);
        when(wrapped.chat("hi")).thenThrow(unavailable()).thenReturn("ok");

        var service = fast(wrapped).maxDuration(Duration.ofMinutes(1)).build();

        assertEquals("ok", service.chat("hi"));
        verify(wrapped, times(2)).chat("hi");
    }

    /**
     * A retry waits before it tries again, so a provider which is briefly overloaded is not hit with the next attempt at once.
     */
    @Test
    void backoff_betweenAttempts_waitsTheConfiguredDuration() {
        var wrapped = mock(AIService.class);
        when(wrapped.chat("hi")).thenThrow(unavailable()).thenReturn("ok");

        var service = RetryingAIService.newBuilder(wrapped)
            .initialBackoff(Duration.ofMillis(20)).maxBackoff(Duration.ofMillis(20)).jitter(false).build();

        var startNanos = System.nanoTime();

        assertEquals("ok", service.chat("hi"));
        assertTrue(Duration.ofNanos(System.nanoTime() - startNanos).compareTo(Duration.ofMillis(20)) >= 0);
    }

    /**
     * Jitter spreads the waits of callers which failed at the same moment, so it picks somewhere up to the wait rather than the wait itself.
     */
    @Test
    void backoff_withJitter_waitsAtMostTheCappedDuration() {
        var wrapped = mock(AIService.class);
        when(wrapped.chat("hi")).thenThrow(unavailable()).thenReturn("ok");

        var service = RetryingAIService.newBuilder(wrapped)
            .initialBackoff(Duration.ofMillis(2)).maxBackoff(Duration.ofSeconds(1)).jitter(true).build();

        var startNanos = System.nanoTime();

        assertEquals("ok", service.chat("hi"));
        assertTrue(Duration.ofNanos(System.nanoTime() - startNanos).compareTo(Duration.ofSeconds(1)) < 0);
    }

    /**
     * A caller which is being interrupted wants to stop, so the wait is given up on and the interrupt is passed on rather than swallowed.
     */
    @Test
    void backoff_whenTheCallerIsInterrupted_givesUpAndKeepsTheInterrupt() {
        var wrapped = mock(AIService.class);
        when(wrapped.chat("hi")).thenThrow(unavailable());

        var service = RetryingAIService.newBuilder(wrapped).initialBackoff(Duration.ofMinutes(1)).maxBackoff(Duration.ofMinutes(1)).jitter(false).build();

        Thread.currentThread().interrupt();

        try {
            var exception = assertThrows(AIException.class, () -> service.chat("hi"));

            assertTrue(exception.getMessage().contains("interrupted"), exception.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        }
        finally {
            Thread.interrupted();
        }
    }

    // =================================================================================================================
    // The default policy on which failures are worth retrying
    // =================================================================================================================

    /**
     * A failure of the connection itself is worth retrying wherever it sits in the chain, as the next attempt may well get through.
     */
    @Test
    void defaultRetryPredicate_rateLimit_isRetried() {
        assertTrue(RetryingAIService.DEFAULT_RETRY_ON.test(new AIRateLimitExceededException(ENDPOINT, "rate limited")));
    }

    @Test
    void defaultRetryPredicate_ioExceptionAnywhereInTheChain_isRetried() {
        assertTrue(RetryingAIService.DEFAULT_RETRY_ON.test(new IllegalStateException("wrapper", new IOException("connection reset"))));
    }

    /**
     * A chain which points back at itself is walked once rather than forever.
     */
    @Test
    void defaultRetryPredicate_selfReferentialCause_terminates() {
        assertFalse(RetryingAIService.DEFAULT_RETRY_ON.test(new SelfCausingException()));
    }

    private static final class SelfCausingException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        @Override
        public synchronized Throwable getCause() {
            return this;
        }

    }

    /**
     * An attempt can fail before it returns a future at all, e.g. when building the payload uploads an attachment and that upload fails. That still counts as a
     * failed attempt, so it is retried.
     */
    @Test
    void asyncRetriesWhenAttemptFailsBeforeReturningAFuture() throws Exception {
        var wrapped = mock(AIService.class);
        when(wrapped.chatAsync(any(ChatInput.class), any(ChatOptions.class)))
            .thenThrow(unavailable())
            .thenReturn(CompletableFuture.completedFuture("ok"));

        assertEquals("ok", fast(wrapped).build().chatAsync(INPUT, OPTIONS).get());
    }

    @Test
    void retryOn_ownPredicate_decidesWhichFailureIsRetried() {
        var wrapped = mock(AIService.class);
        when(wrapped.chat("hi")).thenThrow(new AIBadRequestException(ENDPOINT, "bad")).thenReturn("ok");

        var service = fast(wrapped).retryOn(AIBadRequestException.class::isInstance).build();

        assertEquals("ok", service.chat("hi"));
        verify(wrapped, times(2)).chat("hi");
    }

}
