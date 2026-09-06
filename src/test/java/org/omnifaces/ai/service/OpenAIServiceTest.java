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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIModality.AUDIO_ANALYSIS;
import static org.omnifaces.ai.AIModality.AUDIO_GENERATION;
import static org.omnifaces.ai.AIModality.IMAGE_ANALYSIS;
import static org.omnifaces.ai.AIModality.IMAGE_GENERATION;
import static org.omnifaces.ai.AIModality.VIDEO_GENERATION;
import static org.omnifaces.ai.AIProvider.OPENAI;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.ModerationOptions.Category;

/**
 * The modalities OpenAI publishes follow the model version where it has one and the model name otherwise, as the image, audio and transcription models carry no
 * comparable version. The native moderation response is scored per category here as well, as the categories the caller asked for decide both which scores are
 * kept and whether the content counts as flagged.
 */
class OpenAIServiceTest {

    private static final String API_KEY = "test-api-key";
    private static final String GPT_3_5 = "gpt-3.5-turbo";
    private static final String GPT_4 = "gpt-4o";
    private static final String VISION = "gpt-4-vision-preview";
    private static final String TRANSCRIBE = "gpt-4o-transcribe";
    private static final String TTS = "tts-1";
    private static final String DALL_E = "dall-e-3";

    @Test
    void supportsModality_imageAnalysis_followsTheVersionOrTheVisionSuffix() {
        assertFalse(newService(GPT_3_5).supportsModality(IMAGE_ANALYSIS));
        assertTrue(newService(GPT_4).supportsModality(IMAGE_ANALYSIS));
        assertTrue(newService(VISION).supportsModality(IMAGE_ANALYSIS));
    }

    @Test
    void supportsModality_imageGeneration_followsTheDallEVersionOrTheImageSuffix() {
        assertFalse(newService(GPT_4).supportsModality(IMAGE_GENERATION));
        assertTrue(newService(DALL_E).supportsModality(IMAGE_GENERATION));
    }

    @Test
    void supportsModality_audioAnalysis_followsTheVersionOrTheTranscribeSuffix() {
        assertFalse(newService(GPT_3_5).supportsModality(AUDIO_ANALYSIS));
        assertTrue(newService(TRANSCRIBE).supportsModality(AUDIO_ANALYSIS));
    }

    @Test
    void supportsModality_audioGeneration_followsTheTtsSuffixAlone() {
        assertFalse(newService(GPT_4).supportsModality(AUDIO_GENERATION));
        assertTrue(newService(TTS).supportsModality(AUDIO_GENERATION));
    }

    @Test
    void supportsModality_servesNoVideo() {
        assertFalse(newService(GPT_4).supportsModality(VIDEO_GENERATION));
    }

    @Test
    void supportsOpenAIResponsesApi_isGatedAtGpt4() {
        assertFalse(newService(GPT_3_5).supportsOpenAIResponsesApi());
        assertTrue(newService(GPT_4).supportsOpenAIResponsesApi());
    }

    /**
     * A category the moderation endpoint does not score has to fall back to chat based moderation, as asking for it would silently drop it.
     */
    @Test
    void supportsOpenAIModerationCapability_isFalseAsSoonAsOneCategoryIsUnknown() {
        var service = newService(GPT_4);

        assertTrue(service.supportsOpenAIModerationCapability(Set.of("hate", "violence")));
        assertFalse(service.supportsOpenAIModerationCapability(Set.of("hate", "not-a-category")));
    }

    @Test
    void parseOpenAIModerationResult_keepsTheScoresOfTheRequestedCategoriesAlone() {
        var result = newService(GPT_4).parseOpenAIModerationResult(parseJson("""
            {"results":[{"category_scores":{"hate":0.1,"violence":0.2,"self-harm":0.9}}]}
            """), ModerationOptions.newBuilder().categories(Category.HATE, Category.VIOLENCE).build());

        assertEquals(Set.of("hate", "violence"), result.getScores().keySet());
    }

    /**
     * OpenAI scores the subcategories under a {@code parent/child} name, which belong to the parent category the caller asked for.
     */
    @Test
    void parseOpenAIModerationResult_keepsASubcategoryOfARequestedCategory() {
        var result = newService(GPT_4).parseOpenAIModerationResult(parseJson("""
            {"results":[{"category_scores":{"hate/threatening":0.4}}]}
            """), ModerationOptions.newBuilder().categories(Category.HATE).build());

        assertEquals(Set.of("hate/threatening"), result.getScores().keySet());
    }

