package org.omnifaces.ai.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.omnifaces.ai.AIProvider;

@EnabledIfEnvironmentVariable(named = AzureAIServiceTextAnalyzerIT.API_KEY_ENV_NAME, matches = ".+")
class AzureAIServiceTextAnalyzerIT extends BaseAIServiceTextAnalyzerIT {

    protected static final String API_KEY_ENV_NAME = "AZURE_API_KEY";

    @Override
    protected AIProvider getProvider() {
        return AIProvider.AZURE;
    }

    @Override
    protected String getApiKeyEnvName() {
        return API_KEY_ENV_NAME;
    }
}
