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

import static java.lang.String.format;
import static org.omnifaces.ai.helper.JsonHelper.findFirstByPath;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

import jakarta.json.JsonObject;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.model.ChatInput.Attachment;

/**
 * AI service implementation using Google AI API.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 * <li>provider: {@link AIProvider#GOOGLE}</li>
 * <li>apiKey: your Google API key</li>
 * </ul>
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional. See {@link AIProvider#GOOGLE} for defaults.
 * <ul>
 * <li>model: the model to use</li>
 * <li>endpoint: the API endpoint URL</li>
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider#GOOGLE
 * @see BaseAIService
 * @see AIService
 * @see <a href="https://ai.google.dev/api">API Reference</a>
 */
public class GoogleAIService extends BaseAIService {

    private static final long serialVersionUID = 1L;

    private static final AIModelVersion GEMINI_1_5 = AIModelVersion.of("gemini", 1, 5);
    private static final String VEO_MODEL_NAME = "veo";
    private static final String OPERATIONS_PATH_PREFIX = "operations/";
    private static final String ABSOLUTE_URI_PREFIX = "https://";
    private static final AIModelVersion GEMINI_2 = AIModelVersion.of("gemini", 2);
    private static final AIModelVersion GEMINI_2_5 = AIModelVersion.of("gemini", 2, 5);
    private static final AIModelVersion GEMINI_3 = AIModelVersion.of("gemini", 3);

    private static final String FILE_STATE_ACTIVE = "ACTIVE";
    private static final String FILE_STATE_PROCESSING = "PROCESSING";
    private static final String FILE_STATE_UNSPECIFIED = "STATE_UNSPECIFIED";
    private static final String FILE_STATE_FAILED = "FAILED";
    private static final String FILE_ERROR_MESSAGE_PATH = "error.message";

    private static final Duration INITIAL_UPLOADED_FILE_POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration MAX_UPLOADED_FILE_POLL_INTERVAL = Duration.ofSeconds(15);
    private static final double UPLOADED_FILE_POLL_BACKOFF_MULTIPLIER = 1.5;

    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;
    private static final Duration UPLOADED_FILE_PROCESSING_TIME_PER_MEGABYTE = Duration.ofSeconds(2);
    private static final Duration MIN_UPLOADED_FILE_PROCESSING_TIME = Duration.ofMinutes(1);
    private static final Duration MAX_UPLOADED_FILE_PROCESSING_TIME = Duration.ofMinutes(15);

    /**
     * Constructs a Google AI service with the specified configuration.
     *
     * @param config the AI configuration
     * @see AIConfig
     */
    public GoogleAIService(AIConfig config) {
        super(config);
    }

    @Override
    public boolean supportsModality(AIModality modality) {
        var currentModelVersion = getModelVersion();
        var fullModelName = getModelName().toLowerCase(Locale.ROOT);

        return switch (modality) {
            case IMAGE_ANALYSIS -> true;
            case IMAGE_GENERATION -> currentModelVersion.gte(GEMINI_2) || fullModelName.contains("image");
            case AUDIO_ANALYSIS -> currentModelVersion.gte(GEMINI_1_5);
            case AUDIO_GENERATION -> currentModelVersion.gte(GEMINI_2_5) || fullModelName.contains("tts");
            case VIDEO_ANALYSIS -> currentModelVersion.gte(GEMINI_1_5);
            case VIDEO_GENERATION -> fullModelName.startsWith(VEO_MODEL_NAME);
            default -> false;
        };
    }

    /**
     * Google AI supports streaming, but it comes in big chunks. According to Gemini it's caused by "Safety Filter" bottleneck whereby the AI doublechecks every
     * paragraph before sending out, so it basically comes in paragraphs.
     */
    @Override
    public boolean supportsStreaming() {
        return true; // Not version-bound, support is API-bound.
    }

    @Override
    public boolean supportsFileAttachments() {
        return true; // Not version-bound, support is API-bound.
    }

    @Override
    public boolean supportsFileAttachmentsInHistory() {
        return true;
    }

    @Override
    public boolean supportsStructuredOutput() {
        return getModelVersion().gte(GEMINI_2);
    }

    @Override
    public boolean supportsWebSearch() {
        return true; // Not version-bound, support is API-bound.
    }

    @Override
    public boolean supportsReasoningEffort() {
        return getModelVersion().gte(GEMINI_3);
    }

