package org.omnifaces.ai.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.omnifaces.ai.AIProvider;

@EnabledIfEnvironmentVariable(named = MetaAIServiceImageGeneratorIT.API_KEY_ENV_NAME, matches = ".+")
class MetaAIServiceImageGeneratorIT extends BaseAIServiceImageGeneratorIT {

    protected static final String API_KEY_ENV_NAME = "META_API_KEY";

    @Override
    protected AIProvider getProvider() {
        return AIProvider.META;
    }

    @Override
    protected String getApiKeyEnvName() {
        return API_KEY_ENV_NAME;
    }
}
