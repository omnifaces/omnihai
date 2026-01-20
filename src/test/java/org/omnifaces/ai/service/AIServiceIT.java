package org.omnifaces.ai.service;

import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;

@TestInstance(Lifecycle.PER_CLASS)
abstract class AIServiceIT {

    protected AIService service;
    private Logger logger;

    protected abstract AIProvider getProvider();

    protected abstract String getApiKeyEnvName();

    protected String getModel() {
        return getProvider().getDefaultModel();
    }

    @BeforeAll
    void setup() {
        service = AIConfig.of(getProvider(), System.getenv(getApiKeyEnvName()), getModel()).createService();
        logger = Logger.getLogger(getProvider() + "." + getModel());
    }

    void log(String message) {
        logger.info(getProvider() + " " + getModel() + ": " + message);
    }
}
