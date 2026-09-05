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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatOptions;

/**
 * Ollama carries the system prompt as the first message of the conversation rather than beside it, attaches images to the message as bare base64, and states
 * the sampling parameters under an options object of its own.
 */
class OllamaAITextHandlerTest {

    private final OllamaAITextHandler handler = new OllamaAITextHandler();

    @Test
    void buildChatPayload_namesTheModelAndCarriesTheMessage() {
        var payload = payload(ChatOptions.DEFAULT);

        assertEquals("llama3.2", payload.getString("model"));
        assertEquals("user", payload.getJsonArray("messages").getJsonObject(0).getString("role"));
        assertEquals("Hello", payload.getJsonArray("messages").getJsonObject(0).getString("content"));
    }

    /**
     * The response is read in one piece, so the request says so rather than leaving the provider to choose.
     */
    @Test
    void buildChatPayload_asksForTheWholeAnswerAtOnce() {
        assertFalse(payload(ChatOptions.DEFAULT).getBoolean("stream"));
    }

    @Test
    void buildChatPayload_withASystemPrompt_leadsTheConversationWithIt() {
        var messages = payload(ChatOptions.newBuilder().systemPrompt("You are terse.").build()).getJsonArray("messages");

        assertEquals("system", messages.getJsonObject(0).getString("role"));
        assertEquals("You are terse.", messages.getJsonObject(0).getString("content"));
        assertEquals("user", messages.getJsonObject(1).getString("role"));
    }

    @Test
    void buildChatPayload_withoutASystemPrompt_leadsWithTheUserMessage() {
        assertEquals("user", payload(ChatOptions.DEFAULT).getJsonArray("messages").getJsonObject(0).getString("role"));
    }

    @Test
    void buildChatPayload_withHistory_replaysBothRoles() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "What is 2+2?");
        options.recordMessage(Role.ASSISTANT, "4");
        var input = ChatInput.newBuilder().message("And 3+3?").build().withHistory(options.getHistory());

        var messages = handler.buildChatPayload(newService(), input, options, false).getJsonArray("messages");

        assertEquals("user", messages.getJsonObject(0).getString("role"));
        assertEquals("assistant", messages.getJsonObject(1).getString("role"));
        assertEquals("4", messages.getJsonObject(1).getString("content"));
    }

    /**
     * Ollama takes the images as bare base64 beside the message rather than as typed content blocks.
     */
    @Test
    void buildChatPayload_withAnImage_carriesItBesideTheMessage() {
        var input = ChatInput.newBuilder().message("What is this?").attach(png()).build();

        var message = handler.buildChatPayload(newService(), input, ChatOptions.DEFAULT, false).getJsonArray("messages").getJsonObject(0);

        assertEquals(1, message.getJsonArray("images").size());
        assertFalse(message.getJsonArray("images").getString(0).isEmpty());
        assertEquals("What is this?", message.getString("content"));
    }

    @Test
    void buildChatPayload_withoutAnImage_statesNone() {
        assertFalse(payload(ChatOptions.DEFAULT).getJsonArray("messages").getJsonObject(0).containsKey("images"));
    }

    @Test
    void buildChatPayload_alwaysStatesTheTemperature() {
        assertTrue(payload(ChatOptions.DEFAULT).getJsonObject("options").containsKey("temperature"));
    }

    @Test
    void buildChatPayload_withALimitAndATopP_statesBothUnderItsOwnNames() {
        var options = ChatOptions.newBuilder().maxTokens(256).topP(0.5).build();

        var payloadOptions = payload(options).getJsonObject("options");

        assertEquals(256, payloadOptions.getInt("num_predict"));
        assertEquals(0.5, payloadOptions.getJsonNumber("top_p").doubleValue());
    }

    @Test
    void buildChatPayload_withoutALimitOrATopP_statesNeither() {
        var payloadOptions = payload(ChatOptions.DEFAULT).getJsonObject("options");

        assertFalse(payloadOptions.containsKey("num_predict"));
        assertFalse(payloadOptions.containsKey("top_p"));
    }

    @Test
    void buildChatPayload_withAJsonSchema_statesItAsTheFormat() {
        var options = ChatOptions.newBuilder().jsonSchema(parseJson("{\"type\":\"object\"}")).build();

        assertEquals("object", payload(options).getJsonObject("format").getString("type"));
    }

    @Test
    void buildChatPayload_withoutAJsonSchema_statesNoFormat() {
        assertFalse(payload(ChatOptions.DEFAULT).containsKey("format"));
    }

    /**
     * Ollama answers a chat under {@code message.content} and a bare completion under {@code response}, so both are read.
     */
    @Test
    void parseChatResponse_readsBothAnswerShapes() {
        assertEquals("4", handler.parseChatResponse(parseJson("{\"message\":{\"content\":\"4\"}}")));
        assertEquals("4", handler.parseChatResponse(parseJson("{\"response\":\"4\"}")));
    }

    @Test
    void parseChatUsage_readsTheEvaluationCounts() {
        var usage = handler.parseChatUsage(parseJson("{\"prompt_eval_count\":10,\"eval_count\":20}"));

        assertEquals(10, usage.inputTokens());
        assertEquals(20, usage.outputTokens());
    }

    private JsonObject payload(ChatOptions options) {
        return handler.buildChatPayload(newService(), ChatInput.newBuilder().message("Hello").build(), options, false);
    }

    private static byte[] png() {
        try {
            var output = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "PNG", output);
            return output.toByteArray();
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static AIService newService() {
        return AIConfig.of(AIProvider.OLLAMA, null).withModel("llama3.2").createService();
    }

}
