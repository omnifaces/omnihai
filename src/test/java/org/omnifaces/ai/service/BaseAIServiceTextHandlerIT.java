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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.omnifaces.ai.model.ChatOptions.DETERMINISTIC;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.Location;
import org.omnifaces.ai.model.ChatOptions.ReasoningEffort;
import org.omnifaces.ai.model.ChatUsage;
import org.omnifaces.ai.model.ClassificationResult;
import org.omnifaces.ai.model.ModerationOptions.Category;
import org.opentest4j.TestAbortedException;

/**
 * Base class for IT on text-analyzer-related methods of AI service.
 */
abstract class BaseAIServiceTextHandlerIT extends AIServiceIT {

    /**
     * Whether the AI provider reports the reasoning tokens as part of the chat usage. A provider which does not report them leaves them at {@code -1}.
     *
     * @return Whether the AI provider reports the reasoning tokens.
     */
    protected boolean supportsReasoningTokens() {
        return false;
    }

    /**
     * Whether the AI provider uploads an attached file to its files API and references it by ID, rather than inlining it in the request payload.
     *
     * @return Whether the AI provider uploads an attached file to its files API.
     */
    protected boolean supportsFilesApi() {
        return false;
    }

    @Test
    void chat() {
        var response = service.chat("Reply with only: OK");
        log(response);
        assertTrue(response.contains("OK"), "response must contain 'OK'");
    }

    @Test
    void chatWithMemory() {
        var options = ChatOptions.newBuilder()
            .systemPrompt("You are a helpful assistant. Reply concisely.")
            .withMemory()
            .build();

        var response1 = service.chat("My name is Bob.", options);
        var usage1 = options.getLastUsage();
        log("response1: " + response1);
        log("usage1: " + usage1);

        var response2 = service.chat("What is my name?", options);
        var usage2 = options.getLastUsage();
        log("response2: " + response2);
        log("usage2: " + usage2);

        var history = options.getHistory();

        assertAll(
            () -> assertTrue(response2.contains("Bob"), "response must contain 'Bob'"),
            () -> assertEquals(4, history.size()),
            () -> assertEquals(Role.USER, history.get(0).role()),
            () -> assertEquals("My name is Bob.", history.get(0).content()),
            () -> assertEquals(Role.ASSISTANT, history.get(1).role()),
            () -> assertEquals(Role.USER, history.get(2).role()),
            () -> assertEquals("What is my name?", history.get(2).content()),
            () -> assertEquals(Role.ASSISTANT, history.get(3).role()),
            () -> assertUsage(usage1),
            () -> assertUsage(usage2)
        );
    }

    @Test
    void chatWithoutMemory() {
        var options = ChatOptions.newBuilder()
            .systemPrompt("You are a helpful assistant. Reply concisely.")
            .build();

        var response1 = service.chat("My name is Bob.", options);
        var usage1 = options.getLastUsage();
        log("response1: " + response1);
        log("usage1: " + usage1);

        var response2 = service.chat("What is my name?", options);
        var usage2 = options.getLastUsage();
        log("response2: " + response2);
        log("usage2: " + usage2);

        assertAll(
            () -> assertFalse(response2.contains("Bob"), "response should not contain 'Bob'"),
            () -> assertFalse(options.hasMemory(), "options should not have memory"),
            () -> assertThrows(IllegalStateException.class, options::getHistory),
            () -> assertUsage(usage1),
            () -> assertUsage(usage2)
        );
    }

