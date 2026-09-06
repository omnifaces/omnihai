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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Which executor the library runs its asynchronous work on when a CDI container is up. A container which offers no managed executor of its own, which is what
 * Java SE and a servlet container such as Tomcat offer, leaves the library on a pool of its own rather than on none.
 */
@ExtendWith(WeldJunit5Extension.class)
class ExecutorServiceCDIContainerTest {

    @WeldSetup
    WeldInitiator weld = WeldInitiator.from(ManagedBeans.executorServiceManager()).activate(ApplicationScoped.class).build();

    /**
     * The manager is looked up as a bean rather than constructed, so that its {@code @PostConstruct} runs and the lookup it performs is the real one.
     */
    @Test
    void getCurrentInstance_containerWithoutAManagedExecutor_statesItHasNone() {
        assertNull(ExecutorServiceManager.getCurrentInstance().getManagedExecutorService());
    }

    @Test
    void runAsync_containerWithoutAManagedExecutor_stillRunsTheTask() throws InterruptedException {
        var ran = new CountDownLatch(1);

        ExecutorServiceHelper.runAsync(ran::countDown);

        assertTrue(ran.await(5, SECONDS));
    }

}
