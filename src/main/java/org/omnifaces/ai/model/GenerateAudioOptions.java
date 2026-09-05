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

import org.omnifaces.ai.AIAudioHandler;

/**
 * Options for AI audio generation.
 * <p>
 * This class provides configuration options for AI audio generation operations, including voice, speed, and output format.
 * <p>
 * Note: Not all options are supported by all AI providers. Unsupported options are silently ignored.
 *
 * @author Bauke Scholtz
 * @since 1.2
 * @see org.omnifaces.ai.AIService#generateAudio(String, GenerateAudioOptions)
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
     * Gets the voice of the generated audio. Defaults to {@value #DEFAULT_VOICE}, which lets the
     * {@link AIAudioHandler#buildGenerateAudioPayload(org.omnifaces.ai.AIService, String, GenerateAudioOptions)} choose its own default. The available values
     * depend on the AI provider used.
     *
     * @return The voice name string; never {@code null}.
     */
    public String getVoice() {
        return voice;
    }

    /**
     * Gets the playback speed of the generated audio. Defaults to {@value #DEFAULT_SPEED}. The allowed values depend on the AI provider used.
     *
     * @return The playback speed.
     */
    public double getSpeed() {
        return speed;
    }

    /**
     * Gets the output format of the generated audio. Defaults to {@value #DEFAULT_OUTPUT_FORMAT}. The available values depend on the AI provider used, or it
     * may even be ignored.
     *
     * @return The output format string; never {@code null}.
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
     *
     * <pre>
     *
     * GenerateAudioOptions options = GenerateAudioOptions.newBuilder()
     *     .voice("breeze")
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

        private Builder() {
        }

        /**
         * Sets the voice of the generated audio. Defaults to {@value GenerateAudioOptions#DEFAULT_VOICE}. The available values depend on the AI provider used.
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
         * Sets the playback speed of the generated audio. Defaults to {@value GenerateAudioOptions#DEFAULT_SPEED}. The allowed values depend on the AI provider
         * used.
         *
         * @param speed The playback speed, must be positive.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException when speed is not positive.
         */
        public Builder speed(double speed) {
            if (speed <= 0) {
                throw new IllegalArgumentException("Speed must be positive");
            }
            this.speed = speed;
            return this;
        }

        /**
         * Sets the output format of the generated audio. Defaults to {@value GenerateAudioOptions#DEFAULT_OUTPUT_FORMAT}. The available values depend on the AI
         * provider used, or it may even be ignored.
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
