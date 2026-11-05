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

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.AIStrategy;

/**
 * This test runs OpenAI through the legacy {@code chat/completions} fallback, which OpenAI only selects for models older than GPT-4. OpenAI serves no such
 * model anymore, so the test forces the fallback on a current model via {@link LegacyOpenAIService}.
 */
@EnabledIfEnvironmentVariable(named = OpenAIServiceLegacyTextHandlerIT.API_KEY_ENV_NAME, matches = ".+")
class OpenAIServiceLegacyTextHandlerIT extends BaseAIServiceTextHandlerIT {

    protected static final String API_KEY_ENV_NAME = "OPENAI_API_KEY";

    /**
     * An {@link OpenAIService} which never uses the OpenAI {@code responses} API.
     */
    public static class LegacyOpenAIService extends OpenAIService {

        private static final long serialVersionUID = 1L;

        public LegacyOpenAIService(AIConfig config) {
            super(config);
        }

        @Override
        public boolean supportsOpenAIResponsesApi() {
            return false;
        }

    }

    @Override
    protected AIProvider getProvider() {
        return AIProvider.OPENAI;
    }

    @Override
    protected String getApiKeyEnvName() {
        return API_KEY_ENV_NAME;
    }

    @Override
    protected String getModel() {
        return "gpt-4.1";
    }

    @Override
    protected AIService createService(String model) {
        var openai = getProvider();
        var strategy = new AIStrategy(
            openai.getDefaultTextHandler(), openai.getDefaultImageHandler(), openai.getDefaultAudioHandler(), openai.getDefaultVideoHandler()
        );
        return AIConfig.of(LegacyOpenAIService.class, System.getenv(API_KEY_ENV_NAME))
            .withModel(model)
            .withEndpoint(openai.getDefaultEndpoint())
            .withStrategy(strategy)
            .createService();
    }

    @Override
    protected boolean supportsReasoningTokens() {
        return true;
    }

}
