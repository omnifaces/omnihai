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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIModality.AUDIO_ANALYSIS;
import static org.omnifaces.ai.AIModality.AUDIO_GENERATION;
import static org.omnifaces.ai.AIModality.IMAGE_GENERATION;
import static org.omnifaces.ai.AIModality.VIDEO_GENERATION;
import static org.omnifaces.ai.AIProvider.HUGGINGFACE;
import static org.omnifaces.ai.AIProvider.MISTRAL;
import static org.omnifaces.ai.AIProvider.OPENAI;
import static org.omnifaces.ai.AIProvider.OPENROUTER;
import static org.omnifaces.ai.AIProvider.XAI;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.ModerationOptions.Category;
import org.omnifaces.ai.service.LoopbackHttpServer.Answer;

/**
 * The endpoints a provider serves beside the chat one, and the model listing an aggregator publishes: which path each one addresses and what it makes of the
 * answer. The provider is an HTTP server on the loopback interface.
 */
class ProviderEndpointsLoopbackTest {

    private static final String WAV = "RIFF....WAVEfmt ";

    private LoopbackHttpServer server;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void startServer() {
        server = LoopbackHttpServer.start();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    // =================================================================================================================
    // OpenAI, which serves moderation and transcription on endpoints of their own
    // =================================================================================================================

    @Test
    void openAI_moderation_addressesTheModerationEndpoint() {
        server.answer(Answer.ofJson("{\"results\":[{\"category_scores\":{\"hate\":0.8}}]}"));
        var options = ModerationOptions.newBuilder().categories(Category.HATE).threshold(0.5).build();

        var result = newService(OPENAI, "gpt-4o").moderateContentAsync("I hate you.", options).join();

        assertTrue(result.isFlagged());
        assertEquals("/v1/moderations", server.lastRequest().path());
    }

    /**
     * A category which the moderation endpoint does not know is judged by the chat model instead, as the endpoint would silently drop it.
     */
    @Test
    void openAI_moderationOfACategoryTheEndpointDoesNotKnow_asksTheChatModel() {
        server.answer(Answer.ofJson("{\"choices\":[{\"message\":{\"content\":\"{\\\"scores\\\":{\\\"gambling\\\":0.1}}\"}}]}"));
        var options = ModerationOptions.newBuilder().addCategories("gambling").build();

        var result = newService(OPENAI, "gpt-4o").moderateContentAsync("Place your bets.", options).join();

        assertFalse(result.isFlagged());
        assertEquals("/v1/responses", server.lastRequest().path());
    }

    @Test
    void openAI_transcription_addressesTheTranscriptionEndpoint() throws IOException {
        server.answer(Answer.ofJson("{\"text\":\"Hello there.\"}"));
        var service = newService(OPENAI, "gpt-4o-transcribe");

        assertEquals("Hello there.", service.transcribeAsync(WAV.getBytes(UTF_8)).join());
        assertEquals("/v1/audio/transcriptions", server.lastRequest().path());

        assertEquals("Hello there.", service.transcribeAsync(writeTempFile("a.wav")).join());
        assertTrue(server.lastRequest().bodyAsString().contains("name=\"model\""), server.lastRequest().bodyAsString());
    }

    /**
     * A provider which serves no transcription endpoint of its own asks its chat model instead, whatever the shape of the audio.
     */
    @Test
    void providerWithoutATranscriptionEndpoint_asksTheChatModel() throws IOException {
        server.answer(Answer.ofJson("{\"choices\":[{\"message\":{\"content\":\"Hello there.\"}}]}"));
        var service = newService(XAI, "grok-4");

        assertEquals("Hello there.", service.transcribeAsync(WAV.getBytes(UTF_8)).join());
        assertEquals("/v1/responses", server.lastRequest().path());

        assertEquals("Hello there.", service.transcribeAsync(writeTempFile("b.wav")).join());
    }

    // =================================================================================================================
    // Hugging Face, which routes transcription to the inference provider rather than to an OpenAI compatible endpoint
    // =================================================================================================================

    @Test
    void huggingFace_transcription_addressesTheRoutedInferenceEndpoint() throws IOException {
        server.answer(Answer.ofJson("{\"text\":\"Hello there.\"}"));
        var service = newService(HUGGINGFACE, "openai/whisper-large-v3");

        assertEquals("Hello there.", service.transcribeAsync(WAV.getBytes(UTF_8)).join());
        assertEquals("/hf-inference/models/openai/whisper-large-v3", server.lastRequest().path());

        assertEquals("Hello there.", service.transcribeAsync(writeTempFile("a.wav")).join());
    }

    // =================================================================================================================
    // Mistral, which references an uploaded document by a signed URL rather than by its id
    // =================================================================================================================

    @Test
    void mistral_signedUrl_addressesTheUrlOfTheUploadedFile() {
        server.answer(Answer.ofJson("{\"url\":\"https://example.org/signed\"}"));

        var service = (MistralAIService) newService(MISTRAL, "mistral-medium-3-5");

        assertEquals("https://example.org/signed", service.getSignedUrl("file-1"));
        assertEquals("/v1/files/file-1/url", server.lastRequest().path());
    }

    // =================================================================================================================
    // The model listing an aggregator publishes
    // =================================================================================================================

    /**
     * A listing which states the modalities per model is what they are read from, rather than the model name. A model stated twice keeps the first entry, as an
     * aggregator enumerates the same model once per listing it appears in.
     */
    @Test
    void openRouter_servedListing_isWhatTheModalitiesAreReadFrom() {
        server.answer(Answer.ofJson("""
            {"data": [
                {"id": "acme/painter", "architecture": {"input_modalities": ["text"], "output_modalities": ["text", "image"]}},
                {"id": "acme/painter", "architecture": {"input_modalities": ["text"], "output_modalities": ["text"]}},
                {"id": "acme/talker", "architecture": {"input_modalities": ["text", "audio"], "output_modalities": ["text"]}}
            ]}
            """));

        assertTrue(newService(OPENROUTER, "acme/painter").supportsModality(IMAGE_GENERATION), "the name states nothing, so the listing must");
        assertTrue(newService(OPENROUTER, "acme/talker").supportsModality(AUDIO_ANALYSIS));
        assertFalse(newService(OPENROUTER, "acme/talker").supportsModality(IMAGE_GENERATION));
    }

    @Test
    void huggingFace_servedListing_isWhatTheModalitiesAreReadFrom() {
        server.answer(Answer.ofJson("""
            {"data": [{"id": "acme/listener", "architecture": {"input_modalities": ["text", "audio"], "output_modalities": ["text"]}}]}
            """));

        assertTrue(newService(HUGGINGFACE, "acme/listener").supportsModality(AUDIO_ANALYSIS), "the name states nothing, so the listing must");
    }

    /**
     * A listing which claims the model produces audio or video is not believed, as Hugging Face is wired with neither an audio nor a video handler, so the
     * generation the listing promises has no call behind it.
     */
    @Test
    void huggingFace_listingClaimingGeneration_isNotBelieved() {
        server.answer(Answer.ofJson("""
            {"data": [{"id": "acme/speaker", "architecture": {"input_modalities": ["text"], "output_modalities": ["text", "audio", "video"]}}]}
            """));
        var service = newService(HUGGINGFACE, "acme/speaker");

        assertFalse(service.supportsModality(AUDIO_GENERATION));
        assertFalse(service.supportsModality(VIDEO_GENERATION));
    }

    private Path writeTempFile(String name) throws IOException {
        return Files.writeString(tempDir.resolve(name), WAV);
    }

    private BaseAIService newService(AIProvider provider, String model) {
        return (BaseAIService) AIConfig.of(provider, "test-api-key").withModel(model).withEndpoint(server.endpoint()).createService();
    }

}
