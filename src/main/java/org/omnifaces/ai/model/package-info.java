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
 * Data models for AI service operations.
 * <p>
 * This package contains value types representing inputs, outputs, and configuration options:
 * <ul>
 * <li>{@link org.omnifaces.ai.model.ChatInput} - user input with optional file attachments</li>
 * <li>{@link org.omnifaces.ai.model.ChatOptions} - chat configuration (temperature, max tokens, etc.)</li>
 * <li>{@link org.omnifaces.ai.model.ModerationOptions} - content moderation configuration</li>
 * <li>{@link org.omnifaces.ai.model.ModerationResult} - content moderation results</li>
 * <li>{@link org.omnifaces.ai.model.GenerateImageOptions} - image generation configuration</li>
 * <li>{@link org.omnifaces.ai.model.Sse.Event} - SSE event for streaming responses</li>
 * </ul>
 */
package org.omnifaces.ai.model;
