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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;
import static org.omnifaces.ai.service.BaseAIService.parseClassificationResults;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.model.ClassificationResult;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.ModerationOptions.Category;
import org.omnifaces.ai.service.BaseAIService.UploadedFileJsonStructure;

class BaseAIServiceTest {

    // =================================================================================================================
    // UploadedFileJsonStructure - valid construction
    // =================================================================================================================

    @Test
    void uploadedFileJsonStructure_validArguments_createsRecord() {
        var structure = new UploadedFileJsonStructure("data", "filename", "id", "created_at");
        assertEquals("data", structure.filesArrayProperty());
        assertEquals("filename", structure.fileNameProperty());
        assertEquals("id", structure.fileIdProperty());
        assertEquals("created_at", structure.createdAtProperty());
    }

    // =================================================================================================================
    // UploadedFileJsonStructure - blank argument validation
    // =================================================================================================================

    @Test
    void uploadedFileJsonStructure_blankFilesArrayProperty_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new UploadedFileJsonStructure(" ", "filename", "id", "created_at"));
    }

    @Test
    void uploadedFileJsonStructure_blankFileNameProperty_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new UploadedFileJsonStructure("data", " ", "id", "created_at"));
    }

    @Test
    void uploadedFileJsonStructure_blankFileIdProperty_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new UploadedFileJsonStructure("data", "filename", " ", "created_at"));
    }

    @Test
    void uploadedFileJsonStructure_blankCreatedAtProperty_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new UploadedFileJsonStructure("data", "filename", "id", " "));
    }

    @Test
    void uploadedFileJsonStructure_nullFilesArrayProperty_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new UploadedFileJsonStructure(null, "filename", "id", "created_at"));
    }

    // =================================================================================================================
    // parseClassificationResults
    // =================================================================================================================

    private static final List<String> LABELS = List.of("billing", "shipping", "technical");

    @Test
    void parseClassificationResults_ordersByConfidenceDescending() {
        var results = parseClassificationResults(parseJson("""
            {"results":[{"label":"billing","confidence":0.2},{"label":"shipping","confidence":0.9},{"label":"technical","confidence":0.5}]}
            """), LABELS);

        assertEquals(List.of("shipping", "technical", "billing"), results.stream().map(ClassificationResult::label).toList());
    }

    @Test
    void parseClassificationResults_labelLeftOutByAi_scoresZero() {
        var results = parseClassificationResults(parseJson("""
            {"results":[{"label":"billing","confidence":0.7}]}
            """), LABELS);

        assertEquals(3, results.size());
        assertEquals(new ClassificationResult("billing", 0.7), results.get(0));
        assertEquals(0.0, results.get(1).confidence());
        assertEquals(0.0, results.get(2).confidence());
    }

    @Test
    void parseClassificationResults_labelInventedByAi_isLeftOut() {
        var results = parseClassificationResults(parseJson("""
            {"results":[{"label":"refunds","confidence":0.9},{"label":"billing","confidence":0.3}]}
            """), LABELS);

        assertEquals(LABELS.size(), results.size());
        assertEquals(new ClassificationResult("billing", 0.3), results.get(0));
    }

    @Test
    void parseClassificationResults_labelScoredTwiceByAi_takesTheFirstScore() {
        var results = parseClassificationResults(parseJson("""
            {"results":[{"label":"billing","confidence":0.8},{"label":"billing","confidence":0.1}]}
            """), LABELS);

        assertEquals(new ClassificationResult("billing", 0.8), results.get(0));
    }

    @Test
    void parseClassificationResults_noResultsAtAll_scoresEveryLabelZero() {
        var results = parseClassificationResults(parseJson("{}"), LABELS);

        assertEquals(LABELS.size(), results.size());
        assertEquals(0.0, results.stream().mapToDouble(ClassificationResult::confidence).sum());
    }

    // =================================================================================================================
    // The schemas which shape the answer
    // =================================================================================================================

    /**
     * The classification schema offers the labels as the only values the AI may answer with, so it cannot invent one of its own.
     */
    @Test
    void buildClassifyJsonSchema_offersTheLabelsAsTheOnlyChoices() {
        var schema = BaseAIService.buildClassifyJsonSchema(LABELS);

        var label = schema.getJsonObject("properties").getJsonObject("label");
        assertEquals("string", label.getString("type"));
        assertEquals(LABELS, label.getJsonArray("enum").getValuesAs(jakarta.json.JsonString.class).stream().map(jakarta.json.JsonString::getString).toList());
        assertEquals(
            List.of("label", "confidence"), schema.getJsonArray("required").getValuesAs(jakarta.json.JsonString.class).stream()
                .map(jakarta.json.JsonString::getString).toList()
        );
    }

    /**
     * Scoring every label needs each of them stated as a required field, as a label the AI leaves out is not the same as one it scored at zero.
     */
    @Test
    void buildClassifyAllJsonSchema_requiresAScorePerLabel() {
        var schema = BaseAIService.buildClassifyAllJsonSchema(LABELS);

        assertEquals("object", schema.getString("type"));
        assertFalse(schema.toString().isEmpty());
        LABELS.forEach(label -> assertTrue(schema.toString().contains(label), label));
    }

    @Test
    void buildModerationJsonSchema_requiresAScorePerRequestedCategory() {
        var options = ModerationOptions.newBuilder().categories(Category.HATE, Category.VIOLENCE).build();

        var scores = BaseAIService.buildModerationJsonSchema(options).getJsonObject("properties").getJsonObject("scores");

        assertEquals("number", scores.getJsonObject("properties").getJsonObject("hate").getString("type"));
        assertEquals("number", scores.getJsonObject("properties").getJsonObject("violence").getString("type"));
        assertEquals(2, scores.getJsonArray("required").size());
    }

    // =================================================================================================================
    // Reading the moderation answer
    // =================================================================================================================

    @Test
    void parseModerationResult_keepsTheScoresOfTheRequestedCategoriesAlone() {
        var options = ModerationOptions.newBuilder().categories(Category.HATE).build();

        var result = BaseAIService.parseModerationResult(parseJson("{\"scores\":{\"hate\":0.2,\"violence\":0.9}}"), options);

        assertEquals(Set.of("hate"), result.getScores().keySet());
    }

    @Test
    void parseModerationResult_flagsWhenAScoreExceedsTheThreshold() {
        var options = ModerationOptions.newBuilder().categories(Category.HATE).threshold(0.5).build();

        assertFalse(BaseAIService.parseModerationResult(parseJson("{\"scores\":{\"hate\":0.4}}"), options).isFlagged());
        assertTrue(BaseAIService.parseModerationResult(parseJson("{\"scores\":{\"hate\":0.6}}"), options).isFlagged());
    }

    /**
     * A category the AI did not score is left out rather than reported as zero, which would read as a judgement it never made.
     */
    @Test
    void parseModerationResult_categoryWhichWasNotScored_isLeftOut() {
        var options = ModerationOptions.newBuilder().categories(Category.HATE, Category.VIOLENCE).build();

        var result = BaseAIService.parseModerationResult(parseJson("{\"scores\":{\"hate\":0.2}}"), options);

        assertEquals(Set.of("hate"), result.getScores().keySet());
    }

    @Test
    void parseModerationResult_answerWithoutAnyScores_isNotFlagged() {
        var result = BaseAIService.parseModerationResult(parseJson("{}"), ModerationOptions.DEFAULT);

        assertFalse(result.isFlagged());
        assertTrue(result.getScores().isEmpty());
    }

    // =================================================================================================================
    // Reading the timestamp of an uploaded file
    // =================================================================================================================

    /**
     * Providers state the upload time either as epoch seconds or as an ISO instant, so both are read into the same instant.
     */
    @Test
    void tryParseFileCreatedAtTimestamp_epochSecondsAndIsoInstant_readTheSameInstant() {
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), BaseAIService.tryParseFileCreatedAtTimestamp("1704067200"));
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), BaseAIService.tryParseFileCreatedAtTimestamp("2024-01-01T00:00:00Z"));
    }

    @Test
    void tryParseFileCreatedAtTimestamp_valueWhichIsNeither_isRejected() {
        assertThrows(DateTimeParseException.class, () -> BaseAIService.tryParseFileCreatedAtTimestamp("yesterday"));
    }

}
