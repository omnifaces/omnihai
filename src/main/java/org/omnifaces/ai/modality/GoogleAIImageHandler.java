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

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.GenerateImageOptions;
import org.omnifaces.ai.service.GoogleAIService;

/**
 * Default image handler for Google AI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see GoogleAIService
 */
public class GoogleAIImageHandler extends BaseAIImageHandler {

    @Override
    public JsonObject buildGenerateImagePayload(AIService service, String prompt, GenerateImageOptions options) {
        var generationConfig = Json.createObjectBuilder()
            .add("responseModalities", Json.createArrayBuilder().add("IMAGE"))
            .add("imageConfig", Json.createObjectBuilder()
                .add("aspectRatio", options.getAspectRatio()));

        return Json.createObjectBuilder()
            .add("contents", Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                    .add("parts", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                            .add("text", prompt)))))
            .add("generationConfig", generationConfig)
            .build();
    }

    @Override
    public List<String> getImageResponseContentPaths() {
        return List.of("candidates[0].content.parts[0].inlineData.data");
    }
}