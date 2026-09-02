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
 * Provider-specific handlers for text, image, audio and video modalities.
 * <p>
 * AI providers differ in their JSON payload formats and response structures. This package contains {@link org.omnifaces.ai.AITextHandler},
 * {@link org.omnifaces.ai.AIImageHandler}, {@link org.omnifaces.ai.AIAudioHandler} and {@link org.omnifaces.ai.AIVideoHandler} implementations that adapt the
 * generic API to provider-specific requirements:
 * <ul>
 * <li>{@link org.omnifaces.ai.modality.DefaultAITextHandler} / {@link org.omnifaces.ai.modality.DefaultAIImageHandler} /
 * {@link org.omnifaces.ai.modality.DefaultAIAudioHandler} / {@link org.omnifaces.ai.modality.DefaultAIVideoHandler} - sensible defaults for most LLMs</li>
 * <li>{@link org.omnifaces.ai.modality.OpenAITextHandler} / {@link org.omnifaces.ai.modality.OpenAIImageHandler} /
 * {@link org.omnifaces.ai.modality.OpenAIAudioHandler} - OpenAI-specific handling</li>
 * <li>{@link org.omnifaces.ai.modality.AnthropicAITextHandler} - Anthropic-specific handling</li>
 * <li>{@link org.omnifaces.ai.modality.GoogleAITextHandler} / {@link org.omnifaces.ai.modality.GoogleAIImageHandler} /
 * {@link org.omnifaces.ai.modality.GoogleAIAudioHandler} / {@link org.omnifaces.ai.modality.GoogleAIVideoHandler} - Google AI-specific handling</li>
 * <li>{@link org.omnifaces.ai.modality.XAIImageHandler} / {@link org.omnifaces.ai.modality.XAIVideoHandler} - xAI-specific handling</li>
 * <li>{@link org.omnifaces.ai.modality.MistralAITextHandler} - Mistral-specific handling</li>
 * <li>{@link org.omnifaces.ai.modality.MetaAIAudioHandler} - Meta AI-specific handling</li>
 * <li>{@link org.omnifaces.ai.modality.AzureAITextHandler} - Azure AI-specific handling</li>
 * <li>{@link org.omnifaces.ai.modality.OpenRouterAITextHandler} / {@link org.omnifaces.ai.modality.OpenRouterAIAudioHandler} /
 * {@link org.omnifaces.ai.modality.OpenRouterAIVideoHandler} - OpenRouter-specific handling</li>
 * <li>{@link org.omnifaces.ai.modality.OllamaAITextHandler} - Ollama-specific handling</li>
 * </ul>
 * Custom handlers can be specified via {@link org.omnifaces.ai.cdi.AI#textHandler()}, {@link org.omnifaces.ai.cdi.AI#imageHandler()},
 * {@link org.omnifaces.ai.cdi.AI#audioHandler()} and {@link org.omnifaces.ai.cdi.AI#videoHandler()} to customize request payloads or response parsing.
 *
 * @see org.omnifaces.ai.AITextHandler
 * @see org.omnifaces.ai.AIImageHandler
 * @see org.omnifaces.ai.AIAudioHandler
 * @see org.omnifaces.ai.AIVideoHandler
 */
package org.omnifaces.ai.modality;
