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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;

/**
 * OmniHai runs on plain Java SE: CDI, EL and MicroProfile Config are optional dependencies, and every code path which touches them is guarded so that the core
 * works without them.
 * <p>
 * This test runs in the {@code vanilla-test} surefire execution, which drops those APIs from the runtime classpath. Its first test asserts that they really are
 * gone, so that a change to that execution shows up as a failure here rather than as coverage which silently stops covering anything.
 *
 * @author Bauke Scholtz
 */
class PlainJavaRuntimeVanillaTest {

    private static final String[] OPTIONAL_APIS = {
        "jakarta.enterprise.inject.spi.CDI",
        "jakarta.enterprise.inject.spi.el.ELAwareBeanManager",
        "jakarta.el.ELProcessor",
        "org.eclipse.microprofile.config.ConfigProvider"
    };

    @Test
    void optionalApis_areOffTheClasspath() {
        for (var api : OPTIONAL_APIS) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(api), api + " must not be on the vanilla classpath");
        }
    }

    @Test
    void createService_withoutOptionalApis_createsService() {
        assertInstanceOf(OpenAIService.class, AIConfig.of(AIProvider.OPENAI, "test-key").createService());
    }

    @Test
    void runAsync_withoutCDI_fallsBackToItsOwnPoolAndRunsTheTask() throws InterruptedException {
        var latch = new CountDownLatch(1);
        ExecutorServiceHelper.runAsync(latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void delayedExecutor_withoutCDI_fallsBackToItsOwnPoolAndRunsTheTask() throws InterruptedException {
        var latch = new CountDownLatch(1);
        ExecutorServiceHelper.delayedExecutor(Duration.ofMillis(1)).execute(latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

}
