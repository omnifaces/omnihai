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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.exception.AIStreamAbortedException;
import org.omnifaces.ai.model.AnalyzeVideoOptions;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.Location;
import org.omnifaces.ai.model.ClassificationResult;
import org.omnifaces.ai.model.GenerateAudioOptions;
import org.omnifaces.ai.model.GenerateImageOptions;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.ModerationResult;

/**
 * Abstract {@link AIServiceWrapper} that funnels every service operation through a single interception hook.
 * <p>
 * Whereas {@link AIServiceWrapper} delegates each overload straight to the identical overload on the wrapped service, this class routes <em>all</em>
 * work-performing methods &mdash; regardless of which overload the caller uses &mdash; through {@link #intercept(Function)} (synchronous) or
 * {@link #interceptAsync(Function)} (asynchronous). Subclasses implement just those two hooks to apply cross-cutting behavior (retry, failover, throttling,
 * caching, circuit breaking, etc.) uniformly across the entire {@link AIService} surface.
 * <p>
 * The operation is expressed as a {@link Function} of {@link AIService} rather than a plain supplier, so an implementation may apply it to a service other than
 * {@link #getWrapped()} &mdash; for example {@link FailoverAIService} re-applies the same operation to an alternate provider.
 * <p>
 * Service metadata methods ({@code getName()}, {@code getProviderName()}, {@code supports*()}, etc.) are not intercepted; they retain the plain pass-through
 * behavior of {@link AIServiceWrapper}.
 *
 * @author Bauke Scholtz
 * @since 1.5
 * @see RetryingAIService
 * @see FailoverAIService
 */
public abstract class InterceptingAIServiceWrapper extends AIServiceWrapper {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new intercepting wrapper around the given AIService.
     *
     * @param wrapped the AIService to wrap, must not be {@code null}.
     * @throws NullPointerException if wrapped is {@code null}.
     */
    protected InterceptingAIServiceWrapper(AIService wrapped) {
        super(wrapped);
    }

    /**
     * Applies the given synchronous operation with this decorator's behavior and returns its result.
     * <p>
     * The operation is expected to invoke the intended method on the supplied {@link AIService}. A typical implementation applies it to {@link #getWrapped()},
     * optionally more than once (retry) or against alternate services (failover).
     *
     * @param <R> The operation's result type.
     * @param operation The operation to apply, receiving the service to invoke and returning its result.
     * @return The operation's result.
     * @throws AIException if the operation ultimately fails.
     */
    protected abstract <R> R intercept(Function<AIService, R> operation);

    /**
     * Applies the given asynchronous operation with this decorator's behavior and returns its (composed) result.
     * <p>
     * The operation is expected to invoke the intended asynchronous method on the supplied {@link AIService}. A typical implementation applies it to
     * {@link #getWrapped()}, optionally scheduling additional attempts (retry) or against alternate services (failover).
     *
     * @param <R> The operation's result type.
     * @param operation The operation to apply, receiving the service to invoke and returning its pending result.
     * @return A CompletableFuture that will contain the operation's result.
     * @throws AIException if the operation ultimately fails.
     */
    protected abstract <R> CompletableFuture<R> interceptAsync(Function<AIService, CompletableFuture<R>> operation);

    /**
     * Unwraps a {@link CompletionException} to its cause, so an interception decision inspects the actual failure rather than the asynchronous wrapper. A
     * throwable that is not a {@link CompletionException} (or has no cause) is returned as-is.
     *
     * @param throwable The throwable to unwrap.
     * @return The unwrapped cause, or the throwable itself.
     */
    protected static Throwable unwrap(Throwable throwable) {
        return throwable instanceof CompletionException && throwable.getCause() != null ? throwable.getCause() : throwable;
    }

    /**
     * Returns whether the given failure must never be re-attempted, regardless of the retry or failover predicate in effect.
     * <p>
     * A stream that already emitted tokens to a consumer which cannot be reset would replay them on a new attempt, so {@link AIStreamAbortedException} is
     * terminal.
     *
     * @param throwable The failure to inspect.
     * @return Whether the failure is terminal.
     */
    protected static boolean isUnrecoverable(Throwable throwable) {
        return unwrap(throwable) instanceof AIStreamAbortedException;
    }

