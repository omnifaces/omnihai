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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIProvider.ANTHROPIC;
import static org.omnifaces.ai.AIProvider.CUSTOM;
import static org.omnifaces.ai.AIProvider.OPENAI;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.service.AnthropicAIService;
import org.omnifaces.ai.service.ManagedBeans;
import org.omnifaces.ai.service.OpenAIService;
import org.omnifaces.ai.service.RetryingAIService;
import org.omnifaces.ai.service.ToolCallingAIService;
import org.omnifaces.ai.tool.AITool;
import org.omnifaces.ai.tool.AIToolParam;
import org.omnifaces.ai.tool.ToolRegistry;

/**
 * The {@link AI} qualifier as a real injection point in a running CDI container: what each attribute configures, which decorator it wraps the service in, and
 * which tool bean it reaches. This runs in a fork of its own, as booting a container changes what the library picks for its executor.
 */
@ExtendWith(WeldJunit5Extension.class)
class AIServiceProducerCDIContainerTest {

    @WeldSetup
    WeldInitiator weld = WeldInitiator.from(
        AIServiceProducer.class, ExpressionResolvers.class, InjectedServices.class, OrderTools.class, DependentTools.class,
        WithDependentTool.class, WithCustomProvider.class, WithoutAnyAttempt.class, ManagedBeans.executorServiceManager()
    ).activate(ApplicationScoped.class).build();

    @Inject
    private InjectedServices services;

    // =================================================================================================================
    // What the attributes configure
    // =================================================================================================================

    @Test
    void inject_qualifierWithoutAttributes_producesTheDefaultProvider() {
        assertInstanceOf(OpenAIService.class, services.plain);
    }

    @Test
    void inject_provider_producesTheServiceOfThatProvider() {
        assertInstanceOf(AnthropicAIService.class, services.anthropic);
    }

    @Test
    void inject_model_configuresTheService() {
        assertEquals("gpt-4o-mini", services.withModel.getModelName());
    }

    /**
     * An attribute holding a MicroProfile Config expression is read from the configuration rather than taken as the value, which is what an application states
     * a key with rather than the key itself.
     */
    @Test
    void inject_microProfileConfigExpression_isResolvedFromTheConfiguration() {
        assertEquals("gpt-4o-mini", services.withConfigExpression.getModelName());
    }

    /**
     * A configuration which is equal is served the same instance, so an application injecting the same service in a hundred beans holds one.
     */
    @Test
    void inject_equalConfiguration_isServedTheSameInstance() {
        assertEquals(services.plain, services.plainAgain);
    }

    @Test
    void inject_differentConfiguration_isServedItsOwnInstance() {
        assertNotSame(services.plain, services.withModel);
    }

    // =================================================================================================================
    // Which decorator the attributes wrap the service in
    // =================================================================================================================

    @Test
    void inject_maxAttempts_wrapsTheServiceInTheRetryingDecorator() {
        assertInstanceOf(RetryingAIService.class, services.retrying);
    }

    @Test
    void inject_tools_wrapsTheServiceInTheToolCallingDecorator() {
        assertInstanceOf(ToolCallingAIService.class, services.withTools);
        assertTrue(services.withTools.toString().contains("OrderTools_findOrder"), services.withTools.toString());
    }

    /**
     * A container hands out a proxy rather than the bean itself, and a proxy overrides every method it forwards without carrying the parameter names. The tools
     * are therefore scanned on the class which declares them rather than on the proxy standing in for it.
     */
    @Test
    void toolRegistry_beanHandedOverAsAContainerProxy_isScannedOnTheClassDeclaringIt() {
        var proxy = weld.select(OrderTools.class).get();

        assertNotSame(OrderTools.class, proxy.getClass(), "the container must hand out a proxy for this to prove anything");
        assertEquals(Set.of("OrderTools_findOrder"), ToolRegistry.newBuilder().add(proxy).build().getToolNames());
    }

    // =================================================================================================================
    // What the container refuses
    // =================================================================================================================

    /**
     * A tool pinned to the lifecycle of its injection point would observe neither its own scope nor its interceptors on a call the AI makes, so it is refused.
     */
    @Test
    void inject_dependentToolBean_namesTheToolClass() {
        var exception = assertThrows(Exception.class, () -> weld.select(WithDependentTool.class).get());

        assertTrue(rootCause(exception).getMessage().contains(DependentTools.class.getName()), rootCause(exception).getMessage());
    }

    /**
     * A custom provider is named by its service class rather than by the enum constant standing for "not one of the built-in ones".
     */
    @Test
    void inject_customProvider_saysToUseTheServiceClassInstead() {
        var exception = assertThrows(Exception.class, () -> weld.select(WithCustomProvider.class).get());

        assertTrue(rootCause(exception).getMessage().contains("serviceClass"), rootCause(exception).getMessage());
    }

    @Test
    void inject_maxAttemptsBelowOne_isRefused() {
        var exception = assertThrows(Exception.class, () -> weld.select(WithoutAnyAttempt.class).get());

        assertTrue(rootCause(exception).getMessage().contains("maxAttempts"), rootCause(exception).getMessage());
    }

    private static Throwable rootCause(Throwable throwable) {
        var current = throwable;

        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }

        return current;
    }

    // =================================================================================================================
    // The beans under test
    // =================================================================================================================

    @Dependent
    static class InjectedServices {

        @Inject
        @AI(apiKey = "test-api-key")
        AIService plain;

        @Inject
        @AI(apiKey = "test-api-key")
        AIService plainAgain;

        @Inject
        @AI(provider = ANTHROPIC, apiKey = "test-api-key")
        AIService anthropic;

        @Inject
        @AI(apiKey = "test-api-key", model = "gpt-4o-mini")
        AIService withModel;

        @Inject
        @AI(apiKey = "test-api-key", model = "${config:omnihai.test.model}")
        AIService withConfigExpression;

        @Inject
        @AI(apiKey = "test-api-key", maxAttempts = 3)
        AIService retrying;

        @Inject
        @AI(apiKey = "test-api-key", tools = OrderTools.class)
        AIService withTools;

    }

    @Dependent
    static class WithDependentTool {

        @Inject
        @AI(apiKey = "test-api-key", tools = DependentTools.class)
        AIService service;

    }

    @Dependent
    static class WithCustomProvider {

        @Inject
        @AI(provider = CUSTOM, apiKey = "test-api-key")
        AIService service;

    }

    @Dependent
    static class WithoutAnyAttempt {

        @Inject
        @AI(provider = OPENAI, apiKey = "test-api-key", maxAttempts = 0)
        AIService service;

    }

    @ApplicationScoped
    public static class OrderTools {

        @AITool("Looks up the status of an order")
        public String findOrder(@AIToolParam("The order id") String orderId) {
            return "order " + orderId + " is shipped";
        }

    }

    @Dependent
    public static class DependentTools {

        @AITool("Looks up the status of an order")
        public String findOrder(@AIToolParam("The order id") String orderId) {
            return "order " + orderId + " is shipped";
        }

    }

}
