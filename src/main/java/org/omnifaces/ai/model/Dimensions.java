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

import java.util.regex.Pattern;

/**
 * Reconciles the size and the aspect ratio of generated media, which are two views on one and the same shape.
 * <p>
 * AI providers state the shape of what they generate in one of both vocabularies and rarely in both, so the options classes accept either and keep the other in
 * step: stating a size recalculates the aspect ratio, stating an aspect ratio resets the size. The two can therefore never contradict each other.
 *
 * @author Bauke Scholtz
 * @since 1.7
 * @see GenerateImageOptions
 * @see GenerateVideoOptions
 */
final class Dimensions {

    private static final Pattern SIZE_PATTERN = Pattern.compile("[1-9]\\d*x[1-9]\\d*");
    private static final Pattern ASPECT_RATIO_PATTERN = Pattern.compile("[1-9]\\d*:[1-9]\\d*");
    private static final String SIZE_SEPARATOR = "x";
    private static final String ASPECT_RATIO_SEPARATOR = ":";

    private Dimensions() {
        throw new AssertionError();
    }

    /**
     * Checks that the given size is formatted as {@code {width}x{height}} with both a positive width and a positive height, and returns it.
     *
     * @param size The size to check.
     * @return The given size, guaranteed to be valid.
     * @throws NullPointerException when size is null.
     * @throws IllegalArgumentException when size is invalid.
     */
    static String requireValidSize(String size) {
        requireNonNull(size, "size");

        if (!SIZE_PATTERN.matcher(size).matches()) {
            throw new IllegalArgumentException("Invalid size: " + size);
        }

        return size;
    }

    /**
     * Checks that the given aspect ratio is formatted as {@code {width}:{height}} with both a positive width and a positive height, and returns it.
     *
     * @param aspectRatio The aspect ratio to check.
     * @return The given aspect ratio, guaranteed to be valid.
     * @throws NullPointerException when aspect ratio is null.
     * @throws IllegalArgumentException when aspect ratio is invalid.
     */
    static String requireValidAspectRatio(String aspectRatio) {
        requireNonNull(aspectRatio, "aspectRatio");

        if (!ASPECT_RATIO_PATTERN.matcher(aspectRatio).matches()) {
            throw new IllegalArgumentException("Invalid aspect ratio: " + aspectRatio);
        }

        return aspectRatio;
    }

    /**
     * Calculates the aspect ratio of the given size by reducing its width and height by their greatest common divisor.
     *
     * @param size The size to calculate the aspect ratio of, formatted as {@code {width}x{height}}.
     * @return The aspect ratio, formatted as {@code {width}:{height}}.
     */
    static String calculateAspectRatioBasedOnSize(String size) {
        var parts = size.split(SIZE_SEPARATOR);
        var width = Integer.parseInt(parts[0]);
        var height = Integer.parseInt(parts[1]);
        var greatestCommonDivisor = calculateGreatestCommonDivisor(width, height);
        return (width / greatestCommonDivisor) + ASPECT_RATIO_SEPARATOR + (height / greatestCommonDivisor);
    }

    /**
     * Returns whether the given aspect ratio is portrait, i.e. taller than wide. A square one is neither portrait nor landscape.
     *
     * @param aspectRatio The aspect ratio to check, formatted as {@code {width}:{height}}.
     * @return {@code true} if the height exceeds the width.
     */
    static boolean isPortrait(String aspectRatio) {
        var parts = aspectRatio.split(ASPECT_RATIO_SEPARATOR);
        return Integer.parseInt(parts[1]) > Integer.parseInt(parts[0]);
    }

    /**
     * Returns whether the given aspect ratio is landscape, i.e. wider than tall. A square one is neither portrait nor landscape.
     *
     * @param aspectRatio The aspect ratio to check, formatted as {@code {width}:{height}}.
     * @return {@code true} if the width exceeds the height.
     */
    static boolean isLandscape(String aspectRatio) {
        var parts = aspectRatio.split(ASPECT_RATIO_SEPARATOR);
        return Integer.parseInt(parts[0]) > Integer.parseInt(parts[1]);
    }

    private static int calculateGreatestCommonDivisor(int a, int b) {
        return b == 0 ? a : calculateGreatestCommonDivisor(b, a % b);
    }

}
