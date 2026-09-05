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
import static org.omnifaces.ai.AIModality.IMAGE_GENERATION;
import static org.omnifaces.ai.AIModality.VIDEO_GENERATION;
import static org.omnifaces.ai.AIProvider.XAI;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;

/**
 * xAI serves an OpenAI compatible API, but not every capability of it, and states its own modalities per Grok version and model name.
 */
class XAIServiceTest {

    @Test
    void supportsModality_imageAnalysis_followsTheVersionOrTheVisionSuffix() {
        assertFalse(newService("grok-3").supportsModality(IMAGE_ANALYSIS));
        assertTrue(newService("grok-4").supportsModality(IMAGE_ANALYSIS));
        assertTrue(newService("grok-2-vision-1212").supportsModality(IMAGE_ANALYSIS));
    }

    @Test
    void supportsModality_generation_followsTheModelName() {
        assertTrue(newService("grok-2-image-1212").supportsModality(IMAGE_GENERATION));
        assertFalse(newService("grok-4").supportsModality(IMAGE_GENERATION));
        assertTrue(newService("grok-video-1").supportsModality(VIDEO_GENERATION));
        assertFalse(newService("grok-4").supportsModality(VIDEO_GENERATION));
    }

    @Test
    void supportsModality_servesNoAudio() {
        assertFalse(newService("grok-4").supportsModality(AUDIO_ANALYSIS));
    }

    @Test
    void supportsReasoningEffort_isTheMultiAgentModelsOfGrok4_20Alone() {
        assertFalse(newService("grok-4").supportsReasoningEffort());
        assertFalse(newService("grok-4-20").supportsReasoningEffort());
        assertTrue(newService("grok-4-20-multi-agent").supportsReasoningEffort());
    }

    @Test
    void supportsOpenAIResponsesApi_isGatedAtGrok4() {
        assertFalse(newService("grok-3").supportsOpenAIResponsesApi());
        assertTrue(newService("grok-4").supportsOpenAIResponsesApi());
    }

    @Test
    void capabilities_whichAreApiBoundRatherThanVersionBound_areServedWhateverTheModel() {
        var service = newService("grok-3");

        assertTrue(service.supportsFileAttachments());
        assertTrue(service.supportsStructuredOutput());
        assertTrue(service.supportsOpenAIFilesApi());
    }

    /**
     * xAI serves neither of these OpenAI endpoints, so the chat model is asked instead.
     */
    @Test
    void moderationAndTranscription_areNotServedByAnEndpointOfTheirOwn() {
        var service = newService("grok-4");

        assertFalse(service.supportsOpenAIModerationCapability(Set.of("hate")));
        assertFalse(service.supportsOpenAITranscriptionCapability());
    }

    private static XAIService newService(String model) {
        return new XAIService(AIConfig.of(XAI, "test-api-key").withModel(model));
    }

}
