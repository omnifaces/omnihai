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

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.omnifaces.ai.exception.AIBudgetExceededException;
import org.omnifaces.ai.helper.JsonSchemaHelper;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.ChatInput.Message;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatInput.UploadedFile;

/**
 * Options for chat-based AI interactions.
 * <p>
 * This class provides configuration options for AI chat operations, including system prompt, JSON schema for structured output, temperature, max tokens, and
 * various sampling parameters.
 * <p>
 * <strong>Threading:</strong> a plain instance carries only configuration and may be freely shared, except that it shares its {@link #getLastUsage() usage} and
 * {@link #getTotalCost() cost} accounting with every instance derived from it via a {@code withXxx} method, as those model one and the same conversation.
 * Recording is atomic, but a budget cap therefore bounds the conversation as a whole; give each conversation its own instance via {@link #copy()} when you want
 * separate bills. A {@link #hasMemory() memory-enabled} instance additionally carries mutable conversation state, and models exactly one sequential
 * conversation: it must not be shared across concurrent chat calls. The conversation history is neither synchronized nor able to tell one caller's turn from
 * another's, so concurrent use corrupts it. Give each conversation its own instance.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see org.omnifaces.ai.AIService#chat(String, ChatOptions)
 * @see org.omnifaces.ai.AIService#chat(String, Class)
 * @see ChatInput
 */
