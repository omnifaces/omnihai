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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.junit.jupiter.api.Test;

class GenerateAudioOptionsTest {

    // =================================================================================================================
    // Default values tests
    // =================================================================================================================

    @Test
    void builder_defaultValues() {
        var options = GenerateAudioOptions.newBuilder().build();

        assertEquals(GenerateAudioOptions.DEFAULT_VOICE, options.getVoice());
        assertEquals(GenerateAudioOptions.DEFAULT_SPEED, options.getSpeed());
        assertEquals(GenerateAudioOptions.DEFAULT_OUTPUT_FORMAT, options.getOutputFormat());
    }

    @Test
    void defaultConstant_hasExpectedValues() {
        assertEquals("auto", GenerateAudioOptions.DEFAULT.getVoice());
        assertEquals(1.0, GenerateAudioOptions.DEFAULT.getSpeed());
        assertEquals("mp3", GenerateAudioOptions.DEFAULT.getOutputFormat());
    }

    // =================================================================================================================
    // useDefaultVoice tests
    // =================================================================================================================

    @Test
    void useDefaultVoice_true_whenDefault() {
        var options = GenerateAudioOptions.newBuilder().build();

        assertTrue(options.useDefaultVoice());
    }

    @Test
    void useDefaultVoice_false_whenCustom() {
        var options = GenerateAudioOptions.newBuilder().voice("Kore").build();

        assertFalse(options.useDefaultVoice());
    }

    // =================================================================================================================
    // Builder tests - voice
    // =================================================================================================================

    @Test
    void builder_voice_null_throwsException() {
        var builder = GenerateAudioOptions.newBuilder();

        assertThrows(NullPointerException.class, () -> builder.voice(null));
    }

    @Test
    void builder_voice_customValue() {
        var options = GenerateAudioOptions.newBuilder().voice("alloy").build();

        assertEquals("alloy", options.getVoice());
    }

    // =================================================================================================================
    // Builder tests - speed
    // =================================================================================================================

    @Test
    void builder_speed_zero_throwsException() {
        var builder = GenerateAudioOptions.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.speed(0));
        assertEquals("Speed must be positive", exception.getMessage());
    }

    @Test
    void builder_speed_negative_throwsException() {
        var builder = GenerateAudioOptions.newBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.speed(-1.0));
    }

    @Test
    void builder_speed_positiveValue() {
        var options = GenerateAudioOptions.newBuilder().speed(0.5).build();

        assertEquals(0.5, options.getSpeed());
    }

    @Test
    void builder_speed_largeValue() {
        var options = GenerateAudioOptions.newBuilder().speed(10.0).build();

        assertEquals(10.0, options.getSpeed());
    }

    // =================================================================================================================
    // Builder tests - outputFormat
    // =================================================================================================================

    @Test
    void builder_outputFormat_null_throwsException() {
        var builder = GenerateAudioOptions.newBuilder();

        assertThrows(NullPointerException.class, () -> builder.outputFormat(null));
    }

    @Test
    void builder_outputFormat_customValue() {
        var options = GenerateAudioOptions.newBuilder().outputFormat("wav").build();

        assertEquals("wav", options.getOutputFormat());
    }

    // =================================================================================================================
    // Builder tests - chaining
    // =================================================================================================================

    @Test
    void builder_chaining_allOptions() {
        var options = GenerateAudioOptions.newBuilder()
                .voice("breeze")
                .speed(1.5)
                .outputFormat("wav")
                .build();

        assertEquals("breeze", options.getVoice());
        assertEquals(1.5, options.getSpeed());
        assertEquals("wav", options.getOutputFormat());
    }

    // =================================================================================================================
    // Serialization tests
    // =================================================================================================================

    @Test
    void implementsSerializable() {
        assertTrue(Serializable.class.isAssignableFrom(GenerateAudioOptions.class));
    }

    @Test
    void serialization_preservesAllFields() throws Exception {
        var original = GenerateAudioOptions.newBuilder()
                .voice("Kore")
                .speed(1.5)
                .outputFormat("wav")
                .build();

        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        var bais = new ByteArrayInputStream(baos.toByteArray());
        try (var ois = new ObjectInputStream(bais)) {
            var deserialized = (GenerateAudioOptions) ois.readObject();

            assertEquals(original.getVoice(), deserialized.getVoice());
            assertEquals(original.getSpeed(), deserialized.getSpeed());
            assertEquals(original.getOutputFormat(), deserialized.getOutputFormat());
        }
    }
}
