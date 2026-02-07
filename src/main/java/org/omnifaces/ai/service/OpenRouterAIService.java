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

import java.util.Set;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;

/**
 * AI service implementation using OpenRouter API.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 *     <li>provider: {@link AIProvider#OPENROUTER}</li>
 *     <li>apiKey: your OpenRouter API key</li>
 * </ul>
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional.
 * See {@link AIProvider#OPENROUTER} for defaults.
 * <ul>
 *     <li>model: the model to use</li>
 *     <li>endpoint: the API endpoint URL</li>
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider#OPENROUTER
 * @see OpenAIService
 * @see BaseAIService
 * @see AIService
 * @see <a href="https://openrouter.ai/docs/api/reference">API Reference</a>
 */
public class OpenRouterAIService extends OpenAIService {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an OpenRouter AI service with the specified configuration.
     *
     * @param config the AI configuration
     * @see AIConfig
     */
    public OpenRouterAIService(AIConfig config) {
        super(config);
    }

    @Override
    public boolean supportsModality(AIModality modality) {
        var fullModelName = getModelName().toLowerCase();

        return switch (modality) {
            case IMAGE_ANALYSIS -> true;
            case IMAGE_GENERATION -> fullModelName.contains("image");
            case AUDIO_ANALYSIS -> fullModelName.contains("audio");
            default -> false;
        };
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public boolean supportsFileUpload() {
        return true;
    }

    @Override
    public boolean supportsStructuredOutput() {
        return true;
    }

    @Override
    public boolean supportsOpenAIResponsesApi() {
        return false;
    }

    @Override
    public boolean supportsOpenAIModerationCapability(Set<String> categories) {
        return false;
    }

    @Override
    public boolean supportsOpenAITranscriptionCapability() {
        return false;
    }
}
