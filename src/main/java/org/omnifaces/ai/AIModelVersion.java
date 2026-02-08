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
package org.omnifaces.ai;

import static java.util.Arrays.stream;
import static org.omnifaces.ai.helper.TextHelper.requireNonBlank;

import java.io.Serializable;

/**
 * A record that holds AI model version information and provides comparison utilities.
 * <p>
 * This record can be used to check version requirements against an {@link AIService}. Comparison methods first verify that the service's model name prefix (the
 * part before the major version number) contains this version's model name, then compare version numbers.
 *
 * @param modelName The name of the AI model to match against, may not be empty.
 * @param majorVersion The major version number, may not be negative.
 * @param minorVersion The minor version number, may not be negative.
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIService#getModelVersion()
 */
public final record AIModelVersion(String modelName, int majorVersion, int minorVersion) implements Comparable<AIModelVersion>, Serializable {

    /**
     * Validates and normalizes the record components.
     *
     * @param modelName The name of the AI model to match against, may not be blank.
     * @param majorVersion The major version number, may not be negative.
     * @param minorVersion The minor version number, may not be negative.
     * @throws IllegalArgumentException if modelName is blank, or if majorVersion or minorVersion is negative.
     */
    public AIModelVersion {
        modelName = requireNonBlank(modelName, "Model name").strip();
        if (majorVersion < 0) {
            throw new IllegalArgumentException("Major version may not be negative");
        }
        if (minorVersion < 0) {
            throw new IllegalArgumentException("Minor version may not be negative");
        }
    }

    /**
     * Creates an AIModelVersion from an AIService.
     *
     * @param service The AI service to extract version information from.
     * @return A new AIModelVersion instance.
     */
    public static AIModelVersion of(AIService service) {
        return of(service.getModelName());
    }

    /**
     * Creates an AIModelVersion with the given model name, major version, and minor version.
     *
     * @param modelName The name of the AI model to match against.
     * @param majorVersion The major version number.
     * @param minorVersion The minor version number.
     * @return A new AIModelVersion instance.
     */
    public static AIModelVersion of(String modelName, int majorVersion, int minorVersion) {
        return new AIModelVersion(modelName, majorVersion, minorVersion);
    }

    /**
     * Creates an AIModelVersion with the given model name and major version. Minor version defaults to {@code 0}.
     *
     * @param modelName The name of the AI model to match against.
     * @param majorVersion The major version number.
     * @return A new AIModelVersion instance.
     */
    public static AIModelVersion of(String modelName, int majorVersion) {
        return new AIModelVersion(modelName, majorVersion, 0);
    }

    /**
     * Creates an AIModelVersion with the given full model name. Major and minor version will be extracted from the model name.
     *
     * @param fullModelName The full name of the AI model to match against.
     * @return A new AIModelVersion instance.
     */
    public static AIModelVersion of(String fullModelName) {
        var normalizedName = stripDateSuffix(fullModelName);
        return new AIModelVersion(getModelPrefix(normalizedName), getModelMajorVersion(normalizedName), getModelMinorVersion(normalizedName));
    }

    /**
     * Checks if this version is less than the version of any of the given AI model versions. Returns {@code false} if none of the model names match.
     *
     * @param others The other AI model versions to compare against.
     * @return {@code true} if this version is less than any of the given versions (with matching model names).
     */
    public boolean lt(AIModelVersion... others) {
        return stream(others).anyMatch(other -> hasMatchingModel(other) && compareVersionTo(other) < 0);
    }

    /**
     * Checks if this version is less than or equal to the version of any of the given AI model versions. Returns {@code false} if none of the model names match.
     *
     * @param others The other AI model versions to compare against.
     * @return {@code true} if this version is less than or equal to any of the given versions (with matching model names).
     */
    public boolean lte(AIModelVersion... others) {
        return stream(others).anyMatch(other -> hasMatchingModel(other) && compareVersionTo(other) <= 0);
    }

    /**
     * Checks if this version is greater than the version of any of the given AI model versions. Returns {@code false} if none of the model names match.
     *
     * @param others The other AI model versions to compare against.
     * @return {@code true} if this version is greater than any of the given versions (with matching model names).
     */
    public boolean gt(AIModelVersion... others) {
        return stream(others).anyMatch(other -> hasMatchingModel(other) && compareVersionTo(other) > 0);
    }

    /**
     * Checks if this version is greater than or equal to the version of any of the given AI model versions. Returns {@code false} if none of the model names match.
     *
     * @param others The other AI model versions to compare against.
     * @return {@code true} if this version is greater than or equal to any of the given versions (with matching model names).
     */
    public boolean gte(AIModelVersion... others) {
        return stream(others).anyMatch(other -> hasMatchingModel(other) && compareVersionTo(other) >= 0);
    }

