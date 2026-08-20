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
package org.omnifaces.ai.model;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.model.VideoGeneration.Job;
import org.omnifaces.ai.model.VideoGeneration.Source;
import org.omnifaces.ai.model.VideoGeneration.Status;

class VideoGenerationTest {

    private static final byte[] VIDEO_CONTENT = "video".getBytes(UTF_8);
    private static final GenerateVideoOptions OPTIONS = GenerateVideoOptions.newBuilder().pollInterval(Duration.ofMillis(1)).build();

    @Test
    void status_performsNoRequest() {
        var source = new RecordingSource();
        var video = new VideoGeneration(Job.pending("job-1", null), OPTIONS, source);

        assertEquals(Status.PENDING, video.status());
        assertEquals("job-1", video.jobId());
        assertEquals(0, source.polls, "status() must not poll");
    }

    @Test
    void refresh_pollsExactlyOnce() {
        var source = new RecordingSource(new Job("job-1", Status.RUNNING, null, null, null));
        var video = new VideoGeneration(Job.pending("job-1", null), OPTIONS, source);

        assertSame(video, video.refresh());
        assertEquals(Status.RUNNING, video.status());
        assertEquals(1, source.polls);
    }

    @Test
    void refresh_onTerminalJob_pollsNoMore() {
        var source = new RecordingSource();
        var video = new VideoGeneration(new Job("job-1", Status.COMPLETED, null, null, null), OPTIONS, source);

        video.refresh();

        assertEquals(0, source.polls, "a terminal job cannot change, so polling it again is waste");
    }

    @Test
    void completion_pollsUntilTerminal() throws Exception {
        var source = new RecordingSource(
            new Job("job-1", Status.RUNNING, null, null, null), new Job("job-1", Status.COMPLETED, null, null, null)
        );
        var video = new VideoGeneration(Job.pending("job-1", null), OPTIONS, source);

        assertSame(video, video.completion().get(5, SECONDS));
        assertEquals(Status.COMPLETED, video.status());
    }

    @Test
    void completion_calledTwiceWhileWaiting_joinsTheSameWait() {
        var source = new PollingSource(new CompletableFuture<>());
        var video = new VideoGeneration(Job.pending("job-1", null), OPTIONS, source);

        var first = video.completion();
        var second = video.completion();

        assertSame(first, second, "watching one job from several places may not poll it several times");
        assertEquals(1, source.waits(), "the second caller may not start a wait of its own");
    }

    @Test
    void completion_afterTheWaitWasCanceled_startsANewOne() {
        var source = new PollingSource(new CompletableFuture<>());
        var video = new VideoGeneration(Job.pending("job-1", null), OPTIONS, source);

        var canceled = video.completion();
        canceled.cancel(true);
        var restarted = video.completion();

        assertNotSame(canceled, restarted, "a finished wait cannot represent an ongoing one");
        assertFalse(restarted.isDone());
    }

    @Test
    void completion_onTerminalJob_completesWithoutPolling() throws Exception {
        var source = new RecordingSource();
        var video = new VideoGeneration(new Job("job-1", Status.COMPLETED, null, null, null), OPTIONS, source);

        assertSame(video, video.completion().get(5, SECONDS));
        assertEquals(0, source.polls);
    }

    @Test
    void canceledCompletion_cancelsThePolling() {
        var polling = new CompletableFuture<Job>();
        var video = new VideoGeneration(Job.pending("job-1", null), OPTIONS, new PollingSource(polling));

        var completion = video.completion();
        completion.cancel(true);

        assertTrue(polling.isCancelled(), "canceling the completion must stop the polling behind it");
    }

    @Test
    void writeTo_whileUnfinished_throwsIllegalState() {
        var video = new VideoGeneration(Job.pending("job-1", null), OPTIONS, new RecordingSource());

        assertThrows(IllegalStateException.class, () -> video.writeTo(new ByteArrayOutputStream()));
    }

