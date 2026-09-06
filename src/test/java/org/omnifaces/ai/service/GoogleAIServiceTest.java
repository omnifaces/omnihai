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

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIModality.AUDIO_ANALYSIS;
import static org.omnifaces.ai.AIModality.AUDIO_GENERATION;
import static org.omnifaces.ai.AIModality.IMAGE_ANALYSIS;
import static org.omnifaces.ai.AIModality.IMAGE_GENERATION;
import static org.omnifaces.ai.AIModality.VIDEO_ANALYSIS;
import static org.omnifaces.ai.AIModality.VIDEO_GENERATION;
import static org.omnifaces.ai.AIProvider.GOOGLE;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.model.ChatInput.Attachment;

class GoogleAIServiceTest {

    private static final long MEGABYTE = 1024L * 1024L;

    private static final String API_KEY = "test-api-key";
    private static final String MODEL = "gemini-3.7-flash";
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/";

    @TempDir
    private Path tempDir;

    /** Returns an attachment of the given size, backed by a sparse file so that a large size costs neither heap nor disk. */
    private Attachment newAttachment(long size) {
        var file = tempDir.resolve(size + ".mp4");

        try (var sparseFile = new RandomAccessFile(file.toFile(), "rw")) {
            sparseFile.setLength(size);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new Attachment(file);
    }

    @Test
    void maxProcessingTime_smallFile_isFlooredAtOneMinute() {
        assertEquals(ofMinutes(1), GoogleAIService.maxProcessingTime(newAttachment(1024)));
        assertEquals(ofMinutes(1), GoogleAIService.maxProcessingTime(newAttachment(29 * MEGABYTE)));
    }

    @Test
    void maxProcessingTime_mediumFile_scalesWithSize() {
        assertEquals(ofSeconds(200), GoogleAIService.maxProcessingTime(newAttachment(100 * MEGABYTE)));
    }

    @Test
    void maxProcessingTime_largeFile_isCappedAtFifteenMinutes() {
        assertEquals(ofMinutes(15), GoogleAIService.maxProcessingTime(newAttachment(500 * MEGABYTE)));
    }

    // =================================================================================================================
    // Awaiting an uploaded file
    // =================================================================================================================

    /**
     * An upload is usable only once the provider states it active; anything else means waiting, and a state which is not one of the documented ones is an
     * answer nobody can act on.
     */
    @Test
    void isStillProcessing_followsTheStateTheProviderStates() {
        assertFalse(GoogleAIService.isStillProcessing("files/a", parseJson("{\"state\":\"ACTIVE\"}")));
        assertTrue(GoogleAIService.isStillProcessing("files/a", parseJson("{\"state\":\"PROCESSING\"}")));
        assertTrue(GoogleAIService.isStillProcessing("files/a", parseJson("{\"state\":\"STATE_UNSPECIFIED\"}")));
    }

    /**
     * A file which states no state at all is taken as active, as that is what the provider answers once it stops reporting progress.
     */
    @Test
    void isStillProcessing_fileWithoutAState_isTakenAsActive() {
        assertFalse(GoogleAIService.isStillProcessing("files/a", parseJson("{}")));
    }

    /**
     * A poll which answers nothing says nothing about the file, so it is polled again rather than taken as ready.
     */
    @Test
    void isStillProcessing_withoutAnyAnswer_keepsPolling() {
        assertTrue(GoogleAIService.isStillProcessing("files/a", null));
    }

    @Test
    void isStillProcessing_failedFile_reportsWhyItFailed() {
        var file = parseJson("{\"state\":\"FAILED\",\"error\":{\"message\":\"unsupported codec\"}}");

        var exception = assertThrows(AIException.class, () -> GoogleAIService.isStillProcessing("files/a", file));
        assertTrue(exception.getMessage().contains("unsupported codec"), exception.getMessage());
    }

    @Test
    void isStillProcessing_unknownState_namesIt() {
        var file = parseJson("{\"state\":\"EXPLODED\"}");

        var exception = assertThrows(AIException.class, () -> GoogleAIService.isStillProcessing("files/a", file));
        assertTrue(exception.getMessage().contains("EXPLODED"), exception.getMessage());
    }

    /**
     * Each poll waits longer than the one before it, up to a ceiling, so a slow upload is not hammered.
     */
    @Test
    void nextPollInterval_growsUpToACeiling() {
        var first = GoogleAIService.nextPollInterval(Duration.ofMillis(100));

        assertTrue(first.compareTo(Duration.ofMillis(100)) > 0, first.toString());
        assertEquals(GoogleAIService.nextPollInterval(Duration.ofHours(1)), GoogleAIService.nextPollInterval(Duration.ofHours(2)));
    }

    // =================================================================================================================
    // Capabilities
    // =================================================================================================================

    @Test
    void supportsModality_imageAnalysis_isServedWhateverTheModel() {
        assertTrue(newService("gemini-1.0-pro").supportsModality(IMAGE_ANALYSIS));
    }

    @Test
    void supportsModality_imageGeneration_followsTheVersionOrTheImageSuffix() {
        assertFalse(newService("gemini-1.5-flash").supportsModality(IMAGE_GENERATION));
        assertTrue(newService("gemini-2.0-flash").supportsModality(IMAGE_GENERATION));
        assertTrue(newService("imagen-4.0-generate-image").supportsModality(IMAGE_GENERATION));
    }

    @Test
    void supportsModality_audioAndVideoAnalysis_areGatedAtGemini15() {
        assertFalse(newService("gemini-1.0-pro").supportsModality(AUDIO_ANALYSIS));
        assertFalse(newService("gemini-1.0-pro").supportsModality(VIDEO_ANALYSIS));
        assertTrue(newService("gemini-1.5-flash").supportsModality(AUDIO_ANALYSIS));
        assertTrue(newService("gemini-1.5-flash").supportsModality(VIDEO_ANALYSIS));
    }

    @Test
    void supportsModality_audioGeneration_followsTheVersionOrTheTtsSuffix() {
        assertFalse(newService("gemini-2.0-flash").supportsModality(AUDIO_GENERATION));
        assertTrue(newService("gemini-2.5-flash").supportsModality(AUDIO_GENERATION));
        assertTrue(newService("gemini-2.0-flash-tts").supportsModality(AUDIO_GENERATION));
    }

    @Test
    void supportsModality_videoGeneration_isTheVeoModelsAlone() {
        assertFalse(newService("gemini-3.7-flash").supportsModality(VIDEO_GENERATION));
        assertTrue(newService("veo-3.0-generate-001").supportsModality(VIDEO_GENERATION));
    }

    @Test
    void capabilities_whichAreApiBoundRatherThanVersionBound_areServedWhateverTheModel() {
        var service = newService("gemini-1.0-pro");

        assertTrue(service.supportsStreaming());
        assertTrue(service.supportsFileAttachments());
        assertTrue(service.supportsFileAttachmentsInHistory());
        assertTrue(service.supportsWebSearch());
    }

    // =================================================================================================================
    // Addressing the endpoint
    // =================================================================================================================

    /**
     * Google AI takes the API key in the query string rather than in a header, and addresses the model in the path rather than in the payload.
     */
    @Test
    void resolveURI_chatPath_addressesTheModelAndCarriesTheApiKey() {
        var service = newService(MODEL);

        assertEquals(BASE + "models/" + MODEL + ":generateContent?key=" + API_KEY, service.resolveURI(service.getChatPath(false)).toString());
    }

    /**
     * A path which carries a query string of its own keeps it beside the API key rather than losing it.
     */
    @Test
    void resolveURI_pathWithItsOwnQueryString_keepsItBesideTheApiKey() {
        var service = newService(MODEL);

        assertEquals(
            BASE + "models/" + MODEL + ":streamGenerateContent?key=" + API_KEY + "&alt=sse",
            service.resolveURI(service.getChatPath(true)).toString()
        );
    }

    /**
     * Uploading addresses the upload host rather than the API host, which is a sibling of the configured endpoint.
     */
    @Test
    void resolveURI_filesPath_addressesTheUploadEndpoint() {
        assertEquals(
            "https://generativelanguage.googleapis.com/upload/v1beta/files?key=" + API_KEY, newService(MODEL).resolveURI("files").toString()
        );
    }

    @Test
    void resolveURI_uploadedFilePath_isAddressedAsItIs() {
        assertEquals(BASE + "files/abc123?key=" + API_KEY, newService(MODEL).resolveURI("files/abc123").toString());
    }

    /**
     * A long-running operation is polled on the path the provider handed out, which the model is not part of.
     */
    @Test
    void resolveURI_operationPath_isAddressedAsItIsBesideTheApiKey() {
        var service = newService(MODEL);

        assertEquals(BASE + "operations/abc123?key=" + API_KEY, service.resolveURI("operations/abc123").toString());
        assertEquals(BASE + "models/veo-3.0/operations/abc123?key=" + API_KEY, service.resolveURI("models/veo-3.0/operations/abc123").toString());
        assertEquals(BASE + "operations/abc123?alt=json&key=" + API_KEY, service.resolveURI("operations/abc123?alt=json").toString());
    }

    /**
     * Generated content is served from a host of Google's own, which must never be handed the API key.
     */
    @Test
    void resolveURI_absoluteUriOfAnotherOrigin_carriesNoApiKey() {
        var uri = newService(MODEL).resolveURI("https://storage.example.org/operations/video.mp4");

        assertEquals("https://storage.example.org/operations/video.mp4", uri.toString());
    }

    @Test
    void resolveURI_absoluteUriOfTheEndpointItself_carriesTheApiKey() {
        assertEquals(BASE + "operations/abc123?key=" + API_KEY, newService(MODEL).resolveURI(BASE + "operations/abc123").toString());
    }

    @Test
    void getChatPath_streaming_asksForServerSentEvents() {
        var service = newService(MODEL);

        assertEquals("generateContent", service.getChatPath(false));
        assertEquals("streamGenerateContent?alt=sse", service.getChatPath(true));
    }

    private static GoogleAIService newService(String model) {
        return new GoogleAIService(AIConfig.of(GOOGLE, API_KEY).withModel(model));
    }

}