    /**
     * Funnels a streaming operation through {@link #interceptAsync(Function)}, tracking token emission so a re-attempt can never silently replay tokens.
     * <p>
     * The caller's token consumer is wrapped so each attempt knows whether it emitted anything. When an attempt fails after emitting a token, the operation is
     * only re-attempted if the caller supplied a {@link ResettableConsumer}, which is notified via {@link ResettableConsumer#onReset(Throwable, int)} before
     * the new attempt starts; otherwise the failure is rethrown as a terminal {@link AIStreamAbortedException}.
     */
    private CompletableFuture<Void> interceptStream(Consumer<String> onToken, BiFunction<AIService, Consumer<String>, CompletableFuture<Void>> operation) {
        var attempts = new AtomicInteger();
        var lastFailure = new AtomicReference<Throwable>();

        return interceptAsync(service -> {
            var attempt = attempts.incrementAndGet();

            if (attempt > 1 && onToken instanceof ResettableConsumer<?> resettable) {
                resettable.onReset(lastFailure.get(), attempt);
            }

            var emitted = new AtomicBoolean();
            CompletableFuture<Void> future;

            // An attempt may fail before it ever returns a future, e.g. when building the payload uploads an attachment and that upload fails. Normalizing that
            // into a failed future keeps every failure flowing through the same handler, so the next attempt is always told what went wrong.
            try {
                future = operation.apply(service, trackingConsumer(onToken, emitted));
            }
            catch (RuntimeException e) {
                future = CompletableFuture.failedFuture(e);
            }

            return future.exceptionallyCompose(exception -> {
                lastFailure.set(unwrap(exception));
                var terminal = emitted.get() && !(onToken instanceof ResettableConsumer<?>) && !isUnrecoverable(exception);
                return CompletableFuture.failedFuture(terminal ? streamAborted(unwrap(exception)) : exception);
            });
        });
    }

    /**
     * Wraps the caller's token consumer so the current attempt records whether it emitted anything.
     * <p>
     * The wrapper preserves resettability: when the caller's consumer is a {@link ResettableConsumer}, so is the wrapper, forwarding
     * {@link ResettableConsumer#onReset(Throwable, int)} to it. Without this, a nested intercepting decorator would observe a plain consumer and abort a
     * partially consumed stream that the caller is in fact prepared to restart.
     */
    private static Consumer<String> trackingConsumer(Consumer<String> onToken, AtomicBoolean emitted) {
        Consumer<String> tracking = token -> {
            emitted.set(true);
            onToken.accept(token);
        };

        return onToken instanceof ResettableConsumer<?> resettable ? ResettableConsumer.of(tracking, resettable::onReset) : tracking;
    }

    private static AIStreamAbortedException streamAborted(Throwable cause) {
        return new AIStreamAbortedException(
            "Chat stream failed after tokens were already emitted and therefore cannot be safely restarted; supply a "
                + ResettableConsumer.class.getSimpleName() + " as token consumer to enable restarting",
            cause
        );
    }

    // Chat Capabilities ----------------------------------------------------------------------------------------------

    @Override
    public String chat(String message) throws AIException {
        return intercept(service -> service.chat(message));
    }

    @Override
    public String chat(ChatInput input) throws AIException {
        return intercept(service -> service.chat(input));
    }

    @Override
    public <T> T chat(String message, Class<T> type) throws AIException {
        return intercept(service -> service.chat(message, type));
    }

    @Override
    public <T> T chat(ChatInput input, Class<T> type) throws AIException {
        return intercept(service -> service.chat(input, type));
    }

    @Override
    public String chat(String message, ChatOptions options) throws AIException {
        return intercept(service -> service.chat(message, options));
    }

    @Override
    public String chat(ChatInput input, ChatOptions options) throws AIException {
        return intercept(service -> service.chat(input, options));
    }

    @Override
    public <T> T chat(String message, ChatOptions options, Class<T> type) throws AIException {
        return intercept(service -> service.chat(message, options, type));
    }

    @Override
    public <T> T chat(ChatInput input, ChatOptions options, Class<T> type) throws AIException {
        return intercept(service -> service.chat(input, options, type));
    }

    @Override
    public CompletableFuture<String> chatAsync(String message) throws AIException {
        return interceptAsync(service -> service.chatAsync(message));
    }

