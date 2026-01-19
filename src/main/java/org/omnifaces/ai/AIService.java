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

import jakarta.enterprise.context.ApplicationScoped;

import org.omnifaces.ai.ModerationOptions.Category;
import org.omnifaces.ai.exception.AIException;

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
 */
public interface AIService extends Serializable {

    // Chat Capabilities ----------------------------------------------------------------------------------------------

    /**
     * Sends a message to the AI and returns a response.
     * <p>
     * This is the core method for chat-based AI interactions.
     * The message represents the user's input, while the system prompt in options defines the AI's behavior.
     *
     * @param message The user's message to send to the AI.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @return The AI's response, never {@code null}.
     * @throws UnsupportedOperationException if chat capability is not supported by the implementation.
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
     * Asynchronously sends a message to the AI and returns a response.
     * <p>
     * This is the core method for chat-based AI interactions.
     * The message represents the user's input, while the system prompt in options defines the AI's behavior.
     *
     * @param message The user's message to send to the AI.
     * @param options Chat options (system prompt, temperature, max tokens, etc.).
     * @return A CompletableFuture that will contain the AI's response, never {@code null}.
     * @throws UnsupportedOperationException if chat capability is not supported by the implementation.
     */
    CompletableFuture<String> chatAsync(String message, ChatOptions options);

    /**
     * Sends a message to the AI with default options.
     *
     * @param message The user's message to send to the AI.
     * @return The AI's response, never {@code null}.
     * @throws UnsupportedOperationException if chat capability is not supported by the implementation.
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
     * Asynchronously sends a message to the AI with default options.
     *
     * @param message The user's message to send to the AI.
     * @return A CompletableFuture that will contain the AI's response, never {@code null}.
     * @throws UnsupportedOperationException if chat capability is not supported by the implementation.
     */
    default CompletableFuture<String> chatAsync(String message) {
        return chatAsync(message, ChatOptions.newBuilder().systemPrompt(getChatPrompt()).build());
    }


    // Text Analysis Capabilities -------------------------------------------------------------------------------------

    /**
     * Summarizes text to given maximum word count.
     *
     * @param text The text to summarize.
     * @param maxWords Maximum number of words in the summary.
     * @return The summarized text, never {@code null}.
     * @throws UnsupportedOperationException if summarization capability is not supported by the implementation.
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
     *
     * @param text The text to summarize.
     * @param maxWords Maximum number of words in the summary.
     * @return A CompletableFuture that will contain the summarized text, never {@code null}.
     * @throws UnsupportedOperationException if summarization capability is not supported by the implementation.
     */
    CompletableFuture<String> summarizeAsync(String text, int maxWords);

    /**
     * Extracts key points from text as a list.
     *
     * @param text The text to extract key points from.
     * @param maxPoints Maximum number of key points to extract.
     * @return List of key points, never {@code null}.
     * @throws UnsupportedOperationException if key point extraction capability is not supported by the implementation.
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
     *
     * @param text The text to extract key points from.
     * @param maxPoints Maximum number of key points to extract.
     * @return A CompletableFuture that will contain a list of key points, never {@code null}.
     * @throws UnsupportedOperationException if key point extraction capability is not supported by the implementation.
     */
    CompletableFuture<List<String>> extractKeyPointsAsync(String text, int maxPoints);


    // Text Translation Capabilities --------------------------------------------------------------------------------------

    /**
     * Translates text from source language to target language while preserving any markup and placeholders.
     *
     * @param text The text to translate.
     * @param sourceLang Source language code (ISO 639-1), or {@code null} for auto-detection.
     * @param targetLang Target language code (ISO 639-1).
     * @return The translated text, never {@code null}.
     * @throws UnsupportedOperationException if translation capability is not supported by the implementation.
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
     *
     * @param text The text to translate.
     * @param sourceLang Source language code (ISO 639-1), or {@code null} for auto-detection.
     * @param targetLang Target language code (ISO 639-1).
     * @return A CompletableFuture that will contain the translated text, never {@code null}.
     * @throws UnsupportedOperationException if translation capability is not supported by the implementation.
     */
    CompletableFuture<String> translateAsync(String text, String sourceLang, String targetLang);

    /**
     * Detects the language of the given text.
     *
     * @param text The text to analyze.
     * @return The detected language code (ISO 639-1), never {@code null}.
     * @throws UnsupportedOperationException if language detection capability is not supported by the implementation.
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
     *
     * @param text The text to analyze.
     * @return A CompletableFuture that will contain the detected language code (ISO 639-1), never {@code null}.
     * @throws UnsupportedOperationException if language detection capability is not supported by the implementation.
     */
    CompletableFuture<String> detectLanguageAsync(String text);


    // Text Moderation Capabilities -----------------------------------------------------------------------------------

