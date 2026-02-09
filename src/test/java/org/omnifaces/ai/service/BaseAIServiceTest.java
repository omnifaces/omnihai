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

import org.junit.jupiter.api.Test;

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
}
