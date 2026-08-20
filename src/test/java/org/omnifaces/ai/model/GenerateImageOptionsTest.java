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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.junit.jupiter.api.Test;

class GenerateImageOptionsTest {

    // =================================================================================================================
    // Builder tests - size and aspect ratio calculation
    // =================================================================================================================

    @Test
    void builder_defaultValues() {
        var options = GenerateImageOptions.newBuilder().build();

        assertEquals(GenerateImageOptions.DEFAULT_SIZE, options.getSize());
        assertEquals(GenerateImageOptions.DEFAULT_ASPECT_RATIO, options.getAspectRatio());
        assertEquals(GenerateImageOptions.DEFAULT_QUALITY, options.getQuality());
        assertEquals(GenerateImageOptions.DEFAULT_OUTPUT_FORMAT, options.getOutputFormat());
    }

    @Test
    void builder_size_calculatesAspectRatio_square() {
        var options = GenerateImageOptions.newBuilder().size("1024x1024").build();

        assertEquals("1024x1024", options.getSize());
        assertEquals("1:1", options.getAspectRatio());
    }

    @Test
    void builder_size_calculatesAspectRatio_16by9() {
        var options = GenerateImageOptions.newBuilder().size("1920x1080").build();

        assertEquals("16:9", options.getAspectRatio());
    }

    @Test
    void builder_size_calculatesAspectRatio_withGcdReduction() {
        // 800x600 -> GCD is 200 -> 4:3
        var options = GenerateImageOptions.newBuilder().size("800x600").build();

        assertEquals("4:3", options.getAspectRatio());
    }

    @Test
    void builder_size_null_throwsException() {
        var builder = GenerateImageOptions.newBuilder();

        assertThrows(NullPointerException.class, () -> builder.size(null));
    }

    @Test
    void builder_size_invalidFormat_throwsException() {
        var builder = GenerateImageOptions.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.size("1024"));
        assertEquals("Invalid size: 1024", exception.getMessage());
    }

    @Test
    void builder_aspectRatio_resetsSizeToDefault() {
        var options = GenerateImageOptions.newBuilder()
            .size("1920x1080")
            .aspectRatio("1:1")
            .build();

        assertEquals(GenerateImageOptions.DEFAULT_SIZE, options.getSize());
        assertEquals("1:1", options.getAspectRatio());
    }

    @Test
    void builder_aspectRatio_null_throwsException() {
        var builder = GenerateImageOptions.newBuilder();

        assertThrows(NullPointerException.class, () -> builder.aspectRatio(null));
    }

    @Test
    void builder_aspectRatio_invalidFormat_throwsException() {
        var builder = GenerateImageOptions.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.aspectRatio("16/9"));
        assertEquals("Invalid aspect ratio: 16/9", exception.getMessage());
    }

    @Test
    void builder_sizeAfterAspectRatio_recalculatesAspectRatio() {
        var options = GenerateImageOptions.newBuilder()
            .aspectRatio("16:9")
            .size("1024x1024")
            .build();

        assertEquals("1024x1024", options.getSize());
        assertEquals("1:1", options.getAspectRatio());
    }

    // =================================================================================================================
    // Builder tests - quality and outputFormat
    // =================================================================================================================

    @Test
    void builder_quality_null_throwsException() {
        var builder = GenerateImageOptions.newBuilder();

        assertThrows(NullPointerException.class, () -> builder.quality(null));
    }

    @Test
    void builder_outputFormat_null_throwsException() {
        var builder = GenerateImageOptions.newBuilder();

        assertThrows(NullPointerException.class, () -> builder.outputFormat(null));
    }

    @Test
    void builder_chaining_allOptions() {
        var options = GenerateImageOptions.newBuilder()
            .size("1920x1080")
            .quality("hd")
            .outputFormat("jpeg")
            .build();

        assertEquals("1920x1080", options.getSize());
        assertEquals("16:9", options.getAspectRatio());
        assertEquals("hd", options.getQuality());
        assertEquals("jpeg", options.getOutputFormat());
    }

    // =================================================================================================================
    // Serialization tests
    // =================================================================================================================

    @Test
    void implementsSerializable() {
        assertTrue(Serializable.class.isAssignableFrom(GenerateImageOptions.class));
    }

    @Test
    void serialization_preservesAllFields() throws Exception {
        var original = GenerateImageOptions.newBuilder()
            .size("1920x1080")
            .quality("hd")
            .outputFormat("jpeg")
            .build();

        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        var bais = new ByteArrayInputStream(baos.toByteArray());
        try (var ois = new ObjectInputStream(bais)) {
            var deserialized = (GenerateImageOptions) ois.readObject();

            assertEquals(original.getSize(), deserialized.getSize());
            assertEquals(original.getAspectRatio(), deserialized.getAspectRatio());
            assertEquals(original.getQuality(), deserialized.getQuality());
            assertEquals(original.getOutputFormat(), deserialized.getOutputFormat());
        }
    }

    @Test
    void builder_size_withAZeroDimension_throwsException() {
        var builder = GenerateImageOptions.newBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.size("0x0"), "a zero size has no aspect ratio to derive");
        assertThrows(IllegalArgumentException.class, () -> builder.size("1920x0"));
        assertThrows(IllegalArgumentException.class, () -> builder.size("0x1080"));
    }

    @Test
    void builder_aspectRatio_withAZeroDimension_throwsException() {
        var builder = GenerateImageOptions.newBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.aspectRatio("0:0"));
        assertThrows(IllegalArgumentException.class, () -> builder.aspectRatio("16:0"));
        assertThrows(IllegalArgumentException.class, () -> builder.aspectRatio("0:9"));
    }

}
