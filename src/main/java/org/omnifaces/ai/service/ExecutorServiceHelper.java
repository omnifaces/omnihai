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

import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.WARNING;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

/**
 * Package-private helper for fire-and-forget async tasks.
 * <p>
 * Without CDI the tasks run on a pool which grows on demand and reaps its idle threads, as the ones which occupy a thread the longest, such as consuming a
 * server-sent event stream, spend that time waiting on I/O rather than on a processor: how many are needed follows from how many calls the application has in
 * flight rather than from how many processors it has.
 *
 * @author Bauke Scholtz
 * @since 1.1
 */
final class ExecutorServiceHelper {

    private static final Logger logger = Logger.getLogger(ExecutorServiceHelper.class.getPackageName());

    private static ExecutorService executorService;
    private static boolean managedExecutorService;

    static {
        if (isCDIAvailable()) {
            executorService = ExecutorServiceManager.getCurrentInstance().getManagedExecutorService();
        }

        if (executorService != null) {
            managedExecutorService = true;
        }
        else {
            executorService = newCachedThreadPool(runnable -> {
                var thread = new Thread(runnable, "omnihai.executorService");
                thread.setDaemon(true);
                return thread;
            });

            getRuntime().addShutdownHook(new Thread(ExecutorServiceHelper::destroy));
        }
    }

    private static boolean isCDIAvailable() {
        try {
            return Class.forName("jakarta.enterprise.inject.spi.CDI").getMethod("current").invoke(null) != null;
        }
        catch (Exception | LinkageError ignore) {
            return false;
        }
    }

    private ExecutorServiceHelper() {
        throw new AssertionError();
    }

    /**
     * Runs the given task asynchronously, swallowing and logging any exceptions.
     *
     * @param task The task to run.
     */
    static void runAsync(Runnable task) {
        var callerStackTrace = new Exception("Caller stack trace");

        CompletableFuture.runAsync(task, executorService).exceptionally(throwable -> {
            throwable.addSuppressed(callerStackTrace);
            logger.log(WARNING, "Async task failed", throwable);
            return null;
        });
    }

    /**
     * Returns an executor which runs a task after the given delay. The delay itself occupies no thread.
     *
     * @param delay The delay to wait before running the task.
     * @return An executor which runs a task after the given delay.
     */
    static Executor delayedExecutor(Duration delay) {
        return CompletableFuture.delayedExecutor(delay.toMillis(), MILLISECONDS, executorService);
    }

    /**
     * Attempts to orderly shut down the unmanaged executor service. If it's still not shut down after 5 seconds, then terminate it.
     */
    private static void destroy() {
        if (managedExecutorService || executorService == null) {
            throw new IllegalStateException();
        }

        executorService.shutdown();

        try {
            if (!executorService.awaitTermination(5, SECONDS)) {
                executorService.shutdownNow();
            }
        }
        catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}