    @Override
    protected URI resolveURI(String path) {
        if (path.startsWith(OPERATIONS_PATH_PREFIX) || path.contains("/" + OPERATIONS_PATH_PREFIX) || path.startsWith(ABSOLUTE_URI_PREFIX)) {
            var uri = super.resolveURI(path);

            if (!isSameOrigin(uri)) {
                return uri;
            }

            return super.resolveURI(path + (path.contains("?") ? "&" : "?") + format("key=%s", apiKey));
        }
        else if (path.equals(getFilesPath())) {
            return super.resolveURI("../upload/v1beta/" + format("%s?key=%s", getFilesPath(), apiKey));
        }
        else if (path.startsWith(getFilesPath() + "/")) {
            return super.resolveURI(format("%s?key=%s", path, apiKey));
        }
        else {
            var parts = path.split("\\?", 2);
            var query = parts.length > 1 ? ("&" + parts[1]) : "";
            return super.resolveURI(format("models/%s:%s?key=%s%s", model, parts[0], apiKey, query));
        }
    }

    /**
     * Returns {@code streamGenerateContent?alt=sse} if streaming, {@code generateContent} otherwise.
     */
    @Override
    protected String getChatPath(boolean streaming) {
        return streaming ? "streamGenerateContent?alt=sse" : "generateContent";
    }

    /**
     * Google AI processes an uploaded file asynchronously and rejects a chat request referencing a file which is not yet active. Video takes the longest, as
     * every sampled frame is extracted up front. This blocks the calling thread until the file is active, at most {@link #maxProcessingTime(Attachment)}. A
     * file which is already active on the first poll does not block at all.
     */
    @Override
    protected void awaitUploadedFile(Attachment attachment, String fileId) throws AIException {
        var index = fileId.lastIndexOf(getFilesPath() + "/");

        if (index < 0) {
            return;
        }

        var filePath = fileId.substring(index);
        var maxProcessingTime = maxProcessingTime(attachment);
        var startNanos = System.nanoTime();
        var pollInterval = INITIAL_UPLOADED_FILE_POLL_INTERVAL;

        while (isStillProcessing(filePath, pollUploadedFile(filePath))) {
            if (System.nanoTime() - startNanos >= maxProcessingTime.toNanos()) {
                throw new AIException("Uploaded file " + filePath + " is still being processed after " + maxProcessingTime.toSeconds() + " seconds");
            }

            sleep(pollInterval);
            pollInterval = nextPollInterval(pollInterval);
        }
    }

    /**
     * Returns how long the given attachment may reasonably take to process, derived from its size and bounded by {@link #MIN_UPLOADED_FILE_PROCESSING_TIME} and
     * {@link #MAX_UPLOADED_FILE_PROCESSING_TIME}, as processing time grows with the amount of content to extract.
     *
     * @param attachment The uploaded file attachment.
     * @return How long the given attachment may reasonably take to process.
     */
    static Duration maxProcessingTime(Attachment attachment) {
        var derived = UPLOADED_FILE_PROCESSING_TIME_PER_MEGABYTE.multipliedBy(attachment.size() / BYTES_PER_MEGABYTE);

        if (derived.compareTo(MIN_UPLOADED_FILE_PROCESSING_TIME) < 0) {
            return MIN_UPLOADED_FILE_PROCESSING_TIME;
        }

        return derived.compareTo(MAX_UPLOADED_FILE_PROCESSING_TIME) > 0 ? MAX_UPLOADED_FILE_PROCESSING_TIME : derived;
    }

    private JsonObject pollUploadedFile(String filePath) {
        return HTTP_CLIENT.get(this, filePath).join();
    }

    private static boolean isStillProcessing(String filePath, JsonObject file) {
        if (file == null) {
            return true; // State unknown, so it must be polled.
        }

        var state = file.getString("state", FILE_STATE_ACTIVE);

        if (FILE_STATE_FAILED.equals(state)) {
            throw new AIException("Uploaded file " + filePath + " failed to process: " + findFirstByPath(file, FILE_ERROR_MESSAGE_PATH).orElse(state));
        }
        else if (!FILE_STATE_ACTIVE.equals(state) && !FILE_STATE_PROCESSING.equals(state) && !FILE_STATE_UNSPECIFIED.equals(state)) {
            throw new AIException("Uploaded file " + filePath + " ended up in state " + state);
        }

        return !FILE_STATE_ACTIVE.equals(state);
    }

    private static Duration nextPollInterval(Duration pollInterval) {
        var next = Duration.ofMillis((long) (pollInterval.toMillis() * UPLOADED_FILE_POLL_BACKOFF_MULTIPLIER));
        return next.compareTo(MAX_UPLOADED_FILE_POLL_INTERVAL) > 0 ? MAX_UPLOADED_FILE_POLL_INTERVAL : next;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AIException("Interrupted while awaiting the uploaded file", e);
        }
    }

    @Override
    protected String getFilesPath() {
        return "files";
    }

    /**
     * Returns {@code predictLongRunning}, as Veo is submitted as a long running operation.
     */
    @Override
    protected String getGenerateVideoPath() {
        return "predictLongRunning";
    }

    /**
     * Returns the job id unchanged, as the id which Google assigns a video generation job is the name of the operation to poll it at, rather than a segment
     * below the path it was submitted to.
     */
    @Override
    protected String getVideoGenerationPath(String jobId) {
        return jobId;
    }

}
