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

import java.io.Serializable;

import org.omnifaces.ai.modality.DefaultAIVideoHandler;

/**
 * Handler for video-based AI operations.
 * <p>
 * Covers:
 * <ul>
 * <li>detailed video analysis / description / VQA</li>
 * </ul>
 * <p>
 * The frame sampling rate and the clip offsets of {@link org.omnifaces.ai.model.AnalyzeVideoOptions} are carried by the attachment itself, and are therefore
 * rendered by the {@link AITextHandler} which builds the content parts of the chat payload.
 * <p>
 * The implementations must be stateless and able to be {@code jakarta.enterprise.context.ApplicationScoped}.
 *
 * @author Bauke Scholtz
 * @since 1.7
 * @see AIService
 * @see DefaultAIVideoHandler
 */
public interface AIVideoHandler extends Serializable {

    /**
     * Builds the default system prompt to use when no custom user prompt is provided to {@link AIService#analyzeVideo(byte[], String)} or any of its overloads.
     *
     * @return The general-purpose video analysis prompt.
     */
    String buildAnalyzeVideoPrompt();

}
