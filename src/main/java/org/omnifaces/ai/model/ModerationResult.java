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

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Result of content moderation.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public class ModerationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** A result indicating no violations were found. */
    public static final ModerationResult SAFE = new ModerationResult(false, Collections.emptyMap());

    /** Whether the content was flagged. */
    private final boolean flagged;
    /** The confidence scores by category. */
    private final Map<String, Double> scores;

    /**
     * Creates a moderation result.
     *
     * @param flagged Whether the content was flagged
     * @param scores Map of confidence scores (0.0 to 1.0) by category.
     */
    public ModerationResult(boolean flagged, Map<String, Double> scores) {
        this.flagged = flagged;
        this.scores = Collections.unmodifiableMap(new TreeMap<>(scores));
    }

    /**
     * Checks if the content was flagged for any violation.
     *
     * @return true if flagged, false otherwise
     */
    public boolean isFlagged() {
        return flagged;
    }

    /**
     * Gets the confidence scores for each category.
     * <p>
     * Scores range from 0.0 (definitely not a violation) to 1.0 (definitely a violation).
     *
     * @return Map of scores by category
     */
    public Map<String, Double> getScores() {
        return scores;
    }

    /**
     * Gets the score for a specific category.
     *
     * @param category The category
     * @return The score, or 0.0 if category not checked
     */
    public double getScore(String category) {
        return scores.getOrDefault(category, 0.0);
    }

    /**
     * Checks if a specific category was flagged.
     *
     * @param category The category
     * @param threshold The threshold to use (0.0 to 1.0)
     * @return true if the category score exceeds the threshold
     */
    public boolean isFlagged(String category, double threshold) {
        return getScore(category) > threshold;
    }

    /**
     * Gets the categories that were flagged.
     *
     * @param threshold The threshold to use
     * @return Map of flagged categories and their scores
     */
    public Map<String, Double> getFlaggedCategories(double threshold) {
        return scores.entrySet().stream()
            .filter(e -> e.getValue() > threshold)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Gets the highest scoring category.
     *
     * @return The category with the highest score, or null if no scores
     */
    public String getHighestCategory() {
        return scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    /**
     * Gets the highest score across all categories.
     *
     * @return The highest score, or 0.0 if no scores
     */
    public double getHighestScore() {
        return scores.values().stream()
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0.0);
    }

    @Override
    public String toString() {
        return "ModerationResult{flagged=" + flagged + ", scores=" + scores + "}";
    }
}
