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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIModality.AUDIO_ANALYSIS;
import static org.omnifaces.ai.AIModality.IMAGE_ANALYSIS;
import static org.omnifaces.ai.AIModality.VIDEO_ANALYSIS;
import static org.omnifaces.ai.AIProvider.MISTRAL;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;

/**
 * Mistral names its models under two schemes at once: a legacy dated one whose major version is a YYMM number, and a semantic one. A capability floor therefore
 * has to be compared against the floor of the model's own scheme, else a semantic floor matches every dated model and the other way around.
 */
class MistralAIServiceTest {

    private static final String API_KEY = "test-api-key";
    private static final String DATED_BELOW_FLOOR = "mistral-small-2312";
    private static final String DATED_ABOVE_FLOOR = "mistral-medium-2508";
    private static final String SEMANTIC_BELOW_FLOOR = "mistral-medium-3";
    private static final String SEMANTIC_ABOVE_FLOOR = "mistral-medium-3-5";
    private static final String LATEST = "mistral-large-latest";
    private static final String VOXTRAL = "voxtral-small-2507";
    private static final String VOXTRAL_MINI = "voxtral-mini-2507";

    @Test
    void supportsModality_servesImageAnalysisOnEveryModel() {
        assertTrue(newService(SEMANTIC_ABOVE_FLOOR).supportsModality(IMAGE_ANALYSIS));
        assertFalse(newService(SEMANTIC_ABOVE_FLOOR).supportsModality(VIDEO_ANALYSIS));
    }

    @Test
    void supportsModality_servesAudioAnalysisOnVoxtralAlone() {
        assertFalse(newService(SEMANTIC_ABOVE_FLOOR).supportsModality(AUDIO_ANALYSIS));
        assertTrue(newService(VOXTRAL).supportsModality(AUDIO_ANALYSIS));
    }

    /**
     * A dated model is gated against the dated floor, so the small semantic floor may not let every dated model through.
     */
    @Test
    void supportsStreaming_datedModel_isGatedAgainstTheDatedFloor() {
        assertFalse(newService(DATED_BELOW_FLOOR).supportsStreaming());
        assertTrue(newService(DATED_ABOVE_FLOOR).supportsStreaming());
    }

    @Test
    void supportsStreaming_semanticModel_isGatedAgainstTheSemanticFloor() {
        assertFalse(newService(SEMANTIC_BELOW_FLOOR).supportsStreaming());
        assertTrue(newService(SEMANTIC_ABOVE_FLOOR).supportsStreaming());
    }

    /**
     * A {@code -latest} model carries no version to compare, and always points at a current model, so it passes every floor.
     */
    @Test
    void supportsStreamingAndReasoningEffort_latestModel_passTheFloor() {
        assertTrue(newService(LATEST).supportsStreaming());
        assertTrue(newService(LATEST).supportsReasoningEffort());
    }

    @Test
    void supportsReasoningEffort_datedModelBelowItsOwnFloor_isFalse() {
        assertFalse(newService(DATED_ABOVE_FLOOR).supportsReasoningEffort());
        assertTrue(newService(SEMANTIC_ABOVE_FLOOR).supportsReasoningEffort());
    }

    /**
     * The semantic models reference an uploaded document by a signed URL, the legacy dated ones by a bare file id which the signed URL would break.
     */
    @Test
    void supportsSignedUrl_followsTheModelIdScheme() {
        assertTrue(newService(SEMANTIC_ABOVE_FLOOR).supportsSignedUrl());
        assertFalse(newService(DATED_ABOVE_FLOOR).supportsSignedUrl());
    }

    @Test
    void supportsOpenAITranscriptionCapability_isGatedAtVoxtralMini() {
        assertTrue(newService(VOXTRAL_MINI).supportsOpenAITranscriptionCapability());
        assertFalse(newService(SEMANTIC_ABOVE_FLOOR).supportsOpenAITranscriptionCapability());
    }

    @Test
    void openAICapabilities_areFixedRegardlessOfTheModel() {
        var service = newService(SEMANTIC_ABOVE_FLOOR);

        assertTrue(service.supportsFileAttachments());
        assertTrue(service.supportsStructuredOutput());
        assertTrue(service.supportsOpenAIFilesApi());
        assertFalse(service.supportsOpenAIResponsesApi());
        assertFalse(service.supportsOpenAIModerationCapability(Set.of("hate")));
    }

    private static MistralAIService newService(String model) {
        return (MistralAIService) AIConfig.of(MISTRAL, API_KEY).withModel(model).createService();
    }

}
