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
import java.util.regex.Pattern;

/**
 * Options for AI image generation.
 * <p>
 * This class provides configuration options for AI image generation operations, including size, quality, and style.
 * <p>
 * Note: Not all options are supported by all AI providers. Unsupported options are silently ignored.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see org.omnifaces.ai.AIService#generateImage(String, GenerateImageOptions)
 */
public class GenerateImageOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Default size: {@value}. */
    public static final String DEFAULT_SIZE = "auto";

    /** Default aspect ratio: {@value}. */
    public static final String DEFAULT_ASPECT_RATIO = "1:1";

    /** Default quality: {@value}. */
    public static final String DEFAULT_QUALITY = "auto";

    /** Default output format: {@value}. */
    public static final String DEFAULT_OUTPUT_FORMAT = "png";

    /** Default image generation options. */
    public static final GenerateImageOptions DEFAULT = GenerateImageOptions.newBuilder().build();

    /** The image size. */
    private final String size;
    /** The image aspect ratio. */
    private final String aspectRatio;
    /** The image quality. */
    private final String quality;
    /** The output format. */
    private final String outputFormat;

    private GenerateImageOptions(Builder builder) {
        this.size = builder.size;
        this.aspectRatio = builder.aspectRatio;
        this.quality = builder.quality;
        this.outputFormat = builder.outputFormat;
    }

    /**
     * Gets the size of the generated image. Defaults to {@value #DEFAULT_SIZE}.
     * <p>
     * Common values include:
     * <ul>
     * <li>{@code "auto"} - Let the model choose the best size
     * <li>{@code "1024x1024"} - Square image
     * <li>{@code "1792x1024"} - Landscape image
     * <li>{@code "1024x1792"} - Portrait image
     * </ul>
     *
     * @return The image size string.
     */
    public String getSize() {
        return size;
    }

    /**
     * Gets the aspect ratio of the generated image. Defaults to {@value #DEFAULT_ASPECT_RATIO}.
     * <p>
     * Common values include:
     * <ul>
     * <li>{@code "1:1"} - Square image
     * <li>{@code "3:2"}, {@code "4:3"}, {@code "5:4"}, {@code 16:9} - Landscape image
     * <li>{@code "2:3"}, {@code "3:4"}, {@code "4:5"}, {@code 9:16} - Portrait image
     * </ul>
     *
     * @return The image aspect ratio string.
     */
    public String getAspectRatio() {
        return aspectRatio;
    }

    /**
     * Gets the quality of the generated image. Defaults to {@value #DEFAULT_QUALITY}.
     * <p>
     * Common values include:
     * <ul>
     * <li>{@code "auto"} - Let the model choose the best quality
     * <li>{@code "standard"} - Standard quality (faster, cheaper)
     * <li>{@code "hd"} - High definition quality
     * <li>{@code "low"}, {@code "medium"}, {@code "high"} - Quality levels
     * </ul>
     *
     * @return The image quality string.
     */
    public String getQuality() {
        return quality;
    }

    /**
     * Gets the output format of the generated image. Defaults to {@value #DEFAULT_OUTPUT_FORMAT}.
     * <p>
     * Common values include:
     * <ul>
     * <li>{@code "png"} - PNG format
     * <li>{@code "jpeg"} - JPEG format
     * <li>{@code "webp"} - WebP format
     * </ul>
     *
     * @return The output format string.
     */
    public String getOutputFormat() {
        return outputFormat;
    }

    /**
     * Creates a new builder for constructing {@link GenerateImageOptions} instances. For example:
     * <pre>
     * GenerateImageOptions options = GenerateImageOptions.newBuilder()
     *     .size("1024x1024")
     *     .build();
     * </pre>
     *
     * @return A new {@code GenerateImageOptions.Builder} instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for creating {@link GenerateImageOptions} instances.
     * <p>
     * Use {@link GenerateImageOptions#newBuilder()} to obtain a new builder instance.
     */
    public static class Builder {
        private static final Pattern SIZE_PATTERN = Pattern.compile("\\d+x\\d+");
        private static final Pattern ASPECT_RATIO_PATTERN = Pattern.compile("\\d+:\\d+");

        private String size = GenerateImageOptions.DEFAULT_SIZE;
        private String aspectRatio = GenerateImageOptions.DEFAULT_ASPECT_RATIO;
        private String quality = GenerateImageOptions.DEFAULT_QUALITY;
        private String outputFormat = GenerateImageOptions.DEFAULT_OUTPUT_FORMAT;

        private Builder() {}

        /**
         * Sets the size of the generated image. Defaults to {@value GenerateImageOptions#DEFAULT_SIZE}.
         * <p>
         * Common values include:
         * <ul>
         * <li>{@code "auto"} - Let the model choose the best size
         * <li>{@code "1024x1024"} - Square image
         * <li>{@code "1792x1024"} - Landscape image
         * <li>{@code "1024x1792"} - Portrait image
         * </ul>
         * <p>
         * Setting the size will recalculate the aspect ratio.
         *
         * @param size The image size string.
         * @return This builder instance for chaining.
         * @throws NullPointerException when size is null.
         * @throws IllegalArgumentException when size is invalid.
         */
        public Builder size(String size) {
            requireNonNull(size, "size");

            if (!SIZE_PATTERN.matcher(size).matches()) {
                throw new IllegalArgumentException("Invalid size: " + size);
            }

            this.size = size;
            this.aspectRatio = calculateAspectRatioBasedOnSize(size);
            return this;
        }

        /**
         * Sets the aspect ratio of the generated image. Defaults to {@value GenerateImageOptions#DEFAULT_ASPECT_RATIO}.
         * <p>
         * Common values include:
         * <ul>
         * <li>{@code "1:1"} - Square image
         * <li>{@code "3:2"}, {@code "4:3"}, {@code "5:4"}, {@code 16:9} - Landscape image
         * <li>{@code "2:3"}, {@code "3:4"}, {@code "4:5"}, {@code 9:16} - Portrait image
         * </ul>
         * <p>
         * Setting the aspect ratio will reset the size.
         *
         * @param aspectRatio The image aspect ratio string.
         * @return This builder instance for chaining.
         * @throws NullPointerException when aspect ratio is null.
         * @throws IllegalArgumentException when aspect ratio is invalid.
         */
        public Builder aspectRatio(String aspectRatio) {
            requireNonNull(aspectRatio, "aspectRatio");

            if (!ASPECT_RATIO_PATTERN.matcher(aspectRatio).matches()) {
                throw new IllegalArgumentException("Invalid aspect ratio: " + aspectRatio);
            }

            this.aspectRatio = aspectRatio;
            this.size = DEFAULT_SIZE;
            return this;
        }

        /**
         * Sets the quality of the generated image. Defaults to {@value GenerateImageOptions#DEFAULT_QUALITY}.
         * <p>
         * Common values include:
         * <ul>
         * <li>{@code "auto"} - Let the model choose the best quality
         * <li>{@code "standard"} - Standard quality (faster, cheaper)
         * <li>{@code "hd"} - High definition quality
         * <li>{@code "low"}, {@code "medium"}, {@code "high"} - Quality levels
         * </ul>
         *
         * @param quality The image quality string.
         * @return This builder instance for chaining.
         * @throws NullPointerException when quality is null.
         */
        public Builder quality(String quality) {
            this.quality = requireNonNull(quality, "quality");
            return this;
        }

        /**
         * Sets the output format of the generated image. Defaults to {@value GenerateImageOptions#DEFAULT_OUTPUT_FORMAT}.
         * <p>
         * Common values include:
         * <ul>
         * <li>{@code "png"} - PNG format
         * <li>{@code "jpeg"} - JPEG format
         * <li>{@code "webp"} - WebP format
         * </ul>
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
         * Finalizes the configuration and creates a {@link GenerateImageOptions} instance.
         *
         * @return A fully configured {@code GenerateImageOptions} object.
         */
        public GenerateImageOptions build() {
            return new GenerateImageOptions(this);
        }

        private static String calculateAspectRatioBasedOnSize(String size) {
            var parts = size.split("x");
            var width = Integer.parseInt(parts[0]);
            var height = Integer.parseInt(parts[1]);
            var greatestCommonDivisor = calculateGreatestCommonDivisor(width, height);
            return (width / greatestCommonDivisor) + ":" + (height / greatestCommonDivisor);
        }

        private static int calculateGreatestCommonDivisor(int a, int b) {
            return b == 0 ? a : calculateGreatestCommonDivisor(b, a % b);
        }
    }
}
