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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.omnifaces.ai.DeliberateFailures;

class ExecutorServiceHelperTest {

    /**
     * A task which fails is reported at WARNING with its stack trace, which these tests provoke on purpose. Only that message is dropped: switching the level
     * of the whole package off would silence every test running beside this one, whose records are none of this one's business.
     */
    @BeforeAll
    static void dropTheWarningsOfTheDeliberatelyFailingTasks() {
        DeliberateFailures.dropMessagesContaining(ExecutorServiceHelper.class.getPackageName(), "Async task failed");
    }

    // =================================================================================================================
    // runAsync - successful task
    // =================================================================================================================

    @Test
    void runAsync_successfulTask_executesTask() throws InterruptedException {
        var latch = new CountDownLatch(1);
        ExecutorServiceHelper.runAsync(latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void runAsync_successfulTask_callsRunnable() throws InterruptedException {
        var latch = new CountDownLatch(1);
        var task = mock(Runnable.class);

        ExecutorServiceHelper.runAsync(() -> {
            task.run();
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        verify(task).run();
    }

    // =================================================================================================================
    // runAsync - failing task
    // =================================================================================================================

    @Test
    void runAsync_failingTask_doesNotPropagate() throws InterruptedException {
        var latch = new CountDownLatch(1);

        assertDoesNotThrow(() -> ExecutorServiceHelper.runAsync(() -> {
            try {
                throw new RuntimeException("test failure");
            }
            finally {
                latch.countDown();
            }
        }));

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void runAsync_multipleFailingTasks_doNotBlockSubsequentTasks() throws InterruptedException {
        var latch = new CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            ExecutorServiceHelper.runAsync(() -> {
                try {
                    throw new RuntimeException("test failure");
                }
                finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void longRunningTask_doesNotDelayAShortOneBehindIt() throws Exception {
        var blocking = new CountDownLatch(1);
        var occupied = new CountDownLatch(1);
        var completed = new CountDownLatch(1);

        ExecutorServiceHelper.runAsync(() -> {
            occupied.countDown();
            await(blocking);
        });

        assertTrue(occupied.await(5, TimeUnit.SECONDS), "the blocking task must have taken a thread");
        ExecutorServiceHelper.runAsync(completed::countDown);

        try {
            assertTrue(completed.await(5, TimeUnit.SECONDS), "a task which waits on I/O may not hold up every task behind it");
        }
        finally {
            blocking.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
