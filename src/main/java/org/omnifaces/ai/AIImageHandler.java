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
package org.omnifaces.ai;

import jakarta.json.JsonObject;

import org.omnifaces.ai.modality.BaseAIImageHandler;
import org.omnifaces.ai.model.GenerateImageOptions;

/**
 * Handler for image-based AI operations including vision analysis, alt-text generation, and image generation.
 * <p>
 * Covers:
 * <ul>
 * <li>vision payload construction</li>
 * <li>detailed image analysis / description / VQA</li>
 * <li>alt-text generation</li>
 * <li>image generation</li>
 * </ul>
 * <p>
 * No temperature control is used. Most vision models produce deterministic or near-deterministic output.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIService
 * @see BaseAIImageHandler
 */
public interface AIImageHandler {

    /**
     * Builds the default system prompt to use when no custom user prompt is provided to
     * {@link AIService#analyzeImage(byte[], String)} or {@link AIService#analyzeImageAsync(byte[], String)}.
     *
     * @return The general-purpose image analysis prompt.
     */
    String buildAnalyzeImagePrompt();

    /**
     * Builds the system prompt for {@link AIService#generateAltText(byte[])} and {@link AIService#generateAltTextAsync(byte[])}.
     *
     * @return The system prompt.
     */
    String buildGenerateAltTextPrompt();

    /**
     * Builds the JSON request payload for all vision operations.
     * @param service The involved AI service.
     * @param image The image bytes.
     * @param prompt The analysis prompt.
     * @return The JSON request payload.
     */
    JsonObject buildVisionPayload(AIService service, byte[] image, String prompt);

    /**
     * Builds the JSON request payload for all generate image operations.
     * The default implementation throws UnsupportedOperationException.
     * @param service The visiting AI service.
     * @param prompt The image generation prompt.
     * @param options The image generation options.
     * @return The JSON request payload.
     */
    default JsonObject buildGenerateImagePayload(AIService service, String prompt, GenerateImageOptions options) {
        throw new UnsupportedOperationException("Please implement buildGenerateImagePayload(String prompt, GenerateImageOptions options) method in class " + getClass().getSimpleName());
    }

}
