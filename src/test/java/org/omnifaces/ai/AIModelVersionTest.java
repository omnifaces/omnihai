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
package org.omnifaces.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AIModelVersionTest {

    // =================================================================================================================
    // Test factory method of(String fullModelName) with real model names from AIProvider
    // =================================================================================================================

    @Test
    void ofFullModelName_gptMini() {
        var version = AIModelVersion.of("gpt-5-mini");
        assertEquals("gpt", version.modelName());
        assertEquals(5, version.majorVersion());
        assertEquals(0, version.minorVersion());
    }

    @Test
    void ofFullModelName_claudeSonnet() {
        var version = AIModelVersion.of("claude-sonnet-4-5-20250929");
        assertEquals("claude-sonnet", version.modelName());
        assertEquals(4, version.majorVersion());
        assertEquals(5, version.minorVersion());
    }

    @Test
    void ofFullModelName_geminiFlash() {
        var version = AIModelVersion.of("gemini-2.5-flash");
        assertEquals("gemini", version.modelName());
        assertEquals(2, version.majorVersion());
        assertEquals(5, version.minorVersion());
    }

    @Test
    void ofFullModelName_grokFastReasoning() {
        var version = AIModelVersion.of("grok-4-1-fast-reasoning");
        assertEquals("grok", version.modelName());
        assertEquals(4, version.majorVersion());
        assertEquals(1, version.minorVersion());
    }

    @Test
    void ofFullModelName_llamaScout() {
        var version = AIModelVersion.of("Llama-4-Scout-17B-16E-Instruct-FP8");
        assertEquals("Llama", version.modelName());
        assertEquals(4, version.majorVersion());
        assertEquals(0, version.minorVersion());
    }

    @Test
    void ofFullModelName_googleGemma() {
        var version = AIModelVersion.of("google/gemma-3-27b-it:free");
        assertEquals("google/gemma", version.modelName());
        assertEquals(3, version.majorVersion());
        assertEquals(27, version.minorVersion());
    }

    @Test
    void ofFullModelName_llama32() {
        var version = AIModelVersion.of("llama3.2");
        assertEquals("llama", version.modelName());
        assertEquals(3, version.majorVersion());
        assertEquals(2, version.minorVersion());
    }

    @Test
    void ofFullModelName_bedrockClaude() {
        var version = AIModelVersion.of("anthropic.claude-3-haiku-20240307-v1:0");
        assertEquals("anthropic.claude", version.modelName());
        assertEquals(3, version.majorVersion());
        assertEquals(0, version.minorVersion());
    }

    // =================================================================================================================
    // Test factory method of(String modelName, int majorVersion, int minorVersion)
    // =================================================================================================================

    @Test
    void ofWithAllParams() {
        var version = AIModelVersion.of("gpt", 4, 5);
        assertEquals("gpt", version.modelName());
        assertEquals(4, version.majorVersion());
        assertEquals(5, version.minorVersion());
    }

    // =================================================================================================================
    // Test factory method of(String modelName, int majorVersion)
    // =================================================================================================================

    @Test
    void ofWithMajorOnly_shouldDefaultMinorToZero() {
        var version = AIModelVersion.of("gpt", 5);
        assertEquals("gpt", version.modelName());
        assertEquals(5, version.majorVersion());
        assertEquals(0, version.minorVersion());
    }

    // =================================================================================================================
    // Test validation constraints
    // =================================================================================================================

    @Test
    void constructor_nullModelName_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> AIModelVersion.of(null, 1, 0));
    }

    @Test
    void constructor_blankModelName_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> AIModelVersion.of("   ", 1, 0));
    }

    @Test
    void constructor_negativeMajorVersion_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> AIModelVersion.of("gpt", -1, 0));
    }

    @Test
    void constructor_negativeMinorVersion_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> AIModelVersion.of("gpt", 1, -1));
    }

    @Test
    void constructor_shouldTrimModelName() {
        var version = AIModelVersion.of("  gpt  ", 4, 0);
        assertEquals("gpt", version.modelName());
    }

    // =================================================================================================================
    // Test edge cases for version extraction
    // =================================================================================================================

    @Test
    void ofFullModelName_multiDigitMajor() {
        var version = AIModelVersion.of("model-123.456");
        assertEquals("model", version.modelName());
        assertEquals(123, version.majorVersion());
        assertEquals(456, version.minorVersion());
    }

    @Test
    void ofFullModelName_underscoreSeparator() {
        var version = AIModelVersion.of("model-4_5");
        assertEquals("model", version.modelName());
        assertEquals(4, version.majorVersion());
        assertEquals(5, version.minorVersion());
    }

    @Test
    void ofFullModelName_letterAfterMajor() {
        var version = AIModelVersion.of("gpt-4o");
        assertEquals("gpt", version.modelName());
        assertEquals(4, version.majorVersion());
        assertEquals(0, version.minorVersion());
    }

    // =================================================================================================================
    // Test lt (less than)
    // =================================================================================================================

    @Test
    void lt_sameMajorLowerMinor_shouldReturnTrue() {
        var v1 = AIModelVersion.of("gpt", 4, 0);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertTrue(v1.lt(v2));
    }

    @Test
    void lt_lowerMajor_shouldReturnTrue() {
        var v1 = AIModelVersion.of("gpt", 3, 5);
        var v2 = AIModelVersion.of("gpt", 4, 0);
        assertTrue(v1.lt(v2));
    }

    @Test
    void lt_equalVersions_shouldReturnFalse() {
        var v1 = AIModelVersion.of("gpt", 4, 5);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertFalse(v1.lt(v2));
    }

    @Test
    void lt_higherVersion_shouldReturnFalse() {
        var v1 = AIModelVersion.of("gpt", 5, 0);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertFalse(v1.lt(v2));
    }

    @Test
    void lt_differentModels_shouldReturnFalse() {
        var v1 = AIModelVersion.of("gpt", 3, 0);
        var v2 = AIModelVersion.of("claude", 4, 0);
        assertFalse(v1.lt(v2));
    }

    // =================================================================================================================
    // Test lte (less than or equal)
    // =================================================================================================================

    @Test
    void lte_lowerVersion_shouldReturnTrue() {
        var v1 = AIModelVersion.of("gpt", 4, 0);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertTrue(v1.lte(v2));
    }

    @Test
    void lte_equalVersion_shouldReturnTrue() {
        var v1 = AIModelVersion.of("gpt", 4, 5);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertTrue(v1.lte(v2));
    }

    @Test
    void lte_higherVersion_shouldReturnFalse() {
        var v1 = AIModelVersion.of("gpt", 5, 0);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertFalse(v1.lte(v2));
    }

    // =================================================================================================================
    // Test gt (greater than)
    // =================================================================================================================

    @Test
    void gt_higherMajor_shouldReturnTrue() {
        var v1 = AIModelVersion.of("gpt", 5, 0);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertTrue(v1.gt(v2));
    }

    @Test
    void gt_sameMajorHigherMinor_shouldReturnTrue() {
        var v1 = AIModelVersion.of("gpt", 4, 5);
        var v2 = AIModelVersion.of("gpt", 4, 0);
        assertTrue(v1.gt(v2));
    }

    @Test
    void gt_equalVersions_shouldReturnFalse() {
        var v1 = AIModelVersion.of("gpt", 4, 5);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertFalse(v1.gt(v2));
    }

    @Test
    void gt_lowerVersion_shouldReturnFalse() {
        var v1 = AIModelVersion.of("gpt", 3, 0);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertFalse(v1.gt(v2));
    }

    // =================================================================================================================
    // Test gte (greater than or equal)
    // =================================================================================================================

    @Test
    void gte_higherVersion_shouldReturnTrue() {
        var v1 = AIModelVersion.of("gpt", 5, 0);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertTrue(v1.gte(v2));
    }

    @Test
    void gte_equalVersion_shouldReturnTrue() {
        var v1 = AIModelVersion.of("gpt", 4, 5);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertTrue(v1.gte(v2));
    }

    @Test
    void gte_lowerVersion_shouldReturnFalse() {
        var v1 = AIModelVersion.of("gpt", 3, 0);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertFalse(v1.gte(v2));
    }

    // =================================================================================================================
    // Test eq (equal)
    // =================================================================================================================

    @Test
    void eq_equalVersions_shouldReturnTrue() {
        var v1 = AIModelVersion.of("gpt", 4, 5);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertTrue(v1.eq(v2));
    }

    @Test
    void eq_differentMajor_shouldReturnFalse() {
        var v1 = AIModelVersion.of("gpt", 4, 5);
        var v2 = AIModelVersion.of("gpt", 5, 5);
        assertFalse(v1.eq(v2));
    }

    @Test
    void eq_differentMinor_shouldReturnFalse() {
        var v1 = AIModelVersion.of("gpt", 4, 5);
        var v2 = AIModelVersion.of("gpt", 4, 0);
        assertFalse(v1.eq(v2));
    }

    @Test
    void eq_differentModels_shouldReturnFalse() {
        var v1 = AIModelVersion.of("gpt", 4, 5);
        var v2 = AIModelVersion.of("claude", 4, 5);
        assertFalse(v1.eq(v2));
    }

    // =================================================================================================================
    // Test ne (not equal)
    // =================================================================================================================

    @Test
    void ne_equalVersions_shouldReturnFalse() {
        var v1 = AIModelVersion.of("gpt", 4, 5);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertFalse(v1.ne(v2));
    }

    @Test
    void ne_differentMajor_shouldReturnTrue() {
        var v1 = AIModelVersion.of("gpt", 4, 5);
        var v2 = AIModelVersion.of("gpt", 5, 5);
        assertTrue(v1.ne(v2));
    }

    @Test
    void ne_differentMinor_shouldReturnTrue() {
        var v1 = AIModelVersion.of("gpt", 4, 5);
        var v2 = AIModelVersion.of("gpt", 4, 0);
        assertTrue(v1.ne(v2));
    }

    @Test
    void ne_differentModels_shouldReturnTrue() {
        var v1 = AIModelVersion.of("gpt", 4, 5);
        var v2 = AIModelVersion.of("claude", 4, 5);
        assertTrue(v1.ne(v2));
    }

    @Test
    void ne_negatesEq() {
        var v1 = AIModelVersion.of("gpt", 4, 5);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        var v3 = AIModelVersion.of("gpt", 5, 0);
        assertEquals(v1.eq(v2), !v1.ne(v2));
        assertEquals(v1.eq(v3), !v1.ne(v3));
    }

    // =================================================================================================================
    // Test model matching (prefix contains)
    // =================================================================================================================

    @Test
    void modelMatching_prefixContainsShortName() {
        var v1 = AIModelVersion.of("gpt", 4, 0);
        var v2 = AIModelVersion.of("gpt-5-mini");
        assertTrue(v1.lt(v2));
    }

    @Test
    void modelMatching_shortNameContainedInPrefix() {
        var v1 = AIModelVersion.of("gpt-5-mini");
        var v2 = AIModelVersion.of("gpt", 4, 0);
        assertTrue(v1.gt(v2));
    }

    @Test
    void modelMatching_caseInsensitive() {
        var v1 = AIModelVersion.of("GPT", 4, 0);
        var v2 = AIModelVersion.of("gpt-5-mini");
        assertTrue(v1.lt(v2));
    }

    @Test
    void modelMatching_partialMatch() {
        var v1 = AIModelVersion.of("claude", 3, 0);
        var v2 = AIModelVersion.of("claude-sonnet-4-5-20250929");
        assertTrue(v1.lt(v2));
    }

    @Test
    void modelMatching_noMatch() {
        var v1 = AIModelVersion.of("gpt", 3, 0);
        var v2 = AIModelVersion.of("claude-sonnet-4-5-20250929");
        assertFalse(v1.lt(v2));
        assertFalse(v1.gt(v2));
        assertFalse(v1.eq(v2));
    }

    @Test
    void modelMatching_bedrockClaudeMatchesClaude() {
        var v1 = AIModelVersion.of("claude", 3, 0);
        var v2 = AIModelVersion.of("anthropic.claude-3-haiku-20240307-v1:0");
        assertTrue(v1.lte(v2));
        assertTrue(v2.gte(v1));
        assertTrue(v1.eq(v2));
    }

    // =================================================================================================================
    // Test compareTo (Comparable interface)
    // =================================================================================================================

    @Test
    void compareTo_sameModelLowerVersion_shouldBeNegative() {
        var v1 = AIModelVersion.of("gpt", 4, 0);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertTrue(v1.compareTo(v2) < 0);
    }

    @Test
    void compareTo_sameModelHigherVersion_shouldBePositive() {
        var v1 = AIModelVersion.of("gpt", 5, 0);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertTrue(v1.compareTo(v2) > 0);
    }

    @Test
    void compareTo_equalVersions_shouldBeZero() {
        var v1 = AIModelVersion.of("gpt", 4, 5);
        var v2 = AIModelVersion.of("gpt", 4, 5);
        assertEquals(0, v1.compareTo(v2));
    }

    @Test
    void compareTo_differentModels_shouldCompareByName() {
        var v1 = AIModelVersion.of("claude", 4, 0);
        var v2 = AIModelVersion.of("gpt", 4, 0);
        assertTrue(v1.compareTo(v2) < 0);
    }

    @Test
    void compareTo_differentModelsCaseInsensitive() {
        var v1 = AIModelVersion.of("GPT", 4, 0);
        var v2 = AIModelVersion.of("gpt", 4, 0);
        assertEquals(0, v1.compareTo(v2));
    }

    // =================================================================================================================
    // Test with real-world comparison scenarios
    // =================================================================================================================

    @Test
    void realWorld_checkMinimumGptVersion() {
        var minimum = AIModelVersion.of("gpt", 5, 0);
        var actual = AIModelVersion.of("gpt-5-mini");
        assertTrue(minimum.lte(actual));
    }

    @Test
    void realWorld_checkMinimumClaudeVersion() {
        var minimum = AIModelVersion.of("claude", 4, 0);
        var actual = AIModelVersion.of("claude-sonnet-4-5-20250929");
        assertTrue(minimum.lte(actual));
    }

    @Test
    void realWorld_checkMinimumGeminiVersion() {
        var minimum = AIModelVersion.of("gemini", 2, 0);
        var actual = AIModelVersion.of("gemini-2.5-flash");
        assertTrue(minimum.lte(actual));
    }

    @Test
    void realWorld_checkMinimumGrokVersion() {
        var minimum = AIModelVersion.of("grok", 4, 0);
        var actual = AIModelVersion.of("grok-4-1-fast-reasoning");
        assertTrue(minimum.lte(actual));
    }

    @Test
    void realWorld_checkMinimumLlamaVersion() {
        var minimum = AIModelVersion.of("llama", 3, 0);
        var actual = AIModelVersion.of("llama3.2");
        assertTrue(minimum.lte(actual));
    }

    @Test
    void realWorld_olderVersionDoesNotMeetMinimum() {
        var minimum = AIModelVersion.of("gpt", 5, 0);
        var actual = AIModelVersion.of("gpt-4o");
        assertFalse(minimum.lte(actual));
    }

    @Test
    void realWorld_checkMinimumBedrockClaudeVersion() {
        var minimum = AIModelVersion.of("claude", 3, 0);
        var actual = AIModelVersion.of("anthropic.claude-3-haiku-20240307-v1:0");
        assertTrue(minimum.lte(actual));
    }

    // =================================================================================================================
    // Test edge cases with missing minor versions (defaults to 0)
    // =================================================================================================================

    @Test
    void comparison_missingMinor_shouldDefaultToZero() {
        var v1 = AIModelVersion.of("gpt-5-mini");
        var v2 = AIModelVersion.of("gpt", 5, 0);
        assertTrue(v1.eq(v2));
    }

    @Test
    void comparison_missingMinorVsExplicitZero() {
        var v1 = AIModelVersion.of("gpt", 5);
        var v2 = AIModelVersion.of("gpt-5-mini");
        assertTrue(v1.eq(v2));
    }
}
