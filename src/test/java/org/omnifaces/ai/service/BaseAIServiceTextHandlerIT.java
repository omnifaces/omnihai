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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.model.ModerationOptions.Category;

/**
 * Base class for IT on text-analyzer-related methods of AI service.
 */
abstract class BaseAIServiceTextHandlerIT extends AIServiceIT {

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
        assertAll(
            () -> assertTrue(response.split("\\s+").length <= 6, "max 6 words (slack of 1)"),
            () -> assertTrue(response.toLowerCase().contains("fox")),
            () -> assertTrue(response.toLowerCase().contains("jump")),
            () -> assertTrue(response.toLowerCase().contains("dog"))
        );

    }

    @Test
    void extractKeyPoints() {
        var response = service.extractKeyPoints("Willemstad is the capital of Curacao. Amsterdam is the capital of The Netherlands.", 2);
        log(response.toString());
        assertFalse(response.isEmpty(), response.toString());
        assertAll(
            () -> assertEquals(2, response.size(), response.toString()),
            () -> assertTrue(response.get(0).split("\\s+").length <= 30, "max 30 words (slack of 5)"),
            () -> assertTrue(response.get(0).toLowerCase().contains("willemstad")),
            () -> assertTrue(response.get(0).toLowerCase().contains("cura")),
            () -> assertTrue(response.get(1).split("\\s+").length <= 30, "max 30 words (slack of 5)"),
            () -> assertTrue(response.get(1).toLowerCase().contains("amsterdam")),
            () -> assertTrue(response.get(1).toLowerCase().contains("netherlands"))
        );
    }

    @Test
    void detectLanguage() {
        var response = service.detectLanguage("De kat zat op de stoel.");
        log(response);
        assertEquals("nl", response);
    }

    @Test
    void translate() {
        var response = service.translate("<section style='color:blue'>The cat #{bean.verb} on the chair.</section><script>{'key':'value'}</script>", "en", "nl");
        log(response);
        assertEquals("<section style='color:blue'>De kat #{bean.verb} op de stoel.</section><script>{'key':'value'}</script>", response);
    }

    @Test
    void translateAutomatically() {
        var response = service.translate("<section style='color:blauw'>De kat #{boon.werkwoord} op de stoel.</section><script>{'sleutel':'waarde'}</script>", null, "en");
        log(response);
        assertEquals("<section style='color:blauw'>The cat #{boon.werkwoord} on the chair.</section><script>{'sleutel':'waarde'}</script>", response);
    }

    @Test
    void moderateContentSafe() {
        var response = service.moderateContent("The quick brown fox jumps over the lazy dog near the river.");
        log(response.toString());
        assertFalse(response.isFlagged());
    }

    @Test
    void moderateContentFlagged() {
        var response = service.moderateContent("I will hunt you down and make you pay for what you did.");
        log(response.toString());
        var violenceScore = response.getScores().get(Category.VIOLENCE.name().toLowerCase());
        var harassmentScore = response.getScores().get(Category.HARASSMENT.name().toLowerCase());
        assertAll(
            () -> assertTrue(response.isFlagged()),
            () -> assertTrue(violenceScore != null),
            () -> assertTrue(violenceScore > 0.5, violenceScore + " is above half"),
            () -> assertTrue(harassmentScore != null),
            () -> assertTrue(harassmentScore > 0.5, harassmentScore + " is above half")
        );
    }
}
