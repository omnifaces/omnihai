package org.omnifaces.ai.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.omnifaces.ai.AIProvider;

@EnabledIfEnvironmentVariable(named = AnthropicAIServiceTextAnalyzerIT.API_KEY_ENV_NAME, matches = ".+")
class AnthropicAIServiceTextAnalyzerIT extends BaseAIServiceTextAnalyzerIT {

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
