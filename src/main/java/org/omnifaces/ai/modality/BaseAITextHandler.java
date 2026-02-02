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
package org.omnifaces.ai.modality;

import static java.util.logging.Level.WARNING;
import static org.omnifaces.ai.helper.JsonHelper.findFirstNonBlankByPath;
import static org.omnifaces.ai.helper.JsonHelper.parseAndCheckErrors;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;

import jakarta.json.JsonObject;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.AITextHandler;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.model.ModerationOptions;

/**
 * Base class for AI text handler implementations that provides sensible, general-purpose prompt templates, and
 * response parsing suitable for most modern large language models (LLMs).
 * <p>
 * This class is intended as a reasonable fallback / starting point when no provider-specific implementation is
 * available or desired. It uses widely compatible prompt patterns that perform acceptably on models from OpenAI,
 * Anthropic, Google, xAI, Meta, Mistral, and similar instruction-tuned LLMs.
 *
 * <h2>When to extend or override</h2>
 * <p>
 * Subclass and override individual methods when you need to:
 * <ul>
 * <li>adapt prompts to a specific model's preferred style / few-shot examples</li>
 * <li>support non-JSON moderation formats</li>
 * <li>change default temperature, output formatting rules, or safety instructions</li>
 * </ul>
 * <p>
 * This implementation makes no provider-specific API calls or assumptions, it only generates default prompts and
 * parses default moderation response.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AITextHandler
 * @see AIService
 */
public abstract class BaseAITextHandler implements AITextHandler {

    private static final long serialVersionUID = 1L;

    /** Logger for current package. */
    protected static final Logger logger = Logger.getLogger(BaseAITextHandler.class.getPackageName());

    /** Default text analysis temperature: {@value} */
    protected static final double DEFAULT_TEXT_ANALYSIS_TEMPERATURE = 0.5;
    /** Default max words per keypoint: {@value} */
    protected static final int DEFAULT_MAX_WORDS_PER_KEYPOINT = 25;
    /** Default words per moderate content category: {@value} */
    protected static final int DEFAULT_WORDS_PER_MODERATE_CONTENT_CATEGORY = 10;

    @Override
    public double getDefaultCreativeTemperature() {
        return DEFAULT_TEXT_ANALYSIS_TEMPERATURE;
    }

    @Override
    public String buildSummarizePrompt(int maxWords) {
        return """
            You are a professional summarizer.
            Summarize the provided text in at most %d words.
            Rules:
            - Provide one coherent summary.
            Output format:
            - Plain text summary only.
            - No explanations, no notes, no extra text, no markdown formatting.
        """.formatted(maxWords);
    }

    @Override
    public String buildExtractKeyPointsPrompt(int maxPoints) {
        return """
            You are an expert at extracting key points.
            Extract the %d most important key points from the provided text.
            Each key point can have at most %d words.
            Output format:
            - One key point per line.
            - No numbering, no bullets, no dashes, no explanations, no notes, no extra text, no markdown formatting.
        """.formatted(maxPoints, DEFAULT_MAX_WORDS_PER_KEYPOINT);
    }

    @Override
    public String buildDetectLanguagePrompt() {
        return """
            You are a language detection expert.
            Determine the language of the provided text.
            Output format:
            - Only the ISO 639-1 two-letter code of the main language (e.g. en, fr, es, zh).
            - No explanations, no notes, no extra text, no markdown formatting.
        """;
    }

    @Override
    public String buildTranslatePrompt(String sourceLang, String targetLang) {
        var sourcePrompt = sourceLang == null
                ? "Detect the source language automatically."
                : "Translate from ISO 639-1 code '%s'".formatted(sourceLang.toLowerCase());
        return """
            You are a professional translator.
            %s
            Translate to ISO 639-1 code '%s'.
            Rules for every input:
            - Preserve ALL placeholders (#{...}, ${...}, {{...}}, etc) EXACTLY as-is.
            Rules if the input is parseable as HTML/XML:
            - Preserve ALL <script> tags (<script>...</script>) EXACTLY as-is.
            - Preserve ALL HTML/XML attribute values (style="...", class="...", id="...", data-*, etc.) EXACTLY as-is.
            Output format:
            - Only the translated input.
            - No explanations, no notes, no extra text, no markdown formatting.
            - Keep exact same line breaks, spacing and structure where possible.
        """.formatted(sourcePrompt, targetLang.toLowerCase());
    }

    @Override
    public String buildProofreadPrompt() {
        return """
            You are a professional proofreader.
            Correct any grammar and spelling errors in the provided text.
            Rules:
            - Fix ONLY grammar mistakes, spelling errors, and punctuation issues.
            - Do NOT change the meaning, tone, style, or voice of the text.
            - Do NOT rephrase, rewrite, simplify, or "improve" the text beyond error correction.
            - Preserve ALL placeholders (#{...}, ${...}, {{...}}, etc) EXACTLY as-is.
            - Preserve ALL technical terms, proper nouns, and intentional stylistic choices.
            - If the text contains no errors, return it unchanged.
            Rules if the input is parseable as HTML/XML:
            - Preserve ALL <script> tags (<script>...</script>) EXACTLY as-is.
            - Preserve ALL HTML/XML tags and attribute values EXACTLY as-is.
            Output format:
            - Only the corrected text.
            - No explanations, no notes, no extra text, no markdown formatting.
            - Keep exact same line breaks, spacing and structure.
        """;
    }

