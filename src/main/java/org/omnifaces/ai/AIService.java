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

import static java.util.Objects.requireNonNull;
import static org.omnifaces.ai.helper.JsonSchemaHelper.buildJsonSchema;
import static org.omnifaces.ai.helper.JsonSchemaHelper.fromJson;
import static org.omnifaces.ai.model.ChatOptions.DEFAULT;
import static org.omnifaces.ai.model.ChatOptions.Location.GLOBAL;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.helper.JsonSchemaHelper;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.Location;
import org.omnifaces.ai.model.GenerateAudioOptions;
import org.omnifaces.ai.model.GenerateImageOptions;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.ModerationOptions.Category;
import org.omnifaces.ai.model.ModerationResult;
import org.omnifaces.ai.service.AIServiceWrapper;
import org.omnifaces.ai.service.ToolCallingAIService;
import org.omnifaces.ai.tool.AITool;
import org.omnifaces.ai.tool.ToolRegistry;

/**
 * Generic interface for AI service providers.
 * <p>
 * This interface provides a unified abstraction for various AI capabilities including chat, summarization, translation, proofreading, content moderation, image
 * analysis, and more.
 * <p>
 * The implementations must be stateless and able to be {@code jakarta.enterprise.context.ApplicationScoped}.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider
 * @see AIConfig
 * @see AIModality
 * @see AIServiceWrapper
 */
public interface AIService extends Serializable {

    // Chat Capabilities ----------------------------------------------------------------------------------------------

    /**
     * Sends a message to the AI with default system prompt from {@link #getChatPrompt()}.
     * <p>
     * Usage example:
     *
     * <pre>
     *
     * var response = service.chat(message);
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatAsync(String)}.
     * @param message The user's message to send to the AI.
     * @return The AI's response, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     * @throws AIException if the chat request fails.
     */
    default String chat(String message) throws AIException {
        return joinAsync(chatAsync(message));
    }

    /**
     * Sends input to the AI with default system prompt from {@link #getChatPrompt()}.
     * <p>
     * Usage example:
     *
     * <pre>
     *
     * var input = ChatInput.newBuilder()
     *     .message(message)
     *     .attach(imageBytes)
     *     .build();
     * var response = service.chat(input);
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatAsync(ChatInput)}.
     * @param input The user's input containing message and optional file attachments.
     * @return The AI's response, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     * @throws AIException if the chat request fails.
     */
    default String chat(ChatInput input) throws AIException {
        return joinAsync(chatAsync(input));
    }

    /**
     * Sends a message to the AI with default system prompt from {@link #getChatPrompt()} and returns a typed response.
     * <p>
     * This method auto-generates a JSON schema from the given type, instructs the AI to return structured output conforming to that schema, and parses the
     * response back into the specified type.
     * <p>
     * Usage example:
     *
     * <pre>
     *
     * record ProductReview(String sentiment, int rating, List&lt;String&gt; pros, List&lt;String&gt; cons) {
     * }
     *
     * var review = service.chat("Analyze this review: " + reviewText, ProductReview.class);
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatAsync(String, Class)}.
     * @param <T> The target type.
     * @param message The user's message to send to the AI.
     * @param type The target class for the structured response (record or bean).
     * @return The AI's response parsed into the specified type, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     * @throws UnsupportedOperationException if structured output is not supported by the implementation.
     * @throws AIException if the chat request fails.
     * @see JsonSchemaHelper#buildJsonSchema(Class)
     * @see JsonSchemaHelper#fromJson(String, Class)
     */
    default <T> T chat(String message, Class<T> type) throws AIException {
        return joinAsync(chatAsync(message, type));
    }

    /**
     * Sends input to the AI with default system prompt from {@link #getChatPrompt()} and returns a typed response.
     * <p>
     * This method auto-generates a JSON schema from the given type, instructs the AI to return structured output conforming to that schema, and parses the
     * response back into the specified type.
     * <p>
     * Usage example:
     *
     * <pre>
     *
     * record ProductReview(String sentiment, int rating, List&lt;String&gt; pros, List&lt;String&gt; cons) {
     * }
     *
     * var input = ChatInput.newBuilder()
     *     .message("Analyze this review: " + reviewText)
     *     .attach(imageBytes)
     *     .build();
     * var review = service.chat(input, ProductReview.class);
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatAsync(ChatInput, Class)}.
     * @param <T> The target type.
     * @param input The user's input containing message and file attachments.
     * @param type The target class for the structured response (record or bean).
     * @return The AI's response parsed into the specified type, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     * @throws UnsupportedOperationException if structured output is not supported by the implementation.
     * @throws AIException if the chat request fails.
     * @see JsonSchemaHelper#buildJsonSchema(Class)
     * @see JsonSchemaHelper#fromJson(String, Class)
     */
    default <T> T chat(ChatInput input, Class<T> type) throws AIException {
        return joinAsync(chatAsync(input, type));
    }

    /**
     * Sends a message to the AI and returns a response.
     * <p>
     * The message represents the user's input, while the system prompt in options defines the AI's behavior.
     * <p>
     * Usage example:
     *
     * <pre>
     *
     * var response = service.chat(message, options);
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatAsync(String, ChatOptions)}.
     * @param message The user's message to send to the AI.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @return The AI's response, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     * @throws AIException if the chat request fails.
     */
    default String chat(String message, ChatOptions options) throws AIException {
        return joinAsync(chatAsync(message, options));
    }

    /**
     * Sends input to the AI and returns a response.
     * <p>
     * The input contains the user's message and file attachments, while the system prompt in options defines the AI's behavior.
     * <p>
     * Usage example:
     *
     * <pre>
     *
     * var input = ChatInput.newBuilder()
     *     .message(message)
     *     .attach(imageBytes)
     *     .build();
     * var response = service.chat(input, options);
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatAsync(ChatInput, ChatOptions)}.
     * @param input The user's input containing message and file attachments.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @return The AI's response, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     * @throws AIException if the chat request fails.
     */
    default String chat(ChatInput input, ChatOptions options) throws AIException {
        return joinAsync(chatAsync(input, options));
    }

    /**
     * Sends a message to the AI and returns a typed response.
     * <p>
     * This method auto-generates a JSON schema from the given type, instructs the AI to return structured output conforming to that schema, and parses the
     * response back into the specified type.
     * <p>
     * Usage example:
     *
     * <pre>
     *
     * record ProductReview(String sentiment, int rating, List&lt;String&gt; pros, List&lt;String&gt; cons) {
     * }
     *
     * var options = ChatOptions.newBuilder()
     *     .systemPrompt("You are a product review analyzer.")
     *     .build();
     * var review = service.chat("Analyze this review: " + reviewText, options, ProductReview.class);
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatAsync(String, ChatOptions, Class)}.
     * @param <T> The target type.
     * @param message The user's message to send to the AI.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @param type The target class for the structured response (record or bean).
     * @return The AI's response parsed into the specified type, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     * @throws UnsupportedOperationException if structured output is not supported by the implementation.
     * @throws AIException if the chat request fails.
     * @see JsonSchemaHelper#buildJsonSchema(Class)
     * @see JsonSchemaHelper#fromJson(String, Class)
     */
    default <T> T chat(String message, ChatOptions options, Class<T> type) throws AIException {
        return joinAsync(chatAsync(message, options, type));
    }

