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
 * @see AIService#getAIModelVersion()
 */
public record AIModelVersion(String modelName, int majorVersion, int minorVersion) implements Comparable<AIModelVersion>, Serializable {

    /**
     * Validates and normalizes the record components.
     *
     * @param modelName The name of the AI model to match against, may not be empty.
     * @param majorVersion The major version number, may not be negative.
     * @param minorVersion The minor version number, may not be negative.
     * @throws IllegalArgumentException if modelName is blank, or if majorVersion or minorVersion is negative.
     */
    public AIModelVersion {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("Model name may not be blank");
        }
        if (majorVersion < 0) {
            throw new IllegalArgumentException("Major version may not be negative");
        }
        if (minorVersion < 0) {
            throw new IllegalArgumentException("Minor version may not be negative");
        }
        modelName = modelName.strip();
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
        return new AIModelVersion(getModelPrefix(fullModelName), getModelMajorVersion(fullModelName), getModelMinorVersion(fullModelName));
    }

    /**
     * Checks if this version is less than the version of the given AI model version. Returns {@code false} if the model names do not match.
     *
     * @param other The other AI model version to compare against.
     * @return {@code true} if models match and this version is less than the given version.
     */
    public boolean lt(AIModelVersion other) {
        return hasMatchingModel(other) && compareVersionTo(other) < 0;
    }

    /**
     * Checks if this version is less than or equal to the version of the given AI model version. Returns {@code false} if the model names do not match.
     *
     * @param other The other AI model version to compare against.
     * @return {@code true} if models match and this version is less than or equal to the given version.
     */
    public boolean lte(AIModelVersion other) {
        return hasMatchingModel(other) && compareVersionTo(other) <= 0;
    }

    /**
     * Checks if this version is greater than the version of the given AI model version. Returns {@code false} if the model names do not match.
     *
     * @param other The other AI model version to compare against.
     * @return {@code true} if models match and this version is greater than the given version.
     */
    public boolean gt(AIModelVersion other) {
        return hasMatchingModel(other) && compareVersionTo(other) > 0;
    }

    /**
     * Checks if this version is greater than or equal to the version of the given AI model version. Returns {@code false} if the model names do not match.
     *
     * @param other The other AI model version to compare against.
     * @return {@code true} if models match and this version is greater than or equal to the given version.
     */
    public boolean gte(AIModelVersion other) {
        return hasMatchingModel(other) && compareVersionTo(other) >= 0;
    }

    /**
     * Checks if this version is equal to the version of the given AI model version. Returns {@code false} if the model names do not match.
     *
     * @param other The other AI model version to compare against.
     * @return {@code true} if models match and this version equals the given version.
     */
    public boolean eq(AIModelVersion other) {
        return hasMatchingModel(other) && compareVersionTo(other) == 0;
    }

    /**
     * Checks if this version is not equal to the version of the given AI model version. Returns {@code true} if the model names do not match.
     *
     * @param other The other AI model version to compare against.
     * @return {@code true} if models do not match or this version does not equal the given version.
     */
    public boolean ne(AIModelVersion other) {
        return !eq(other);
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
            if (!foundMajor) {
                if (Character.isDigit(c)) {
                    foundMajor = true;
                }
            }
            else if (!foundSeparator) {
                if (!Character.isDigit(c)) {
                    if (!Character.isLetterOrDigit(c)) {
                        foundSeparator = true;
                    }
                    else {
                        break;
                    }
                }
            }
            else {
                if (Character.isDigit(c)) {
                    digits.append(c);
                }
                else {
                    break;
                }
            }
        }

        return digits.length() > 0 ? Integer.parseInt(digits.toString()) : 0;
    }
}
