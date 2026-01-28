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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.enterprise.inject.spi.el.ELAwareBeanManager;

import org.junit.jupiter.api.Test;

class ExpressionResolverTest {

    // =================================================================================================================
    // Single expression resolution
    // =================================================================================================================

    @Test
    void resolveEL_singleHashExpression() {
        var beans = Map.of("config", Map.of("apiKey", "my-secret-key"));
        assertResolved("#{config.apiKey}", beans, "my-secret-key");
    }

    @Test
    void resolveEL_singleDollarExpression() {
        var beans = Map.of("config", Map.of("apiKey", "my-secret-key"));
        assertResolved("${config.apiKey}", beans, "my-secret-key");
    }

    @Test
    void resolveEL_expressionWithSurroundingText() {
        var beans = Map.of("config", Map.of("apiKey", "my-key"));
        assertResolved("prefix-#{config.apiKey}-suffix", beans, "prefix-my-key-suffix");
    }

    // =================================================================================================================
    // Multiple expression resolution
    // =================================================================================================================

    @Test
    void resolveEL_multipleHashExpressions() {
        var beans = Map.of("env", Map.<String, Object>of("host", "localhost", "port", 8443));
        assertResolved("https://#{env.host}:#{env.port}/api", beans, "https://localhost:8443/api");
    }

    @Test
    void resolveEL_mixedHashAndDollarExpressions() {
        var beans = Map.of("env", Map.<String, Object>of("host", "localhost", "port", 8443));
        assertResolved("https://#{env.host}:${env.port}/api", beans, "https://localhost:8443/api");
    }

    @Test
    void resolveEL_adjacentExpressions() {
        var beans = Map.of("a", Map.of("b", "X"), "c", Map.of("d", "Y"));
        assertResolved("#{a.b}#{c.d}", beans, "XY");
    }

    // =================================================================================================================
    // Null and empty results
    // =================================================================================================================

    @Test
    void resolveEL_nullResult_becomesEmptyString() {
        // Empty inner map — MapELResolver returns null for missing key.
        var beans = Map.of("config", Map.of());
        assertResolved("#{config.apiKey}", beans, "");
    }

    @Test
    void resolveEL_nullResultWithSurroundingText() {
        var beans = Map.of("config", Map.of());
        assertResolved("prefix-#{config.value}-suffix", beans, "prefix--suffix");
    }

    @Test
    void resolveEL_emptyStringResult() {
        var beans = Map.of("config", Map.of("apiKey", ""));
        assertResolved("#{config.apiKey}", beans, "");
    }

    // =================================================================================================================
    // Expression evaluation failure
    // =================================================================================================================

    @Test
    void resolveEL_evalThrows_preservesOriginalExpression() {
        var beanManager = mockBeanManager(Map.of("broken", new FailingBean()));
        assertEquals("#{broken.value}", ELExpressionResolver.resolveEL(beanManager, "#{broken.value}"));
    }

    @Test
    void resolveEL_mixedSuccessAndFailure_resolvesSuccessfulAndPreservesFailed() {
        var beans = Map.<String, Object>of("good", Map.of("expr", "resolved"), "bad", new FailingBean());
        var beanManager = mockBeanManager(beans);
        assertEquals("a resolved b #{bad.value} c", ELExpressionResolver.resolveEL(beanManager, "a #{good.expr} b #{bad.value} c"));
    }

    @Test
    void resolveEL_dollarExpressionEvalThrows_preservesOriginal() {
        var beanManager = mockBeanManager(Map.of("broken", new FailingBean()));
        assertEquals("${broken.value}", ELExpressionResolver.resolveEL(beanManager, "${broken.value}"));
    }

    // =================================================================================================================
    // No expressions
    // =================================================================================================================

    @Test
    void resolveEL_noExpressions_returnsAsIs() {
        assertPassedThrough("plain text");
    }

    @Test
    void resolveEL_emptyBraces_notMatchedByPattern() {
        // #{} has empty body — regex [^}]+ requires at least one char, so not matched.
        assertPassedThrough("#{}");
    }

    // =================================================================================================================
    // Half-baked / malformed expressions — should pass through without errors
    // =================================================================================================================

    @Test
    void resolveEL_unclosedHashExpression_passesThrough() {
        assertPassedThrough("#{unclosed.expression");
    }

    @Test
    void resolveEL_unclosedDollarExpression_passesThrough() {
        assertPassedThrough("${also.unclosed");
    }

    @Test
    void resolveEL_closingBraceOnly_passesThrough() {
        assertPassedThrough("just a } here");
    }

    @Test
    void resolveEL_unclosedWithSurroundingText_passesThrough() {
        assertPassedThrough("before #{no.end and after");
    }

    @Test
    void resolveEL_closingBeforeOpening_passesThrough() {
        assertPassedThrough("} reversed #{ order");
    }

