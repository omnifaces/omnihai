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
package org.omnifaces.ai.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Base class for IT on image-analyzer-related methods of AI service.
 *
 * NOTE: this is a separate class from {@link BaseAIServiceTextHandlerIT} because image analysis might require a different model than e.g. text analysis.
 */
abstract class BaseAIServiceImageHandlerIT extends AIServiceIT {

    private static final Set<String> ACCEPTABLE_SHAPES = Set.of("pentagon", "pentagram", "star");
    private static final Set<String> UNACCEPTABLE_SHAPES = Set.of("hexagon");
    private static final Set<String> ACCEPTABLE_DESCRIPTIONS = Set.of("five", "node", "dot", "line", "circle", "circular");
    private static final Set<String> UNACCEPTABLE_DESCRIPTIONS = Set.of("six");

    @Test
    void analyzeImageFromBytes() {
        assertShape(service.analyzeImage(readAllBytes("/omnifaces.png"), "What shape is this?"));
    }

    @Test
    void analyzeImageFromPath() {
        assertShape(service.analyzeImage(getPath("/omnifaces.png"), "What shape is this?"));
    }

    private void assertShape(String response) {
        log(response);
        assertAll(
            () -> assertTrue(
                ACCEPTABLE_SHAPES.stream().anyMatch(response.toLowerCase()::contains), "response must contain acceptable shapes: " + ACCEPTABLE_SHAPES
            ),
            () -> assertFalse(
                UNACCEPTABLE_SHAPES.stream().anyMatch(response.toLowerCase()::contains), "response may not contain unacceptable shapes: " + UNACCEPTABLE_SHAPES
            )
        );
    }

    @Test
    void generateAltTextFromBytes() {
        assertAltText(service.generateAltText(readAllBytes("/omnifaces.png")));
    }

    @Test
    void generateAltTextFromPath() {
        assertAltText(service.generateAltText(getPath("/omnifaces.png")));
    }

    private void assertAltText(String response) {
        log(response);
        var sentences = response.split("\\.");
        assertAll(
            () -> assertTrue(
                ACCEPTABLE_DESCRIPTIONS.stream().anyMatch(response.toLowerCase()::contains),
                "response must contain acceptable descriptions: " + ACCEPTABLE_DESCRIPTIONS
            ),
            () -> assertFalse(
                UNACCEPTABLE_DESCRIPTIONS.stream().anyMatch(response.toLowerCase()::contains),
                "response may not contain unacceptable descriptions: " + UNACCEPTABLE_DESCRIPTIONS
            ),
            () -> assertTrue(sentences.length <= 2, "max 2 sentences"),
            () -> assertTrue(sentences[0].split("\\s+").length <= 30, "max 30 words (slack of 5) in 1st sentence"),
            () -> assertTrue(sentences.length < 2 || sentences[1].split("\\s+").length <= 30, "max 30 words (slack of 5) in 2nd sentence")
        );
    }

}
