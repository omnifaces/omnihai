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
import static org.omnifaces.ai.helper.TextHelper.requireNonBlank;
import static org.omnifaces.ai.model.Dimensions.calculateAspectRatioBasedOnSize;
import static org.omnifaces.ai.model.Dimensions.requireValidAspectRatio;
import static org.omnifaces.ai.model.Dimensions.requireValidSize;

import java.io.Serializable;
import java.time.Duration;

/**
 * Options for AI video generation.
 * <p>
 * This class provides configuration options for AI video generation operations, including size, aspect ratio, resolution, duration and poll interval.
 * <p>
 * Note: Not all options are supported by all AI providers. Unsupported options are silently ignored.
 *
 * @author Bauke Scholtz
 * @since 1.7
 * @see org.omnifaces.ai.AIService#generateVideo(String, GenerateVideoOptions)
 */
public class GenerateVideoOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Default size: {@value}. */
    public static final String DEFAULT_SIZE = "auto";

    /** Default aspect ratio: {@value}. */
    public static final String DEFAULT_ASPECT_RATIO = "16:9";

    /** Default resolution: {@value}. */
    public static final String DEFAULT_RESOLUTION = "auto";

    /** Default duration in seconds: {@value}, which lets the AI provider choose its own default. */
    public static final int DEFAULT_SECONDS = 0;

    /** Default poll interval in seconds: {@value}. */
    public static final int DEFAULT_POLL_INTERVAL_SECONDS = 5;

    /** Default poll interval. */
    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(DEFAULT_POLL_INTERVAL_SECONDS);

    /** Default maximum wait in minutes: {@value}. */
    public static final int DEFAULT_MAX_WAIT_MINUTES = 5;

    /** Default maximum wait. */
    public static final Duration DEFAULT_MAX_WAIT = Duration.ofMinutes(DEFAULT_MAX_WAIT_MINUTES);

    /** Default video generation options. */
    public static final GenerateVideoOptions DEFAULT = GenerateVideoOptions.newBuilder().build();

    /** The video size. */
    private final String size;
    /** The video aspect ratio. */
    private final String aspectRatio;
    /** The video resolution. */
    private final String resolution;
    /** The video duration in seconds. */
    private final int seconds;
    /** The interval between two consecutive job status polls. */
    private final Duration pollInterval;
    /** The maximum time to wait for the job to reach a terminal status. */
    private final Duration maxWait;

    private GenerateVideoOptions(Builder builder) {
        this.size = builder.size;
        this.aspectRatio = builder.aspectRatio;
        this.resolution = builder.resolution;
        this.seconds = builder.seconds;
        this.pollInterval = builder.pollInterval;
        this.maxWait = builder.maxWait;
    }

    /**
     * Gets the size of the generated video. Defaults to {@value #DEFAULT_SIZE}.
     * <p>
     * Common values include:
     * <ul>
     * <li>{@code "auto"} - Let the model choose the best size
     * <li>{@code "1280x720"}, {@code "1920x1080"} - Landscape video
     * <li>{@code "720x1280"}, {@code "1080x1920"} - Portrait video
     * <li>{@code "1024x1024"} - Square video
     * </ul>
     *
     * @return The video size string; never {@code null}.
     */
    public String getSize() {
        return size;
    }

    /**
     * Gets the aspect ratio of the generated video. Defaults to {@value #DEFAULT_ASPECT_RATIO}.
     * <p>
     * Common values include:
     * <ul>
     * <li>{@code "16:9"}, {@code "4:3"}, {@code "3:2"} - Landscape video
     * <li>{@code "9:16"}, {@code "3:4"}, {@code "2:3"} - Portrait video
     * <li>{@code "1:1"} - Square video
     * </ul>
     *
     * @return The video aspect ratio string; never {@code null}.
     */
    public String getAspectRatio() {
        return aspectRatio;
    }

    /**
     * Gets the resolution of the generated video. Defaults to {@value #DEFAULT_RESOLUTION}, which lets the AI provider choose its own default.
     * <p>
     * Common values include:
     * <ul>
     * <li>{@code "auto"} - Let the model choose the best resolution
     * <li>{@code "480p"} - Standard definition, fastest and cheapest
     * <li>{@code "720p"} - High definition
     * <li>{@code "1080p"} - Full high definition, slowest and most expensive
     * </ul>
     *
     * @return The video resolution string; never {@code null}.
     */
    public String getResolution() {
        return resolution;
    }

    /**
     * Gets the duration of the generated video in seconds. Defaults to {@value #DEFAULT_SECONDS}, which lets the AI provider choose its own default. The
     * allowed values depend on the AI provider used.
     *
     * @return The duration in seconds.
     */
    public int getSeconds() {
        return seconds;
    }

    /**
     * Gets the interval to wait between two consecutive job status polls of {@link VideoGeneration#completion()}. Defaults to
     * {@value #DEFAULT_POLL_INTERVAL_SECONDS} seconds.
     *
     * @return The poll interval; never {@code null}.
     */
    public Duration getPollInterval() {
        return pollInterval;
    }

    /**
     * Gets the maximum time to wait for the job to reach a terminal status, after which {@link VideoGeneration#completion()} fails rather than polls on.
     * Defaults to {@value #DEFAULT_MAX_WAIT_MINUTES} minutes. Raise it for an AI provider or resolution which takes longer than that.
     *
     * @return The maximum wait; never {@code null}.
     */
    public Duration getMaxWait() {
        return maxWait;
    }

    /**
     * Returns whether the generated video is taller than wide, as stated by the aspect ratio. A square one is neither portrait nor landscape.
     *
     * @return {@code true} if the aspect ratio is portrait.
     */
    public boolean isPortrait() {
        return Dimensions.isPortrait(aspectRatio);
    }

    /**
     * Returns whether the generated video is wider than tall, as stated by the aspect ratio. A square one is neither portrait nor landscape.
     *
     * @return {@code true} if the aspect ratio is landscape.
     */
    public boolean isLandscape() {
        return Dimensions.isLandscape(aspectRatio);
    }

    /**
     * Returns whether the size is set to the default value {@value #DEFAULT_SIZE}, in which case the aspect ratio states the shape instead.
     *
     * @return {@code true} if the size equals {@value #DEFAULT_SIZE}.
     */
    public boolean useDefaultSize() {
        return DEFAULT_SIZE.equals(size);
    }

    /**
     * Returns whether the resolution is set to the default value {@value #DEFAULT_RESOLUTION}.
     *
     * @return {@code true} if the resolution equals {@value #DEFAULT_RESOLUTION}.
     */
    public boolean useDefaultResolution() {
        return DEFAULT_RESOLUTION.equals(resolution);
    }

    /**
     * Returns whether the duration is set to the default value {@value #DEFAULT_SECONDS}.
     *
     * @return {@code true} if the duration equals {@value #DEFAULT_SECONDS}.
     */
    public boolean useDefaultSeconds() {
        return seconds == DEFAULT_SECONDS;
    }

    /**
     * Creates a new builder for constructing {@link GenerateVideoOptions} instances. For example:
     *
     * <pre>
     *
     * GenerateVideoOptions options = GenerateVideoOptions.newBuilder()
     *     .aspectRatio("9:16")
     *     .resolution("720p")
     *     .seconds(8)
     *     .maxWait(Duration.ofMinutes(10))
     *     .build();
     * </pre>
     *
     * @return A new {@code GenerateVideoOptions.Builder} instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for creating {@link GenerateVideoOptions} instances.
     * <p>
     * Use {@link GenerateVideoOptions#newBuilder()} to obtain a new builder instance.
     */
    public static class Builder {

        private String size = GenerateVideoOptions.DEFAULT_SIZE;
        private String aspectRatio = GenerateVideoOptions.DEFAULT_ASPECT_RATIO;
        private String resolution = GenerateVideoOptions.DEFAULT_RESOLUTION;
        private int seconds = GenerateVideoOptions.DEFAULT_SECONDS;
        private Duration pollInterval = GenerateVideoOptions.DEFAULT_POLL_INTERVAL;
        private Duration maxWait = GenerateVideoOptions.DEFAULT_MAX_WAIT;

        private Builder() {
        }

        /**
         * Sets the size of the generated video. Defaults to {@value GenerateVideoOptions#DEFAULT_SIZE}.
         * <p>
         * Common values include:
         * <ul>
         * <li>{@code "1280x720"}, {@code "1920x1080"} - Landscape video
         * <li>{@code "720x1280"}, {@code "1080x1920"} - Portrait video
         * <li>{@code "1024x1024"} - Square video
         * </ul>
         * <p>
         * Setting the size will recalculate the aspect ratio.
         *
         * @param size The video size string.
         * @return This builder instance for chaining.
         * @throws NullPointerException when size is null.
         * @throws IllegalArgumentException when size is invalid.
         */
        public Builder size(String size) {
            this.size = requireValidSize(size);
            this.aspectRatio = calculateAspectRatioBasedOnSize(size);
            return this;
        }

        /**
         * Sets the aspect ratio of the generated video. Defaults to {@value GenerateVideoOptions#DEFAULT_ASPECT_RATIO}.
         * <p>
         * Common values include:
         * <ul>
         * <li>{@code "16:9"}, {@code "4:3"}, {@code "3:2"} - Landscape video
         * <li>{@code "9:16"}, {@code "3:4"}, {@code "2:3"} - Portrait video
         * <li>{@code "1:1"} - Square video
         * </ul>
         * <p>
         * Setting the aspect ratio will reset the size.
         *
         * @param aspectRatio The video aspect ratio string.
         * @return This builder instance for chaining.
         * @throws NullPointerException when aspect ratio is null.
         * @throws IllegalArgumentException when aspect ratio is invalid.
         */
        public Builder aspectRatio(String aspectRatio) {
            this.aspectRatio = requireValidAspectRatio(aspectRatio);
            this.size = DEFAULT_SIZE;
            return this;
        }

        /**
         * Sets the resolution of the generated video. Defaults to {@value GenerateVideoOptions#DEFAULT_RESOLUTION}.
         * <p>
         * Common values include:
         * <ul>
         * <li>{@code "auto"} - Let the model choose the best resolution
         * <li>{@code "480p"} - Standard definition, fastest and cheapest
         * <li>{@code "720p"} - High definition
         * <li>{@code "1080p"} - Full high definition, slowest and most expensive
         * </ul>
         *
         * @param resolution The video resolution string.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException when resolution is null or blank.
         */
        public Builder resolution(String resolution) {
            this.resolution = requireNonBlank(resolution, "resolution");
            return this;
        }

        /**
         * Sets the duration of the generated video in seconds. Defaults to {@value GenerateVideoOptions#DEFAULT_SECONDS}, which lets the AI provider choose its
         * own default. The allowed values depend on the AI provider used.
         *
         * @param seconds The duration in seconds, must be positive.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException when seconds is not positive.
         */
        public Builder seconds(int seconds) {
            if (seconds <= 0) {
                throw new IllegalArgumentException("Seconds must be positive");
            }
            this.seconds = seconds;
            return this;
        }

        /**
         * Sets the interval to wait between two consecutive job status polls of {@link VideoGeneration#completion()}. Defaults to
         * {@value GenerateVideoOptions#DEFAULT_POLL_INTERVAL_SECONDS} seconds.
         *
         * @param pollInterval The poll interval, must be positive.
         * @return This builder instance for chaining.
         * @throws NullPointerException when poll interval is null.
         * @throws IllegalArgumentException when poll interval is not positive.
         */
        public Builder pollInterval(Duration pollInterval) {
            this.pollInterval = requirePositive(pollInterval, "pollInterval");
            return this;
        }

        private static Duration requirePositive(Duration duration, String varName) {
            if (requireNonNull(duration, varName).isNegative() || duration.isZero()) {
                throw new IllegalArgumentException(varName + " must be positive");
            }

            return duration;
        }

        /**
         * Sets the maximum time to wait for the job to reach a terminal status, after which {@link VideoGeneration#completion()} fails rather than polls on.
         * Defaults to {@value GenerateVideoOptions#DEFAULT_MAX_WAIT_MINUTES} minutes.
         *
         * @param maxWait The maximum wait, must be positive.
         * @return This builder instance for chaining.
         * @throws NullPointerException when maximum wait is null.
         * @throws IllegalArgumentException when maximum wait is not positive.
         */
        public Builder maxWait(Duration maxWait) {
            this.maxWait = requirePositive(maxWait, "maxWait");
            return this;
        }

        /**
         * Finalizes the configuration and creates a {@link GenerateVideoOptions} instance.
         *
         * @return A fully configured {@code GenerateVideoOptions} object.
         */
        public GenerateVideoOptions build() {
            return new GenerateVideoOptions(this);
        }

    }

}
