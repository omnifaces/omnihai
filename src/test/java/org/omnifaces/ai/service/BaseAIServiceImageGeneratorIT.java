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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.helper.ImageHelper.guessImageMimeType;
import static org.omnifaces.ai.helper.ImageHelper.toImageBase64;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Base class for IT on image-generator-related methods of AI service.
 *
 * NOTE: this is a separate class because image generation might require a different model than e.g. image analysis.
 */
abstract class BaseAIServiceImageGeneratorIT extends AIServiceIT {

    @Test
    void generateImage() throws Exception {
        var response = service.generateImage("Willemstad, Curacao");
        assertTrue(response.length > 0);
        var mimeType = guessImageMimeType(toImageBase64(response));
        var targetDir = Path.of(System.getProperty("user.dir"), "target");
        var tempFilePath = Files.createTempFile(targetDir, getClass().getSimpleName(), "." + mimeType.split("/", 2)[1]);
        Files.write(tempFilePath, response);
        log(tempFilePath.toString());
    }
}
