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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.omnifaces.ai.AIProvider.GOOGLE;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIStrategy;
import org.omnifaces.ai.modality.DefaultAIVideoHandler;
import org.omnifaces.ai.modality.GoogleAITextHandler;

class BaseAIServiceHandlerTest {

    /** A handler decorating the default one, as an application would to adapt the prompt to its own domain. */
    public static class CustomVideoHandler extends DefaultAIVideoHandler {

        private static final long serialVersionUID = 1L;

        @Override
        public String buildAnalyzeVideoPrompt() {
            return "Describe this security camera footage.";
        }

    }

    private static BaseAIService newService(AIStrategy strategy) {
        return (BaseAIService) AIConfig.of(GOOGLE, "test-api-key").withStrategy(strategy).createService();
    }

    @Test
    void videoHandler_notConfigured_isProviderDefault() {
        var service = newService(AIStrategy.empty());

        assertInstanceOf(DefaultAIVideoHandler.class, service.videoHandler);
    }

    @Test
    void videoHandler_configured_isUsed() {
        var service = newService(AIStrategy.empty().withVideoHandler(CustomVideoHandler.class));

        assertEquals("Describe this security camera footage.", service.videoHandler.buildAnalyzeVideoPrompt());
    }

    @Test
    void videoHandler_configured_leavesOtherHandlersAtProviderDefault() {
        var service = newService(AIStrategy.empty().withVideoHandler(CustomVideoHandler.class));

        assertInstanceOf(CustomVideoHandler.class, service.videoHandler);
        assertInstanceOf(GoogleAITextHandler.class, service.textHandler);
    }

}