    @Test
    void parseOpenAIModerationResult_flagsWhenAScoreExceedsTheThreshold() {
        var options = ModerationOptions.newBuilder().categories(Category.HATE).threshold(0.5).build();

        assertFalse(newService(GPT_4).parseOpenAIModerationResult(parseJson("""
            {"results":[{"category_scores":{"hate":0.4}}]}
            """), options).isFlagged());
        assertTrue(newService(GPT_4).parseOpenAIModerationResult(parseJson("""
            {"results":[{"category_scores":{"hate":0.6}}]}
            """), options).isFlagged());
    }

    @Test
    void parseOpenAIModerationResult_resultWithoutScores_isNotFlagged() {
        var result = newService(GPT_4).parseOpenAIModerationResult(parseJson("""
            {"results":[{}]}
            """), ModerationOptions.DEFAULT);

        assertFalse(result.isFlagged());
        assertTrue(result.getScores().isEmpty());
    }

    @Test
    void parseOpenAIModerationResult_emptyResults_throws() {
        var service = newService(GPT_4);
        var responseJson = parseJson("{\"results\":[]}");

        assertThrows(AIResponseException.class, () -> service.parseOpenAIModerationResult(responseJson, ModerationOptions.DEFAULT));
    }

    private static OpenAIService newService(String model) {
        return (OpenAIService) AIConfig.of(OPENAI, API_KEY).withModel(model).createService();
    }

    // =================================================================================================================
    // Capabilities the model name states where the version does not
    // =================================================================================================================

    /**
     * A fine-tune and an Azure deployment carry a name of the customer's own, whose version states nothing, so the name is what the modality is read from.
     */
    @Test
    void supportsModality_modelWhoseVersionStatesNothing_readsTheModalityFromTheName() {
        assertTrue(newService("vision-preview").supportsModality(IMAGE_ANALYSIS));
        assertTrue(newService("gpt-image-1").supportsModality(IMAGE_GENERATION));
        assertTrue(newService("whisper-transcribe").supportsModality(AUDIO_ANALYSIS));
    }

    // =================================================================================================================
    // Capabilities and paths of the OpenAI API
    // =================================================================================================================

    @Test
    void supportsOpenAITranscriptionCapability_isServedWhateverTheModel() {
        assertTrue(newService(GPT_3_5).supportsOpenAITranscriptionCapability());
    }

    /**
     * Both of these are served by the Responses API alone, so they follow whether that API is addressed.
     */
    @Test
    void webSearchAndAttachmentsInHistory_followTheResponsesApi() {
        assertFalse(newService(GPT_3_5).supportsWebSearch());
        assertTrue(newService(GPT_4).supportsWebSearch());
        assertFalse(newService(GPT_3_5).supportsFileAttachmentsInHistory());
        assertTrue(newService(GPT_4).supportsFileAttachmentsInHistory());
    }

    @Test
    void getChatPath_followsWhetherTheResponsesApiIsServed() {
        assertEquals("chat/completions", newService(GPT_3_5).getChatPath(false));
        assertEquals("responses", newService(GPT_4).getChatPath(false));
    }

    @Test
    void generationPaths_areTheOpenAIEndpoints() {
        var service = newService(GPT_4);

        assertEquals("images/generations", service.getGenerateImagePath());
        assertEquals("audio/speech", service.getGenerateAudioPath());
    }

    @Test
    void getUploadedFileJsonStructure_namesThePropertiesOpenAIStatesThemUnder() {
        var structure = newService(GPT_4).getUploadedFileJsonStructure();

        assertEquals("data", structure.filesArrayProperty());
        assertEquals("filename", structure.fileNameProperty());
        assertEquals("id", structure.fileIdProperty());
        assertEquals("created_at", structure.createdAtProperty());
    }

    @Test
    void parseOpenAITranscribeResponse_readsTheTranscript() {
        assertEquals("hello there", newService(TRANSCRIBE).parseOpenAITranscribeResponse(parseJson("{\"text\":\"hello there\"}")));
    }

    @Test
    void parseOpenAITranscribeResponse_answerWithoutATranscript_isRejected() {
        var service = newService(TRANSCRIBE);
        var responseJson = parseJson("{\"text\":\" \"}");

        assertThrows(AIResponseException.class, () -> service.parseOpenAITranscribeResponse(responseJson));
    }

}
