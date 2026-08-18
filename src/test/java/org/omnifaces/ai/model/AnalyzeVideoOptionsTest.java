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

import static java.time.Duration.ofSeconds;
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

import org.junit.jupiter.api.Test;

class AnalyzeVideoOptionsTest {

    // =================================================================================================================
    // Default values tests
    // =================================================================================================================

    @Test
    void builder_defaultValues() {
        var options = AnalyzeVideoOptions.newBuilder().build();

        assertEquals(AnalyzeVideoOptions.DEFAULT_FPS, options.getFps());
        assertNull(options.getStartOffset());
        assertNull(options.getEndOffset());
    }

    @Test
    void defaultConstant_hasExpectedValues() {
        assertEquals(0.0, AnalyzeVideoOptions.DEFAULT.getFps());
        assertNull(AnalyzeVideoOptions.DEFAULT.getStartOffset());
        assertNull(AnalyzeVideoOptions.DEFAULT.getEndOffset());
    }

    // =================================================================================================================
    // isDefault tests
    // =================================================================================================================

    @Test
    void isDefault_true_whenNothingSet() {
        assertTrue(AnalyzeVideoOptions.newBuilder().build().isDefault());
    }

    @Test
    void isDefault_false_whenFpsSet() {
        assertFalse(AnalyzeVideoOptions.newBuilder().fps(2).build().isDefault());
    }

    @Test
    void isDefault_false_whenStartOffsetSet() {
        assertFalse(AnalyzeVideoOptions.newBuilder().startOffset(ofSeconds(10)).build().isDefault());
    }

    @Test
    void isDefault_false_whenEndOffsetSet() {
        assertFalse(AnalyzeVideoOptions.newBuilder().endOffset(ofSeconds(10)).build().isDefault());
    }

    // =================================================================================================================
    // Builder tests - fps
    // =================================================================================================================

    @Test
    void builder_fps_zero_throwsException() {
        var builder = AnalyzeVideoOptions.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.fps(0));
        assertEquals("Fps must be positive", exception.getMessage());
    }

    @Test
    void builder_fps_negative_throwsException() {
        var builder = AnalyzeVideoOptions.newBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.fps(-1.0));
    }

    @Test
    void builder_fps_fractionalValue() {
        var options = AnalyzeVideoOptions.newBuilder().fps(0.2).build();

        assertEquals(0.2, options.getFps());
    }

    // =================================================================================================================
    // Builder tests - offsets
    // =================================================================================================================

    @Test
    void builder_startOffset_null_throwsException() {
        var builder = AnalyzeVideoOptions.newBuilder();

        assertThrows(NullPointerException.class, () -> builder.startOffset(null));
    }

    @Test
    void builder_endOffset_null_throwsException() {
        var builder = AnalyzeVideoOptions.newBuilder();

        assertThrows(NullPointerException.class, () -> builder.endOffset(null));
    }

    @Test
    void builder_startOffset_negative_throwsException() {
        var builder = AnalyzeVideoOptions.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.startOffset(ofSeconds(-1)));
        assertEquals("startOffset must not be negative", exception.getMessage());
    }

    @Test
    void builder_endOffset_negative_throwsException() {
        var builder = AnalyzeVideoOptions.newBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.endOffset(ofSeconds(-1)));
    }

    @Test
    void builder_endOffset_zero_throwsException() {
        var builder = AnalyzeVideoOptions.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.endOffset(ofSeconds(0)));
        assertEquals("endOffset must be positive", exception.getMessage());
    }

    @Test
    void build_endOffsetBeforeStartOffset_throwsException() {
        var builder = AnalyzeVideoOptions.newBuilder().startOffset(ofSeconds(20)).endOffset(ofSeconds(10));

        var exception = assertThrows(IllegalArgumentException.class, builder::build);
        assertEquals("End offset must be after start offset", exception.getMessage());
    }

    @Test
    void build_endOffsetEqualToStartOffset_throwsException() {
        var builder = AnalyzeVideoOptions.newBuilder().startOffset(ofSeconds(10)).endOffset(ofSeconds(10));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    // =================================================================================================================
    // Builder tests - chaining
    // =================================================================================================================

    @Test
    void builder_chaining_allOptions() {
        var options = AnalyzeVideoOptions.newBuilder()
            .fps(5)
            .startOffset(ofSeconds(30))
            .endOffset(ofSeconds(90))
            .build();

        assertEquals(5, options.getFps());
        assertEquals(ofSeconds(30), options.getStartOffset());
        assertEquals(ofSeconds(90), options.getEndOffset());
    }

    // =================================================================================================================
    // Serialization tests
    // =================================================================================================================

    @Test
    void implementsSerializable() {
        assertTrue(Serializable.class.isAssignableFrom(AnalyzeVideoOptions.class));
    }

    @Test
    void serialization_preservesAllFields() throws Exception {
        var original = AnalyzeVideoOptions.newBuilder()
            .fps(2)
            .startOffset(ofSeconds(5))
            .endOffset(ofSeconds(15))
            .build();

        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        var bais = new ByteArrayInputStream(baos.toByteArray());
        try (var ois = new ObjectInputStream(bais)) {
            var deserialized = (AnalyzeVideoOptions) ois.readObject();

            assertEquals(original.getFps(), deserialized.getFps());
            assertEquals(original.getStartOffset(), deserialized.getStartOffset());
            assertEquals(original.getEndOffset(), deserialized.getEndOffset());
        }
    }

}
