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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ModerationOptions.Category;
import org.opentest4j.TestAbortedException;

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
    void chatWithMemory() {
        var options = ChatOptions.newBuilder()
            .systemPrompt("You are a helpful assistant. Reply concisely.")
            .withMemory()
            .build();

        var response1 = service.chat("My name is Bob.", options);
        log("response1: " + response1);

        var response2 = service.chat("What is my name?", options);
        log("response2: " + response2);

        var history = options.getHistory();

        assertAll(
            () -> assertTrue(response2.contains("Bob"), response2),
            () -> assertEquals(4, history.size()),
            () -> assertEquals(Role.USER, history.get(0).role()),
            () -> assertEquals("My name is Bob.", history.get(0).content()),
            () -> assertEquals(Role.ASSISTANT, history.get(1).role()),
            () -> assertEquals(Role.USER, history.get(2).role()),
            () -> assertEquals("What is my name?", history.get(2).content()),
            () -> assertEquals(Role.ASSISTANT, history.get(3).role())
        );
    }

    @Test
    void chatWithoutMemory() {
        var options = ChatOptions.newBuilder()
            .systemPrompt("You are a helpful assistant. Reply concisely.")
            .build();

        var response1 = service.chat("My name is Bob.", options);
        log("response1: " + response1);

        var response2 = service.chat("What is my name?", options);
        log("response2: " + response2);

        assertAll(
            () -> assertFalse(response2.contains("Bob"), response2),
            () -> assertFalse(options.hasMemory()),
            () -> assertThrows(IllegalStateException.class, options::getHistory)
        );
    }

    @Test
    void chatStream() {
        if (!service.supportsStreaming()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var responseBuffer = new StringBuilder();
        service.chatStream("Reply with only: OK", responseBuffer::append).join();
        var response = responseBuffer.toString();
        log(response);
        assertTrue(response.contains("OK"), response);
    }

    @Test
    void chatWithAttachedFile() {
        if (!service.supportsFileUpload()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var input = ChatInput.newBuilder()
            .attach(readAllBytes("/dummy.pdf"))
            .message("What exactly does this PDF contain?")
            .build();
        var response = service.chat(input);
        log(response);
        assertTrue(response.toLowerCase().contains("dummy pdf"), response);
    }

    public record Capital(String city, String country) {}
    public record Capitals(List<Capital> capitals) {}

    @Test
    void chatWithStructuredOutput() {
        if (!service.supportsStructuredOutput()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var response = service.chat("What are the capitals of Curacao and The Netherlands?", Capitals.class);
        log(response.toString());
        assertAll(
            () -> assertEquals(2, response.capitals().size()),
            () -> assertTrue(response.capitals().get(0).city().toLowerCase().contains("willemstad")),
            () -> assertTrue(response.capitals().get(0).country().toLowerCase().contains("cura")),
            () -> assertTrue(response.capitals().get(1).city().toLowerCase().contains("amsterdam")),
            () -> assertTrue(response.capitals().get(1).country().toLowerCase().contains("netherlands"))
        );
    }

    @Test
    void summarize() {
        var response = service.summarize("The quick brown fox jumps over the lazy dog near the river.", 5);
        log(response);
        assertFalse(response.isBlank(), response);
        assertAll(
            () -> assertTrue(response.split("\\s+").length <= 6, "max 6 words (slack of 1)"),
            () -> assertTrue(response.toLowerCase().contains("fox")),
            () -> assertTrue(response.toLowerCase().contains("jump") || response.toLowerCase().contains("leap")),
            () -> assertTrue(response.toLowerCase().contains("dog"))
        );

    }

    @Test
    void extractKeyPoints() {
        var response = service.extractKeyPoints("Willemstad is the capital of Curacao and Amsterdam is the capital of The Netherlands.", 2);
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
    void proofread() {
        var response = service.proofread("<p style='color:bleu'>Teh cat #{bean.vreb} on teh chiar.</p><script>{'key':'vaule'}</script>");
        log(response);
        assertEquals("<p style='color:bleu'>The cat #{bean.vreb} on the chair.</p><script>{'key':'vaule'}</script>", response);
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
