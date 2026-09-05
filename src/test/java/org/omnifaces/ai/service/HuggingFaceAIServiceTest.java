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
import static org.omnifaces.ai.AIModality.AUDIO_GENERATION;
import static org.omnifaces.ai.AIModality.IMAGE_ANALYSIS;
import static org.omnifaces.ai.AIModality.IMAGE_GENERATION;
import static org.omnifaces.ai.AIModality.VIDEO_ANALYSIS;
import static org.omnifaces.ai.AIModality.VIDEO_GENERATION;
import static org.omnifaces.ai.AIProvider.HUGGINGFACE;

import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.DeliberateFailures;

/**
 * Hugging Face routes models of every vendor and publishes their modalities in a listing of its own. The listing is consulted first; the model name is what the
 * modalities are read from for as long as the listing cannot be obtained, which is what an endpoint nobody serves puts these tests on.
 */
class HuggingFaceAIServiceTest {

    /** An endpoint which is no HTTP endpoint at all, so every model listing fetch fails at once without touching the network. */
    private static final String UNSERVED_ENDPOINT = "unserved:///v1/";

    /**
     * A listing which cannot be obtained is logged as a warning, which these tests provoke on purpose. The filter is installed once and never changed, so a
     * test running beside this one keeps every warning of its own.
     */
    @BeforeAll
    static void dropTheWarningsOfTheDeliberatelyUnservedListing() {
        DeliberateFailures.dropMessagesContaining(ModelModalitiesRegistry.class.getPackageName(), "Cannot obtain");
    }

    /**
     * Generation is routed to no provider at all, so it is refused before the listing is even consulted.
     */
    @Test
    void supportsModality_generation_isRefusedWhateverTheListingStates() {
        var service = newService("black-forest-labs/FLUX.1-dev");

        assertFalse(service.supportsModality(AUDIO_GENERATION));
        assertFalse(service.supportsModality(VIDEO_GENERATION));
    }

    @Test
    void supportsModality_withoutAListing_readsTheModalitiesFromTheModelName() {
        assertTrue(newService("meta-llama/Llama-3.2-11B").supportsModality(IMAGE_ANALYSIS), "every routed model is taken to read images");
        assertTrue(newService("black-forest-labs/FLUX.1-image").supportsModality(IMAGE_GENERATION));
        assertFalse(newService("meta-llama/Llama-3.2-11B").supportsModality(IMAGE_GENERATION));
        assertTrue(newService("openai/whisper-large-v3").supportsModality(AUDIO_ANALYSIS));
        assertTrue(newService("nvidia/parakeet-transcribe").supportsModality(AUDIO_ANALYSIS));
        assertFalse(newService("meta-llama/Llama-3.2-11B").supportsModality(AUDIO_ANALYSIS));
        assertTrue(newService("qwen/qwen2.5-video-7b").supportsModality(VIDEO_ANALYSIS));
        assertFalse(newService("meta-llama/Llama-3.2-11B").supportsModality(VIDEO_ANALYSIS));
    }

    @Test
    void capabilities_areWhatTheRoutedOpenAICompatibleApiServes() {
        var service = newService("meta-llama/Llama-3.2-11B");

        assertTrue(service.supportsStreaming());
        assertTrue(service.supportsStructuredOutput());
        assertFalse(service.supportsFileAttachments());
        assertFalse(service.supportsOpenAIResponsesApi());
        assertFalse(service.supportsOpenAIFilesApi());
        assertFalse(service.supportsOpenAIModerationCapability(Set.of("hate")));
        assertFalse(service.supportsOpenAITranscriptionCapability());
    }

    private static HuggingFaceAIService newService(String model) {
        return new HuggingFaceAIService(AIConfig.of(HUGGINGFACE, "test-api-key").withModel(model).withEndpoint(UNSERVED_ENDPOINT));
    }

}
