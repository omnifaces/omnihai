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
package org.omnifaces.ai.cdi;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.service.RetryingAIService;

class AIServiceProducerTest {

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key")
    private AIService withDefaultMaxAttempts;

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key", maxAttempts = 1)
    private AIService withSingleAttempt;

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key", maxAttempts = 5)
    private AIService withMultipleAttempts;

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key", maxAttempts = 0)
    private AIService withZeroAttempts;

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key", maxAttempts = -1)
    private AIService withNegativeAttempts;

    /**
     * The default must leave the produced service undecorated, so that existing injection points keep their exact behavior.
     */
    @Test
    void produce_withDefaultMaxAttempts_isNotDecorated() {
        assertFalse(produce("withDefaultMaxAttempts") instanceof RetryingAIService);
    }

    /**
     * {@code maxAttempts} counts the initial attempt plus retries, as on {@link RetryingAIService.Builder#maxAttempts(int)}, so 1 means a single attempt and
     * hence no retrying.
     */
    @Test
    void produce_withSingleAttempt_isNotDecorated() {
        assertFalse(produce("withSingleAttempt") instanceof RetryingAIService);
    }

    @Test
    void produce_withMultipleAttempts_isDecorated() {
        assertInstanceOf(RetryingAIService.class, produce("withMultipleAttempts"));
    }

    /**
     * The decorator must wrap the cached service rather than a fresh one, so that two injection points sharing a config also share the underlying service.
     */
    @Test
    void produce_withMultipleAttempts_wrapsTheCachedService() {
        var producer = new AIServiceProducer();
        var undecorated = produce(producer, "withDefaultMaxAttempts");
        var decorated = (RetryingAIService) produce(producer, "withMultipleAttempts");

        assertSame(undecorated, decorated.getWrapped());
    }

    /**
     * An out of range {@code maxAttempts} must fail at injection time rather than silently produce a service that never retries.
     */
    @Test
    void produce_withZeroAttempts_throws() {
        assertThrows(IllegalArgumentException.class, () -> produce("withZeroAttempts"));
    }

    @Test
    void produce_withNegativeAttempts_throws() {
        assertThrows(IllegalArgumentException.class, () -> produce("withNegativeAttempts"));
    }

    private static AIService produce(String fieldName) {
        return produce(new AIServiceProducer(), fieldName);
    }

    private static AIService produce(AIServiceProducer producer, String fieldName) {
        var annotated = mock(Annotated.class);
        when(annotated.getAnnotation(AI.class)).thenReturn(getAnnotation(fieldName));

        var injectionPoint = mock(InjectionPoint.class);
        when(injectionPoint.getAnnotated()).thenReturn(annotated);

        return producer.produce(injectionPoint, mock(BeanManager.class));
    }

    private static AI getAnnotation(String fieldName) {
        try {
            return AIServiceProducerTest.class.getDeclaredField(fieldName).getAnnotation(AI.class);
        }
        catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
