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

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.model.GenerateVideoOptions;
import org.omnifaces.ai.model.VideoGeneration.Job;
import org.omnifaces.ai.model.VideoGeneration.Status;

class BaseAIServiceVideoPollingTest {

    private static final GenerateVideoOptions OPTIONS = GenerateVideoOptions.newBuilder().pollInterval(Duration.ofMillis(10)).build();
    private static final String JOB_ID = "job-1";

    @Test
    void terminalJob_completesWithoutPolling() throws Exception {
        var polls = new AtomicInteger();
        var job = new Job(JOB_ID, Status.COMPLETED, null, null, null);

        var completion = BaseAIService.awaitVideoCompletion(job, OPTIONS, polled -> {
            polls.incrementAndGet();
            return completedFuture(polled);
        });

        assertEquals(Status.COMPLETED, completion.get(5, SECONDS).status());
        assertEquals(0, polls.get(), "a job which is already terminal needs no request");
    }

    @Test
    void pendingJob_isPolledUntilTerminal() throws Exception {
        var polls = new AtomicInteger();

        var completion = BaseAIService.awaitVideoCompletion(Job.pending(JOB_ID, null), OPTIONS, polled -> {
            var status = polls.incrementAndGet() < 3 ? Status.RUNNING : Status.COMPLETED;
            return completedFuture(new Job(JOB_ID, status, null, null, null));
        });

        assertEquals(Status.COMPLETED, completion.get(5, SECONDS).status());
        assertEquals(3, polls.get(), "polling stops at the first terminal status");
    }

    @Test
    void failedJob_completesRatherThanThrows() throws Exception {
        var completion = BaseAIService.awaitVideoCompletion(
            Job.pending(JOB_ID, null), OPTIONS, polled -> completedFuture(new Job(JOB_ID, Status.FAILED, null, null, "moderation blocked"))
        );

        var job = completion.get(5, SECONDS);

        assertEquals(Status.FAILED, job.status(), "a failed job is a completed poll, not a failed poll");
        assertEquals("moderation blocked", job.failureReason());
    }

    @Test
    void jobWhichNeverFinishes_failsOnceTheMaxWaitHasPassed() {
        var options = GenerateVideoOptions.newBuilder().pollInterval(Duration.ofMillis(10)).maxWait(Duration.ofMillis(500)).build();
        var polls = new AtomicInteger();

        var completion = BaseAIService.awaitVideoCompletion(Job.pending(JOB_ID, null), options, polled -> {
            polls.incrementAndGet();
            return completedFuture(new Job(JOB_ID, Status.RUNNING, null, null, null));
        });

        var exception = assertThrows(ExecutionException.class, () -> completion.get(5, SECONDS));

        assertTrue(exception.getCause() instanceof TimeoutException, "a job the AI provider never finishes may not block the caller forever");
        assertTrue(polls.get() > 0, "the deadline may not fire before a single poll was attempted");
    }

    @Test
    void failingPoll_completesExceptionally() {
        var completion = BaseAIService.awaitVideoCompletion(
            Job.pending(JOB_ID, null), OPTIONS, polled -> failedFuture(new AIException("Endpoint unreachable"))
        );

        var exception = assertThrows(ExecutionException.class, () -> completion.get(5, SECONDS));

        assertTrue(exception.getCause() instanceof AIException, "the poll failure must survive as its own exception");
    }

    @Test
    void pollThrowingSynchronously_completesExceptionally() {
        var completion = BaseAIService.awaitVideoCompletion(Job.pending(JOB_ID, null), OPTIONS, polled -> {
            throw new AIException("Endpoint unreachable");
        });

        var exception = assertThrows(ExecutionException.class, () -> completion.get(5, SECONDS));

        assertTrue(exception.getCause() instanceof AIException, "a poller which throws rather than returns a failed future must not strand the completion");
    }

    @Test
    void canceledCompletion_stopsPolling() throws Exception {
        var polls = new AtomicInteger();
        var secondPoll = new CountDownLatch(2);
        var holder = new AtomicReference<CompletableFuture<Job>>();

        holder.set(BaseAIService.awaitVideoCompletion(Job.pending(JOB_ID, null), OPTIONS, polled -> {
            polls.incrementAndGet();
            secondPoll.countDown();
            holder.get().cancel(true);
            return completedFuture(new Job(JOB_ID, Status.RUNNING, null, null, null));
        }));

        var polledAgain = secondPoll.await(OPTIONS.getPollInterval().toMillis() * 20, MILLISECONDS);

        assertFalse(polledAgain, "a canceled completion must not schedule another poll");
        assertTrue(holder.get().isCancelled());
        assertEquals(1, polls.get());
    }

}
