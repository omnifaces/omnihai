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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.el.ELAwareBeanManager;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.service.OpenAIService;

/**
 * A bare CDI container offers neither an EL implementation nor MicroProfile Config, so an {@link AI} attribute holding an expression has to name the missing
 * dependency instead of failing somewhere further down.
 * <p>
 * This test runs in the {@code cdi-only-test} surefire execution, which keeps CDI on the runtime classpath and drops those two implementations. Its first test
 * asserts they really are gone, so that a change to that execution shows up as a failure here rather than as coverage which silently stops covering anything.
 *
 * @author Bauke Scholtz
 */
class AIServiceProducerCDIOnlyVanillaTest {

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key")
    private AIService withoutExpression;

    @AI(provider = AIProvider.OPENAI, apiKey = "${config:test.api.key}")
    private AIService withMicroProfileConfigExpression;

    @AI(provider = AIProvider.OPENAI, apiKey = "#{config.apiKey}")
    private AIService withELExpression;

    @Test
    void optionalImplementations_areOffTheClasspath() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.eclipse.microprofile.config.ConfigProvider"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.glassfish.expressly.ExpressionFactoryImpl"));
    }

    @Test
    void produce_withoutExpression_createsService() {
        assertInstanceOf(OpenAIService.class, produce("withoutExpression", mock(BeanManager.class)));
    }

    @Test
    void produce_microProfileConfigExpressionWithoutMicroProfileConfig_throws() {
        var exception = assertThrows(
            UnsupportedOperationException.class, () -> produce("withMicroProfileConfigExpression", mock(BeanManager.class))
        );
        assertTrue(exception.getMessage().contains("microprofile-config-api"));
    }

    @Test
    void produce_elExpressionWithoutELImplementation_throws() {
        var exception = assertThrows(UnsupportedOperationException.class, () -> produce("withELExpression", mock(ELAwareBeanManager.class)));
        assertTrue(exception.getMessage().contains("jakarta.el-api"));
    }

    private static AIService produce(String fieldName, BeanManager beanManager) {
        var annotated = mock(Annotated.class);
        when(annotated.getAnnotation(AI.class)).thenReturn(getAnnotation(fieldName));

        var injectionPoint = mock(InjectionPoint.class);
        when(injectionPoint.getAnnotated()).thenReturn(annotated);

        return new AIServiceProducer().produce(injectionPoint, beanManager);
    }

    private static AI getAnnotation(String fieldName) {
        try {
            return AIServiceProducerCDIOnlyVanillaTest.class.getDeclaredField(fieldName).getAnnotation(AI.class);
        }
        catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

}
