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
 * AI service implementations for various providers.
 * <p>
 * This package contains {@link org.omnifaces.ai.AIService} implementations:
 * <ul>
 * <li>{@link org.omnifaces.ai.service.OpenAIService} - OpenAI (GPT models)</li>
 * <li>{@link org.omnifaces.ai.service.AnthropicAIService} - Anthropic (Claude models)</li>
 * <li>{@link org.omnifaces.ai.service.GoogleAIService} - Google AI (Gemini models)</li>
 * <li>{@link org.omnifaces.ai.service.XAIService} - xAI (Grok models)</li>
 * <li>{@link org.omnifaces.ai.service.MetaAIService} - Meta AI (Llama models)</li>
 * <li>{@link org.omnifaces.ai.service.MistralAIService} - Mistral AI (Mistral models)</li>
 * <li>{@link org.omnifaces.ai.service.AzureAIService} - Azure OpenAI (multi-provider gateway)</li>
 * <li>{@link org.omnifaces.ai.service.OpenRouterAIService} - OpenRouter (multi-provider gateway)</li>
 * <li>{@link org.omnifaces.ai.service.HuggingFaceAIService} - Hugging Face (multi-provider gateway)</li>
 * <li>{@link org.omnifaces.ai.service.OllamaAIService} - Ollama (local models)</li>
 * </ul>
 * All implementations extend {@link org.omnifaces.ai.service.BaseAIService} which provides common HTTP client functionality via
 * {@link java.net.http.HttpClient}.
 * <p>
 * {@link org.omnifaces.ai.service.AIServiceWrapper} is an abstract decorator that allows wrapping any {@link org.omnifaces.ai.AIService} instance to add or
 * override behaviour without modifying the underlying service. {@link org.omnifaces.ai.service.InterceptingAIServiceWrapper} builds on it to funnel every
 * operation through a single interception hook, which the ready-to-use resilience decorators {@link org.omnifaces.ai.service.RetryingAIService} and
 * {@link org.omnifaces.ai.service.FailoverAIService} use to apply retry and failover uniformly across the entire service surface. Being pure decorators, they
 * compose freely. When such a decorator re-attempts a streaming operation, a {@link org.omnifaces.ai.service.ResettableConsumer} lets the caller opt into
 * restarting a partially consumed stream rather than aborting it.
 *
 * @see org.omnifaces.ai.AIProvider
 */
package org.omnifaces.ai.service;
