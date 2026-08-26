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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.omnifaces.ai.helper.FileHelper.cleanupFiles;
import static org.omnifaces.ai.helper.FileHelper.requireWritableFile;
import static org.omnifaces.ai.helper.TextHelper.requireNonBlank;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIException;

/**
 * A handle on one video generation job.
 * <p>
 * Video generation is the only operation which cannot be modeled as a single request: the AI provider answers the submission with a job id within seconds, then
 * takes minutes to produce the video, and never calls back. This handle represents that job, and offers three ways of arriving at the video:
 *
 * <pre>
 *
 * VideoGeneration video = service.generateVideo(prompt); // Returns at once, PENDING.
 *
 * Status status = video.status(); // Pure getter, performs no I/O.
 * VideoGeneration refreshed = video.refresh(); // Caller-driven poll, exactly one request.
 * CompletableFuture&lt;VideoGeneration&gt; completed = video.completion(); // Library-driven poll, completes when the job reaches a terminal status.
 * video.writeTo(path); // Once it has completed.
 * </pre>
 * <p>
 * The handle is serializable and carries the job id, so that a web application can submit the job in one request and poll it from later ones, possibly after a
 * restart or on another node. It is bound to the AI service which submitted the job rather than to any wrapper around it, so a decorator such as
 * {@code RetryingAIService} covers the submission but not the polling and the download; those reach the AI provider directly, which is what lets the handle
 * outlive the call that produced it. A deserialized handle has lost its connection to the AI service and can therefore only be read; revive it with
 * {@link AIService#findVideoGeneration(String)} to poll or download it again.
 *
 * @author Bauke Scholtz
 * @since 1.7
 * @see AIService#generateVideo(String, GenerateVideoOptions)
 * @see AIService#findVideoGeneration(String)
 */