    @Test
    void resolveEL_hashAndBraceSeparatedBySpace_passesThrough() {
        assertPassedThrough("# {not.an.expression}");
    }

    @Test
    void resolveEL_dollarAndBraceSeparatedBySpace_passesThrough() {
        assertPassedThrough("$ {not.an.expression}");
    }

    @Test
    void resolveEL_onlyHashBrace_passesThrough() {
        assertPassedThrough("#{");
    }

    @Test
    void resolveEL_onlyDollarBrace_passesThrough() {
        assertPassedThrough("${");
    }

    @Test
    void resolveEL_onlyClosingBrace_passesThrough() {
        assertPassedThrough("}");
    }

    // =================================================================================================================
    // Whitespace handling in expressions
    // =================================================================================================================

    @Test
    void resolveEL_expressionWithWhitespace_isStrippedBeforeEval() {
        var beans = Map.of("config", Map.of("apiKey", "my-key"));
        assertResolved("#{ config.apiKey }", beans, "my-key");
    }

    @Test
    void resolveEL_expressionWithLeadingWhitespace() {
        var beans = Map.of("config", Map.of("apiKey", "my-key"));
        assertResolved("#{  config.apiKey}", beans, "my-key");
    }

    // =================================================================================================================
    // Special characters in result
    // =================================================================================================================

    @Test
    void resolveEL_resultContainsDollarSign() {
        var beans = Map.of("config", Map.of("value", "$100"));
        assertResolved("#{config.value}", beans, "$100");
    }

    @Test
    void resolveEL_resultContainsBackslash() {
        var beans = Map.of("config", Map.of("path", "C:\\Users\\test"));
        assertResolved("#{config.path}", beans, "C:\\Users\\test");
    }

    @Test
    void resolveEL_resultContainsCurlyBraces() {
        var beans = Map.of("config", Map.of("json", "{\"key\":\"val\"}"));
        assertResolved("#{config.json}", beans, "{\"key\":\"val\"}");
    }

    // =================================================================================================================
    // Non-string result types
    // =================================================================================================================

    @Test
    void resolveEL_integerResult_convertedViaToString() {
        var beans = Map.of("config", Map.<String, Object>of("port", 8080));
        assertResolved("#{config.port}", beans, "8080");
    }

    @Test
    void resolveEL_booleanResult_convertedViaToString() {
        var beans = Map.of("config", Map.<String, Object>of("enabled", true));
        assertResolved("#{config.enabled}", beans, "true");
    }

    // =================================================================================================================
    // Helper methods
    // =================================================================================================================

    /**
     * Asserts that the input is resolved to the expected output using real EL evaluation
     * with the given beans registered in the EL context.
     */
    private static void assertResolved(String input, Map<String, ?> beans, String expectedOutput) {
        var beanManager = mockBeanManager(beans);
        assertEquals(expectedOutput, ELExpressionResolver.resolveEL(beanManager, input));
    }

    /**
     * Asserts that the input passes through resolveEL unchanged (no expressions matched).
     */
    private static void assertPassedThrough(String input) {
        var beanManager = mockBeanManager(Map.of());
        assertEquals(input, ELExpressionResolver.resolveEL(beanManager, input));
    }

    private static ELAwareBeanManager mockBeanManager(Map<String, ?> beans) {
        var beanManager = mock(ELAwareBeanManager.class);
        when(beanManager.getELResolver()).thenReturn(new TestBeanELResolver(beans));
        return beanManager;
    }

    // =================================================================================================================
    // Test support classes
    // =================================================================================================================

    /**
     * EL resolver that resolves top-level bean names from a map.
     * Nested property access (e.g. {@code config.apiKey}) is handled by the built-in {@code MapELResolver}
     * when the bean value is a {@code Map}, or by {@code BeanELResolver} when it is a POJO.
     */
    private static class TestBeanELResolver extends ELResolver {

        private final Map<String, ?> beans;

        TestBeanELResolver(Map<String, ?> beans) {
            this.beans = beans;
        }

        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            if (base == null && property != null && beans.containsKey(property.toString())) {
                context.setPropertyResolved(true);
                return beans.get(property.toString());
            }

            return null;
        }

        @Override public Class<?> getType(ELContext context, Object base, Object property) { return null; }
        @Override public void setValue(ELContext context, Object base, Object property, Object value) {}
        @Override public boolean isReadOnly(ELContext context, Object base, Object property) { return true; }
        @Override public Class<?> getCommonPropertyType(ELContext context, Object base) { return null; }
    }

    /** Bean whose property access throws, for testing error handling in ELExpressionResolver. */
    public static class FailingBean {
        public Object getValue() {
            throw new RuntimeException("EL evaluation failed");
        }
    }
}