    @Override
    public CompletableFuture<String> chatAsync(ChatInput input) throws AIException {
        return interceptAsync(service -> service.chatAsync(input));
    }

    @Override
    public <T> CompletableFuture<T> chatAsync(String message, Class<T> type) throws AIException {
        return interceptAsync(service -> service.chatAsync(message, type));
    }

    @Override
    public <T> CompletableFuture<T> chatAsync(ChatInput input, Class<T> type) throws AIException {
        return interceptAsync(service -> service.chatAsync(input, type));
    }

    @Override
    public CompletableFuture<String> chatAsync(String message, ChatOptions options) throws AIException {
        return interceptAsync(service -> service.chatAsync(message, options));
    }

    @Override
    public <T> CompletableFuture<T> chatAsync(String message, ChatOptions options, Class<T> type) throws AIException {
        return interceptAsync(service -> service.chatAsync(message, options, type));
    }

    @Override
    public <T> CompletableFuture<T> chatAsync(ChatInput input, ChatOptions options, Class<T> type) throws AIException {
        return interceptAsync(service -> service.chatAsync(input, options, type));
    }

    @Override
    public CompletableFuture<String> chatAsync(ChatInput input, ChatOptions options) throws AIException {
        return interceptAsync(service -> service.chatAsync(input, options));
    }

