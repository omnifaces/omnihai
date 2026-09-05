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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIProvider.ANTHROPIC;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIStrategy;
import org.omnifaces.ai.modality.DefaultAIAudioHandler;
import org.omnifaces.ai.modality.DefaultAIImageHandler;
import org.omnifaces.ai.modality.DefaultAITextHandler;
import org.omnifaces.ai.modality.DefaultAIVideoHandler;
import org.omnifaces.ai.model.AnalyzeVideoOptions;

/**
 * What a service refuses before it ever addresses the provider: a configuration which cannot be served, and a question which the AI could not answer anyway.
 */
class BaseAIServiceGuardsTest {

    private static final String API_KEY = "test-api-key";

    // =================================================================================================================
    // Configuration a service cannot be built from
    // =================================================================================================================

    /**
     * The provider states which service class serves it, so a service built on another provider's configuration would address the wrong API.
     */
    @Test
    void construction_configurationOfAnotherProvider_isRefused() {
        var config = AIConfig.of(ANTHROPIC, API_KEY);

        assertThrows(IllegalArgumentException.class, () -> new OpenAIService(config));
    }

    /**
     * A custom provider names a handler per modality itself, as there is no provider default to fall back to.
     */
    @Test
    void construction_customProviderWithoutAHandlerPerModality_namesTheModalityItIsMissing() {
        var config = newCustomConfig().withStrategy(AIStrategy.of(DefaultAITextHandler.class));

        var exception = assertThrows(IllegalArgumentException.class, () -> new CustomAIService(config));

        assertTrue(exception.getMessage().contains("image handler"), exception.getMessage());
    }

    @Test
    void construction_handlerWhichCannotBeInstantiated_namesTheHandlerClass() {
        var config = newCustomConfig().withStrategy(
            new AIStrategy(
                UninstantiableTextHandler.class, DefaultAIImageHandler.class, DefaultAIAudioHandler.class, DefaultAIVideoHandler.class
            )
        );

        var exception = assertThrows(IllegalStateException.class, () -> new CustomAIService(config));

        assertTrue(exception.getMessage().contains(UninstantiableTextHandler.class.getName()), exception.getMessage());
    }

    /**
     * A custom provider states no default of its own, so the model and the endpoint may equally be stated as configuration properties rather than through the
     * shorthands, which is what a container populating the whole configuration from one source does.
     */
    @Test
    void construction_modelAndEndpointStatedAsProperties_areWhatTheServiceIsBuiltOn() {
        var config = CustomAIService.newConfig().withModel(null).withEndpoint(null)
            .withProperty(AIConfig.PROPERTY_MODEL, "custom-2").withProperty(AIConfig.PROPERTY_ENDPOINT, "https://stated.example.org/v2/");

        var service = new CustomAIService(config);

        assertEquals("custom-2", service.getModelName());
        assertEquals("https://stated.example.org/v2/chat", service.resolveURI(service.getChatPath(false)).toString());
    }

    /**
     * A provider which states no default names its model and endpoint in the configuration, and says which one is missing when it does not.
     */
    @Test
    void construction_customProviderWithoutAModelOrEndpoint_namesThePropertyItIsMissing() {
        var withoutModel = CustomAIService.newConfig().withModel(null);
        var withoutEndpoint = CustomAIService.newConfig().withEndpoint(null);

        assertTrue(assertThrows(IllegalStateException.class, () -> new CustomAIService(withoutModel)).getMessage().contains("MODEL"));
        assertTrue(assertThrows(IllegalStateException.class, () -> new CustomAIService(withoutEndpoint)).getMessage().contains("ENDPOINT"));
    }

    // =================================================================================================================
    // What a service which states nothing of its own answers
    // =================================================================================================================

    @Test
    void getChatPrompt_isTheConfiguredPrompt() {
        assertEquals("You are terse.", newCustomService(newCustomConfig().withPrompt("You are terse.")).getChatPrompt());
    }

    @Test
    void getRequestHeaders_areEmptyUntilAProviderStatesItsOwn() {
        assertTrue(newCustomService(newCustomConfig()).getRequestHeaders().isEmpty());
    }

    /**
     * A provider which serves no files endpoint says so rather than addressing a path which does not exist.
     */
    @Test
    void getFilesPath_whichNoProviderStated_namesTheServiceClass() {
        var service = newCustomService(newCustomConfig());

        var exception = assertThrows(UnsupportedOperationException.class, service::getFilesPath);

        assertTrue(exception.getMessage().contains(CustomAIService.class.getSimpleName()), exception.getMessage());
    }

    /**
     * The API key travels to the configured endpoint alone, so a URI is only the same origin when scheme, host and effective port all match. A port which the
     * scheme implies counts as stated.
     */
    @Test
    void isSameOrigin_followsSchemeHostAndEffectivePort() {
        var service = newCustomService(newCustomConfig().withEndpoint("http://example.org/v1/"));

        assertTrue(service.isSameOrigin(URI.create("http://example.org:80/v1/files")));
        assertTrue(service.isSameOrigin(URI.create("http://EXAMPLE.ORG/v1/files")));
        assertFalse(service.isSameOrigin(URI.create("https://example.org/v1/files")));
        assertFalse(service.isSameOrigin(URI.create("http://other.example.org/v1/files")));
        assertFalse(service.isSameOrigin(URI.create("http://example.org:8080/v1/files")));
        assertFalse(service.isSameOrigin(URI.create("/v1/files")), "a URI without a scheme names no origin");
        assertFalse(service.isSameOrigin(URI.create("http:/v1/files")), "a URI without a host names no origin");
    }

    // =================================================================================================================
    // Questions which are refused or answered before the AI is asked
    // =================================================================================================================

    /**
     * A classification needs something to pick between, and a label repeated only takes up room in the prompt.
     */
    @Test
    void classifyAsync_fewerThanTwoDistinctLabels_isRefused() {
        var service = newCustomService(newCustomConfig());
        List<String> one = List.of("spam");
        List<String> repeated = List.of("spam", " spam ", "");

        assertThrows(IllegalArgumentException.class, () -> service.classifyAsync("text", one));
        assertThrows(IllegalArgumentException.class, () -> service.classifyAsync("text", repeated));
        assertThrows(NullPointerException.class, () -> service.classifyAsync("text", (List<String>) null));
    }

    @Test
    void analyzeVideoAsync_contentWhichIsNoVideo_namesTheTypeItGot() {
        var service = newCustomService(newCustomConfig());
        var content = "plain text".getBytes(UTF_8);

        var exception = assertThrows(IllegalArgumentException.class, () -> service.analyzeVideoAsync(content, "What happens?", AnalyzeVideoOptions.DEFAULT));

        assertTrue(exception.getMessage().contains("text/plain"), exception.getMessage());
    }

    /**
     * A job submitted earlier is picked up by its id alone, so a generation can be polled from another request than the one which started it.
     */
    @Test
    void findVideoGeneration_picksUpTheJobByItsIdAlone() {
        var generation = newCustomService(newCustomConfig()).findVideoGeneration("job-42");

        assertEquals("job-42", generation.jobId());
    }

    private static AIConfig newCustomConfig() {
        return CustomAIService.newConfig();
    }

    private static CustomAIService newCustomService(AIConfig config) {
        return new CustomAIService(config);
    }

    /** A handler which cannot be constructed, as its constructor is not reachable. */
    private static final class UninstantiableTextHandler extends DefaultAITextHandler {

        private UninstantiableTextHandler() {
            throw new AssertionError();
        }

    }

}