    /**
     * Sends input to the AI and returns a typed response.
     * <p>
     * This method auto-generates a JSON schema from the given type, instructs the AI to return structured output conforming to that schema, and parses the
     * response back into the specified type.
     * <p>
     * Usage example:
     *
     * <pre>
     *
     * record ProductReview(String sentiment, int rating, List&lt;String&gt; pros, List&lt;String&gt; cons) {
     * }
     *
     * var input = ChatInput.newBuilder()
     *     .message("Analyze this review: " + reviewText)
     *     .attach(imageBytes)
     *     .build();
     * var options = ChatOptions.newBuilder()
     *     .systemPrompt("You are a product review analyzer.")
     *     .build();
     * var review = service.chat(input, options, ProductReview.class);
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatAsync(ChatInput, ChatOptions, Class)}.
     * @param <T> The target type.
     * @param input The user's input containing message and file attachments.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @param type The target class for the structured response (record or bean).
     * @return The AI's response parsed into the specified type, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     * @throws UnsupportedOperationException if structured output is not supported by the implementation.
     * @throws AIException if the chat request fails.
     * @see JsonSchemaHelper#buildJsonSchema(Class)
     * @see JsonSchemaHelper#fromJson(String, Class)
     */
    default <T> T chat(ChatInput input, ChatOptions options, Class<T> type) throws AIException {
        return joinAsync(chatAsync(input, options, type));
    }

    /**
     * Asynchronously sends a message to the AI with default system prompt from {@link #getChatPrompt()}.
     * <p>
     * Usage example:
     *
     * <pre>
     * service.chatAsync(message).thenAccept(response -> {
     *     // handle full response
     * }).exceptionally(e -> {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatAsync(ChatInput)}.
     * @param message The user's message to send to the AI.
     * @return A CompletableFuture that will contain the AI's response, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     * @throws AIException if the chat request fails.
     */
    default CompletableFuture<String> chatAsync(String message) throws AIException {
        return chatAsync(ChatInput.newBuilder().message(message).build());
    }

    /**
     * Asynchronously sends input to the AI with default system prompt from {@link #getChatPrompt()}.
     * <p>
     * Usage example:
     *
     * <pre>
     * var input = ChatInput.newBuilder()
     *     .message(message)
     *     .attach(imageBytes)
     *     .build();
     * service.chatAsync(input).thenAccept(response -> {
     *     // handle full response
     * }).exceptionally(e -> {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatAsync(ChatInput, ChatOptions)}.
     * @param input The user's input containing message and file attachments.
     * @return A CompletableFuture that will contain the AI's response, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     * @throws AIException if the chat request fails.
     */
    default CompletableFuture<String> chatAsync(ChatInput input) throws AIException {
        return chatAsync(input, DEFAULT.withSystemPrompt(getChatPrompt()));
    }

    /**
     * Asynchronously sends a message to the AI with default system prompt from {@link #getChatPrompt()} and returns a typed response.
     * <p>
     * This method auto-generates a JSON schema from the given type, instructs the AI to return structured output conforming to that schema, and parses the
     * response back into the specified type.
     * <p>
     * Usage example:
     *
     * <pre>
     * record ProductReview(String sentiment, int rating, List&lt;String&gt; pros, List&lt;String&gt; cons) {
     * }
     *
     * service.chatAsync("Analyze this review: " + reviewText, ProductReview.class).thenAccept(review -&gt; {
     *     // handle typed response
     * }).exceptionally(e -&gt; {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatAsync(String, ChatOptions, Class)}.
     * @param <T> The target type.
     * @param message The user's message to send to the AI.
     * @param type The target class for the structured response (record or bean).
     * @return A CompletableFuture that will contain the AI's response parsed into the specified type, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     * @throws UnsupportedOperationException if structured output is not supported by the implementation.
     * @throws AIException if the chat request fails.
     * @see JsonSchemaHelper#buildJsonSchema(Class)
     * @see JsonSchemaHelper#fromJson(String, Class)
     */
    default <T> CompletableFuture<T> chatAsync(String message, Class<T> type) throws AIException {
        return chatAsync(message, DEFAULT.withSystemPrompt(getChatPrompt()), type);
    }

    /**
     * Asynchronously sends input to the AI with default system prompt from {@link #getChatPrompt()} and returns a typed response.
     * <p>
     * This method auto-generates a JSON schema from the given type, instructs the AI to return structured output conforming to that schema, and parses the
     * response back into the specified type.
     * <p>
     * Usage example:
     *
     * <pre>
     * record ProductReview(String sentiment, int rating, List&lt;String&gt; pros, List&lt;String&gt; cons) {
     * }
     *
     * var input = ChatInput.newBuilder()
     *     .message("Analyze this review: " + reviewText)
     *     .attach(imageBytes)
     *     .build();
     * service.chatAsync(input, ProductReview.class).thenAccept(review -&gt; {
     *     // handle typed response
     * }).exceptionally(e -&gt; {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatAsync(ChatInput, ChatOptions, Class)}.
     * @param <T> The target type.
     * @param input The user's input containing message and file attachments.
     * @param type The target class for the structured response (record or bean).
     * @return A CompletableFuture that will contain the AI's response parsed into the specified type, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     * @throws UnsupportedOperationException if structured output is not supported by the implementation.
     * @throws AIException if the chat request fails.
     * @see JsonSchemaHelper#buildJsonSchema(Class)
     * @see JsonSchemaHelper#fromJson(String, Class)
     */
    default <T> CompletableFuture<T> chatAsync(ChatInput input, Class<T> type) throws AIException {
        return chatAsync(input, DEFAULT.withSystemPrompt(getChatPrompt()), type);
    }

    /**
     * Asynchronously sends a message to the AI and returns a response.
     * <p>
     * The message represents the user's input, while the system prompt in options defines the AI's behavior.
     * <p>
     * Usage example:
     *
     * <pre>
     * service.chatAsync(message, options).thenAccept(response -> {
     *     // handle full response
     * }).exceptionally(e -> {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatAsync(ChatInput, ChatOptions)}.
     * @param message The user's message to send to the AI.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @return A CompletableFuture that will contain the AI's response, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     * @throws AIException if the chat request fails.
     */
    default CompletableFuture<String> chatAsync(String message, ChatOptions options) throws AIException {
        return chatAsync(ChatInput.newBuilder().message(message).build(), options);
    }

    /**
     * Asynchronously sends a message to the AI and returns a typed response.
     * <p>
     * This method auto-generates a JSON schema from the given type, instructs the AI to return structured output conforming to that schema, and parses the
     * response back into the specified type.
     * <p>
     * Usage example:
     *
     * <pre>
     * record ProductReview(String sentiment, int rating, List&lt;String&gt; pros, List&lt;String&gt; cons) {
     * }
     *
     * var options = ChatOptions.newBuilder()
     *     .systemPrompt("You are a product review analyzer.")
     *     .build();
     * service.chatAsync("Analyze this review: " + reviewText, options, ProductReview.class).thenAccept(review -&gt; {
     *     // handle typed response
     * }).exceptionally(e -&gt; {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     *
     * @implNote The default implementation generates a JSON schema via {@link JsonSchemaHelper#buildJsonSchema(Class)}, merges it into the options via
     * {@link ChatOptions#withJsonSchema(jakarta.json.JsonObject)}, delegates to {@link #chatAsync(String, ChatOptions)}, and parses the response via
     * {@link JsonSchemaHelper#fromJson(String, Class)}.
     * @param <T> The target type.
     * @param message The user's message to send to the AI.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @param type The target class for the structured response (record or bean).
     * @return A CompletableFuture that will contain the AI's response parsed into the specified type, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     * @throws UnsupportedOperationException if structured output is not supported by the implementation.
     * @throws AIException if the chat request fails.
     * @see JsonSchemaHelper#buildJsonSchema(Class)
     * @see JsonSchemaHelper#fromJson(String, Class)
     */
    default <T> CompletableFuture<T> chatAsync(String message, ChatOptions options, Class<T> type) throws AIException {
        return chatAsync(message, options.withJsonSchema(buildJsonSchema(type))).thenApply(json -> fromJson(json, type));
    }

