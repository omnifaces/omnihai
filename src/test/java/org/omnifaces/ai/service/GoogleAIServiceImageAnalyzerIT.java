package org.omnifaces.ai.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.omnifaces.ai.AIProvider;

@EnabledIfEnvironmentVariable(named = GoogleAIServiceImageAnalyzerIT.API_KEY_ENV_NAME, matches = ".+")
class GoogleAIServiceImageAnalyzerIT extends BaseAIServiceImageAnalyzerIT {

    protected static final String API_KEY_ENV_NAME = "GOOGLE_API_KEY";

    @Override
    protected AIProvider getProvider() {
        return AIProvider.GOOGLE;
    }

    @Override
    protected String getApiKeyEnvName() {
        return API_KEY_ENV_NAME;
    }
}
