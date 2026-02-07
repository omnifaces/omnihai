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

import jakarta.enterprise.context.ApplicationScoped;

import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.modality.DefaultAIAudioHandler;

/**
 * Handler for audio-based AI operations including transcription.
 * <p>
 * Covers:
 * <ul>
 * <li>audio transcription</li>
 * </ul>
 * <p>
 * The implementations must be stateless and able to be {@link ApplicationScoped}.
 *
 * @author Bauke Scholtz
 * @since 1.1
 * @see AIService
 * @see DefaultAIAudioHandler
 */
public interface AIAudioHandler extends Serializable {

    /**
     * Builds the system prompt for {@link AIService#transcribe(byte[])} and {@link AIService#transcribeAsync(byte[])}.
     * @return The system prompt.
     */
    String buildTranscribePrompt();

    /**
     * Parses transcription text from the API response body of a transcribe operation.
     * @implNote The default implementation throws UnsupportedOperationException.
     * @param responseBody The API response body, usually a JSON object with the AI response, along with some meta data.
     * @return The extracted transcription text from the API response body.
     * @throws AIResponseException If the response cannot be parsed as JSON, contains an error object, or is missing expected transcription text.
     */
    default String parseTranscribeResponse(String responseBody) throws AIResponseException {
        throw new UnsupportedOperationException("Please implement parseTranscribeResponse(String responseBody) method in class " + getClass().getSimpleName());
    }
}
