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
package org.omnifaces.ai.helper;

/**
 * Utility class for text operations.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public final class TextHelper {

    private TextHelper() {
        throw new AssertionError();
    }

    /**
     * Returns whether the given text is null or blank.
     *
     * @param text Text to check.
     * @return Whether the given text is null or blank.
     */
    public static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * Strips whitespace from the given text and returns null if the result is empty.
     *
     * @param text Text to strip.
     * @return The stripped text, or null if null was passed in or if the result is empty.
     */
    public static String stripToNull(String text) {
        if (text == null) {
            return null;
        }
        var stripped = text.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * Checks that the given text is not blank and returns it. Throws an {@link IllegalArgumentException} if it is.
     *
     * @param text Text to check.
     * @param varName Variable name to include in the exception message.
     * @return The given text, guaranteed to be non-blank.
     * @throws IllegalArgumentException If the given text is null or blank.
     */
    public static String requireNonBlank(String text, String varName) {
        if (isBlank(text)) {
            throw new IllegalArgumentException(varName + " cannot be blank");
        }
        return text;
    }
}
