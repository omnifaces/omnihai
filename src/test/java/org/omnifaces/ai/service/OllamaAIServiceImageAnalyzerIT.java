package org.omnifaces.ai.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.omnifaces.ai.AIProvider;

@EnabledIfEnvironmentVariable(named = OllamaAIServiceImageAnalyzerIT.API_KEY_ENV_NAME, matches = ".+")
class OllamaAIServiceImageAnalyzerIT extends BaseAIServiceImageAnalyzerIT {

    protected static final String API_KEY_ENV_NAME = "OLLAMA_API_KEY";

    @Override
    protected AIProvider getProvider() {
        return AIProvider.OLLAMA;
    }

    @Override
    protected String getApiKeyEnvName() {
        return API_KEY_ENV_NAME;
    }
}
