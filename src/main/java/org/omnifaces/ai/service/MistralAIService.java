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

import java.util.Locale;
import java.util.Set;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;

/**
 * AI service implementation using Mistral AI API.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 * <li>provider: {@link AIProvider#MISTRAL}</li>
 * <li>apiKey: your Mistral API key</li>
 * </ul>
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional. See {@link AIProvider#MISTRAL} for defaults.
 * <ul>
 * <li>model: the model to use</li>
 * <li>endpoint: the API endpoint URL</li>
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider#MISTRAL
 * @see OpenAIService
 * @see BaseAIService
 * @see AIService
 * @see <a href="https://docs.mistral.ai/api">API Reference</a>
 */
public class MistralAIService extends OpenAIService {

    private static final long serialVersionUID = 1L;

    // Mistral mixes two id schemes: legacy dated (YYMM, e.g. mistral-medium-2508) and semantic (e.g. mistral-medium-3-5).
    // A dated major dwarfs a semantic one numerically, so each id must be gated against the floor of its own scheme.
    private static final int MIN_DATED_MAJOR_VERSION = 100;
    private static final AIModelVersion MISTRAL_2402 = AIModelVersion.of("mistral", 2402);
    private static final AIModelVersion MISTRAL_2603 = AIModelVersion.of("mistral", 2603);
    private static final AIModelVersion MISTRAL_3_5 = AIModelVersion.of("mistral", 3, 5);
    private static final AIModelVersion VOXTRAL = AIModelVersion.of("voxtral");
    private static final AIModelVersion VOXTRAL_MINI = AIModelVersion.of("voxtral-mini");

    /**
     * Constructs a Mistral AI service with the specified configuration.
     *
     * @param config the AI configuration
     * @see AIConfig
     */
    public MistralAIService(AIConfig config) {
        super(config);
    }

    @Override
    public boolean supportsModality(AIModality modality) {
        var currentModelVersion = getModelVersion();

        return switch (modality) {
            case IMAGE_ANALYSIS -> true;
            case AUDIO_ANALYSIS -> currentModelVersion.gte(VOXTRAL);
            default -> false;
        };
    }

    @Override
    public boolean supportsStreaming() {
        return isAtLeast(MISTRAL_2402, MISTRAL_3_5) || getModelName().toLowerCase(Locale.ROOT).endsWith("latest");
    }

    @Override
    public boolean supportsFileAttachments() {
        return true;
    }

    @Override
    public boolean supportsStructuredOutput() {
        return true; // Not version-bound, support is API-bound since January 2025.
    }

    @Override
    public boolean supportsReasoningEffort() {
        return isAtLeast(MISTRAL_2603, MISTRAL_3_5) || getModelName().toLowerCase(Locale.ROOT).endsWith("latest");
    }

    @Override
    public boolean supportsOpenAIResponsesApi() {
        return false;
    }

    @Override
    public boolean supportsOpenAIFilesApi() {
        return true; // Not version-bound, support is API-bound.
    }

    @Override
    public boolean supportsOpenAIModerationCapability(Set<String> categories) {
        return false;
    }

    @Override
    public boolean supportsOpenAITranscriptionCapability() {
        return getModelVersion().gte(VOXTRAL_MINI);
    }

    /**
     * Fetches a short-lived signed URL for an uploaded file. Mistral chat models reference uploaded documents by signed {@code document_url} rather than by a
     * bare file id.
     *
     * @param fileId The uploaded file id.
     * @return A signed, fetchable URL for the file.
     * @since 1.5
     */
    public String getSignedUrl(String fileId) {
        return HTTP_CLIENT.get(this, getFilesPath() + "/" + fileId + "/url").join().getString("url");
    }

    /**
     * Whether this model references an uploaded document by a signed {@code document_url} (semantic-versioned models such as {@code mistral-medium-3-5}) rather
     * than by a bare {@code file_id} content block (legacy dated models such as {@code mistral-medium-2508}, which reject {@code document_url}).
     *
     * @return Whether uploaded documents are referenced by signed URL.
     * @since 1.5
     */
    public boolean supportsSignedUrl() {
        return getModelVersion().majorVersion() < MIN_DATED_MAJOR_VERSION;
    }

    /**
     * Gates the current model against the appropriate floor for its id scheme: {@code datedFloor} for legacy dated ids (YYMM major), {@code semanticFloor} for
     * semantic ids. This avoids a semantic floor (small major) spuriously matching every dated id, or vice versa.
     *
     * @param datedFloor the minimum version for dated ids.
     * @param semanticFloor the minimum version for semantic ids.
     * @return whether the current model version is at least the floor of its own scheme.
     */
    private boolean isAtLeast(AIModelVersion datedFloor, AIModelVersion semanticFloor) {
        var version = getModelVersion();
        return version.gte(version.majorVersion() >= MIN_DATED_MAJOR_VERSION ? datedFloor : semanticFloor);
    }

}
