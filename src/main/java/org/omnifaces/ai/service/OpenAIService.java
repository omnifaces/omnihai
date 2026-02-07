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
package org.omnifaces.ai.service;

import static org.omnifaces.ai.helper.JsonHelper.findFirstNonBlankByPath;
import static org.omnifaces.ai.helper.JsonHelper.isEmpty;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import jakarta.json.Json;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIModelVersion;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.modality.OpenAIImageHandler;
import org.omnifaces.ai.modality.OpenAITextHandler;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.ModerationOptions.Category;
import org.omnifaces.ai.model.ModerationResult;

/**
 * AI service implementation using OpenAI API.
 *
 * <h2>Required Configuration</h2>
 * <p>
 * The following configuration properties must be provided via {@link AIConfig}:
 * <ul>
 *     <li>provider: {@link AIProvider#OPENAI}</li>
 *     <li>apiKey: your OpenAI API key</li>
 * </ul>
 *
 * <h2>Optional Configuration</h2>
 * <p>
 * The following configuration properties are optional.
 * See {@link AIProvider#OPENAI} for defaults.
 * <ul>
 *     <li>model: the model to use</li>
 *     <li>endpoint: the API endpoint URL</li>
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIProvider#OPENAI
 * @see OpenAITextHandler
 * @see OpenAIImageHandler
 * @see BaseAIService
 * @see AIService
 * @see <a href="https://platform.openai.com/docs/api-reference">API Reference</a>
 */
public class OpenAIService extends BaseAIService {

    private static final long serialVersionUID = 1L;

    private static final AIModelVersion GPT_4 = AIModelVersion.of("gpt", 4);
    private static final AIModelVersion GPT_4_1 = AIModelVersion.of("gpt", 4, 1);
    private static final AIModelVersion GPT_5 = AIModelVersion.of("gpt", 5);
    private static final AIModelVersion DALL_E = AIModelVersion.of("dall-e");

    /**
     * Constructs an OpenAI service with the specified configuration.
     *
     * @param config the AI configuration
     * @see AIConfig
     */
    public OpenAIService(AIConfig config) {
        super(config);
    }

    @Override
    public boolean supportsModality(AIModality modality) {
        var currentModelVersion = getModelVersion();
        var fullModelName = getModelName().toLowerCase();

        return switch (modality) {
            case IMAGE_ANALYSIS -> currentModelVersion.gte(GPT_4) || fullModelName.contains("vision");
            case IMAGE_GENERATION -> currentModelVersion.gte(DALL_E) || fullModelName.contains("image");
            case AUDIO_ANALYSIS -> currentModelVersion.gte(GPT_4) || fullModelName.contains("transcribe");
            case AUDIO_GENERATION -> currentModelVersion.gte(GPT_4) || fullModelName.contains("tts");
            case VIDEO_ANALYSIS -> currentModelVersion.gte(GPT_5);
            case VIDEO_GENERATION -> false;
        };
    }

    /**
     * Returns whether this OpenAI based service supports native moderation for the given categories.
     * When {@code true}, {@link #moderateContentAsync(String, ModerationOptions)} will use OpenAI's moderation API.
     * When {@code false}, it falls back to the chat-based moderation in {@link BaseAIService}.
     *
     * @implNote The default implementation checks if all categories are {@link Category#isOpenAISupported()}.
     * @param categories The moderation categories to check.
     * @return {@code true} if all categories are supported by OpenAI's moderation API.
     */
    public boolean supportsOpenAIModerationCapability(Set<String> categories) {
        return categories.stream().allMatch(Category.OPENAI_SUPPORTED_CATEGORY_NAMES::contains);
    }

    /**
     * Returns whether this OpenAI based service supports native transcription.
     * When {@code true}, {@link #transcribeAsync(byte[] audio)} will use OpenAI's transcription API.
     * When {@code false}, it falls back to the chat-based transcription in {@link BaseAIService}.
     *
     * @implNote The default implementation returns true.
     * @return {@code true} if this service supports OpenAI's transcription API.
     */
    public boolean supportsOpenAITranscriptionCapability() {
        return true;
    }

