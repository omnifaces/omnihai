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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.GenerateImageOptions;

/**
 * Google asks for an image by naming it as the response modality of an otherwise ordinary chat turn, and answers with it inline.
 */
class GoogleAIImageHandlerTest {

    private final GoogleAIImageHandler handler = new GoogleAIImageHandler();

    @Test
    void buildGenerateImagePayload_asksForAnImageBackAndCarriesThePrompt() {
        var payload = handler.buildGenerateImagePayload(newService(), "A cat", GenerateImageOptions.DEFAULT);

        var config = payload.getJsonObject("generationConfig");
        assertEquals("IMAGE", config.getJsonArray("responseModalities").getString(0));
        assertEquals(
            "A cat", payload.getJsonArray("contents").getJsonObject(0).getJsonArray("parts").getJsonObject(0).getString("text")
        );
    }

    @Test
    void buildGenerateImagePayload_statesTheAspectRatio() {
        var options = GenerateImageOptions.newBuilder().aspectRatio("16:9").build();

        var config = handler.buildGenerateImagePayload(newService(), "A cat", options).getJsonObject("generationConfig");

        assertEquals("16:9", config.getJsonObject("imageConfig").getString("aspectRatio"));
    }

    @Test
    void parseImageContent_decodesTheInlineImage() {
        var encoded = Base64.getEncoder().encodeToString("image bytes".getBytes(UTF_8));
        var response = parseJson("{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"data\":\"" + encoded + "\"}}]}}]}");

        assertArrayEquals("image bytes".getBytes(UTF_8), handler.parseImageContent(response));
    }

    private static AIService newService() {
        return AIConfig.of(AIProvider.GOOGLE, "test-api-key").withModel("gemini-3-pro-image-preview").createService();
    }

}
