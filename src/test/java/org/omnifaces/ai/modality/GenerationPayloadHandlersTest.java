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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.GenerateAudioOptions;
import org.omnifaces.ai.model.GenerateImageOptions;

/**
 * The image and audio generators each name the same options under the field their own provider expects, and leave the ones the caller did not set to the
 * provider's own default rather than stating one.
 */
class GenerationPayloadHandlersTest {

    @Test
    void openAIImage_namesTheModelTheSizeAndTheQuality() {
        var options = GenerateImageOptions.newBuilder().size("1024x1024").quality("high").outputFormat("png").build();

        var payload = new OpenAIImageHandler().buildGenerateImagePayload(newService(AIProvider.OPENAI, "gpt-image-1"), "A cat", options);

        assertEquals("gpt-image-1", payload.getString("model"));
        assertEquals("A cat", payload.getString("prompt"));
        assertEquals(1, payload.getInt("n"));
        assertEquals("1024x1024", payload.getString("size"));
        assertEquals("high", payload.getString("quality"));
        assertEquals("png", payload.getString("output_format"));
    }

    /**
     * xAI states the shape as a ratio rather than a pixel size, and always asks for the bytes back inline.
     */
    @Test
    void xaiImage_statesTheAspectRatioAndAsksForTheBytesInline() {
        var options = GenerateImageOptions.newBuilder().aspectRatio("16:9").build();

        var payload = new XAIImageHandler().buildGenerateImagePayload(newService(AIProvider.XAI, "grok-2-image"), "A cat", options);

        assertEquals("16:9", payload.getString("aspect_ratio"));
        assertEquals("b64_json", payload.getString("response_format"));
        assertEquals(1, payload.getInt("n"));
    }

    @Test
    void openAIAudio_namesTheVoiceAndTheSpeed() {
        var options = GenerateAudioOptions.newBuilder().voice("nova").speed(1.5).outputFormat("mp3").build();

        var payload = new OpenAIAudioHandler().buildGenerateAudioPayload(newService(AIProvider.OPENAI, "tts-1"), "Hello", options);

        assertEquals("Hello", payload.getString("input"));
        assertEquals("nova", payload.getString("voice"));
        assertEquals(1.5, payload.getJsonNumber("speed").doubleValue());
        assertEquals("mp3", payload.getString("format"));
    }

    /**
     * A caller who states no voice gets the provider's own, rather than none at all.
     */
    @Test
    void openAIAudio_withoutAVoice_fallsBackToTheProviderDefault() {
        var payload = new OpenAIAudioHandler().buildGenerateAudioPayload(newService(AIProvider.OPENAI, "tts-1"), "Hello", GenerateAudioOptions.DEFAULT);

        assertEquals("alloy", payload.getString("voice"));
        assertFalse(payload.getString("format").isEmpty());
    }

    @Test
    void googleAudio_asksForAudioBackAndNamesTheVoice() {
        var options = GenerateAudioOptions.newBuilder().voice("Puck").build();

        var payload = new GoogleAIAudioHandler().buildGenerateAudioPayload(newService(AIProvider.GOOGLE, "gemini-2.5-flash-preview-tts"), "Hello", options);

        var config = payload.getJsonObject("generationConfig");
        assertEquals("AUDIO", config.getJsonArray("responseModalities").getString(0));
        assertEquals(
            "Puck", config.getJsonObject("speechConfig").getJsonObject("voiceConfig").getJsonObject("prebuiltVoiceConfig").getString("voiceName")
        );
        assertEquals("Hello", payload.getJsonArray("contents").getJsonObject(0).getJsonArray("parts").getJsonObject(0).getString("text"));
    }

    @Test
    void googleAudio_withoutAVoice_fallsBackToTheProviderDefault() {
        var payload = new GoogleAIAudioHandler()
            .buildGenerateAudioPayload(newService(AIProvider.GOOGLE, "gemini-2.5-flash-preview-tts"), "Hello", GenerateAudioOptions.DEFAULT);

        assertTrue(
            !payload.getJsonObject("generationConfig").getJsonObject("speechConfig").getJsonObject("voiceConfig").getJsonObject("prebuiltVoiceConfig")
                .getString("voiceName").isEmpty()
        );
    }

    private static AIService newService(AIProvider provider, String model) {
        return AIConfig.of(provider, "test-api-key").withModel(model).createService();
    }

}
