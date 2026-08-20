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
import static org.omnifaces.ai.AIProvider.OPENAI;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.util.Locale;

import jakarta.json.Json;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.ChatOptions.ReasoningEffort;

class OpenAITextHandlerTest {

    private final OpenAITextHandler handler = new OpenAITextHandler();

    /**
     * A Responses API turn which searched the web announces each lookup in a message of its own before answering in the last one.
     */
    private static final String AGENTIC_RESPONSE = """
        {"output": [
            {"type": "reasoning", "summary": [{"type": "summary_text", "text": "The user wants the weather."}]},
            {"type": "message", "content": [{"type": "output_text", "text": "I'll check the current weather for Miami."}]},
            {"type": "web_search_call", "status": "completed"},
            {"type": "message", "content": [{"type": "output_text", "text": "Fetching detailed current conditions."}]},
            {"type": "web_search_call", "status": "completed"},
            {"type": "message", "content": [{"type": "output_text", "text": "Miami is 82F with a high of 88 and a low of 75."}]}
        ]}
        """;

    @Test
    void parseChatResponse_agenticTurn_answersWithTheLastMessage() {
        assertEquals("Miami is 82F with a high of 88 and a low of 75.", handler.parseChatResponse(parseJson(AGENTIC_RESPONSE)));
    }

    @Test
    void parseChatResponse_singleMessage_answersWithIt() {
        var responseJson = parseJson("{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"The one answer\"}]}]}");

        assertEquals("The one answer", handler.parseChatResponse(responseJson));
    }

    @Test
    void parseChatResponse_completionsApi_answersWithTheMessageContent() {
        var responseJson = parseJson("{\"choices\":[{\"message\":{\"content\":\"The completions answer\"}}]}");

        assertEquals("The completions answer", handler.parseChatResponse(responseJson));
    }

    // =================================================================================================================
    // Locale independence
    // =================================================================================================================

    @Test
    void addReasoningEffort_underADottedIlessLocale_staysAscii() {
        var service = newService();
        var payload = Json.createObjectBuilder();
        var defaultLocale = Locale.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));

            handler.addReasoningEffort(service, payload, ReasoningEffort.HIGH, false);

            assertEquals("high", payload.build().getString("reasoning_effort"), "an AI provider takes its own enum value, not the default locale's rendering");
        }
        finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    void addReasoningEffort_underADottedIlessLocale_staysAsciiOnTheResponsesApi() {
        var service = newService();
        var payload = Json.createObjectBuilder();
        var defaultLocale = Locale.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));

            handler.addReasoningEffort(service, payload, ReasoningEffort.XHIGH, true);

            assertEquals("xhigh", payload.build().getJsonObject("reasoning").getString("effort"), "XHIGH is the value a dotted-I-less locale mangles worst");
        }
        finally {
            Locale.setDefault(defaultLocale);
        }
    }

    private static AIService newService() {
        return AIConfig.of(OPENAI, "test-api-key").withModel("gpt-5.6-terra").createService();
    }

}
