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

import static org.omnifaces.ai.service.ModelModalitiesRegistry.findModelModalities;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.ChatInput.Attachment;

/**
 * AI service implementation using Hugging Face API.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 * <li>provider: {@link AIProvider#HUGGINGFACE}</li>
 * <li>apiKey: your Hugging Face API key</li>
 * </ul>
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional. See {@link AIProvider#HUGGINGFACE} for defaults.
 * <ul>
 * <li>model: the model to use</li>
 * <li>endpoint: the API endpoint URL</li>
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider#HUGGINGFACE
 * @see OpenAIService
 * @see BaseAIService
 * @see AIService
 * @see <a href="https://huggingface.co/docs/inference-providers/en/tasks/index">API Reference</a>
 */
public class HuggingFaceAIService extends OpenAIService {

    private static final long serialVersionUID = 1L;

    /**
     * The modalities which the listing may report but no call can serve: OmniHai has no video generation API at all, and Hugging Face is wired with the
     * {@link org.omnifaces.ai.modality.DefaultAIAudioHandler}, which does not implement audio generation.
     */
    private static final Set<AIModality> UNSERVEABLE_MODALITIES = EnumSet.of(AIModality.AUDIO_GENERATION, AIModality.VIDEO_GENERATION);

    /**
     * Constructs a Hugging Face AI service with the specified configuration.
     *
     * @param config the AI configuration
     * @see AIConfig
     */
    public HuggingFaceAIService(AIConfig config) {
        super(config);
    }

    /**
     * Hugging Face publishes the input and output modalities of every routed model, so they are looked up rather than guessed from the model name. Matching the
     * model name against the modality name is the fallback for as long as the listing cannot be obtained, and for a model which is not in it.
     *
     * @see <a href="https://huggingface.co/docs/inference-providers/index">Inference Providers API Reference</a>
     */
    @Override
    public boolean supportsModality(AIModality modality) {
        if (UNSERVEABLE_MODALITIES.contains(modality)) {
            return false;
        }

        return findModelModalities(this).map(modalities -> modalities.contains(modality)).orElseGet(() -> supportsModalityByModelName(modality));
    }

    /**
     * Reads the modalities from the model name, which is what is left when the listing cannot be obtained. The match is deliberately narrow: a name states a
     * modality only where the model is named after it, so a wrong yes, which fails at the provider, is traded for a wrong no, which refuses gracefully.
     */
    boolean supportsModalityByModelName(AIModality modality) {
        var fullModelName = getModelName().toLowerCase(Locale.ROOT);

        return switch (modality) {
            case IMAGE_ANALYSIS -> true;
            case IMAGE_GENERATION -> fullModelName.contains("image");
            case AUDIO_ANALYSIS -> fullModelName.contains("transcribe") || fullModelName.contains("whisper");
            case VIDEO_ANALYSIS -> fullModelName.contains("video");
            case AUDIO_GENERATION, VIDEO_GENERATION -> false;
        };
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public boolean supportsFileAttachments() {
        return false;
    }

    @Override
    public boolean supportsStructuredOutput() {
        return true;
    }

    @Override
    public boolean supportsOpenAIResponsesApi() {
        return false;
    }

    @Override
    public boolean supportsOpenAIFilesApi() {
        return false;
    }

    @Override
    public boolean supportsOpenAIModerationCapability(Set<String> categories) {
        return false;
    }

    @Override
    public boolean supportsOpenAITranscriptionCapability() {
        return false;
    }

    @Override
    public CompletableFuture<String> transcribeAsync(byte[] audio) throws AIException {
        var mimeType = MimeType.guessMimeType(audio);
        return HTTP_CLIENT.post(this, "../hf-inference/models/" + getModelName(), new Attachment(audio, mimeType, "audio." + mimeType.extension()))
            .thenApply(this::parseOpenAITranscribeResponse);
    }

    @Override
    public CompletableFuture<String> transcribeAsync(Path source) throws AIException {
        return HTTP_CLIENT.post(this, "../hf-inference/models/" + getModelName(), new Attachment(source)).thenApply(this::parseOpenAITranscribeResponse);
    }

}
