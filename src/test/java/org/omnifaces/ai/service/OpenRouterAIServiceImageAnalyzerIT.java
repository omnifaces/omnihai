package org.omnifaces.ai.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.omnifaces.ai.AIProvider;

@EnabledIfEnvironmentVariable(named = OpenRouterAIServiceImageAnalyzerIT.API_KEY_ENV_NAME, matches = ".+")
class OpenRouterAIServiceImageAnalyzerIT extends BaseAIServiceImageAnalyzerIT {

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
