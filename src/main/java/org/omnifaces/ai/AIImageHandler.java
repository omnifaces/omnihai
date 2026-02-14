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

import java.io.Serializable;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.JsonObject;

import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.modality.DefaultAIImageHandler;
import org.omnifaces.ai.model.GenerateImageOptions;

/**
 * Handler for image-based AI operations including vision analysis, alt-text generation, and image generation.
 * <p>
 * Covers:
 * <ul>
 * <li>detailed image analysis / description / VQA</li>
 * <li>alt-text generation</li>
 * <li>image generation</li>
 * </ul>
 * <p>
 * The implementations must be stateless and able to be {@link ApplicationScoped}.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIService
 * @see DefaultAIImageHandler
 */
public interface AIImageHandler extends Serializable {

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
     * Builds the JSON request payload for all generate image operations.
     * @implNote The default implementation throws UnsupportedOperationException.
     * @param service The visiting AI service.
     * @param prompt The image generation prompt.
     * @param options The image generation options.
     * @return The JSON request payload.
     */
    default JsonObject buildGenerateImagePayload(AIService service, String prompt, GenerateImageOptions options) {
        throw new UnsupportedOperationException("Please implement buildGenerateImagePayload(AIService service, String prompt, GenerateImageOptions options) method in class " + getClass().getSimpleName());
    }

    /**
     * Parses image content from the API response body of generate image operation.
     * @implNote The default implementation throws UnsupportedOperationException.
     * @param responseBody The API response body, usually a JSON object with the AI response, along with some meta data.
     * @return The extracted image content from the API response body.
     * @throws AIResponseException If the response cannot be parsed as JSON, contains an error object, or is missing expected image content.
     */
    default byte[] parseImageContent(String responseBody) throws AIResponseException {
        throw new UnsupportedOperationException("Please implement parseImageContent(String responseBody) method in class " + getClass().getSimpleName());
    }
}
