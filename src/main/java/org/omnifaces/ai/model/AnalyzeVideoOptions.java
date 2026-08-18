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

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.time.Duration;

/**
 * Options for AI video analysis.
 * <p>
 * This class provides configuration options for AI video analysis operations, being the frame sampling rate and the clip to analyze.
 * <p>
 * Note: Not all options are supported by all AI providers. Unsupported options are silently ignored.
 *
 * @author Bauke Scholtz
 * @since 1.7
 * @see org.omnifaces.ai.AIService#analyzeVideo(byte[], String, AnalyzeVideoOptions)
 */
public class AnalyzeVideoOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Default frames per second: {@value}, which leaves the sampling rate to the AI provider. */
    public static final double DEFAULT_FPS = 0.0;

    /** Default video analysis options. */
    public static final AnalyzeVideoOptions DEFAULT = AnalyzeVideoOptions.newBuilder().build();

    /** The frame sampling rate. */
    private final double fps;
    /** The offset at which the analyzed clip starts. */
    private final Duration startOffset;
    /** The offset at which the analyzed clip ends. */
    private final Duration endOffset;

    private AnalyzeVideoOptions(Builder builder) {
        this.fps = builder.fps;
        this.startOffset = builder.startOffset;
        this.endOffset = builder.endOffset;
    }

    /**
     * Gets the frame sampling rate in frames per second. Defaults to {@value #DEFAULT_FPS}, which leaves the sampling rate to the AI provider. A rate below 1
     * suits long and mostly static video such as a recorded lecture, a rate above 1 suits fast motion.
     *
     * @return The frame sampling rate, or {@value #DEFAULT_FPS} if unset.
     */
    public double getFps() {
        return fps;
    }

    /**
     * Gets the offset at which the analyzed clip starts. Defaults to {@code null}, which starts at the beginning of the video.
     *
     * @return The start offset, or {@code null} if unset.
     */
    public Duration getStartOffset() {
        return startOffset;
    }

    /**
     * Gets the offset at which the analyzed clip ends. Defaults to {@code null}, which ends at the end of the video.
     *
     * @return The end offset, or {@code null} if unset.
     */
    public Duration getEndOffset() {
        return endOffset;
    }

    /**
     * Returns whether every option is unset, in which case the AI provider analyzes the whole video at its own sampling rate.
     *
     * @return {@code true} if every option is unset.
     */
    public boolean isDefault() {
        return fps == DEFAULT_FPS && startOffset == null && endOffset == null;
    }

    @Override
    public String toString() {
        return "AnalyzeVideoOptions[fps=" + fps + ", startOffset=" + startOffset + ", endOffset=" + endOffset + "]";
    }

    /**
     * Creates a new builder for constructing {@link AnalyzeVideoOptions} instances. For example:
     *
     * <pre>
     *
     * AnalyzeVideoOptions options = AnalyzeVideoOptions.newBuilder()
     *     .fps(0.5)
     *     .startOffset(Duration.ofSeconds(30))
     *     .endOffset(Duration.ofMinutes(2))
     *     .build();
     * </pre>
     *
     * @return A new {@code AnalyzeVideoOptions.Builder} instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for creating {@link AnalyzeVideoOptions} instances.
     * <p>
     * Use {@link AnalyzeVideoOptions#newBuilder()} to obtain a new builder instance.
     */
    public static class Builder {

        private double fps = AnalyzeVideoOptions.DEFAULT_FPS;
        private Duration startOffset;
        private Duration endOffset;

        private Builder() {
        }

        /**
         * Sets the frame sampling rate in frames per second. Defaults to {@value AnalyzeVideoOptions#DEFAULT_FPS}, which leaves the sampling rate to the AI
         * provider. The allowed values depend on the AI provider used.
         *
         * @param fps The frame sampling rate, must be positive.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException when fps is not positive.
         */
        public Builder fps(double fps) {
            if (fps <= 0) {
                throw new IllegalArgumentException("Fps must be positive");
            }
            this.fps = fps;
            return this;
        }

        /**
         * Sets the offset at which the analyzed clip starts. Defaults to the beginning of the video.
         *
         * @param startOffset The start offset, must not be negative.
         * @return This builder instance for chaining.
         * @throws NullPointerException when start offset is null.
         * @throws IllegalArgumentException when start offset is negative.
         */
        public Builder startOffset(Duration startOffset) {
            this.startOffset = requireNonNegative(startOffset, "startOffset");
            return this;
        }

        /**
         * Sets the offset at which the analyzed clip ends. Defaults to the end of the video.
         *
         * @param endOffset The end offset, must be positive.
         * @return This builder instance for chaining.
         * @throws NullPointerException when end offset is null.
         * @throws IllegalArgumentException when end offset is not positive.
         */
        public Builder endOffset(Duration endOffset) {
            if (requireNonNull(endOffset, "endOffset").isZero() || endOffset.isNegative()) {
                throw new IllegalArgumentException("endOffset must be positive");
            }

            this.endOffset = endOffset;
            return this;
        }

        /**
         * Finalizes the configuration and creates an {@link AnalyzeVideoOptions} instance.
         *
         * @return A fully configured {@code AnalyzeVideoOptions} object.
         * @throws IllegalArgumentException when the end offset is not after the start offset.
         */
        public AnalyzeVideoOptions build() {
            if (startOffset != null && endOffset != null && endOffset.compareTo(startOffset) <= 0) {
                throw new IllegalArgumentException("End offset must be after start offset");
            }

            return new AnalyzeVideoOptions(this);
        }

        private static Duration requireNonNegative(Duration offset, String name) {
            if (requireNonNull(offset, name).isNegative()) {
                throw new IllegalArgumentException(name + " must not be negative");
            }

            return offset;
        }

    }

}
