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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.model.GeneratedVideo.Job;
import org.omnifaces.ai.model.GeneratedVideo.Status;

/**
 * Tests that the poll path an AI provider states once, in the submit response, keeps being used by every poll after the first.
 */
class BaseAIServiceVideoPollPathTest {

    private static final String JOB_ID = "job-1";
    private static final String POLL_PATH = "https://openrouter.ai/api/v1/videos/job-1";

    @Test
    void pollResponseWithoutPollPath_inheritsTheOneTheSubmitResponseStated() {
        var previous = Job.pending(JOB_ID, POLL_PATH);
        var polled = new Job(JOB_ID, Status.RUNNING, null, null, null);

        var retained = BaseAIService.retainPollPath(polled, previous);

        assertEquals(POLL_PATH, retained.pollPath(), "every poll after the first must reach the target the AI provider named");
        assertEquals(Status.RUNNING, retained.status());
    }

    @Test
    void pollResponseWithItsOwnPollPath_keepsIt() {
        var previous = Job.pending(JOB_ID, POLL_PATH);
        var polled = new Job(JOB_ID, Status.RUNNING, "videos/moved", null, null);

        assertEquals("videos/moved", BaseAIService.retainPollPath(polled, previous).pollPath(), "an AI provider may move the job");
    }

    @Test
    void pollResponseWithoutPollPath_andNoneStatedBefore_staysDerived() {
        var polled = new Job(JOB_ID, Status.RUNNING, null, null, null);

        var retained = BaseAIService.retainPollPath(polled, Job.pending(JOB_ID, null));

        assertSame(polled, retained, "nothing to carry forward leaves the job as it was parsed");
        assertNull(retained.pollPath());
    }

    @Test
    void retainedPollPath_carriesEveryOtherFieldOfThePolledJob() {
        var polled = new Job(JOB_ID, Status.FAILED, null, "videos/job-1/content", "moderation blocked");

        var retained = BaseAIService.retainPollPath(polled, Job.pending(JOB_ID, POLL_PATH));

        assertEquals(Status.FAILED, retained.status());
        assertEquals("videos/job-1/content", retained.contentPath());
        assertEquals("moderation blocked", retained.failureReason());
    }

}
