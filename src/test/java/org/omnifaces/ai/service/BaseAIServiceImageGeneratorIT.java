package org.omnifaces.ai.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.helper.ImageHelper.guessImageMimeType;
import static org.omnifaces.ai.helper.ImageHelper.toImageBase64;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

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
