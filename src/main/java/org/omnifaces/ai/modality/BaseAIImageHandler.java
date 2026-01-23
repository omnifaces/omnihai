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

import java.util.logging.Logger;

import org.omnifaces.ai.AIImageHandler;
import org.omnifaces.ai.AIService;

/**
 * Base class for AI image handler implementations that provides general-purpose prompt templates suitable for most
 * current vision-capable models.
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
public abstract class BaseAIImageHandler implements AIImageHandler {

    /** Logger for current package. */
    protected static final Logger logger = Logger.getLogger(BaseAIImageHandler.class.getPackageName());

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
}
