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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

/**
 * The WAV header is what makes the bare PCM which a text-to-speech model emits playable, and is shared by every handler parsing such a response, so a mistake
 * in it corrupts the audio of all of them at once.
 */
class DefaultAIAudioHandlerTest {

    private static final int PCM_CONTENT_LENGTH = 48000;
    private static final int HEADER_LENGTH = 44;

    private static ByteBuffer newWavHeader() {
        return ByteBuffer.wrap(DefaultAIAudioHandler.createWavHeader(PCM_CONTENT_LENGTH)).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static String readTag(ByteBuffer header, int offset) {
        var tag = new byte[4];
        header.position(offset).get(tag);
        return new String(tag, US_ASCII);
    }

    @Test
    void createWavHeader_hasTheChunkTags() {
        var header = newWavHeader();

        assertEquals(HEADER_LENGTH, header.capacity(), "a WAV header is 44 bytes");
        assertEquals("RIFF", readTag(header, 0));
        assertEquals("WAVE", readTag(header, 8));
        assertEquals("fmt ", readTag(header, 12));
        assertEquals("data", readTag(header, 36));
    }

    @Test
    void createWavHeader_statesTheAudioFormat() {
        var header = newWavHeader();

        assertEquals(16, header.getInt(16), "PCM format chunk is 16 bytes");
        assertEquals(1, header.getShort(20), "1 denotes uncompressed PCM");
        assertEquals(DefaultAIAudioHandler.PCM_WAV_CHANNELS, header.getShort(22));
        assertEquals(DefaultAIAudioHandler.PCM_WAV_SAMPLE_RATE, header.getInt(24));
        assertEquals(DefaultAIAudioHandler.PCM_WAV_BITS_PER_SAMPLE, header.getShort(34));
    }

    @Test
    void createWavHeader_statesTheDerivedSizes() {
        var header = newWavHeader();
        var bytesPerFrame = DefaultAIAudioHandler.PCM_WAV_CHANNELS * DefaultAIAudioHandler.PCM_WAV_BITS_PER_SAMPLE / 8;

        assertEquals(PCM_CONTENT_LENGTH + 36, header.getInt(4), "RIFF size counts everything after this field");
        assertEquals(DefaultAIAudioHandler.PCM_WAV_SAMPLE_RATE * bytesPerFrame, header.getInt(28), "byte rate");
        assertEquals(bytesPerFrame, header.getShort(32), "block align");
        assertEquals(PCM_CONTENT_LENGTH, header.getInt(40), "data size is the PCM content length");
    }

}
