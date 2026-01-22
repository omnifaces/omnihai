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

import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;

/**
 * AI service implementation using Mistral AI API.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 *     <li>provider: {@link AIProvider#MISTRAL}</li>
 *     <li>apiKey: your Meta API key</li>
 * </ul>
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional.
 * See {@link AIProvider#MISTRAL} for defaults.
 * <ul>
 *     <li>model: the model to use</li>
 *     <li>endpoint: the API endpoint URL</li>
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider#MISTRAL
 * @see AIConfig
 * @see OpenAIService
 * @see BaseAIService
 * @see AIService
 * @see <a href="https://docs.mistral.ai/api">API Reference</a>
 */
public class MistralAIService extends OpenAIService {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a Mistral AI service with the specified configuration.
     *
     * @param config the AI configuration
     * @see AIConfig
     */
    public MistralAIService(AIConfig config) {
        super(config);
    }

    @Override
    public boolean supportsModality(AIModality modality) {
        return switch (modality) {
            case IMAGE_ANALYSIS -> true;
            default -> false;
        };
    }

    @Override
    protected boolean supportsOpenAIModerationCapability(Set<String> categories) {
        return false;
    }

    @Override
    protected boolean supportsResponsesApi() {
        return false;
    }
}
