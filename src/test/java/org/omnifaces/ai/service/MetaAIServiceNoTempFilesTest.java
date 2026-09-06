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
import static org.omnifaces.ai.AIProvider.META;

import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.helper.FileHelper;
import org.omnifaces.ai.service.LoopbackHttpServer.Answer;

/**
 * Meta AI converts the audio into the format its ASR endpoint accepts before it travels, normally into a temporary file which is streamed from. A runtime which
 * permits no temporary file converts in memory instead, which costs memory rather than the transcription.
 */
class MetaAIServiceNoTempFilesTest {

    private LoopbackHttpServer server;
    private MetaAIService service;

    @BeforeEach
    void startServer() {
        server = LoopbackHttpServer.start();
        service = new MetaAIService(AIConfig.of(META, "test-api-key").withModel("muse-voice-transcribe-1.0").withEndpoint(server.endpoint()));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void transcribeAsync_fromAPath_convertsInMemory() throws URISyntaxException {
        assertFalse(FileHelper.tempFilesSupported(), "this test states what happens when no temporary file can be made");
        server.answer(Answer.ofJson("{\"transcript\":\"Hello there.\"}"));

        assertEquals("Hello there.", service.transcribeAsync(audio()).join());
        assertEquals("/v1/asr/transcribe", server.lastRequest().path());
    }

    /** The fixture is read where it lies, as a runtime without a temporary directory has nowhere to copy it to. */
    private static Path audio() throws URISyntaxException {
        return Path.of(MetaAIServiceNoTempFilesTest.class.getResource("/helloworld.wav").toURI());
    }

}
