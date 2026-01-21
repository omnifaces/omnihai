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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Base class for IT on text-analyzer-related methods of AI service.
 */
abstract class BaseAIServiceTextAnalyzerIT extends AIServiceIT {

    @Test
    void chat() {
        var response = service.chat("Reply with only: OK");
        log(response);
        assertTrue(response.contains("OK"), response);
    }

    @Test
    void summarize() {
        var response = service.summarize("The quick brown fox jumps over the lazy dog near the river.", 5);
        log(response);
        assertFalse(response.isBlank(), response);
        assertTrue(response.split("\\s+").length <= 6, response);  // allow some slack
        assertTrue(response.toLowerCase().contains("fox"));
        assertTrue(response.toLowerCase().contains("jump"));
        assertTrue(response.toLowerCase().contains("dog"));
    }

    @Test
    void extractKeyPoints() {
        var response = service.extractKeyPoints("Willemstad is the capital of Curacao. Amsterdam is the capital of The Netherlands.", 2);
        log(response.toString());
        assertFalse(response.isEmpty(), response.toString());
        assertEquals(2, response.size(), response.toString());
        assertTrue(response.get(0).toLowerCase().contains("willemstad"));
        assertTrue(response.get(0).toLowerCase().contains("cura"));
        assertTrue(response.get(1).toLowerCase().contains("amsterdam"));
        assertTrue(response.get(1).toLowerCase().contains("netherlands"));
    }

    @Test
    void detectLanguage() {
        var response = service.detectLanguage("De kat zat op de mat.");
        log(response);
        assertEquals("nl", response);
    }

    @Test
    void translate() {
        var response = service.translate("<p style='color:blue'>The cat #{bean.verb} on the mat.</p>", "en", "nl");
        log(response);
        assertEquals("<p style='color:blue'>De kat #{bean.verb} op de mat.</p>", response);
    }
}
