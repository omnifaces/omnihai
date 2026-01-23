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

import static org.omnifaces.ai.helper.ImageHelper.toImageBase64;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIService;

/**
 * Default image handler for Ollama AI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public class OllamaAIImageHandler extends BaseAIImageHandler {

    @Override
    public JsonObject buildVisionPayload(AIService service, byte[] image, String prompt) {
        return Json.createObjectBuilder()
            .add("model", service.getModelName())
            .add("messages", Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                    .add("role", "user")
                    .add("content", prompt)
                    .add("images", Json.createArrayBuilder()
                        .add(toImageBase64(image)))))
            .add("stream", false)
            .build();
    }
}