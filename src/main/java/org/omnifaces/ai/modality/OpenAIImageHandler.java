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

import static org.omnifaces.ai.helper.ImageHelper.toImageDataUri;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.GenerateImageOptions;
import org.omnifaces.ai.service.OpenAIService;

/**
 * Default image handler for OpenAI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public class OpenAIImageHandler extends BaseAIImageHandler {

    @Override
    public JsonObject buildVisionPayload(AIService service, byte[] image, String prompt) {
        var supportsResponsesApi = ((OpenAIService) service).supportsResponsesApi();
        var imageJson = Json.createObjectBuilder()
            .add("type", supportsResponsesApi ? "input_image" : "image_url");

        if (supportsResponsesApi) {
            imageJson.add("image_url", toImageDataUri(image));
        }
        else {
            imageJson.add("image_url", Json.createObjectBuilder().add("url", toImageDataUri(image)));
        }

        return Json.createObjectBuilder()
            .add("model", service.getModelName())
            .add(supportsResponsesApi ? "input" : "messages", Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                    .add("role", "user")
                    .add("content", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                            .add("type", supportsResponsesApi ? "input_text" : "text")
                            .add("text", prompt))
                        .add(imageJson))))
            .build();
    }

    @Override
    public JsonObject buildGenerateImagePayload(AIService service, String prompt, GenerateImageOptions options) {
        return Json.createObjectBuilder()
            .add("model", service.getModelName())
            .add("prompt", prompt)
            .add("n", 1)
            .add("size", options.getSize())
            .add("quality", options.getQuality())
            .add("output_format", options.getOutputFormat())
            .build();
    }

}