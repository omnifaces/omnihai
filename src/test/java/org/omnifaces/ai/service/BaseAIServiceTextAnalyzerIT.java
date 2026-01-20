package org.omnifaces.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
    }

    @Test
    void extractKeyPoints() {
        var response = service.extractKeyPoints("Willemstad is the capital of Curacao. Amsterdam is the capital of The Netherlands.", 2);
        log(response.toString());
        assertFalse(response.isEmpty(), response.toString());
        assertEquals(2, response.size(), response.toString());
    }

    @Test
    void detectLanguage() {
        var response = service.detectLanguage("De kat zat op de mat.");
        log(response);
        assertEquals("nl", response);
    }

    @Test
    void translate() {
        var response = service.translate("<p style='color:blue'>The #{bean.animal} sat on the mat.</p>", "en", "nl");
        log(response);
        assertTrue(response.equals("<p style='color:blue'>De #{bean.animal} zat op de mat.</p>")
                || response.equals("<p style='color:blue'>Het #{bean.animal} zat op de mat.</p>"), response);
    }
}
