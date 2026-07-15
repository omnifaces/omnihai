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

/**
 * Provider-specific handlers for text, image, and audio modalities.
 * <p>
 * AI providers differ in their JSON payload formats and response structures. This package contains {@link org.omnifaces.ai.AITextHandler},
 * {@link org.omnifaces.ai.AIImageHandler}, and {@link org.omnifaces.ai.AIAudioHandler} implementations that adapt the generic API to provider-specific
 * requirements:
 * <ul>
 * <li>{@link org.omnifaces.ai.modality.DefaultAITextHandler} / {@link org.omnifaces.ai.modality.DefaultAIImageHandler} /
 * {@link org.omnifaces.ai.modality.DefaultAIAudioHandler} - sensible defaults for most LLMs</li>
 * <li>{@link org.omnifaces.ai.modality.OpenAITextHandler} / {@link org.omnifaces.ai.modality.OpenAIImageHandler} /
 * {@link org.omnifaces.ai.modality.OpenAIAudioHandler} - OpenAI-specific handling</li>
 * <li>{@link org.omnifaces.ai.modality.AnthropicAITextHandler} - Anthropic-specific handling</li>
 * <li>{@link org.omnifaces.ai.modality.GoogleAITextHandler} / {@link org.omnifaces.ai.modality.GoogleAIImageHandler} /
 * {@link org.omnifaces.ai.modality.GoogleAIAudioHandler} - Google AI-specific handling</li>
 * <li>{@link org.omnifaces.ai.modality.XAIImageHandler} - xAI-specific handling</li>
 * <li>{@link org.omnifaces.ai.modality.MistralAITextHandler} - Mistral-specific handling</li>
 * <li>{@link org.omnifaces.ai.modality.AzureAITextHandler} - Azure AI-specific handling</li>
 * <li>{@link org.omnifaces.ai.modality.OpenRouterAITextHandler} - OpenRouter-specific handling</li>
 * <li>{@link org.omnifaces.ai.modality.OllamaAITextHandler} - Ollama-specific handling</li>
 * </ul>
 * Custom handlers can be specified via {@link org.omnifaces.ai.cdi.AI#textHandler()}, {@link org.omnifaces.ai.cdi.AI#imageHandler()}, and
 * {@link org.omnifaces.ai.cdi.AI#audioHandler()} to customize request payloads or response parsing.
 *
 * @see org.omnifaces.ai.AITextHandler
 * @see org.omnifaces.ai.AIImageHandler
 * @see org.omnifaces.ai.AIAudioHandler
 */
package org.omnifaces.ai.modality;