    /**
     * Asynchronously sends input to the AI and returns a typed response.
     * <p>
     * This method auto-generates a JSON schema from the given type, instructs the AI to return structured output conforming to that schema, and parses the
     * response back into the specified type.
     * <p>
     * Usage example:
     *
     * <pre>
     * record ProductReview(String sentiment, int rating, List&lt;String&gt; pros, List&lt;String&gt; cons) {
     * }
     *
     * var input = ChatInput.newBuilder()
     *     .message("Analyze this review: " + reviewText)
     *     .attach(imageBytes)
     *     .build();
     * var options = ChatOptions.newBuilder()
     *     .systemPrompt("You are a product review analyzer.")
     *     .build();
     * service.chatAsync(input, options, ProductReview.class).thenAccept(review -&gt; {
     *     // handle typed response
     * }).exceptionally(e -&gt; {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     *
     * @implNote The default implementation generates a JSON schema via {@link JsonSchemaHelper#buildJsonSchema(Class)}, merges it into the options via
     * {@link ChatOptions#withJsonSchema(jakarta.json.JsonObject)}, delegates to {@link #chatAsync(ChatInput, ChatOptions)}, and parses the response via
     * {@link JsonSchemaHelper#fromJson(String, Class)}.
     * @param <T> The target type.
     * @param input The user's input containing message and file attachments.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @param type The target class for the structured response (record or bean).
     * @return A CompletableFuture that will contain the AI's response parsed into the specified type, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     * @throws UnsupportedOperationException if structured output is not supported by the implementation.
     * @throws AIException if the chat request fails.
     * @see JsonSchemaHelper#buildJsonSchema(Class)
     * @see JsonSchemaHelper#fromJson(String, Class)
     */
    default <T> CompletableFuture<T> chatAsync(ChatInput input, ChatOptions options, Class<T> type) throws AIException {
        return chatAsync(input, options.withJsonSchema(buildJsonSchema(type))).thenApply(json -> fromJson(json, type));
    }

    /**
     * Asynchronously sends input to the AI and returns a response.
     * <p>
     * The input contains the user's message and file attachments, while the system prompt in options defines the AI's behavior.
     * <p>
     * Usage example:
     *
     * <pre>
     * var input = ChatInput.newBuilder()
     *     .message(message)
     *     .attach(imageBytes)
     *     .build();
     * service.chatAsync(input, options).thenAccept(response -> {
     *     // handle full response
     * }).exceptionally(e -> {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     * <p>
     * This is the core method for chat-based AI interactions.
     * <p>
     * If the options are {@link ChatOptions#hasMemory() memory-enabled}, the conversation history preceding this input is automatically included in the
     * request, and the history is updated with the user message before the request is sent and with the assistant response upon successful completion.
     * Re-attempting a failed request (e.g. via a resilience decorator) discards the identical user message the failed attempt left behind, so the user message
     * is recorded only once.
     *
     * @param input The user's input containing message and file attachments.
     * @param options Chat options (system prompt, temperature, max tokens, memory, etc.).
     * @return A CompletableFuture that will contain the AI's response, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     * @throws AIException if the chat request fails.
     */
    CompletableFuture<String> chatAsync(ChatInput input, ChatOptions options) throws AIException;

    // Chat Streaming capabilities -------------------------------------------------------------------------------------

    /**
     * Send a message to the AI with default system prompt from {@link #getChatPrompt()} and retrieve an asynchronous stream of tokens.
     * <p>
     * Usage example:
     *
     * <pre>
     * service.chatStream(message, token -> {
     *     // handle partial response
     * }).exceptionally(e -> {
     *     // handle exception
     *     return null;
     * }).thenRun(() -> {
     *     // handle completion
     * });
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatStream(ChatInput, Consumer)}.
     * @param message The user's message to send to the AI.
     * @param onToken The token consumer, this will be invoked for every chat response token in the stream.
     * @return An empty CompletableFuture which only completes when the end of stream is reached, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     * @throws UnsupportedOperationException if chat streaming is not supported by the implementation.
     * @throws AIException if the chat request fails.
     */
    default CompletableFuture<Void> chatStream(String message, Consumer<String> onToken) throws AIException {
        return chatStream(ChatInput.newBuilder().message(message).build(), onToken);
    }

    /**
     * Send input to the AI with default system prompt from {@link #getChatPrompt()} and retrieve an asynchronous stream of tokens.
     * <p>
     * Usage example:
     *
     * <pre>
     * var input = ChatInput.newBuilder()
     *     .message(message)
     *     .attach(imageBytes)
     *     .build();
     * service.chatStream(input, token -> {
     *     // handle partial response
     * }).exceptionally(e -> {
     *     // handle exception
     *     return null;
     * }).thenRun(() -> {
     *     // handle completion
     * });
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatStream(ChatInput, ChatOptions, Consumer)}.
     * @param input The user's input containing message and file attachments.
     * @param onToken The token consumer, this will be invoked for every chat response token in the stream.
     * @return An empty CompletableFuture which only completes when the end of stream is reached, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     * @throws UnsupportedOperationException if chat streaming is not supported by the implementation.
     * @throws AIException if the chat request fails.
     */
    default CompletableFuture<Void> chatStream(ChatInput input, Consumer<String> onToken) throws AIException {
        return chatStream(input, DEFAULT.withSystemPrompt(getChatPrompt()), onToken);
    }

    /**
     * Send a message to the AI and retrieve an asynchronous stream of tokens.
     * <p>
     * The message represents the user's input, while the system prompt in options defines the AI's behavior.
     * <p>
     * Usage example:
     *
     * <pre>
     * service.chatStream(message, options, token -> {
     *     // handle partial response
     * }).exceptionally(e -> {
     *     // handle exception
     *     return null;
     * }).thenRun(() -> {
     *     // handle completion
     * });
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatStream(ChatInput, ChatOptions, Consumer)}.
     * @param message The user's message to send to the AI.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @param onToken The token consumer, this will be invoked for every chat response token in the stream.
     * @return An empty CompletableFuture which only completes when the end of stream is reached, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     * @throws UnsupportedOperationException if chat streaming is not supported by the implementation.
     * @throws AIException if the chat request fails.
     */
    default CompletableFuture<Void> chatStream(String message, ChatOptions options, Consumer<String> onToken) throws AIException {
        return chatStream(ChatInput.newBuilder().message(message).build(), options, onToken);
    }

    /**
     * Send input to the AI and retrieve an asynchronous stream of tokens.
     * <p>
     * The input contains the user's message and file attachments, while the system prompt in options defines the AI's behavior.
     * <p>
     * Usage example:
     *
     * <pre>
     * var input = ChatInput.newBuilder()
     *     .message(message)
     *     .attach(imageBytes)
     *     .build();
     * service.chatStream(input, options, token -> {
     *     // handle partial response
     * }).exceptionally(e -> {
     *     // handle exception
     *     return null;
     * }).thenRun(() -> {
     *     // handle completion
     * });
     * </pre>
     * <p>
     * This is the core method for streaming chat-based AI interactions with images.
     * <p>
     * If the options are {@link ChatOptions#hasMemory() memory-enabled}, the conversation history preceding this input is automatically included in the
     * request, and the history is updated with the user message before the request is sent and with the accumulated assistant response upon successful
     * completion.
     * <p>
     * When {@link ChatOptions#useWebSearch() web search} is enabled, streaming is supported but intermediate tool-use activity (e.g., the model fetching search
     * results) produces no tokens in the {@code onToken} callback. The stream will appear paused during that phase and resume once the model begins writing its
     * response. This behavior is provider-dependent and may vary across AI implementations.
     *
     * @param input The user's input containing message and file attachments.
     * @param options Chat options (system prompt, temperature, max tokens, memory, etc.).
     * @param onToken The token consumer, this will be invoked for every chat response token in the stream.
     * @return An empty CompletableFuture which only completes when the end of stream is reached, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     * @throws UnsupportedOperationException if chat streaming is not supported by the implementation.
     * @throws AIException if the chat request fails.
     */
    CompletableFuture<Void> chatStream(ChatInput input, ChatOptions options, Consumer<String> onToken) throws AIException;

