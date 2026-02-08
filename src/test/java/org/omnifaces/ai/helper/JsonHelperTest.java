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
package org.omnifaces.ai.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.exception.AIResponseException;

class JsonHelperTest {

    // =================================================================================================================
    // isEmpty tests
    // =================================================================================================================

    @Test
    void isEmpty_null_returnsTrue() {
        assertTrue(JsonHelper.isEmpty(null));
    }

    @Test
    void isEmpty_emptyObject_returnsTrue() {
        var obj = Json.createObjectBuilder().build();
        assertTrue(JsonHelper.isEmpty(obj));
    }

    @Test
    void isEmpty_nonEmptyObject_returnsFalse() {
        var obj = Json.createObjectBuilder().add("key", "value").build();
        assertFalse(JsonHelper.isEmpty(obj));
    }

    @Test
    void isEmpty_emptyArray_returnsTrue() {
        var arr = Json.createArrayBuilder().build();
        assertTrue(JsonHelper.isEmpty(arr));
    }

    @Test
    void isEmpty_nonEmptyArray_returnsFalse() {
        var arr = Json.createArrayBuilder().add("item").build();
        assertFalse(JsonHelper.isEmpty(arr));
    }

    @Test
    void isEmpty_blankString_returnsTrue() {
        var str = Json.createValue("   ");
        assertTrue(JsonHelper.isEmpty(str));
    }

    @Test
    void isEmpty_nonBlankString_returnsFalse() {
        var str = Json.createValue("hello");
        assertFalse(JsonHelper.isEmpty(str));
    }

    @Test
    void isEmpty_unsupportedType_throwsException() {
        assertThrows(UnsupportedOperationException.class, () -> JsonHelper.isEmpty(JsonValue.TRUE));
    }

    // =================================================================================================================
    // parseJson tests
    // =================================================================================================================

    @Test
    void parseJson_validJson() {
        var result = JsonHelper.parseJson("{\"name\":\"test\",\"value\":42}");

        assertEquals("test", result.getString("name"));
        assertEquals(42, result.getInt("value"));
    }

    @Test
    void parseJson_jsonInMarkdownBlock() {
        var input = "```json\n{\"key\":\"value\"}\n```";
        var result = JsonHelper.parseJson(input);

        assertEquals("value", result.getString("key"));
    }

    @Test
    void parseJson_jsonWithSurroundingText() {
        var input = "Here is the response: {\"data\":\"test\"} end of response";
        var result = JsonHelper.parseJson(input);

        assertEquals("test", result.getString("data"));
    }

    @Test
    void parseJson_invalidJson_throwsException() {
        assertThrows(AIResponseException.class, () -> JsonHelper.parseJson("not json"));
    }

    @Test
    void parseJson_noBraces_throwsException() {
        assertThrows(AIResponseException.class, () -> JsonHelper.parseJson("just text"));
    }

    // =================================================================================================================
    // findByPath tests
    // =================================================================================================================

    @Test
    void findByPath_simplePath() {
        var json = Json.createObjectBuilder()
                .add("message", "hello")
                .build();

        assertEquals("hello", JsonHelper.findByPath(json, "message").orElseThrow());
    }

    @Test
    void findByPath_nestedPath() {
        var json = Json.createObjectBuilder()
                .add("response", Json.createObjectBuilder()
                        .add("content", "nested value"))
                .build();

        assertEquals("nested value", JsonHelper.findByPath(json, "response.content").orElseThrow());
    }

    @Test
    void findByPath_arrayIndex() {
        var json = Json.createObjectBuilder()
                .add("choices", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("text", "first"))
                        .add(Json.createObjectBuilder().add("text", "second")))
                .build();

