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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIModality.VIDEO_GENERATION;
import static org.omnifaces.ai.AIProvider.AZURE;
import static org.omnifaces.ai.AIProvider.GOOGLE;
import static org.omnifaces.ai.AIProvider.OPENAI;
import static org.omnifaces.ai.AIProvider.OPENROUTER;
import static org.omnifaces.ai.AIProvider.XAI;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;

/**
 * Tests the paths and URIs which the video generation operations of each AI provider are addressed by, and the modality each publishes for it. These are pure
 * string builders, so they are covered here rather than only through the credential-gated video generator ITs.
 */
class BaseAIServiceVideoPathTest {

    private static final String API_KEY = "test-api-key";
    private static final String JOB_ID = "job-1";

    // =================================================================================================================
    // Submit, poll and download paths
    // =================================================================================================================

    @Test
    void openRouter_derivesEveryPathFromTheJobId() {
        var service = newService(OPENROUTER, "google/veo-3.1-lite");

        assertEquals("videos", service.getGenerateVideoPath());
        assertEquals("videos/" + JOB_ID, service.getGeneratedVideoPath(JOB_ID));
        assertEquals("videos/" + JOB_ID + "/content", service.getGeneratedVideoContentPath(JOB_ID));
    }

    @Test
    void xai_pollsOutsideThePathItSubmitsTo() {
        var service = newService(XAI, "grok-imagine-video-1.5");

        assertEquals("videos/generations", service.getGenerateVideoPath());
        assertEquals("videos/" + JOB_ID, service.getGeneratedVideoPath(JOB_ID), "xAI submits to videos/generations but polls at videos/{id}");
    }

    @Test
    void azure_addsTheApiVersionAndKeysTheDownloadByTheGeneration() {
        var service = newService(AZURE, "sora-2");

        assertEquals("video/generations/jobs?api-version=preview", service.getGenerateVideoPath());
        assertEquals("video/generations/jobs/" + JOB_ID + "?api-version=preview", service.getGeneratedVideoPath(JOB_ID));
        assertEquals("video/generations/gen-2/content/video?api-version=preview", AzureAIService.getVideoGenerationContentPath("gen-2"));
    }

    @Test
    void google_pollsTheOperationTheJobIdNames() {
        var service = newService(GOOGLE, "veo-3.1-generate-preview");

        assertEquals("predictLongRunning", service.getGenerateVideoPath());
        assertEquals(
            "models/veo-3.1-generate-preview/operations/abc", service.getGeneratedVideoPath("models/veo-3.1-generate-preview/operations/abc"),
            "the job id is the operation name, so a revived job may not append it to the submit path"
        );
    }

    // =================================================================================================================
    // URI resolution
    // =================================================================================================================

    @Test
    void google_resolvesTheSubmitPathBelowTheModel() {
        var uri = newService(GOOGLE, "veo-3.1-generate-preview").resolveURI("predictLongRunning");

        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/veo-3.1-generate-preview:predictLongRunning?key=" + API_KEY, uri.toString());
    }

    @Test
    void google_resolvesAnOperationPathVerbatim() {
        var uri = newService(GOOGLE, "veo-3.1-generate-preview").resolveURI("models/veo-3.1-generate-preview/operations/abc");

        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/veo-3.1-generate-preview/operations/abc?key=" + API_KEY, uri.toString());
    }

    @Test
    void google_withholdsTheApiKeyFromAForeignUri() {
        var uri = newService(GOOGLE, "veo-3.1-generate-preview").resolveURI("https://evil.example/video.mp4");

        assertEquals("https://evil.example/video.mp4", uri.toString());
        assertFalse(uri.toString().contains(API_KEY), "a URI the AI provider names elsewhere may never carry the API key");
    }

    @Test
    void azure_resolvesVideoPathsOutsideTheDeployment() {
        var service = newService(AZURE, "sora-2");

        assertTrue(service.resolveURI(service.getGenerateVideoPath()).toString().endsWith("/openai/v1/video/generations/jobs?api-version=preview"));
        assertFalse(service.resolveURI(service.getGenerateVideoPath()).toString().contains("deployments"), "the video endpoints are not below a deployment");
    }

    // =================================================================================================================
    // Same origin
    // =================================================================================================================

    @Test
    void sameOrigin_acceptsOnlyTheExactOrigin() {
        var service = newService(XAI, "grok-imagine-video-1.5");

        assertTrue(service.isSameOrigin(URI.create("https://api.x.ai/v1/videos/1")));
        assertTrue(service.isSameOrigin(URI.create("https://API.X.AI/v1/videos/1")), "a host differs only by case");
        assertTrue(service.isSameOrigin(URI.create("https://api.x.ai:443/v1/videos/1")), "the default port is the same port");
        assertFalse(service.isSameOrigin(URI.create("https://vidgen.x.ai/video.mp4")), "another host is another origin");
        assertFalse(service.isSameOrigin(URI.create("http://api.x.ai/v1/videos/1")), "cleartext is another origin");
        assertFalse(service.isSameOrigin(URI.create("https://api.x.ai:8443/v1/videos/1")), "another port is another origin");
        assertFalse(service.isSameOrigin(URI.create("/relative/path")), "a URI without a host is not the endpoint");
    }

    // =================================================================================================================
    // Modality
    // =================================================================================================================

    @Test
    void videoGeneration_isPublishedByTheVideoModelsOnly() {
        assertTrue(newService(GOOGLE, "veo-3.1-generate-preview").supportsModality(VIDEO_GENERATION));
        assertFalse(newService(GOOGLE, "gemini-3.5-flash").supportsModality(VIDEO_GENERATION));
        assertTrue(newService(XAI, "grok-imagine-video-1.5").supportsModality(VIDEO_GENERATION));
        assertFalse(newService(XAI, "grok-4.5").supportsModality(VIDEO_GENERATION));
        assertTrue(newService(AZURE, "sora-2").supportsModality(VIDEO_GENERATION));
        assertFalse(newService(AZURE, "gpt-5.5").supportsModality(VIDEO_GENERATION));
    }

    @Test
    void videoGeneration_isNotPublishedByOpenAI() {
        assertFalse(
            newService(OPENAI, "sora-2").supportsModality(VIDEO_GENERATION), "OpenAI publishes no video generation, as its video API is scheduled to shut down"
        );
    }

    private static BaseAIService newService(AIProvider provider, String model) {
        var config = AIConfig.of(provider, API_KEY).withModel(model);
        return (BaseAIService) (provider == AZURE ? config.withProperty(AzureAIService.OPTION_AZURE_RESOURCE, "test-resource") : config).createService();
    }

}
