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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * An annotation attribute is a literal unless it looks like an expression, and an attribute which was never set is a literal too rather than something to
 * inspect. An expression which cannot be evaluated is left as it stands, so that the reader sees what was written rather than a blank.
 */
class ExpressionResolversTest {

    private static final Pattern PATTERN = Pattern.compile("(\\$\\{)([^}]+)(\\})");

    @Test
    void looksLikeExpression_recognizesBothNotations() {
        assertTrue(ExpressionResolvers.looksLikeExpression("${config.apiKey}"));
        assertTrue(ExpressionResolvers.looksLikeExpression("#{config.apiKey}"));
        assertFalse(ExpressionResolvers.looksLikeExpression("plain-key"));
    }

    @Test
    void looksLikeExpression_unsetAttribute_isNotOne() {
        assertFalse(ExpressionResolvers.looksLikeExpression(null));
    }

    @Test
    void looksLikeMicroProfileConfigExpression_recognizesTheConfigPrefixAlone() {
        assertTrue(ExpressionResolvers.looksLikeMicroProfileConfigExpression("${config:test.key}"));
        assertFalse(ExpressionResolvers.looksLikeMicroProfileConfigExpression("${config.key}"));
    }

    @Test
    void looksLikeMicroProfileConfigExpression_unsetAttribute_isNotOne() {
        assertFalse(ExpressionResolvers.looksLikeMicroProfileConfigExpression(null));
    }

    @Test
    void resolve_replacesEveryExpressionWithWhatTheEvaluatorAnswers() {
        assertEquals("a-x-b-y", ExpressionResolvers.resolve(PATTERN, "a-${x}-b-${y}", expression -> expression));
    }

    /**
     * An expression which resolves to nothing yields nothing, rather than the literal word null.
     */
    @Test
    void resolve_evaluatorAnsweringNothing_leavesAnEmptyString() {
        assertEquals("a--b", ExpressionResolvers.resolve(PATTERN, "a-${x}-b", expression -> null));
    }

    /**
     * A failing evaluation is not an error the caller can act on, so the expression stays as it was written and reaches the reader intact.
     */
    @Test
    void resolve_failingEvaluator_leavesTheExpressionAsItWasWritten() {
        assertEquals("a-${x}-b", ExpressionResolvers.resolve(PATTERN, "a-${x}-b", expression -> {
            throw new IllegalStateException("cannot evaluate");
        }));
    }

    @Test
    void resolve_valueWithoutAnyExpression_isLeftAsItIs() {
        assertEquals("plain-key", ExpressionResolvers.resolve(PATTERN, "plain-key", expression -> "resolved"));
    }

}