    /**
     * Returns whether this OpenAI based service implementation supports the {@code responses} API as replacement for the legacy {@code chat/completions} API.
     * @implNote The default implementation returns true if {@link #getModelVersion()} is at least {@code gpt-4}.
     * @return Whether this OpenAI based service implementation supports the {@code responses} API as replacement for the legacy {@code chat/completions} API.
     */
    public boolean supportsOpenAIResponsesApi() {
        return getModelVersion().gte(GPT_4);
    }

    @Override
    public boolean supportsStreaming() {
        return supportsOpenAIResponsesApi();
    }

    @Override
    public boolean supportsFileUpload() {
        return getModelVersion().gte(GPT_4_1);
    }

    @Override
    public boolean supportsStructuredOutput() {
        return getModelVersion().gte(GPT_4);
    }

    /**
     * Returns only authorization bearer header with API key as value.
     */
    @Override
    protected Map<String, String> getRequestHeaders() {
        return Map.of("Authorization", "Bearer ".concat(apiKey));
    }

    /**
     * If {@link #supportsOpenAIResponsesApi()} then returns {@code responses} else {@code chat/completions}.
     */
    @Override
    protected String getChatPath(boolean streaming) {
        return supportsOpenAIResponsesApi() ? "responses" : "chat/completions";
    }

    /**
     * Returns {@code files}.
     */
    @Override
    protected String getFilesPath() {
        return "files";
    }

    /**
     * Returns {@code image/generations}.
     */
    @Override
    protected String getGenerateImagePath() {
        return "images/generations";
    }

    @Override
    public CompletableFuture<ModerationResult> moderateContentAsync(String content, ModerationOptions options) throws AIException {
        if (supportsOpenAIModerationCapability(options.getCategories())) {
            var payload = Json.createObjectBuilder().add("input", content).build();
            return HTTP_CLIENT.post(this, "moderations", payload).thenApply(response -> parseOpenAIModerationResult(response, options));
        }
        else {
            return super.moderateContentAsync(content, options);
        }
    }

    /**
     * Parses the moderation result from OpenAI's moderation API response.
     *
     * @param responseBody The JSON response from OpenAI's moderation API.
     * @param options The moderation options containing categories and threshold.
     * @return The parsed moderation result.
     * @throws AIResponseException If the response cannot be parsed as JSON or is missing moderation results.
     */
    protected ModerationResult parseOpenAIModerationResult(String responseBody, ModerationOptions options) throws AIResponseException {
        var responseJson = parseJson(responseBody);
        var results = responseJson.getJsonArray("results");

        if (isEmpty(results)) {
            throw new AIResponseException("Response is empty", responseBody);
        }

        var result = results.getJsonObject(0);
        var scores = new HashMap<String, Double>();
        var flagged = false;

        if (result.containsKey("category_scores")) {
            var categoryScores = result.getJsonObject("category_scores");

            for (var category : categoryScores.keySet()) {
                if (options.getCategories().contains(category.split("/", 2)[0])) {
                    var score = categoryScores.getJsonNumber(category).doubleValue();
                    scores.put(category, score);

                    if (score > options.getThreshold()) {
                        flagged = true;
                    }
                }
            }
        }

        return new ModerationResult(flagged, scores);
    }

    @Override
    public CompletableFuture<String> transcribeAsync(byte[] audio) throws AIException {
        if (supportsOpenAITranscriptionCapability()) {
            var mimeType = MimeType.guessMimeType(audio);
            var attachment = new Attachment(audio, mimeType, "audio." + mimeType.extension(), Map.of("model", getModelName(), "response_format", "json"));
            return HTTP_CLIENT.upload(this, "audio/transcriptions", attachment).thenApply(this::parseOpenAITranscribeResponse);
        }
        else {
            return super.transcribeAsync(audio);
        }
    }

    /**
     * Parses the transcription result from OpenAI's transcription API response.
     *
     * @param responseBody The JSON response from OpenAI's transcription API.
     * @return The transcription result.
     * @throws AIResponseException If the response cannot be parsed as JSON or is missing transcription text.
     * @since 1.1
     */
    protected String parseOpenAITranscribeResponse(String responseBody) throws AIResponseException {
        var responseJson = parseJson(responseBody);
        return findFirstNonBlankByPath(responseJson, List.of("text")).orElseThrow(() -> new AIResponseException("No transcription text found", responseBody));
    }
}
