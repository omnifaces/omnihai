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

import org.junit.jupiter.api.Test;

class ExecutorServiceHelperTest {

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
}
