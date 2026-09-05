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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
import org.omnifaces.ai.model.ChatOptions.ReasoningEffort;

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

    // =================================================================================================================
    // Inline attachments
    // =================================================================================================================

    /**
     * OpenRouter takes a video under a block of its own, and leaves every other file to the shape the OpenAI base states.
     */
    @Test
    void buildChatPayload_withAVideo_carriesItAsAVideoUrl() {
        var input = ChatInput.newBuilder().message("What happens here?").attach(mp4()).build();

        var content = handler.buildChatPayload(newService(), input, ChatOptions.DEFAULT, false).getJsonArray("messages").getJsonObject(0)
            .getJsonArray("content");

        assertEquals("video_url", content.getJsonObject(0).getString("type"));
        assertTrue(content.getJsonObject(0).getJsonObject("video_url").getString("url").startsWith("data:video/mp4;base64,"));
    }

    @Test
    void buildChatPayload_withADocument_leavesItToTheBaseShape() {
        var input = ChatInput.newBuilder().message("Read it").attach(pdf()).build();

        var content = handler.buildChatPayload(newService(), input, ChatOptions.DEFAULT, false).getJsonArray("messages").getJsonObject(0)
            .getJsonArray("content");

        assertEquals("file", content.getJsonObject(0).getString("type"));
    }

    // =================================================================================================================
    // Reasoning effort
    // =================================================================================================================

    /**
     * OpenRouter translates the effort for whichever model it routes to, so the caller's own level is passed on rather than folded into another.
     */
    @Test
    void buildChatPayload_reasoningEffort_isPassedOnUnderANestedObject() {
        var options = ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.LOW).build();

        var payload = handler.buildChatPayload(newService(), ChatInput.newBuilder().message("Hello").build(), options, false);

        assertEquals("low", payload.getJsonObject("reasoning").getString("effort"));
    }

    /**
     * A routed model may mandate reasoning and reject being told not to, so an unstated effort leaves the object off entirely.
     */
    @Test
    void buildChatPayload_effortLeftToTheModel_statesNothing() {
        var options = ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.AUTO).build();

        assertFalse(handler.buildChatPayload(newService(), ChatInput.newBuilder().message("Hello").build(), options, false).containsKey("reasoning"));
    }

    /**
     * A routed reasoning model may answer with no content at all and the answer itself among its reasoning, which is the last place to look.
     */
    @Test
    void getChatResponseContentPaths_fallBackToTheReasoningOfTheAnswer() {
        assertEquals("choices[0].message.reasoning", handler.getChatResponseContentPaths().get(handler.getChatResponseContentPaths().size() - 1));
    }

    private static byte[] mp4() {
        var mp4 = new byte[32];
        System.arraycopy("ftyp".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, mp4, 4, 4);
        System.arraycopy("isom".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, mp4, 8, 4);
        return mp4;
    }

    private static byte[] pdf() {
        return "%PDF-1.4\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    /**
     * A model which cannot think is asked for none, and the effort is then left off the payload entirely.
     */
    @Test
    void buildChatPayload_onAServiceWhichCannotThink_statesNoReasoning() {
        var service = mock(AIService.class);
        when(service.getModelName()).thenReturn("deepseek/deepseek-v4-pro");
        var options = ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.HIGH).build();

        assertFalse(handler.buildChatPayload(service, ChatInput.newBuilder().message("Hello").build(), options, false).containsKey("reasoning"));
    }

}
