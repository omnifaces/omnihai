package org.omnifaces.ai.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.omnifaces.ai.AIProvider;

@EnabledIfEnvironmentVariable(named = OpenAIServiceTextAnalyzerIT.API_KEY_ENV_NAME, matches = ".+")
class OpenAIServiceTextAnalyzerIT extends BaseAIServiceTextAnalyzerIT {

    protected static final String API_KEY_ENV_NAME = "OPENAI_API_KEY";

    @Override
    protected AIProvider getProvider() {
        return AIProvider.OPENAI;
    }

    @Override
    protected String getApiKeyEnvName() {
        return API_KEY_ENV_NAME;
    }
}
