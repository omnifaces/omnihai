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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIBadRequestException;
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

}
