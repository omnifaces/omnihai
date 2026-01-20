package org.omnifaces.ai.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.omnifaces.ai.AIProvider;

@EnabledIfEnvironmentVariable(named = XAIServiceImageAnalyzerIT.API_KEY_ENV_NAME, matches = ".+")
class XAIServiceImageAnalyzerIT extends BaseAIServiceImageAnalyzerIT {

    protected static final String API_KEY_ENV_NAME = "XAI_API_KEY";

    @Override
    protected AIProvider getProvider() {
        return AIProvider.XAI;
    }

    @Override
    protected String getApiKeyEnvName() {
        return API_KEY_ENV_NAME;
    }
}
