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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;
import static org.omnifaces.ai.service.BaseAIService.parseClassificationResults;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.model.ClassificationResult;
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

}
