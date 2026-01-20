package org.omnifaces.ai.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.omnifaces.ai.AIProvider;

@EnabledIfEnvironmentVariable(named = AnthropicAIServiceImageAnalyzerIT.API_KEY_ENV_NAME, matches = ".+")
class AnthropicAIServiceImageAnalyzerIT extends BaseAIServiceImageAnalyzerIT {

    protected static final String API_KEY_ENV_NAME = "ANTHROPIC_API_KEY";

    @Override
    protected AIProvider getProvider() {
        return AIProvider.ANTHROPIC;
    }

    @Override
    protected String getApiKeyEnvName() {
        return API_KEY_ENV_NAME;
    }
}
