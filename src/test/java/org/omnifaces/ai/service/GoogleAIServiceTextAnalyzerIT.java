package org.omnifaces.ai.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.omnifaces.ai.AIProvider;

@EnabledIfEnvironmentVariable(named = GoogleAIServiceTextAnalyzerIT.API_KEY_ENV_NAME, matches = ".+")
class GoogleAIServiceTextAnalyzerIT extends BaseAIServiceTextAnalyzerIT {

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
