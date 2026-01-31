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
package org.omnifaces.ai;

import static java.util.Arrays.stream;

import org.omnifaces.ai.cdi.AI;
import org.omnifaces.ai.modality.AnthropicAIImageHandler;
import org.omnifaces.ai.modality.AnthropicAITextHandler;
import org.omnifaces.ai.modality.GoogleAIImageHandler;
import org.omnifaces.ai.modality.GoogleAITextHandler;
import org.omnifaces.ai.modality.MetaAITextHandler;
import org.omnifaces.ai.modality.OllamaAIImageHandler;
import org.omnifaces.ai.modality.OllamaAITextHandler;
import org.omnifaces.ai.modality.OpenAIImageHandler;
import org.omnifaces.ai.modality.OpenAITextHandler;
import org.omnifaces.ai.modality.OpenRouterAITextHandler;
import org.omnifaces.ai.modality.XAIImageHandler;
import org.omnifaces.ai.service.AnthropicAIService;
import org.omnifaces.ai.service.AzureAIService;
import org.omnifaces.ai.service.GoogleAIService;
import org.omnifaces.ai.service.HuggingFaceAIService;
import org.omnifaces.ai.service.MetaAIService;
import org.omnifaces.ai.service.MistralAIService;
import org.omnifaces.ai.service.OllamaAIService;
import org.omnifaces.ai.service.OpenAIService;
import org.omnifaces.ai.service.OpenRouterAIService;
import org.omnifaces.ai.service.XAIService;

/**
 * Enumeration of major AI model providers / platforms.
 *
 * <p>
 * This is not meant to be exhaustive. New providers can be added over time. Default models and endpoints can also change over time.
 *
 * <h2>Usage</h2>
 * <p>
 * Use {@link AIConfig} to create an {@link AIService} instance of a chosen provider.
 * For example, to use {@link AIProvider#ANTHROPIC}:
 * <pre>
 * var service = AIConfig.of(AIProvider.ANTHROPIC, "your-anthropic-api-key").createService();
 * </pre>
 * For {@link AIProvider#CUSTOM}, you'll need to provide the {@link Class} instance of your custom {@link AIService} implementation instead of the AIProvider enum:
 * <pre>
 * var service = AIConfig.of(YourCustomAIService.class, "your-api-key").createService();
 * </pre>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIConfig
 * @see AIService
 */
public enum AIProvider {

    /**
     * OpenAI: GPT, GPT mini, GPT nano, etc.
     * <p>
     * Defaults currently to model {@code gpt-5-mini} at endpoint {@code https://api.openai.com/v1}.
     * @see OpenAIService
     * @see <a href="https://platform.openai.com/api-keys">Manage OpenAI API Keys</a>
     * @see <a href="https://platform.openai.com/docs/models">Available OpenAI Models</a>
     */
    OPENAI("OpenAI", OpenAIService.class, true, "gpt-5-mini", "https://api.openai.com/v1", OpenAITextHandler.class, OpenAIImageHandler.class),

    /**
     * Anthropic: Claude Opus, Claude Sonnet, Claude Haiku, etc.
     * <p>
     * Defaults currently to model {@code claude-sonnet-4-5-20250929} at endpoint {@code https://api.anthropic.com/v1}.
     * @see AnthropicAIService
     * @see <a href="https://platform.claude.com/settings/keys">Manage Anthropic API Keys</a>
     * @see <a href="https://platform.claude.com/docs/en/about-claude/models/overview">Available Anthropic AI Models</a>
     */
    ANTHROPIC("Anthropic", AnthropicAIService.class, true, "claude-sonnet-4-5-20250929", "https://api.anthropic.com/v1", AnthropicAITextHandler.class, AnthropicAIImageHandler.class),

    /**
     * Google AI: Gemini Pro, Gemini Flash, Gemini Flash Lite, etc.
     * <p>
     * Defaults currently to model {@code gemini-2.5-flash} at endpoint {@code https://generativelanguage.googleapis.com/v1beta}.
     * @see GoogleAIService
     * @see <a href="https://aistudio.google.com/app/api-keys">Manage Google AI API Keys</a>
     * @see <a href="https://ai.google.dev/gemini-api/docs/models">Available Google AI Models</a>
     */
    GOOGLE("Google AI", GoogleAIService.class, true, "gemini-2.5-flash", "https://generativelanguage.googleapis.com/v1beta", GoogleAITextHandler.class, GoogleAIImageHandler.class),

