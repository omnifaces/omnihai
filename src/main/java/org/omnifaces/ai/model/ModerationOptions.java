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
import static java.util.stream.Collectors.toCollection;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Options for AI content moderation.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public class ModerationOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Standard categories for content moderation.
     */
    public enum Category {
        /** Sexual content (OpenAI supported). */
        SEXUAL("sexual", true),
        /** Harassment content (OpenAI supported). */
        HARASSMENT("harassment", true),
        /** Hate speech (OpenAI supported). */
        HATE("hate", true),
        /** Illicit content (OpenAI supported). */
        ILLICIT("illicit", true),
        /** Self-harm content (OpenAI supported). */
        SELF_HARM("self-harm", true),
        /** Violent content (OpenAI supported). */
        VIOLENCE("violence", true),
        /** Personally identifiable information. */
        PII("pii", false),
        /** Spam content. */
        SPAM("spam", false),
        /** Profanity. */
        PROFANITY("profanity", false);

        /** All category names. */
        public static final Set<String> ALL_CATEGORY_NAMES = Set.copyOf(Arrays.stream(values()).map(Category::getName).collect(toCollection(TreeSet::new)));

        /** Category names supported by OpenAI v1 moderation API. */
        public static final Set<String> OPENAI_SUPPORTED_CATEGORY_NAMES = Set.copyOf(Arrays.stream(values()).filter(Category::isOpenAISupported).map(Category::getName).collect(toCollection(TreeSet::new)));

        private final String name;
        private final boolean openAISupported;

        private Category(String name, boolean openAIStandard) {
            this.name = name;
            this.openAISupported = openAIStandard;
        }

        /**
         * Returns the category name.
         * @return The category name.
         */
        public String getName() {
            return name;
        }

        /**
         * Returns whether this category is supported by OpenAI v1 moderation API.
         * @return {@code true} if supported by OpenAI v1 moderation API.
         */
        public boolean isOpenAISupported() {
            return openAISupported;
        }
    }

    /** Default threshold: {@value}. */
    public static final double DEFAULT_THRESHOLD = 0.5;

    /** Strict threshold: {@value}. */
    public static final double STRICT_THRESHOLD = 0.2;

    /** Lenient threshold: {@value}. */
    public static final double LENIENT_THRESHOLD = 0.7;

    /** Default moderation options checking all categories with medium threshold of 0.5. */
    public static final ModerationOptions DEFAULT = ModerationOptions.newBuilder().build();

    /** Strict moderation with lower threshold of {@value #STRICT_THRESHOLD}. */
    public static final ModerationOptions STRICT = ModerationOptions.newBuilder().threshold(STRICT_THRESHOLD).build();

    /** Lenient moderation with higher threshold of {@value #LENIENT_THRESHOLD}. */
    public static final ModerationOptions LENIENT = ModerationOptions.newBuilder().threshold(LENIENT_THRESHOLD).build();

    /** The categories to check. */
    private final Set<String> categories;
    /** The threshold for flagging content. */
    private final double threshold;

    private ModerationOptions(Builder builder) {
        this.categories = Collections.unmodifiableSet(builder.categories);
        this.threshold = builder.threshold;
    }

    /**
     * Gets the categories to check for violations. Defaults to {@link Category#OPENAI_SUPPORTED_CATEGORY_NAMES}.
     *
     * @return Set of category names.
     */
    public Set<String> getCategories() {
        return categories;
    }

    /**
     * Gets the threshold for flagging content (0.0 to 1.0). Defaults to {@value #DEFAULT_THRESHOLD}.
     * <p>
     * Content is flagged when any category score exceeds this threshold.
     * Lower values (e.g., {@value #STRICT_THRESHOLD}) are stricter, higher values (e.g., {@value #LENIENT_THRESHOLD}) are more lenient.
     *
     * @return The threshold value.
     */
    public double getThreshold() {
        return threshold;
    }

    /**
     * Creates a new builder for constructing {@link ModerationOptions} instances. For example:
     * <pre>
     * ModerationOptions options = ModerationOptions.newBuilder()
     *     .categories(Category.HATE, Category.VIOLENCE)
     *     .threshold(0.8)
     *     .build();
     * </pre>
     *
     * @return A new {@code ModerationOptions.Builder} instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for creating {@link ModerationOptions} instances.
     * <p>
     * Use {@link ModerationOptions#newBuilder()} to obtain a new builder instance.
     */
    public static class Builder {
        private Set<String> categories = new TreeSet<>(Category.OPENAI_SUPPORTED_CATEGORY_NAMES);
        private double threshold = DEFAULT_THRESHOLD;

        private Builder() {}

        /**
         * Sets the categories to check. Defaults to {@link Category#OPENAI_SUPPORTED_CATEGORY_NAMES}.
         *
         * @param categories Categories to check.
         * @return This builder instance for chaining.
         */
        public Builder categories(Category... categories) {
            this.categories = new TreeSet<>(Arrays.stream(categories).map(Category::getName).toList());
            return this;
        }

        /**
         * Adds custom categories to check.
         *
         * @param categories Custom categories to check, each category may only contain alphabetic characters or hyphens.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException if a category contains illegal characters.
         */
        public Builder addCategories(String... categories) {
            for (var category : categories) {
                requireNonNull(category, "category");

                if (!category.matches("\\w+")) {
                    throw new IllegalArgumentException(category + " may only contain alphabetic characters or hyphens");
                }

                this.categories.add(category.toLowerCase());
            }

            return this;
        }

        /**
         * Sets the threshold. Defaults to {@value ModerationOptions#DEFAULT_THRESHOLD}.
         * <p>
         * Content is flagged when any category score exceeds this threshold.
         * Lower values (e.g., {@value #STRICT_THRESHOLD}) are stricter, higher values (e.g., {@value #LENIENT_THRESHOLD}) are more lenient.
         *
         * @param threshold The threshold value, typically between 0.0 and 1.0.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException if the value is not within the range [0.0, 1.0].
         */
        public Builder threshold(double threshold) {
            if (threshold < 0.0 || threshold > 1.0) {
                throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0");
            }
            this.threshold = threshold;
            return this;
        }

        /**
         * Finalizes the configuration and creates a {@link ModerationOptions} instance.
         *
         * @return A fully configured {@code ModerationOptions} object.
         */
        public ModerationOptions build() {
            if (categories.isEmpty()) {
                throw new IllegalArgumentException("categories cannot be blank");
            }

            return new ModerationOptions(this);
        }
    }
}
