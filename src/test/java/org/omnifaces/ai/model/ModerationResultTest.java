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
package org.omnifaces.ai.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ModerationResultTest {

    // =================================================================================================================
    // SAFE constant tests
    // =================================================================================================================

    @Test
    void safe_isFlagged() {
        assertFalse(ModerationResult.SAFE.isFlagged());
        assertTrue(ModerationResult.SAFE.getScores().isEmpty());
        assertEquals(0.0, ModerationResult.SAFE.getHighestScore());
        assertNull(ModerationResult.SAFE.getHighestCategory());
    }

    // =================================================================================================================
    // Constructor tests
    // =================================================================================================================

    @Test
    void constructor_flaggedTrue() {
        var scores = Map.of("hate", 0.8);
        var result = new ModerationResult(true, scores);

        assertTrue(result.isFlagged());
    }

    @Test
    void constructor_flaggedFalse() {
        var scores = Map.of("hate", 0.2);
        var result = new ModerationResult(false, scores);

        assertFalse(result.isFlagged());
    }

    // =================================================================================================================
    // getScores tests
    // =================================================================================================================

    @Test
    void getScores_returnsCorrectValues() {
        var scores = Map.of("hate", 0.8, "violence", 0.3);
        var result = new ModerationResult(true, scores);

        assertEquals(0.8, result.getScores().get("hate"));
        assertEquals(0.3, result.getScores().get("violence"));
    }

    @Test
    void getScores_isImmutable() {
        var scores = new HashMap<String, Double>();
        scores.put("hate", 0.5);
        var result = new ModerationResult(false, scores);

        var immutableScores = result.getScores();

        assertThrows(UnsupportedOperationException.class, () -> immutableScores.put("violence", 0.3));
    }

    @Test
    void getScores_isSorted() {
        var scores = Map.of(
            "violence", 0.3,
            "hate", 0.1,
            "sexual", 0.5
        );
        var result = new ModerationResult(false, scores);

        var keys = result.getScores().keySet().toArray(new String[0]);
        assertEquals("hate", keys[0]);
        assertEquals("sexual", keys[1]);
        assertEquals("violence", keys[2]);
    }

    // =================================================================================================================
    // getScore tests
    // =================================================================================================================

    @Test
    void getScore_existingCategory() {
        var scores = Map.of("hate", 0.75);
        var result = new ModerationResult(true, scores);

        assertEquals(0.75, result.getScore("hate"));
    }

    @Test
    void getScore_nonExistingCategory() {
        var scores = Map.of("hate", 0.75);
        var result = new ModerationResult(true, scores);

        assertEquals(0.0, result.getScore("violence"));
    }

    @Test
    void getScore_nullCategory() {
        var result = new ModerationResult(false, Map.of("hate", 0.5));

        assertEquals(0.0, result.getScore(null));
    }

    // =================================================================================================================
    // isFlagged(category, threshold) tests
    // =================================================================================================================

    @Test
    void isFlagged_categoryAboveThreshold() {
        var scores = Map.of("hate", 0.8);
        var result = new ModerationResult(true, scores);

        assertTrue(result.isFlagged("hate", 0.5));
    }

    @Test
    void isFlagged_categoryBelowThreshold() {
        var scores = Map.of("hate", 0.3);
        var result = new ModerationResult(false, scores);

        assertFalse(result.isFlagged("hate", 0.5));
    }

    @Test
    void isFlagged_categoryAtThreshold() {
        var scores = Map.of("hate", 0.5);
        var result = new ModerationResult(false, scores);

        // Score must be GREATER than threshold
        assertFalse(result.isFlagged("hate", 0.5));
    }

    // =================================================================================================================
    // getFlaggedCategories tests
    // =================================================================================================================

    @Test
    void getFlaggedCategories_someAboveThreshold() {
        var scores = Map.of(
            "hate", 0.8,
            "violence", 0.3,
            "sexual", 0.6
        );
        var result = new ModerationResult(true, scores);

        var flagged = result.getFlaggedCategories(0.5);

        assertEquals(2, flagged.size());
        assertTrue(flagged.containsKey("hate"));
        assertTrue(flagged.containsKey("sexual"));
        assertFalse(flagged.containsKey("violence"));
    }

    @Test
    void getFlaggedCategories_emptyScores() {
        var result = new ModerationResult(false, Collections.emptyMap());

        var flagged = result.getFlaggedCategories(0.5);

        assertTrue(flagged.isEmpty());
    }

    // =================================================================================================================
    // getHighestCategory tests
    // =================================================================================================================

    @Test
    void getHighestCategory_multipleScores() {
        var scores = Map.of(
            "hate", 0.3,
            "violence", 0.9,
            "sexual", 0.5
        );
        var result = new ModerationResult(true, scores);

        assertEquals("violence", result.getHighestCategory());
    }

    @Test
    void getHighestCategory_emptyScores() {
        var result = new ModerationResult(false, Collections.emptyMap());

        assertNull(result.getHighestCategory());
    }

    // =================================================================================================================
    // getHighestScore tests
    // =================================================================================================================

    @Test
    void getHighestScore_multipleScores() {
        var scores = Map.of(
            "hate", 0.3,
            "violence", 0.9,
            "sexual", 0.5
        );
        var result = new ModerationResult(true, scores);

        assertEquals(0.9, result.getHighestScore());
    }

    @Test
    void getHighestScore_emptyScores() {
        var result = new ModerationResult(false, Collections.emptyMap());

        assertEquals(0.0, result.getHighestScore());
    }

    // =================================================================================================================
    // toString tests
    // =================================================================================================================

    @Test
    void toString_format() {
        var result = new ModerationResult(true, Map.of("hate", 0.8));
        var str = result.toString();

        assertTrue(str.startsWith("ModerationResult{"));
        assertTrue(str.contains("flagged=true"));
        assertTrue(str.contains("scores="));
    }

    // =================================================================================================================
    // Serialization tests
    // =================================================================================================================

    @Test
    void implementsSerializable() {
        assertTrue(Serializable.class.isAssignableFrom(ModerationResult.class));
    }

    @Test
    void serialization_preservesAllFields() throws Exception {
        var original = new ModerationResult(true, Map.of("hate", 0.8, "violence", 0.6));

        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        var bais = new ByteArrayInputStream(baos.toByteArray());
        try (var ois = new ObjectInputStream(bais)) {
            var deserialized = (ModerationResult) ois.readObject();

            assertTrue(deserialized.isFlagged());
            assertEquals(original.getScores(), deserialized.getScores());
        }
    }

}