    /**
     * xAI: Grok Fast Reasoning, Grok Non Reasoning, Grok Code, etc.
     * <p>
     * Defaults currently to model {@code grok-4-1-fast-reasoning} at endpoint {@code https://api.x.ai/v1}.
     * @see XAIService
     * @see <a href="https://console.x.ai/">Manage xAI API Keys</a>
     * @see <a href="https://docs.x.ai/docs/models">Available xAI Models</a>
     */
    XAI("xAI", XAIService.class, true, "grok-4-1-fast-reasoning", "https://api.x.ai/v1", OpenAITextHandler.class, XAIImageHandler.class),

    /**
     * Mistral AI: Mistral Large, Mistral Medium, Mistral Small, etc.
     * <p>
     * Defaults currently to model {@code mistral-medium-2508} at endpoint {@code https://api.mistral.ai/v1}.
     * @see MistralAIService
     * @see <a href="https://console.mistral.ai/home?workspace_dialog=apiKeys">Manage Mistral AI API Keys</a>
     * @see <a href="https://docs.mistral.ai/getting-started/models/">Available Mistral AI Models</a>
     */
    MISTRAL("Mistral AI", MistralAIService.class, true, "mistral-medium-2508", "https://api.mistral.ai/v1", OpenAITextHandler.class, OpenAIImageHandler.class),

    /**
     * Meta AI: Llama Maverick, Llama Scout, Llama default, etc.
     * <p>
     * Defaults currently to model {@code Llama-4-Scout-17B-16E-Instruct-FP8} at endpoint {@code https://api.llama.com/v1}.
     * @see MetaAIService
     * @see <a href="https://llama.developer.meta.com/docs/api-keys/">Manage Meta AI API Keys</a>
     * @see <a href="https://llama.developer.meta.com/docs/models/">Available Meta AI Models</a>
     */
    META("Meta AI", MetaAIService.class, true, "Llama-4-Scout-17B-16E-Instruct-FP8", "https://api.llama.com/v1", MetaAITextHandler.class, OpenAIImageHandler.class),

    /**
     * Azure OpenAI: Aggregates a broad range of AI models via a unified OpenAI-compatible API.
     * <p>
     * Defaults currently to model {@code gpt-5-mini} at endpoint {@code https://{org.omnifaces.ai.AZURE_RESOURCE}.openai.azure.com/openai/v1}.
     * @see AzureAIService
     * @see <a href="https://portal.azure.com/">Manage Azure OpenAI API Keys</a>
     * @see <a href="https://ai.azure.com/catalog">Available Azure OpenAI Models</a>
     */
    AZURE("Azure OpenAI", AzureAIService.class, true, "gpt-5-mini", "https://{org.omnifaces.ai.AZURE_RESOURCE}.openai.azure.com/openai/v1", OpenAITextHandler.class, OpenAIImageHandler.class),

    /**
     * OpenRouter: Aggregates a broad range of AI models via a unified OpenAI-compatible API.
     * <p>
     * Defaults currently to model {@code deepseek/deepseek-v3.2} at endpoint {@code https://openrouter.ai/api/v1}.
     * @see OpenRouterAIService
     * @see <a href="https://openrouter.ai/settings/keys/">Manage OpenRouter API Keys</a>
     * @see <a href="https://openrouter.ai/models">Available OpenRouter Models</a>
     */
    OPENROUTER("OpenRouter", OpenRouterAIService.class, true, "deepseek/deepseek-v3.2", "https://openrouter.ai/api/v1", OpenRouterAITextHandler.class, OpenAIImageHandler.class),

    /**
     * Hugging Face: Aggregates a broad range of AI models via a unified OpenAI-compatible API.
     * <p>
     * Defaults currently to model {@code google/gemma-3-27b-it} at endpoint {@code https://router.huggingface.co/v1}.
     * @see HuggingFaceAIService
     * @see <a href="https://huggingface.co/settings/tokens">Manage Hugging Face API Keys</a>
     * @see <a href="https://huggingface.co/models">Available Hugging Face Models</a>
     */
    HUGGINGFACE("Hugging Face", HuggingFaceAIService.class, true, "google/gemma-3-27b-it", "https://router.huggingface.co/v1", OpenAITextHandler.class, OpenAIImageHandler.class),

