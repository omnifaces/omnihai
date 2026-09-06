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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIModality.AUDIO_ANALYSIS;
import static org.omnifaces.ai.AIModality.IMAGE_ANALYSIS;
import static org.omnifaces.ai.AIModality.VIDEO_ANALYSIS;
import static org.omnifaces.ai.AIProvider.OLLAMA;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;

/**
 * Ollama runs models of every vendor and publishes no capabilities of its own, so image analysis follows the model version or the model name and nothing else
 * is served.
 */
class OllamaAIServiceTest {

    @Test
    void supportsModality_imageAnalysis_followsTheVersionOrTheModelName() {
        assertFalse(newService("llama3.2").supportsModality(IMAGE_ANALYSIS));
        assertTrue(newService("llama4").supportsModality(IMAGE_ANALYSIS));
        assertTrue(newService("llama3.2-vision").supportsModality(IMAGE_ANALYSIS));
        assertTrue(newService("llava").supportsModality(IMAGE_ANALYSIS));
        assertTrue(newService("gemma3").supportsModality(IMAGE_ANALYSIS));
    }

    @Test
    void supportsModality_servesNothingBesideImageAnalysis() {
        var service = newService("llama4");

        assertFalse(service.supportsModality(AUDIO_ANALYSIS));
        assertFalse(service.supportsModality(VIDEO_ANALYSIS));
    }

    @Test
    void supportsStructuredOutput_isServedWhateverTheModel() {
        assertTrue(newService("llama3.2").supportsStructuredOutput());
    }

    @Test
    void paths_areOllamasOwnRatherThanTheOpenAICompatibleOnes() {
        var service = newService("llama4");

        assertEquals("api/chat", service.getChatPath(false));
        assertEquals("api/chat", service.getChatPath(true));
        assertEquals("files", service.getFilesPath());
    }

    private static OllamaAIService newService(String model) {
        return new OllamaAIService(AIConfig.of(OLLAMA, "test-api-key").withModel(model));
    }

}
