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

import org.omnifaces.ai.AIAudioHandler;
import org.omnifaces.ai.AIService;

/**
 * Default AI audio handler implementation that provides response parsing suitable for most current
 * transcription-capable models.
 * <p>
 * This class is intended as a fallback when no provider-specific implementation is available.
 *
 * @author Bauke Scholtz
 * @since 1.1
 * @see AIAudioHandler
 * @see AIService
 */
public class DefaultAIAudioHandler implements AIAudioHandler {

    private static final long serialVersionUID = 1L;

    @Override
    public String buildTranscribePrompt() {
        return """
            You are an expert at transcribing audio.
            Transcribe the audio verbatim in its original language.
            Preserve the spoken words exactly as heard.
            Rules:
            - Do not summarize or paraphrase.
            - Do not add explanations or notes.
            - Do not translate.
            - Omit non-speech sounds unless they are spoken words.
            - Do not include speaker labels.
            Output format:
            - Plain text transcription only.
            - No markdown or extra text.
        """;
    }
}
