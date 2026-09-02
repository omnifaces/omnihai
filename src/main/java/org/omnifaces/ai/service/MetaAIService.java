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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.omnifaces.ai.AIAudioHandler;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.modality.MetaAIAudioHandler;
import org.omnifaces.ai.model.ChatInput.Attachment;

/**
 * AI service implementation using Meta AI API.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 * <li>provider: {@link AIProvider#META}</li>
 * <li>apiKey: your Meta API key</li>
 * </ul>
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional. See {@link AIProvider#META} for defaults.
 * <ul>
 * <li>model: the model to use</li>
 * <li>endpoint: the API endpoint URL</li>
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider#META
 * @see MetaAIAudioHandler
 * @see OpenAIService
 * @see BaseAIService
 * @see AIService
 * @see <a href="https://dev.meta.ai/docs/getting-started/overview/">API Reference</a>
 */
public class MetaAIService extends OpenAIService {

    private static final long serialVersionUID = 1L;

    private static final String TRANSCRIBE_MODEL_NAME_PART = "transcribe";
    private static final String TRANSCRIBE_PATH = "asr/transcribe";

    /** The name of the multipart part carrying the JSON handshake of a transcribe request: {@value} */
    static final String TRANSCRIBE_REQUEST_PART_NAME = "request";
    /** The name of the multipart part carrying the audio of a transcribe request: {@value} */
    static final String TRANSCRIBE_FILE_PART_NAME = "audio";

    /**
     * Constructs a Meta AI service with the specified configuration.
     *
     * @param config the AI configuration
     * @see AIConfig
     */
    public MetaAIService(AIConfig config) {
        super(config);
    }

    @Override
    public boolean supportsModality(AIModality modality) {
        return switch (modality) {
            case IMAGE_ANALYSIS, VIDEO_ANALYSIS -> !isTranscribeModel();
            case AUDIO_ANALYSIS -> isTranscribeModel();
            default -> false;
        };
    }

    /**
     * Returns whether the configured model is the one serving the ASR endpoint, which is the only Meta AI model that takes audio, and takes nothing else.
     *
     * @return Whether the configured model is the one serving the ASR endpoint.
     */
    private boolean isTranscribeModel() {
        return getModelName().toLowerCase(Locale.ROOT).contains(TRANSCRIBE_MODEL_NAME_PART);
    }

    @Override
    public boolean supportsFileAttachments() {
        return true; // Not version-bound, support is API-bound.
    }

    @Override
    public boolean supportsStructuredOutput() {
        return true; // Not version-bound, support is API-bound.
    }

    @Override
    public boolean supportsReasoningEffort() {
        return true; // Not version-bound, support is API-bound.
    }

    @Override
    public boolean supportsOpenAIResponsesApi() {
        return true;
    }

    @Override
    public boolean supportsOpenAIModerationCapability(Set<String> categories) {
        return false;
    }

    /**
     * Meta AI has no OpenAI compatible transcription endpoint, and has no chat model taking audio to fall back to either. {@link #transcribeAsync(byte[])}
     * addresses the Meta ASR endpoint whatever this answers.
     */
    @Override
    public boolean supportsOpenAITranscriptionCapability() {
        return false;
    }

    /**
     * Meta AI accepts {@code minimal} as lowest reasoning effort and rejects {@code none}.
     */
    @Override
    public boolean supportsOpenAIReasoningEffortNone() {
        return false;
    }

    /**
     * Meta AI accepts only {@code auto} as tool choice.
     */
    @Override
    public boolean supportsOpenAIToolChoiceRequired() {
        return false;
    }

    /**
     * Meta AI takes the {@code user_location} of its web search tool without applying it to the search.
     */
    @Override
    public boolean supportsOpenAIWebSearchUserLocation() {
        return false;
    }

    @Override
    public CompletableFuture<String> transcribeAsync(byte[] audio) throws AIException {
        return transcribeMetaAsync(audioHandler.buildTranscribeContent(audio));
    }

    /**
     * The audio is read into memory as a whole, as the conversion into the format which the ASR endpoint accepts needs it there anyway.
     */
    @Override
    public CompletableFuture<String> transcribeAsync(Path audio) throws AIException {
        try {
            return transcribeAsync(Files.readAllBytes(audio));
        }
        catch (IOException e) {
            throw new AIException("Cannot read audio " + audio, e);
        }
    }

    /**
     * Returns the path of the transcription endpoint.
     *
     * @return The path of the transcription endpoint.
     * @since 1.7.1
     * @see <a href="https://dev.meta.ai/docs/speech-to-text">API Reference</a>
     */
    protected String getTranscribePath() {
        return TRANSCRIBE_PATH;
    }

    /**
     * Builds the audio attachment of a transcribe request, carrying the JSON handshake as the part named {@value #TRANSCRIBE_REQUEST_PART_NAME}, which the ASR
     * endpoint requires next to the audio part.
     *
     * @param audio The audio content, in the format which {@link AIAudioHandler#buildTranscribeContent(byte[])} converted it to.
     * @return The audio attachment of a transcribe request.
     */
    Attachment newTranscribeAttachment(byte[] audio) {
        var request = Map.of(TRANSCRIBE_REQUEST_PART_NAME, audioHandler.buildTranscribePayload(this).toString());
        var mimeType = MimeType.guessMimeType(audio);
        return new Attachment(audio, mimeType, "audio." + mimeType.extension(), request);
    }

    private CompletableFuture<String> transcribeMetaAsync(byte[] audio) {
        return HTTP_CLIENT.upload(this, getTranscribePath(), newTranscribeAttachment(audio), TRANSCRIBE_FILE_PART_NAME)
            .thenApply(audioHandler::parseTranscribeResponse);
    }

}