    // File Attachment Capabilities -----------------------------------------------------------------------------------

    /**
     * Uploads a file attachment to the AI provider and retrieves a file ID to attach to chat payload.
     * <p>
     * This is the core method for sending file attachments to AI providers that require file attachments to be uploaded separately before being referenced in
     * chat requests.
     * <p>
     * The implementation should also attempt to ensure that any stale files are cleaned up in case the AI provider does not automatically do that or does not
     * allow configuring that via file upload metadata.
     *
     * @param attachment The file attachment to upload.
     * @param options Chat options (system prompt, temperature, max tokens, memory, etc.).
     * @return The file ID or URI that can be used to reference the uploaded file attachment in subsequent chat requests.
     * @throws UnsupportedOperationException if file upload is not supported by the implementation.
     * @throws AIException if the upload fails.
     */
    String upload(Attachment attachment, ChatOptions options) throws AIException;

    // Text Analysis Capabilities -------------------------------------------------------------------------------------

    /**
     * Summarizes text to given maximum word count.
     *
     * @implNote The default implementation delegates to {@link #summarizeAsync(String, int)}.
     * @param text The text to summarize.
     * @param maxWords Maximum number of words in the summary.
     * @return The summarized text, never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if summarization is not supported by the implementation.
     * @throws AIException if summarization fails.
     */
    default String summarize(String text, int maxWords) throws AIException {
        return joinAsync(summarizeAsync(text, maxWords));
    }

    /**
     * Asynchronously summarizes text to given maximum word count.
     * <p>
     * This is the core method for summarization.
     *
     * @param text The text to summarize.
     * @param maxWords Maximum number of words in the summary.
     * @return A CompletableFuture that will contain the summarized text, never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if summarization is not supported by the implementation.
     */
    CompletableFuture<String> summarizeAsync(String text, int maxWords);

    /**
     * Extracts key points from text as a list.
     *
     * @implNote The default implementation delegates to {@link #extractKeyPointsAsync(String, int)}.
     * @param text The text to extract key points from.
     * @param maxPoints Maximum number of key points to extract.
     * @return List of key points, never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if key point extraction is not supported by the implementation.
     * @throws AIException if extraction fails.
     */
    default List<String> extractKeyPoints(String text, int maxPoints) throws AIException {
        return joinAsync(extractKeyPointsAsync(text, maxPoints));
    }

    /**
     * Asynchronously extracts key points from text as a list.
     * <p>
     * This is the core method for key points extraction.
     *
     * @param text The text to extract key points from.
     * @param maxPoints Maximum number of key points to extract.
     * @return A CompletableFuture that will contain a list of key points, never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if key point extraction is not supported by the implementation.
     * @throws AIException if extraction fails.
     */
    CompletableFuture<List<String>> extractKeyPointsAsync(String text, int maxPoints) throws AIException;

    // Text Translation Capabilities --------------------------------------------------------------------------------------

    /**
     * Detects the language of the given text.
     *
     * @implNote The default implementation delegates to {@link #detectLanguageAsync(String)}.
     * @param text The text to analyze.
     * @return The detected language code (ISO 639-1), never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if language detection is not supported by the implementation.
     * @throws AIException if language detection fails.
     */
    default String detectLanguage(String text) throws AIException {
        return joinAsync(detectLanguageAsync(text));
    }

    /**
     * Asynchronously detects the language of the given text.
     * <p>
     * This is the core method for language detection.
     *
     * @param text The text to analyze.
     * @return A CompletableFuture that will contain the detected language code (ISO 639-1), never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if language detection is not supported by the implementation.
     * @throws AIException if language detection fails.
     */
    CompletableFuture<String> detectLanguageAsync(String text) throws AIException;

    /**
     * Translates text from source language to target language while preserving any markup and placeholders.
     *
     * @implNote The default implementation delegates to {@link #translateAsync(String, String, String)}.
     * @param text The text to translate.
     * @param sourceLang Source language code (ISO 639-1), or {@code null} for auto-detection.
     * @param targetLang Target language code (ISO 639-1).
     * @return The translated text, never {@code null}.
     * @throws IllegalArgumentException if text or targetLang is blank.
     * @throws UnsupportedOperationException if translation is not supported by the implementation.
     * @throws AIException if translation fails.
     */
    default String translate(String text, String sourceLang, String targetLang) throws AIException {
        return joinAsync(translateAsync(text, sourceLang, targetLang));
    }

    /**
     * Asynchronously translates text from source language to target language while preserving any markup and placeholders.
     * <p>
     * This is the core method for translation.
     *
     * @param text The text to translate.
     * @param sourceLang Source language code (ISO 639-1), or {@code null} for auto-detection.
     * @param targetLang Target language code (ISO 639-1).
     * @return A CompletableFuture that will contain the translated text, never {@code null}.
     * @throws IllegalArgumentException if text or targetLang is blank.
     * @throws UnsupportedOperationException if translation is not supported by the implementation.
     * @throws AIException if translation fails.
     */
    CompletableFuture<String> translateAsync(String text, String sourceLang, String targetLang) throws AIException;

    // Text Proofreading Capabilities ---------------------------------------------------------------------------------

    /**
     * Proofreads text by correcting grammar and spelling errors while preserving the original meaning, tone, and style.
     *
     * @implNote The default implementation delegates to {@link #proofreadAsync(String)}.
     * @param text The text to proofread.
     * @return The proofread text with corrections applied, never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if proofreading is not supported by the implementation.
     * @throws AIException if proofreading fails.
     */
    default String proofread(String text) throws AIException {
        return joinAsync(proofreadAsync(text));
    }

    /**
     * Asynchronously proofreads text by correcting grammar and spelling errors while preserving the original meaning, tone, and style.
     * <p>
     * This is the core method for proofreading.
     *
     * @param text The text to proofread.
     * @return A CompletableFuture that will contain the proofread text with corrections applied, never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if proofreading is not supported by the implementation.
     * @throws AIException if proofreading fails.
     */
    CompletableFuture<String> proofreadAsync(String text) throws AIException;

    // Text Moderation Capabilities -----------------------------------------------------------------------------------

    /**
     * Moderates content with default options.
     *
     * @implNote The default implementation delegates to {@link #moderateContentAsync(String)}.
     * @param content The content to moderate.
     * @return Moderation result with detected violations, never {@code null}.
     * @throws IllegalArgumentException if content is blank.
     * @throws UnsupportedOperationException if content moderation is not supported by the implementation.
     * @throws AIException if moderation fails.
     */
    default ModerationResult moderateContent(String content) throws AIException {
        return joinAsync(moderateContentAsync(content));
    }

    /**
     * Asynchronously moderates content with default options.
     *
     * @implNote The default implementation delegates to {@link #moderateContentAsync(String)} with {@link ModerationOptions#DEFAULT}.
     * @param content The content to moderate.
     * @return A CompletableFuture that will contain the moderation result with detected violations, never {@code null}.
     * @throws IllegalArgumentException if content is blank.
     * @throws UnsupportedOperationException if content moderation is not supported by the implementation.
     * @throws AIException if moderation fails.
     */
    default CompletableFuture<ModerationResult> moderateContentAsync(String content) throws AIException {
        return moderateContentAsync(content, ModerationOptions.DEFAULT);
    }

