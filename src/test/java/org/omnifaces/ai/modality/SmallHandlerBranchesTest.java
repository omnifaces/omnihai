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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.DeliberateFailures;
import org.omnifaces.ai.exception.AIResponseException;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.Location;
import org.omnifaces.ai.model.GenerateVideoOptions;
import org.omnifaces.ai.model.VideoGeneration.Status;

/**
 * The prompts, the paths and the capability guards which each handler states for itself, and which a provider without the capability is refused by.
 */
class SmallHandlerBranchesTest {

    /**
     * Usage data which cannot be read is logged as a warning, which these tests provoke on purpose. The filter is installed once and never changed, so a test
     * running beside this one keeps every warning of its own.
     */
    @BeforeAll
    static void dropTheWarningsOfTheDeliberatelyUnreadableUsage() {
        DeliberateFailures.dropMessagesContaining(DefaultAITextHandler.class.getPackageName(), "Skipping unparseable chat usage data");
    }

    private static final jakarta.json.JsonObject EMPTY = parseJson("{}");

    // =================================================================================================================
    // Prompts and names stated per provider
    // =================================================================================================================

    @Test
    void azureText_namesItsOwnWebSearchTool() {
        assertFalse(new AzureAITextHandler().getWebSearchToolName().isEmpty());
    }

    @Test
    void defaultAudio_asksForATranscript() {
        assertTrue(new DefaultAIAudioHandler().buildTranscribePrompt().toLowerCase(Locale.ROOT).contains("transcri"));
    }

    @Test
    void defaultVideo_asksWhatIsInTheVideo() {
        assertTrue(new DefaultAIVideoHandler().buildAnalyzeVideoPrompt().toLowerCase(Locale.ROOT).contains("video"));
    }

    @Test
    void openAIImage_readsBothAnswerShapes() {
        assertEquals(2, new OpenAIImageHandler().getImageResponseContentPaths().size());
    }

    @Test
    void googleText_statesItsAnswerAndFilePaths() {
        var handler = new GoogleAITextHandler();

        assertFalse(handler.getChatResponseContentPaths().isEmpty());
        assertFalse(handler.getFileResponseIdPaths().isEmpty());
    }

    // =================================================================================================================
    // Paths a provider has to state for itself
    // =================================================================================================================

    /**
     * The base handlers leave the answer paths to the provider, so one which states none says which method to implement.
     */
    @Test
    void baseHandlers_withoutStatedPaths_nameTheMethodToImplement() {
        assertNamesTheMethod("getChatResponseContentPaths", () -> new DefaultAITextHandler().getChatResponseContentPaths());
        assertNamesTheMethod("getChatUsageInputTokensPaths", () -> new DefaultAITextHandler().getChatUsageInputTokensPaths());
        assertNamesTheMethod("getChatUsageOutputTokensPaths", () -> new DefaultAITextHandler().getChatUsageOutputTokensPaths());
        assertNamesTheMethod("getImageResponseContentPaths", () -> new DefaultAIImageHandler().getImageResponseContentPaths());
    }

    // =================================================================================================================
    // Capability guards
    // =================================================================================================================

    @Test
    void checkSupportsFileAttachments_onAServiceWhichCannot_namesTheService() {
        var service = serviceWithout();

        var exception = assertThrows(UnsupportedOperationException.class, () -> DefaultAITextHandler.checkSupportsFileAttachments(service));
        assertTrue(exception.getMessage().contains("File"), exception.getMessage());
    }

    @Test
    void checkSupportsWebSearch_onAServiceWhichCannot_namesTheService() {
        var service = serviceWithout();

        var exception = assertThrows(UnsupportedOperationException.class, () -> DefaultAITextHandler.checkSupportsWebSearch(service));
        assertTrue(exception.getMessage().contains("Web search"), exception.getMessage());
    }

    @Test
    void checkSupports_onAServiceWhichCan_passes() {
        var service = mock(AIService.class);
        when(service.supportsFileAttachments()).thenReturn(true);
        when(service.supportsWebSearch()).thenReturn(true);

        DefaultAITextHandler.checkSupportsFileAttachments(service);
        DefaultAITextHandler.checkSupportsWebSearch(service);
    }

    // =================================================================================================================
    // Locations offered to a web search
    // =================================================================================================================

    /**
     * A global search states no location at all, and a partial one states only the parts it knows.
     */
    @Test
    void buildUserLocation_globalLocation_statesNone() {
        assertTrue(OpenAITextHandler.buildUserLocation(Location.GLOBAL).isEmpty());
    }

    @Test
    void buildUserLocation_partialLocation_statesOnlyWhatItKnows() {
        var userLocation = OpenAITextHandler.buildUserLocation(new Location("US", null, "Miami")).orElseThrow();

        assertEquals("approximate", userLocation.getString("type"));
        assertEquals("US", userLocation.getString("country"));
        assertEquals("Miami", userLocation.getString("city"));
        assertFalse(userLocation.containsKey("region"));
    }

