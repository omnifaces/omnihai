package org.omnifaces.ai.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.omnifaces.ai.AIProvider;

@EnabledIfEnvironmentVariable(named = OpenRouterAIServiceTextAnalyzerIT.API_KEY_ENV_NAME, matches = ".+")
class OpenRouterAIServiceTextAnalyzerIT extends BaseAIServiceTextAnalyzerIT {

    protected static final String API_KEY_ENV_NAME = "OPENROUTER_API_KEY";

    @Override
    protected AIProvider getProvider() {
        return AIProvider.OPENROUTER;
    }

    @Override
    protected String getApiKeyEnvName() {
        return API_KEY_ENV_NAME;
    }
}
