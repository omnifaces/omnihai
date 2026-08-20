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

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;

/**
 * AI service implementation using OpenRouter API.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 * <li>provider: {@link AIProvider#OPENROUTER}</li>
 * <li>apiKey: your OpenRouter API key</li>
 * </ul>
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional. See {@link AIProvider#OPENROUTER} for defaults.
 * <ul>
 * <li>model: the model to use</li>
 * <li>endpoint: the API endpoint URL</li>
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider#OPENROUTER
 * @see OpenAIService
 * @see BaseAIService
 * @see AIService
 * @see <a href="https://openrouter.ai/docs/api/reference">API Reference</a>
 */
public class OpenRouterAIService extends OpenAIService {

    private static final long serialVersionUID = 1L;

    /** The model listing paths, as OpenRouter omits the video generators from its default one. */
    private static final List<String> MODELS_PATHS = List.of("models", "models?output_modalities=video");

    /**
     * Constructs an OpenRouter AI service with the specified configuration.
     *
     * @param config the AI configuration
     * @see AIConfig
     */
    public OpenRouterAIService(AIConfig config) {
        super(config);
    }

    /**
     * OpenRouter publishes the input and output modalities of every routed model, so they are looked up rather than guessed from the model name. Matching the
     * model name against the modality name is the fallback for as long as the listing cannot be obtained, and for a model which is not in it.
     *
     * @see <a href="https://openrouter.ai/docs/api-reference/list-available-models">List Models API Reference</a>
     */
    @Override
    public boolean supportsModality(AIModality modality) {
        return findModelModalities(this).map(modalities -> modalities.contains(modality)).orElseGet(() -> supportsModalityByModelName(modality));
    }

    /**
     * OpenRouter enumerates the video generators only under {@code output_modalities=video}, so that listing is consulted next to the default one.
     */
    @Override
    protected List<String> getModelsPaths() {
        return MODELS_PATHS;
    }

    /**
     * Matches the model name against the modality name, which a model offering that modality as output is generally named after, such as
     * {@code google/gemini-3-flash-image}. A model offering it as input is named after its family instead, so image analysis is assumed rather than matched, as
     * nearly every routed model accepts an image, and audio and video analysis are assumed absent rather than guessed. Video generation is left to the listing
     * alone, as a model offering it is named after neither.
     *
     * @param modality The modality to check.
     * @return Whether the model name suggests the given modality.
     */
    private boolean supportsModalityByModelName(AIModality modality) {
        var fullModelName = getModelName().toLowerCase(Locale.ROOT);

        return switch (modality) {
            case IMAGE_ANALYSIS -> true;
            case IMAGE_GENERATION -> fullModelName.contains("image");
            case AUDIO_ANALYSIS -> fullModelName.contains("audio");
            case AUDIO_GENERATION -> fullModelName.contains("audio") || fullModelName.contains("tts");
            case VIDEO_ANALYSIS -> fullModelName.contains("video");
            case VIDEO_GENERATION -> false;
        };
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public boolean supportsFileAttachments() {
        return true;
    }

    @Override
    public boolean supportsStructuredOutput() {
        return true;
    }

    @Override
    public boolean supportsWebSearch() {
        return true;
    }

    @Override
    public boolean supportsReasoningEffort() {
        return true;
    }

    /**
     * OpenRouter has no usable speech endpoint: {@code audio/speech} exists but serves a model catalog of its own which the model listing does not enumerate,
     * so audio is generated by an audio capable chat model instead, as {@link org.omnifaces.ai.modality.OpenRouterAIAudioHandler} does.
     */
    @Override
    protected String getGenerateAudioPath() {
        return getChatPath(false);
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

}