public class VideoGeneration implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String TEMP_FILE_SUFFIX = ".omnihai.tmp";

    private static final String REVIVE_MESSAGE = "This video generation is deserialized. Use AIService#findVideoGeneration(String) to obtain a pollable one.";

    /**
     * The status of a video generation job.
     */
    public enum Status {

        /** The job is accepted and waiting to be picked up. */
        PENDING,

        /** The job is being processed. Not every AI provider distinguishes this from {@link #PENDING}. */
        RUNNING,

        /** The job has finished and the video is available for download. */
        COMPLETED,

        /** The job has failed. The reason, when the AI provider states one, is in {@link VideoGeneration#failureReason()}. */
        FAILED,

        /** The job has finished, but the AI provider has meanwhile deleted the video. Provider-hosted results live about a day. */
        EXPIRED;

        /**
         * Returns whether this status is terminal, i.e. polling it again cannot change it.
         *
         * @return {@code true} if this status is {@link #COMPLETED}, {@link #FAILED} or {@link #EXPIRED}.
         */
        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED || this == EXPIRED;
        }

    }

    /**
     * The AI provider's view of one video generation job, as parsed from a submit or poll response.
     * <p>
     * The paths are stated by those AI providers which name their own poll target or host the video elsewhere; a {@code null} leaves the AI service to derive
     * the path from the job id.
     *
     * @param id The job id.
     * @param status The job status.
     * @param pollPath The path to poll the job status at, or {@code null} to derive it from the job id.
     * @param contentPath The path to download the video from, or {@code null} to derive it from the job id.
     * @param failureReason The reason of a {@link Status#FAILED} job, or {@code null} if the AI provider states none.
     */
    public record Job(String id, Status status, String pollPath, String contentPath, String failureReason) implements Serializable {

        /**
         * Validates the record components.
         *
         * @param id The job id.
         * @param status The job status.
         * @param pollPath The path to poll the job status at, or {@code null} to derive it from the job id.
         * @param contentPath The path to download the video from, or {@code null} to derive it from the job id.
         * @param failureReason The reason of a {@link Status#FAILED} job, or {@code null} if the AI provider states none.
         * @throws IllegalArgumentException when id is null or blank.
         * @throws NullPointerException when status is null.
         */
        public Job {
            requireNonBlank(id, "id");
            requireNonNull(status, "status");
        }

        /**
         * Returns a new {@link Status#PENDING} job with the given id and poll path.
         *
         * @param id The job id.
         * @param pollPath The path to poll the job status at, or {@code null} to derive it from the job id.
         * @return A new pending job.
         */
        public static Job pending(String id, String pollPath) {
            return new Job(id, Status.PENDING, pollPath, null, null);
        }

    }

    /**
     * The AI service which submitted the job, performing the poll and download requests on its behalf.
     * <p>
     * This is implemented by {@code org.omnifaces.ai.service.BaseAIService}. A custom {@link AIService} which does not extend it needs to implement this in
     * order to support video generation.
     */
    public interface Source {

        /**
         * Polls the given job once and returns its current state.
         *
         * @param job The job to poll.
         * @return The current state of the job.
         * @throws AIException if the poll request fails.
         */
        Job pollVideo(Job job);

        /**
         * Opens a stream on the video of the given completed job. The caller closes it.
         *
         * @param job The job to download the video of.
         * @return The video content stream, which the caller must close.
         * @throws AIException if the download request fails.
         */
        InputStream downloadVideo(Job job);

        /**
         * Polls the given job at {@link GenerateVideoOptions#getPollInterval()} until it reaches a terminal status, failing once
         * {@link GenerateVideoOptions#getMaxWait()} has passed.
         *
         * @param job The job to poll.
         * @param options The video generation options the job was submitted with.
         * @return A future which completes with the terminal state of the job.
         */
        CompletableFuture<Job> awaitVideoCompletion(Job job, GenerateVideoOptions options);

    }

    /** The AI provider's view of the job, as of the last poll. */
    private volatile Job job;
    /** The options the job was submitted with, which state how long and how often to poll it. */
    private final GenerateVideoOptions options;
    /** The AI service which submitted the job, absent after deserialization. */
    private final transient Source source;
    /** The wait which {@link #completion()} started, so that a second caller joins it rather than polling the same job again. */
    private transient volatile CompletableFuture<VideoGeneration> completion;

    /**
     * Constructs a new handle on the given job.
     *
     * @param job The job to handle.
     * @param options The options the job was submitted with.
     * @param source The AI service which submitted the job.
     * @throws NullPointerException when any argument is null.
     */
    public VideoGeneration(Job job, GenerateVideoOptions options, Source source) {
        this.job = requireNonNull(job, "job");
        this.options = requireNonNull(options, "options");
        this.source = requireNonNull(source, "source");
    }

    /**
     * Returns the id which the AI provider assigned to this job. This is the value to hand to {@link AIService#findVideoGeneration(String)} later on.
     *
     * @return The job id, never {@code null}.
     */
    public String jobId() {
        return job.id();
    }

    /**
     * Returns the status as of the last poll. This performs no I/O and is therefore free to call from a render pass.
     *
     * @return The current status, never {@code null}.
     */
    public Status status() {
        return job.status();
    }

    /**
     * Returns the reason why the job failed, as stated by the AI provider.
     *
     * @return The failure reason, or {@code null} if the job did not fail or the AI provider states no reason.
     */
    public String failureReason() {
        return job.failureReason();
    }

    /**
     * Polls the AI provider once and updates {@link #status()} accordingly. This performs exactly one request, on the caller's schedule.
     * <p>
     * A poll which was already in flight when the job reached a terminal status is discarded, so the status never moves back off a terminal one.
     *
     * @return This handle for chaining.
     * @throws IllegalStateException if this handle is deserialized and the job is not already terminal.
     * @throws AIException if the poll request fails.
     */
    public VideoGeneration refresh() {
        if (!job.status().isTerminal()) {
            var polled = requireSource().pollVideo(job);

            synchronized (this) {
                if (!job.status().isTerminal()) {
                    job = polled;
                }
            }
        }

        return this;
    }

    /**
     * Polls the AI provider at the configured interval until the job reaches a terminal status.
     * <p>
     * The polling starts when this method is called and stops as soon as the returned future is completed or canceled, so a handle which nobody watches costs
     * nothing. A job which has not reached a terminal status within {@link GenerateVideoOptions#getMaxWait()} fails the returned future rather than polling on.
     * <p>
     * Calling this again while a wait is already under way hands back that same future rather than polling the job twice, so watching one job from several
     * places costs one stream of requests. It follows that canceling the returned future cancels the wait for every one of them. A wait which is already
     * finished, whether by completing, failing or being canceled, is not handed back; the next call starts a new one.
     *
     * @return A future which completes with this handle once the job is no longer {@link Status#PENDING} or {@link Status#RUNNING}.
     * @throws IllegalStateException if this handle is deserialized and the job is not already terminal.
     */
    public CompletableFuture<VideoGeneration> completion() {
        if (job.status().isTerminal()) {
            return completedFuture(this);
        }

        var current = completion;

        if (current != null && !current.isDone()) {
            return current;
        }

        synchronized (this) {
            if (completion == null || completion.isDone()) {
                completion = startPolling();
            }

            return completion;
        }
    }

    private CompletableFuture<VideoGeneration> startPolling() {
        var polling = requireSource().awaitVideoCompletion(job, options);
        var awaited = polling.thenApply(polled -> {
            synchronized (this) {
                job = polled;
            }

            return this;
        });
        awaited.whenComplete((video, throwable) -> polling.cancel(true));
        return awaited;
    }

    /**
     * Writes the generated video to the given path, replacing an existing file.
     *
     * @param path The path to write the video to.
     * @throws NullPointerException when path is null.
     * @throws IllegalArgumentException when path names no file, or its directory does not exist or cannot be written to.
     * @throws IllegalStateException if the job has not completed yet, or if this handle is deserialized.
     * @throws AIException if the job failed or expired, or if the download request fails.
     * @throws UncheckedIOException if writing to the path fails.
     */
    public void writeTo(Path path) {
        requireWritableFile(path);

        try (var content = openContent()) {
            copyToPath(content, path);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Cannot write generated video to path " + path, e);
        }
    }

    /**
     * Writes the generated video to the given output stream. The stream is not closed.
     *
     * @param output The output stream to write the video to.
     * @throws NullPointerException when output is null.
     * @throws IllegalStateException if the job has not completed yet, or if this handle is deserialized.
     * @throws AIException if the job failed or expired, or if the download request fails.
     * @throws UncheckedIOException if writing to the output stream fails.
     */
    public void writeTo(OutputStream output) {
        requireNonNull(output, "output");

        try (var content = openContent()) {
            content.transferTo(output);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Cannot write generated video to output stream", e);
        }
    }

    /**
     * Copies the content through a temporary file beside the target and moves it into place once it is complete, so that a transfer which fails midway leaves
     * the target untouched rather than truncated. The temporary file is a sibling so that the move stays within one file system, and so that the video ends up
     * with the permissions the file system grants a new file rather than those of a private temporary one. Its name carries a nanosecond stamp so that two
     * writers of one target do not interleave into each other's.
     */
    private static void copyToPath(InputStream content, Path path) throws IOException {
        var temp = path.resolveSibling(path.getFileName() + "." + System.nanoTime() + TEMP_FILE_SUFFIX);
        var moved = false;

        try {
            Files.copy(content, temp, REPLACE_EXISTING);
            Files.move(temp, path, REPLACE_EXISTING);
            moved = true;
        }
        finally {
            if (!moved) {
                cleanupFiles(temp);
            }
        }
    }

    private InputStream openContent() {
        var current = job;

        return switch (current.status()) {
            case COMPLETED -> requireSource().downloadVideo(current);
            case FAILED -> throw new AIException("Video generation job " + current.id() + " failed: " + ofNullable(current.failureReason()).orElse("unknown"));
            case EXPIRED -> throw new AIException("Video generation job " + current.id() + " expired and is no longer hosted by the AI provider");
            default -> throw new IllegalStateException("Video generation job " + current.id() + " is still " + current.status() + ", await completion() first");
        };
    }

    private Source requireSource() {
        if (source == null) {
            throw new IllegalStateException(REVIVE_MESSAGE);
        }

        return source;
    }

    @Override
    public String toString() {
        return "VideoGeneration[" + job.id() + ", " + job.status() + "]";
    }

}
