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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.modality.DefaultAIAudioHandler;
import org.omnifaces.ai.modality.DefaultAIImageHandler;
import org.omnifaces.ai.modality.DefaultAITextHandler;
import org.omnifaces.ai.modality.DefaultAIVideoHandler;

/**
 * A custom provider names a handler per modality it serves, and leaves the modalities it does not serve unstated rather than pointing them at a handler which
 * cannot serve them.
 */
class AIStrategyTest {

    @Test
    void of_textHandlerAlone_leavesTheOtherModalitiesUnstated() {
        var strategy = AIStrategy.of(DefaultAITextHandler.class);

        assertEquals(DefaultAITextHandler.class, strategy.textHandler());
        assertNull(strategy.imageHandler());
        assertNull(strategy.audioHandler());
        assertNull(strategy.videoHandler());
    }

    @Test
    void of_textAndImageHandler_leavesAudioAndVideoUnstated() {
        var strategy = AIStrategy.of(DefaultAITextHandler.class, DefaultAIImageHandler.class);

        assertEquals(DefaultAIImageHandler.class, strategy.imageHandler());
        assertNull(strategy.audioHandler());
        assertNull(strategy.videoHandler());
    }

    @Test
    void of_textImageAndAudioHandler_leavesVideoUnstated() {
        var strategy = AIStrategy.of(DefaultAITextHandler.class, DefaultAIImageHandler.class, DefaultAIAudioHandler.class);

        assertEquals(DefaultAIAudioHandler.class, strategy.audioHandler());
        assertNull(strategy.videoHandler());
    }

    @Test
    void of_everyHandler_statesThemAll() {
        var strategy = AIStrategy.of(
            DefaultAITextHandler.class, DefaultAIImageHandler.class, DefaultAIAudioHandler.class, DefaultAIVideoHandler.class
        );

        assertEquals(DefaultAITextHandler.class, strategy.textHandler());
        assertEquals(DefaultAIImageHandler.class, strategy.imageHandler());
        assertEquals(DefaultAIAudioHandler.class, strategy.audioHandler());
        assertEquals(DefaultAIVideoHandler.class, strategy.videoHandler());
    }

    /**
     * A strategy is replaced one modality at a time, so that the handler for a single modality can be swapped without restating the others.
     */
    @Test
    void withHandler_replacesOneModalityAndKeepsTheRest() {
        var strategy = AIStrategy.of(DefaultAITextHandler.class);

        assertEquals(DefaultAITextHandler.class, strategy.withTextHandler(DefaultAITextHandler.class).textHandler());
        assertEquals(DefaultAIImageHandler.class, strategy.withImageHandler(DefaultAIImageHandler.class).imageHandler());
        assertEquals(DefaultAIAudioHandler.class, strategy.withAudioHandler(DefaultAIAudioHandler.class).audioHandler());
        assertEquals(DefaultAIVideoHandler.class, strategy.withVideoHandler(DefaultAIVideoHandler.class).videoHandler());
        assertEquals(DefaultAITextHandler.class, strategy.withVideoHandler(DefaultAIVideoHandler.class).textHandler());
    }

}
