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
package org.omnifaces.ai.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.Location;
import org.omnifaces.ai.model.GenerateAudioOptions;
import org.omnifaces.ai.model.GenerateImageOptions;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.ModerationResult;

/**
 * Abstract decorator wrapper for {@link AIService} instances.
 * <p>
 * This class implements the decorator pattern, allowing you to wrap existing {@code AIService} implementations to decorate behavior without modifying the
 * underlying service implementation.
 * <p>
 * All methods delegate to the wrapped service by default. Subclasses can override specific methods to add behavior before or after delegation.
 *
 * @author Bauke Scholtz
 * @since 1.3
 * @see AIService
 */
public abstract class AIServiceWrapper implements AIService {

    private static final long serialVersionUID = 1L;

    /** The wrapped AIService instance. */
    private final AIService wrapped;

    /**
     * Creates a new wrapper around the given AIService.
     *
     * @param wrapped the AIService to wrap, must not be {@code null}.
     * @throws NullPointerException if wrapped is {@code null}.
     */
    public AIServiceWrapper(AIService wrapped) {
        Objects.requireNonNull(wrapped, "wrapped");
        this.wrapped = wrapped;
    }

    /**
     * Returns the wrapped AIService instance.
     *
     * @return the wrapped AIService
     */
    public AIService getWrapped() {
        return wrapped;
    }

    @Override
    public String chat(String message) throws AIException {
        return getWrapped().chat(message);
    }

    @Override
    public String chat(ChatInput input) throws AIException {
        return getWrapped().chat(input);
    }

    @Override
    public <T> T chat(String message, Class<T> type) throws AIException {
        return getWrapped().chat(message, type);
    }

    @Override
    public <T> T chat(ChatInput input, Class<T> type) throws AIException {
        return getWrapped().chat(input, type);
    }

    @Override
    public String chat(String message, ChatOptions options) throws AIException {
        return getWrapped().chat(message, options);
    }

    @Override
    public String chat(ChatInput input, ChatOptions options) throws AIException {
        return getWrapped().chat(input, options);
    }

    @Override
    public <T> T chat(String message, ChatOptions options, Class<T> type) throws AIException {
        return getWrapped().chat(message, options, type);
    }

    @Override
    public <T> T chat(ChatInput input, ChatOptions options, Class<T> type) throws AIException {
        return getWrapped().chat(input, options, type);
    }

    @Override
    public CompletableFuture<String> chatAsync(String message) throws AIException {
        return getWrapped().chatAsync(message);
    }

    @Override
    public CompletableFuture<String> chatAsync(ChatInput input) throws AIException {
        return getWrapped().chatAsync(input);
    }

    @Override
    public <T> CompletableFuture<T> chatAsync(String message, Class<T> type) throws AIException {
        return getWrapped().chatAsync(message, type);
    }

    @Override
    public <T> CompletableFuture<T> chatAsync(ChatInput input, Class<T> type) throws AIException {
        return getWrapped().chatAsync(input, type);
    }

    @Override
    public CompletableFuture<String> chatAsync(String message, ChatOptions options) throws AIException {
        return getWrapped().chatAsync(message, options);
    }

    @Override
    public <T> CompletableFuture<T> chatAsync(String message, ChatOptions options, Class<T> type) throws AIException {
        return getWrapped().chatAsync(message, options, type);
    }

    @Override
    public <T> CompletableFuture<T> chatAsync(ChatInput input, ChatOptions options, Class<T> type) throws AIException {
        return getWrapped().chatAsync(input, options, type);
    }

    @Override
    public CompletableFuture<String> chatAsync(ChatInput input, ChatOptions options) throws AIException {
        return getWrapped().chatAsync(input, options);
    }

    @Override
    public CompletableFuture<Void> chatStream(String message, Consumer<String> onToken) throws AIException {
        return getWrapped().chatStream(message, onToken);
    }

    @Override
    public CompletableFuture<Void> chatStream(ChatInput input, Consumer<String> onToken) throws AIException {
        return getWrapped().chatStream(input, onToken);
    }

