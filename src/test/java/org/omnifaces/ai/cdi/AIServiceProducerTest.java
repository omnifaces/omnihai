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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.el.ELAwareBeanManager;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.modality.DefaultAIAudioHandler;
import org.omnifaces.ai.modality.DefaultAIImageHandler;
import org.omnifaces.ai.modality.DefaultAITextHandler;
import org.omnifaces.ai.modality.DefaultAIVideoHandler;
import org.omnifaces.ai.service.OpenAIService;
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

    @AI(provider = AIProvider.OPENAI, apiKey = "#{config.apiKey}")
    private AIService withELExpression;

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key", model = "#{config.model}")
    private AIService withELExpressionInTheModel;

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key", model = "${config:test.model}")
    private AIService withMicroProfileConfigExpressionInTheModel;

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key", model = "#{config.model")
    private AIService withMalformedExpression;

    @AI(provider = AIProvider.CUSTOM, apiKey = "test-key")
    private AIService withCustomProvider;

    @AI(
        serviceClass = OpenAIService.class, apiKey = "test-key", endpoint = "https://example.org/v1/", model = "gpt-4o", textHandler = DefaultAITextHandler.class, imageHandler = DefaultAIImageHandler.class, audioHandler = DefaultAIAudioHandler.class, videoHandler = DefaultAIVideoHandler.class
    )
    private AIService withServiceClass;

    /**
     * The default must leave the produced service undecorated, so that existing injection points keep their exact behavior.
     */
    @Test
    void produce_withDefaultMaxAttempts_isNotDecorated() {
        assertFalse(produce("withDefaultMaxAttempts") instanceof RetryingAIService);
    }

    /**
     * {@code maxAttempts} counts the initial attempt plus retries, as on {@link RetryingAIService.Builder_maxAttempts_int}, so 1 means a single attempt and
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

    // =================================================================================================================
    // Tools
    // =================================================================================================================

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

        assertEquals(Set.of("OrderTools_findOrder", "OrderTools_refund"), Set.copyOf(agent.getRegistry().getToolNames()));
    }

    @Test
    void produce_withToolGroup_narrowsTheRegistry() {
        var agent = assertInstanceOf(ToolCallingAIService.class, produce("withToolGroup"));

        assertEquals(Set.of("OrderTools_findOrder"), Set.copyOf(agent.getRegistry().getToolNames()));
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

        var producer = new AIServiceProducer();

        assertThrows(IllegalArgumentException.class, () -> producer.produce(injectionPoint, beanManager));
    }

    /**
     * A dependent tool bean would be pinned to the lifecycle of the injection point instead of observing its own scope on every call.
     */
    @Test
    void produce_withDependentToolBean_throws() {
        var producer = new AIServiceProducer();

        assertThrows(IllegalArgumentException.class, () -> produce(producer, "withTools", Dependent.class));
    }

    /**
     * EL resolution reads the expression through the bean manager, so a runtime whose bean manager is not EL aware has to say which dependency is missing
     * rather than fail somewhere further down.
     */
    @Test
    void produce_elExpressionWithBeanManagerWhichIsNotELAware_throws() {
        var exception = assertThrows(UnsupportedOperationException.class, () -> produce("withELExpression"));
        assertTrue(exception.getMessage().contains("jakarta.enterprise.cdi-el-api"));
    }

    // =================================================================================================================
    // Expression resolution
    // =================================================================================================================

    /**
     * An attribute may name a bean rather than a literal, so that a key or a model can be configured where the application configures everything else.
     */
    @Test
    void produce_elExpression_isResolvedThroughTheBeanManager() {
        var beanManager = mock(ELAwareBeanManager.class);
        when(beanManager.getELResolver()).thenReturn(new StubBeanELResolver(Map.of("config", Map.of("model", "gpt-4o-from-el"))));

        assertEquals("gpt-4o-from-el", produce("withELExpressionInTheModel", beanManager).getModelName());
    }

    @Test
    void produce_microProfileConfigExpression_isResolvedFromTheConfiguration() {
        System.setProperty("test.model", "gpt-4o-from-config");

        try {
            assertEquals("gpt-4o-from-config", produce("withMicroProfileConfigExpressionInTheModel", mock(BeanManager.class)).getModelName());
        }
        finally {
            System.clearProperty("test.model");
        }
    }

    /**
     * An expression missing its closing brace is a typo rather than a literal, and says so instead of reaching the AI provider as a model name.
     */
    @Test
    void produce_malformedExpression_throws() {
        var beanManager = mock(BeanManager.class);

        var exception = assertThrows(IllegalArgumentException.class, () -> produce("withMalformedExpression", beanManager));
        assertTrue(exception.getMessage().contains("trailing '}'"));
    }

    // =================================================================================================================
    // Provider selection
    // =================================================================================================================

    /**
     * A custom service has no provider to name it by, so it is declared by its class instead.
     */
    @Test
    void produce_customProvider_throws() {
        var beanManager = mock(BeanManager.class);

        assertThrows(IllegalArgumentException.class, () -> produce("withCustomProvider", beanManager));
    }

    /**
     * A service declared by its class carries no provider defaults, so it names its own endpoint and the handler for each modality it serves.
     */
    @Test
    void produce_serviceClass_isPreferredOverTheProviderAndCarriesItsOwnHandlers() {
        assertInstanceOf(OpenAIService.class, produce("withServiceClass", mock(BeanManager.class)));
    }

    private static AIService produce(String fieldName, BeanManager beanManager) {
        var annotated = mock(Annotated.class);
        when(annotated.getAnnotation(AI.class)).thenReturn(getAnnotation(fieldName));

        var injectionPoint = mock(InjectionPoint.class);
        when(injectionPoint.getAnnotated()).thenReturn(annotated);

        return new AIServiceProducer().produce(injectionPoint, beanManager);
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
