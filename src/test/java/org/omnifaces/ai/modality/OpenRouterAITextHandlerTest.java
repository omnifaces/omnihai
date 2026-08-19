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
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import org.junit.jupiter.api.Test;

class OpenRouterAITextHandlerTest {

    private final OpenRouterAITextHandler handler = new OpenRouterAITextHandler();

    @Test
    void parseChatResponse_content_isPreferredOverReasoning() {
        var responseJson = parseJson("{\"choices\":[{\"message\":{\"content\":\"The answer\",\"reasoning\":\"Let me think\"}}]}");

        assertEquals("The answer", handler.parseChatResponse(responseJson));
    }

    @Test
    void parseChatResponse_nullContent_fallsBackToReasoning() {
        var responseJson = parseJson("{\"choices\":[{\"message\":{\"content\":null,\"reasoning\":\"The answer, which the provider left in here\"}}]}");

        assertEquals("The answer, which the provider left in here", handler.parseChatResponse(responseJson));
    }

}
