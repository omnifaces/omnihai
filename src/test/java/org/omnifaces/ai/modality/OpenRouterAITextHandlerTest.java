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

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIProvider.OPENROUTER;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.Location;

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

    // =================================================================================================================
    // Web search
    // =================================================================================================================

    @Test
    void buildChatPayload_webSearchWithLocation_statesItInTheSystemPrompt() {
        var options = ChatOptions.DEFAULT.withWebSearch(new Location("US", "FL", "Miami"));

        var payload = handler.buildChatPayload(newService(), newInput(), options, false);

        assertTrue(systemPromptOf(payload).contains("Miami"), "OpenRouter takes no location of its own, so it must reach the model through the prompt");
    }

    @Test
    void buildChatPayload_webSearch_asksTheOnlineVariantOfTheModel() {
        var options = ChatOptions.DEFAULT.withWebSearch(new Location("US", "FL", "Miami"));

        var payload = handler.buildChatPayload(newService(), newInput(), options, false);

        assertEquals("deepseek/deepseek-v4-pro:online", payload.getString("model"));
        assertFalse(payload.containsKey("plugins"), "the online suffix is how the search is asked for, so no plugin may be asked for as well");
    }

    @Test
    void buildChatPayload_globalWebSearch_statesNoLocation() {
        var payload = handler.buildChatPayload(newService(), newInput(), ChatOptions.DEFAULT.withWebSearch(Location.GLOBAL), false);

        assertEquals("deepseek/deepseek-v4-pro:online", payload.getString("model"));
        assertFalse(systemPromptOf(payload).contains("Search within"), "a global search is restricted to nowhere");
    }

    @Test
    void buildChatPayload_withoutWebSearch_asksThePlainModel() {
        var payload = handler.buildChatPayload(newService(), newInput(), ChatOptions.DEFAULT, false);

        assertEquals("deepseek/deepseek-v4-pro", payload.getString("model"));
    }

    private static String systemPromptOf(JsonObject payload) {
        return payload.getJsonArray("messages").stream()
            .map(JsonValue::asJsonObject)
            .filter(message -> "system".equals(message.getString("role", null)))
            .map(message -> message.getString("content", ""))
            .collect(joining("\n"));
    }

    private static ChatInput newInput() {
        return ChatInput.newBuilder().message("What is the current weather? High/Low?").build();
    }

    private static AIService newService() {
        return AIConfig.of(OPENROUTER, "test-api-key").withModel("deepseek/deepseek-v4-pro").createService();
    }

}