    @Override
    public CompletableFuture<Void> chatStream(String message, ChatOptions options, Consumer<String> onToken) throws AIException {
        return getWrapped().chatStream(message, options, onToken);
    }

    @Override
    public CompletableFuture<Void> chatStream(ChatInput input, ChatOptions options, Consumer<String> onToken) throws AIException {
        return getWrapped().chatStream(input, options, onToken);
    }

    @Override
    public String upload(Attachment attachment, ChatOptions options) throws AIException {
        return getWrapped().upload(attachment, options);
    }

    @Override
    public String summarize(String text, int maxWords) throws AIException {
        return getWrapped().summarize(text, maxWords);
    }

    @Override
    public CompletableFuture<String> summarizeAsync(String text, int maxWords) {
        return getWrapped().summarizeAsync(text, maxWords);
    }

    @Override
    public List<String> extractKeyPoints(String text, int maxPoints) throws AIException {
        return getWrapped().extractKeyPoints(text, maxPoints);
    }

    @Override
    public CompletableFuture<List<String>> extractKeyPointsAsync(String text, int maxPoints) throws AIException {
        return getWrapped().extractKeyPointsAsync(text, maxPoints);
    }

    @Override
    public String detectLanguage(String text) throws AIException {
        return getWrapped().detectLanguage(text);
    }

    @Override
    public CompletableFuture<String> detectLanguageAsync(String text) throws AIException {
        return getWrapped().detectLanguageAsync(text);
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) throws AIException {
        return getWrapped().translate(text, sourceLang, targetLang);
    }

    @Override
    public CompletableFuture<String> translateAsync(String text, String sourceLang, String targetLang) throws AIException {
        return getWrapped().translateAsync(text, sourceLang, targetLang);
    }

    @Override
    public String proofread(String text) throws AIException {
        return getWrapped().proofread(text);
    }

    @Override
    public CompletableFuture<String> proofreadAsync(String text) throws AIException {
        return getWrapped().proofreadAsync(text);
    }

    @Override
    public ModerationResult moderateContent(String content) throws AIException {
        return getWrapped().moderateContent(content);
    }

    @Override
    public CompletableFuture<ModerationResult> moderateContentAsync(String content) throws AIException {
        return getWrapped().moderateContentAsync(content);
    }

    @Override
    public ModerationResult moderateContent(String content, ModerationOptions options) throws AIException {
        return getWrapped().moderateContent(content, options);
    }

    @Override
    public CompletableFuture<ModerationResult> moderateContentAsync(String content, ModerationOptions options) throws AIException {
        return getWrapped().moderateContentAsync(content, options);
    }

    @Override
    public String webSearch(String query) throws AIException {
        return getWrapped().webSearch(query);
    }

    @Override
    public String webSearch(String query, Location location) throws AIException {
        return getWrapped().webSearch(query, location);
    }

    @Override
    public <T> T webSearch(String query, Class<T> type) throws AIException {
        return getWrapped().webSearch(query, type);
    }

    @Override
    public <T> T webSearch(String query, Location location, Class<T> type) throws AIException {
        return getWrapped().webSearch(query, location, type);
    }

    @Override
    public CompletableFuture<String> webSearchAsync(String query) throws AIException {
        return getWrapped().webSearchAsync(query);
    }

    @Override
    public CompletableFuture<String> webSearchAsync(String query, Location location) throws AIException {
        return getWrapped().webSearchAsync(query, location);
    }

    @Override
    public <T> CompletableFuture<T> webSearchAsync(String query, Class<T> type) throws AIException {
        return getWrapped().webSearchAsync(query, type);
    }

    @Override
    public <T> CompletableFuture<T> webSearchAsync(String query, Location location, Class<T> type) throws AIException {
        return getWrapped().webSearchAsync(query, location, type);
    }

    @Override
    public String analyzeImage(byte[] image, String prompt) throws AIException {
        return getWrapped().analyzeImage(image, prompt);
    }

    @Override
    public CompletableFuture<String> analyzeImageAsync(byte[] image, String prompt) throws AIException {
        return getWrapped().analyzeImageAsync(image, prompt);
    }

