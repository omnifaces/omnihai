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

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.ModerationOptions.Category;

/**
 * The text operations a service serves by asking the chat model rather than by an endpoint of its own: what each one asks for, and what it makes of the answer.
 */
class BaseAIServiceDelegatesTest {

    // =================================================================================================================
    // Summarizing, extracting and rewriting
    // =================================================================================================================

    @Test
    void summarizeAsync_asksForTheGivenNumberOfWords() {
        var service = scripted("A short summary.");

        assertEquals("A short summary.", service.summarizeAsync("A very long text.", 25).join());
        assertTrue(service.lastSystemPrompt().contains("25"), service.lastSystemPrompt());
    }

    /**
     * The key points arrive as one line each, so blank lines and stray indentation are dropped rather than passed on as points of their own.
     */
    @Test
    void extractKeyPointsAsync_readsOnePointPerLine() {
        var service = scripted("  First point  \n\n  Second point\n");

        assertEquals(List.of("First point", "Second point"), service.extractKeyPointsAsync("A very long text.", 5).join());
        assertTrue(service.lastSystemPrompt().contains("5"), service.lastSystemPrompt());
    }

    @Test
    void proofreadAsync_answersTheCorrectedText() {
        assertEquals("The corrected text.", scripted("The corrected text.").proofreadAsync("The corected text.").join());
    }

    @Test
    void translateAsync_namesBothLanguagesInThePrompt() {
        var service = scripted("Hallo daar.");

        assertEquals("Hallo daar.", service.translateAsync("Hello there.", "EN", "nl").join());
        assertTrue(service.lastSystemPrompt().contains("from ISO 639-1 code 'en'"), service.lastSystemPrompt());
        assertTrue(service.lastSystemPrompt().contains("to ISO 639-1 code 'nl'"), service.lastSystemPrompt());
    }

    /**
     * A source language left out is one the AI works out for itself, rather than one it is told to assume.
     */
    @Test
    void translateAsync_withoutASourceLanguage_asksTheAiToDetectIt() {
        var service = scripted("Hallo daar.");

        service.translateAsync("Hello there.", null, "nl").join();

        assertTrue(service.lastSystemPrompt().contains("Detect the source language"), service.lastSystemPrompt());
    }

    @Test
    void translateAsync_withoutATargetLanguage_isRefused() {
        var service = scripted("Hallo daar.");

        assertThrows(IllegalArgumentException.class, () -> service.translateAsync("Hello there.", "en", " "));
    }

    // =================================================================================================================
    // Detecting the language
    // =================================================================================================================

    /**
     * The answer names a language rather than states a code, so whatever it carries beside the letters is stripped.
     */
    @Test
    void detectLanguageAsync_keepsTheLettersOfTheAnswerAlone() {
        assertEquals("dutch", scripted(" Dutch.\n").detectLanguageAsync("Hallo daar.").join());
    }

    @Test
    void detectLanguageAsync_emptyAnswer_isRejected() {
        var future = scripted("  ").detectLanguageAsync("Hallo daar.");

        assertInstanceOf(AIResponseException.class, assertThrows(CompletionException.class, future::join).getCause());
    }

    // =================================================================================================================
    // Classifying
    // =================================================================================================================

    @Test
    void classifyAsync_answersTheLabelAndItsConfidence() {
        var service = scripted("{\"label\":\"spam\",\"confidence\":0.9}");

        var result = service.classifyAsync("Buy now!", List.of("spam", "ham")).join();

        assertEquals("spam", result.label());
        assertTrue(service.lastSystemPrompt().contains("spam"), service.lastSystemPrompt());
    }

    /**
     * A label repeated only takes up room in the prompt, so the duplicates are dropped before the AI is asked.
     */
    @Test
    void classifyAsync_repeatedLabel_reachesTheAiOnce() {
        var service = scripted("{\"label\":\"spam\",\"confidence\":0.9}");

        service.classifyAsync("Buy now!", List.of("spam", " spam ", "ham")).join();

        assertEquals(1, service.lastSystemPrompt().split("spam", -1).length - 1, service.lastSystemPrompt());
    }

    @Test
    void classifyAllAsync_answersAScorePerLabel() {
        var service = scripted("{\"results\":[{\"label\":\"spam\",\"confidence\":0.9},{\"label\":\"ham\",\"confidence\":0.1}]}");

        var results = service.classifyAllAsync("Buy now!", List.of("spam", "ham")).join();

        assertEquals(List.of("spam", "ham"), results.stream().map(result -> result.label()).toList());
    }

    // =================================================================================================================
    // Moderating
    // =================================================================================================================

    @Test
    void moderateContentAsync_answersTheScoreOfEveryRequestedCategory() {
        var service = scripted("{\"scores\":{\"hate\":0.8}}");
        var options = ModerationOptions.newBuilder().categories(Category.HATE).threshold(0.5).build();

        var result = service.moderateContentAsync("I hate you.", options).join();

        assertTrue(result.isFlagged());
        assertEquals(0.8, result.getScores().get("hate"));
    }

    @Test
    void moderateContentAsync_contentWhichIsBlank_isRefused() {
        var service = scripted("{}");

        assertThrows(IllegalArgumentException.class, () -> service.moderateContentAsync(" ", ModerationOptions.DEFAULT));
    }

    @Test
    void moderateContentAsync_answerWithoutAnyScore_isNotFlagged() {
        assertFalse(scripted("{}").moderateContentAsync("Hello there.", ModerationOptions.DEFAULT).join().isFlagged());
    }

    private static ScriptedAIService scripted(String... answers) {
        return new ScriptedAIService(CustomAIService.newConfig(), answers);
    }

    /**
     * A provider which answers what it was scripted with rather than addressing an endpoint, recording what it was asked.
     */
    private static final class ScriptedAIService extends CustomAIService {

        private static final long serialVersionUID = 1L;

        private final transient Queue<String> answers;
        private final transient List<ChatOptions> asked = new ArrayList<>();

        private ScriptedAIService(AIConfig config, String... answers) {
            super(config);
            this.answers = new ArrayDeque<>(List.of(answers));
        }

        @Override
        public CompletableFuture<String> chatAsync(ChatInput input, ChatOptions options) {
            asked.add(options);
            return completedFuture(answers.poll());
        }

        private String lastSystemPrompt() {
            return asked.get(asked.size() - 1).getSystemPrompt();
        }

    }

}
