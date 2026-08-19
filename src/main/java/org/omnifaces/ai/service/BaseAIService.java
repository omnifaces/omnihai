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

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Predicate.not;
import static java.util.logging.Level.WARNING;
import static org.omnifaces.ai.AIConfig.PROPERTY_API_KEY;
import static org.omnifaces.ai.AIConfig.PROPERTY_ENDPOINT;
import static org.omnifaces.ai.AIConfig.PROPERTY_MODEL;
import static org.omnifaces.ai.helper.JsonHelper.findNonBlankByPath;
import static org.omnifaces.ai.helper.TextHelper.isBlank;
import static org.omnifaces.ai.helper.TextHelper.requireNonBlank;
import static org.omnifaces.ai.mime.MimeType.guessMimeType;
import static org.omnifaces.ai.model.ChatOptions.DETERMINISTIC;
import static org.omnifaces.ai.model.ChatOptions.DETERMINISTIC_TEMPERATURE;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.omnifaces.ai.AIAudioHandler;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIImageHandler;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.AITextHandler;
import org.omnifaces.ai.AIVideoHandler;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.helper.JsonHelper;
import org.omnifaces.ai.helper.TextHelper;
import org.omnifaces.ai.model.AnalyzeVideoOptions;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatInput.Message;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatInput.UploadedFile;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatUsage;
import org.omnifaces.ai.model.GenerateAudioOptions;
import org.omnifaces.ai.model.GenerateImageOptions;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.ModerationResult;
import org.omnifaces.ai.model.Sse.Event;

/**
 * Base class for AI service implementations providing common API functionality.
 *
 * @see AIConfig
 * @see AIService
 * @author Bauke Scholtz
 * @since 1.0
 */
public abstract class BaseAIService implements AIService {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(BaseAIService.class.getPackageName());
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    /** Whether the "stale uploaded files cleanup" task is running. */
    private final AtomicBoolean staleUploadedFilesCleanupRunning = new AtomicBoolean();

    /** The shared HTTP client for API requests. */
    static final AIHttpClient HTTP_CLIENT = AIHttpClient.newInstance(DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);

    /** The AI provider for this service. */
    protected final AIProvider provider;
    /** The API key for authentication. */
    protected final String apiKey;
    /** The AI model name. */
    protected final String model;
    /** The API endpoint URI. */
    protected final URI endpoint;
    /** The AI chat prompt. */
    protected final String prompt;
    /** The AI text handler for this service. */
    protected final AITextHandler textHandler;
    /** The AI image handler for this service. */
    protected final AIImageHandler imageHandler;
    /** The AI audio handler for this service. */
    protected final AIAudioHandler audioHandler;

    /** The video handler. */
    protected final AIVideoHandler videoHandler;

    /**
     * Constructs an AI service with the specified configuration.
     *
     * @param config The AI configuration containing provider, API key, model, endpoint, prompt, and strategy settings.
     * @throws NullPointerException when config is null.
     * @throws IllegalArgumentException if the provider in the config doesn't match this service class, or if a handler class is unspecified.
     * @throws IllegalStateException If a required configuration property is missing, or if a handler class cannot be instantiated.
     */
    protected BaseAIService(AIConfig config) {
        this.provider = requireNonNull(config, "config").resolveProvider();

        if (provider.getServiceClass() != null && !provider.getServiceClass().isInstance(this)) {
            throw new IllegalArgumentException("Wrong AI provider for this service class");
        }

        this.apiKey = provider.isApiKeyRequired() ? config.require(PROPERTY_API_KEY) : config.apiKey();
        this.model = ofNullable(config.model()).or(() -> ofNullable(provider.getDefaultModel())).orElseGet(() -> config.require(PROPERTY_MODEL));
        this.endpoint = URI.create(
            ensureTrailingSlash(
                ofNullable(config.endpoint()).or(() -> ofNullable(provider.getDefaultEndpoint())).orElseGet(() -> config.require(PROPERTY_ENDPOINT))
            )
        );
        this.prompt = config.prompt();
        this.textHandler = createHandler(config.strategy().textHandler(), provider.getDefaultTextHandler(), "text");
        this.imageHandler = createHandler(config.strategy().imageHandler(), provider.getDefaultImageHandler(), "image");
        this.audioHandler = createHandler(config.strategy().audioHandler(), provider.getDefaultAudioHandler(), "audio");
        this.videoHandler = createHandler(config.strategy().videoHandler(), provider.getDefaultVideoHandler(), "video");
    }

