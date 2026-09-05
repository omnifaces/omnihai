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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.exception.AIResponseException;

/**
 * An image arrives Base64 encoded in the response body, and content which does not decode is reported as an unusable answer rather than handed on as bytes.
 */
class DefaultAIImageHandlerTest {

    private final DefaultAIImageHandler handler = new DefaultAIImageHandler() {

        @Override
        public List<String> getImageResponseContentPaths() {
            return List.of("data[0].b64_json");
        }

    };

    @Test
    void buildAnalyzeImagePrompt_asksWhatIsInTheImage() {
        assertTrue(handler.buildAnalyzeImagePrompt().toLowerCase(java.util.Locale.ROOT).contains("image"), handler.buildAnalyzeImagePrompt());
    }

    @Test
    void buildGenerateAltTextPrompt_asksForAltText() {
        assertTrue(handler.buildGenerateAltTextPrompt().toLowerCase(java.util.Locale.ROOT).contains("alt"), handler.buildGenerateAltTextPrompt());
    }

    @Test
    void parseImageContent_decodesTheContent() {
        var encoded = Base64.getEncoder().encodeToString("image bytes".getBytes(UTF_8));

        assertArrayEquals("image bytes".getBytes(UTF_8), handler.parseImageContent(parseJson("{\"data\":[{\"b64_json\":\"" + encoded + "\"}]}")));
    }

    @Test
    void parseImageContent_withoutAnyContent_saysWhereItLooked() {
        var response = parseJson("{\"data\":[]}");

        var exception = assertThrows(AIResponseException.class, () -> handler.parseImageContent(response));
        assertTrue(exception.getMessage().contains("No image content found"), exception.getMessage());
    }

    @Test
    void parseImageContent_contentWhichDoesNotDecode_saysSo() {
        var response = parseJson("{\"data\":[{\"b64_json\":\"!!!not base64!!!\"}]}");

        var exception = assertThrows(AIResponseException.class, () -> handler.parseImageContent(response));
        assertTrue(exception.getMessage().contains("Base64"), exception.getMessage());
    }

    @Test
    void parseImageContent_responseStatingAnError_reportsTheError() {
        var response = parseJson("{\"error\":{\"message\":\"content policy\"}}");

        var exception = assertThrows(AIResponseException.class, () -> handler.parseImageContent(response));
        assertTrue(exception.getMessage().contains("content policy"), exception.getMessage());
    }

}
