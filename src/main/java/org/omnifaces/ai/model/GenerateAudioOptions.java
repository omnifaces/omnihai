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

/**
 * Options for AI audio generation.
 * <p>
 * This class provides configuration options for AI audio generation operations, including voice, speed, and output format.
 * <p>
 * Note: Not all options are supported by all AI providers. Unsupported options are silently ignored.
 *
 * @author Bauke Scholtz
 * @since 1.2
 * @see org.omnifaces.ai.AIService#generateAudio(String)
 */
public class GenerateAudioOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Default voice: {@value}. */
    public static final String DEFAULT_VOICE = "auto";

    /** Default speed: {@value}. */
    public static final double DEFAULT_SPEED = 1.0;

    /** Default output format: {@value}. */
    public static final String DEFAULT_OUTPUT_FORMAT = "mp3";

    /** Default audio generation options. */
    public static final GenerateAudioOptions DEFAULT = GenerateAudioOptions.newBuilder().build();

    /** The audio voice. */
    private final String voice;
    /** The audio playback speed. */
    private final double speed;
    /** The output format. */
    private final String outputFormat;

    private GenerateAudioOptions(Builder builder) {
        this.voice = builder.voice;
        this.speed = builder.speed;
        this.outputFormat = builder.outputFormat;
    }

    /**
     * Gets the voice of the generated audio. Defaults to {@value #DEFAULT_VOICE}.
     * <p>
     * Common values include:
     * <ul>
     * <li>{@code "auto"} - Let the model choose the best voice
     * <li>{@code "alloy"}, {@code "ash"}, {@code "coral"}, {@code "echo"}, {@code "fable"}, {@code "onyx"}, {@code "nova"}, {@code "shimmer"} - OpenAI voices
     * </ul>
     *
     * @return The voice name string.
     */
    public String getVoice() {
        return voice;
    }

    /**
     * Gets the playback speed of the generated audio. Defaults to {@value #DEFAULT_SPEED}.
     *
     * @return The playback speed, between 0.25 and 4.0.
     */
    public double getSpeed() {
        return speed;
    }

    /**
     * Gets the output format of the generated audio. Defaults to {@value #DEFAULT_OUTPUT_FORMAT}.
     * <p>
     * Common values include:
     * <ul>
     * <li>{@code "mp3"} - MP3 format
     * <li>{@code "opus"} - Opus format
     * <li>{@code "aac"} - AAC format
     * <li>{@code "flac"} - FLAC format
     * <li>{@code "wav"} - WAV format
     * <li>{@code "pcm"} - PCM format
     * </ul>
     *
     * @return The output format string.
     */
    public String getOutputFormat() {
        return outputFormat;
    }

    /**
     * Returns whether the voice is set to the default value {@value #DEFAULT_VOICE}.
     *
     * @return {@code true} if the voice equals {@value #DEFAULT_VOICE}.
     */
    public boolean useDefaultVoice() {
        return DEFAULT_VOICE.equals(voice);
    }

    /**
     * Creates a new builder for constructing {@link GenerateAudioOptions} instances. For example:
     * <pre>
     * GenerateAudioOptions options = GenerateAudioOptions.newBuilder()
     *     .voice("alloy")
     *     .speed(1.5)
     *     .build();
     * </pre>
     *
     * @return A new {@code GenerateAudioOptions.Builder} instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for creating {@link GenerateAudioOptions} instances.
     * <p>
     * Use {@link GenerateAudioOptions#newBuilder()} to obtain a new builder instance.
     */
    public static class Builder {
        private String voice = GenerateAudioOptions.DEFAULT_VOICE;
        private double speed = GenerateAudioOptions.DEFAULT_SPEED;
        private String outputFormat = GenerateAudioOptions.DEFAULT_OUTPUT_FORMAT;

        private Builder() {}

        /**
         * Sets the voice of the generated audio. Defaults to {@value GenerateAudioOptions#DEFAULT_VOICE}.
         *
         * @param voice The voice name string.
         * @return This builder instance for chaining.
         * @throws NullPointerException when voice is null.
         */
        public Builder voice(String voice) {
            this.voice = requireNonNull(voice, "voice");
            return this;
        }

        /**
         * Sets the playback speed of the generated audio. Defaults to {@value GenerateAudioOptions#DEFAULT_SPEED}.
         *
         * @param speed The playback speed, must be between 0.25 and 4.0.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException when speed is not between 0.25 and 4.0.
         */
        public Builder speed(double speed) {
            if (speed < 0.25 || speed > 4.0) {
                throw new IllegalArgumentException("Speed must be between 0.25 and 4.0"); // OpenAI
            }
            this.speed = speed;
            return this;
        }

        /**
         * Sets the output format of the generated audio. Defaults to {@value GenerateAudioOptions#DEFAULT_OUTPUT_FORMAT}.
         *
         * @param outputFormat The output format string.
         * @return This builder instance for chaining.
         * @throws NullPointerException when output format is null.
         */
        public Builder outputFormat(String outputFormat) {
            this.outputFormat = requireNonNull(outputFormat, "outputFormat");
            return this;
        }

        /**
         * Finalizes the configuration and creates a {@link GenerateAudioOptions} instance.
         *
         * @return A fully configured {@code GenerateAudioOptions} object.
         */
        public GenerateAudioOptions build() {
            return new GenerateAudioOptions(this);
        }
    }
}
