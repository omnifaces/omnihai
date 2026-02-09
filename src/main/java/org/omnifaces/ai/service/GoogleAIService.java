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

import java.net.URI;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;

/**
 * AI service implementation using Google AI API.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 *     <li>provider: {@link AIProvider#GOOGLE}</li>
 *     <li>apiKey: your Google API key</li>
 * </ul>
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional.
 * See {@link AIProvider#GOOGLE} for defaults.
 * <ul>
 *     <li>model: the model to use</li>
 *     <li>endpoint: the API endpoint URL</li>
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
    private static final AIModelVersion GEMINI_2 = AIModelVersion.of("gemini", 2);

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
        var fullModelName = getModelName().toLowerCase();

        return switch (modality) {
            case IMAGE_ANALYSIS -> true;
            case IMAGE_GENERATION -> currentModelVersion.gte(GEMINI_2) || fullModelName.contains("image");
            case AUDIO_ANALYSIS -> currentModelVersion.gte(GEMINI_1_5);
            default -> false;
        };
    }

    /**
     * Google AI supports streaming, but it comes in big chunks. According to Gemini it's caused by "Safety Filter"
     * bottleneck whereby the AI doublechecks every paragraph before sending out, so it basically comes in paragraphs.
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
    public boolean supportsStructuredOutput() {
        return getModelVersion().gte(GEMINI_2);
    }

    @Override
    protected URI resolveURI(String path) {
        if (path.equals(getFilesPath())) {
            return super.resolveURI("../upload/v1beta/"+ format(getFilesPath() + "?key=%s", apiKey));
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

    @Override
    protected String getFilesPath() {
        return "files";
    }
}