    /**
     * Moderates content to detect violations per {@link Category}.
     *
     * @implNote The default implementation delegates to {@link #moderateContentAsync(String, ModerationOptions)}.
     * @param content The content to moderate.
     * @param options Moderation options (categories to check, threshold, etc.).
     * @return Moderation result with detected violations, never {@code null}.
     * @throws IllegalArgumentException if content is blank.
     * @throws UnsupportedOperationException if content moderation is not supported by the implementation.
     * @throws AIException if moderation fails.
     */
    default ModerationResult moderateContent(String content, ModerationOptions options) throws AIException {
        return joinAsync(moderateContentAsync(content, options));
    }

    /**
     * Asynchronously moderates content to detect violations per {@link Category}.
     * <p>
     * This is the core method for content moderation.
     *
     * @param content The content to moderate.
     * @param options Moderation options (categories to check, threshold, etc.).
     * @return A CompletableFuture that will contain the moderation result with detected violations, never {@code null}.
     * @throws IllegalArgumentException if content is blank.
     * @throws UnsupportedOperationException if content moderation is not supported by the implementation.
     * @throws AIException if moderation fails.
     */
    CompletableFuture<ModerationResult> moderateContentAsync(String content, ModerationOptions options) throws AIException;

    // Web Search Capabilities ----------------------------------------------------------------------------------------

    /**
     * Sends a web search query to the AI and returns a response.
     * <p>
     * Useful if you need the model to access up-to-date information from the internet.
     * <p>
     * Usage example:
     *
     * <pre>
     *
     * var response = service.webSearch("What is the latest news about Jakarta EE?");
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #webSearchAsync(String)}.
     * @param query The user's web search query to send to the AI.
     * @return The AI's response, never {@code null}.
     * @throws IllegalArgumentException if query is blank.
     * @throws AIException if the web search request fails.
     * @since 1.3
     */
    default String webSearch(String query) throws AIException {
        return joinAsync(webSearchAsync(query));
    }

    /**
     * Sends a web search query with location context to the AI and returns a response.
     * <p>
     * Usage example:
     *
     * <pre>
     *
     * var miami = new Location("US", "Florida", "Miami");
     * var weather = service.webSearch("What is the weather like?", miami);
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #webSearchAsync(String, Location)}.
     * @param query The user's web search query to send to the AI.
     * @param location The location context for web search, or {@link Location#GLOBAL} for global search.
     * @return The AI's response parsed into the specified type, never {@code null}.
     * @throws IllegalArgumentException if query is blank.
     * @throws AIException if the web search request fails.
     * @since 1.3
     */
    default String webSearch(String query, Location location) throws AIException {
        return joinAsync(webSearchAsync(query, location));
    }

    /**
     * Sends a web search query to the AI and returns a typed response.
     * <p>
     * This method auto-generates a JSON schema from the given type, instructs the AI to return structured output conforming to that schema, and parses the
     * response back into the specified type.
     * <p>
     * Usage example:
     *
     * <pre>
     *
     * record StockPrice(String ticker, BigDecimal price, String currencyCode) {
     * }
     *
     * var price = service.webSearch("What is the current stock price of: " + companyName, StockPrice.class);
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #webSearchAsync(String, Class)}.
     * @param <T> The target type.
     * @param query The user's web search query to send to the AI.
     * @param type The target class for the structured response (record or bean).
     * @return The AI's response parsed into the specified type, never {@code null}.
     * @throws IllegalArgumentException if query is blank.
     * @throws UnsupportedOperationException if structured output is not supported by the implementation.
     * @throws AIException if the web search request fails.
     * @see JsonSchemaHelper#buildJsonSchema(Class)
     * @see JsonSchemaHelper#fromJson(String, Class)
     * @since 1.3
     */
    default <T> T webSearch(String query, Class<T> type) throws AIException {
        return joinAsync(webSearchAsync(query, type));
    }

    /**
     * Sends a web search query with location context to the AI and returns a typed response.
     * <p>
     * This method auto-generates a JSON schema from the given type, instructs the AI to return structured output conforming to that schema, and parses the
     * response back into the specified type.
     * <p>
     * Usage example:
     *
     * <pre>
     *
     * record WeatherForecast(String summary, int highCelsius, int lowCelsius, String precipitation) {
     * }
     *
     * var miami = new Location("US", "Florida", "Miami");
     * var forecast = service.webSearch("What is the weather forecast for this weekend?", miami, WeatherForecast.class);
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #webSearchAsync(String, Location, Class)}.
     * @param <T> The target type.
     * @param query The user's web search query to send to the AI.
     * @param location The location context for web search, or {@link Location#GLOBAL} for global search.
     * @param type The target class for the structured response (record or bean).
     * @return The AI's response parsed into the specified type, never {@code null}.
     * @throws IllegalArgumentException if query is blank.
     * @throws UnsupportedOperationException if structured output is not supported by the implementation.
     * @throws AIException if the web search request fails.
     * @see JsonSchemaHelper#buildJsonSchema(Class)
     * @see JsonSchemaHelper#fromJson(String, Class)
     * @since 1.3
     */
    default <T> T webSearch(String query, Location location, Class<T> type) throws AIException {
        return joinAsync(webSearchAsync(query, location, type));
    }

    /**
     * Asynchronously sends a web search query to the AI and returns a response.
     * <p>
     * Useful if you need the model to access up-to-date information from the internet.
     * <p>
     * Usage example:
     *
     * <pre>
     * service.webSearchAsync("What is the latest news about Jakarta EE?").thenAccept(response -&gt; {
     *     // handle response
     * }).exceptionally(e -&gt; {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #webSearchAsync(String, Location)} with {@link Location#GLOBAL}.
     * @param query The user's web search query to send to the AI.
     * @return A CompletableFuture that will contain the AI's response, never {@code null}.
     * @throws IllegalArgumentException if query is blank.
     * @throws AIException if the web search request fails.
     * @since 1.3
     */
    default CompletableFuture<String> webSearchAsync(String query) throws AIException {
        return webSearchAsync(query, GLOBAL);
    }

    /**
     * Asynchronously sends a web search query with location context to the AI and returns a response.
     * <p>
     * Usage example:
     *
     * <pre>
     * var miami = new Location("US", "Florida", "Miami");
     * service.webSearchAsync("What is the weather like?", miami).thenAccept(response -&gt; {
     *     // handle response
     * }).exceptionally(e -&gt; {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatAsync(String, ChatOptions)} with {@link ChatOptions#withWebSearch(Location)} on the given
     * location.
     * @param query The user's web search query to send to the AI.
     * @param location The location context for web search, or {@link Location#GLOBAL} for global search.
     * @return A CompletableFuture that will contain the AI's response parsed into the specified type, never {@code null}.
     * @throws IllegalArgumentException if query is blank.
     * @throws AIException if the web search request fails.
     * @since 1.3
     */
    default CompletableFuture<String> webSearchAsync(String query, Location location) throws AIException {
        return chatAsync(query, DEFAULT.withWebSearch(requireNonNull(location, "location")));
    }

    /**
     * Asynchronously sends a web search query to the AI and returns a typed response.
     * <p>
     * This method auto-generates a JSON schema from the given type, instructs the AI to return structured output conforming to that schema, and parses the
     * response back into the specified type.
     * <p>
     * Usage example:
     *
     * <pre>
     * record StockPrice(String ticker, BigDecimal price, String currencyCode) {
     * }
     *
     * service.webSearchAsync("What is the current stock price of: " + companyName, StockPrice.class).thenAccept(price -&gt; {
     *     // handle typed response
     * }).exceptionally(e -&gt; {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #webSearchAsync(String, Location, Class)} with {@link ChatOptions#DEFAULT}.
     * @param <T> The target type.
     * @param query The user's web search query to send to the AI.
     * @param type The target class for the structured response (record or bean).
     * @return A CompletableFuture that will contain the AI's response parsed into the specified type, never {@code null}.
     * @throws IllegalArgumentException if query is blank.
     * @throws UnsupportedOperationException if structured output is not supported by the implementation.
     * @throws AIException if the web search request fails.
     * @see JsonSchemaHelper#buildJsonSchema(Class)
     * @see JsonSchemaHelper#fromJson(String, Class)
     * @since 1.3
     */
    default <T> CompletableFuture<T> webSearchAsync(String query, Class<T> type) throws AIException {
        return webSearchAsync(query, GLOBAL, type);
    }