    @Test
    void buildUserLocation_regionOnly_statesTheRegion() {
        assertEquals("Florida", OpenAITextHandler.buildUserLocation(new Location(null, "Florida", null)).orElseThrow().getString("region"));
    }

    // =================================================================================================================
    // Audio and video generation options which the caller left alone
    // =================================================================================================================

    /**
     * A size implies the shape, so stating one states the aspect ratio it works out to rather than leaving the two to disagree.
     */
    @Test
    void openRouterVideo_withEveryOptionStated_carriesThemAll() {
        var options = GenerateVideoOptions.newBuilder().size("1024x1024").resolution("1080p").seconds(8).build();

        var payload = new OpenRouterAIVideoHandler().buildGenerateVideoPayload(newService(AIProvider.OPENROUTER, "sora"), "A cat", options);

        assertEquals("1:1", payload.getString("aspect_ratio"));
        assertEquals("1024x1024", payload.getString("size"));
        assertEquals("1080p", payload.getString("resolution"));
        assertEquals(8, payload.getInt("duration"));
    }

    /**
     * An option the caller left alone is not stated, so the provider applies its own rather than one chosen on its behalf.
     */
    @Test
    void openRouterVideo_withoutAnyOptionStated_leavesThemToTheProvider() {
        var payload = new OpenRouterAIVideoHandler()
            .buildGenerateVideoPayload(newService(AIProvider.OPENROUTER, "sora"), "A cat", GenerateVideoOptions.DEFAULT);

        assertFalse(payload.containsKey("size"));
        assertFalse(payload.containsKey("resolution"));
        assertFalse(payload.containsKey("duration"));
    }

    // =================================================================================================================
    // Video job status
    // =================================================================================================================

    @Test
    void openRouterVideo_parsesTheSubmittedJobAndItsProgress() {
        var handler = new OpenRouterAIVideoHandler();

        var submitted = handler.parseSubmittedVideo(parseJson("{\"id\":\"job-1\",\"status\":\"queued\",\"polling_url\":\"https://example.org/poll\"}"));
        assertEquals("job-1", submitted.id());
        assertEquals("https://example.org/poll", submitted.pollPath());

        var failed = handler.parseVideoGeneration(parseJson("{\"status\":\"failed\",\"error\":{\"message\":\"content policy\"}}"), "job-1");
        assertEquals("content policy", failed.failureReason());
    }

    // =================================================================================================================
    // Video job status words
    // =================================================================================================================

    /**
     * Every provider spells the same few states differently, so each spelling maps to the state it means and anything else is an answer nobody can act on.
     */
    @Test
    void parseVideoStatus_mapsEverySpellingOfEachState() {
        assertEquals(Status.PENDING, DefaultAIVideoHandler.parseVideoStatus("queued", EMPTY));
        assertEquals(Status.PENDING, DefaultAIVideoHandler.parseVideoStatus("not_started", EMPTY));
        assertEquals(Status.RUNNING, DefaultAIVideoHandler.parseVideoStatus("in_progress", EMPTY));
        assertEquals(Status.COMPLETED, DefaultAIVideoHandler.parseVideoStatus("succeeded", EMPTY));
        assertEquals(Status.FAILED, DefaultAIVideoHandler.parseVideoStatus("cancelled", EMPTY));
    }

    @Test
    void parseVideoStatus_statusWhichIsNotRecognized_isRejected() {
        assertThrows(AIResponseException.class, () -> DefaultAIVideoHandler.parseVideoStatus("exploded", EMPTY));
        assertThrows(AIResponseException.class, () -> DefaultAIVideoHandler.parseVideoStatus(null, EMPTY));
    }

    // =================================================================================================================
    // Prompts which are added to what the caller already stated
    // =================================================================================================================

    /**
     * A web search within a region tells the AI so, and does that on top of whatever the caller already asked for rather than in place of it.
     */
    @Test
    void appendPrompt_keepsThePromptTheCallerAlreadyStated() {
        var options = DefaultAITextHandler.appendPrompt(ChatOptions.newBuilder().systemPrompt("You are terse.").build(), "Search within Miami");

        assertTrue(options.getSystemPrompt().startsWith("You are terse."), options.getSystemPrompt());
        assertTrue(options.getSystemPrompt().endsWith("Search within Miami"), options.getSystemPrompt());
    }

    @Test
    void appendPrompt_withoutAPromptToKeep_statesTheAdditionAlone() {
        assertEquals("Search within Miami", DefaultAITextHandler.appendPrompt(ChatOptions.DEFAULT, "Search within Miami").getSystemPrompt());
    }

    // =================================================================================================================
    // Handlers which state an empty list of paths
    // =================================================================================================================

