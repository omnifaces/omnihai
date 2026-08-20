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

import java.time.Duration;

import org.junit.jupiter.api.Test;

class GenerateVideoOptionsTest {

    @Test
    void defaults_leaveEverySizingChoiceToTheProvider() {
        var options = GenerateVideoOptions.DEFAULT;

        assertTrue(options.useDefaultSize(), "size must not be imposed on the provider");
        assertTrue(options.useDefaultResolution(), "resolution must not be imposed on the provider");
        assertTrue(options.useDefaultSeconds(), "duration must not be imposed on the provider");
        assertEquals("16:9", options.getAspectRatio());
        assertEquals(Duration.ofSeconds(5), options.getPollInterval());
        assertEquals(Duration.ofMinutes(5), options.getMaxWait());
    }

    @Test
    void builder_retainsEveryOption() {
        var options = GenerateVideoOptions.newBuilder()
            .size("1280x720")
            .resolution("720p")
            .seconds(8)
            .pollInterval(Duration.ofSeconds(30))
            .maxWait(Duration.ofMinutes(10))
            .build();

        assertEquals("1280x720", options.getSize());
        assertEquals("16:9", options.getAspectRatio());
        assertEquals("720p", options.getResolution());
        assertEquals(8, options.getSeconds());
        assertEquals(Duration.ofSeconds(30), options.getPollInterval());
        assertEquals(Duration.ofMinutes(10), options.getMaxWait());
        assertFalse(options.useDefaultSize());
        assertFalse(options.useDefaultResolution());
        assertFalse(options.useDefaultSeconds());
    }

    @Test
    void builder_size_calculatesAspectRatio() {
        assertEquals("16:9", GenerateVideoOptions.newBuilder().size("1920x1080").build().getAspectRatio());
        assertEquals("9:16", GenerateVideoOptions.newBuilder().size("1080x1920").build().getAspectRatio());
        assertEquals("1:1", GenerateVideoOptions.newBuilder().size("1024x1024").build().getAspectRatio());
    }

    @Test
    void builder_aspectRatio_resetsSizeToDefault() {
        var options = GenerateVideoOptions.newBuilder().size("1920x1080").aspectRatio("9:16").build();

        assertEquals(GenerateVideoOptions.DEFAULT_SIZE, options.getSize(), "size and aspect ratio may never contradict each other");
        assertEquals("9:16", options.getAspectRatio());
        assertTrue(options.useDefaultSize());
    }

    @Test
    void builder_sizeAfterAspectRatio_recalculatesAspectRatio() {
        var options = GenerateVideoOptions.newBuilder().aspectRatio("9:16").size("1920x1080").build();

        assertEquals("1920x1080", options.getSize());
        assertEquals("16:9", options.getAspectRatio(), "the last stated of both wins");
    }

    @Test
    void builder_size_invalidFormat_throwsException() {
        var builder = GenerateVideoOptions.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.size("720p"));
        assertEquals("Invalid size: 720p", exception.getMessage());
    }

    @Test
    void builder_aspectRatio_invalidFormat_throwsException() {
        var builder = GenerateVideoOptions.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.aspectRatio("16/9"));
        assertEquals("Invalid aspect ratio: 16/9", exception.getMessage());
    }

    @Test
    void seconds_mustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> GenerateVideoOptions.newBuilder().seconds(0));
        assertThrows(IllegalArgumentException.class, () -> GenerateVideoOptions.newBuilder().seconds(-1));
    }

    @Test
    void maxWait_mustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> GenerateVideoOptions.newBuilder().maxWait(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> GenerateVideoOptions.newBuilder().maxWait(Duration.ofSeconds(-1)));
        assertThrows(NullPointerException.class, () -> GenerateVideoOptions.newBuilder().maxWait(null));
    }

    @Test
    void pollInterval_mustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> GenerateVideoOptions.newBuilder().pollInterval(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> GenerateVideoOptions.newBuilder().pollInterval(Duration.ofSeconds(-1)));
        assertThrows(NullPointerException.class, () -> GenerateVideoOptions.newBuilder().pollInterval(null));
    }

    @Test
    void nullValues_areRejected() {
        assertThrows(NullPointerException.class, () -> GenerateVideoOptions.newBuilder().size(null));
        assertThrows(NullPointerException.class, () -> GenerateVideoOptions.newBuilder().aspectRatio(null));
        assertThrows(IllegalArgumentException.class, () -> GenerateVideoOptions.newBuilder().resolution(null));
    }

    @Test
    void builder_resolution_blank_throwsException() {
        var builder = GenerateVideoOptions.newBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.resolution(""), "a blank resolution would reach the AI provider as an empty value");
        assertThrows(IllegalArgumentException.class, () -> builder.resolution("   "));
    }

    @Test
    void builder_size_withAZeroDimension_throwsException() {
        var builder = GenerateVideoOptions.newBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.size("0x0"), "a zero size has no aspect ratio to derive");
        assertThrows(IllegalArgumentException.class, () -> builder.size("1920x0"));
        assertThrows(IllegalArgumentException.class, () -> builder.size("0x1080"));
    }

    @Test
    void builder_aspectRatio_withAZeroDimension_throwsException() {
        var builder = GenerateVideoOptions.newBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.aspectRatio("0:0"));
        assertThrows(IllegalArgumentException.class, () -> builder.aspectRatio("16:0"));
        assertThrows(IllegalArgumentException.class, () -> builder.aspectRatio("0:9"));
    }

    @Test
    void orientation_followsTheAspectRatio() {
        var landscape = GenerateVideoOptions.newBuilder().aspectRatio("16:9").build();
        var portrait = GenerateVideoOptions.newBuilder().aspectRatio("9:16").build();
        var square = GenerateVideoOptions.newBuilder().aspectRatio("1:1").build();

        assertTrue(landscape.isLandscape());
        assertFalse(landscape.isPortrait());
        assertTrue(portrait.isPortrait());
        assertFalse(portrait.isLandscape());
        assertFalse(square.isPortrait(), "a square is neither portrait nor landscape");
        assertFalse(square.isLandscape());
    }

    @Test
    void orientation_followsASizeTheAspectRatioWasDerivedFrom() {
        assertTrue(GenerateVideoOptions.newBuilder().size("1920x1080").build().isLandscape());
        assertTrue(GenerateVideoOptions.newBuilder().size("1080x1920").build().isPortrait());
    }

    @Test
    void defaultOptions_areLandscape() {
        assertTrue(GenerateVideoOptions.DEFAULT.isLandscape(), "an AI provider which takes no aspect ratio derives its size from this");
    }

}
