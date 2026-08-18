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
package org.omnifaces.ai.modality;

import org.omnifaces.ai.AIVideoHandler;

/**
 * Default video handler, holding the provider-independent video analysis prompt.
 * <p>
 * This class is intended as a fallback when no provider-specific implementation is available.
 *
 * @author Bauke Scholtz
 * @since 1.7
 * @see AIVideoHandler
 */
public class DefaultAIVideoHandler implements AIVideoHandler {

    private static final long serialVersionUID = 1L;

    @Override
    public String buildAnalyzeVideoPrompt() {
        return """
                You are an expert at analyzing videos.
                Describe this video in detail.
                Rules:
                - Focus on: main subject, what happens over time, spoken content if any, visual style if relevant, and intended purpose.
                - Refer to a moment in the video by its timestamp.
                Output format:
                - Plain text description only.
                - No explanations, no notes, no extra text, no markdown formatting.
            """;
    }

}
