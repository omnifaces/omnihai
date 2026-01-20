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

import static org.omnifaces.ai.helper.TextHelper.isBlank;

import java.util.Set;

import jakarta.json.Json;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.GenerateImageOptions;

/**
 * AI service implementation using xAI API.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 *     <li>provider: {@link AIProvider#XAI}</li>
 *     <li>apiKey: your xAI API key</li>
 * </ul>
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional.
 * See {@link AIProvider#XAI} for defaults.
 * <ul>
 *     <li>model: the model to use</li>
 *     <li>endpoint: the API endpoint URL</li>
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider#XAI
 * @see AIConfig
 * @see OpenAIService
 * @see BaseAIService
 * @see AIService
 * @see <a href="https://docs.x.ai/docs/api-reference">API Reference</a>
 */
public class XAIService extends OpenAIService {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an xAI service with the specified configuration.
     *
     * @param config the AI configuration
     * @see AIConfig
     */
    public XAIService(AIConfig config) {
        super(config);
    }

    @Override
    protected String buildGenerateImagePayload(String prompt, GenerateImageOptions options) {
        if (isBlank(prompt)) {
            throw new IllegalArgumentException("Prompt cannot be blank");
        }

        return Json.createObjectBuilder()
            .add("model", model)
            .add("prompt", prompt)
            .add("n", 1)
            .add("aspect_ratio", options.getAspectRatio())
            .add("response_format", "b64_json")
            .build()
            .toString();
    }

    @Override
    protected boolean supportsModerationCapability(Set<String> categories) {
        return false;
    }
}