        assertEquals("first", JsonHelper.findByPath(json, "choices[0].text").orElseThrow());
        assertEquals("second", JsonHelper.findByPath(json, "choices[1].text").orElseThrow());
    }

    @Test
    void findByPath_wildcardArray() {
        var json = Json.createObjectBuilder()
                .add("items", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("value", "one"))
                        .add(Json.createObjectBuilder().add("value", "two")))
                .build();

        // Returns first match
        assertEquals("one", JsonHelper.findByPath(json, "items[*].value").orElseThrow());
    }

    @Test
    void findByPath_missingPath_returnsEmpty() {
        var json = Json.createObjectBuilder().add("key", "value").build();

        assertTrue(JsonHelper.findByPath(json, "nonexistent").isEmpty());
        assertTrue(JsonHelper.findByPath(json, "key.nested").isEmpty());
    }

    @Test
    void findByPath_nullInputs_returnsEmpty() {
        var json = Json.createObjectBuilder().add("key", "value").build();

        assertTrue(JsonHelper.findByPath(null, "key").isEmpty());
        assertTrue(JsonHelper.findByPath(json, null).isEmpty());
    }

    @Test
    void findByPath_arrayIndexOutOfBounds_returnsEmpty() {
        var json = Json.createObjectBuilder()
                .add("items", Json.createArrayBuilder().add("only"))
                .build();

        assertTrue(JsonHelper.findByPath(json, "items[5]").isEmpty());
    }

    @Test
    void findByPath_whitespaceOnly_returnsWhitespace() {
        var json = Json.createObjectBuilder()
                .add("token", "   ")
                .build();

        // findByPath allows whitespace because it can be significant (e.g., streaming tokens)
        assertEquals("   ", JsonHelper.findByPath(json, "token").orElseThrow());
    }

    // =================================================================================================================
    // findFirstNonBlankByPath tests
    // =================================================================================================================

    @Test
    void findFirstNonBlankByPath_returnsFirstMatch() {
        var json = Json.createObjectBuilder()
                .add("primary", "first")
                .add("fallback", "second")
                .build();

        assertEquals("first", JsonHelper.findFirstNonBlankByPath(json, List.of("primary", "fallback")).orElseThrow());
    }

    @Test
    void findFirstNonBlankByPath_skipsEmptyValues() {
        var json = Json.createObjectBuilder()
                .add("empty", "")
                .add("valid", "found")
                .build();

        assertEquals("found", JsonHelper.findFirstNonBlankByPath(json, List.of("empty", "valid")).orElseThrow());
    }

    @Test
    void findFirstNonBlankByPath_skipsWhitespaceOnlyValues() {
        var json = Json.createObjectBuilder()
                .add("whitespace", "   ")
                .add("valid", "found")
                .build();

        // findFirstNonBlankByPath skips whitespace-only because it's looking for meaningful content
        assertEquals("found", JsonHelper.findFirstNonBlankByPath(json, List.of("whitespace", "valid")).orElseThrow());
    }

    @Test
    void findFirstNonBlankByPath_returnsFallback() {
        var json = Json.createObjectBuilder()
                .add("fallback", "value")
                .build();

        assertEquals("value", JsonHelper.findFirstNonBlankByPath(json, List.of("missing", "fallback")).orElseThrow());
    }

    @Test
    void findFirstNonBlankByPath_allMissing_returnsEmpty() {
        var json = Json.createObjectBuilder()
                .add("other", "value")
                .build();

        assertTrue(JsonHelper.findFirstNonBlankByPath(json, List.of("missing1", "missing2")).isEmpty());
    }

    // =================================================================================================================
    // parseAndCheckErrors tests
    // =================================================================================================================

    @Test
    void parseAndCheckErrors_noError_returnsJson() {
        var responseBody = "{\"result\":\"success\"}";

        var result = JsonHelper.parseAndCheckErrors(responseBody, List.of("error.message", "error"));

        assertEquals("success", result.getString("result"));
    }

    @Test
    void parseAndCheckErrors_errorAtFirstPath_throwsException() {
        var responseBody = "{\"error\":{\"message\":\"Something went wrong\"}}";

        var exception = assertThrows(AIResponseException.class,
                () -> JsonHelper.parseAndCheckErrors(responseBody, List.of("error.message", "error")));

        assertTrue(exception.getMessage().contains("Something went wrong"));
    }

    @Test
    void parseAndCheckErrors_errorAtSecondPath_throwsException() {
        var responseBody = "{\"error\":\"Simple error\"}";

        var exception = assertThrows(AIResponseException.class,
                () -> JsonHelper.parseAndCheckErrors(responseBody, List.of("error.message", "error")));

        assertTrue(exception.getMessage().contains("Simple error"));
    }

    @Test
    void parseAndCheckErrors_invalidJson_throwsException() {
        assertThrows(AIResponseException.class,
                () -> JsonHelper.parseAndCheckErrors("not json", List.of("error")));
    }

    // =================================================================================================================
    // addStrictAdditionalProperties tests
    // =================================================================================================================

    @Test
    void addStrictAdditionalProperties_simpleSchema() {
        var schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                        .add("name", Json.createObjectBuilder().add("type", "string")))
                .build();

        var result = JsonHelper.addStrictAdditionalProperties(schema);

        assertFalse(result.getBoolean("additionalProperties"));
    }

    @Test
    void addStrictAdditionalProperties_nestedSchema() {
        var schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                        .add("inner", Json.createObjectBuilder()
                                .add("type", "object")
                                .add("properties", Json.createObjectBuilder()
                                        .add("value", Json.createObjectBuilder().add("type", "string")))))
                .build();

        var result = JsonHelper.addStrictAdditionalProperties(schema);

        assertFalse(result.getBoolean("additionalProperties"));
        var innerSchema = result.getJsonObject("properties").getJsonObject("inner");
        assertFalse(innerSchema.getBoolean("additionalProperties"));
    }

    @Test
    void addStrictAdditionalProperties_arrayWithNestedObjectItems() {
        var schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", Json.createObjectBuilder()
                        .add("items", Json.createObjectBuilder()
                                .add("type", "array")
                                .add("items", Json.createObjectBuilder()
                                        .add("type", "object")
                                        .add("properties", Json.createObjectBuilder()
                                                .add("name", Json.createObjectBuilder().add("type", "string"))))))
                .build();

        var result = JsonHelper.addStrictAdditionalProperties(schema);

        assertFalse(result.getBoolean("additionalProperties"));
        var arrayProp = result.getJsonObject("properties").getJsonObject("items");
        var itemsSchema = arrayProp.getJsonObject("items");
        assertFalse(itemsSchema.getBoolean("additionalProperties"));
    }

    // =================================================================================================================
    // replaceField tests
    // =================================================================================================================

    @Test
    void replaceField_replacesExistingField() {
        var original = Json.createObjectBuilder()
                .add("name", "old")
                .add("value", 1)
                .build();

        var result = JsonHelper.replaceField(original, "name", Json.createValue("new")).build();

        assertEquals("new", result.getString("name"));
        assertEquals(1, result.getInt("value"));
    }

    @Test
    void replaceField_preservesOrder() {
        var original = Json.createObjectBuilder()
                .add("a", 1)
                .add("b", 2)
                .add("c", 3)
                .build();

        var result = JsonHelper.replaceField(original, "b", Json.createValue(99)).build();

        var keys = result.keySet().toArray(new String[0]);
        assertEquals("a", keys[0]);
        assertEquals("b", keys[1]);
        assertEquals("c", keys[2]);
        assertEquals(99, result.getInt("b"));
    }

    @Test
    void replaceField_nonExistentField_addsNothing() {
        var original = Json.createObjectBuilder()
                .add("key", "value")
                .build();

        var result = JsonHelper.replaceField(original, "nonexistent", Json.createValue("new")).build();

        assertEquals(1, result.size());
        assertEquals("value", result.getString("key"));
    }
}
