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

import static java.time.Duration.ofDays;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIModality.AUDIO_ANALYSIS;
import static org.omnifaces.ai.AIModality.AUDIO_GENERATION;
import static org.omnifaces.ai.AIModality.IMAGE_ANALYSIS;
import static org.omnifaces.ai.AIModality.IMAGE_GENERATION;
import static org.omnifaces.ai.AIModality.VIDEO_ANALYSIS;
import static org.omnifaces.ai.AIModality.VIDEO_GENERATION;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.service.ModelModalitiesRegistry.CachedModels;

class ModelModalitiesRegistryTest {

    private static final String LISTING = """
        {"data": [
            {"id": "google/gemini-3.7-flash", "architecture": {"input_modalities": ["text", "image", "video"], "output_modalities": ["text"]}},
            {"id": "openai/sora-2", "architecture": {"input_modalities": ["text"], "output_modalities": ["video"]}},
            {"id": "deepseek/deepseek-v4-pro", "architecture": {"input_modalities": ["text"], "output_modalities": ["text"]}},
            {"id": "some/unpublished-model"}
        ]}
        """;

    @Test
    void parseModels_readsModalitiesPerModel() {
        var models = ModelModalitiesRegistry.parseModels(parseJson(LISTING));

        assertEquals(3, models.size());
        assertTrue(models.get("google/gemini-3.7-flash").contains(VIDEO_ANALYSIS));
        assertTrue(models.get("openai/sora-2").contains(VIDEO_GENERATION));
    }

    @Test
    void parseModels_skipsModelWithoutArchitecture() {
        assertFalse(ModelModalitiesRegistry.parseModels(parseJson(LISTING)).containsKey("some/unpublished-model"));
    }

    @Test
    void parseModels_mapsInputModalitiesToAnalysis() {
        var modalities = ModelModalitiesRegistry.parseModels(parseJson(LISTING)).get("google/gemini-3.7-flash");

        assertTrue(modalities.contains(IMAGE_ANALYSIS));
        assertTrue(modalities.contains(VIDEO_ANALYSIS));
        assertFalse(modalities.contains(AUDIO_ANALYSIS));
        assertFalse(modalities.contains(IMAGE_GENERATION));
    }

    @Test
    void parseModels_mapsOutputModalitiesToGeneration() {
        var modalities = ModelModalitiesRegistry.parseModels(parseJson(LISTING)).get("openai/sora-2");

        assertTrue(modalities.contains(VIDEO_GENERATION));
        assertFalse(modalities.contains(IMAGE_GENERATION));
        assertFalse(modalities.contains(AUDIO_GENERATION));
        assertFalse(modalities.contains(VIDEO_ANALYSIS));
    }

    @Test
    void parseModels_textOnlyModelSupportsNoModality() {
        var modalities = ModelModalitiesRegistry.parseModels(parseJson(LISTING)).get("deepseek/deepseek-v4-pro");

        for (var modality : AIModality.values()) {
            assertFalse(modalities.contains(modality), modality + " may not be supported");
        }
    }

    // =================================================================================================================
    // Cache lifetime
    // =================================================================================================================

    @Test
    void maxAge_successfulListing_isCachedForADay() {
        assertEquals(ofDays(1), newCachedModels(0).maxAge());
    }

    @Test
    void maxAge_failedListing_backsOffPerConsecutiveFailure() {
        assertEquals(ofMinutes(1), newCachedModels(1).maxAge());
        assertEquals(ofMinutes(2), newCachedModels(2).maxAge());
        assertEquals(ofMinutes(4), newCachedModels(3).maxAge());
        assertEquals(ofMinutes(32), newCachedModels(6).maxAge());
    }

    @Test
    void maxAge_failedListing_isCappedAtAnHour() {
        assertEquals(ofHours(1), newCachedModels(7).maxAge());
        assertEquals(ofHours(1), newCachedModels(100).maxAge());
    }

    private static CachedModels newCachedModels(int consecutiveFailures) {
        return new CachedModels(emptyMap(), 0L, consecutiveFailures);
    }

    // =================================================================================================================
    // findModelModalities - across several listings
    // =================================================================================================================

    @Test
    void findModelModalities_unionsWhatEveryListingStates() {
        var byName = Map.of("google/veo-3.1", Set.of(IMAGE_ANALYSIS));
        var byOutput = Map.of("google/veo-3.1", Set.of(VIDEO_GENERATION));

        var modalities = ModelModalitiesRegistry.findModelModalities(List.of(byName, byOutput), "google/veo-3.1");

        assertEquals(Set.of(IMAGE_ANALYSIS, VIDEO_GENERATION), modalities.orElseThrow());
    }

    @Test
    void findModelModalities_unionsWithAListingStatingNone() {
        var withoutModalities = Map.of("google/veo-3.1", Set.<AIModality>of());
        var withVideo = Map.of("google/veo-3.1", Set.of(VIDEO_GENERATION));

        var modalities = ModelModalitiesRegistry.findModelModalities(List.of(withoutModalities, withVideo), "google/veo-3.1");

        assertEquals(Set.of(VIDEO_GENERATION), modalities.orElseThrow(), "a listing stating no modality may not defeat one which states some");
    }

    @Test
    void findModelModalities_takesTheModelFromWhicheverListingKnowsIt() {
        var byName = Map.of("deepseek/deepseek-v4-pro", Set.of(IMAGE_ANALYSIS));
        var byOutput = Map.of("google/veo-3.1", Set.of(VIDEO_GENERATION));

        var modalities = ModelModalitiesRegistry.findModelModalities(List.of(byName, byOutput), "google/veo-3.1");

        assertEquals(Set.of(VIDEO_GENERATION), modalities.orElseThrow());
    }

    @Test
    void findModelModalities_fallsBackToTheBaseNameOfAVariant() {
        var listing = Map.of("google/veo-3.1", Set.of(VIDEO_GENERATION));

        var modalities = ModelModalitiesRegistry.findModelModalities(List.of(listing), "google/veo-3.1:free");

        assertEquals(Set.of(VIDEO_GENERATION), modalities.orElseThrow());
    }

    @Test
    void findModelModalities_unknownModel_isEmpty() {
        var listing = Map.of("google/veo-3.1", Set.of(VIDEO_GENERATION));

        assertTrue(ModelModalitiesRegistry.findModelModalities(List.of(listing), "nope/nope").isEmpty());
    }

}