    @Override
    public String generateAltText(byte[] image) throws AIException {
        return getWrapped().generateAltText(image);
    }

    @Override
    public CompletableFuture<String> generateAltTextAsync(byte[] image) throws AIException {
        return getWrapped().generateAltTextAsync(image);
    }

    @Override
    public byte[] generateImage(String prompt) throws AIException {
        return getWrapped().generateImage(prompt);
    }

    @Override
    public CompletableFuture<byte[]> generateImageAsync(String prompt) throws AIException {
        return getWrapped().generateImageAsync(prompt);
    }

    @Override
    public byte[] generateImage(String prompt, GenerateImageOptions options) throws AIException {
        return getWrapped().generateImage(prompt, options);
    }

    @Override
    public CompletableFuture<byte[]> generateImageAsync(String prompt, GenerateImageOptions options) throws AIException {
        return getWrapped().generateImageAsync(prompt, options);
    }

    @Override
    public String transcribe(byte[] audio) throws AIException {
        return getWrapped().transcribe(audio);
    }

    @Override
    public String transcribe(Path audio) throws AIException {
        return getWrapped().transcribe(audio);
    }

    @Override
    public CompletableFuture<String> transcribeAsync(byte[] audio) throws AIException {
        return getWrapped().transcribeAsync(audio);
    }

    @Override
    public CompletableFuture<String> transcribeAsync(Path audio) throws AIException {
        return getWrapped().transcribeAsync(audio);
    }

    @Override
    public byte[] generateAudio(String text) throws AIException {
        return getWrapped().generateAudio(text);
    }

    @Override
    public byte[] generateAudio(String text, GenerateAudioOptions options) throws AIException {
        return getWrapped().generateAudio(text, options);
    }

    @Override
    public void generateAudio(String text, Path path) throws AIException {
        getWrapped().generateAudio(text, path);
    }

    @Override
    public void generateAudio(String text, Path path, GenerateAudioOptions options) throws AIException {
        getWrapped().generateAudio(text, path, options);
    }

    @Override
    public CompletableFuture<byte[]> generateAudioAsync(String text) throws AIException {
        return getWrapped().generateAudioAsync(text);
    }

    @Override
    public CompletableFuture<Void> generateAudioAsync(String text, Path path) throws AIException {
        return getWrapped().generateAudioAsync(text, path);
    }

    @Override
    public CompletableFuture<byte[]> generateAudioAsync(String text, GenerateAudioOptions options) throws AIException {
        return getWrapped().generateAudioAsync(text, options);
    }

    @Override
    public CompletableFuture<Void> generateAudioAsync(String text, Path path, GenerateAudioOptions options) throws AIException {
        return getWrapped().generateAudioAsync(text, path, options);
    }

    @Override
    public String getName() {
        return getWrapped().getName();
    }

    @Override
    public String getProviderName() {
        return getWrapped().getProviderName();
    }

    @Override
    public String getModelName() {
        return getWrapped().getModelName();
    }

    @Override
    public String getChatPrompt() {
        return getWrapped().getChatPrompt();
    }

    @Override
    public AIModelVersion getModelVersion() {
        return getWrapped().getModelVersion();
    }

    @Override
    public boolean supportsStreaming() {
        return getWrapped().supportsStreaming();
    }

    @Override
    public boolean supportsFileAttachments() {
        return getWrapped().supportsFileAttachments();
    }

    @Override
    public boolean supportsFileAttachmentsInHistory() {
        return getWrapped().supportsFileAttachmentsInHistory();
    }

    @Override
    public boolean supportsStructuredOutput() {
        return getWrapped().supportsStructuredOutput();
    }

    @Override
    public boolean supportsWebSearch() {
        return getWrapped().supportsWebSearch();
    }

    @Override
    public boolean supportsReasoningEffort() {
        return getWrapped().supportsReasoningEffort();
    }

    @Override
    public boolean supportsModality(AIModality modality) {
        return getWrapped().supportsModality(modality);
    }

}