    /**
     * Moderates content to detect violations per {@link Category}.
     *
     * @param content The content to moderate.
     * @param options Moderation options (categories to check, threshold, etc.).
     * @return Moderation result with detected violations, never {@code null}.
     * @throws UnsupportedOperationException if content moderation capability is not supported by the implementation.
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
     *
     * @param content The content to moderate.
     * @param options Moderation options (categories to check, threshold, etc.).
     * @return A CompletableFuture that will contain the moderation result with detected violations, never {@code null}.
     * @throws UnsupportedOperationException if content moderation capability is not supported by the implementation.
     */
    CompletableFuture<ModerationResult> moderateContentAsync(String content, ModerationOptions options);

    /**
     * Moderates content with default options.
     * <p>
     * Default implementation checks for all categories defined in {@link Category#OPENAI_SUPPORTED_CATEGORY_NAMES}.
     *
     * @param content The content to moderate.
     * @return Moderation result with detected violations, never {@code null}.
     * @throws UnsupportedOperationException if content moderation capability is not supported by the implementation.
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
     * Default implementation checks for all categories defined in {@link Category#OPENAI_SUPPORTED_CATEGORY_NAMES}.
     *
     * @param content The content to moderate.
     * @return A CompletableFuture that will contain the moderation result with detected violations, never {@code null}.
     * @throws UnsupportedOperationException if content moderation capability is not supported by the implementation.
     */
    default CompletableFuture<ModerationResult> moderateContentAsync(String content) {
        return moderateContentAsync(content, ModerationOptions.DEFAULT);
    }


    // Image Analysis Capabilities ------------------------------------------------------------------------------------

    /**
     * Analyzes an image and generates a description based on the given prompt.
     * <p>
     * Useful for generating alt text for accessibility, extracting information from images, or describing visual content.
     *
     * @param image The image bytes to analyze.
     * @param prompt The prompt describing what to focus on (e.g., "describe the product", "what's the main subject"), or {@code null} for a general description.
     * @return Description of the image, never {@code null}.
     * @throws UnsupportedOperationException if image analysis capability is not supported by the implementation.
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
     *
     * @param image The image bytes to analyze.
     * @param prompt The prompt describing what to focus on (e.g., "describe the product", "what's the main subject"), or {@code null} for a general description.
     * @return A CompletableFuture that will contain the description of the image, never {@code null}.
     * @throws UnsupportedOperationException if image analysis capability is not supported by the implementation.
     */
    CompletableFuture<String> analyzeImageAsync(byte[] image, String prompt);

    /**
     * Generates alt text for an image suitable for accessibility purposes.
     *
     * @param image The image bytes to analyze.
     * @return Alt text description, never {@code null}.
     * @throws UnsupportedOperationException if image analysis capability is not supported by the implementation.
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
     *
     * @param image The image bytes to analyze.
     * @return A CompletableFuture that will contain the alt text description, never {@code null}.
     * @throws UnsupportedOperationException if image analysis capability is not supported by the implementation.
     */
    CompletableFuture<String> generateAltTextAsync(byte[] image);


    // Image Generation Capabilities ----------------------------------------------------------------------------------

    /**
     * Generates an image based on a text prompt.
     *
     * @param prompt The text prompt describing the image to generate.
     * @param options Image generation options (size, quality, style, etc.).
     * @return Generated image bytes, never {@code null}.
     * @throws UnsupportedOperationException if image generation capability is not supported by the implementation.
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
     *
     * @param prompt The text prompt describing the image to generate.
     * @param options Image generation options (size, quality, style, etc.).
     * @return A CompletableFuture that will contain the generated image bytes, never {@code null}.
     * @throws UnsupportedOperationException if image generation capability is not supported by the implementation.
     */
    CompletableFuture<byte[]> generateImageAsync(String prompt, GenerateImageOptions options);

    /**
     * Generates an image based on a text prompt with default options.
     *
     * @param prompt The text prompt describing the image to generate.
     * @return Generated image bytes, never {@code null}.
     * @throws UnsupportedOperationException if image generation capability is not supported by the implementation.
     * @throws AIException if image generation fails.
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
     *
     * @param prompt The text prompt describing the image to generate.
     * @return A CompletableFuture that will contain the generated image bytes, never {@code null}.
     * @throws UnsupportedOperationException if image generation capability is not supported by the implementation.
     */
    default CompletableFuture<byte[]> generateImageAsync(String prompt) {
        return generateImageAsync(prompt, GenerateImageOptions.DEFAULT);
    }


    // Service Metadata -----------------------------------------------------------------------------------------------

    /**
     * Returns the name of this AI service.
     * @return The name of this AI service (e.g. AnthropicAIService (Anthropic claude-sonnet-4-5-20250929)).
     */
    default String getServiceName() {
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
     * Returns the estimated token usage per word.
     * The default implementation returns 1.5.
     * @return The estimated token usage per word.
     */
    default double getEstimatedTokensPerWord() {
        return 1.5;
    }

    /**
     * Returns the AI model version information for this AI service.
     * The version is extracted from {@link #getModelName()} and includes the model name prefix, major version, and minor version.
     *
     * @return The AI model version for this AI service.
     * @see AIModelVersion#of(AIService)
     */
    default AIModelVersion getAIModelVersion() {
        return AIModelVersion.of(this);
    }
}
