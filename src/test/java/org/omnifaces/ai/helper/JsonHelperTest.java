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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    // extractByPath tests
    // =================================================================================================================

    @Test
    void extractByPath_simplePath() {
        var json = Json.createObjectBuilder()
                .add("message", "hello")
                .build();

        assertEquals("hello", JsonHelper.extractByPath(json, "message"));
    }

    @Test
    void extractByPath_nestedPath() {
        var json = Json.createObjectBuilder()
                .add("response", Json.createObjectBuilder()
                        .add("content", "nested value"))
                .build();

        assertEquals("nested value", JsonHelper.extractByPath(json, "response.content"));
    }

    @Test
    void extractByPath_arrayIndex() {
        var json = Json.createObjectBuilder()
                .add("choices", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("text", "first"))
                        .add(Json.createObjectBuilder().add("text", "second")))
                .build();

        assertEquals("first", JsonHelper.extractByPath(json, "choices[0].text"));
        assertEquals("second", JsonHelper.extractByPath(json, "choices[1].text"));
    }

    @Test
    void extractByPath_wildcardArray() {
        var json = Json.createObjectBuilder()
                .add("items", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("value", "one"))
                        .add(Json.createObjectBuilder().add("value", "two")))
                .build();

        // Returns first match
        assertEquals("one", JsonHelper.extractByPath(json, "items[*].value"));
    }

    @Test
    void extractByPath_missingPath_returnsNull() {
        var json = Json.createObjectBuilder().add("key", "value").build();

        assertNull(JsonHelper.extractByPath(json, "nonexistent"));
        assertNull(JsonHelper.extractByPath(json, "key.nested"));
    }

    @Test
    void extractByPath_nullInputs_returnsNull() {
        var json = Json.createObjectBuilder().add("key", "value").build();

        assertNull(JsonHelper.extractByPath(null, "key"));
        assertNull(JsonHelper.extractByPath(json, null));
    }

    @Test
    void extractByPath_arrayIndexOutOfBounds_returnsNull() {
        var json = Json.createObjectBuilder()
                .add("items", Json.createArrayBuilder().add("only"))
                .build();

        assertNull(JsonHelper.extractByPath(json, "items[5]"));
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
