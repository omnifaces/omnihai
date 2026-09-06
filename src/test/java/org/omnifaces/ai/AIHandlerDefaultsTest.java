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
package org.omnifaces.ai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omnifaces.ai.modality.DefaultAIAudioHandler;

/**
 * A handler serves the operations its AI provider offers and inherits the rest. An operation the provider does not offer says which method to implement rather
 * than failing somewhere further down, and an operation which needs no translation passes its argument through untouched.
 */
class AIHandlerDefaultsTest {

    private static final byte[] CONTENT = { 1, 2, 3 };

    // =================================================================================================================
    // Text
    // =================================================================================================================

    @Test
    void textHandler_unimplementedOperations_nameTheMethodToImplement() {
        var handler = mock(AITextHandler.class, CALLS_REAL_METHODS);

        assertNamesTheMethod("buildChatPayload", () -> handler.buildChatPayload(null, null, null, false));
        assertNamesTheMethod("processChatStreamEvent", () -> handler.processChatStreamEvent(null, null, null, null));
        assertNamesTheMethod("parseFileResponse", () -> handler.parseFileResponse(null));
    }

    /**
     * A provider which reports no usage leaves it unstated rather than reporting zero, which would read as a call that cost nothing.
     */
    @Test
    void textHandler_parseChatUsage_isUnstatedByDefault() {
        assertNull(mock(AITextHandler.class, CALLS_REAL_METHODS).parseChatUsage(null));
    }

    // =================================================================================================================
    // Image
    // =================================================================================================================

    @Test
    void imageHandler_unimplementedOperations_nameTheMethodToImplement() {
        var handler = mock(AIImageHandler.class, CALLS_REAL_METHODS);

        assertNamesTheMethod("buildGenerateImagePayload", () -> handler.buildGenerateImagePayload(null, null, null));
        assertNamesTheMethod("parseImageContent", () -> handler.parseImageContent(null));
    }

    // =================================================================================================================
    // Audio
    // =================================================================================================================

    @Test
    void audioHandler_unimplementedOperations_nameTheMethodToImplement() {
        var handler = mock(AIAudioHandler.class, CALLS_REAL_METHODS);

        assertNamesTheMethod("buildTranscribePayload", () -> handler.buildTranscribePayload(null));
        assertNamesTheMethod("parseTranscribeResponse", () -> handler.parseTranscribeResponse(null));
        assertNamesTheMethod("buildGenerateAudioPayload", () -> handler.buildGenerateAudioPayload(null, null, null));
    }

    /**
     * A provider taking the audio as it stands needs no conversion, so the content travels through untouched.
     */
    @Test
    void audioHandler_contentOperations_passTheirArgumentThrough() {
        var handler = mock(AIAudioHandler.class, CALLS_REAL_METHODS);

        var responseBody = new ByteArrayInputStream(CONTENT);

        assertArrayEquals(CONTENT, handler.buildTranscribeContent(CONTENT));
        assertSame(responseBody, handler.parseAudioContent(responseBody));
    }

    // =================================================================================================================
    // Video
    // =================================================================================================================

    @Test
    void videoHandler_unimplementedOperations_nameTheMethodToImplement() {
        var handler = mock(AIVideoHandler.class, CALLS_REAL_METHODS);

        assertNamesTheMethod("buildGenerateVideoPayload", () -> handler.buildGenerateVideoPayload(null, null, null));
        assertNamesTheMethod("parseSubmittedVideo", () -> handler.parseSubmittedVideo(null));
        assertNamesTheMethod("parseVideoGeneration", () -> handler.parseVideoGeneration(null, null));
    }

    private static void assertNamesTheMethod(String method, Runnable operation) {
        var exception = assertThrows(UnsupportedOperationException.class, operation::run);
        assertTrue(exception.getMessage().contains(method), exception.getMessage());
    }

    /**
     * A provider whose transcribe endpoint takes the audio as it is needs no conversion, so it hands back the very file it was given rather than a copy.
     */
    @Test
    void buildTranscribeContent_handlerWhichConvertsNothing_answersTheSourceItself(@TempDir Path tempDir) throws IOException {
        var audio = Files.writeString(tempDir.resolve("a.wav"), "the audio");

        assertSame(audio, new DefaultAIAudioHandler().buildTranscribeContent(audio));
    }

}
