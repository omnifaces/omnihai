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
import org.omnifaces.ai.model.GenerateAudioOptions;
import org.omnifaces.ai.service.OpenAIService;

/**
 * Default audio handler for OpenAI service.
 *
 * @author Bauke Scholtz
 * @since 1.2
 * @see OpenAIService
 */
public class OpenAIAudioHandler extends DefaultAIAudioHandler {

    private static final long serialVersionUID = 1L;

    /**
     * @see <a href="https://developers.openai.com/api/reference/resources/audio/subresources/speech/methods/create">API Reference</a>
     */
    @Override
    public JsonObject buildGenerateAudioPayload(AIService service, String text, GenerateAudioOptions options) {
        var payload = Json.createObjectBuilder()
                .add("model", service.getModelName())
                .add("input", text)
                .add("voice", options.useDefaultVoice() ? "alloy" : options.getVoice())
                .add("speed", options.getSpeed());

        if (options.getOutputFormat() != null) {
            payload.add("format", options.getOutputFormat());
        }

        return payload.build();
    }
}
