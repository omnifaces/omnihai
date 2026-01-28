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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;

import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.GenerateImageOptions;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.ModerationOptions.Category;
import org.omnifaces.ai.model.ModerationResult;

/**
 * Generic interface for AI service providers.
 * <p>
 * This interface provides a unified abstraction for various AI capabilities including
 * chat, summarization, translation, content moderation, image analysis, and more.
 * <p>
 * The implementations must be stateless and able to be {@link ApplicationScoped}.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider
 * @see AIConfig
 * @see AIModality
 */
public interface AIService extends Serializable {

    // Chat Capabilities ----------------------------------------------------------------------------------------------

    /**
     * Sends a message to the AI with default system prompt from {@link #getChatPrompt()}.
     * <p>
     * Usage example:
     * <pre>
     * try {
     *     var response = service.chat(message);
     *     // handle full response
     * } catch (Exception e) {
     *     // handle exception
     * }
     * </pre>
     * <p>
     * The default implementation delegates to {@link #chatAsync(String)}.
     *
     * @param message The user's message to send to the AI.
     * @return The AI's response, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     * @throws AIException if the chat request fails.
     */
    default String chat(String message) throws AIException {
        try {
            return chatAsync(message).join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
    }

    /**
     * Sends input to the AI with default system prompt from {@link #getChatPrompt()}.
     * <p>
     * Usage example:
     * <pre>
     * try {
     *     var input = ChatInput.newBuilder()
     *         .message(message)
     *         .images(imageBytes)
     *         .build();
     *     var response = service.chat(input);
     *     // handle full response
     * } catch (Exception e) {
     *     // handle exception
     * }
     * </pre>
     * <p>
     * The default implementation delegates to {@link #chatAsync(ChatInput)}.
     *
     * @param input The user's input containing message and optional images.
     * @return The AI's response, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     * @throws AIException if the chat request fails.
     */
    default String chat(ChatInput input) throws AIException {
        try {
            return chatAsync(input).join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
    }

    /**
     * Sends a message to the AI and returns a response.
     * <p>
     * The message represents the user's input, while the system prompt in options defines the AI's behavior.
     * <p>
     * Usage example:
     * <pre>
     * try {
     *     var response = service.chat(message, options);
     *     // handle full response
     * } catch (Exception e) {
     *     // handle exception
     * }
     * </pre>
     * <p>
     * The default implementation delegates to {@link #chatAsync(String, ChatOptions)}.
     *
     * @param message The user's message to send to the AI.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @return The AI's response, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     * @throws AIException if the chat request fails.
     */
    default String chat(String message, ChatOptions options) throws AIException {
        try {
            return chatAsync(message, options).join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
    }

    /**
     * Sends input to the AI and returns a response.
     * <p>
     * The input contains the user's message and optional images, while the system prompt in options defines the AI's behavior.
     * <p>
     * Usage example:
     * <pre>
     * try {
     *     var input = ChatInput.newBuilder()
     *         .message(message)
     *         .images(imageBytes)
     *         .build();
     *     var response = service.chat(input, options);
     *     // handle full response
     * } catch (Exception e) {
     *     // handle exception
     * }
     * </pre>
     * <p>
     * The default implementation delegates to {@link #chatAsync(ChatInput, ChatOptions)}.
     *
     * @param input The user's input containing message and optional images.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @return The AI's response, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     * @throws AIException if the chat request fails.
     */
    default String chat(ChatInput input, ChatOptions options) throws AIException {
        try {
            return chatAsync(input, options).join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
    }

    /**
     * Asynchronously sends a message to the AI with default system prompt from {@link #getChatPrompt()}.
     * <p>
     * Usage example:
     * <pre>
     * service.chatAsync(message).thenAccept(response -> {
     *     // handle full response
     * }).exceptionally(e -> {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     * <p>
     * The default implementation delegates to {@link #chatAsync(ChatInput)}.
     *
     * @param message The user's message to send to the AI.
     * @return A CompletableFuture that will contain the AI's response, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     */
    default CompletableFuture<String> chatAsync(String message) {
        return chatAsync(ChatInput.newBuilder().message(message).build());
    }

    /**
     * Asynchronously sends input to the AI with default system prompt from {@link #getChatPrompt()}.
     * <p>
     * Usage example:
     * <pre>
     * var input = ChatInput.newBuilder()
     *     .message(message)
     *     .images(imageBytes)
     *     .build();
     * service.chatAsync(input).thenAccept(response -> {
     *     // handle full response
     * }).exceptionally(e -> {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     * <p>
     * The default implementation delegates to {@link #chatAsync(ChatInput, ChatOptions)}.
     *
     * @param input The user's input containing message and optional images.
     * @return A CompletableFuture that will contain the AI's response, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     */
    default CompletableFuture<String> chatAsync(ChatInput input) {
        return chatAsync(input, ChatOptions.newBuilder().systemPrompt(getChatPrompt()).build());
    }

    /**
     * Asynchronously sends a message to the AI and returns a response.
     * <p>
     * The message represents the user's input, while the system prompt in options defines the AI's behavior.
     * <p>
     * Usage example:
     * <pre>
     * service.chatAsync(message, options).thenAccept(response -> {
     *     // handle full response
     * }).exceptionally(e -> {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     * <p>
     * The default implementation delegates to {@link #chatAsync(ChatInput, ChatOptions)}.
     *
     * @param message The user's message to send to the AI.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @return A CompletableFuture that will contain the AI's response, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     */
    default CompletableFuture<String> chatAsync(String message, ChatOptions options) {
        return chatAsync(ChatInput.newBuilder().message(message).build(), options);
    }

    /**
     * Asynchronously sends input to the AI and returns a response.
     * <p>
     * The input contains the user's message and optional images, while the system prompt in options defines the AI's behavior.
     * <p>
     * Usage example:
     * <pre>
     * var input = ChatInput.newBuilder()
     *     .message(message)
     *     .images(imageBytes)
     *     .build();
     * service.chatAsync(input, options).thenAccept(response -> {
     *     // handle full response
     * }).exceptionally(e -> {
     *     // handle exception
     *     return null;
     * });
     * </pre>
     * <p>
     * This is the core method for static chat-based AI interactions with images.
     *
     * @param input The user's input containing message and optional images.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @return A CompletableFuture that will contain the AI's response, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     */
    CompletableFuture<String> chatAsync(ChatInput input, ChatOptions options);

    /**
     * Send a message to the AI with default system prompt from {@link #getChatPrompt()} and retrieve an asynchronous stream of tokens.
     * <p>
     * Usage example:
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
     * <p>
     * The default implementation delegates to {@link #chatStream(ChatInput, Consumer)}.
     *
     * @param message The user's message to send to the AI.
     * @param onToken The token consumer, this will be invoked for every chat response token in the stream.
     * @return An empty CompletableFuture which only completes when the end of stream is reached, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     * @throws UnsupportedOperationException if chat streaming is not supported by the implementation.
     */
    default CompletableFuture<Void> chatStream(String message, Consumer<String> onToken) {
        return chatStream(ChatInput.newBuilder().message(message).build(), onToken);
    }

    /**
     * Send input to the AI with default system prompt from {@link #getChatPrompt()} and retrieve an asynchronous stream of tokens.
     * <p>
     * Usage example:
     * <pre>
     * var input = ChatInput.newBuilder()
     *     .message(message)
     *     .images(imageBytes)
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
     * <p>
     * The default implementation delegates to {@link #chatStream(ChatInput, ChatOptions, Consumer)}.
     *
     * @param input The user's input containing message and optional images.
     * @param onToken The token consumer, this will be invoked for every chat response token in the stream.
     * @return An empty CompletableFuture which only completes when the end of stream is reached, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     * @throws UnsupportedOperationException if chat streaming is not supported by the implementation.
     */
    default CompletableFuture<Void> chatStream(ChatInput input, Consumer<String> onToken) {
        return chatStream(input, ChatOptions.newBuilder().systemPrompt(getChatPrompt()).build(), onToken);
    }

    /**
     * Send a message to the AI and retrieve an asynchronous stream of tokens.
     * <p>
     * The message represents the user's input, while the system prompt in options defines the AI's behavior.
     * <p>
     * Usage example:
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
     * <p>
     * The default implementation delegates to {@link #chatStream(ChatInput, ChatOptions, Consumer)}.
     *
     * @param message The user's message to send to the AI.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @param onToken The token consumer, this will be invoked for every chat response token in the stream.
     * @return An empty CompletableFuture which only completes when the end of stream is reached, never {@code null}.
     * @throws IllegalArgumentException if message is blank.
     * @throws UnsupportedOperationException if chat streaming is not supported by the implementation.
     */
    default CompletableFuture<Void> chatStream(String message, ChatOptions options, Consumer<String> onToken) {
        return chatStream(ChatInput.newBuilder().message(message).build(), options, onToken);
    }

    /**
     * Send input to the AI and retrieve an asynchronous stream of tokens.
     * <p>
     * The input contains the user's message and optional images, while the system prompt in options defines the AI's behavior.
     * <p>
     * Usage example:
     * <pre>
     * var input = ChatInput.newBuilder()
     *     .message(message)
     *     .images(imageBytes)
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
     *
     * @param input The user's input containing message and optional images.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @param onToken The token consumer, this will be invoked for every chat response token in the stream.
     * @return An empty CompletableFuture which only completes when the end of stream is reached, never {@code null}.
     * @throws IllegalArgumentException if input message is blank.
     * @throws UnsupportedOperationException if chat streaming is not supported by the implementation.
     */
    default CompletableFuture<Void> chatStream(ChatInput input, ChatOptions options, Consumer<String> onToken) {
        throw new UnsupportedOperationException();
    }


    // Text Analysis Capabilities -------------------------------------------------------------------------------------

    /**
     * Summarizes text to given maximum word count.
     * <p>
     * The default implementation delegates to {@link #summarizeAsync(String, int)}.
     *
     * @param text The text to summarize.
     * @param maxWords Maximum number of words in the summary.
     * @return The summarized text, never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if summarization is not supported by the implementation.
     * @throws AIException if summarization fails.
     */
    default String summarize(String text, int maxWords) throws AIException {
        try {
            return summarizeAsync(text, maxWords).join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
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
     * <p>
     * The default implementation delegates to {@link #extractKeyPointsAsync(String, int)}.
     *
     * @param text The text to extract key points from.
     * @param maxPoints Maximum number of key points to extract.
     * @return List of key points, never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if key point extraction is not supported by the implementation.
     * @throws AIException if extraction fails.
     */
    default List<String> extractKeyPoints(String text, int maxPoints) throws AIException {
        try {
            return extractKeyPointsAsync(text, maxPoints).join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
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
     */
    CompletableFuture<List<String>> extractKeyPointsAsync(String text, int maxPoints);


    // Text Translation Capabilities --------------------------------------------------------------------------------------

    /**
     * Detects the language of the given text.
     * <p>
     * The default implementation delegates to {@link #detectLanguageAsync(String)}.
     *
     * @param text The text to analyze.
     * @return The detected language code (ISO 639-1), never {@code null}.
     * @throws IllegalArgumentException if text is blank.
     * @throws UnsupportedOperationException if language detection is not supported by the implementation.
     * @throws AIException if language detection fails.
     */
    default String detectLanguage(String text) throws AIException {
        try {
            return detectLanguageAsync(text).join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
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
     */
    CompletableFuture<String> detectLanguageAsync(String text);

    /**
     * Translates text from source language to target language while preserving any markup and placeholders.
     * <p>
     * The default implementation delegates to {@link #translateAsync(String, String, String)}.
     *
     * @param text The text to translate.
     * @param sourceLang Source language code (ISO 639-1), or {@code null} for auto-detection.
     * @param targetLang Target language code (ISO 639-1).
     * @return The translated text, never {@code null}.
     * @throws IllegalArgumentException if text or targetLang is blank.
     * @throws UnsupportedOperationException if translation is not supported by the implementation.
     * @throws AIException if translation fails.
     */
    default String translate(String text, String sourceLang, String targetLang) throws AIException {
        try {
            return translateAsync(text, sourceLang, targetLang).join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
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
     */
    CompletableFuture<String> translateAsync(String text, String sourceLang, String targetLang);


    // Text Moderation Capabilities -----------------------------------------------------------------------------------

    /**
     * Moderates content with default options.
     * <p>
     * The default implementation delegates to {@link #moderateContentAsync(String)}.
     *
     * @param content The content to moderate.
     * @return Moderation result with detected violations, never {@code null}.
     * @throws IllegalArgumentException if content is blank.
     * @throws UnsupportedOperationException if content moderation is not supported by the implementation.
     * @throws AIException if moderation fails.
     */
    default ModerationResult moderateContent(String content) throws AIException {
        try {
            return moderateContentAsync(content).join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
    }

    /**
     * Asynchronously moderates content with default options.
     * <p>
     * The default implementation delegates to {@link #moderateContentAsync(String)} with {@link ModerationOptions#DEFAULT}.
     *
     * @param content The content to moderate.
     * @return A CompletableFuture that will contain the moderation result with detected violations, never {@code null}.
     * @throws IllegalArgumentException if content is blank.
     * @throws UnsupportedOperationException if content moderation is not supported by the implementation.
     */
    default CompletableFuture<ModerationResult> moderateContentAsync(String content) {
        return moderateContentAsync(content, ModerationOptions.DEFAULT);
    }

    /**
     * Moderates content to detect violations per {@link Category}.
     * <p>
     * The default implementation delegates to {@link #moderateContentAsync(String, ModerationOptions)}.
     *
     * @param content The content to moderate.
     * @param options Moderation options (categories to check, threshold, etc.).
     * @return Moderation result with detected violations, never {@code null}.
     * @throws IllegalArgumentException if content is blank.
     * @throws UnsupportedOperationException if content moderation is not supported by the implementation.
     * @throws AIException if moderation fails.
     */
    default ModerationResult moderateContent(String content, ModerationOptions options) throws AIException {
        try {
            return moderateContentAsync(content, options).join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
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
     */
    CompletableFuture<ModerationResult> moderateContentAsync(String content, ModerationOptions options);


    // Image Analysis Capabilities ------------------------------------------------------------------------------------

    /**
     * Analyzes an image and generates a description based on the given prompt.
     * <p>
     * Useful for generating alt text for accessibility, extracting information from images, or describing visual content.
     * <p>
     * The default implementation delegates to {@link #analyzeImageAsync(byte[], String)}.
     *
     * @param image The image bytes to analyze.
     * @param prompt The prompt describing what to focus on (e.g., "describe the product", "what's the main subject"),
     * or {@code null} for a general description.
     * @return Description of the image, never {@code null}.
     * @throws IllegalArgumentException if prompt is blank.
     * @throws UnsupportedOperationException if image analysis is not supported by the implementation.
     * @throws AIException if image analysis fails.
     */
    default String analyzeImage(byte[] image, String prompt) throws AIException {
        try {
            return analyzeImageAsync(image, prompt).join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
    }

    /**
     * Asynchronously analyzes an image and generates a description based on the given prompt.
     * <p>
     * Useful for generating alt text for accessibility, extracting information from images, or describing visual content.
     * <p>
     * This is the core method for image analysis.
     *
     * @param image The image bytes to analyze.
     * @param prompt The prompt describing what to focus on (e.g., "describe the product", "what's the main subject"),
     * or {@code null} for a general description.
     * @return A CompletableFuture that will contain the description of the image, never {@code null}.
     * @throws IllegalArgumentException if prompt is blank.
     * @throws UnsupportedOperationException if image analysis is not supported by the implementation.
     */
    CompletableFuture<String> analyzeImageAsync(byte[] image, String prompt);

    /**
     * Generates alt text for an image suitable for accessibility purposes.
     * <p>
     * The default implementation delegates to {@link #generateAltTextAsync(byte[])}.
     *
     * @param image The image bytes to analyze.
     * @return Alt text description, never {@code null}.
     * @throws UnsupportedOperationException if image analysis is not supported by the implementation.
     * @throws AIException if image analysis fails.
     */
    default String generateAltText(byte[] image) throws AIException {
        try {
            return generateAltTextAsync(image).join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
    }

    /**
     * Asynchronously generates alt text for an image suitable for accessibility purposes.
     * <p>
     * This is the core method for image alt text generation.
     *
     * @param image The image bytes to analyze.
     * @return A CompletableFuture that will contain the alt text description, never {@code null}.
     * @throws UnsupportedOperationException if image analysis is not supported by the implementation.
     */
    CompletableFuture<String> generateAltTextAsync(byte[] image);


    // Image Generation Capabilities ----------------------------------------------------------------------------------

    /**
     * Generates an image based on a text prompt with default options.
     * <p>
     * The default implementation delegates to {@link #generateImageAsync(String)}.
     *
     * @param prompt The text prompt describing the image to generate.
     * @return Generated image bytes, never {@code null}.
     * @throws IllegalArgumentException if prompt is blank.
     * @throws UnsupportedOperationException if image generation is not supported by the implementation.
     * @throws AIException if image generation fails.
     * @see #generateImage(String, GenerateImageOptions)
     * @see GenerateImageOptions#DEFAULT
     */
    default byte[] generateImage(String prompt) throws AIException {
        try {
            return generateImageAsync(prompt).join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
    }

    /**
     * Asynchronously generates an image based on a text prompt with default options.
     * <p>
     * The default implementation delegates to {@link #generateImageAsync(String, GenerateImageOptions)} with {@link GenerateImageOptions#DEFAULT}.
     *
     * @param prompt The text prompt describing the image to generate.
     * @return A CompletableFuture that will contain the generated image bytes, never {@code null}.
     * @throws IllegalArgumentException if prompt is blank.
     * @throws UnsupportedOperationException if image generation is not supported by the implementation.
     */
    default CompletableFuture<byte[]> generateImageAsync(String prompt) {
        return generateImageAsync(prompt, GenerateImageOptions.DEFAULT);
    }

    /**
     * Generates an image based on a text prompt.
     * <p>
     * The default implementation delegates to {@link #generateImageAsync(String, GenerateImageOptions)}.
     *
     * @param prompt The text prompt describing the image to generate.
     * @param options Image generation options (size, quality, style, etc.).
     * @return Generated image bytes, never {@code null}.
     * @throws IllegalArgumentException if prompt is blank.
     * @throws UnsupportedOperationException if image generation is not supported by the implementation.
     * @throws AIException if image generation fails.
     */
    default byte[] generateImage(String prompt, GenerateImageOptions options) throws AIException {
        try {
            return generateImageAsync(prompt, options).join();
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
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
     */
    CompletableFuture<byte[]> generateImageAsync(String prompt, GenerateImageOptions options);


    // Service Metadata -----------------------------------------------------------------------------------------------

    /**
     * Returns the name of this AI service.
     * @return The name of this AI service (e.g. AnthropicAIService (Anthropic claude-sonnet-4-5-20250929)).
     */
    default String getName() {
        return getClass().getSimpleName() + " (" + getProviderName() + " " + getModelName() + ")";
    }

    /**
     * Returns the AI provider name of this AI service.
     * @return The AI provider name of this AI service (e.g., "OpenAI", "Anthropic", "Google AI", etc).
     */
    String getProviderName();

    /**
     * Returns the (full) AI model name being used by this AI service.
     * @return The (full) AI model name being used by this AI service (e.g., "gpt-5-mini", "claude-sonnet-4-5-20250929", "gemini-2.5-flash", etc)
     */
    String getModelName();

    /**
     * Returns the chat prompt being used by this AI service.
     * @return The chat prompt being used by this AI service.
     */
    String getChatPrompt();

    /**
     * Returns the AI model version information for this AI service.
     * The version is extracted from {@link #getModelName()} and includes the model name prefix, major version, and
     * minor version.
     *
     * @return The AI model version for this AI service.
     * @see AIModelVersion#of(AIService)
     */
    default AIModelVersion getModelVersion() {
        return AIModelVersion.of(this);
    }

    /**
     * Returns whether this AI service implementation supports chat streaming via SSE.
     * The default implementation returns false.
     * @return Whether this AI service implementation supports chat streaming via SSE.
     */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * Returns whether this AI service implementation supports structured (JSON schema) outputs.
     * The default implementation returns false.
     * @return Whether this AI service implementation supports structured (JSON schema) outputs.
     */
    default boolean supportsStructuredOutput() {
        return false;
    }

    /**
     * Checks whether the given modality is supported by this AI service, which is usually determined by
     * {@link #getModelName()} or {@link #getModelVersion()}.
     * <p>
     * <strong>Important:</strong> This method provides a <em>hint</em> rather than a strict guarantee. Implementations
     * are <em>not required</em> to enforce this check before performing operations such as
     * {@link #analyzeImage(byte[], String)}, {@link #generateImage(String)}, or other modality-specific calls.
     * <p>
     * The primary purposes of this method are to allow callers to:
     * <ul>
     * <li>skip unnecessary API calls when a modality is known to be unsupported,</li>
     * <li>improve user experience (e.g., disable buttons or hide options in the UI),</li>
     * <li>dynamically select a fallback provider or model at runtime.</li>
     * </ul>
     * <p>
     * If a modality-specific operation is invoked despite {@code supports(AIModality)} returning {@code false},
     * the implementation <strong>may</strong> fail fast, typically by throwing {@code UnsupportedOperationException}
     * or a provider-specific exception.
     *
     * @param modality The modality (input analysis or output generation type) to check
     * @return {@code true} If this service is expected to support the given modality with the current model and
     * configuration, {@code false} otherwise.
     * @see AIModality
     */
    boolean supportsModality(AIModality modality);
}