    /**
     * A handler which answers an empty list of paths states a contradiction rather than a provider quirk, so it fails on the spot instead of reading nothing.
     */
    @Test
    void parsers_withAnEmptyListOfPaths_failOnTheSpot() {
        var text = new DefaultAITextHandler() {

            @Override
            public List<String> getChatResponseContentPaths() {
                return List.of();
            }

            @Override
            public List<String> getChatUsageInputTokensPaths() {
                return List.of();
            }

            @Override
            public List<String> getFileResponseIdPaths() {
                return List.of();
            }

        };

        assertThrows(IllegalStateException.class, () -> text.parseChatResponse(EMPTY));
        assertThrows(IllegalStateException.class, () -> text.parseFileResponse(EMPTY));
        assertNull(text.parseChatUsage(EMPTY), "usage is reported as unknown rather than aborting an answer already received");
    }

    /**
     * Usage is not the answer, so a handler which cannot read it reports none rather than throwing away a response the caller is waiting for.
     */
    @Test
    void parseChatUsage_withoutStatedOutputPaths_reportsNoUsage() {
        var text = new DefaultAITextHandler() {

            @Override
            public List<String> getChatUsageInputTokensPaths() {
                return List.of("usage.prompt_tokens");
            }

            @Override
            public List<String> getChatUsageOutputTokensPaths() {
                return List.of();
            }

        };

        assertNull(text.parseChatUsage(EMPTY));
    }

    @Test
    void parseImageContent_withAnEmptyListOfPaths_failsOnTheSpot() {
        var image = new DefaultAIImageHandler() {

            @Override
            public List<String> getImageResponseContentPaths() {
                return List.of();
            }

        };

        assertThrows(IllegalStateException.class, () -> image.parseImageContent(EMPTY));
    }

    @Test
    void parseChatResponse_anthropicToolTurnWithoutStatedPaths_failsOnTheSpot() {
        var anthropic = new AnthropicAITextHandler() {

            @Override
            public List<String> getChatResponseContentPaths() {
                return List.of();
            }

        };
        var response = parseJson("{\"content\":[{\"type\":\"server_tool_use\"}]}");

        assertThrows(IllegalStateException.class, () -> anthropic.parseChatResponse(response));
    }

    // =================================================================================================================
    // Usage which the provider states only partly
    // =================================================================================================================

    /**
     * A provider may report one half of the counts and not the other, which is still usage worth recording, with the missing half stated as unknown.
     */
    @Test
    void parseChatUsage_withOnlyOneHalfStated_recordsTheHalfItGot() {
        var handler = new DefaultAITextHandler() {

            @Override
            public List<String> getChatUsageInputTokensPaths() {
                return List.of("usage.prompt_tokens");
            }

            @Override
            public List<String> getChatUsageOutputTokensPaths() {
                return List.of("usage.completion_tokens");
            }

        };

        var usage = handler.parseChatUsage(parseJson("{\"usage\":{\"completion_tokens\":20}}"));

        assertEquals(-1, usage.inputTokens());
        assertEquals(20, usage.outputTokens());
    }

    /**
     * A count which is not a number at all is not usage, and is skipped rather than allowed to abort the answer already received.
     */
    @Test
    void parseChatUsage_countWhichIsNotANumber_isSkipped() {
        var handler = new DefaultAITextHandler() {

            @Override
            public List<String> getChatUsageInputTokensPaths() {
                return List.of("usage.prompt_tokens");
            }

            @Override
            public List<String> getChatUsageOutputTokensPaths() {
                return List.of("usage.completion_tokens");
            }

        };

        assertNull(handler.parseChatUsage(parseJson("{\"usage\":{\"prompt_tokens\":\"lots\"}}")));
    }

    // =================================================================================================================
    // Video jobs which the provider states incompletely
    // =================================================================================================================

    @Test
    void openRouterVideo_answerWithoutAnIdOrStatus_saysWhichIsMissing() {
        var handler = new OpenRouterAIVideoHandler();
        var withoutId = parseJson("{\"status\":\"queued\"}");
        var withoutStatus = parseJson("{\"id\":\"job-1\"}");

        assertTrue(assertThrows(AIResponseException.class, () -> handler.parseSubmittedVideo(withoutId)).getMessage().contains("id"));
        assertTrue(assertThrows(AIResponseException.class, () -> handler.parseSubmittedVideo(withoutStatus)).getMessage().contains("status"));
        assertTrue(assertThrows(AIResponseException.class, () -> handler.parseVideoGeneration(withoutStatus, "job-1")).getMessage().contains("status"));
    }

    @Test
    void xaiVideo_answerWithoutAStatus_saysSo() {
        var handler = new XAIVideoHandler();

        assertThrows(AIResponseException.class, () -> handler.parseVideoGeneration(EMPTY, "job-1"));
    }

    private static void assertNamesTheMethod(String method, Runnable operation) {
        var exception = assertThrows(UnsupportedOperationException.class, operation::run);
        assertTrue(exception.getMessage().contains(method), exception.getMessage());
    }

    private static AIService serviceWithout() {
        var service = mock(AIService.class);
        when(service.getName()).thenReturn("StubAIService");
        return service;
    }

    private static AIService newService(AIProvider provider, String model) {
        return AIConfig.of(provider, "test-api-key").withModel(model).createService();
    }

}
