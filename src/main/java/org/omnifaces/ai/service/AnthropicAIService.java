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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;

/**
 * AI service implementation using Anthropic API.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 *     <li>provider: {@link AIProvider#ANTHROPIC}</li>
 *     <li>apiKey: your Anthropic API key</li>
 * </ul>
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional.
 * See {@link AIProvider#ANTHROPIC} for defaults.
 * <ul>
 *     <li>model: the model to use</li>
 *     <li>endpoint: the API endpoint URL</li>
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider#ANTHROPIC
 * @see BaseAIService
 * @see AIService
 * @see <a href="https://platform.claude.com/docs/en/api/overview">API Reference</a>
 */
public class AnthropicAIService extends BaseAIService {

    private static final long serialVersionUID = 1L;

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String ANTHROPIC_BETA_FILES_API = "files-api-2025-04-14";
    private static final String ANTHROPIC_BETA_STRUCTURED_OUTPUTS = "structured-outputs-2025-11-13";

    private static final AIModelVersion CLAUDE_3 = AIModelVersion.of("claude", 3);
    private static final AIModelVersion CLAUDE_OPUS_4_1 = AIModelVersion.of("claude-opus", 4, 1);
    private static final AIModelVersion CLAUDE_SONNET_4_5 = AIModelVersion.of("claude-sonnet", 4, 5);

    /**
     * Constructs an Anthropic AI service with the specified configuration.
     *
     * @param config the AI configuration
     * @see AIConfig
     */
    public AnthropicAIService(AIConfig config) {
        super(config);
    }

    @Override
    public boolean supportsModality(AIModality modality) {
        return switch (modality) {
            case IMAGE_ANALYSIS -> true;
            default -> false;
        };
    }

    @Override
    public boolean supportsStreaming() {
        return getModelVersion().gte(CLAUDE_3);
    }

    @Override
    public boolean supportsFileAttachments() {
        return getModelVersion().gte(CLAUDE_3);
    }

    @Override
    public boolean supportsStructuredOutput() {
        return getModelVersion().gte(CLAUDE_OPUS_4_1, CLAUDE_SONNET_4_5);
    }

    @Override
    protected Map<String, String> getRequestHeaders() {
        var headers = new HashMap<>(Map.of("x-api-key", apiKey, "anthropic-version", ANTHROPIC_VERSION));
        var betas = new ArrayList<String>();

        if (supportsFileAttachments()) {
            betas.add(ANTHROPIC_BETA_FILES_API);
        }

        if (supportsStructuredOutput()) {
            betas.add(ANTHROPIC_BETA_STRUCTURED_OUTPUTS);
        }

        if (!betas.isEmpty()) {
            headers.put("anthropic-beta", String.join(",", betas));
        }

        return Map.copyOf(headers);
    }

    @Override
    protected String getChatPath(boolean streaming) {
        return "messages";
    }

    @Override
    protected String getFilesPath() {
        return "files";
    }

    @Override
    protected UploadedFileJsonStructure getUploadedFileJsonStructure() {
        return new UploadedFileJsonStructure("data", "filename", "id", "created_at");
    }
}