    @Test
    void writeTo_whenFailed_throwsAIExceptionStatingTheReason() {
        var video = new VideoGeneration(new Job("job-1", Status.FAILED, null, null, "moderation blocked"), OPTIONS, new RecordingSource());

        var exception = assertThrows(AIException.class, () -> video.writeTo(new ByteArrayOutputStream()));
        assertTrue(exception.getMessage().contains("moderation blocked"), "the provider's reason must survive: " + exception.getMessage());
    }

    @Test
    void writeTo_whenExpired_throwsAIException() {
        var video = new VideoGeneration(new Job("job-1", Status.EXPIRED, null, null, null), OPTIONS, new RecordingSource());

        assertThrows(AIException.class, () -> video.writeTo(new ByteArrayOutputStream()));
    }

    @Test
    void writeTo_whenCompleted_writesTheContent() throws Exception {
        var video = new VideoGeneration(new Job("job-1", Status.COMPLETED, null, null, null), OPTIONS, new RecordingSource());
        var output = new ByteArrayOutputStream();

        video.writeTo(output);

        assertArrayEquals(VIDEO_CONTENT, output.toByteArray());
    }

    @Test
    void writeTo_toAnUnusablePath_failsBeforeDownloading() {
        var source = new RecordingSource();
        var video = new VideoGeneration(new Job("job-1", Status.COMPLETED, null, null, null), OPTIONS, source);
        var missingDirectory = Path.of("no", "such", "directory", "video.mp4");

        assertThrows(IllegalArgumentException.class, () -> video.writeTo(missingDirectory));
        assertEquals(0, source.downloads(), "a path which cannot receive the video may not cost a download");
    }

    @Test
    void writeTo_toAFileSystemRoot_isRejected() {
        var video = new VideoGeneration(new Job("job-1", Status.COMPLETED, null, null, null), OPTIONS, new RecordingSource());

        assertThrows(IllegalArgumentException.class, () -> video.writeTo(Path.of("/")));
    }