    private static <T> T createHandler(Class<? extends T> configuredHandler, Class<? extends T> defaultHandler, String handlerName) {
        var handlerClass = configuredHandler == null ? defaultHandler : configuredHandler;

        if (handlerClass == null) {
            throw new IllegalArgumentException(
                "No " + handlerName + " handler configured. Custom providers must supply handlers via AIConfig.withStrategy(AIStrategy)."
            );
        }

        try {
            return handlerClass.getDeclaredConstructor().newInstance();
        }
        catch (Exception e) {
            throw new IllegalStateException("Cannot instantiate " + handlerName + " handler " + handlerClass.getName(), e);
        }
    }

    @Override
    public String getProviderName() {
        return provider.getName();
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public String getChatPrompt() {
        return prompt;
    }

    // Chat Implementation --------------------------------------------------------------------------------------------

    /**
     * Returns the path of the chat endpoint. E.g. {@code chat/completions} or {@code responses}.
     *
     * @param streaming Whether this is for chat streaming endpoint.
     * @return the path of the chat endpoint.
     */
    protected abstract String getChatPath(boolean streaming);

    @Override
    public CompletableFuture<String> chatAsync(ChatInput input, ChatOptions options) throws AIException {
        options.checkBudget();
        var effectiveInput = options.hasMemory() ? input.withHistory(precedingHistory(options, input.getMessage())) : input;

        // The user message must be recorded before the payload is built, because building it uploads any file attachments, and upload() anchors each file ID to
        // the most recent user message.
        if (options.hasMemory()) {
            recordUserMessage(options, input.getMessage());
        }

        // Building the payload uploads and awaits any file attachment, and therefore blocks. It belongs on the calling thread, which is the only one holding
        // the caller's transaction, scope and security context; only the provider call is asynchronous.
        var payload = textHandler.buildChatPayload(this, effectiveInput, options, false);
        var future = asyncPostAndParseChatResponse(getChatPath(false), payload, options);

        if (options.hasMemory()) {
            future = future.thenApply(response -> {
                options.recordMessage(Role.ASSISTANT, response);
                return response;
            });
        }

        return future;
    }

    @Override
    public CompletableFuture<Void> chatStream(ChatInput input, ChatOptions options, Consumer<String> onToken) {
        if (!supportsStreaming()) {
            throw new UnsupportedOperationException("Streaming is not supported by " + getName());
        }

        options.checkBudget();
        var effectiveInput = options.hasMemory() ? input.withHistory(precedingHistory(options, input.getMessage())) : input;

        // See chatAsync(ChatInput, ChatOptions) for why the user message is recorded before the payload is built.
        if (options.hasMemory()) {
            recordUserMessage(options, input.getMessage());
        }

        var payload = textHandler.buildChatPayload(this, effectiveInput, options, true);
        var responseAccumulator = options.hasMemory() ? new StringBuilder() : null;
        Consumer<String> effectiveOnToken = responseAccumulator != null ? token -> {
            responseAccumulator.append(token);
            onToken.accept(token);
        } : onToken;

        var callerStackTrace = new Exception("Caller stack trace");

        return asyncPostAndProcessStreamEvents(getChatPath(true), payload, event -> textHandler.processChatStreamEvent(this, options, event, effectiveOnToken))
            .handle((result, exception) -> {
                if (exception == null) {
                    if (responseAccumulator != null) {
                        options.recordMessage(Role.ASSISTANT, responseAccumulator.toString());
                    }

                    return result;
                }

                throw AIException.asyncRequestFailed(exception, callerStackTrace);
            });
    }

    /**
     * Records the current turn's user message, first discarding an identical one left behind by a failed prior attempt of the same request.
     * <p>
     * A re-attempt resends the identical message, so without this a retried or failed-over request would record the user message once per attempt and
     * accumulate the file references each attempt anchored to it. The identity is decided on content, so a trailing user message recorded by an unrelated call
     * or seeded manually is never mistaken for the current one and is left in place.
     */
    private static void recordUserMessage(ChatOptions options, String message) {
        var history = options.getHistory();

        if (!history.isEmpty()) {
            var last = history.get(history.size() - 1);

            if (last.role() == Role.USER && last.content().equals(message)) {
                options.discardPendingUserMessage();
            }
        }

        options.recordMessage(Role.USER, message);
    }

    /**
     * Returns the conversation history preceding the current turn, i.e. everything recorded before the given user message.
     * <p>
     * The payload carries the prior turns while the text handler adds the current user message separately, so carrying the current user message here as well
     * would make the request contain it twice. A trailing user message with the same content is such a current message, left behind by an attempt that failed
     * before it could record an assistant response, and is therefore excluded. The comparison is on content rather than on position, so that a message recorded
     * by an unrelated call is never mistaken for the current one.
     */
    private static List<Message> precedingHistory(ChatOptions options, String currentMessage) {
        var history = options.getHistory();

        if (!history.isEmpty()) {
            var last = history.get(history.size() - 1);

            if (last.role() == Role.USER && last.content().equals(currentMessage)) {
                return List.copyOf(history.subList(0, history.size() - 1));
            }
        }

        return List.copyOf(history);
    }

    // Files Implementation (for attaching files to chat) -------------------------------------------------------------

    /**
     * Returns the path of the files endpoint. E.g. {@code files}.
     *
     * @implNote The default implementation throws UnsupportedOperationException.
     * @return the path of the files endpoint.
     */
    protected String getFilesPath() {
        throw new UnsupportedOperationException("Please implement getFilesPath() method in class " + getClass().getSimpleName());
    }

    /**
     * This also cleans up uploaded files older than 2 days if {@link #getUploadedFileJsonStructure()} returns non-{@code null}. This is called as a
     * fire-and-forget task after each upload. Failures are logged at WARNING level and never propagated.
     */
    @Override
    public String upload(Attachment attachment, ChatOptions options) throws AIException {
        try {
            var responseJson = asyncUpload(getFilesPath(), attachment).join();
            var fileId = textHandler.parseFileResponse(responseJson);
            awaitUploadedFile(attachment, fileId, responseJson);

            if (getUploadedFileJsonStructure() != null && staleUploadedFilesCleanupRunning.compareAndSet(false, true)) {
                ExecutorServiceHelper.runAsync(() -> {
                    try {
                        cleanupStaleUploadedFiles();
                    }
                    finally {
                        staleUploadedFilesCleanupRunning.set(false);
                    }
                });
            }

            if (options.hasMemory()) {
                options.recordUploadedFile(new UploadedFile(fileId, attachment.mimeType(), attachment.videoOptions()));
            }

            return fileId;
        }
        catch (CompletionException e) {
            throw AIException.asyncRequestFailed(e);
        }
    }

    /**
     * Upload file attachment to API at given path along with request headers obtained from {@link #getRequestHeaders()}. The caller parses the file ID from the
     * returned response with help of {@link AITextHandler#parseFileResponse(JsonObject)}, and reads from it whatever else it needs, such as the state which
     * {@link #awaitUploadedFile(Attachment, String, JsonObject)} acts on.
     * <p>
     * This is package private on purpose: it is the seam which {@link #upload(Attachment, ChatOptions)} is built on rather than an extension point, and
     * {@link #asyncUploadAndParseFileIdResponse(String, Attachment)} is the one to call for just the file ID.
     *
     * @param path API path, relative to {@link #endpoint}.
     * @param attachment The file attachment to upload.
     * @return A CompletableFuture containing the upload response JSON.
     * @throws AIException if anything fails during the process.
     */
    CompletableFuture<JsonObject> asyncUpload(String path, Attachment attachment) throws AIException {
        return HTTP_CLIENT.upload(this, path, attachment);
    }

    /**
     * Awaits until the file uploaded by {@link #upload(Attachment, ChatOptions)} is ready to be referenced in a chat request. Providers which process an
     * uploaded file asynchronously, such as when extracting frames from a video, reject a chat request referencing a file which is still being processed.
     *
     * @implNote The default implementation does nothing, as most providers accept the file ID right away.
     * @param attachment The uploaded file attachment, whose {@link Attachment#size() size} indicates how long processing may reasonably take.
     * @param fileId The file ID as returned by the upload request.
     * @param responseJson The upload response JSON, which may already state whether the file is ready.
     * @throws AIException if the file cannot be processed by the AI provider.
     * @since 1.7
     */
    protected void awaitUploadedFile(Attachment attachment, String fileId, JsonObject responseJson) throws AIException {
        // NOOP.
    }

    /**
     * Describes the JSON structure of the file listing response, used by the clean up task of {@link BaseAIService#upload(Attachment, ChatOptions)} to identify
     * and delete stale uploads.
     *
     * @param filesArrayProperty JSON property name of the array containing file objects.
     * @param fileNameProperty JSON property name of the file name within each file object.
     * @param fileIdProperty JSON property name of the file ID within each file object.
     * @param createdAtProperty JSON property name of the creation timestamp within each file object.
     */
    protected final record UploadedFileJsonStructure(String filesArrayProperty, String fileNameProperty, String fileIdProperty, String createdAtProperty) {

        /**
         * Validates and normalizes the record components by stripping whitespace.
         *
         * @param filesArrayProperty JSON property name of the array containing file objects, may not be blank.
         * @param fileNameProperty JSON property name of the file name within each file object, may not be blank.
         * @param fileIdProperty JSON property name of the file ID within each file object, may not be blank.
         * @param createdAtProperty JSON property name of the creation timestamp within each file object, may not be blank.
         */
        public UploadedFileJsonStructure {
            filesArrayProperty = requireNonBlank(filesArrayProperty, "filesArrayProperty");
            fileNameProperty = requireNonBlank(fileNameProperty, "fileNameProperty");
            fileIdProperty = requireNonBlank(fileIdProperty, "fileIdProperty");
            createdAtProperty = requireNonBlank(createdAtProperty, "createdAtProperty");
        }

    }

    /**
     * Returns the uploaded file JSON structure which will be used for automatic cleanup of stale files.
     *
     * @implNote The default implementation returns {@code null}, indicating that cleanup is not needed.
     * @return The uploaded file JSON structure, or {@code null} if cleanup is not needed (i.e. the AI provider already automatically does that).
     */
    protected UploadedFileJsonStructure getUploadedFileJsonStructure() {
        return null;
    }

    private void cleanupStaleUploadedFiles() {
        var jsonStructure = getUploadedFileJsonStructure();

        if (jsonStructure == null) {
            throw new IllegalStateException();
        }

        try {
            var responseJson = HTTP_CLIENT.get(this, getFilesPath()).join();
            var files = responseJson.getJsonArray(jsonStructure.filesArrayProperty);

            if (files == null || files.isEmpty()) {
                return;
            }

            var cutoff = Instant.now().minus(2, ChronoUnit.DAYS); // Same default as Google AI.

            files.stream()
                .map(JsonValue::asJsonObject)
                .filter(file -> isEligibleForCleanup(file, jsonStructure))
                .forEach(file -> deleteFileQuietly(file, jsonStructure, cutoff));
        }
        catch (Exception e) {
            logger.log(WARNING, "Failed to list files for cleanup", e);
        }
    }

    private static boolean isEligibleForCleanup(JsonObject file, UploadedFileJsonStructure jsonStructure) {
        return findNonBlankByPath(file, jsonStructure.fileNameProperty)
            .filter(name -> name.startsWith(HTTP_CLIENT.uploadedFileNamePrefix))
            .isPresent();
    }

    private void deleteFileQuietly(JsonObject file, UploadedFileJsonStructure jsonStructure, Instant cutoff) {
        try {
            var id = findNonBlankByPath(file, jsonStructure.fileIdProperty);
            var createdAt = findNonBlankByPath(file, jsonStructure.createdAtProperty);

            if (id.isPresent() && createdAt.isPresent()) {
                var timestamp = tryParseFileCreatedAtTimestamp(createdAt.get());

                if (timestamp.isBefore(cutoff)) {
                    HTTP_CLIENT.delete(this, getFilesPath() + "/" + id.get()).join();
                }
            }
        }
        catch (Exception e) {
            logger.log(WARNING, "Failed to cleanup file: " + file, e);
        }
    }

    private static Instant tryParseFileCreatedAtTimestamp(String createdAt) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(createdAt)); // At least OpenAI, Mistral and xAI.
        }
        catch (NumberFormatException ignore) {
            return Instant.parse(createdAt); // At least Anthropic.
        }
    }

    // Text Analysis Implementation (delegates to chat) ---------------------------------------------------------------

    @Override
    public CompletableFuture<String> summarizeAsync(String text, int maxWords) throws AIException {
        var options = ChatOptions.newBuilder()
            .systemPrompt(textHandler.buildSummarizePrompt(maxWords))
            .temperature(textHandler.getDefaultCreativeTemperature())
            .build();

        return chatAsync(requireNonBlank(text, "text"), options);
    }

    @Override
    public CompletableFuture<List<String>> extractKeyPointsAsync(String text, int maxPoints) throws AIException {
        var options = ChatOptions.newBuilder()
            .systemPrompt(textHandler.buildExtractKeyPointsPrompt(maxPoints))
            .temperature(textHandler.getDefaultCreativeTemperature())
            .build();

        return chatAsync(requireNonBlank(text, "text"), options)
            .thenApply(response -> Arrays.asList(response.split("\n")).stream().map(String::strip).filter(not(TextHelper::isBlank)).toList());
    }

    // Text Translation Implementation (delegates to chat) ------------------------------------------------------------

    @Override
    public CompletableFuture<String> detectLanguageAsync(String text) throws AIException {
        return chatAsync(requireNonBlank(text, "text"), DETERMINISTIC.withSystemPrompt(textHandler.buildDetectLanguagePrompt())).thenApply(response -> {
            if (isBlank(response)) {
                throw new AIResponseException("Response is empty", response);
            }

            return response.strip().toLowerCase().replaceAll("[^a-z]", "");
        });
    }

    @Override
    public CompletableFuture<String> translateAsync(String text, String sourceLang, String targetLang) throws AIException {
        return chatAsync(
            requireNonBlank(text, "text"),
            DETERMINISTIC.withSystemPrompt(textHandler.buildTranslatePrompt(sourceLang, requireNonBlank(targetLang, "target language")))
        );
    }

    // Text Proofreading Implementation (delegates to chat) -----------------------------------------------------------

    @Override
    public CompletableFuture<String> proofreadAsync(String text) throws AIException {
        return chatAsync(requireNonBlank(text, "text"), DETERMINISTIC.withSystemPrompt(textHandler.buildProofreadPrompt()));
    }

    // Text Moderation Implementation (delegates to chat) -------------------------------------------------------------

    @Override
    public CompletableFuture<ModerationResult> moderateContentAsync(String content, ModerationOptions options) throws AIException {
        if (options.getCategories().isEmpty()) {
            return completedFuture(ModerationResult.SAFE);
        }

        var chatOptions = ChatOptions.newBuilder()
            .systemPrompt(textHandler.buildModerationPrompt(options))
            .jsonSchema(buildModerationJsonSchema(options))
            .temperature(DETERMINISTIC_TEMPERATURE)
            .build();

        return chatAsync(requireNonBlank(content, "content"), chatOptions).thenApply(JsonHelper::parseJson)
            .thenApply(response -> parseModerationResult(response, options));
    }

    private static JsonObject buildModerationJsonSchema(ModerationOptions options) {
        var categoryProperties = Json.createObjectBuilder();
        var requiredCategories = Json.createArrayBuilder();

        for (var category : options.getCategories()) {
            categoryProperties.add(category, Json.createObjectBuilder().add("type", "number"));
            requiredCategories.add(category);
        }

        var scoresSchema = Json.createObjectBuilder()
            .add("type", "object")
            .add("properties", categoryProperties)
            .add("required", requiredCategories);

        return Json.createObjectBuilder()
            .add("type", "object")
            .add("properties", Json.createObjectBuilder().add("scores", scoresSchema))
            .add("required", Json.createArrayBuilder().add("scores"))
            .build();
    }

    private static ModerationResult parseModerationResult(JsonObject responseJson, ModerationOptions options) throws AIResponseException {
        var scores = new TreeMap<String, Double>();
        var flagged = false;

        if (responseJson.containsKey("scores")) {
            var categoryScores = responseJson.getJsonObject("scores");
            for (String category : options.getCategories()) {
                if (categoryScores.containsKey(category)) {
                    double score = categoryScores.getJsonNumber(category).doubleValue();
                    scores.put(category, score);
                    if (score > options.getThreshold()) {
                        flagged = true;
                    }
                }
            }
        }

        return new ModerationResult(flagged, scores);
    }

    // Image Analysis Implementation (delegates to chat) --------------------------------------------------------------

    @Override
    public CompletableFuture<String> analyzeImageAsync(byte[] image, String prompt) throws AIException {
        return analyzeImageAsync(input -> input.attach(image), prompt);
    }

    @Override
    public CompletableFuture<String> analyzeImageAsync(Path image, String prompt) throws AIException {
        return analyzeImageAsync(input -> input.attach(image), prompt);
    }

    private CompletableFuture<String> analyzeImageAsync(Consumer<ChatInput.Builder> inputBuilder, String prompt) throws AIException {
        var input = ChatInput.newBuilder().message(isBlank(prompt) ? "Analyze image" : prompt);
        inputBuilder.accept(input);
        var options = DETERMINISTIC.withSystemPrompt(isBlank(prompt) ? imageHandler.buildAnalyzeImagePrompt() : null);
        return asyncPostAndParseChatResponse(getChatPath(false), textHandler.buildChatPayload(this, input.build(), options, false), null);
    }

    @Override
    public CompletableFuture<String> generateAltTextAsync(byte[] image) throws AIException {
        return analyzeImageAsync(image, imageHandler.buildGenerateAltTextPrompt());
    }

    @Override
    public CompletableFuture<String> generateAltTextAsync(Path image) throws AIException {
        return analyzeImageAsync(image, imageHandler.buildGenerateAltTextPrompt());
    }

    // Image Generator Implementation ---------------------------------------------------------------------------------

    /**
     * Returns the path of the image generation endpoint. E.g. {@code images/generations}.
     *
     * @implNote The default implementation delegates to {@link #getChatPath(boolean)} with {@code false}.
     * @return the path of the image generation endpoint.
     */
    protected String getGenerateImagePath() {
        return getChatPath(false);
    }

    @Override
    public CompletableFuture<byte[]> generateImageAsync(String prompt, GenerateImageOptions options) throws AIException {
        return asyncPostAndParseImageContent(getGenerateImagePath(), imageHandler.buildGenerateImagePayload(this, requireNonBlank(prompt, "prompt"), options));
    }

    // Audio Transcription Implementation ------------------------------------------------------------------------------

    @Override
    public CompletableFuture<String> transcribeAsync(byte[] audio) throws AIException {
        return transcribeAsync(input -> input.attach(audio));
    }

    @Override
    public CompletableFuture<String> transcribeAsync(Path audio) throws AIException {
        return transcribeAsync(input -> input.attach(audio));
    }

    private CompletableFuture<String> transcribeAsync(Consumer<ChatInput.Builder> inputBuilder) throws AIException {
        var input = ChatInput.newBuilder().message("Transcribe audio");
        inputBuilder.accept(input);
        var options = DETERMINISTIC.withSystemPrompt(audioHandler.buildTranscribePrompt());
        return asyncPostAndParseChatResponse(getChatPath(false), textHandler.buildChatPayload(this, input.build(), options, false), null);
    }

    // Audio Generator Implementation -------------------------------------------------------------------------------------------

    /**
     * Returns the path of the audio generation (text-to-speech) endpoint.
     *
     * @implNote The default implementation delegates to {@link #getChatPath(boolean)} with {@code false}.
     * @return the path of the audio generation endpoint.
     * @since 1.2
     */
    protected String getGenerateAudioPath() {
        return getChatPath(false);
    }

    @Override
    public CompletableFuture<byte[]> generateAudioAsync(String text, GenerateAudioOptions options) {
        return asyncPostAndStreamAudioContent(getGenerateAudioPath(), audioHandler.buildGenerateAudioPayload(this, requireNonBlank(text, "text"), options))
            .thenApply(stream -> {
                try {
                    return stream.readAllBytes();
                }
                catch (IOException e) {
                    throw new UncheckedIOException("Cannot read all bytes from response body", e);
                }
            });
    }

    @Override
    public CompletableFuture<Void> generateAudioAsync(String text, Path path, GenerateAudioOptions options) {
        return asyncPostAndStreamAudioContent(getGenerateAudioPath(), audioHandler.buildGenerateAudioPayload(this, requireNonBlank(text, "text"), options))
            .thenApply(stream -> {
                try {
                    Files.copy(stream, path, StandardCopyOption.REPLACE_EXISTING);
                    return null;
                }
                catch (IOException e) {
                    throw new CompletionException("Cannot stream response body to path " + path, e);
                }
            });
    }

    // Video Analysis Implementation (delegates to chat) --------------------------------------------------------------

    @Override
    public CompletableFuture<String> analyzeVideoAsync(byte[] video, String prompt, AnalyzeVideoOptions options) throws AIException {
        requireNonNull(options, "options");
        var mimeType = guessMimeType(video);
        return analyzeVideoAsync(new Attachment(video, mimeType, "video." + mimeType.extension()), prompt, options);
    }

    @Override
    public CompletableFuture<String> analyzeVideoAsync(Path video, String prompt, AnalyzeVideoOptions options) throws AIException {
        requireNonNull(options, "options");
        return analyzeVideoAsync(new Attachment(video), prompt, options);
    }

    private CompletableFuture<String> analyzeVideoAsync(Attachment video, String prompt, AnalyzeVideoOptions videoOptions) throws AIException {
        if (!video.mimeType().isVideo()) {
            throw new IllegalArgumentException("Cannot recognize " + video.mimeType().value() + " as video");
        }

        var input = ChatInput.newBuilder()
            .message(isBlank(prompt) ? "Analyze video" : prompt)
            .attach(video.withVideoOptions(videoOptions))
            .build();
        var options = DETERMINISTIC.withSystemPrompt(isBlank(prompt) ? videoHandler.buildAnalyzeVideoPrompt() : null);
        return asyncPostAndParseChatResponse(getChatPath(false), textHandler.buildChatPayload(this, input, options, false), null);
    }

    // HTTP Helper Methods --------------------------------------------------------------------------------------------

    private static String ensureTrailingSlash(String uri) {
        return uri.endsWith("/") ? uri : uri.concat("/");
    }

    /**
     * Returns additional request headers to use at {@link #asyncPostAndParseChatResponse(String, JsonObject, ChatOptions)}, e.g. authorization or version
     * headers. These headers are added on top of the default request headers: {@code User-Agent}, {@code Content-Type} and {@code Accept}.
     *
     * @implNote The default implementation returns an empty map.
     * @return Additional request headers to use at {@link #asyncPostAndParseChatResponse(String, JsonObject, ChatOptions)}.
     */
    protected Map<String, String> getRequestHeaders() {
        return emptyMap();
    }

    /**
     * Resolve URI of the given path based on endpoint URI.
     *
     * @param path The path to resolve the URI for based on endpoint URI.
     * @return Resolved URI of the given path based on endpoint URI.
     */
    protected URI resolveURI(String path) {
        return endpoint.resolve(path);
    }

    /**
     * Send POST request to API at given path with given payload along with request headers obtained from {@link #getRequestHeaders()}, and parse chat response
     * from the POST response with help of {@link AITextHandler#parseChatResponse(JsonObject)}.
     *
     * @param path API path, relative to {@link #endpoint}.
     * @param payload POST request payload.
     * @param options The user-supplied chat options, or {@code null} if there is none. Implementations should call {@link ChatOptions#recordUsage(ChatUsage)}
     * when usage data is available in the response.
     * @return The message content of the POST request.
     * @throws AIException if anything fails during the process.
     */
    protected CompletableFuture<String> asyncPostAndParseChatResponse(String path, JsonObject payload, ChatOptions options) throws AIException {
        return HTTP_CLIENT.post(this, path, payload).thenApply(response -> {
            if (options != null && !options.isDefault()) {
                options.recordUsage(textHandler.parseChatUsage(response));
            }

            return textHandler.parseChatResponse(response);
        });
    }

    /**
     * Upload file attachment to API at given path along with request headers obtained from {@link #getRequestHeaders()}, and parse file ID from the response
     * with help of {@link AITextHandler#parseFileResponse(JsonObject)}.
     *
     * @param path API path, relative to {@link #endpoint}.
     * @param attachment The file attachment to upload.
     * @return A CompletableFuture containing the file ID from the upload response.
     * @throws AIException if anything fails during the process.
     * @implNote {@link #upload(Attachment, ChatOptions)} obtains the whole response instead, as {@link #awaitUploadedFile(Attachment, String, JsonObject)}
     * needs the state which it states, so overriding this one does not intercept an upload; override {@link #asyncUpload(String, Attachment)} for that.
     */
    protected CompletableFuture<String> asyncUploadAndParseFileIdResponse(String path, Attachment attachment) throws AIException {
        return asyncUpload(path, attachment).thenApply(textHandler::parseFileResponse);
    }

    /**
     * Send POST request to API at given path with given payload along with request headers obtained from {@link #getRequestHeaders()}, and parse image content
     * from the POST response with help of {@link AIImageHandler#parseImageContent(JsonObject)}.
     *
     * @param path API path, relative to {@link #endpoint}.
     * @param payload POST request payload.
     * @return The image content of the POST request.
     * @throws AIException if anything fails during the process.
     */
    protected CompletableFuture<byte[]> asyncPostAndParseImageContent(String path, JsonObject payload) throws AIException {
        return HTTP_CLIENT.post(this, path, payload).thenApply(imageHandler::parseImageContent);
    }

    /**
     * Send SSE request to API at given path with given payload along with request headers obtained from {@link #getRequestHeaders()}, and process each reveived
     * stream event using supplied {@code eventProcessor}.
     *
     * @param path API path, relative to {@link #endpoint}.
     * @param payload Initial SSE POST request payload.
     * @param eventProcessor Callback invoked for each stream event; it must return {@code true} to continue processing the stream, or {@code false} to stop
     * processing the stream.
     * @return A future that completes when stream ends normally, is stopped by the processor, or fails exceptionally.
     * @throws AIException if anything fails during the process.
     */
    protected CompletableFuture<Void> asyncPostAndProcessStreamEvents(String path, JsonObject payload, Predicate<Event> eventProcessor) throws AIException {
        return HTTP_CLIENT.stream(this, path, payload, eventProcessor);
    }

    /**
     * Send POST request to API at given path with given payload along with request headers obtained from {@link #getRequestHeaders()}, and parse image content
     * from the POST response with help of {@link AIImageHandler#parseImageContent(JsonObject)}.
     *
     * @param path API path, relative to {@link #endpoint}.
     * @param payload POST request payload.
     * @return The image content of the POST request.
     * @throws AIException if anything fails during the process.
     * @since 1.2
     */
    protected CompletableFuture<InputStream> asyncPostAndStreamAudioContent(String path, JsonObject payload) throws AIException {
        return HTTP_CLIENT.stream(this, path, payload).thenApply(audioHandler::parseAudioContent);
    }

}
