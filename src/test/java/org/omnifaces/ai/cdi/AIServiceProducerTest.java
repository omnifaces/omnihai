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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.service.RetryingAIService;
import org.omnifaces.ai.service.ToolCallingAIService;
import org.omnifaces.ai.tool.AITool;
import org.omnifaces.ai.tool.AIToolGroup;
import org.omnifaces.ai.tool.AIToolParam;

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

    // Tools ------------------------------------------------------------------------------------------------------------

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key", tools = OrderTools.class)
    private AIService withTools;

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key", tools = OrderTools.class, toolGroup = ReadOnly.class)
    private AIService withToolGroup;

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key", tools = OrderTools.class, maxAttempts = 3)
    private AIService withToolsAndRetry;

    @AIToolGroup
    @Retention(RUNTIME)
    @Target(METHOD)
    @interface ReadOnly {
        //
    }

    public static class OrderTools {

        @ReadOnly
        @AITool("Looks up a single order by id")
        public String findOrder(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
            return "order " + orderId;
        }

        @AITool("Issues a refund for an order")
        public String refund(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
            return "refunded " + orderId;
        }

    }

    @Test
    void produce_withoutTools_isNotDecorated() {
        assertFalse(produce("withDefaultMaxAttempts") instanceof ToolCallingAIService);
    }

    @Test
    void produce_withTools_isDecoratedWithTheirRegistry() {
        var agent = assertInstanceOf(ToolCallingAIService.class, produce("withTools"));

        assertEquals(Set.of("FIND_ORDER", "REFUND"), Set.copyOf(agent.getRegistry().getToolNames()));
    }

    @Test
    void produce_withToolGroup_narrowsTheRegistry() {
        var agent = assertInstanceOf(ToolCallingAIService.class, produce("withToolGroup"));

        assertEquals(Set.of("FIND_ORDER"), Set.copyOf(agent.getRegistry().getToolNames()));
    }

    /**
     * Tool calling must sit outside retrying, so that a retry re-attempts a single provider call rather than replaying the loop and every side effect it
     * already caused.
     */
    @Test
    void produce_withToolsAndRetry_composesToolCallingAroundRetrying() {
        var agent = assertInstanceOf(ToolCallingAIService.class, produce("withToolsAndRetry"));

        assertInstanceOf(RetryingAIService.class, agent.getWrapped());
    }

    /**
     * A tool class which is no bean at all is a wiring error, not something to discover when the AI first reaches for it.
     */
    @Test
    void produce_withToolClassWhichIsNoBean_throws() {
        var annotated = mock(Annotated.class);
        when(annotated.getAnnotation(AI.class)).thenReturn(getAnnotation("withTools"));

        var injectionPoint = mock(InjectionPoint.class);
        when(injectionPoint.getAnnotated()).thenReturn(annotated);

        var beanManager = mock(BeanManager.class);
        when(beanManager.resolve(any())).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> new AIServiceProducer().produce(injectionPoint, beanManager));
    }

    /**
     * A dependent tool bean would be pinned to the lifecycle of the injection point instead of observing its own scope on every call.
     */
    @Test
    void produce_withDependentToolBean_throws() {
        assertThrows(IllegalArgumentException.class, () -> produce(new AIServiceProducer(), "withTools", Dependent.class));
    }

    private static AIService produce(String fieldName) {
        return produce(new AIServiceProducer(), fieldName);
    }

    private static AIService produce(AIServiceProducer producer, String fieldName) {
        return produce(producer, fieldName, ApplicationScoped.class);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static AIService produce(AIServiceProducer producer, String fieldName, Class<? extends Annotation> toolBeanScope) {
        var annotated = mock(Annotated.class);
        when(annotated.getAnnotation(AI.class)).thenReturn(getAnnotation(fieldName));

        var injectionPoint = mock(InjectionPoint.class);
        when(injectionPoint.getAnnotated()).thenReturn(annotated);

        var bean = mock(Bean.class);
        when(bean.getScope()).thenReturn((Class) toolBeanScope);

        var beanManager = mock(BeanManager.class);
        when(beanManager.resolve(any())).thenReturn(bean);
        when(beanManager.getReference(any(), any(), any())).thenReturn(new OrderTools());

        return producer.produce(injectionPoint, beanManager);
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