    // Chat Streaming capabilities -------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<Void> chatStream(String message, Consumer<String> onToken) throws AIException {
        return interceptStream(onToken, (service, token) -> service.chatStream(message, token));
    }

    @Override
    public CompletableFuture<Void> chatStream(ChatInput input, Consumer<String> onToken) throws AIException {
        return interceptStream(onToken, (service, token) -> service.chatStream(input, token));
    }

    @Override
    public CompletableFuture<Void> chatStream(String message, ChatOptions options, Consumer<String> onToken) throws AIException {
        return interceptStream(onToken, (service, token) -> service.chatStream(message, options, token));
    }

    @Override
    public CompletableFuture<Void> chatStream(ChatInput input, ChatOptions options, Consumer<String> onToken) throws AIException {
        return interceptStream(onToken, (service, token) -> service.chatStream(input, options, token));
    }

    // File Attachment Capabilities -----------------------------------------------------------------------------------

    @Override
    public String upload(Attachment attachment, ChatOptions options) throws AIException {
        return intercept(service -> service.upload(attachment, options));
    }

    // Text Analysis Capabilities -------------------------------------------------------------------------------------

    @Override
    public String summarize(String text, int maxWords) throws AIException {
        return intercept(service -> service.summarize(text, maxWords));
    }

    @Override
    public CompletableFuture<String> summarizeAsync(String text, int maxWords) {
        return interceptAsync(service -> service.summarizeAsync(text, maxWords));
    }

    @Override
    public List<String> extractKeyPoints(String text, int maxPoints) throws AIException {
        return intercept(service -> service.extractKeyPoints(text, maxPoints));
    }

    @Override
    public CompletableFuture<List<String>> extractKeyPointsAsync(String text, int maxPoints) throws AIException {
        return interceptAsync(service -> service.extractKeyPointsAsync(text, maxPoints));
    }

    // Text Translation Capabilities ----------------------------------------------------------------------------------

    @Override
    public String detectLanguage(String text) throws AIException {
        return intercept(service -> service.detectLanguage(text));
    }

    @Override
    public CompletableFuture<String> detectLanguageAsync(String text) throws AIException {
        return interceptAsync(service -> service.detectLanguageAsync(text));
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) throws AIException {
        return intercept(service -> service.translate(text, sourceLang, targetLang));
    }

    @Override
    public CompletableFuture<String> translateAsync(String text, String sourceLang, String targetLang) throws AIException {
        return interceptAsync(service -> service.translateAsync(text, sourceLang, targetLang));
    }

    // Text Proofreading Capabilities ---------------------------------------------------------------------------------

    @Override
    public String proofread(String text) throws AIException {
        return intercept(service -> service.proofread(text));
    }

    @Override
    public CompletableFuture<String> proofreadAsync(String text) throws AIException {
        return interceptAsync(service -> service.proofreadAsync(text));
    }

    // Text Moderation Capabilities -----------------------------------------------------------------------------------

    @Override
    public ModerationResult moderateContent(String content) throws AIException {
        return intercept(service -> service.moderateContent(content));
    }

    @Override
    public CompletableFuture<ModerationResult> moderateContentAsync(String content) throws AIException {
        return interceptAsync(service -> service.moderateContentAsync(content));
    }

    @Override
    public ModerationResult moderateContent(String content, ModerationOptions options) throws AIException {
        return intercept(service -> service.moderateContent(content, options));
    }

    @Override
    public CompletableFuture<ModerationResult> moderateContentAsync(String content, ModerationOptions options) throws AIException {
        return interceptAsync(service -> service.moderateContentAsync(content, options));
    }

    @Override
    public ClassificationResult classify(String text, List<String> labels) throws AIException {
        return intercept(service -> service.classify(text, labels));
    }

    @Override
    public ClassificationResult classify(String text, String... labels) throws AIException {
        return intercept(service -> service.classify(text, labels));
    }

    @Override
    public CompletableFuture<ClassificationResult> classifyAsync(String text, List<String> labels) throws AIException {
        return interceptAsync(service -> service.classifyAsync(text, labels));
    }

    @Override
    public CompletableFuture<ClassificationResult> classifyAsync(String text, String... labels) throws AIException {
        return interceptAsync(service -> service.classifyAsync(text, labels));
    }

    // Web Search Capabilities ----------------------------------------------------------------------------------------

    @Override
    public String webSearch(String query) throws AIException {
        return intercept(service -> service.webSearch(query));
    }

    @Override
    public String webSearch(String query, Location location) throws AIException {
        return intercept(service -> service.webSearch(query, location));
    }

    @Override
    public <T> T webSearch(String query, Class<T> type) throws AIException {
        return intercept(service -> service.webSearch(query, type));
    }

    @Override
    public <T> T webSearch(String query, Location location, Class<T> type) throws AIException {
        return intercept(service -> service.webSearch(query, location, type));
    }

    @Override
    public CompletableFuture<String> webSearchAsync(String query) throws AIException {
        return interceptAsync(service -> service.webSearchAsync(query));
    }

    @Override
    public CompletableFuture<String> webSearchAsync(String query, Location location) throws AIException {
        return interceptAsync(service -> service.webSearchAsync(query, location));
    }

    @Override
    public <T> CompletableFuture<T> webSearchAsync(String query, Class<T> type) throws AIException {
        return interceptAsync(service -> service.webSearchAsync(query, type));
    }

    @Override
    public <T> CompletableFuture<T> webSearchAsync(String query, Location location, Class<T> type) throws AIException {
        return interceptAsync(service -> service.webSearchAsync(query, location, type));
    }

    // Image Analysis Capabilities ------------------------------------------------------------------------------------

    @Override
    public String analyzeImage(byte[] image, String prompt) throws AIException {
        return intercept(service -> service.analyzeImage(image, prompt));
    }

    @Override
    public CompletableFuture<String> analyzeImageAsync(byte[] image, String prompt) throws AIException {
        return interceptAsync(service -> service.analyzeImageAsync(image, prompt));
    }

    @Override
    public String analyzeImage(Path image, String prompt) throws AIException {
        return intercept(service -> service.analyzeImage(image, prompt));
    }

    @Override
    public CompletableFuture<String> analyzeImageAsync(Path image, String prompt) throws AIException {
        return interceptAsync(service -> service.analyzeImageAsync(image, prompt));
    }

    @Override
    public String generateAltText(byte[] image) throws AIException {
        return intercept(service -> service.generateAltText(image));
    }

    @Override
    public CompletableFuture<String> generateAltTextAsync(byte[] image) throws AIException {
        return interceptAsync(service -> service.generateAltTextAsync(image));
    }

    @Override
    public String generateAltText(Path image) throws AIException {
        return intercept(service -> service.generateAltText(image));
    }

    @Override
    public CompletableFuture<String> generateAltTextAsync(Path image) throws AIException {
        return interceptAsync(service -> service.generateAltTextAsync(image));
    }

    // Image Generation Capabilities ----------------------------------------------------------------------------------

    @Override
    public byte[] generateImage(String prompt) throws AIException {
        return intercept(service -> service.generateImage(prompt));
    }

    @Override
    public CompletableFuture<byte[]> generateImageAsync(String prompt) throws AIException {
        return interceptAsync(service -> service.generateImageAsync(prompt));
    }

    @Override
    public byte[] generateImage(String prompt, GenerateImageOptions options) throws AIException {
        return intercept(service -> service.generateImage(prompt, options));
    }

    @Override
    public CompletableFuture<byte[]> generateImageAsync(String prompt, GenerateImageOptions options) throws AIException {
        return interceptAsync(service -> service.generateImageAsync(prompt, options));
    }

    // Audio Transcription Capabilities -------------------------------------------------------------------------------

    @Override
    public String transcribe(byte[] audio) throws AIException {
        return intercept(service -> service.transcribe(audio));
    }

    @Override
    public String transcribe(Path audio) throws AIException {
        return intercept(service -> service.transcribe(audio));
    }

    @Override
    public CompletableFuture<String> transcribeAsync(byte[] audio) throws AIException {
        return interceptAsync(service -> service.transcribeAsync(audio));
    }

    @Override
    public CompletableFuture<String> transcribeAsync(Path audio) throws AIException {
        return interceptAsync(service -> service.transcribeAsync(audio));
    }

    // Audio Generation Capabilities ----------------------------------------------------------------------------------

    @Override
    public byte[] generateAudio(String text) throws AIException {
        return intercept(service -> service.generateAudio(text));
    }

    @Override
    public byte[] generateAudio(String text, GenerateAudioOptions options) throws AIException {
        return intercept(service -> service.generateAudio(text, options));
    }

    @Override
    public void generateAudio(String text, Path path) throws AIException {
        intercept(service -> {
            service.generateAudio(text, path);
            return null;
        });
    }

    @Override
    public void generateAudio(String text, Path path, GenerateAudioOptions options) throws AIException {
        intercept(service -> {
            service.generateAudio(text, path, options);
            return null;
        });
    }

    @Override
    public CompletableFuture<byte[]> generateAudioAsync(String text) throws AIException {
        return interceptAsync(service -> service.generateAudioAsync(text));
    }

    @Override
    public CompletableFuture<Void> generateAudioAsync(String text, Path path) throws AIException {
        return interceptAsync(service -> service.generateAudioAsync(text, path));
    }

    @Override
    public CompletableFuture<byte[]> generateAudioAsync(String text, GenerateAudioOptions options) throws AIException {
        return interceptAsync(service -> service.generateAudioAsync(text, options));
    }

    @Override
    public CompletableFuture<Void> generateAudioAsync(String text, Path path, GenerateAudioOptions options) throws AIException {
        return interceptAsync(service -> service.generateAudioAsync(text, path, options));
    }

    // Video Analysis Capabilities ------------------------------------------------------------------------------------

    @Override
    public String analyzeVideo(byte[] video, String prompt) throws AIException {
        return intercept(service -> service.analyzeVideo(video, prompt));
    }

    @Override
    public String analyzeVideo(Path video, String prompt) throws AIException {
        return intercept(service -> service.analyzeVideo(video, prompt));
    }

    @Override
    public String analyzeVideo(byte[] video, String prompt, AnalyzeVideoOptions options) throws AIException {
        return intercept(service -> service.analyzeVideo(video, prompt, options));
    }

    @Override
    public String analyzeVideo(Path video, String prompt, AnalyzeVideoOptions options) throws AIException {
        return intercept(service -> service.analyzeVideo(video, prompt, options));
    }

    @Override
    public CompletableFuture<String> analyzeVideoAsync(byte[] video, String prompt) throws AIException {
        return interceptAsync(service -> service.analyzeVideoAsync(video, prompt));
    }

    @Override
    public CompletableFuture<String> analyzeVideoAsync(Path video, String prompt) throws AIException {
        return interceptAsync(service -> service.analyzeVideoAsync(video, prompt));
    }

    @Override
    public CompletableFuture<String> analyzeVideoAsync(byte[] video, String prompt, AnalyzeVideoOptions options) throws AIException {
        return interceptAsync(service -> service.analyzeVideoAsync(video, prompt, options));
    }

    @Override
    public CompletableFuture<String> analyzeVideoAsync(Path video, String prompt, AnalyzeVideoOptions options) throws AIException {
        return interceptAsync(service -> service.analyzeVideoAsync(video, prompt, options));
    }

}
