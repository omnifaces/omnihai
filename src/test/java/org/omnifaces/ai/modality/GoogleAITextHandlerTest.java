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

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.AnalyzeVideoOptions;
import org.omnifaces.ai.model.ChatInput.Attachment;

class GoogleAITextHandlerTest {

    private final GoogleAITextHandler handler = new GoogleAITextHandler();

    private static Attachment newVideo() {
        return new Attachment(new byte[] { 1, 2, 3 }, MimeType.of("video/mp4"), "video.mp4");
    }

    @Test
    void buildVideoMetadata_absent_whenNoVideoOptions() {
        assertTrue(handler.buildVideoMetadata(newVideo()).isEmpty());
    }

    @Test
    void buildVideoMetadata_absent_whenDefaultVideoOptions() {
        assertTrue(handler.buildVideoMetadata(newVideo().withVideoOptions(AnalyzeVideoOptions.DEFAULT)).isEmpty());
    }

    @Test
    void buildVideoMetadata_containsOnlyTheOptionsWhichAreSet() {
        var video = newVideo().withVideoOptions(AnalyzeVideoOptions.newBuilder().fps(0.5).build());

        var videoMetadata = handler.buildVideoMetadata(video).orElseThrow().build();

        assertEquals(1, videoMetadata.size());
        assertEquals(0.5, videoMetadata.getJsonNumber("fps").doubleValue());
    }

    @Test
    void buildVideoMetadata_rendersWholeSecondOffsetWithoutFraction() {
        var video = newVideo().withVideoOptions(AnalyzeVideoOptions.newBuilder().startOffset(ofSeconds(30)).endOffset(ofSeconds(90)).build());

        var videoMetadata = handler.buildVideoMetadata(video).orElseThrow().build();

        assertEquals("30s", videoMetadata.getString("start_offset"));
        assertEquals("90s", videoMetadata.getString("end_offset"));
    }

    @Test
    void buildVideoMetadata_rendersSubSecondOffsetAsFraction() {
        var video = newVideo().withVideoOptions(AnalyzeVideoOptions.newBuilder().startOffset(ofMillis(1500)).build());

        var videoMetadata = handler.buildVideoMetadata(video).orElseThrow().build();

        assertEquals("1.500s", videoMetadata.getString("start_offset"));
    }

}
