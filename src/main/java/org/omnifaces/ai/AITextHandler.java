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
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.JsonObject;

import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.modality.BaseAITextHandler;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.ModerationResult;
import org.omnifaces.ai.model.Sse.Event;

/**
 * Handler for text-based AI operations including chat, streaming, text analysis, and content moderation.
 * <p>
 * Covers:
 * <ul>
 * <li>chat payload construction and stream processing</li>
 * <li>summarization</li>
 * <li>key-point extraction</li>
 * <li>language detection</li>
 * <li>translation</li>
 * <li>proofreading</li>
 * <li>content moderation</li>
 * </ul>
 * <p>
 * Creative / interpretive tasks use a configurable temperature ({@link #getDefaultCreativeTemperature()});
 * classification tasks use low fixed temperature of {@link ChatOptions#DETERMINISTIC_TEMPERATURE}.
 * <p>
 * The implementations must be stateless and able to be {@link ApplicationScoped}.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIService
 * @see BaseAITextHandler
 */
public interface AITextHandler extends Serializable {

    /**
     * Builds the JSON request payload for all chat operations.
     * @param service The visiting AI service.
     * @param input The chat input.
     * @param options The chat options.
     * @param streaming Whether this is for chat streaming endpoint.
     * @return The JSON request payload.
     * @throws UnsupportedOperationException If streaming is requested but not supported as per {@link AIService#supportsStreaming()},
     * or if structured output is requested but not supported as per {@link AIService#supportsStructuredOutput()}.
     */
    JsonObject buildChatPayload(AIService service, ChatInput input, ChatOptions options, boolean streaming);

    /**
     * Processes each stream event for {@link AIService#chatStream(String, ChatOptions, Consumer)}.
     * The default implementation throws UnsupportedOperationException.
     * @param service The visiting AI service.
     * @param event Stream event.
     * @param onToken Callback receiving each stream data chunk (often one word/token/line).
     * @return {@code true} to continue processing the stream, or {@code false} when end of stream is reached.
     */
    default boolean processChatStreamEvent(AIService service, Event event, Consumer<String> onToken) {
        throw new UnsupportedOperationException("Please implement processStreamEvent(AIService service, Event event, Consumer<String> onToken) method in class " + getClass().getSimpleName());
    }

    /**
     * Returns the default temperature used for creative or interpretative text analysis related operations in
     * {@link AIService}. These operations typically use a moderate temperature (e.g. 0.3-0.7) to allow natural
     * phrasing and reasoning.
     * <ul>
     * <li>summarization</li>
     * <li>key-point extraction</li>
     * </ul>
     * <p>
     * Deterministic / classification-style operations use by design a fixed low temperature of
     * {@link ChatOptions#DETERMINISTIC_TEMPERATURE} to ensure consistent, factual and reproducible results:
     * <ul>
     * <li>language detection</li>
     * <li>translation</li>
     * <li>proofreading</li>
     * <li>content moderation</li>
     * </ul>
     * <p>
     * The default implementation {@link BaseAITextHandler} returns 0.5.
     *
     * @return default temperature value in range 0.0-1.0 for summarization and key-point extraction.
     * @see AIService#summarize(String, int)
     * @see AIService#extractKeyPoints(String, int)
     */
    double getDefaultCreativeTemperature();

    /**
     * Builds the system prompt for {@link AIService#summarize(String, int)} and {@link AIService#summarizeAsync(String, int)}.
     *
     * @param maxWords Maximum words in summary.
     * @return The system prompt.
     */
    String buildSummarizePrompt(int maxWords);

    /**
     * Builds the system prompt for {@link AIService#extractKeyPoints(String, int)} and {@link AIService#extractKeyPointsAsync(String, int)}.
     *
     * @param maxPoints Maximum number of key points.
     * @return The system prompt.
     */
    String buildExtractKeyPointsPrompt(int maxPoints);

    /**
     * Builds the system prompt for {@link AIService#detectLanguage(String)} and {@link AIService#detectLanguageAsync(String)}.
     *
     * @return The system prompt.
     */
    String buildDetectLanguagePrompt();

    /**
     * Builds the system prompt for {@link AIService#translate(String, String, String)} and {@link AIService#translateAsync(String, String, String)}.
     *
     * @param sourceLang Source language ISO 639-1 code, or {@code null} for auto-detection.
     * @param targetLang Target language ISO 639-1 code.
     * @return The system prompt.
     */
    String buildTranslatePrompt(String sourceLang, String targetLang);

    /**
     * Builds the system prompt for {@link AIService#proofread(String)} and {@link AIService#proofreadAsync(String)}.
     *
     * @return The system prompt.
     */
    String buildProofreadPrompt();

    /**
     * Parses message content from the API response body returned by chat operation.
     *
     * @param responseBody The API response body, usually a JSON object with the AI response, along with some meta data.
     * @return The extracted message content from the API response body.
     * @throws AIResponseException If the response cannot be parsed as JSON, contains an error object, or is missing expected message content.
     */
    String parseChatResponse(String responseBody) throws AIResponseException;

    /**
     * Parses file ID from the API response body of file upload operation.
     * The default implementation throws UnsupportedOperationException.
     * @param responseBody The API response body, usually a JSON object with the file ID.
     * @return The extracted file ID from the API response body.
     * @throws AIResponseException If the response cannot be parsed as JSON, contains an error object, or is missing expected file ID.
     */
    default String parseFileResponse(String responseBody) throws AIResponseException {
        throw new UnsupportedOperationException("Please implement parseFileResponse(String responseBody) method in class " + getClass().getSimpleName());
    }

    /**
     * Builds the system prompt for {@link AIService#moderateContent(String, ModerationOptions)} and {@link AIService#moderateContentAsync(String, ModerationOptions)}.
     *
     * @param options Moderation options containing categories and threshold.
     * @return The system prompt.
     */
    String buildModerationPrompt(ModerationOptions options);

    /**
     * Builds the JSON schema for structured output by {@link AIService#moderateContent(String, ModerationOptions)} and {@link AIService#moderateContentAsync(String, ModerationOptions)}.
     * <p>
     * The returned schema enforces that the AI model returns a valid JSON object with a {@code scores} property
     * containing numeric values (0.0-1.0) for each category specified in the moderation options.
     * <p>
     * Example output when used:
     * <pre>
     * {
     *   "scores": {
     *     "sexual": 0.1,
     *     "violence": 0.0,
     *     "hate": 0.2
     *   }
     * }
     * </pre>
     *
     * @param options Moderation options containing categories to include in the schema.
     * @return The JSON schema object for moderation response format.
     * @see AIService#moderateContent(String, ModerationOptions)
     * @see AIService#moderateContentAsync(String, ModerationOptions)
     */
    JsonObject buildModerationJsonSchema(ModerationOptions options);

    /**
     * Parses the moderation result from response returned by {@link AIService#moderateContent(String, ModerationOptions)} and {@link AIService#moderateContentAsync(String, ModerationOptions)}.
     *
     * @param responseBody The response from the AI model containing moderation scores.
     * @param options The moderation options containing categories and threshold.
     * @return The parsed moderation result.
     * @throws AIResponseException If the response cannot be parsed.
     */
    ModerationResult parseModerationResult(String responseBody, ModerationOptions options) throws AIResponseException;
}