    /**
     * Checks if this version is equal to the version of any of the given AI model versions. Returns {@code false} if none of the model names match.
     *
     * @param others The other AI model versions to compare against.
     * @return {@code true} if this version equals any of the given versions (with matching model names).
     */
    public boolean eq(AIModelVersion... others) {
        return stream(others).anyMatch(other -> hasMatchingModel(other) && compareVersionTo(other) == 0);
    }

    /**
     * Checks if this version is not equal to any of the given AI model versions. Returns {@code true} if the model names do not match.
     *
     * @param others The other AI model versions to compare against.
     * @return {@code true} if this version does not equal any of the given versions.
     */
    public boolean ne(AIModelVersion... others) {
        return stream(others).noneMatch(other -> hasMatchingModel(other) && compareVersionTo(other) == 0);
    }

    /**
     * Compares this version with the specified version for order.
     * Comparison is performed first by model name (case-insensitive), then by major version, then by minor version.
     *
     * @param other The other AI model version to compare against.
     * @return A negative integer, zero, or a positive integer as this version is less than, equal to, or greater than the specified version.
     */
    @Override
    public int compareTo(AIModelVersion other) {
        int nameCompare = modelName.compareToIgnoreCase(other.modelName);

        if (nameCompare != 0) {
            return nameCompare;
        }

        int majorCompare = Integer.compare(majorVersion, other.majorVersion);

        if (majorCompare != 0) {
            return majorCompare;
        }

        return Integer.compare(minorVersion, other.minorVersion);
    }

    /**
     * Checks if this version's model name matches the other version's model name.
     *
     * @param other The other AI model version to check.
     * @return {@code true} if either model name prefix contains the other.
     */
    private boolean hasMatchingModel(AIModelVersion other) {
        var thisModelName = modelName.toLowerCase();
        var otherModelName = getModelPrefix(other.modelName).toLowerCase();
        return thisModelName.contains(otherModelName) || otherModelName.contains(thisModelName);
    }

    /**
     * Compares version numbers with the given AI model version.
     *
     * @param other The other AI model version to compare against.
     * @return Negative if this version is less, positive if greater, zero if equal.
     */
    private int compareVersionTo(AIModelVersion other) {
        int majorCompare = Integer.compare(majorVersion, other.majorVersion);

        if (majorCompare != 0) {
            return majorCompare;
        }

        return Integer.compare(minorVersion, other.minorVersion);
    }

    /**
     * Strips date-like suffixes and everything after them from the model name to prevent date components from being
     * parsed as version numbers. Handles both {@code YYYY-MM-DD} (e.g., {@code -2025-08-07}) and {@code YYYYMMDD}
     * (e.g., {@code -20250929}) formats.
     *
     * @param fullModelName The full model name.
     * @return The model name with date suffixes and trailing content removed.
     */
    private static String stripDateSuffix(String fullModelName) {
        return fullModelName.split("-\\d{4}-\\d{2}-\\d{2}|-\\d{8}")[0];
    }

    /**
     * Extracts the prefix of a model name before the first digit (major version).
     *
     * @param fullModelName The full model name.
     * @return The prefix before the major version number, excluding trailing non-letter characters (e.g., "anthropic.claude" for "anthropic.claude-3-haiku").
     */
    private static String getModelPrefix(String fullModelName) {
        var lastLetterIndex = -1;

        for (var i = 0; i < fullModelName.length(); i++) {
            var c = fullModelName.charAt(i);

            if (Character.isDigit(c)) {
                break;
            }
            else if (Character.isLetter(c)) {
                lastLetterIndex = i;
            }
        }

        return lastLetterIndex >= 0 ? fullModelName.substring(0, lastLetterIndex + 1) : fullModelName;
    }

    /**
     * Extracts the major version from a model name, or {@code 0} if none is found.
     *
     * @param fullModelName The full model name.
     * @return The major version number, or {@code 0} if none is found (e.g., 4 for "model-4.5").
     */
    private static int getModelMajorVersion(String fullModelName) {
        var digits = new StringBuilder();

        for (var c : fullModelName.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            }
            else if (digits.length() > 0) {
                break;
            }
        }

        return digits.length() > 0 ? Integer.parseInt(digits.toString()) : 0;
    }

    /**
     * Extracts the minor version from a model name, or {@code 0} if none is found.
     *
     * @param fullModelName The full model name.
     * @return The minor version number, or {@code 0} if none is found (e.g., 5 for "model-4.5").
     */
    private static int getModelMinorVersion(String fullModelName) {
        var foundMajor = false;
        var foundSeparator = false;
        var digits = new StringBuilder();

        for (var c : fullModelName.toCharArray()) {
            var isDigit = Character.isDigit(c);

            if (!foundMajor) {
                foundMajor = isDigit;
            }
            else if (!foundSeparator) {
                if (!isDigit) {
                    if (!Character.isLetterOrDigit(c)) {
                        foundSeparator = true;
                    }
                    else {
                        break;
                    }
                }
            }
            else if (isDigit) {
                digits.append(c);
            }
            else {
                break;
            }
        }

        return digits.length() > 0 ? Integer.parseInt(digits.toString()) : 0;
    }
}
