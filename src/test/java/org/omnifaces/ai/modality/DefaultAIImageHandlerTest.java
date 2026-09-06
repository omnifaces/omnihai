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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    /**
     * An answer which carries no image is reported as an unusable answer naming why, whether the provider stated an error, stated no content at all, or stated
     * content which is no image. The expected message names the case.
     */
    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', textBlock = """
        {"data":[]}                                | No image content found
        {"data":[{"b64_json":"!!!not base64!!!"}]} | Base64
        {"error":{"message":"content policy"}}     | content policy
        """)
    void parseImageContent_answerWhichCarriesNoImage_saysWhy(String answer, String expected) {
        var response = parseJson(answer);

        var exception = assertThrows(AIResponseException.class, () -> handler.parseImageContent(response));
        assertTrue(exception.getMessage().contains(expected), exception.getMessage());
    }

}
