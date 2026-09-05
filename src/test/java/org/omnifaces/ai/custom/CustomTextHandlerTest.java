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
package org.omnifaces.ai.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.modality.OpenAITextHandler;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.Location;

/**
 * A handler of one's own lives outside this library's packages and reaches the payload builders through inheritance alone. The builders therefore have to cope
 * with whatever service such a handler hands them, rather than assuming the one its own provider would have supplied.
 */
class CustomTextHandlerTest {

    private static final Location MIAMI = new Location("US", null, "Miami");

    private final CustomTextHandler handler = new CustomTextHandler();

    /**
     * The tool options which only an OpenAI based service offers are left out for a service which is not one, rather than failing on the cast.
     */
    @Test
    void buildTools_serviceWhichIsNotOpenAIBased_offersTheSearchToolWithoutTheOpenAIOptions() {
        var tools = handler.buildTools(mock(AIService.class), ChatOptions.newBuilder().webSearch(MIAMI).build());

        assertFalse(tools.containsKey("tool_choice"), tools.toString());
        assertEquals(1, tools.getJsonArray("tools").size());
        assertEquals("Miami", tools.getJsonArray("tools").getJsonObject(0).getJsonObject("user_location").getString("city"));
    }

    @Test
    void buildTools_withoutWebSearch_offersNoTools() {
        assertTrue(handler.buildTools(mock(AIService.class), ChatOptions.DEFAULT).isEmpty());
    }

    /**
     * A handler of one's own may also name its own web search tool, which the builders take as given.
     */
    @Test
    void buildTools_handlerNamingItsOwnSearchTool_usesThatName() {
        var service = mock(AIService.class);
        when(service.getModelName()).thenReturn("some-model");

        var tools = handler.buildTools(service, ChatOptions.newBuilder().webSearch(Location.GLOBAL).build());

        assertEquals("web_search_of_my_own", tools.getJsonArray("tools").getJsonObject(0).getString("type"));
    }

    /**
     * Stands in for the handler an application writes for a provider this library does not ship, reaching the inherited builders as any subclass would.
     */
    private static class CustomTextHandler extends OpenAITextHandler {

        private static final long serialVersionUID = 1L;

        @Override
        public String getWebSearchToolName() {
            return "web_search_of_my_own";
        }

        private JsonObject buildTools(AIService service, ChatOptions options) {
            var payload = Json.createObjectBuilder();
            buildChatPayloadToolsWithResponsesApi(service, payload, options);
            return payload.build();
        }

    }

}
