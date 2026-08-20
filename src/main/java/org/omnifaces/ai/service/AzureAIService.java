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

import static java.util.Optional.ofNullable;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;

/**
 * AI service implementation using Microsoft Azure OpenAI API.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 * <li>provider: {@link AIProvider#AZURE}</li>
 * <li>apiKey: your Azure OpenAI API key</li>
 * <li>{@code org.omnifaces.ai.AZURE_RESOURCE}: your Azure resource name</li>
 * </ul>
 * <p>
 * The Azure-specific {@code org.omnifaces.ai.AZURE_RESOURCE} must represent the <code>{org.omnifaces.ai.AZURE_RESOURCE}</code> part of the default endpoint URL
 * <code>https://{org.omnifaces.ai.AZURE_RESOURCE}.openai.azure.com/openai/v1</code>.
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional. See {@link AIProvider#AZURE} for defaults.
 * <ul>
 * <li>model: the model to use</li>
 * <li>endpoint: the API endpoint URL</li>
 * </ul>
 * <p>
 * If the custom endopint does not contain the <code>{org.omnifaces.ai.AZURE_RESOURCE}</code> part, then the {@code org.omnifaces.ai.AZURE_RESOURCE}
 * configuration parameter is not anymore required and will be ignored.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider#AZURE
 * @see OpenAIService
 * @see BaseAIService
 * @see AIService
 * @see <a href="https://learn.microsoft.com/en-us/azure/ai-foundry/openai/latest">API Reference</a>
 */
public class AzureAIService extends OpenAIService {

    private static final long serialVersionUID = 1L;

    /** Configuration property key for the Azure resource name. */
    public static final String OPTION_AZURE_RESOURCE = AIConfig.createPropertyKey("AZURE_RESOURCE");

    private static final String RESOURCE_SUBSTITUTION = "{" + OPTION_AZURE_RESOURCE + "}";
    private static final String SORA_MODEL_NAME = "sora";
    private static final String VIDEO_PATH_PREFIX = "video/";
    private static final String API_VERSION_QUERY = "?api-version=preview";

    /**
     * Constructs an Azure AI service with the specified configuration.
     *
     * @param config the AI configuration
     * @see AIConfig
     */
    public AzureAIService(AIConfig config) {
        super(config.withEndpoint(substituteResourceIfNecessary(config)));
    }

    private static String substituteResourceIfNecessary(AIConfig config) {
        var endpoint = ofNullable(config.endpoint()).orElseGet(AIProvider.AZURE::getDefaultEndpoint);

        if (endpoint.contains(RESOURCE_SUBSTITUTION)) {
            endpoint = endpoint.replace(RESOURCE_SUBSTITUTION, config.require(OPTION_AZURE_RESOURCE));
        }

        return endpoint;
    }

    @Override
    public boolean supportsWebSearch() {
        return true;
    }

    @Override
    public boolean supportsModality(AIModality modality) {
        return modality == AIModality.VIDEO_GENERATION ? getModelName().toLowerCase(Locale.ROOT).contains(SORA_MODEL_NAME) : super.supportsModality(modality);
    }

    @Override
    public boolean supportsOpenAIResponsesApi() {
        return false;
    }

    @Override
    public boolean supportsOpenAIModerationCapability(Set<String> categories) {
        return false;
    }

    @Override
    protected Map<String, String> getRequestHeaders() {
        return Map.of("api-key", apiKey);
    }

    @Override
    protected URI resolveURI(String path) {
        return path.startsWith(VIDEO_PATH_PREFIX) ? super.resolveURI(path) : super.resolveURI(String.format("openai/v1/deployments/%s/%s", model, path));
    }

    /**
     * Returns {@code video/generations/jobs}, which answers with a job to poll rather than with the video itself.
     */
    @Override
    protected String getGenerateVideoPath() {
        return VIDEO_PATH_PREFIX + "generations/jobs" + API_VERSION_QUERY;
    }

    @Override
    protected String getGeneratedVideoPath(String jobId) {
        return VIDEO_PATH_PREFIX + "generations/jobs/" + jobId + API_VERSION_QUERY;
    }

    /**
     * Returns the path to download the video of the given generation from. Azure OpenAI keys this by the id of the generation rather than by the id of the job
     * which produced it, so this is reached from {@link org.omnifaces.ai.modality.AzureAIVideoHandler} rather than from the job id.
     *
     * @param generationId The id of the video generation.
     * @return The path to download the generated video from.
     * @since 1.7
     */
    public static String getVideoGenerationContentPath(String generationId) {
        return VIDEO_PATH_PREFIX + "generations/" + generationId + "/content/video" + API_VERSION_QUERY;
    }

}
