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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.omnifaces.ai.AIProvider.OPENROUTER;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.model.GenerateVideoOptions;
import org.omnifaces.ai.model.VideoGeneration.Status;

class OpenRouterAIVideoHandlerTest {

    private static final String POLLING_URL = "https://openrouter.ai/api/v1/videos/abc123";
    private static final String CONTENT_URL = "https://openrouter.ai/api/v1/videos/abc123/content?index=0";

    private final OpenRouterAIVideoHandler handler = new OpenRouterAIVideoHandler();

    @Test
    void buildGenerateVideoPayload_statesEveryConfiguredOption() {
        var options = GenerateVideoOptions.newBuilder().size("1920x1080").resolution("1080p").seconds(5).build();
        var payload = handler.buildGenerateVideoPayload(newService(), "A golden retriever on a beach", options);

        assertEquals("google/veo-3.1", payload.getString("model"));
        assertEquals("A golden retriever on a beach", payload.getString("prompt"));
        assertEquals("1920x1080", payload.getString("size"));
        assertEquals("16:9", payload.getString("aspect_ratio"), "the aspect ratio is derived from the size");
        assertEquals("1080p", payload.getString("resolution"));
        assertEquals(5, payload.getInt("duration"));
    }

    @Test
    void parseSubmittedVideo_takesThePollingUrlItNames() {
        var job = handler.parseSubmittedVideo(parseJson("{\"id\":\"abc123\",\"polling_url\":\"" + POLLING_URL + "\",\"status\":\"pending\"}"));

        assertEquals("abc123", job.id());
        assertEquals(Status.PENDING, job.status());
        assertEquals(POLLING_URL, job.pollPath(), "OpenRouter names its own poll URL");
    }

    @Test
    void parseSubmittedVideo_withoutStatus_throwsException() {
        assertThrows(AIResponseException.class, () -> handler.parseSubmittedVideo(parseJson("{\"id\":\"abc123\"}")));
    }

    @Test
    void parseVideoGeneration_whenCompleted_takesTheFirstContentUrl() {
        var job = handler.parseVideoGeneration(parseJson("{\"status\":\"completed\",\"unsigned_urls\":[\"" + CONTENT_URL + "\"]}"), "abc123");

        assertEquals(Status.COMPLETED, job.status());
        assertEquals(CONTENT_URL, job.contentPath());
    }

    @Test
    void parseVideoGeneration_whenInProgress_hasNoContentYet() {
        var job = handler.parseVideoGeneration(parseJson("{\"status\":\"in_progress\"}"), "abc123");

        assertEquals(Status.RUNNING, job.status());
        assertNull(job.contentPath());
    }

    @Test
    void parseVideoGeneration_whenCanceled_isFailed() {
        assertEquals(Status.FAILED, handler.parseVideoGeneration(parseJson("{\"status\":\"cancelled\"}"), "abc123").status());
    }

    @Test
    void parseVideoGeneration_whenExpired_isExpired() {
        assertEquals(Status.EXPIRED, handler.parseVideoGeneration(parseJson("{\"status\":\"expired\"}"), "abc123").status());
    }

    @Test
    void parseVideoGeneration_withUnknownStatus_throwsException() {
        assertThrows(AIResponseException.class, () -> handler.parseVideoGeneration(parseJson("{\"status\":\"levitating\"}"), "abc123"));
    }

    private static AIService newService() {
        return AIConfig.of(OPENROUTER, "test-api-key").withModel("google/veo-3.1").createService();
    }

}