    /**
     * Asynchronously sends a web search query with location context to the AI and returns a typed response.
     * <p>
     * This method auto-generates a JSON schema from the given type, instructs the AI to return structured output conforming to that schema, and parses the
     * response back into the specified type.
     * <p>
     * Usage example:
     *
     * <pre>
     * record WeatherForecast(String summary, int highCelsius, int lowCelsius, String precipitation) {
     * }
     *
     * var miami = new Location("US", "Florida", "Miami");
     * service.webSearchAsync("What is the weather forecast for this weekend?", miami, WeatherForecast.class).thenAccept(forecast -&gt; {
     *     // handle typed response
     * }).exceptionally(e -&gt; {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     *
     * @implNote The default implementation delegates to {@link #chatAsync(String, ChatOptions)} with {@link ChatOptions#withWebSearch(Location)} on the given
     * location and generates a JSON schema via {@link JsonSchemaHelper#buildJsonSchema(Class)} which is merged into the options via
     * {@link ChatOptions#withJsonSchema(jakarta.json.JsonObject)}.
     * @param <T> The target type.
     * @param query The user's web search query to send to the AI.
     * @param location The location context for web search, or {@link Location#GLOBAL} for global search.
     * @param type The target class for the structured response (record or bean).
     * @return A CompletableFuture that will contain the AI's response parsed into the specified type, never {@code null}.
     * @throws IllegalArgumentException if query is blank.
     * @throws UnsupportedOperationException if structured output is not supported by the implementation.
     * @throws AIException if the web search request fails.
     * @see JsonSchemaHelper#buildJsonSchema(Class)
     * @see JsonSchemaHelper#fromJson(String, Class)
     * @since 1.3
     */
    default <T> CompletableFuture<T> webSearchAsync(String query, Location location, Class<T> type) throws AIException {
        return chatAsync(query, DEFAULT.withWebSearch(requireNonNull(location, "location")).withJsonSchema(buildJsonSchema(type)))
            .thenApply(json -> fromJson(json, type));
    }

    // Image Analysis Capabilities ------------------------------------------------------------------------------------

    /**
     * Analyzes an image and generates a description based on the given prompt.
     * <p>
     * Useful for generating alt text for accessibility, extracting information from images, or describing visual content.
     *
     * @implNote The default implementation delegates to {@link #analyzeImageAsync(byte[], String)}.
     * @param image The image bytes to analyze.
     * @param prompt The prompt describing what to focus on (e.g., "describe the product", "what's the main subject"), or {@code null} for a general
     * description.
     * @return Description of the image, never {@code null}.
     * @throws IllegalArgumentException if prompt is blank.
     * @throws UnsupportedOperationException if image analysis is not supported by the implementation.
     * @throws AIException if image analysis fails.
     */
    default String analyzeImage(byte[] image, String prompt) throws AIException {
        return joinAsync(analyzeImageAsync(image, prompt));
    }

    /**
     * Asynchronously analyzes an image and generates a description based on the given prompt.
     * <p>
     * Useful for generating alt text for accessibility, extracting information from images, or describing visual content.
     * <p>
     * This is the core method for image analysis.
     *
     * @param image The image bytes to analyze.
     * @param prompt The prompt describing what to focus on (e.g., "describe the product", "what's the main subject"), or {@code null} for a general
     * description.
     * @return A CompletableFuture that will contain the description of the image, never {@code null}.
     * @throws IllegalArgumentException if prompt is blank.
     * @throws UnsupportedOperationException if image analysis is not supported by the implementation.
     * @throws AIException if image analysis fails.
     */
    CompletableFuture<String> analyzeImageAsync(byte[] image, String prompt) throws AIException;

    /**
     * Generates alt text for an image suitable for accessibility purposes.
     *
     * @implNote The default implementation delegates to {@link #generateAltTextAsync(byte[])}.
     * @param image The image bytes to analyze.
     * @return Alt text description, never {@code null}.
     * @throws UnsupportedOperationException if image analysis is not supported by the implementation.
     * @throws AIException if image analysis fails.
     */
    default String generateAltText(byte[] image) throws AIException {
        return joinAsync(generateAltTextAsync(image));
    }

    /**
     * Asynchronously generates alt text for an image suitable for accessibility purposes.
     * <p>
     * This is the core method for image alt text generation.
     *
     * @param image The image bytes to analyze.
     * @return A CompletableFuture that will contain the alt text description, never {@code null}.
     * @throws UnsupportedOperationException if image analysis is not supported by the implementation.
     * @throws AIException if image analysis fails.
     */
    CompletableFuture<String> generateAltTextAsync(byte[] image) throws AIException;

    // Image Generation Capabilities ----------------------------------------------------------------------------------

    /**
     * Generates an image based on a text prompt with default options.
     *
     * @implNote The default implementation delegates to {@link #generateImageAsync(String)}.
     * @param prompt The text prompt describing the image to generate.
     * @return Generated image bytes, never {@code null}.
     * @throws IllegalArgumentException if prompt is blank.
     * @throws UnsupportedOperationException if image generation is not supported by the implementation.
     * @throws AIException if image generation fails.
     * @see #generateImage(String, GenerateImageOptions)
     * @see GenerateImageOptions#DEFAULT
     */
    default byte[] generateImage(String prompt) throws AIException {
        return joinAsync(generateImageAsync(prompt));
    }

    /**
     * Asynchronously generates an image based on a text prompt with default options.
     *
     * @implNote The default implementation delegates to {@link #generateImageAsync(String, GenerateImageOptions)} with {@link GenerateImageOptions#DEFAULT}.
     * @param prompt The text prompt describing the image to generate.
     * @return A CompletableFuture that will contain the generated image bytes, never {@code null}.
     * @throws IllegalArgumentException if prompt is blank.
     * @throws UnsupportedOperationException if image generation is not supported by the implementation.
     * @throws AIException if image generation fails.
     */
    default CompletableFuture<byte[]> generateImageAsync(String prompt) throws AIException {
        return generateImageAsync(prompt, GenerateImageOptions.DEFAULT);
    }

    /**
     * Generates an image based on a text prompt.
     *
     * @implNote The default implementation delegates to {@link #generateImageAsync(String, GenerateImageOptions)}.
     * @param prompt The text prompt describing the image to generate.
     * @param options Image generation options (size, quality, style, etc.).
     * @return Generated image bytes, never {@code null}.
     * @throws IllegalArgumentException if prompt is blank.
     * @throws UnsupportedOperationException if image generation is not supported by the implementation.
     * @throws AIException if image generation fails.
     */
    default byte[] generateImage(String prompt, GenerateImageOptions options) throws AIException {
        return joinAsync(generateImageAsync(prompt, options));
    }

    /**
     * Asynchronously generates an image based on a text prompt.
     * <p>
     * This is the core method for image generation.
     *
     * @param prompt The text prompt describing the image to generate.
     * @param options Image generation options (size, quality, style, etc.).
     * @return A CompletableFuture that will contain the generated image bytes, never {@code null}.
     * @throws IllegalArgumentException if prompt is blank.
     * @throws UnsupportedOperationException if image generation is not supported by the implementation.
     * @throws AIException if image generation fails.
     */
    CompletableFuture<byte[]> generateImageAsync(String prompt, GenerateImageOptions options) throws AIException;

