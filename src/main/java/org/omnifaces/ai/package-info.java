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
 * Core API for AI service integration.
 * <p>
 * This package contains the primary interfaces and types for interacting with AI providers:
 * <ul>
 * <li>{@link org.omnifaces.ai.AIProvider} - enumeration of supported AI providers (OpenAI, Anthropic, Google, etc.)</li>
 * <li>{@link org.omnifaces.ai.AIConfig} - configuration interface for API keys, endpoints, and models</li>
 * <li>{@link org.omnifaces.ai.AIStrategy} - holder for custom text and image handler configuration</li>
 * <li>{@link org.omnifaces.ai.AIService} - unified interface for AI operations (chat, summarization, translation, proofreading, moderation, image analysis/generation)</li>
 * <li>{@link org.omnifaces.ai.AIModality} - capabilities supported by AI providers (text input/output, image input/output, etc.)</li>
 * <li>{@link org.omnifaces.ai.AITextHandler} - customization point for text-based request/response handling</li>
 * <li>{@link org.omnifaces.ai.AIImageHandler} - customization point for image-based request/response handling</li>
 * </ul>
 *
 * @see org.omnifaces.ai.cdi
 * @see org.omnifaces.ai.service
 */
package org.omnifaces.ai;
