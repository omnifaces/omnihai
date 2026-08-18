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

import static org.omnifaces.ai.AIModality.AUDIO_GENERATION;
import static org.omnifaces.ai.service.ModelModalitiesRegistry.findModelModalities;

import java.util.EnumSet;
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

    /**
     * The modalities which the listing may report but no call can serve: OmniHai has no video generation API at all, and OpenRouter emits audio as a chat
     * completion stream which no {@link org.omnifaces.ai.AIAudioHandler} implements.
     */
    private static final Set<AIModality> UNSERVEABLE_MODALITIES = EnumSet.of(AIModality.AUDIO_GENERATION, AIModality.VIDEO_GENERATION);

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
        if (UNSERVEABLE_MODALITIES.contains(modality)) {
            return false;
        }

        return findModelModalities(this).map(modalities -> modalities.contains(modality)).orElseGet(() -> supportsModalityByModelName(modality));
    }

    /**
     * Matches the model name against the modality name, which a model offering that modality as output is generally named after, such as
     * {@code google/gemini-3-flash-image}. A model offering it as input is named after its family instead, so image analysis is assumed rather than matched, as
     * nearly every routed model accepts an image, and audio and video analysis are assumed absent rather than guessed.
     *
     * @param modality The modality to check.
     * @return Whether the model name suggests the given modality.
     */
    private boolean supportsModalityByModelName(AIModality modality) {
        var fullModelName = getModelName().toLowerCase();

        return switch (modality) {
            case IMAGE_ANALYSIS -> true;
            case IMAGE_GENERATION -> fullModelName.contains("image");
            case AUDIO_ANALYSIS -> fullModelName.contains("audio");
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
