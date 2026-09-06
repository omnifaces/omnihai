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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * What the library runs its asynchronous work on when the runtime is a Jakarta EE server: the executor that server manages, rather than a pool of its own. The
 * naming service is stubbed, so this states the server path without a server.
 */
@ExtendWith(WeldJunit5Extension.class)
class ExecutorServiceManagedExecutorTest {

    @WeldSetup
    WeldInitiator weld = WeldInitiator.from(ExecutorServiceManager.class).activate(ApplicationScoped.class).build();

    @Test
    void getCurrentInstance_serverOfferingAManagedExecutor_answersTheOneItOffers() {
        assertSame(StubInitialContextFactory.MANAGED_EXECUTOR_SERVICE, ExecutorServiceManager.getCurrentInstance().getManagedExecutorService());
    }

    /**
     * The executor a server manages carries that server's context onto the task, which is the whole reason to prefer it over a pool of our own.
     */
    @Test
    void runAsync_serverOfferingAManagedExecutor_runsTheTaskOnIt() throws Exception {
        var runOn = new CompletableFuture<String>();

        ExecutorServiceHelper.runAsync(() -> runOn.complete(Thread.currentThread().getName()));

        assertEquals(StubInitialContextFactory.THREAD_NAME, runOn.get(5, SECONDS));
    }

}