    // Audio Transcription Capabilities ---------------------------------------------------------------------------------

    /**
     * Transcribes audio to text.
     *
     * @implNote The default implementation delegates to {@link #transcribeAsync(byte[])}.
     * @param audio The audio bytes to transcribe.
     * @return The transcription text, never {@code null}.
     * @throws UnsupportedOperationException if audio transcription is not supported by the implementation.
     * @throws AIException if transcription fails.
     * @since 1.1
     */
    default String transcribe(byte[] audio) throws AIException {
        return joinAsync(transcribeAsync(audio));
    }

    /**
     * Transcribes audio to text.
     *
     * @implNote The default implementation delegates to {@link #transcribeAsync(Path)}.
     * @param audio The audio source to transcribe.
     * @return The transcription text, never {@code null}.
     * @throws UnsupportedOperationException if audio transcription is not supported by the implementation.
     * @throws AIException if transcription fails.
     * @since 1.2
     */
    default String transcribe(Path audio) throws AIException {
        return joinAsync(transcribeAsync(audio));
    }

    /**
     * Asynchronously transcribes audio to text.
     * <p>
     * This is the core method for in-memory audio transcription.
     *
     * @param audio The audio bytes to transcribe.
     * @return A CompletableFuture that will contain the transcription text, never {@code null}.
     * @throws UnsupportedOperationException if audio transcription is not supported by the implementation.
     * @throws AIException if transcription fails.
     * @since 1.1
     */
    CompletableFuture<String> transcribeAsync(byte[] audio) throws AIException;

    /**
     * Asynchronously transcribes audio to text.
     * <p>
     * This is the core method for streaming audio transcription.
     *
     * @param audio The audio source to transcribe.
     * @return A CompletableFuture that will contain the transcription text, never {@code null}.
     * @throws UnsupportedOperationException if audio transcription is not supported by the implementation.
     * @throws AIException if transcription fails.
     * @since 1.2
     */
    CompletableFuture<String> transcribeAsync(Path audio) throws AIException;

    // Audio generation capabilities ----------------------------------------------------------------------------------

    /**
     * Generates audio from text and returns it as a byte array.
     *
     * @implNote The default implementation delegates to {@link #generateAudioAsync(String)}.
     * @param text The text to convert to audio.
     * @return Generated audio bytes, never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if audio generation is not supported by the implementation.
     * @throws AIException if audio generation fails.
     * @since 1.2
     */
    default byte[] generateAudio(String text) throws AIException {
        return joinAsync(generateAudioAsync(text));
    }

    /**
     * Generates audio from text and returns it as a byte array.
     *
     * @implNote The default implementation delegates to {@link #generateAudioAsync(String, GenerateAudioOptions)}.
     * @param text The text to convert to audio.
     * @param options Audio generation options (voice, speed, output format, etc.).
     * @return Generated audio bytes, never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if audio generation is not supported by the implementation.
     * @throws AIException if audio generation fails.
     * @since 1.2
     */
    default byte[] generateAudio(String text, GenerateAudioOptions options) throws AIException {
        return joinAsync(generateAudioAsync(text, options));
    }

    /**
     * Generates audio from text and saves it to the specified path.
     *
     * @implNote The default implementation delegates to {@link #generateAudioAsync(String, Path)}.
     * @param text The text to convert to audio.
     * @param path The path where the audio file should be saved.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if audio generation is not supported by the implementation.
     * @throws AIException if audio generation fails.
     * @since 1.2
     */
    default void generateAudio(String text, Path path) throws AIException {
        joinAsync(generateAudioAsync(text, path));
    }

    /**
     * Generates audio from text and saves it to the specified path.
     *
     * @implNote The default implementation delegates to {@link #generateAudioAsync(String, Path, GenerateAudioOptions)}.
     * @param text The text to convert to audio.
     * @param path The path where the audio file should be saved.
     * @param options Audio generation options (voice, speed, output format, etc.).
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if audio generation is not supported by the implementation.
     * @throws AIException if audio generation fails.
     * @since 1.2
     */
    default void generateAudio(String text, Path path, GenerateAudioOptions options) throws AIException {
        joinAsync(generateAudioAsync(text, path, options));
    }

    /**
     * Asynchronously generates audio from text and returns it as a byte array.
     *
     * @implNote The default implementation delegates to {@link #generateAudioAsync(String, GenerateAudioOptions)} with {@link GenerateAudioOptions#DEFAULT}.
     * @param text The text to convert to audio.
     * @return A CompletableFuture that will contain the generated audio bytes, never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if audio generation is not supported by the implementation.
     * @throws AIException if audio generation fails.
     * @since 1.2
     */
    default CompletableFuture<byte[]> generateAudioAsync(String text) throws AIException {
        return generateAudioAsync(text, GenerateAudioOptions.DEFAULT);
    }

    /**
     * Asynchronously generates audio from text and saves it to the specified path.
     *
     * @implNote The default implementation delegates to {@link #generateAudioAsync(String, Path, GenerateAudioOptions)} with
     * {@link GenerateAudioOptions#DEFAULT}.
     * @param text The text to convert to audio.
     * @param path The path where the audio file should be saved.
     * @return An empty CompletableFuture which only completes when the end of stream is reached, never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if audio generation is not supported by the implementation.
     * @throws AIException if audio generation fails.
     * @since 1.2
     */
    default CompletableFuture<Void> generateAudioAsync(String text, Path path) throws AIException {
        return generateAudioAsync(text, path, GenerateAudioOptions.DEFAULT);
    }

    /**
     * Asynchronously generates audio from text and returns it as a byte array.
     * <p>
     * This is the core method for audio generation returning a byte array.
     *
     * @param text The text to convert to audio.
     * @param options Audio generation options (voice, speed, output format, etc.).
     * @return A CompletableFuture that will contain the generated audio bytes, never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if audio generation is not supported by the implementation.
     * @throws AIException if audio generation fails.
     * @since 1.2
     */
    CompletableFuture<byte[]> generateAudioAsync(String text, GenerateAudioOptions options) throws AIException;

    /**
     * Asynchronously generates audio from text and saves it to the specified path.
     * <p>
     * This is the core method for streaming audio generation to a file.
     *
     * @param text The text to convert to audio.
     * @param path The path where the audio file should be saved.
     * @param options Audio generation options (voice, speed, output format, etc.).
     * @return An empty CompletableFuture which only completes when the end of stream is reached, never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if audio generation is not supported by the implementation.
     * @throws AIException if audio generation fails.
     * @since 1.2
     */
    CompletableFuture<Void> generateAudioAsync(String text, Path path, GenerateAudioOptions options) throws AIException;

    // Service Metadata -----------------------------------------------------------------------------------------------

    /**
     * Returns the name of this AI service.
     *
     * @return The name of this AI service (e.g. AnthropicAIService (Anthropic claude-sonnet-4-5-20250929)).
     */
    default String getName() {
        return getClass().getSimpleName() + " (" + getProviderName() + " " + getModelName() + ")";
    }

    // Tool calling capabilities ---------------------------------------------------------------------------------------

    /**
     * Returns a view on this service which lets the AI call the {@link AITool} annotated methods of the given objects before it answers.
     * <p>
     * Usage example:
     *
     * <pre>
     *
     * AIService agent = ai.withTools(orderTools);
     * String answer = agent.chat("Where is order 42?");
     * </pre>
     * <p>
     * There is no classpath scanning: only the methods of the objects passed here are offered to the AI.
     *
     * @param instances The objects declaring {@link AITool} annotated methods.
     * @return A view on this service which offers the given tools to the AI, never {@code null}.
     * @throws IllegalArgumentException If none of the given objects declares an {@link AITool} annotated method, or if two of them declare the same tool name.
     * @since 1.6
     * @see AITool
     * @see ToolCallingAIService
     */
    default AIService withTools(Object... instances) {
        return new ToolCallingAIService(this, ToolRegistry.of(instances));
    }

