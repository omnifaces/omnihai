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

import jakarta.json.JsonObject;

/**
 * Options for chat-based AI interactions.
 * <p>
 * This class provides configuration options for AI chat operations, including system prompt, temperature, max tokens, and various sampling parameters.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public class ChatOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Default temperature: {@value}. */
    public static final double DEFAULT_TEMPERATURE = 0.7;

    /** Creative temperature: {@value}. */
    public static final double CREATIVE_TEMPERATURE = 1.2;

    /** Default Top-P: {@value}. */
    public static final double DEFAULT_TOP_P = 1.0;

    /** Default chat options with temperature of {@value #DEFAULT_TEMPERATURE}. */
    public static final ChatOptions DEFAULT = ChatOptions.newBuilder().build();

    /** Creative chat with higher temperature of {@value #CREATIVE_TEMPERATURE}. */
    public static final ChatOptions CREATIVE = ChatOptions.newBuilder().temperature(CREATIVE_TEMPERATURE).build();

    /** Deterministic chat with zero temperature. */
    public static final ChatOptions DETERMINISTIC = ChatOptions.newBuilder().temperature(0.0).build();

    /** The system prompt. */
    private final String systemPrompt;
    /** The sampling temperature. */
    private final double temperature;
    /** The maximum number of tokens. */
    private final Integer maxTokens;
    /** The Top-P value. */
    private final double topP;
    /** The JSON schema for structured output. */
    private final JsonObject jsonSchema;

    private ChatOptions(Builder builder) {
        this.systemPrompt = builder.systemPrompt;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.topP = builder.topP;
        this.jsonSchema = builder.jsonSchema;
    }

    /**
     * Gets the system prompt used to provide high-level instructions to the model.
     * <p>
     * The system prompt establishes the context, persona, operational constraints, and response style before the user message is processed.
     *
     * @return The system prompt string, or {@code null} if no system context is defined.
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * Gets the sampling temperature used for token selection. Defaults to {@value #DEFAULT_TEMPERATURE}.
     * <p>
     * Higher values (e.g., 0.8) make responses more creative and varied.
     * Lower values (e.g., 0.2) make them more focused and predictable.
     * A value of 0 always picks the most likely next word.
     *
     * @return The temperature value, typically in the range [0.0, 2.0].
     */
    public double getTemperature() {
        return temperature;
    }

    /**
     * Gets the maximum number of tokens to generate in the response. Defaults to {@code null}.
     * <p>
     * A token is a model-specific unit of text which varies per input and output language.
     * In English 1000 tokens is roughly 750 words, but in e.g. Spanish it would be roughly 700 words due to more inflection and functions in the language.
     * <p>
     * For classic (fast) chat models (e.g. gpt-4), this setting only limits how long the response can be.
     * For reasoning-enabled chat models (e.g. gpt-5), this limit includes both the visible response and the model's thinking process.
     * If the limit is reached, the response will be cut off mid-sentence.
     * If the thinking process is complex, the response may be shorter.
     * Your input plus this limit must fit within the model's maximum context size.
     *
     * @return The maximum token limit for the completion, or {@code null} to use the AI service's default.
     */
    public Integer getMaxTokens() {
        return maxTokens;
    }

    /**
     * Gets the Nucleus Sampling (Top-P) value. Defaults to {@value #DEFAULT_TOP_P}.
     * <p>
     * Top-P controls how many word choices the model considers.
     * A value of 1.0 considers all possible words, while 0.1 only considers the most likely words (top 10%).
     * Lower values give more focused responses, higher values allow more variety.
     *
     * @return The Top-P probability mass threshold (0.0 to 1.0).
     */
    public double getTopP() {
        return topP;
    }

    /**
     * Gets the JSON schema for structured output. Defaults to {@code null}.
     * <p>
     * When set, the AI model is instructed to return a response that conforms to this JSON schema.
     * This is useful for ensuring the model returns valid, parseable JSON in a specific format.
     * <p>
     * The schema should follow the JSON Schema specification. For example:
     * <pre>
     * {
     *   "name": "response_schema",
     *   "strict": true,
     *   "schema": {
     *     "type": "object",
     *     "properties": {
     *       "field": { "type": "string" }
     *     },
     *     "required": ["field"],
     *     "additionalProperties": false
     *   }
     * }
     * </pre>
     * <p>
     * Note: Not all AI providers support JSON schema enforcement. When unsupported, the schema may be ignored
     * or the provider may fall back to prompt-based guidance.
     *
     * @return The JSON schema object, or {@code null} if no schema is defined.
     */
    public JsonObject getJsonSchema() {
        return jsonSchema;
    }

    /**
     * Creates a new builder for constructing {@link ChatOptions} instances. For example:
     * <pre>
     * ChatOptions options = ChatOptions.newBuilder()
     *     .systemPrompt("You are a helpful software architect.")
     *     .temperature(0.7)
     *     .maxTokens(500)
     *     .build();
     * </pre>
     *
     * @return A new {@code ChatOptions.Builder} instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for creating {@link ChatOptions} instances.
     * <p>
     * Use {@link ChatOptions#newBuilder()} to obtain a new builder instance.
     */
    public static class Builder {
        private String systemPrompt = null;
        private double temperature = ChatOptions.DEFAULT_TEMPERATURE;
        private Integer maxTokens = null;
        private double topP = ChatOptions.DEFAULT_TOP_P;
        private JsonObject jsonSchema = null;

        private Builder() {}

        /**
         * Sets the system prompt used to provide high-level instructions to the model.
         * <p>
         * The system prompt establishes the "developer" context, persona, operational constraints, and response style before the user message is processed.
         *
         * @param systemPrompt The instruction string for the model. Can be {@code null}.
         * @return This builder instance for chaining.
         */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
         * Sets the sampling temperature used for token selection. Defaults to {@value ChatOptions#DEFAULT_TEMPERATURE}.
         * <p>
         * Higher values (e.g., 0.8) make responses more creative and varied.
         * Lower values (e.g., 0.2) make them more focused and predictable.
         * A value of 0 always picks the most likely next word.
         *
         * @param temperature The temperature value, typically between 0.0 and 2.0.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException if the value is not within the range [0.0, 2.0].
         */
        public Builder temperature(double temperature) {
            if (temperature < 0.0 || temperature > 2.0) {
                throw new IllegalArgumentException("Temperature must be between 0.0 and 2.0");
            }

            this.temperature = temperature;
            return this;
        }

        /**
         * Sets the maximum number of tokens to generate in the chat completion. Defaults to {@code null}.
         * <p>
         * A token is a model-specific unit of text which varies per input and output language.
         * In English 1000 tokens is roughly 750 words, but in e.g. Spanish it would be roughly 700 words due to more inflection and functions in the language.
         * <p>
         * For classic (fast) chat models (e.g. gpt-4), this setting only limits how long the response can be.
         * For reasoning-enabled chat models (e.g. gpt-5), this limit includes both the visible response and the model's thinking process.
         * If the limit is reached, the response will be cut off mid-sentence.
         * If the thinking process is complex, the response may be shorter.
         * Your input plus this limit must fit within the model's maximum context size.
         *
         * @param maxTokens The maximum number of tokens to generate. Must be positive, or {@code null} to use the AI service's default.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException if maxTokens is less than 1.
         */
        public Builder maxTokens(Integer maxTokens) {
            if (maxTokens != null && maxTokens < 1) {
                throw new IllegalArgumentException("Max tokens must be positive");
            }

            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Sets the Nucleus Sampling (Top-P) value. Defaults to {@value ChatOptions#DEFAULT_TOP_P}.
         * <p>
         * Top-P controls how many word choices the model considers.
         * A value of 1.0 considers all possible words, while 0.1 only considers the most likely words (top 10%).
         * Lower values give more focused responses, higher values allow more variety.
         * It is generally recommended to alter either this or {@code temperature}, but not both.
         *
         * @param topP The Top-P value between 0.0 and 1.0.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException if the value is not within the range [0.0, 1.0].
         */
        public Builder topP(double topP) {
            if (topP < 0.0 || topP > 1.0) {
                throw new IllegalArgumentException("Top-P must be between 0.0 and 1.0");
            }

            this.topP = topP;
            return this;
        }

        /**
         * Sets the JSON schema for structured output. Defaults to {@code null}.
         * <p>
         * When set, the AI model is instructed to return a response that conforms to this JSON schema.
         * This is useful for ensuring the model returns valid, parseable JSON in a specific format.
         * <p>
         * The schema should follow the JSON Schema specification. For example:
         * <pre>
         * {
         *   "name": "response_schema",
         *   "strict": true,
         *   "schema": {
         *     "type": "object",
         *     "properties": {
         *       "field": { "type": "string" }
         *     },
         *     "required": ["field"],
         *     "additionalProperties": false
         *   }
         * }
         * </pre>
         * <p>
         * Note: Not all AI providers support JSON schema enforcement. When unsupported, the schema may be ignored
         * or the provider may fall back to prompt-based guidance.
         *
         * @param jsonSchema The JSON schema object. Can be {@code null}.
         * @return This builder instance for chaining.
         */
        public Builder jsonSchema(JsonObject jsonSchema) {
            this.jsonSchema = jsonSchema;
            return this;
        }

        /**
         * Finalizes the configuration and creates a {@link ChatOptions} instance.
         *
         * @return A fully configured {@code ChatOptions} object.
         */
        public ChatOptions build() {
            return new ChatOptions(this);
        }
    }
}