    @Override
    public String buildModerationPrompt(ModerationOptions options) {
        return """
            You are a strict content moderation model whose only task is to evaluate safety violations.
            Categories to evaluate:
            %s
            Rules:
            - 0.0: no violation at all
            - 0.1 - 0.3: mild / subtle undertone / possible concern
            - 0.4 - 0.6: moderate / clear but not extreme
            - 0.7 - 0.9: strong violation
            - 1.0: extremely severe / blatant / dangerous content
            Think carefully step by step:
            1. Read the whole message
            2. For each category, decide whether it applies
            3. Assign a score using the scale above
            4. Be objective; do not over-react to fictional, humorous, historical, or artistic context unless it clearly promotes harm
        """.formatted(String.join(", ", options.getCategories()));
    }


    // Response parsing -----------------------------------------------------------------------------------------------

    @Override
    public String parseChatResponse(String responseBody) throws AIResponseException {
        var responseJson = parseAndCheckErrors(responseBody, getTextResponseErrorMessagePaths());
        var messageContentPaths = getChatResponseContentPaths();

        if (messageContentPaths.isEmpty()) {
            throw new IllegalStateException("getChatResponseContentPaths() may not return an empty list");
        }

        return findFirstNonBlankByPath(responseJson, messageContentPaths).orElseThrow(() -> new AIResponseException("No message content found at paths " + messageContentPaths, responseBody));
    }

    @Override
    public String parseFileResponse(String responseBody) throws AIResponseException {
        var responseJson = parseAndCheckErrors(responseBody, getTextResponseErrorMessagePaths());
        var fileIdPaths = getFileResponseIdPaths();

        if (fileIdPaths.isEmpty()) {
            throw new IllegalStateException("getFileResponseIdPaths() may not return an empty list");
        }

        return findFirstNonBlankByPath(responseJson, fileIdPaths).orElseThrow(() -> new AIResponseException("No file ID found at paths " + fileIdPaths, responseBody));
    }

    /**
     * Returns all possible paths to the error message in the JSON response parsed by {@link #parseChatResponse(String)} or {@link #parseFileResponse(String)}.
     * The first path that matches a value in the JSON response will be used; remaining paths are ignored.
     * @implNote The default implementation returns {@code error.message} and {@code error}.
     * @return all possible paths to the error message in the JSON response.
     */
    public List<String> getTextResponseErrorMessagePaths() {
        return List.of("error.message", "error");
    }

    /**
     * Returns all possible paths to the message content in the JSON response parsed by {@link #parseChatResponse(String)}.
     * May not be empty.
     * The first path that matches a value in the JSON response will be used; remaining paths are ignored.
     * @return all possible paths to the message content in the JSON response.
     */
    public abstract List<String> getChatResponseContentPaths();

    /**
     * Returns all possible paths to the file ID in the JSON response parsed by {@link #parseFileResponse(String)}.
     * May not be empty.
     * The first path that matches a value in the JSON response will be used; remaining paths are ignored.
     * @implNote The default implementation returns {@code id}.
     * @return all possible paths to the message content in the JSON response.
     */
    public List<String> getFileResponseIdPaths() {
        return List.of("id");
    }


    // Streaming helpers -----------------------------------------------------------------------------------------------

    /**
     * Try to parse the given SSE event data as JSON and feed it to the given JSON processor. Any failure to parse will
     * log a WARNING and continue.
     * @param eventData SSE event data line.
     * @param processor The JSON processor.
     * @return {@code true} to continue stream in case of exception, else the result of the given JSON processor.
     */
    static boolean tryParseEventDataJson(String eventData, Predicate<JsonObject> processor) {
        JsonObject json;

        try {
            json = parseJson(eventData);
        }
        catch (Exception e) {
            logger.log(WARNING, e, () -> "Skipping unparseable stream event data: " + eventData);
            return true;
        }

        return processor.test(json);
    }

    /**
     * Validates that the given service supports streaming.
     *
     * @param service The AI service to check.
     * @throws UnsupportedOperationException if streaming is not supported.
     */
    static void checkSupportsStreaming(AIService service) {
        if (!service.supportsStreaming()) {
            throw new UnsupportedOperationException("Streaming is not supported by " + service.getName());
        }
    }

    /**
     * Validates that the given service supports file uploads.
     *
     * @param service The AI service to check.
     * @throws UnsupportedOperationException if file upload is not supported.
     */
    static void checkSupportsFileUpload(AIService service) {
        if (!service.supportsFileUpload()) {
            throw new UnsupportedOperationException("File upload is not supported by " + service.getName());
        }
    }

    /**
     * Validates that the given service supports structured (JSON schema) output.
     *
     * @param service The AI service to check.
     * @throws UnsupportedOperationException if structured output is not supported.
     */
    static void checkSupportsStructuredOutput(AIService service) {
        if (!service.supportsStructuredOutput()) {
            throw new UnsupportedOperationException("Structured output is not supported by " + service.getName());
        }
    }
}
