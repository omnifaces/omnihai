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

import java.io.InputStream;
import java.io.Serializable;

import jakarta.json.JsonObject;

import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.modality.DefaultAIAudioHandler;
import org.omnifaces.ai.model.GenerateAudioOptions;

/**
 * Handler for audio-based AI operations including transcription and generation (text-to-speech).
 * <p>
 * Covers:
 * <ul>
 * <li>audio transcription (speech-to-text)</li>
 * <li>audio generation (text-to-speech)</li>
 * </ul>
 * <p>
 * The implementations must be stateless and able to be {@code jakarta.enterprise.context.ApplicationScoped}.
 *
 * @author Bauke Scholtz
 * @since 1.1
 * @see AIService
 * @see DefaultAIAudioHandler
 */
public interface AIAudioHandler extends Serializable {

    /**
     * Builds the system prompt for {@link AIService#transcribe(byte[])}, {@link AIService#transcribe(java.nio.file.Path)},
     * {@link AIService#transcribeAsync(byte[])}, and {@link AIService#transcribeAsync(java.nio.file.Path)}.
     *
     * @return The system prompt.
     */
    String buildTranscribePrompt();

    /**
     * Builds the JSON request payload for all transcribe operations of an AI provider which transcribes via a dedicated speech-to-text endpoint rather than via
     * a chat completion.
     *
     * @implNote The default implementation throws UnsupportedOperationException.
     * @param service The visiting AI service.
     * @return The JSON request payload.
     * @since 1.7.1
     */
    default JsonObject buildTranscribePayload(AIService service) {
        throw new UnsupportedOperationException("Please implement buildTranscribePayload(AIService service) for this AI provider");
    }

    /**
     * Converts the audio content into the format which the transcribe endpoint of the AI provider accepts. This is invoked by an AI service which transcribes
     * via a dedicated speech-to-text endpoint; one transcribing via a chat completion attaches the audio content as it is.
     *
     * @implNote The default implementation returns the audio content unchanged.
     * @param audio The audio content to transcribe.
     * @return The audio content in the format which the transcribe endpoint accepts.
     * @throws AIException If the audio content cannot be converted into that format.
     * @since 1.7.1
     */
    default byte[] buildTranscribeContent(byte[] audio) {
        return audio;
    }

    /**
     * Parses transcription text from the API response JSON of a transcribe operation.
     *
     * @implNote The default implementation throws UnsupportedOperationException.
     * @param responseJson The API response JSON.
     * @return The extracted transcription text from the API response JSON.
     * @throws AIResponseException If the response JSON contains an error object, or is missing expected transcription text.
     */
    default String parseTranscribeResponse(JsonObject responseJson) throws AIResponseException {
        throw new UnsupportedOperationException("Please implement parseTranscribeResponse(JsonObject responseJson) for this AI provider");
    }

    /**
     * Builds the JSON request payload for all generate audio operations.
     *
     * @implNote The default implementation throws UnsupportedOperationException.
     * @param service The visiting AI service.
     * @param text The text to convert to audio.
     * @param options The audio generation options.
     * @return The JSON request payload.
     * @since 1.2
     */
    default JsonObject buildGenerateAudioPayload(AIService service, String text, GenerateAudioOptions options) {
        throw new UnsupportedOperationException(
            "Please implement buildGenerateAudioPayload(AIService service, String text, GenerateAudioOptions options) for this AI provider"
        );
    }

    /**
     * Parses audio content from the API response body of generate audio operation.
     * <p>
     * The returned stream must be closed by the caller.
     *
     * @implNote The default implementation returns the response body directly.
     * @param responseBody The API response body, usually either the raw audio file or a JSON object with an encoded audio file, along with some meta data.
     * @return The extracted audio content from the API response body.
     * @throws AIResponseException If the response cannot be parsed as JSON, contains an error object, or is missing expected audio content.
     * @since 1.2
     */
    default InputStream parseAudioContent(InputStream responseBody) throws AIResponseException {
        return responseBody;
    }

}
