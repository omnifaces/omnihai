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

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.GenerateImageOptions;
import org.omnifaces.ai.service.XAIService;

/**
 * Default image handler for xAI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see XAIService
 */
public class XAIImageHandler extends OpenAIImageHandler {

    private static final long serialVersionUID = 1L;

    @Override
    public JsonObject buildGenerateImagePayload(AIService service, String prompt, GenerateImageOptions options) {
        return Json.createObjectBuilder()
            .add("model", service.getModelName())
            .add("prompt", prompt)
            .add("n", 1)
            .add("aspect_ratio", options.getAspectRatio())
            .add("response_format", "b64_json")
            .build();
    }
}