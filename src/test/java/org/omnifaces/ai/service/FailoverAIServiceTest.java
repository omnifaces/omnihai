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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIBadRequestException;
import org.omnifaces.ai.exception.AIServiceUnavailableException;
import org.omnifaces.ai.exception.AIStreamAbortedException;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatOptions;

class FailoverAIServiceTest {

    private static final URI ENDPOINT = URI.create("https://example.invalid");
    private static final ChatInput INPUT = ChatInput.newBuilder().message("hi").build();
    private static final ChatOptions OPTIONS = ChatOptions.DEFAULT;

    @Test
    void syncFailsOverToNextService() {
        var primary = mock(AIService.class);
        var secondary = mock(AIService.class);
        when(primary.chat("hi")).thenThrow(unavailable());
        when(secondary.chat("hi")).thenReturn("ok");

        var service = new FailoverAIService(primary, secondary);

        assertEquals("ok", service.chat("hi"));
        verify(primary).chat("hi");
        verify(secondary).chat("hi");
    }

    @Test
    void syncDoesNotFailOverOnNonEligibleError() {
        var primary = mock(AIService.class);
        var secondary = mock(AIService.class);
        when(primary.chat("hi")).thenThrow(new AIBadRequestException(ENDPOINT, "bad"));

        var service = new FailoverAIService(primary, secondary);

        assertThrows(AIBadRequestException.class, () -> service.chat("hi"));
        verify(primary).chat("hi");
        verifyNoInteractions(secondary);
    }

    @Test
    void syncPropagatesLastFailureWhenChainExhausted() {
        var primary = mock(AIService.class);
        var secondary = mock(AIService.class);
        when(primary.chat("hi")).thenThrow(unavailable());
        when(secondary.chat("hi")).thenThrow(unavailable());

        var service = new FailoverAIService(primary, secondary);

        assertThrows(AIServiceUnavailableException.class, () -> service.chat("hi"));
        verify(primary).chat("hi");
        verify(secondary).chat("hi");
    }

    @Test
    void asyncFailsOverToNextService() throws Exception {
        var primary = mock(AIService.class);
        var secondary = mock(AIService.class);
        when(primary.chatAsync(any(ChatInput.class), any(ChatOptions.class))).thenReturn(CompletableFuture.failedFuture(unavailable()));
        when(secondary.chatAsync(any(ChatInput.class), any(ChatOptions.class))).thenReturn(CompletableFuture.completedFuture("ok"));

        var service = new FailoverAIService(primary, secondary);

        assertEquals("ok", service.chatAsync(INPUT, OPTIONS).get());
        verify(primary).chatAsync(any(ChatInput.class), any(ChatOptions.class));
        verify(secondary).chatAsync(any(ChatInput.class), any(ChatOptions.class));
    }

    @Test
    void metadataReflectsPrimaryService() {
        var primary = mock(AIService.class);
        var secondary = mock(AIService.class);
        when(primary.getProviderName()).thenReturn("Primary");

        var service = new FailoverAIService(primary, secondary);

        assertEquals("Primary", service.getProviderName());
        verifyNoInteractions(secondary);
    }

    @Test
    void streamFailsOverAndResetsResettableConsumer() throws Exception {
        var primary = mock(AIService.class);
        var secondary = mock(AIService.class);
        when(primary.chatStream(any(ChatInput.class), any(ChatOptions.class), any())).thenAnswer(invocation -> {
            Consumer<String> onToken = invocation.getArgument(2);
            onToken.accept("stale");
            return CompletableFuture.failedFuture(unavailable());
        });
        when(secondary.chatStream(any(ChatInput.class), any(ChatOptions.class), any())).thenAnswer(invocation -> {
            Consumer<String> onToken = invocation.getArgument(2);
            onToken.accept("fresh");
            return CompletableFuture.completedFuture(null);
        });

        var tokens = new StringBuilder();
        var resets = new ArrayList<Integer>();
        var consumer = ResettableConsumer.<String>of(tokens::append, (cause, attempt) -> {
            resets.add(attempt);
            tokens.setLength(0);
        });

        new FailoverAIService(primary, secondary).chatStream(INPUT, OPTIONS, consumer).get();

        assertEquals("fresh", tokens.toString()); // the primary's stale prefix was discarded on reset
        assertEquals(List.of(2), resets);
    }

    @Test
    void streamAbortsInsteadOfFailingOverWhenConsumerIsNotResettable() {
        var primary = mock(AIService.class);
        var secondary = mock(AIService.class);
        when(primary.chatStream(any(ChatInput.class), any(ChatOptions.class), any())).thenAnswer(invocation -> {
            Consumer<String> onToken = invocation.getArgument(2);
            onToken.accept("partial");
            return CompletableFuture.failedFuture(unavailable());
        });

        var tokens = new StringBuilder();
        var future = new FailoverAIService(primary, secondary).chatStream(INPUT, OPTIONS, tokens::append);

        var exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(chainContains(exception, AIStreamAbortedException.class));
        assertEquals("partial", tokens.toString());
        verifyNoInteractions(secondary);
    }

    /**
     * A {@link ResettableConsumer} must survive nesting: the outer decorator wraps the caller's consumer before handing it to the inner one, so the wrapper has
     * to stay resettable, otherwise the inner decorator sees a plain consumer and aborts a stream the caller is prepared to restart.
     */
    @Test
    void streamRestartsAcrossNestedInterceptingDecorators() throws Exception {
        var primary = mock(AIService.class);
        var secondary = mock(AIService.class);
        when(primary.chatStream(any(ChatInput.class), any(ChatOptions.class), any())).thenAnswer(invocation -> {
            Consumer<String> onToken = invocation.getArgument(2);
            onToken.accept("stale");
            return CompletableFuture.failedFuture(unavailable());
        });
        when(secondary.chatStream(any(ChatInput.class), any(ChatOptions.class), any())).thenAnswer(invocation -> {
            Consumer<String> onToken = invocation.getArgument(2);
            onToken.accept("fresh");
            return CompletableFuture.completedFuture(null);
        });

        var tokens = new StringBuilder();
        var resets = new ArrayList<Integer>();
        var consumer = ResettableConsumer.<String>of(tokens::append, (cause, attempt) -> {
            resets.add(attempt);
            tokens.setLength(0);
        });

        var nested = new RetryingAIService(new FailoverAIService(primary, secondary));
        nested.chatStream(INPUT, OPTIONS, consumer).get();

        assertEquals("fresh", tokens.toString());
        assertEquals(List.of(2), resets);
        verify(secondary).chatStream(any(ChatInput.class), any(ChatOptions.class), any());
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