    /**
     * Ollama: Local models, e.g. Llama, Gemma, Mistral, etc via local server.
     * <p>
     * Defaults currently to model {@code gemma3} at endpoint {@code http://localhost:11434}.
     * <p>
     * To install it:
     * <pre>
     * # Install
     * curl -fsSL https://ollama.com/install.sh | sh
     *
     * # Start
     * sudo systemctl start ollama
     *
     * # Enable on boot
     * sudo systemctl enable ollama
     *
     * # Run and chat with specific model (will download if absent; gemma3 is ~3.3GB and supports vision)
     * ollama run gemma3
     * </pre>
     * <p>
     * Test by opening {@code http://localhost:11434} in web browser.
     *
     * @see OllamaAIService
     * @see <a href="https://ollama.com/library">Available Ollama Models</a> (no API Keys required)
     */
    OLLAMA("Ollama", OllamaAIService.class, false, "gemma3", "http://localhost:11434", OllamaAITextHandler.class, OllamaAIImageHandler.class),

    /**
     * Custom: provide the {@link Class} instance of your custom {@link AIService} implementation as the provider in {@link AIConfig},
     * or use {@link AI#serviceClass()} when using CDI injection.
     * <p>
     * Custom providers that extend {@link org.omnifaces.ai.service.BaseAIService} must supply their handlers via
     * {@link AIConfig#withStrategy(AIStrategy)} or {@link AI#textHandler()} and {@link AI#imageHandler()},
     * since this provider has no default handlers.
     * <p>
     * If you have a great one, feel free to submit it to OmniHai so it ends up as a new enum entry here :)
     */
    CUSTOM(null, null, false, null, null, null, null);

    private final String name;
    private final Class<? extends AIService> serviceClass;
    private final boolean apiKeyRequired;
    private final String defaultModel;
    private final String defaultEndpoint;
    private final Class<? extends AITextHandler> defaultTextHandler;
    private final Class<? extends AIImageHandler> defaultImageHandler;

    AIProvider(String name, Class<? extends AIService> serviceClass, boolean apiKeyRequired, String defaultModel, String defaultEndpoint, Class<? extends AITextHandler> defaultTextHandler, Class<? extends AIImageHandler> defaultImageHandler) {
        this.name = name;
        this.serviceClass = serviceClass;
        this.apiKeyRequired = apiKeyRequired;
        this.defaultModel = defaultModel;
        this.defaultEndpoint = defaultEndpoint;
        this.defaultTextHandler = defaultTextHandler;
        this.defaultImageHandler = defaultImageHandler;
    }

    /**
     * Returns the AI provider instance matching the given name, case insensitive, or {@code null} when there is no match.
     * @param name to match an AI provider for.
     * @return The AI provider instance matching the given name, case insensitive, or {@code null} when there is no match.
     */
    public static AIProvider of(String name) {
        return stream(values()).filter(provider -> provider.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    /**
     * Returns the AI provider's name.
     * @return The AI provider's name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the AI provider's service class.
     * If this returns {@code null}, then the end-user needs to provide a {@link Class} instance of the custom {@link AIService} implementation as {@link AIConfig#provider()}.
     * @return The AI provider's service class.
     */
    public Class<? extends AIService> getServiceClass() {
        return serviceClass;
    }

    /**
     * Returns whether this AI provider requires an API key.
     * @return Whether this AI provider requires an API key.
     */
    public boolean isApiKeyRequired() {
        return apiKeyRequired;
    }

    /**
     * Returns the AI provider's default AI model.
     * Can be overridden via {@link AIConfig#withModel(String)} or {@link AIConfig#PROPERTY_MODEL} or {@link AI#model()}.
     * @return the AI provider's default AI model.
     */
    public String getDefaultModel() {
        return defaultModel;
    }

    /**
     * Returns the AI provider's default API endpoint.
     * Can be overridden via {@link AIConfig#withEndpoint(String)} or {@link AIConfig#PROPERTY_ENDPOINT} or {@link AI#endpoint()}.
     * @return the AI provider's default API endpoint.
     */
    public String getDefaultEndpoint() {
        return defaultEndpoint;
    }

    /**
     * Returns the AI provider's default AI text handler.
     * Can be overridden via {@link AIConfig#withStrategy(AIStrategy)} or {@link AIStrategy#withTextHandler(Class)} or {@link AI#textHandler()}.
     * @return the AI provider's default AI text handler.
     */
    public Class<? extends AITextHandler> getDefaultTextHandler() {
        return defaultTextHandler;
    }

    /**
     * Returns the AI provider's default AI image handler.
     * Can be overridden via {@link AIConfig#withStrategy(AIStrategy)} or {@link AIStrategy#withImageHandler(Class)} or {@link AI#imageHandler()}.
     * @return the AI provider's default AI image handler.
     */
    public Class<? extends AIImageHandler> getDefaultImageHandler() {
        return defaultImageHandler;
    }

}
