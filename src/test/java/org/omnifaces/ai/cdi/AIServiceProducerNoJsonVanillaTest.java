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

/**
 * JSON-P is a provided dependency, so a runtime without an implementation has to be told which dependency to add rather than fail on a missing provider deep
 * inside the first call.
 * <p>
 * This test runs in the {@code no-json-test} surefire execution, which drops JSON-P from the runtime classpath. Its first test asserts it really is gone, so
 * that a change to that execution shows up as a failure here rather than as coverage which silently stops covering anything.
 *
 * @author Bauke Scholtz
 */
class AIServiceProducerNoJsonVanillaTest {

    @AI(provider = AIProvider.OPENAI, apiKey = "test-key")
    private AIService withoutExpression;

    @Test
    void json_isOffTheClasspath() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("jakarta.json.spi.JsonProvider"));
    }

    @Test
    void produce_withoutJson_throws() {
        var exception = assertThrows(UnsupportedOperationException.class, AIServiceProducerNoJsonVanillaTest::produce);
        assertTrue(exception.getMessage().contains("jakarta.json-api"));
    }

    private static AIService produce() {
        var annotated = mock(Annotated.class);
        when(annotated.getAnnotation(AI.class)).thenReturn(getAnnotation());

        var injectionPoint = mock(InjectionPoint.class);
        when(injectionPoint.getAnnotated()).thenReturn(annotated);

        return new AIServiceProducer().produce(injectionPoint, mock(BeanManager.class));
    }

    private static AI getAnnotation() {
        try {
            return AIServiceProducerNoJsonVanillaTest.class.getDeclaredField("withoutExpression").getAnnotation(AI.class);
        }
        catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

}
