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
import static org.omnifaces.ai.cdi.MicroProfileConfigExpressionResolver.resolveMicroProfileConfigExpression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MicroProfileConfigExpressionResolverTest {

    @BeforeEach
    void setUp() {
        System.setProperty("test.api.key", "my-secret-key");
        System.setProperty("test.host", "localhost");
        System.setProperty("test.port", "8443");
        System.setProperty("test.empty", "");
        System.setProperty("test.special.dollar", "$100");
        System.setProperty("test.special.backslash", "C:\\Users\\test");
        System.setProperty("test.special.braces", "{\"key\":\"val\"}");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("test.api.key");
        System.clearProperty("test.host");
        System.clearProperty("test.port");
        System.clearProperty("test.empty");
        System.clearProperty("test.special.dollar");
        System.clearProperty("test.special.backslash");
        System.clearProperty("test.special.braces");
    }

    // =================================================================================================================
    // Single expression resolution
    // =================================================================================================================

    @Test
    void resolveConfig_singleExpression() {
        assertEquals("my-secret-key", resolveMicroProfileConfigExpression("${config:test.api.key}"));
    }

    @Test
    void resolveConfig_expressionWithSurroundingText() {
        assertEquals("prefix-my-secret-key-suffix", resolveMicroProfileConfigExpression("prefix-${config:test.api.key}-suffix"));
    }

    // =================================================================================================================
    // Multiple expression resolution
    // =================================================================================================================

    @Test
    void resolveConfig_multipleExpressions() {
        assertEquals("https://localhost:8443/api", resolveMicroProfileConfigExpression("https://${config:test.host}:${config:test.port}/api"));
    }

    @Test
    void resolveConfig_adjacentExpressions() {
        assertEquals("localhost8443", resolveMicroProfileConfigExpression("${config:test.host}${config:test.port}"));
    }

    // =================================================================================================================
    // Missing and empty config values
    // =================================================================================================================

    @Test
    void resolveConfig_missingKey_becomesEmptyString() {
        assertEquals("", resolveMicroProfileConfigExpression("${config:nonexistent.key}"));
    }

    @Test
    void resolveConfig_missingKeyWithSurroundingText() {
        assertEquals("prefix--suffix", resolveMicroProfileConfigExpression("prefix-${config:nonexistent.key}-suffix"));
    }

    @Test
    void resolveConfig_emptyValue() {
        assertEquals("", resolveMicroProfileConfigExpression("${config:test.empty}"));
    }

    // =================================================================================================================
    // No expressions
    // =================================================================================================================

    @Test
    void resolveConfig_noExpressions_returnsAsIs() {
        assertEquals("plain text", resolveMicroProfileConfigExpression("plain text"));
    }

    @Test
    void resolveConfig_emptyBraces_notMatchedByPattern() {
        // ${config:} has empty body — regex [^}]+ requires at least one char, so not matched.
        assertEquals("${config:}", resolveMicroProfileConfigExpression("${config:}"));
    }

    @Test
    void resolveConfig_differentExpressionSyntax_notMatched() {
        // #{...} and ${...} without config: prefix are not matched
        assertEquals("#{some.value}", resolveMicroProfileConfigExpression("#{some.value}"));
        assertEquals("${some.value}", resolveMicroProfileConfigExpression("${some.value}"));
    }

    // =================================================================================================================
    // Half-baked / malformed expressions — should pass through without errors
    // =================================================================================================================

    @Test
    void resolveConfig_unclosedExpression_passesThrough() {
        assertEquals("${config:unclosed.key", resolveMicroProfileConfigExpression("${config:unclosed.key"));
    }

    @Test
    void resolveConfig_closingBraceOnly_passesThrough() {
        assertEquals("just a } here", resolveMicroProfileConfigExpression("just a } here"));
    }

    @Test
    void resolveConfig_unclosedWithSurroundingText_passesThrough() {
        assertEquals("before ${config:no.end and after", resolveMicroProfileConfigExpression("before ${config:no.end and after"));
    }

    @Test
    void resolveConfig_onlyPrefix_passesThrough() {
        assertEquals("${config:", resolveMicroProfileConfigExpression("${config:"));
    }

    // =================================================================================================================
    // Whitespace handling in expressions
    // =================================================================================================================

    @Test
    void resolveConfig_expressionWithWhitespace_isStrippedBeforeEval() {
        assertEquals("my-secret-key", resolveMicroProfileConfigExpression("${config: test.api.key }"));
    }

    @Test
    void resolveConfig_expressionWithLeadingWhitespace() {
        assertEquals("my-secret-key", resolveMicroProfileConfigExpression("${config:  test.api.key}"));
    }

    // =================================================================================================================
    // Special characters in result
    // =================================================================================================================

    @Test
    void resolveConfig_resultContainsDollarSign() {
        assertEquals("$100", resolveMicroProfileConfigExpression("${config:test.special.dollar}"));
    }

    @Test
    void resolveConfig_resultContainsBackslash() {
        assertEquals("C:\\Users\\test", resolveMicroProfileConfigExpression("${config:test.special.backslash}"));
    }

    @Test
    void resolveConfig_resultContainsCurlyBraces() {
        assertEquals("{\"key\":\"val\"}", resolveMicroProfileConfigExpression("${config:test.special.braces}"));
    }
}
