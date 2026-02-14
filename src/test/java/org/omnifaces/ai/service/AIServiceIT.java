/*
 * Copyright OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.ai.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;

/**
 * Base class for IT tests on AI service instances. Each instance has its own provider and model.
 */
@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(FailFastOnRateLimitExtension.class)
abstract class AIServiceIT {

    private static final Logger logger = Logger.getLogger(AIServiceIT.class.getName());

    private final ThreadLocal<String> currentTestMethod = ThreadLocal.withInitial(() -> "unknown");

    protected AIService service;

    protected abstract AIProvider getProvider();

    protected abstract String getApiKeyEnvName();

    protected String getModel() {
        return getProvider().getDefaultModel();
    }

    @BeforeAll
    void setup() {
        service = AIConfig.of(getProvider(), System.getenv(getApiKeyEnvName())).withModel(getModel()).createService();
    }

    @BeforeEach
    void captureTestMethod(TestInfo testInfo) {
        currentTestMethod.set(testInfo.getDisplayName());
    }

    @BeforeEach
    void rateLimit() {
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        }
    }

    void log(String message) {
        logger.info(String.format("%s %s: %s: %s", getProvider(), getModel(), currentTestMethod.get(), message));
    }

    static byte[] readAllBytes(String resource) {
        try {
            return AIServiceIT.class.getResourceAsStream(resource).readAllBytes();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Path getPath(String resource) {
        try {
            var tempFile = Files.createTempFile("omnihai.AIServiceIT.", ".tmp");
            tempFile.toFile().deleteOnExit();
            Files.write(tempFile, readAllBytes(resource));
            return tempFile;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterEach
    void clearTestMethod() {
        currentTestMethod.remove();
    }
}