    /**
     * Returns a view on this service which lets the AI call the {@link AITool} annotated methods of the given objects which are tagged with the given group.
     * <p>
     * Usage example:
     *
     * <pre>
     *
     * AIService tier1 = ai.withTools(ReadOnly.class, supportTools);
     * </pre>
     * <p>
     * The group narrows the generated response schema rather than filtering afterwards, so the omitted tools are unreachable rather than merely unadvertised.
     *
     * @param group The tool group to narrow to, itself annotated with {@link org.omnifaces.ai.tool.AIToolGroup}.
     * @param instances The objects declaring {@link AITool} annotated methods.
     * @return A view on this service which offers the given tools to the AI, never {@code null}.
     * @throws IllegalArgumentException If the group is not itself annotated with {@link org.omnifaces.ai.tool.AIToolGroup}, if none of the given objects
     * declares a matching {@link AITool} annotated method, or if two of them declare the same tool name.
     * @since 1.6
     * @see org.omnifaces.ai.tool.AIToolGroup
     */
    default AIService withTools(Class<? extends Annotation> group, Object... instances) {
        return new ToolCallingAIService(this, ToolRegistry.of(group, instances));
    }

    /**
     * Returns a view on this service which offers the given tools to the AI before it answers.
     * <p>
     * Use this when the tools are declared programmatically rather than by annotation, or when both are mixed:
     *
     * <pre>
     *
     * ToolRegistry tools = ToolRegistry.newBuilder()
     *     .add("FIND_ORDER", "Looks up a single order by id", orders::findById, ToolParam.of(long.class, "orderId", "The order id"))
     *     .add(shippingTools)
     *     .build();
     *
     * AIService agent = ai.withTools(tools);
     * </pre>
     *
     * @param tools The tools to offer the AI.
     * @return A view on this service which offers the given tools to the AI, never {@code null}.
     * @since 1.6
     * @see ToolRegistry#newBuilder()
     */
    default AIService withTools(ToolRegistry tools) {
        return new ToolCallingAIService(this, tools);
    }

    /**
     * Returns the AI provider name of this AI service.
     *
     * @return The AI provider name of this AI service (e.g., "OpenAI", "Anthropic", "Google AI", etc).
     */
    String getProviderName();

    /**
     * Returns the (full) AI model name being used by this AI service.
     *
     * @return The (full) AI model name being used by this AI service (e.g., "gpt-5-mini", "claude-sonnet-4-5-20250929", "gemini-2.5-flash", etc)
     */
    String getModelName();

    /**
     * Returns the chat prompt being used by this AI service.
     *
     * @return The chat prompt being used by this AI service.
     */
    String getChatPrompt();

    /**
     * Returns the AI model version information for this AI service. The version is extracted from {@link #getModelName()} and includes the model name prefix,
     * major version, and minor version.
     *
     * @return The AI model version for this AI service.
     * @see AIModelVersion#of(AIService)
     */
    default AIModelVersion getModelVersion() {
        return AIModelVersion.of(this);
    }

    /**
     * Returns whether this AI service implementation supports chat streaming via SSE.
     *
     * @implNote The default implementation returns false.
     * @return Whether this AI service implementation supports chat streaming via SSE.
     */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * Returns whether this AI service implementation supports file attachments for chat.
     *
     * @implNote The default implementation returns false.
     * @return Whether this AI service implementation supports file attachments for chat.
     */
    default boolean supportsFileAttachments() {
        return false;
    }

    /**
     * Returns whether this AI service renders the files uploaded in earlier turns again when it replays a memory-enabled conversation's history, so that a
     * caller need not attach them anew on every turn. Not to be confused with {@link #supportsFileAttachments()}, which is about the current turn.
     *
     * @implNote The default implementation returns false.
     * @return Whether this AI service replays uploaded files from the conversation history.
     * @since 1.6
     */
    default boolean supportsFileAttachmentsInHistory() {
        return false;
    }

    /**
     * Returns whether this AI service implementation supports structured (JSON schema) outputs.
     *
     * @implNote The default implementation returns false.
     * @return Whether this AI service implementation supports structured (JSON schema) outputs.
     */
    default boolean supportsStructuredOutput() {
        return false;
    }

    /**
     * Returns whether this AI service implementation supports web search.
     *
     * @implNote The default implementation returns false.
     * @return Whether this AI service implementation supports web search.
     * @since 1.3
     */
    default boolean supportsWebSearch() {
        return false;
    }

    /**
     * Returns whether this AI service implementation honors the {@link org.omnifaces.ai.model.ChatOptions#getReasoningEffort() reasoning effort} configured on
     * {@link org.omnifaces.ai.model.ChatOptions}. When {@code true}, the provider-specific handler translates the requested
     * {@link org.omnifaces.ai.model.ChatOptions.ReasoningEffort} to the provider's native reasoning/thinking payload (e.g. OpenAI
     * {@code reasoning_effort}/{@code reasoning.effort}, Anthropic {@code thinking.budget_tokens}, Google {@code thinkingConfig.thinkingLevel}). When
     * {@code false}, the configured reasoning effort is silently ignored and the provider's own default reasoning behavior applies.
     *
     * @implNote The default implementation returns false.
     * @return Whether this AI service implementation honors the configured reasoning effort.
     * @since 1.4
     * @see org.omnifaces.ai.model.ChatOptions.ReasoningEffort
     */
    default boolean supportsReasoningEffort() {
        return false;
    }

    /**
     * Checks whether this AI service implementation accepts sampling parameters such as {@code temperature} and {@code top_p}, which is usually determined by
     * {@link #getModelName()} or {@link #getModelVersion()}. When {@code false}, the provider-specific handler omits these from the request payload, as some
     * newer models reject them (e.g. they return an HTTP 400 for a non-default {@code temperature}) in favor of prompt-based steering.
     *
     * @implNote The default implementation returns true.
     * @return Whether this AI service implementation accepts sampling parameters.
     * @since 1.5
     */
    default boolean supportsSamplingParameters() {
        return true;
    }

    /**
     * Checks whether the given modality is supported by this AI service, which is usually determined by {@link #getModelName()} or {@link #getModelVersion()}.
     * <p>
     * <strong>Important:</strong> This method provides a <em>hint</em> rather than a strict guarantee. Implementations are <em>not required</em> to enforce
     * this check before performing operations such as {@link #analyzeImage(byte[], String)}, {@link #generateImage(String)}, or other modality-specific calls.
     * <p>
     * The primary purposes of this method are to allow callers to:
     * <ul>
     * <li>skip unnecessary API calls when a modality is known to be unsupported,</li>
     * <li>improve user experience (e.g., disable buttons or hide options in the UI),</li>
     * <li>dynamically select a fallback provider or model at runtime.</li>
     * </ul>
     * <p>
     * If a modality-specific operation is invoked despite {@code supports(AIModality)} returning {@code false}, the implementation <strong>may</strong> fail
     * fast, typically by throwing {@code UnsupportedOperationException} or a provider-specific exception.
     *
     * @param modality The modality (input analysis or output generation type) to check
     * @return {@code true} If this service is expected to support the given modality with the current model and configuration, {@code false} otherwise.
     * @see AIModality
     */
    boolean supportsModality(AIModality modality);

    // Private Helpers ------------------------------------------------------------------------------------------------

    /**
     * Joins a {@link CompletableFuture}, unwrapping any {@link CompletionException} into the appropriate {@link AIException} subclass.
     */
    private static <T> T joinAsync(CompletableFuture<T> future) throws AIException {
        try {
            return future.join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
    }

}
