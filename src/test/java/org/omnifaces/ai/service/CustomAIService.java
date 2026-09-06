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

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIStrategy;
import org.omnifaces.ai.modality.DefaultAIAudioHandler;
import org.omnifaces.ai.modality.DefaultAIImageHandler;
import org.omnifaces.ai.modality.DefaultAITextHandler;
import org.omnifaces.ai.modality.DefaultAIVideoHandler;
import org.omnifaces.ai.modality.OpenAIAudioHandler;
import org.omnifaces.ai.modality.OpenAIImageHandler;
import org.omnifaces.ai.modality.OpenAITextHandler;
import org.omnifaces.ai.modality.OpenRouterAIVideoHandler;

/**
 * A provider which states nothing of its own, so that the defaults of {@link BaseAIService} are what answers.
 */
public class CustomAIService extends BaseAIService {

    private static final long serialVersionUID = 1L;

    /** The configuration of a provider which parses through the default handlers, which is all a service needs to answer a response. */
    public static AIConfig newConfig() {
        return AIConfig.of(CustomAIService.class, "test-api-key").withModel("custom-1").withEndpoint("https://example.org/v1/").withStrategy(
            new AIStrategy(
                DefaultAITextHandler.class, DefaultAIImageHandler.class, DefaultAIAudioHandler.class, DefaultAIVideoHandler.class
            )
        );
    }

    /**
     * The configuration of a provider which builds its payloads through the OpenAI handlers, as the default handlers parse a response but build no request.
     */
    public static AIConfig newConfigWithPayloadBuildingHandlers() {
        return newConfig().withStrategy(
            new AIStrategy(
                OpenAITextHandler.class, OpenAIImageHandler.class, OpenAIAudioHandler.class, OpenRouterAIVideoHandler.class
            )
        );
    }

    public CustomAIService(AIConfig config) {
        super(config);
    }

    @Override
    public boolean supportsModality(AIModality modality) {
        return false;
    }

    @Override
    protected String getChatPath(boolean streaming) {
        return "chat";
    }

}
