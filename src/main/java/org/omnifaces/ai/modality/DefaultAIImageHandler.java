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

import static org.omnifaces.ai.helper.JsonHelper.findFirstNonBlankByPaths;
import static org.omnifaces.ai.helper.JsonHelper.parseAndCheckErrors;

import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

import org.omnifaces.ai.AIImageHandler;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;

/**
 * Default AI image handler implementation that provides general-purpose prompt templates, and response parsing
 * suitable for most current vision-capable models.
 * <p>
 * This class is intended as a fallback when no provider-specific implementation is available. It uses patterns that
 * work reasonably well with GPT, Claude, Gemini, Grok, Llama and similar multimodal models.
 *
 * <h2>When to extend or override</h2>
 * <p>
 * Subclass and override individual methods when you need to:
 * <ul>
 * <li>adapt prompts to a specific model's preferred style or detail level</li>
 * <li>support low/high resolution modes</li>
 * <li>add safety instructions or strict output format constraints</li>
 * <li>optimize for accessibility-focused alt text</li>
 * </ul>
 * <p>
 * This implementation makes no provider-specific API calls or assumptions, it only generates default prompts.

 * @author Bauke Scholtz
 * @since 1.0
 * @see AIImageHandler
 * @see AIService
 */
public class DefaultAIImageHandler implements AIImageHandler {

    private static final long serialVersionUID = 1L;

    /** Logger for current package. */
    protected static final Logger logger = Logger.getLogger(DefaultAIImageHandler.class.getPackageName());

    /** Default max words per alt text sentence: {@value} */
    protected static final int DEFAULT_MAX_WORDS_PER_ALT_TEXT_SENTENCE = 30;

    @Override
    public String buildAnalyzeImagePrompt() {
        return """
            You are an expert at analyzing images.
            Describe this image in detail.
            Rules:
            - Focus on: main subject, key actions/details, visual style if relevant, and intended purpose.
            Output format:
            - Plain text description only.
            - No explanations, no notes, no extra text, no markdown formatting.
        """;
    }

    @Override
    public String buildGenerateAltTextPrompt() {
        return """
            You are an expert at writing web alt text.
            Write concise, descriptive alt text for the image in at most 2 sentences.
            Each sentence can have at most %d words.
            Rules:
            - Focus on: main subject, key actions/details, visual style if relevant, and intended purpose.
            - Do not include phrases like "image of" unless necessary.
            Output format:
            - Plain text description only.
            - No explanations, no notes, no extra text, no markdown formatting.
        """.formatted(DEFAULT_MAX_WORDS_PER_ALT_TEXT_SENTENCE);
    }


    // Response parsing -----------------------------------------------------------------------------------------------

    @Override
    public byte[] parseImageContent(String responseBody) throws AIResponseException {
        var responseJson = parseAndCheckErrors(responseBody, getImageResponseErrorMessagePaths());
        var imageContentPaths = getImageResponseContentPaths();

        if (imageContentPaths.isEmpty()) {
            throw new IllegalStateException("getImageResponseContentPaths() may not return an empty list");
        }

        var imageContent = findFirstNonBlankByPaths(responseJson, imageContentPaths).orElseThrow(() -> new AIResponseException("No image content found at paths " + imageContentPaths, responseBody));

        try {
            return Base64.getDecoder().decode(imageContent);
        }
        catch (Exception e) {
            throw new AIResponseException("Cannot Base64-decode image", responseBody, e);
        }
    }

    /**
     * Returns all possible paths to the error message in the JSON response parsed by {@link #parseImageContent(String)}.
     * The first path that matches a value in the JSON response will be used; remaining paths are ignored.
     * @implNote The default implementation returns {@code error.message} and {@code error}.
     * @return all possible paths to the error message in the JSON response.
     */
    public List<String> getImageResponseErrorMessagePaths() {
        return List.of("error.message", "error");
    }

    /**
     * Returns all possible paths to the image content in the JSON response parsed by {@link #parseImageContent(String)}.
     * May not be empty.
     * The first path that matches a value in the JSON response will be used; remaining paths are ignored.
     * @implNote The default implementation throws UnsupportedOperationException.
     * @return all possible paths to the image content in the JSON response.
     */
    public List<String> getImageResponseContentPaths() {
        throw new UnsupportedOperationException("Please implement getImageResponseContentPaths() method in class " + getClass().getSimpleName());
    }
}
