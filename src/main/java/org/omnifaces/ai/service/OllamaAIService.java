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

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.modality.OllamaAIImageHandler;
import org.omnifaces.ai.modality.OllamaAITextHandler;

/**
 * AI service implementation using Ollama API for local/self-hosted models.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 *     <li>provider: {@link AIProvider#OLLAMA}</li>
 * </ul>
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional.
 * See {@link AIProvider#OLLAMA} for defaults.
 * <ul>
 *     <li>model: the model to use</li>
 *     <li>endpoint: the API endpoint URL</li>
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider#OLLAMA
 * @see OllamaAITextHandler
 * @see OllamaAIImageHandler
 * @see BaseAIService
 * @see AIService
 * @see <a href="https://docs.ollama.com/api/introduction">API Reference</a>.
 */
public class OllamaAIService extends BaseAIService {

    private static final long serialVersionUID = 1L;

    private static final AIModelVersion LLAMA_4 = AIModelVersion.of("llama", 4);

    /**
     * Constructs an Ollama AI service with the specified configuration.
     *
     * @param config the AI configuration
     * @see AIConfig
     */
    public OllamaAIService(AIConfig config) {
        super(config);
    }

    @Override
    public boolean supportsModality(AIModality modality) {
        var currentModelVersion = getModelVersion();
        var fullModelName = getModelName().toLowerCase();

        return switch (modality) {
            case IMAGE_ANALYSIS -> currentModelVersion.gte(LLAMA_4) || fullModelName.contains("vision") || fullModelName.contains("llava") || fullModelName.contains("gemma");
            default -> false;
        };
    }

    /**
     * Returns {@code api/chat}.
     */
    @Override
    protected String getChatPath(boolean streaming) {
        return "api/chat";
    }
}
