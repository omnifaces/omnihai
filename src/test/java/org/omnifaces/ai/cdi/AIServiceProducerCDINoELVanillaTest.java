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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.service.OpenAIService;

/**
 * A CDI container need not offer the EL binding, and a bean manager is then not EL aware in the first place: the type stating that it could be is missing
 * altogether rather than merely unimplemented. An attribute holding an expression names the dependency to add, and everything else is produced as usual.
 * <p>
 * This test runs in the {@code cdi-no-el-test} surefire execution, which keeps CDI on the runtime classpath and drops that binding. Its first test asserts it
 * really is gone, so that a change to that execution shows up as a failure here rather than as coverage which silently stops covering anything.
 *
 * @author Bauke Scholtz
 */
class AIServiceProducerCDINoELVanillaTest {

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key")
    private AIService withoutExpression;

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key", model = "#{config.model}")
    private AIService withELExpression;

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key", model = "${config:omnihai.test.model}")
    private AIService withMicroProfileConfigExpression;

    @Test
    void cdiIsPresentButItsElBindingIsNot() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("jakarta.enterprise.inject.spi.el.ELAwareBeanManager"));
        assertDoesNotThrow(() -> Class.forName("jakarta.enterprise.inject.spi.BeanManager"));
    }

    @Test
    void produce_withoutExpression_createsService() {
        assertInstanceOf(OpenAIService.class, produce("withoutExpression"));
    }

    @Test
    void produce_elExpressionWithoutTheElBinding_throws() {
        var exception = assertThrows(UnsupportedOperationException.class, () -> produce("withELExpression"));

        assertTrue(exception.getMessage().contains("jakarta.enterprise.cdi-el-api"), exception.getMessage());
    }

    /**
     * The resolver is picked by the syntax of the expression rather than by what the container offers, so a container without the EL binding still reads a
     * MicroProfile Config expression.
     */
    @Test
    void produce_microProfileConfigExpressionWithoutTheElBinding_isResolved() {
        assertEquals("gpt-4o-mini", produce("withMicroProfileConfigExpression").getModelName());
    }

    private static AIService produce(String fieldName) {
        var annotated = mock(Annotated.class);
        when(annotated.getAnnotation(AI.class)).thenReturn(getAnnotation(fieldName));

        var injectionPoint = mock(InjectionPoint.class);
        when(injectionPoint.getAnnotated()).thenReturn(annotated);

        return new AIServiceProducer().produce(injectionPoint, mock(BeanManager.class));
    }

    private static AI getAnnotation(String fieldName) {
        try {
            return AIServiceProducerCDINoELVanillaTest.class.getDeclaredField(fieldName).getAnnotation(AI.class);
        }
        catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

}