    @Test
    void chatStream() {
        if (!service.supportsStreaming()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var options = ChatOptions.newBuilder().build();
        var responseBuffer = new StringBuilder();

        service.chatStream("Reply with only: OK", options, responseBuffer::append).join();
        var response = responseBuffer.toString();
        var usage = options.getLastUsage();
        log("response: " + response);
        log("usage: " + usage);

        assertAll(
            () -> assertTrue(response.contains("OK"), response),
            () -> assertUsage(usage)
        );
    }

    private void assertUsage(ChatUsage usage) {
        assertAll(
            () -> assertNotNull(usage),
            () -> assertTrue(usage.inputTokens() > 0, "inputTokens must be positive: " + usage.inputTokens()),
            () -> assertTrue(usage.outputTokens() > 0, "outputTokens must be positive: " + usage.outputTokens()),
            () -> assertTrue(usage.totalTokens() > 0, "totalTokens must be positive: " + usage.totalTokens()),
            () -> assertEquals(usage.inputTokens() + usage.outputTokens(), usage.totalTokens(), "totalTokens = inputTokens + outputTokens")
        );

        if (supportsReasoningTokens()) {
            assertTrue(usage.reasoningTokens() > -1, "reasoningTokens must be set: " + usage.reasoningTokens());
            assertTrue(usage.reasoningTokens() <= usage.outputTokens(), "reasoningTokens <= outputTokens");
        }
        else {
            assertEquals(-1, usage.reasoningTokens(), "reasoningTokens must be -1: " + usage.reasoningTokens());
        }
    }

    @Test
    void chatWithAttachedFile() {
        if (!service.supportsFileAttachments()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var input = ChatInput.newBuilder()
            .attach(readAllBytes("/dummy.pdf"))
            .message("Extract the contents of this PDF. No explanation.")
            .build();
        var response = service.chat(input, DETERMINISTIC);
        log(response);
        assertTrue(response.contains("Dummy PDF file"), "response must contain 'Dummy PDF file'");
    }

    @Test
    void chatWithAttachedFileAndMemory() {
        if (!service.supportsFileAttachments()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var input = ChatInput.newBuilder()
            .attach(getPath("/dummy.pdf"))
            .message("Extract the contents of this PDF. No explanation.")
            .build();
        var options = ChatOptions.newBuilder()
            .temperature(ChatOptions.DETERMINISTIC_TEMPERATURE)
            .withMemory()
            .build();
        var response1 = service.chat(input, options);

        if (options.getHistory().get(0).uploadedFiles().isEmpty()) {
            if (supportsFilesApi()) {
                fail(getProvider() + " is supposed to support files API!");
            }

            throw new TestAbortedException("Not supported by " + getProvider());
        }

        log(response1);
        assertTrue(response1.contains("Dummy PDF file"), "response must contain 'Dummy PDF file'");

        var response2 = service.chat("How many pages does this PDF have?", options);
        log(response2);
        assertTrue(response2.contains("1") || response2.toLowerCase().contains("one"), "response must contain '1' or 'one'");
    }

    public record Capital(String city, String country) {
    }

    public record Capitals(List<Capital> capitals) {
    }

    @Test
    void chatWithStructuredOutput() {
        if (!service.supportsStructuredOutput()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var response = service.chat("What are the capitals of Curacao and The Netherlands?", Capitals.class);
        log(response.toString());
        assertAll(
            () -> assertEquals(2, response.capitals().size(), "response must have 2 capitals"),
            () -> assertTrue(response.capitals().get(0).city().toLowerCase().contains("willemstad"), "first city must contain 'willemstad'"),
            () -> assertTrue(response.capitals().get(0).country().toLowerCase().contains("cura"), "first country must contain 'cura'"),
            () -> assertTrue(response.capitals().get(1).city().toLowerCase().contains("amsterdam"), "second city must contain 'amsterdam'"),
            () -> assertTrue(response.capitals().get(1).country().toLowerCase().contains("netherlands"), "second country must contain 'netherlands'")
        );
    }

    @Test
    void webSearch() {
        if (!service.supportsWebSearch()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var response = service.webSearch("What is the current stock price of Tesla?");
        log(response);
        assertAll(
            () -> assertTrue(response.contains("TSLA"), "response contains 'TSLA'"),
            () -> assertTrue(response.contains("$") || response.contains("USD"), "response contains '$' or 'USD'"),
            () -> assertTrue(Pattern.compile("\\d\\.\\d{2}").matcher(response).find(), "response contains #0.00")
        );
    }

    @Test
    void webSearchWithLocation() {
        if (!service.supportsWebSearch()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var miami = new Location("US", null, "Miami");
        var response = service.webSearch("What is the current weather? High/Low?", miami);
        log(response);
        assertAll(
            () -> assertTrue(response.contains("Miami") || response.contains("MI"), "response contains 'Miami' or 'MI'"),
            () -> assertTrue(response.toLowerCase().contains("high"), "response contains 'high'"),
            () -> assertTrue(response.toLowerCase().contains("low"), "response contains 'low'")
        );
    }

    public record StockPrice(String ticker, BigDecimal price, String currencyCode) {
    }

    @Test
    void webSearchWithStructuredOutput() {
        if (!service.supportsWebSearch()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var response = service.webSearch("What is the current stock price of Tesla?", StockPrice.class);
        log(response.toString());
        assertAll(
            () -> assertNotNull(response.ticker(), "ticker is set"),
            () -> assertEquals("TSLA", response.ticker()),
            () -> assertNotNull(response.price(), "price is set"),
            () -> assertTrue(response.price().compareTo(BigDecimal.ZERO) >= 0, "price is not negative"),
            () -> assertNotNull(response.currencyCode(), "currency code is set"),
            () -> assertEquals("USD", response.currencyCode())
        );
    }

    @Test
    void chatWithReasoningEffort() {
        if (!service.supportsReasoningEffort()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var low = ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.LOW).build();
        var high = ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.HIGH).build();
        var prompt = "Think carefully step by step, then answer: in how many distinct ways can 87 cents be made from US coins of 1, 5, 10 and 25 cents? "
            + "Do not output anything other than the final number.";

        service.chat(prompt, low);
        service.chat(prompt, high);

        var lowUsage = low.getLastUsage();
        var highUsage = high.getLastUsage();
        log("low usage: " + lowUsage);
        log("high usage: " + highUsage);

        assertNotNull(lowUsage, "usage must be recorded for LOW");
        assertNotNull(highUsage, "usage must be recorded for HIGH");

        // A provider which breaks the reasoning out is asked for it directly. Elsewhere outputTokens carries it: OpenAI rolls reasoning tokens into
        // output_tokens, Anthropic rolls thinking tokens into output_tokens, and Google's parseChatUsage adds thoughtsTokenCount to candidatesTokenCount.
        var signal = supportsReasoningTokens() ? "reasoningTokens" : "outputTokens";
        var lowTokens = supportsReasoningTokens() ? lowUsage.reasoningTokens() : lowUsage.outputTokens();
        var highTokens = supportsReasoningTokens() ? highUsage.reasoningTokens() : highUsage.outputTokens();

        assertAll(
            () -> assertTrue(lowTokens > 0, "LOW " + signal + " must be > 0: " + lowTokens),
            () -> assertTrue(highTokens > 0, "HIGH " + signal + " must be > 0: " + highTokens),
            () -> assertTrue(highTokens > lowTokens, "HIGH " + signal + " (" + highTokens + ") must exceed LOW (" + lowTokens + ")")
        );
    }

    @Test
    void summarize() {
        var response = service.summarize("The quick brown fox jumps over the lazy dog near the river.", 5);
        log(response);
        assertFalse(response.isBlank(), "response should not be blank");
        assertAll(
            () -> assertTrue(response.split("\\s+").length <= 6, "max 6 words (slack of 1)"),
            () -> assertTrue(response.toLowerCase().contains("fox"), "response contains 'fox'"),
            () -> assertTrue(response.toLowerCase().contains("jump") || response.toLowerCase().contains("leap"), "response contains 'jump' or 'leap'"),
            () -> assertTrue(response.toLowerCase().contains("dog"), "response contains 'dog'")
        );

    }

    @Test
    void extractKeyPoints() {
        var response = service.extractKeyPoints("Willemstad is the capital of Curacao and Amsterdam is the capital of The Netherlands.", 2);
        log(response.toString());
        assertFalse(response.isEmpty(), "response should not be empty");
        assertAll(
            () -> assertEquals(2, response.size(), "response must have 2 keypoints"),
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
        var response = service
            .translate("<section style='color:blue'>The cat #{bean.verb} on the chair.</section><script>{'key':'value'}</script>", "en", "nl");
        log(response);
        assertEquals("<section style='color:blue'>De kat #{bean.verb} op de stoel.</section><script>{'key':'value'}</script>", response);
    }

    @Test
    void translateAutomatically() {
        var response = service
            .translate("<section style='color:blauw'>De kat #{boon.werkwoord} op de stoel.</section><script>{'sleutel':'waarde'}</script>", null, "en");
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
        assertFalse(response.isFlagged(), "content should not be flagged");
    }

    @Test
    void classify() {
        if (!service.supportsStructuredOutput()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var response = service.classify("My package never arrived and the tracking has not moved in a week.", List.of("billing", "shipping", "technical"));
        log(response.toString());
        assertAll(
            () -> assertEquals("shipping", response.label(), "response must be classified as shipping"),
            () -> assertTrue(response.confidence() > 0.5, "confidence " + response.confidence() + " must be above half")
        );
    }

    @Test
    void classifyWithVarargs() {
        if (!service.supportsStructuredOutput()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var response = service.classify("Buy cheap watches now!!!", "spam", "ham");
        log(response.toString());
        assertEquals("spam", response.label(), "response must be classified as spam");
    }

    @Test
    void classifyWithTooFewLabels() {
        var labels = List.of("only-one");

        assertThrows(IllegalArgumentException.class, () -> service.classify("Anything", labels));
    }

    @Test
    void classifyAll() {
        if (!service.supportsStructuredOutput()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var response = service.classifyAll("My package never arrived and I was charged for it twice.", List.of("billing", "shipping", "technical"));
        log(response.toString());
        assertAll(
            () -> assertEquals(3, response.size(), "every offered label must be scored"),
            () -> assertTrue(confidenceOf(response, "billing") > 0.5, "billing must apply to a double charge"),
            () -> assertTrue(confidenceOf(response, "shipping") > 0.5, "shipping must apply to a package which never arrived"),
            () -> assertTrue(confidenceOf(response, "technical") < 0.5, "technical must not apply"),
            () -> assertTrue(response.get(0).confidence() >= response.get(2).confidence(), "the best fitting label must come first")
        );
    }

    @Test
    void classifyAllWithNoLabelFitting() {
        if (!service.supportsStructuredOutput()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        var response = service.classifyAll("The mitochondrion is the powerhouse of the cell.", "billing", "shipping");
        log(response.toString());
        assertTrue(response.stream().allMatch(result -> result.confidence() < 0.5), "no label may fit an unrelated text");
    }

    private static double confidenceOf(List<ClassificationResult> results, String label) {
        return results.stream().filter(result -> label.equals(result.label())).mapToDouble(ClassificationResult::confidence).findFirst().orElse(-1);
    }

    @Test
    void moderateContentFlagged() {
        var response = service.moderateContent("I will hunt you down and make you pay for what you did.");
        log(response.toString());
        var violenceScore = response.getScores().get(Category.VIOLENCE.name().toLowerCase());
        var harassmentScore = response.getScores().get(Category.HARASSMENT.name().toLowerCase());
        assertAll(
            () -> assertTrue(response.isFlagged(), "content must be flagged"),
            () -> assertNotNull(violenceScore, "violence score must be set"),
            () -> assertTrue(violenceScore > 0.5, "violence score " + violenceScore + " must be above half"),
            () -> assertNotNull(harassmentScore, "harassment score must be set"),
            () -> assertTrue(harassmentScore > 0.5, "harassment score " + harassmentScore + " must be above half")
        );
    }

}