public class ChatOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Default temperature: {@value}. */
    public static final double DEFAULT_TEMPERATURE = 0.7;

    /** Creative temperature: {@value}. */
    public static final double CREATIVE_TEMPERATURE = 1.2;

    /** Deterministic temperature: {@value}. */
    public static final double DETERMINISTIC_TEMPERATURE = 0.0;

    /** Default Top-P: {@value}. */
    public static final double DEFAULT_TOP_P = 1.0;

    /** Default maximum number of messages (both sent and received) retained in conversation history: {@value}. */
    public static final int DEFAULT_MAX_HISTORY = 20;

    /** Default reasoning effort: {@link ReasoningEffort#AUTO}. */
    public static final ReasoningEffort DEFAULT_REASONING_EFFORT = ReasoningEffort.AUTO;

    /** Default chat options with temperature of {@value #DEFAULT_TEMPERATURE}. */
    public static final ChatOptions DEFAULT = ChatOptions.newBuilder().build();

    /** Creative chat with higher temperature of {@value #CREATIVE_TEMPERATURE}. */
    public static final ChatOptions CREATIVE = ChatOptions.newBuilder().temperature(CREATIVE_TEMPERATURE).build();

    /** Deterministic chat with zero temperature. */
    public static final ChatOptions DETERMINISTIC = ChatOptions.newBuilder().temperature(DETERMINISTIC_TEMPERATURE).build();

    // --- JSON keys, shared by toJson() and fromJson() so that a rename cannot break the round trip ---

    private static final String FPS_KEY = "fps";
    private static final String START_OFFSET_KEY = "startOffset";
    private static final String END_OFFSET_KEY = "endOffset";
    private static final String VIDEO_OPTIONS_KEY = "videoOptions";
    private static final String SYSTEM_PROMPT_KEY = "systemPrompt";
    private static final String JSON_SCHEMA_KEY = "jsonSchema";
    private static final String TEMPERATURE_KEY = "temperature";
    private static final String MAX_TOKENS_KEY = "maxTokens";
    private static final String REASONING_EFFORT_KEY = "reasoningEffort";
    private static final String TOP_P_KEY = "topP";
    private static final String WEB_SEARCH_LOCATION_KEY = "webSearchLocation";
    private static final String CACHED_INPUT_TOKEN_PRICE_KEY = "cachedInputTokenPrice";
    private static final String PRICING_KEY = "pricing";
    private static final String MAX_TOTAL_COST_KEY = "maxTotalCost";
    private static final String MAX_HISTORY_KEY = "maxHistory";
    private static final String HISTORY_KEY = "history";
    private static final String UPLOADED_FILES_KEY = "uploadedFiles";
    private static final String COUNTRY_KEY = "country";
    private static final String REGION_KEY = "region";
    private static final String CITY_KEY = "city";
    private static final String INPUT_TOKEN_PRICE_KEY = "inputTokenPrice";
    private static final String OUTPUT_TOKEN_PRICE_KEY = "outputTokenPrice";
    private static final String CURRENCY_KEY = "currency";
    private static final String ROLE_KEY = "role";
    private static final String CONTENT_KEY = "content";
    private static final String ID_KEY = "id";
    private static final String MIME_TYPE_KEY = "mimeType";

    static {
        DEFAULT.immutable = true;
        CREATIVE.immutable = true;
        DETERMINISTIC.immutable = true;
    }

    /**
     * Controls how much internal reasoning (a.k.a. "thinking" or "extended thought") the AI model performs before producing its visible answer, on providers
     * and models that expose this knob.
     * <p>
     * Higher levels typically improve answer quality on hard problems (math, multi-step planning, code) at the cost of more tokens and latency. Lower levels
     * are cheaper and faster but may skip steps on harder problems.
     *
     * @since 1.4
     * @see Builder#reasoningEffort(ReasoningEffort)
     * @see ChatOptions#getReasoningEffort()
     */
    public enum ReasoningEffort {

        /**
         * Do not send any reasoning effort setting; each provider applies its own built-in default. This is the default value for
         * {@link ChatOptions#getReasoningEffort()}.
         */
        AUTO,

        /** Actively disable reasoning where the provider supports it, for minimum cost and latency. */
        NONE,

        /** Allocates a small portion of tokens. */
        LOW,

        /** Allocates a moderate portion of tokens. */
        MEDIUM,

        /** Allocates a large portion of tokens for reasoning. */
        HIGH,

        /** Allocates the largest portion of tokens for reasoning. */
        XHIGH;

    }

    /**
     * Represents a geographical location context for AI operations, such as localized web searching.
     * <p>
     * An instance with all properties set to {@code null} is equivalent to {@link #GLOBAL}, representing a location context without geographical restrictions.
     *
     * @param country The country, usually represented by two-letter ISO country code, e.g. "US", "NL", "CW", etc.
     * @param region The administrative region, such as a state, province, or territory.
     * @param city The city or town or village.
     * @since 1.3
     * @see Builder#webSearch(Location)
     */
    public final record Location(String country, String region, String city) implements Serializable {

        /** Indicates that no specific geographical location is applied. */
        public static final Location GLOBAL = new Location(null, null, null);

        /**
         * Checks if this location represents a global context (i.e., all fields are null).
         *
         * @return {@code true} if this instance is global; {@code false} otherwise.
         */
        public boolean isGlobal() {
            return country == null && region == null && city == null;
        }

        /**
         * Returns a human-readable representation of this location, e.g. {@code "Miami, Florida, US"}. Null fields are omitted. Returns {@code "global"} for
         * {@link #GLOBAL}.
         */
        @Override
        public String toString() {
            return isGlobal() ? "global" : Stream.of(city, region, country).filter(Objects::nonNull).collect(joining(", "));
        }

    }

    /** The system prompt. */
    private final String systemPrompt;
    /** The JSON schema for structured output. */
    private transient JsonObject jsonSchema;
    /** The sampling temperature. */
    private final double temperature;
    /** The maximum number of tokens. */
    private final Integer maxTokens;
    /** The reasoning effort. */
    private final ReasoningEffort reasoningEffort;
    /** The Top-P value. */
    private final double topP;
    /** The web search location. */
    private final Location webSearchLocation;
    /** The pricing used to calculate {@link #getLastCost() cost} from recorded {@link ChatUsage}. */
    private final ChatPricing pricing;
    /** The cumulative-cost cap enforced by {@link #checkBudget()}. */
    private final BigDecimal maxTotalCost;
    /** The conversation history for memory-enabled chat sessions. */
    private final List<Message> history;
    /** The maximum number of messages retained in the conversation history. */
    private final int maxHistory;
    /** The token usage and cumulative cost, shared with the instances derived from this one via a {@code withXxx} method. */
    private transient volatile Accounting accounting = new Accounting();
    /** Whether this instance is a shared default constant and therefore immutable. */
    private boolean immutable;

    /**
     * The runtime accounting of a conversation: the usage of its most recent call and the cumulative cost of all of them.
     * <p>
     * A {@code withXxx} method derives a new {@code ChatOptions} for the same conversation, which therefore keeps one bill rather than starting a new one.
     */
    private static final class Accounting {

        private ChatUsage lastUsage;
        private BigDecimal totalCost = BigDecimal.ZERO;

        private synchronized void recordUsage(ChatUsage usage, ChatPricing pricing) {
            lastUsage = usage;

            if (pricing != null && usage != null) {
                var cost = usage.calculateCost(pricing);

                if (cost != null) {
                    totalCost = totalCost.add(cost.totalCost());
                }
            }
        }

        private synchronized ChatUsage getLastUsage() {
            return lastUsage;
        }

        private synchronized BigDecimal getTotalCost() {
            return totalCost;
        }

        private synchronized void reset() {
            totalCost = BigDecimal.ZERO;
        }

    }

    /**
     * Returns the video analysis options of the given uploaded file as JSON, or empty when there are none to state, so that a file uploaded with a sampling
     * rate or a clip is replayed with them after a round trip.
     *
     * @param videoOptions The video analysis options, which may be {@code null}.
     * @return The video analysis options as JSON, or empty.
     */
    private static Optional<JsonObjectBuilder> toJson(AnalyzeVideoOptions videoOptions) {
        if (videoOptions == null || videoOptions.isDefault()) {
            return Optional.empty();
        }

        var builder = Json.createObjectBuilder();

        if (videoOptions.getFps() != AnalyzeVideoOptions.DEFAULT_FPS) {
            builder.add(FPS_KEY, videoOptions.getFps());
        }

        if (videoOptions.getStartOffset() != null) {
            builder.add(START_OFFSET_KEY, videoOptions.getStartOffset().toMillis());
        }

        if (videoOptions.getEndOffset() != null) {
            builder.add(END_OFFSET_KEY, videoOptions.getEndOffset().toMillis());
        }

        return Optional.of(builder);
    }

    /**
     * Returns the video analysis options which the given uploaded file JSON states, or {@code null} when it states none.
     *
     * @param uploadedFileJson The uploaded file JSON.
     * @return The video analysis options, or {@code null}.
     */
    private static AnalyzeVideoOptions toVideoOptions(JsonObject uploadedFileJson) {
        if (!uploadedFileJson.containsKey(VIDEO_OPTIONS_KEY)) {
            return null;
        }

        var json = uploadedFileJson.getJsonObject(VIDEO_OPTIONS_KEY);
        var builder = AnalyzeVideoOptions.newBuilder();

        if (json.containsKey(FPS_KEY)) {
            builder.fps(json.getJsonNumber(FPS_KEY).doubleValue());
        }

        if (json.containsKey(START_OFFSET_KEY)) {
            builder.startOffset(Duration.ofMillis(json.getJsonNumber(START_OFFSET_KEY).longValue()));
        }

        if (json.containsKey(END_OFFSET_KEY)) {
            builder.endOffset(Duration.ofMillis(json.getJsonNumber(END_OFFSET_KEY).longValue()));
        }

        return builder.build();
    }

    /**
     * Returns the accounting to hand to an instance derived from this one. A shared default constant hands out a fresh one, as every instance derived from it
     * is a conversation of its own.
     *
     * @return The accounting to hand to an instance derived from this one.
     */
    private Accounting sharedAccounting() {
        return isDefault() ? new Accounting() : accounting;
    }

    private ChatOptions(Builder builder) {
        this.systemPrompt = builder.systemPrompt;
        this.jsonSchema = builder.jsonSchema;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.reasoningEffort = builder.reasoningEffort;
        this.topP = builder.topP;
        this.webSearchLocation = builder.webSearchLocation;
        this.pricing = builder.pricing;
        this.maxTotalCost = builder.maxTotalCost;

        var memoryEnabled = builder.maxHistory > 0 || builder.history != null;
        this.maxHistory = resolveMaxHistory(builder.maxHistory, memoryEnabled);
        this.history = memoryEnabled ? new ArrayList<>() : null;

        if (memoryEnabled && builder.history != null) {
            history.addAll(builder.history);

            while (history.size() > maxHistory) {
                history.remove(0);
            }
        }
    }

    /**
     * Answers the sliding window size: the one built with, else the default when memory is enabled by a prebuilt history alone, else no window at all.
     */
    private static int resolveMaxHistory(int maxHistory, boolean memoryEnabled) {
        if (maxHistory > 0) {
            return maxHistory;
        }

        return memoryEnabled ? DEFAULT_MAX_HISTORY : 0;
    }

    private ChatOptions(
        String systemPrompt, JsonObject jsonSchema, double temperature, Integer maxTokens, ReasoningEffort reasoningEffort, double topP,
        Location webSearchLocation, ChatPricing pricing, BigDecimal maxTotalCost, List<Message> history, int maxHistory, Accounting accounting
    )
    {
        this.systemPrompt = systemPrompt;
        this.jsonSchema = jsonSchema;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.reasoningEffort = reasoningEffort;
        this.topP = topP;
        this.webSearchLocation = webSearchLocation;
        this.pricing = pricing;
        this.maxTotalCost = maxTotalCost;
        this.history = history;
        this.maxHistory = maxHistory;
        this.accounting = accounting;
    }

    private ChatOptions(ChatOptions source) {
        this.systemPrompt = source.systemPrompt;
        this.jsonSchema = source.jsonSchema;
        this.temperature = source.temperature;
        this.maxTokens = source.maxTokens;
        this.reasoningEffort = source.reasoningEffort;
        this.topP = source.topP;
        this.webSearchLocation = source.webSearchLocation;
        this.pricing = source.pricing;
        this.maxTotalCost = source.maxTotalCost;
        this.history = source.history;
        this.maxHistory = source.maxHistory;
    }

    /**
     * Custom serialization to handle non-serializable {@link JsonObject}.
     *
     * @param output The object output stream.
     * @throws IOException If an I/O error occurs.
     */
    private void writeObject(ObjectOutputStream output) throws IOException {
        output.defaultWriteObject();
        output.writeObject(jsonSchema != null ? jsonSchema.toString() : null);
    }

    /**
     * Custom deserialization to restore {@link JsonObject} from its string representation.
     *
     * @param input The object input stream.
     * @throws IOException If an I/O error occurs.
     * @throws ClassNotFoundException If the class of a serialized object cannot be found.
     */
    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        var jsonSchemaString = (String) input.readObject();
        if (jsonSchemaString != null) {
            jsonSchema = parseJson(jsonSchemaString);
        }
        this.accounting = new Accounting();
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
     * Gets the JSON schema for structured output. Defaults to {@code null}.
     * <p>
     * For most use cases, prefer the typed chat overloads {@link org.omnifaces.ai.AIService#chat(String, Class)} which handle schema generation and response
     * parsing automatically. Use this property directly only when you need manual control over the schema.
     * <p>
     * You can use {@link JsonSchemaHelper#buildJsonSchema(Class)} to create one for your record or bean class.
     * <p>
     * When set, the AI model is instructed to return a response that conforms to this JSON schema. This is useful for ensuring the model returns valid,
     * parseable JSON in a specific format.
     * <p>
     * The schema should follow the JSON Schema specification. For example:
     *
     * <pre>
     * {
     *   "type": "object",
     *   "properties": {
     *     "name": { "type": "string" },
     *     "age": { "type": "number" }
     *   },
     *   "required": ["name", "age"]
     * }
     * </pre>
     * <p>
     * You can use {@link JsonSchemaHelper#fromJson(String, Class)} to parse the response into your record or bean class.
     * <p>
     * Note: Not all AI providers support JSON schema enforcement. When unsupported, the AI service implementation may throw
     * {@link UnsupportedOperationException} during chat payload construction.
     *
     * @return The JSON schema object, or {@code null} if no schema is defined.
     */
    public JsonObject getJsonSchema() {
        return jsonSchema;
    }

    /**
     * Returns a copy of this instance with the given JSON schema set, preserving all other options including any shared {@link #hasMemory() memory} state and
     * the usage and cost accounting.
     *
     * @param jsonSchema The JSON schema to use for structured output.
     * @return A new {@code ChatOptions} instance with the specified JSON schema.
     */
    public ChatOptions withJsonSchema(JsonObject jsonSchema) {
        return new ChatOptions(
            systemPrompt, jsonSchema, temperature, maxTokens, reasoningEffort, topP, webSearchLocation, pricing, maxTotalCost, history, maxHistory,
            sharedAccounting()
        );
    }

    /**
     * Returns a copy of this instance with the given system prompt set, preserving all other options including any shared {@link #hasMemory() memory} state and
     * the usage and cost accounting.
     *
     * @param systemPrompt The system prompt to use for providing high-level instructions to the model.
     * @return A new {@code ChatOptions} instance with the specified system prompt.
     * @since 1.1
     */
    public ChatOptions withSystemPrompt(String systemPrompt) {
        return new ChatOptions(
            systemPrompt, jsonSchema, temperature, maxTokens, reasoningEffort, topP, webSearchLocation, pricing, maxTotalCost, history, maxHistory,
            sharedAccounting()
        );
    }

    /**
     * Returns a copy of this instance with web search enabled for the given location, preserving all other options including any shared {@link #hasMemory()
     * memory} state and the usage and cost accounting.
     * <p>
     * Pass {@link Location#GLOBAL} to enable web search without restricting it to a specific region. Pass {@code null} to disable web search.
     *
     * @param location The location context for web search, or {@link Location#GLOBAL} for global search, or {@code null} to disable web search.
     * @return A new {@code ChatOptions} instance with the specified web search location enabled.
     * @since 1.3
     * @see #useWebSearch()
     * @see #getWebSearchLocation()
     */
    public ChatOptions withWebSearch(Location location) {
        return new ChatOptions(
            systemPrompt, jsonSchema, temperature, maxTokens, reasoningEffort, topP, location, pricing, maxTotalCost, history, maxHistory, sharedAccounting()
        );
    }

    /**
     * Returns a copy of this instance with the given reasoning effort set, preserving all other options including any shared {@link #hasMemory() memory} state
     * and the usage and cost accounting.
     *
     * @param reasoningEffort The reasoning effort to use. Must not be {@code null}; use {@link ReasoningEffort#AUTO} to defer to the provider default.
     * @return A new {@code ChatOptions} instance with the specified reasoning effort.
     * @throws NullPointerException if {@code reasoningEffort} is {@code null}.
     * @since 1.4
     * @see ReasoningEffort
     */
    public ChatOptions withReasoningEffort(ReasoningEffort reasoningEffort) {
        return new ChatOptions(
            systemPrompt, jsonSchema, temperature, maxTokens, requireNonNull(reasoningEffort, "reasoningEffort"), topP, webSearchLocation, pricing,
            maxTotalCost, history, maxHistory, sharedAccounting()
        );
    }

    /**
     * Returns a copy of this instance with the given pricing set, preserving all other options including any shared {@link #hasMemory() memory} state and the
     * usage and cost accounting. Any previously configured {@link #getMaxTotalCost() budget cap} is cleared; use {@link #withPricing(ChatPricing, BigDecimal)}
     * to set both at once.
     *
     * @param pricing The pricing configuration to use for cost calculations, or {@code null} to clear pricing.
     * @return A new {@code ChatOptions} instance with the specified pricing.
     * @since 1.4
     * @see ChatPricing
     * @see #getLastCost()
     */
    public ChatOptions withPricing(ChatPricing pricing) {
        return new ChatOptions(
            systemPrompt, jsonSchema, temperature, maxTokens, reasoningEffort, topP, webSearchLocation, pricing, null, history, maxHistory, sharedAccounting()
        );
    }

    /**
     * Returns a copy of this instance with the given pricing and cumulative-cost cap set, preserving all other options including any shared {@link #hasMemory()
     * memory} state. Unlike the other {@code withXxx} methods it starts a fresh accounting, so both the {@link #getTotalCost() total cost} counter and the
     * {@link #getLastUsage() last usage} begin empty, as a new cap opens a new budget window.
     *
     * @param pricing The pricing configuration. Must not be {@code null}.
     * @param maxTotalCost The cumulative-cost cap. Must not be {@code null} and must be strictly positive.
     * @return A new {@code ChatOptions} instance with the specified pricing and cap.
     * @throws NullPointerException if {@code pricing} or {@code maxTotalCost} is {@code null}.
     * @throws IllegalArgumentException if {@code maxTotalCost} is not strictly positive.
     * @since 1.4
     * @see AIBudgetExceededException
     * @see #getMaxTotalCost()
     * @see #getTotalCost()
     * @see #resetBudget()
     */
    public ChatOptions withPricing(ChatPricing pricing, BigDecimal maxTotalCost) {
        requireNonNull(pricing, "pricing");
        requireValidMaxTotalCost(maxTotalCost);

        return new ChatOptions(
            systemPrompt, jsonSchema, temperature, maxTokens, reasoningEffort, topP, webSearchLocation, pricing, maxTotalCost, history, maxHistory,
            new Accounting()
        );
    }

    private static BigDecimal requireValidMaxTotalCost(BigDecimal maxTotalCost) {
        if (requireNonNull(maxTotalCost, "maxTotalCost").signum() <= 0) {
            throw new IllegalArgumentException("Max total cost must be strictly positive");
        }

        return maxTotalCost;
    }

    /**
     * Returns a mutable copy of this instance, preserving all options and any shared {@link #hasMemory() memory} state, but starting with no
     * {@link #getLastUsage() last usage} recorded and a zero {@link #getTotalCost() total cost}. Any {@link #getMaxTotalCost() budget cap} is preserved, so a
     * copy starts a fresh budget window under the same cap.
     * <p>
     * This is the recommended way to obtain a dedicated, mutable instance from one of the shared constants ({@link #DEFAULT}, {@link #CREATIVE},
     * {@link #DETERMINISTIC}) when you want to track token usage:
     *
     * <pre>
     * ChatOptions options = ChatOptions.DEFAULT.copy();
     * service.chat("Hello", options);
     * ChatUsage usage = options.getLastUsage();
     * </pre>
     *
     * @return A new mutable {@code ChatOptions} instance with the same settings.
     * @since 1.3
     */
    public ChatOptions copy() {
        return new ChatOptions(this);
    }

    /**
     * Gets the sampling temperature used for token selection. Defaults to {@value #DEFAULT_TEMPERATURE}.
     * <p>
     * Higher values (e.g., 0.8) make responses more creative and varied. Lower values (e.g., 0.2) make them more focused and predictable. A value of 0 always
     * picks the most likely next word.
     *
     * @return The temperature value, typically in the range [0.0, 2.0].
     */
    public double getTemperature() {
        return temperature;
    }

    /**
     * Gets the maximum number of tokens to generate in the response. Defaults to {@code null}.
     * <p>
     * A token is a model-specific unit of text which varies per input and output language. In English 1000 tokens is roughly 750 words, but in e.g. Spanish it
     * would be roughly 700 words due to more inflection and functions in the language.
     * <p>
     * For classic (fast) chat models (e.g. gpt-4), this setting only limits how long the response can be. For reasoning-enabled chat models (e.g. gpt-5), this
     * limit includes both the visible response and the model's thinking process. If the limit is reached, the response will be cut off mid-sentence. If the
     * thinking process is complex, the response may be shorter. Your input plus this limit must fit within the model's maximum context size.
     *
     * @return The maximum token limit for the completion, or {@code null} to use the AI service's default.
     */
    public Integer getMaxTokens() {
        return maxTokens;
    }

    /**
     * Gets the reasoning effort for models that support extended thinking. Defaults to {@link ReasoningEffort#AUTO}.
     * <p>
     * Since {@code maxTokens} on reasoning-capable models includes the model's thinking tokens in addition to the visible response, picking a higher reasoning
     * effort may require a correspondingly higher {@code maxTokens} to avoid truncated responses. See {@link ReasoningEffort} for per-provider details.
     *
     * @return The configured reasoning effort; never {@code null}.
     * @since 1.4
     * @see ReasoningEffort
     */
    public ReasoningEffort getReasoningEffort() {
        return reasoningEffort;
    }

    /**
     * Gets the Nucleus Sampling (Top-P) value. Defaults to {@value #DEFAULT_TOP_P}.
     * <p>
     * Top-P controls how many word choices the model considers. A value of 1.0 considers all possible words, while 0.1 only considers the most likely words
     * (top 10%). Lower values give more focused responses, higher values allow more variety.
     * <ul>
     * <li><strong>Low Temp + Low Top-P:</strong> Corporate email simulator (The Deterministic Robot)</li>
     * <li><strong>Low Temp + High Top-P:</strong> Boring but stable (The Safe Bet)</li>
     * <li><strong>Medium Temp + Low Top-P:</strong> Focused / Professional (The Focused Expert)</li>
     * <li><strong>Medium Temp + High Top-P:</strong> Natural / Casual (The Conversationalist)</li>
     * <li><strong>High Temp + Low Top-P:</strong> Creative but coherent (The Controlled Artist)</li>
     * <li><strong>High Temp + High Top-P:</strong> Word salads / Hallucination station</li>
     * </ul>
     *
     * @return The Top-P probability mass threshold (0.0 to 1.0).
     */
    public double getTopP() {
        return topP;
    }

    /**
     * Returns whether web search is enabled for this instance.
     * <p>
     * When {@code true}, the AI service will allow the model to access up-to-date information from the internet and provide answers with sourced citations.
     *
     * @return {@code true} if web search is enabled, {@code false} otherwise.
     * @since 1.3
     * @see #getWebSearchLocation()
     */
    public boolean useWebSearch() {
        return webSearchLocation != null;
    }

    /**
     * Returns web search location.
     *
     * @return The configured {@link Location} for web searches, or {@code null} if there is none.
     * @since 1.3
     * @see #useWebSearch()
     */
    public Location getWebSearchLocation() {
        return webSearchLocation;
    }

    /**
     * Returns the pricing configuration used to calculate cost from recorded token usage, or {@code null} if none is configured.
     *
     * @return The configured {@link ChatPricing}, or {@code null}.
     * @since 1.4
     * @see Builder#pricing(ChatPricing)
     * @see #withPricing(ChatPricing)
     * @see #getLastCost()
     */
    public ChatPricing getPricing() {
        return pricing;
    }

    /**
     * Returns the cumulative-cost cap configured for this instance via {@link Builder#pricing(ChatPricing, BigDecimal)} or
     * {@link #withPricing(ChatPricing, BigDecimal)}, or {@code null} if no cap is configured.
     *
     * @return The cap, or {@code null}.
     * @since 1.4
     * @see #getTotalCost()
     * @see AIBudgetExceededException
     */
    public BigDecimal getMaxTotalCost() {
        return maxTotalCost;
    }

    /**
     * Returns the cumulative cost across all chat calls made with this instance, starting from {@link BigDecimal#ZERO} and growing by
     * {@link ChatCost#totalCost()} on each call that reports usage with a configured {@link #getPricing() pricing}.
     *
     * @return The cumulative cost; never {@code null}.
     * @since 1.4
     * @see #getMaxTotalCost()
     * @see #resetBudget()
     * @see AIBudgetExceededException
     */
    public BigDecimal getTotalCost() {
        return accounting.getTotalCost();
    }

    /**
     * Resets the cumulative {@link #getTotalCost() total cost} counter back to {@link BigDecimal#ZERO}. The configured {@link #getMaxTotalCost() cap} is left
     * in place. Useful to start a fresh budgeting window on the same instance after catching an {@link AIBudgetExceededException}.
     *
     * @throws IllegalStateException if this is a {@link #isDefault() default} instance.
     * @since 1.4
     * @see #getTotalCost()
     * @see #getMaxTotalCost()
     */
    public void resetBudget() {
        if (isDefault()) {
            throw new IllegalStateException(
                "Cannot reset budget on a default (shared) ChatOptions instance; use copy() or a withXxx() method to create a dedicated instance"
            );
        }

        accounting.reset();
    }

    /**
     * Verifies the cumulative {@link #getTotalCost() total cost} has not yet reached the configured {@link #getMaxTotalCost() cap}. Called by the AI service
     * before dispatching a chat call. No-op when no cap is configured.
     *
     * @throws AIBudgetExceededException if {@code getTotalCost() >= getMaxTotalCost()}.
     * @since 1.4
     */
    public void checkBudget() {
        var totalCost = accounting.getTotalCost();

        if (maxTotalCost != null && totalCost.compareTo(maxTotalCost) >= 0) {
            throw new AIBudgetExceededException(totalCost, maxTotalCost, pricing != null ? pricing.currency() : null);
        }
    }

    /**
     * Returns whether conversation memory is enabled for this instance.
     * <p>
     * When {@code true}, the AI service will automatically track all user messages and assistant responses made with this {@code ChatOptions} instance, and
     * include them as conversation history in subsequent requests. The history is kept within a sliding window of {@link #getMaxHistory()} messages, counting
     * both sent and received messages (default {@value #DEFAULT_MAX_HISTORY}, i.e. 10 conversational turns).
     *
     * @return {@code true} if conversation history is maintained, {@code false} otherwise.
     */
    public boolean hasMemory() {
        return history != null;
    }

    /**
     * Gets the maximum number of messages retained in the conversation history for this memory-enabled instance.
     * <p>
     * This counts both sent (user) and received (assistant) messages. For example, the default of {@value #DEFAULT_MAX_HISTORY} retains up to 10 conversational
     * turns.
     * <p>
     * When conversation memory is enabled, the history acts as a sliding window: once the number of recorded messages exceeds this limit, the oldest messages
     * are automatically discarded.
     *
     * @return The maximum number of messages retained.
     * @throws IllegalStateException if this instance is not {@link #hasMemory() memory-enabled}.
     * @since 1.1
     */
    public int getMaxHistory() {
        if (!hasMemory()) {
            throw new IllegalStateException("Cannot get max history from non-memory ChatOptions; use withMemory() method to create a memory-enabled instance");
        }

        return maxHistory;
    }

    /**
     * Returns the conversation history for this memory-enabled instance.
     *
     * @return An unmodifiable list of prior messages.
     * @throws IllegalStateException if this instance is not {@link #hasMemory() memory-enabled}.
     */
    public List<Message> getHistory() {
        if (!hasMemory()) {
            throw new IllegalStateException(
                "Cannot get message history from non-memory ChatOptions; use withMemory() method to create a memory-enabled instance"
            );
        }

        return unmodifiableList(history);
    }

    /**
     * Returns the token usage recorded for the most recent chat call made with this instance, or {@code null} if no call has been made yet or if the provider
     * does not report usage.
     *
     * @return The last recorded {@link ChatUsage}, or {@code null}.
     * @throws IllegalStateException if this is a {@link #isDefault() default} instance.
     * @since 1.3
     */
    public ChatUsage getLastUsage() {
        if (isDefault()) {
            throw new IllegalStateException(
                "Cannot get last usage from a default (shared) ChatOptions instance; use copy() or a withXxx() method to create a dedicated instance"
            );
        }

        return accounting.getLastUsage();
    }

    /**
     * Returns the computed cost of the most recent chat call made with this instance, or {@code null} if no {@link #getPricing() pricing} has been configured,
     * no call has been made yet, or the provider did not report the input/output token counts needed to compute it.
     * <p>
     * Equivalent to {@code getLastUsage().calculateCost(getPricing())} with {@code null}-guards on both sides.
     *
     * @return The last computed {@link ChatCost}, or {@code null}.
     * @throws IllegalStateException if this is a {@link #isDefault() default} instance.
     * @since 1.4
     * @see #getPricing()
     * @see #getLastUsage()
     * @see ChatUsage#calculateCost(ChatPricing)
     */
    public ChatCost getLastCost() {
        var usage = getLastUsage();

        if (usage == null || pricing == null) {
            return null;
        }

        return usage.calculateCost(pricing);
    }

    /**
     * Returns whether this instance is one of the shared default constants ({@link #DEFAULT}, {@link #CREATIVE}, {@link #DETERMINISTIC}) and therefore
     * immutable. Calling {@link #getLastUsage()} or any {@code recordXxx} method on a default instance throws {@link IllegalStateException}.
     * <p>
     * Use {@link #copy()} to obtain a mutable copy with the same settings, or {@link #newBuilder()} to build a new instance from scratch.
     *
     * @return {@code true} if this is a shared default instance, {@code false} otherwise.
     * @since 1.3
     */
    public boolean isDefault() {
        return immutable;
    }

    /**
     * Records a message in the conversation history for this memory-enabled instance.
     * <p>
     * This is automatically called by the AI service to record assistant responses after a successful response. It can also be called manually to seed the
     * conversation with prior context.
     * <p>
     * When the history exceeds the configured maximum (default {@value #DEFAULT_MAX_HISTORY} messages, counting both sent and received), the oldest messages
     * are automatically discarded to maintain the sliding window.
     *
     * @param role The role of the message.
     * @param message The message content.
     * @throws IllegalStateException if this instance is not {@link #hasMemory() memory-enabled}.
     * @see #discardPendingUserMessage()
     */
    public void recordMessage(Role role, String message) {
        if (!hasMemory()) {
            throw new IllegalStateException("Cannot record message on non-memory ChatOptions; use withMemory() method to create a memory-enabled instance");
        }

        history.add(new Message(role, message, emptyList()));

        while (history.size() > maxHistory) {
            history.remove(0);
        }
    }

    /**
     * Discards a pending user message from the end of the conversation history, if there is one.
     * <p>
     * A pending user message is a trailing user message not yet followed by an assistant response. Since an assistant message is only recorded after a
     * successful response, such a message marks a request that failed before completing. The AI service calls this before re-recording the user message of a
     * re-attempted request, so that the request is not recorded once per attempt and the file references the failed attempt anchored to it are dropped. When
     * the history is empty or ends in an assistant message, this does nothing.
     *
     * @throws IllegalStateException if this instance is not {@link #hasMemory() memory-enabled}.
     * @since 1.5
     * @see #recordMessage(Role, String)
     */
    public void discardPendingUserMessage() {
        if (!hasMemory()) {
            throw new IllegalStateException("Cannot discard message on non-memory ChatOptions; use withMemory() method to create a memory-enabled instance");
        }

        if (!history.isEmpty() && history.get(history.size() - 1).role() == Role.USER) {
            history.remove(history.size() - 1);
        }
    }

    /**
     * Records an uploaded file reference against the most recent user message in the conversation history.
     * <p>
     * This is called by text handlers during {@code buildChatPayload} after uploading a file, so the file ID can be replayed in subsequent turns. The file
     * reference is automatically discarded when its associated message is evicted from the sliding window.
     *
     * @param fileId The provider-assigned file ID or URI.
     * @param mimeType The MIME type of the uploaded file.
     * @throws IllegalStateException if this instance is not {@link #hasMemory() memory-enabled}, or if there is no preceding user message.
     * @since 1.1
     * @see #getHistory()
     * @deprecated Since 1.7. Use {@link #recordUploadedFile(UploadedFile)} instead, which takes everything the uploaded file states, such as the video analysis
     * options it was uploaded with.
     */
    @Deprecated(since = "1.7", forRemoval = true)
    public void recordUploadedFile(String fileId, MimeType mimeType) {
        recordUploadedFile(new UploadedFile(fileId, mimeType));
    }

    /**
     * Records an uploaded file reference against the most recent user message in the conversation history.
     * <p>
     * This is called by text handlers during {@code buildChatPayload} after uploading a file, so the file ID can be replayed in subsequent turns. The file
     * reference is automatically discarded when its associated message is evicted from the sliding window.
     *
     * @param uploadedFile The uploaded file to record, which states everything the next turn needs to reference it exactly as this one did.
     * @throws IllegalStateException if this instance is not {@link #hasMemory() memory-enabled}, or if there is no preceding user message.
     * @since 1.7
     * @see #getHistory()
     */
    public void recordUploadedFile(UploadedFile uploadedFile) {
        if (!hasMemory()) {
            throw new IllegalStateException(
                "Cannot record uploaded file on non-memory ChatOptions; use withMemory() method to create a memory-enabled instance"
            );
        }

        for (var i = history.size() - 1; i >= 0; i--) {
            var message = history.get(i);

            if (message.role() == Role.USER) {
                var uploadedFiles = new ArrayList<>(message.uploadedFiles());
                uploadedFiles.add(uploadedFile);
                history.set(i, new Message(message.role(), message.content(), uploadedFiles));
                return;
            }
        }

        throw new IllegalStateException("Cannot record uploaded file without a preceding user message");
    }

    /**
     * Records token usage for the most recent chat call. This is automatically called by the AI service after each chat response, even when the provider did
     * not report usage. A {@code null} value indicates that the last call did not report any usage, which clears any previously recorded usage.
     *
     * @param usage The usage to record, or {@code null} if the provider did not report any usage.
     * @throws IllegalStateException if this is a {@link #isDefault() default} instance.
     * @since 1.3
     * @see #getLastUsage()
     */
    public void recordUsage(ChatUsage usage) {
        if (isDefault()) {
            throw new IllegalStateException(
                "Cannot record usage on a default (shared) ChatOptions instance; use copy() or a withXxx() method to create a dedicated instance"
            );
        }

        accounting.recordUsage(usage, pricing);
    }

    /**
     * Serializes this instance to a portable JSON string suitable for session stores, databases, audit logs, or cross-service transport.
     * <p>
     * All user-facing options are included: {@code systemPrompt}, {@code jsonSchema}, {@code temperature}, {@code maxTokens}, {@code reasoningEffort},
     * {@code topP}, {@code webSearchLocation}, {@code pricing}, {@code maxTotalCost}, {@code maxHistory}, and {@link #getHistory() history} (including any
     * recorded uploaded file references). Null or unset fields are omitted for a compact payload. Runtime state — {@link #getLastUsage() last usage} and
     * {@link #getTotalCost() total cost} — is deliberately not included.
     * <p>
     * The returned JSON can be rehydrated via {@link #fromJson(String)}. Round-tripping a shared default constant ({@link #DEFAULT}, {@link #CREATIVE},
     * {@link #DETERMINISTIC}) yields a mutable copy, equivalent to calling {@link #copy()}.
     *
     * @return A JSON string representation of this instance.
     * @since 1.4
     * @see #fromJson(String)
     */
    public String toJson() {
        var builder = Json.createObjectBuilder();

        if (systemPrompt != null) {
            builder.add(SYSTEM_PROMPT_KEY, systemPrompt);
        }
        if (jsonSchema != null) {
            builder.add(JSON_SCHEMA_KEY, jsonSchema);
        }

        builder.add(TEMPERATURE_KEY, temperature);

        if (maxTokens != null) {
            builder.add(MAX_TOKENS_KEY, maxTokens);
        }

        builder.add(REASONING_EFFORT_KEY, reasoningEffort.name());
        builder.add(TOP_P_KEY, topP);

        addWebSearchLocation(builder);
        addPricing(builder);

        if (maxTotalCost != null) {
            builder.add(MAX_TOTAL_COST_KEY, maxTotalCost);
        }

        addHistory(builder);

        return builder.build().toString();
    }

    private void addWebSearchLocation(JsonObjectBuilder builder) {
        if (webSearchLocation == null) {
            return;
        }

        var locationBuilder = Json.createObjectBuilder();
        addIfNotNull(locationBuilder, COUNTRY_KEY, webSearchLocation.country());
        addIfNotNull(locationBuilder, REGION_KEY, webSearchLocation.region());
        addIfNotNull(locationBuilder, CITY_KEY, webSearchLocation.city());
        builder.add(WEB_SEARCH_LOCATION_KEY, locationBuilder);
    }

    private void addPricing(JsonObjectBuilder builder) {
        if (pricing == null) {
            return;
        }

        var pricingBuilder = Json.createObjectBuilder()
            .add(INPUT_TOKEN_PRICE_KEY, pricing.inputTokenPrice())
            .add(OUTPUT_TOKEN_PRICE_KEY, pricing.outputTokenPrice());

        if (pricing.cachedInputTokenPrice() != null) {
            pricingBuilder.add(CACHED_INPUT_TOKEN_PRICE_KEY, pricing.cachedInputTokenPrice());
        }
        if (pricing.currency() != null) {
            pricingBuilder.add(CURRENCY_KEY, pricing.currency().getCurrencyCode());
        }

        builder.add(PRICING_KEY, pricingBuilder);
    }

    private void addHistory(JsonObjectBuilder builder) {
        if (history == null) {
            return;
        }

        builder.add(MAX_HISTORY_KEY, maxHistory);
        var historyBuilder = Json.createArrayBuilder();

        for (var message : history) {
            var messageBuilder = Json.createObjectBuilder()
                .add(ROLE_KEY, message.role().name())
                .add(CONTENT_KEY, message.content());

            if (!message.uploadedFiles().isEmpty()) {
                messageBuilder.add(UPLOADED_FILES_KEY, toJson(message.uploadedFiles()));
            }

            historyBuilder.add(messageBuilder);
        }

        builder.add(HISTORY_KEY, historyBuilder);
    }

    private static JsonArrayBuilder toJson(List<UploadedFile> uploadedFiles) {
        var filesBuilder = Json.createArrayBuilder();

        for (var uploadedFile : uploadedFiles) {
            var fileBuilder = Json.createObjectBuilder()
                .add(ID_KEY, uploadedFile.id())
                .add(MIME_TYPE_KEY, uploadedFile.mimeType().value());

            toJson(uploadedFile.videoOptions()).ifPresent(videoOptions -> fileBuilder.add(VIDEO_OPTIONS_KEY, videoOptions));
            filesBuilder.add(fileBuilder);
        }

        return filesBuilder;
    }

    private static void addIfNotNull(JsonObjectBuilder builder, String name, String value) {
        if (value != null) {
            builder.add(name, value);
        }
    }

    /**
     * Deserializes a JSON string produced by {@link #toJson()} into a fresh {@link ChatOptions} instance.
     * <p>
     * Missing fields fall back to the same defaults as {@link #newBuilder()}. The returned instance is always mutable (i.e. {@link #isDefault()} returns
     * {@code false}) and starts with no {@link #getLastUsage() last usage} recorded.
     *
     * @param json The JSON string to parse. Must not be {@code null}.
     * @return A new {@link ChatOptions} instance.
     * @throws NullPointerException if {@code json} is {@code null}.
     * @throws org.omnifaces.ai.exception.AIResponseException if the JSON cannot be parsed.
     * @throws IllegalArgumentException if a field contains an invalid value (e.g. unknown reasoning effort, out-of-range temperature).
     * @since 1.4
     * @see #toJson()
     */
    public static ChatOptions fromJson(String json) {
        var parsed = parseJson(requireNonNull(json, "json"));
        var builder = newBuilder();

        if (parsed.containsKey(SYSTEM_PROMPT_KEY)) {
            builder.systemPrompt(parsed.getString(SYSTEM_PROMPT_KEY));
        }
        if (parsed.containsKey(JSON_SCHEMA_KEY)) {
            builder.jsonSchema(parsed.getJsonObject(JSON_SCHEMA_KEY));
        }
        if (parsed.containsKey(TEMPERATURE_KEY)) {
            builder.temperature(parsed.getJsonNumber(TEMPERATURE_KEY).doubleValue());
        }
        if (parsed.containsKey(MAX_TOKENS_KEY) && !parsed.isNull(MAX_TOKENS_KEY)) {
            builder.maxTokens(parsed.getInt(MAX_TOKENS_KEY));
        }
        if (parsed.containsKey(REASONING_EFFORT_KEY)) {
            builder.reasoningEffort(ReasoningEffort.valueOf(parsed.getString(REASONING_EFFORT_KEY)));
        }
        if (parsed.containsKey(TOP_P_KEY)) {
            builder.topP(parsed.getJsonNumber(TOP_P_KEY).doubleValue());
        }

        restoreWebSearchLocation(builder, parsed);
        restorePricing(builder, parsed);
        restoreHistory(builder, parsed);

        return builder.build();
    }

    private static void restoreWebSearchLocation(Builder builder, JsonObject parsed) {
        if (!parsed.containsKey(WEB_SEARCH_LOCATION_KEY)) {
            return;
        }

        var location = parsed.getJsonObject(WEB_SEARCH_LOCATION_KEY);
        builder.webSearch(
            new Location(
                location.getString(COUNTRY_KEY, null),
                location.getString(REGION_KEY, null),
                location.getString(CITY_KEY, null)
            )
        );
    }

    private static void restorePricing(Builder builder, JsonObject parsed) {
        if (!parsed.containsKey(PRICING_KEY)) {
            return;
        }

        var pricingObject = parsed.getJsonObject(PRICING_KEY);
        var cachedInputTokenPrice = pricingObject.containsKey(CACHED_INPUT_TOKEN_PRICE_KEY)
            ? pricingObject.getJsonNumber(CACHED_INPUT_TOKEN_PRICE_KEY).bigDecimalValue()
            : null;
        var currencyCode = pricingObject.getString(CURRENCY_KEY, null);
        var restoredPricing = new ChatPricing(
            pricingObject.getJsonNumber(INPUT_TOKEN_PRICE_KEY).bigDecimalValue(),
            cachedInputTokenPrice,
            pricingObject.getJsonNumber(OUTPUT_TOKEN_PRICE_KEY).bigDecimalValue(),
            currencyCode != null ? Currency.getInstance(currencyCode) : null
        );

        if (parsed.containsKey(MAX_TOTAL_COST_KEY)) {
            builder.pricing(restoredPricing, parsed.getJsonNumber(MAX_TOTAL_COST_KEY).bigDecimalValue());
        }
        else {
            builder.pricing(restoredPricing);
        }
    }

    private static void restoreHistory(Builder builder, JsonObject parsed) {
        if (!parsed.containsKey(HISTORY_KEY) && !parsed.containsKey(MAX_HISTORY_KEY)) {
            return;
        }

        builder.withMemory(parsed.getInt(MAX_HISTORY_KEY, DEFAULT_MAX_HISTORY));

        if (!parsed.containsKey(HISTORY_KEY)) {
            return;
        }

        var restored = new ArrayList<Message>();

        for (var value : parsed.getJsonArray(HISTORY_KEY)) {
            var message = value.asJsonObject();
            restored.add(new Message(Role.valueOf(message.getString(ROLE_KEY)), message.getString(CONTENT_KEY), toUploadedFiles(message)));
        }

        builder.history(restored);
    }

    private static List<UploadedFile> toUploadedFiles(JsonObject message) {
        var files = new ArrayList<UploadedFile>();

        if (message.containsKey(UPLOADED_FILES_KEY)) {
            for (var fileValue : message.getJsonArray(UPLOADED_FILES_KEY)) {
                var file = fileValue.asJsonObject();
                files.add(new UploadedFile(file.getString(ID_KEY), MimeType.of(file.getString(MIME_TYPE_KEY)), toVideoOptions(file)));
            }
        }

        return files;
    }

    /**
     * Creates a new builder for constructing {@link ChatOptions} instances. For example:
     *
     * <pre>
     *
     * ChatOptions options = ChatOptions.newBuilder()
     *     .systemPrompt("You are a helpful software architect.")
     *     .jsonSchema(myJsonSchema)
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

        private String systemPrompt;
        private JsonObject jsonSchema;
        private double temperature = ChatOptions.DEFAULT_TEMPERATURE;
        private Integer maxTokens;
        private ReasoningEffort reasoningEffort = ChatOptions.DEFAULT_REASONING_EFFORT;
        private double topP = ChatOptions.DEFAULT_TOP_P;
        private Location webSearchLocation;
        private ChatPricing pricing;
        private BigDecimal maxTotalCost;
        private int maxHistory;
        private List<Message> history;

        private Builder() {
        }

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
         * Sets the JSON schema for structured output. Defaults to {@code null}.
         * <p>
         * For most use cases, prefer the typed chat overloads {@link org.omnifaces.ai.AIService#chat(String, Class)} which handle schema generation and
         * response parsing automatically. Use this method directly only when you need manual control over the schema.
         * <p>
         * You can use {@link JsonSchemaHelper#buildJsonSchema(Class)} to create one for your record or bean class.
         * <p>
         * When set, the AI model is instructed to return a response that conforms to this JSON schema. This is useful for ensuring the model returns valid,
         * parseable JSON in a specific format.
         * <p>
         * The schema should follow the JSON Schema specification. For example:
         *
         * <pre>
         * {
         *   "type": "object",
         *   "properties": {
         *     "name": { "type": "string" },
         *     "age": { "type": "number" }
         *   },
         *   "required": ["name", "age"]
         * }
         * </pre>
         * <p>
         * You can use {@link JsonSchemaHelper#fromJson(String, Class)} to parse the response into your record or bean class.
         * <p>
         * Note: Not all AI providers support JSON schema enforcement. When unsupported, the AI service implementation may throw
         * {@link UnsupportedOperationException} during chat payload construction.
         *
         * @param jsonSchema The JSON schema object. Can be {@code null}.
         * @return This builder instance for chaining.
         */
        public Builder jsonSchema(JsonObject jsonSchema) {
            this.jsonSchema = jsonSchema;
            return this;
        }

        /**
         * Sets the sampling temperature used for token selection. Defaults to {@value ChatOptions#DEFAULT_TEMPERATURE}.
         * <p>
         * Higher values (e.g., 0.8) make responses more creative and varied. Lower values (e.g., 0.2) make them more focused and predictable. A value of 0
         * always picks the most likely next word.
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
         * A token is a model-specific unit of text which varies per input and output language. In English 1000 tokens is roughly 750 words, but in e.g. Spanish
         * it would be roughly 700 words due to more inflection and functions in the language.
         * <p>
         * For classic (fast) chat models (e.g. gpt-4), this setting only limits how long the response can be. For reasoning-enabled chat models (e.g. gpt-5),
         * this limit includes both the visible response and the model's thinking process. If the limit is reached, the response will be cut off mid-sentence.
         * If the thinking process is complex, the response may be shorter. Your input plus this limit must fit within the model's maximum context size.
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
         * Sets the reasoning effort for models that support extended thinking. Defaults to {@link ReasoningEffort#AUTO}.
         * <p>
         * Since {@link #maxTokens(Integer) maxTokens} on reasoning-capable models includes the model's thinking tokens in addition to the visible response,
         * picking a higher reasoning effort may require a correspondingly higher {@code maxTokens} to avoid truncated responses. See {@link ReasoningEffort}
         * for per-provider details.
         *
         * @param reasoningEffort The reasoning effort to apply. Must not be {@code null}; use {@link ReasoningEffort#AUTO} to defer to the provider default.
         * @return This builder instance for chaining.
         * @throws NullPointerException if {@code reasoningEffort} is {@code null}.
         * @since 1.4
         * @see ReasoningEffort
         */
        public Builder reasoningEffort(ReasoningEffort reasoningEffort) {
            this.reasoningEffort = requireNonNull(reasoningEffort, "reasoningEffort");
            return this;
        }

        /**
         * Sets the Nucleus Sampling (Top-P) value. Defaults to {@value ChatOptions#DEFAULT_TOP_P}.
         * <p>
         * Top-P controls how many word choices the model considers. A value of 1.0 considers all possible words, while 0.1 only considers the most likely words
         * (top 10%). Lower values give more focused responses, higher values allow more variety. It is generally recommended to alter either this or
         * {@code temperature}, but not both.
         * <ul>
         * <li><strong>Low Temp + Low Top-P:</strong> Corporate email simulator (The Deterministic Robot)</li>
         * <li><strong>Low Temp + High Top-P:</strong> Boring but stable (The Safe Bet)</li>
         * <li><strong>Medium Temp + Low Top-P:</strong> Focused / Professional (The Focused Expert)</li>
         * <li><strong>Medium Temp + High Top-P:</strong> Natural / Casual (The Conversationalist)</li>
         * <li><strong>High Temp + Low Top-P:</strong> Creative but coherent (The Controlled Artist)</li>
         * <li><strong>High Temp + High Top-P:</strong> Word salads / Hallucination station</li>
         * </ul>
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
         * Enables global web search for this {@link ChatOptions} instance.
         * <p>
         * When enabled, the AI service will access up-to-date information from the internet and provide answers with sourced citations, without geographical
         * restriction.
         *
         * @return This builder instance for chaining.
         * @since 1.3
         * @see #webSearch(Location)
         */
        public Builder webSearch() {
            this.webSearchLocation = Location.GLOBAL;
            return this;
        }

        /**
         * Enables localized web search for this {@link ChatOptions} instance.
         * <p>
         * When enabled, the AI service will access up-to-date information from the internet and provide answers with sourced citations scoped to the provided
         * {@link Location}.
         *
         * @param location The specific location.
         * @return This builder instance for chaining.
         * @throws NullPointerException if {@code location} is {@code null}.
         * @since 1.3
         * @see #webSearch()
         */
        public Builder webSearch(Location location) {
            this.webSearchLocation = requireNonNull(location, "location");
            return this;
        }

        /**
         * Sets the pricing configuration used to calculate cost from recorded token usage. Defaults to {@code null} (i.e. no cost calculation). Any previously
         * configured cumulative-cost cap is cleared; use {@link #pricing(ChatPricing, BigDecimal)} to set both at once.
         * <p>
         * Prices are interpreted as per one million tokens. When set, {@link ChatOptions#getLastCost()} returns the computed {@link ChatCost} of the most
         * recent chat call. See {@link ChatPricing} for details.
         *
         * @param pricing The pricing configuration, or {@code null} to disable cost calculation.
         * @return This builder instance for chaining.
         * @since 1.4
         * @see ChatPricing
         * @see ChatOptions#getLastCost()
         */
        public Builder pricing(ChatPricing pricing) {
            this.pricing = pricing;
            this.maxTotalCost = null;
            return this;
        }

        /**
         * Sets the pricing configuration along with a strictly positive cumulative-cost cap. Subsequent chat calls made with the built {@link ChatOptions}
         * instance throw {@link AIBudgetExceededException} once the accumulated {@link ChatOptions#getTotalCost() total cost} reaches or exceeds the given cap.
         * The cap is interpreted in the currency carried by {@code pricing}.
         *
         * @param pricing The pricing configuration. Must not be {@code null}.
         * @param maxTotalCost The cumulative-cost cap. Must not be {@code null} and must be strictly positive.
         * @return This builder instance for chaining.
         * @throws NullPointerException if {@code pricing} or {@code maxTotalCost} is {@code null}.
         * @throws IllegalArgumentException if {@code maxTotalCost} is not strictly positive.
         * @since 1.4
         * @see AIBudgetExceededException
         * @see ChatOptions#getMaxTotalCost()
         * @see ChatOptions#getTotalCost()
         * @see ChatOptions#resetBudget()
         */
        public Builder pricing(ChatPricing pricing, BigDecimal maxTotalCost) {
            this.pricing = requireNonNull(pricing, "pricing");
            this.maxTotalCost = requireValidMaxTotalCost(maxTotalCost);
            return this;
        }

        /**
         * Enables conversation memory for this {@code ChatOptions} instance with a default sliding window of {@value ChatOptions#DEFAULT_MAX_HISTORY} messages
         * (counting both sent and received, i.e. 10 conversational turns).
         * <p>
         * When enabled, the AI service will automatically remember all user messages and assistant responses made with this instance, and include them in
         * subsequent chat requests. This allows multi-turn conversations where the AI has context of previous exchanges.
         * <p>
         * Once the number of recorded messages exceeds the maximum, the oldest messages are automatically discarded.
         *
         * @return This builder instance for chaining.
         * @see #withMemory(int)
         */
        public Builder withMemory() {
            return withMemory(DEFAULT_MAX_HISTORY);
        }

        /**
         * Enables conversation memory for this {@code ChatOptions} instance with a custom sliding window size.
         * <p>
         * When enabled, the AI service will automatically remember all user messages and assistant responses made with this instance, and include them in
         * subsequent chat requests. This allows multi-turn conversations where the AI has context of previous exchanges.
         * <p>
         * Once the number of recorded messages exceeds the given maximum, the oldest messages are automatically discarded.
         *
         * @param maxHistory The maximum number of messages (both sent and received) to retain in the conversation history. Must be positive.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException if maxHistory is less than 1.
         * @see #withMemory()
         * @since 1.1
         */
        public Builder withMemory(int maxHistory) {
            if (maxHistory < 1) {
                throw new IllegalArgumentException("Max history must be positive");
            }

            this.maxHistory = maxHistory;
            return this;
        }

        /**
         * Sets the initial conversation history for this memory-enabled {@code ChatOptions} instance.
         * <p>
         * This allows restoring a previously saved conversation. The history list is typically obtained from {@link ChatOptions#getHistory()} of a prior
         * session and persisted externally (e.g. in a database or HTTP session).
         * <p>
         * If the provided history exceeds the configured {@link #withMemory(int) maximum}, the oldest messages are automatically discarded to fit within the
         * sliding window.
         * <p>
         * Memory is implicitly enabled with {@value ChatOptions#DEFAULT_MAX_HISTORY} if not already set via {@link #withMemory()} or {@link #withMemory(int)}.
         * <p>
         * Usage example:
         *
         * <pre>
         *
         * // Save history from a previous session
         * List&lt;Message&gt; saved = options.getHistory();
         *
         * // Restore history in a new session
         * ChatOptions restored = ChatOptions.newBuilder()
         *     .systemPrompt("You are a helpful assistant")
         *     .withMemory(20)
         *     .history(saved)
         *     .build();
         * </pre>
         *
         * @param history The initial conversation history to seed. Must not be {@code null}.
         * @return This builder instance for chaining.
         * @throws NullPointerException if history is {@code null}.
         * @since 1.2
         * @see ChatOptions#getHistory()
         * @see #withMemory()
         * @see #withMemory(int)
         */
        public Builder history(List<Message> history) {
            this.history = List.copyOf(history);
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