    @Test
    void writeTo_toAnExistingDirectory_isRejected() throws Exception {
        var directory = Files.createTempDirectory("omnihai-generated-video-");
        var source = new RecordingSource();
        var video = new VideoGeneration(new Job("job-1", Status.COMPLETED, null, null, null), OPTIONS, source);

        try {
            assertThrows(IllegalArgumentException.class, () -> video.writeTo(directory));
            assertEquals(0, source.downloads(), "a directory cannot receive the video, so it may not cost a download");
        }
        finally {
            Files.deleteIfExists(directory);
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // Renaming over a read-only target is refused there, so the directory is not the only thing which governs.
    void writeTo_overAReadOnlyTarget_succeeds() throws Exception {
        var directory = Files.createTempDirectory("omnihai-generated-video-");
        var path = directory.resolve("video.mp4");
        Files.writeString(path, "previous");
        path.toFile().setWritable(false);
        assumeFalse(Files.isWritable(path), "the permission bits must bite for this to prove anything, which they do not for a superuser");
        var video = new VideoGeneration(new Job("job-1", Status.COMPLETED, null, null, null), OPTIONS, new RecordingSource());

        try {
            video.writeTo(path);
            assertArrayEquals(VIDEO_CONTENT, Files.readAllBytes(path), "the write renames over the target, so only the directory must be writable");
        }
        finally {
            path.toFile().setWritable(true);
            Files.deleteIfExists(path);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void writeTo_path_writesTheContent() throws Exception {
        var video = new VideoGeneration(new Job("job-1", Status.COMPLETED, null, null, null), OPTIONS, new RecordingSource());
        var path = Files.createTempFile("omnihai-generated-video-", ".mp4");

        try {
            video.writeTo(path);
            assertArrayEquals(VIDEO_CONTENT, Files.readAllBytes(path));
        }
        finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void writeTo_whenTheDownloadFailsMidway_leavesTheTargetUntouched() throws Exception {
        var directory = Files.createTempDirectory("omnihai-generated-video-");
        var path = directory.resolve("video.mp4");
        Files.writeString(path, "previous");
        var video = new VideoGeneration(new Job("job-1", Status.COMPLETED, null, null, null), OPTIONS, new FailingDownloadSource());

        try {
            assertThrows(UncheckedIOException.class, () -> video.writeTo(path));
            assertEquals("previous", Files.readString(path), "a transfer which fails midway may not truncate what was already there");

            try (var files = Files.list(directory)) {
                assertEquals(List.of(path), files.toList(), "no temporary file may be left behind");
            }
        }
        finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void deserialized_isReadableButNotPollable() throws Exception {
        var video = new VideoGeneration(new Job("job-1", Status.RUNNING, "poll/job-1", null, null), OPTIONS, new RecordingSource());

        var revived = roundTrip(video);

        assertEquals("job-1", revived.jobId(), "the job id must survive serialization");
        assertEquals(Status.RUNNING, revived.status());
        assertThrows(IllegalStateException.class, revived::refresh);
        assertThrows(IllegalStateException.class, revived::completion);
    }

    @Test
    void job_requiresIdAndStatus() {
        assertThrows(IllegalArgumentException.class, () -> new Job("", Status.PENDING, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Job(null, Status.PENDING, null, null, null));
        assertThrows(NullPointerException.class, () -> new Job("job-1", null, null, null, null));
    }

    @Test
    void status_isTerminalOnlyWhenPollingCannotChangeIt() {
        assertEquals(List.of(false, false, true, true, true), List.of(Status.values()).stream().map(Status::isTerminal).toList());
    }

    private static VideoGeneration roundTrip(Serializable object) throws IOException, ClassNotFoundException {
        var bytes = new ByteArrayOutputStream();

        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(object);
        }

        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (VideoGeneration) input.readObject();
        }
    }

    /** Source whose completion is driven by the caller, to observe what canceling the returned future does to it. */
    private static class PollingSource implements Source {

        private final CompletableFuture<Job> polling;
        private int waits;

        private PollingSource(CompletableFuture<Job> polling) {
            this.polling = polling;
        }

        private int waits() {
            return waits;
        }

        @Override
        public Job pollVideo(Job job) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream downloadVideo(Job job) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Job> awaitVideoCompletion(Job job, GenerateVideoOptions options) {
            waits++;
            return waits == 1 ? polling : new CompletableFuture<>();
        }

    }

    /** Source whose download hands out a few bytes and then breaks, as a dropped connection would. */
    private record FailingDownloadSource() implements Source {

        @Override
        public Job pollVideo(Job job) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream downloadVideo(Job job) {
            return new InputStream() {

                private boolean served;

                @Override
                public int read() throws IOException {
                    throw new IOException("Connection reset");
                }

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    if (served) {
                        throw new IOException("Connection reset");
                    }

                    served = true;
                    var count = Math.min(length, VIDEO_CONTENT.length);
                    Arrays.fill(buffer, offset, offset + count, (byte) 'x');
                    return count;
                }

            };
        }

        @Override
        public CompletableFuture<Job> awaitVideoCompletion(Job job, GenerateVideoOptions options) {
            throw new UnsupportedOperationException();
        }

    }

    /** Source which answers each poll with the next scripted job, staying on the last one, and each download with fixed content. */
    private static class RecordingSource implements Source {

        private final Deque<Job> scriptedJobs;
        private Job lastJob;
        private int polls;
        private int downloads;

        private RecordingSource(Job... jobs) {
            scriptedJobs = new ArrayDeque<>(List.of(jobs));
        }

        @Override
        public Job pollVideo(Job job) {
            polls++;
            lastJob = scriptedJobs.isEmpty() ? lastJob : scriptedJobs.poll();
            return lastJob != null ? lastJob : job;
        }

        @Override
        public InputStream downloadVideo(Job job) {
            downloads++;
            return new ByteArrayInputStream(VIDEO_CONTENT);
        }

        private int downloads() {
            return downloads;
        }

        @Override
        public CompletableFuture<Job> awaitVideoCompletion(Job job, GenerateVideoOptions options) {
            var polled = job;

            while (!polled.status().isTerminal()) {
                polled = pollVideo(polled);
            }

            return completedFuture(polled);
        }

    }

}
