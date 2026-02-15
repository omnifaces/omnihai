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

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.mime.MimeType;

/**
 * Base class for IT on image-generator-related methods of AI service.
 *
 * NOTE: this is a separate class from {@link BaseAIServiceImageHandlerIT} because image generation might require a different model than even image analysis.
 */
abstract class BaseAIServiceImageGeneratorIT extends AIServiceIT {

    @Test
    void generateImage() throws Exception {
        var response = service.generateImage("Willemstad, Curacao");
        assertTrue(response.length > 0, "Image response should not be empty");
        var mimeType = MimeType.guessMimeType(response);
        var targetDir = Path.of(System.getProperty("user.dir"), "target", "image-generator-test-results");
        targetDir.toFile().mkdirs();
        var tempFilePath = Files.createTempFile(targetDir, getClass().getSimpleName() + "-", "." + mimeType.extension());
        Files.write(tempFilePath, response);
        log("saved in " + tempFilePath.toString());

        assertTrue(mimeType.isImage(), "Mime type is image");
        var image = ImageIO.read(new ByteArrayInputStream(response));
        var size = image.getWidth() + "x" + image.getHeight();
        assertTrue(image.getWidth() >= 512 && image.getHeight() >= 512, "Image size should be at least 512x512: " + size);
        assertTrue(image.getWidth() * image.getHeight() <= 2048 * 2048 * 2, "Image resolution is not supposed to exceed 8k: " + size);
    }
}
