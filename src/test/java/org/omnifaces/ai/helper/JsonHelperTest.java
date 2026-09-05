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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParsingException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omnifaces.ai.exception.AIResponseException;

class JsonHelperTest {

    @TempDir
    private Path tempDir;

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

    @Test
    void parseJson_plainTextAnswer_statesWhatIsWrongWithIt() {
        var exception = assertThrows(AIResponseException.class, () -> JsonHelper.parseJson("shipping\n0.95"));

        assertEquals("shipping\n0.95", exception.getResponseBody());
        assertInstanceOf(JsonParsingException.class, exception.getCause(), "the cause must name the parse problem rather than an index");
    }

    @Test
    void parseJson_openingBraceWithoutClosingOne_statesWhatIsWrongWithIt() {
        var exception = assertThrows(AIResponseException.class, () -> JsonHelper.parseJson("here it is: {\"key\":"));

        assertInstanceOf(JsonParsingException.class, exception.getCause());
    }

    @Test
    void parseJson_closingBraceBeforeOpeningOne_statesWhatIsWrongWithIt() {
        var exception = assertThrows(AIResponseException.class, () -> JsonHelper.parseJson("} then {"));

        assertInstanceOf(JsonParsingException.class, exception.getCause());
    }

    // =================================================================================================================
    // findFirstByPath tests
    // =================================================================================================================

    @Test
    void findByPath_simplePath() {
        var json = Json.createObjectBuilder()
            .add("message", "hello")
            .build();

        assertEquals("hello", JsonHelper.findFirstByPath(json, "message").orElseThrow());
    }

    @Test
    void findByPath_nestedPath() {
        var json = Json.createObjectBuilder()
            .add(
                "response", Json.createObjectBuilder()
                    .add("content", "nested value")
            )
            .build();

        assertEquals("nested value", JsonHelper.findFirstByPath(json, "response.content").orElseThrow());
    }

    @Test
    void findByPath_arrayIndex() {
        var json = Json.createObjectBuilder()
            .add(
                "choices", Json.createArrayBuilder()
                    .add(Json.createObjectBuilder().add("text", "first"))
                    .add(Json.createObjectBuilder().add("text", "second"))
            )
            .build();

        assertEquals("first", JsonHelper.findFirstByPath(json, "choices[0].text").orElseThrow());
        assertEquals("second", JsonHelper.findFirstByPath(json, "choices[1].text").orElseThrow());
    }

    @Test
    void findByPath_wildcardArray() {
        var json = Json.createObjectBuilder()
            .add(
                "items", Json.createArrayBuilder()
                    .add(Json.createObjectBuilder().add("value", "one"))
                    .add(Json.createObjectBuilder().add("value", "two"))
            )
            .build();

        // Returns first match
        assertEquals("one", JsonHelper.findFirstByPath(json, "items[*].value").orElseThrow());
    }

    @Test
    void findByPath_missingPath_returnsEmpty() {
        var json = Json.createObjectBuilder().add("key", "value").build();

        assertTrue(JsonHelper.findFirstByPath(json, "nonexistent").isEmpty());
        assertTrue(JsonHelper.findFirstByPath(json, "key.nested").isEmpty());
    }

    @Test
    void findByPath_nullInputs_returnsEmpty() {
        var json = Json.createObjectBuilder().add("key", "value").build();

        assertTrue(JsonHelper.findFirstByPath((JsonObject) null, "key").isEmpty());
        assertTrue(JsonHelper.findFirstByPath(json, null).isEmpty());
    }

    @Test
    void findByPath_arrayIndexOutOfBounds_returnsEmpty() {
        var json = Json.createObjectBuilder()
            .add("items", Json.createArrayBuilder().add("only"))
            .build();

        assertTrue(JsonHelper.findFirstByPath(json, "items[5]").isEmpty());
    }

    @Test
    void findByPath_whitespaceOnly_returnsWhitespace() {
        var json = Json.createObjectBuilder()
            .add("token", "   ")
            .build();

        // findFirstByPath allows whitespace because it can be significant (e.g., streaming tokens)
        assertEquals("   ", JsonHelper.findFirstByPath(json, "token").orElseThrow());
    }

    // =================================================================================================================
    // findFirstNonBlankByPath tests
    // =================================================================================================================

    @Test
    void findFirstNonBlankByPath_simplePath() {
        var json = Json.createObjectBuilder()
            .add("message", "hello")
            .build();

        assertEquals("hello", JsonHelper.findFirstNonBlankByPath(json, "message").orElseThrow());
    }

    @Test
    void findFirstNonBlankByPath_stripsWhitespace() {
        var json = Json.createObjectBuilder()
            .add("message", "  hello  ")
            .build();

        assertEquals("hello", JsonHelper.findFirstNonBlankByPath(json, "message").orElseThrow());
    }

    @Test
    void findFirstNonBlankByPath_whitespaceOnly_returnsEmpty() {
        var json = Json.createObjectBuilder()
            .add("token", "   ")
            .build();

        assertTrue(JsonHelper.findFirstNonBlankByPath(json, "token").isEmpty());
    }

    @Test
    void findFirstNonBlankByPath_emptyString_returnsEmpty() {
        var json = Json.createObjectBuilder()
            .add("token", "")
            .build();

        assertTrue(JsonHelper.findFirstNonBlankByPath(json, "token").isEmpty());
    }

    @Test
    void findFirstNonBlankByPath_missingPath_returnsEmpty() {
        var json = Json.createObjectBuilder().add("key", "value").build();

        assertTrue(JsonHelper.findFirstNonBlankByPath(json, "nonexistent").isEmpty());
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

        assertEquals("first", JsonHelper.findFirstNonBlankByPaths(json, List.of("primary", "fallback")).orElseThrow());
    }

    @Test
    void findFirstNonBlankByPath_skipsEmptyValues() {
        var json = Json.createObjectBuilder()
            .add("empty", "")
            .add("valid", "found")
            .build();

        assertEquals("found", JsonHelper.findFirstNonBlankByPaths(json, List.of("empty", "valid")).orElseThrow());
    }

    @Test
    void findFirstNonBlankByPath_skipsWhitespaceOnlyValues() {
        var json = Json.createObjectBuilder()
            .add("whitespace", "   ")
            .add("valid", "found")
            .build();

        // findFirstNonBlankByPath skips whitespace-only because it's looking for meaningful content
        assertEquals("found", JsonHelper.findFirstNonBlankByPaths(json, List.of("whitespace", "valid")).orElseThrow());
    }

    @Test
    void findFirstNonBlankByPath_returnsFallback() {
        var json = Json.createObjectBuilder()
            .add("fallback", "value")
            .build();

        assertEquals("value", JsonHelper.findFirstNonBlankByPaths(json, List.of("missing", "fallback")).orElseThrow());
    }

    @Test
    void findFirstNonBlankByPath_allMissing_returnsEmpty() {
        var json = Json.createObjectBuilder()
            .add("other", "value")
            .build();

        assertTrue(JsonHelper.findFirstNonBlankByPaths(json, List.of("missing1", "missing2")).isEmpty());
    }

    // =================================================================================================================
    // checkErrors tests
    // =================================================================================================================

    @Test
    void checkErrors_noError_doesNotThrow() {
        var responseJson = Json.createObjectBuilder().add("result", "success").build();

        JsonHelper.checkErrors(responseJson, List.of("error.message", "error"));
    }

    @Test
    void checkErrors_errorAtFirstPath_throwsException() {
        var responseJson = JsonHelper.parseJson("{\"error\":{\"message\":\"Something went wrong\"}}");
        var paths = List.of("error.message", "error");

        var exception = assertThrows(AIResponseException.class, () -> JsonHelper.checkErrors(responseJson, paths));

        assertTrue(exception.getMessage().contains("Something went wrong"));
    }

    @Test
    void checkErrors_errorAtSecondPath_throwsException() {
        var responseJson = Json.createObjectBuilder().add("error", "Simple error").build();
        var paths = List.of("error.message", "error");

        var exception = assertThrows(AIResponseException.class, () -> JsonHelper.checkErrors(responseJson, paths));

        assertTrue(exception.getMessage().contains("Simple error"));
    }

    // =================================================================================================================
    // addStrictAdditionalProperties tests
    // =================================================================================================================

    @Test
    void addStrictAdditionalProperties_simpleSchema() {
        var schema = Json.createObjectBuilder()
            .add("type", "object")
            .add(
                "properties", Json.createObjectBuilder()
                    .add("name", Json.createObjectBuilder().add("type", "string"))
            )
            .build();

        var result = JsonHelper.addStrictAdditionalProperties(schema);

        assertFalse(result.getBoolean("additionalProperties"));
    }

    @Test
    void addStrictAdditionalProperties_nestedSchema() {
        var schema = Json.createObjectBuilder()
            .add("type", "object")
            .add(
                "properties", Json.createObjectBuilder()
                    .add(
                        "inner", Json.createObjectBuilder()
                            .add("type", "object")
                            .add(
                                "properties", Json.createObjectBuilder()
                                    .add("value", Json.createObjectBuilder().add("type", "string"))
                            )
                    )
            )
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
            .add(
                "properties", Json.createObjectBuilder()
                    .add(
                        "items", Json.createObjectBuilder()
                            .add("type", "array")
                            .add(
                                "items", Json.createObjectBuilder()
                                    .add("type", "object")
                                    .add(
                                        "properties", Json.createObjectBuilder()
                                            .add("name", Json.createObjectBuilder().add("type", "string"))
                                    )
                            )
                    )
            )
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

    // =================================================================================================================
    // findLastNonBlankByPaths
    // =================================================================================================================

    @Test
    void findLastNonBlankByPaths_wildcard_returnsTheLastMatch() {
        var json = JsonHelper.parseJson("{\"output\":[{\"content\":[{\"text\":\"first\"}]},{\"content\":[{\"text\":\"last\"}]}]}");
        var paths = List.of("output[*].content[*].text");

        assertEquals(Optional.of("last"), JsonHelper.findLastNonBlankByPaths(json, paths));
        assertEquals(Optional.of("first"), JsonHelper.findFirstNonBlankByPaths(json, paths), "which is where it differs from the first-match variant");
    }

    @Test
    void findLastNonBlankByPaths_blankLastMatch_isSkipped() {
        var json = JsonHelper.parseJson("{\"output\":[{\"content\":[{\"text\":\"kept\"}]},{\"content\":[{\"text\":\"  \"}]}]}");

        assertEquals(Optional.of("kept"), JsonHelper.findLastNonBlankByPaths(json, List.of("output[*].content[*].text")));
    }

    @Test
    void findLastNonBlankByPaths_takesTheFirstMatchingPath() {
        var json = JsonHelper.parseJson("{\"choices\":[{\"message\":{\"content\":\"completions\"}}]}");

        assertEquals(
            Optional.of("completions"),
            JsonHelper.findLastNonBlankByPaths(json, List.of("output[*].content[*].text", "choices[0].message.content"))
        );
    }

    // =================================================================================================================
    // File based path lookup - the streaming scanner which never loads the whole file
    // =================================================================================================================

    @Test
    void findFirstByPath_file_simplePath() throws IOException {
        assertEquals("hello", JsonHelper.findFirstByPath(jsonFile("{\"message\":\"hello\"}"), "message").orElseThrow());
    }

    @Test
    void findFirstByPath_file_nestedPath() throws IOException {
        var file = jsonFile("{\"response\":{\"content\":\"nested value\"}}");

        assertEquals("nested value", JsonHelper.findFirstByPath(file, "response.content").orElseThrow());
    }

    @Test
    void findFirstByPath_file_arrayIndex() throws IOException {
        var file = jsonFile("{\"choices\":[{\"text\":\"first\"},{\"text\":\"second\"}]}");

        assertEquals("first", JsonHelper.findFirstByPath(file, "choices[0].text").orElseThrow());
        assertEquals("second", JsonHelper.findFirstByPath(file, "choices[1].text").orElseThrow());
    }

    @Test
    void findFirstByPath_file_indexBeyondTheArray_findsNothing() throws IOException {
        var file = jsonFile("{\"choices\":[{\"text\":\"first\"}]}");

        assertTrue(JsonHelper.findFirstByPath(file, "choices[5].text").isEmpty());
    }

    /**
     * A wildcard answers the first element which carries the remaining path, so the elements in front of it which do not carry it are stepped over.
     */
    @Test
    void findFirstByPath_file_wildcardArray_reachesBeyondTheFirstElement() throws IOException {
        var file = jsonFile("{\"items\":[{\"other\":\"one\"},{\"value\":\"two\"}]}");

        assertEquals("two", JsonHelper.findFirstByPath(file, "items[*].value").orElseThrow());
    }

    @Test
    void findFirstByPath_file_nestedWildcards() throws IOException {
        var file = jsonFile("{\"output\":[{\"content\":[{\"text\":\"deep\"}]}]}");

        assertEquals("deep", JsonHelper.findFirstByPath(file, "output[*].content[*].text").orElseThrow());
    }

    /**
     * The scanner walks the file once, so a key which sits behind a nested object or array is only reached by stepping over what stands in front of it.
     */
    @Test
    void findFirstByPath_file_keyBehindNestedStructures() throws IOException {
        var file = jsonFile("{\"before\":{\"a\":[1,2,{\"b\":\"c\"}]},\"wanted\":\"found it\"}");

        assertEquals("found it", JsonHelper.findFirstByPath(file, "wanted").orElseThrow());
    }

    @Test
    void findFirstByPath_file_nestedKeyBehindANestedStructure() throws IOException {
        var file = jsonFile("{\"response\":{\"noise\":[{\"x\":1}],\"content\":\"nested value\"}}");

        assertEquals("nested value", JsonHelper.findFirstByPath(file, "response.content").orElseThrow());
    }

    @Test
    void findFirstByPath_file_unknownKey_findsNothing() throws IOException {
        assertTrue(JsonHelper.findFirstByPath(jsonFile("{\"message\":\"hello\"}"), "absent").isEmpty());
    }

    @Test
    void findFirstByPath_unreadableFile_throws() {
        var file = tempDir.resolve("does-not-exist.json");

        assertThrows(AIResponseException.class, () -> JsonHelper.findFirstByPath(file, "message"));
    }

    /**
     * A large value is handed over as a stream of its raw bytes, so that a caller can write it out without holding it in memory.
     */
    @Test
    void streamByPath_file_streamsTheRawValue() throws IOException {
        var file = jsonFile("{\"data\":{\"b64\":\"aGVsbG8=\"}}");

        try (var stream = JsonHelper.streamByPath(file, "data.b64")) {
            assertEquals("aGVsbG8=", new String(stream.readAllBytes(), UTF_8));
        }
    }

    @Test
    void streamByPath_file_unknownPath_isNull() throws IOException {
        assertNull(JsonHelper.streamByPath(jsonFile("{\"data\":{}}"), "data.absent"));
    }

    // =================================================================================================================
    // Null tolerance
    // =================================================================================================================

    @Test
    void findAllByPath_withoutARootOrAPath_findsNothing() {
        assertTrue(JsonHelper.findAllByPath(null, "message").isEmpty());
        assertTrue(JsonHelper.findAllByPath(JsonHelper.parseJson("{}"), null).isEmpty());
    }

    // =================================================================================================================
    // findAllByPath - values which are not text
    // =================================================================================================================

    @Test
    void findAllByPath_unknownPath_findsNothing() {
        assertTrue(JsonHelper.findAllByPath(JsonHelper.parseJson("{\"a\":1}"), "absent").isEmpty());
    }

    /**
     * A path may point at a number or a boolean, which the caller asked for as text, so it is handed over as it reads in the document.
     */
    @Test
    void findAllByPath_valueWhichIsNotText_isHandedOverAsItReads() {
        var json = JsonHelper.parseJson("{\"count\":42,\"done\":true}");

        assertEquals(List.of("42"), JsonHelper.findAllByPath(json, "count"));
        assertEquals(List.of("true"), JsonHelper.findAllByPath(json, "done"));
    }

    @Test
    void findAllByPath_wildcardOverObjectsAndArrays() {
        var json = JsonHelper.parseJson("{\"rows\":[{\"cells\":[\"a\",\"b\"]},{\"cells\":[\"c\"]}]}");

        assertEquals(List.of("a", "b", "c"), JsonHelper.findAllByPath(json, "rows[*].cells[*]"));
    }

    @Test
    void findAllByPath_indexBeyondTheArray_findsNothing() {
        assertTrue(JsonHelper.findAllByPath(JsonHelper.parseJson("{\"rows\":[\"a\"]}"), "rows[5]").isEmpty());
    }

    @Test
    void findAllByPath_pathThroughAScalar_findsNothing() {
        assertTrue(JsonHelper.findAllByPath(JsonHelper.parseJson("{\"a\":1}"), "a.b").isEmpty());
    }

    // =================================================================================================================
    // The deprecated single value lookups
    // =================================================================================================================

    @Test
    @SuppressWarnings("removal")
    void findByPath_answersTheFirstMatch() {
        assertEquals("hello", JsonHelper.findByPath(JsonHelper.parseJson("{\"message\":\"hello\"}"), "message").orElseThrow());
    }

    @Test
    @SuppressWarnings("removal")
    void findNonBlankByPath_skipsABlankValue() {
        assertTrue(JsonHelper.findNonBlankByPath(JsonHelper.parseJson("{\"message\":\"  \"}"), "message").isEmpty());
    }

    @Test
    @SuppressWarnings("removal")
    void findByPath_file_answersTheFirstMatch() throws IOException {
        assertEquals("hello", JsonHelper.findByPath(jsonFile("{\"message\":\"hello\"}"), "message").orElseThrow());
    }

    // =================================================================================================================
    // Errors stated in a file
    // =================================================================================================================

    @Test
    void checkErrors_file_statingAnError_throws() throws IOException {
        var file = jsonFile("{\"error\":{\"message\":\"quota exceeded\"}}");

        var exception = assertThrows(AIResponseException.class, () -> JsonHelper.checkErrors(file, List.of("error.message")));
        assertTrue(exception.getMessage().contains("quota exceeded"));
    }

    @Test
    void checkErrors_file_withoutAnError_passes() throws IOException {
        var file = jsonFile("{\"data\":\"fine\"}");

        JsonHelper.checkErrors(file, List.of("error.message"));
    }

    // =================================================================================================================
    // Paths which do not match the shape of the document
    // =================================================================================================================

    @Test
    void findFirstByPath_file_emptyPath_findsNothing() throws IOException {
        assertTrue(JsonHelper.findFirstByPath(jsonFile("{\"a\":1}"), "").isEmpty());
    }

    @Test
    void findFirstByPath_file_indexIntoSomethingWhichIsNotAnArray_findsNothing() throws IOException {
        assertTrue(JsonHelper.findFirstByPath(jsonFile("{\"a\":{\"b\":1}}"), "a[0].b").isEmpty());
    }

    @Test
    void findFirstByPath_file_keyIntoAnArrayElementWhichIsNotAnObject_findsNothing() throws IOException {
        assertTrue(JsonHelper.findFirstByPath(jsonFile("{\"rows\":[[1,2]]}"), "rows[0].cell").isEmpty());
    }

    @Test
    void findFirstByPath_file_nestedIndex() throws IOException {
        assertEquals("deep", JsonHelper.findFirstByPath(jsonFile("{\"rows\":[[{\"cell\":\"deep\"}]]}"), "rows[0][0].cell").orElseThrow());
    }

    /**
     * A path has to name a value, as an object or an array is not something the caller can be handed as text.
     */
    @Test
    void findFirstByPath_file_pathEndingAtAnArrayElement_isRejected() throws IOException {
        var file = jsonFile("{\"rows\":[{\"cell\":\"a\"}]}");

        assertThrows(AIResponseException.class, () -> JsonHelper.findFirstByPath(file, "rows[0]"));
    }

    @Test
    void findFirstByPath_malformedFile_throws() throws IOException {
        var file = jsonFile("{ this is not JSON");

        assertThrows(AIResponseException.class, () -> JsonHelper.findFirstByPath(file, "a"));
    }

    /**
     * A value which is not text carries no quotes to step over, so the range is the value as it reads in the document.
     */
    @Test
    void streamByPath_file_valueWhichIsNotText_streamsItAsItReads() throws IOException {
        try (var stream = JsonHelper.streamByPath(jsonFile("{\"count\":42}"), "count")) {
            assertEquals("42", new String(stream.readAllBytes(), UTF_8));
        }
    }

    // =================================================================================================================
    // Paths which the document does not answer
    // =================================================================================================================

    /**
     * A JSON null states that the value is absent, which is not the same as a value that happens to be empty, so it is skipped rather than collected.
     */
    @Test
    void findAllByPath_valueStatedAsNull_findsNothing() {
        assertTrue(JsonHelper.findAllByPath(JsonHelper.parseJson("{\"a\":null}"), "a").isEmpty());
    }

    @Test
    void findAllByPath_indexedSegmentOnAMissingProperty_findsNothing() {
        assertTrue(JsonHelper.findAllByPath(JsonHelper.parseJson("{\"a\":1}"), "rows[0]").isEmpty());
    }

    @Test
    void findAllByPath_indexedSegmentOnAPropertyStatedAsNull_findsNothing() {
        assertTrue(JsonHelper.findAllByPath(JsonHelper.parseJson("{\"rows\":null}"), "rows[0]").isEmpty());
    }

    @Test
    void findAllByPath_indexedSegmentThroughAScalar_findsNothing() {
        assertTrue(JsonHelper.findAllByPath(JsonHelper.parseJson("{\"a\":1}"), "a.b[0]").isEmpty());
    }

    @Test
    void findAllByPath_emptyValue_findsNothing() {
        assertTrue(JsonHelper.findAllByPath(JsonHelper.parseJson("{\"a\":\"\"}"), "a").isEmpty());
    }

    // =================================================================================================================
    // Strict schemas - shapes which state no schema of their own
    // =================================================================================================================

    @Test
    void addStrictAdditionalProperties_leavesWhatIsNotAnObjectSchemaAlone() {
        var strict = JsonHelper.addStrictAdditionalProperties(JsonHelper.parseJson("""
            {"type":"object","properties":{
              "plain":"not a schema",
              "arrayWithoutItems":{"type":"array"},
              "number":{"type":"integer"}}}
            """));

        var properties = strict.getJsonObject("properties");
        assertEquals("not a schema", properties.getString("plain"));
        assertFalse(properties.getJsonObject("arrayWithoutItems").containsKey("additionalProperties"));
        assertFalse(properties.getJsonObject("number").containsKey("additionalProperties"));
    }

    // =================================================================================================================
    // Values which the scanner steps over
    // =================================================================================================================

    /**
     * The bytes between the key and its value are not part of the value, so a document written with spaces streams the same value as one written without.
     */
    @Test
    void streamByPath_file_valueBehindWhitespace_streamsTheValueAlone() throws IOException {
        try (var stream = JsonHelper.streamByPath(jsonFile("{\"data\" :   \"payload\"}"), "data")) {
            assertEquals("payload", new String(stream.readAllBytes(), UTF_8));
        }
    }

    @Test
    void findFirstByPath_file_wildcardIntoAnElementWhichIsNotAnArray_findsNothing() throws IOException {
        assertTrue(JsonHelper.findFirstByPath(jsonFile("{\"rows\":[{\"a\":1}]}"), "rows[*][*]").isEmpty());
    }

    @Test
    void findFirstByPath_file_indexIntoAnElementWhichIsNotAnArray_findsNothing() throws IOException {
        assertTrue(JsonHelper.findFirstByPath(jsonFile("{\"rows\":[{\"a\":1}]}"), "rows[0][0]").isEmpty());
    }

    /**
     * A path may simply not fit the document the AI answered with, which is something the caller asks about rather than an error, so a mismatch answers nothing
     * rather than throwing.
     */
    @Test
    void findAllByPath_indexedSegmentOnAPropertyWhichIsNotAnArray_findsNothing() {
        assertTrue(JsonHelper.findAllByPath(JsonHelper.parseJson("{\"rows\":{\"a\":1}}"), "rows[0]").isEmpty());
    }

    @Test
    void findAllByPath_wildcardOnAPropertyWhichIsNotAnArray_findsNothing() {
        assertTrue(JsonHelper.findAllByPath(JsonHelper.parseJson("{\"rows\":\"not an array\"}"), "rows[*]").isEmpty());
    }

    @Test
    void findAllByPath_plainSegmentOnAnArray_findsNothing() {
        assertTrue(JsonHelper.findAllByPath(JsonHelper.parseJson("{\"rows\":[{\"a\":1}]}"), "rows.a").isEmpty());
    }

    @Test
    void findAllByPath_indexedSegmentOnAnArrayNode_findsNothing() {
        assertTrue(JsonHelper.findAllByPath(JsonHelper.parseJson("{\"rows\":[[1,2]]}"), "rows[*].cells[0]").isEmpty());
    }

    @Test
    void findFirstByPath_file_wildcardIntoANestedArray() throws IOException {
        var file = jsonFile("{\"rows\":[[{\"cell\":\"deep\"}]]}");

        assertEquals("deep", JsonHelper.findFirstByPath(file, "rows[0][*].cell").orElseThrow());
    }

    /**
     * A document whose root is an array carries no key at all, so a path naming one runs to the end of it and answers nothing.
     */
    @Test
    void findFirstByPath_file_keyInADocumentRootedAtAnArray_findsNothing() throws IOException {
        assertTrue(JsonHelper.findFirstByPath(jsonFile("[1,2,3]"), "message").isEmpty());
    }

    @Test
    void findFirstByPath_truncatedObject_findsNothingOrSaysSo() throws IOException {
        var file = jsonFile("{\"a\":1");

        try {
            assertTrue(JsonHelper.findFirstByPath(file, "b").isEmpty());
        }
        catch (AIResponseException e) {
            assertTrue(e.getMessage().contains("Cannot parse JSON file"), e.getMessage());
        }
    }

    @Test
    void findFirstByPath_truncatedArray_findsNothingOrSaysSo() throws IOException {
        var file = jsonFile("{\"rows\":[{\"a\":1}");

        try {
            assertTrue(JsonHelper.findFirstByPath(file, "rows[5].a").isEmpty());
        }
        catch (AIResponseException e) {
            assertTrue(e.getMessage().contains("Cannot parse JSON file"), e.getMessage());
        }
    }

    private Path jsonFile(String json) throws IOException {
        return Files.writeString(tempDir.resolve("response.json"), json);
    }

    // =================================================================================================================
    // Strict schemas
    // =================================================================================================================

    /**
     * Several providers only honor a schema which forbids properties it does not state, and a nested object or the item type of an array is a schema of its own
     * which has to forbid them too.
     */
    @Test
    void addStrictAdditionalProperties_appliesToNestedObjectsAndArrayItems() {
        var strict = JsonHelper.addStrictAdditionalProperties(JsonHelper.parseJson("""
            {"type":"object","properties":{
              "name":{"type":"string"},
              "inner":{"type":"object","properties":{"value":{"type":"string"}}},
              "rows":{"type":"array","items":{"type":"object","properties":{"cell":{"type":"string"}}}},
              "tags":{"type":"array","items":{"type":"string"}}}}
            """));

        var properties = strict.getJsonObject("properties");
        assertFalse(strict.getBoolean("additionalProperties"));
        assertFalse(properties.getJsonObject("inner").getBoolean("additionalProperties"));
        assertFalse(properties.getJsonObject("rows").getJsonObject("items").getBoolean("additionalProperties"));
        assertFalse(properties.getJsonObject("tags").containsKey("additionalProperties"), "an array of scalars states no schema of its own");
        assertEquals("string", properties.getJsonObject("name").getString("type"));
    }

    @Test
    void addStrictAdditionalProperties_schemaWithoutProperties_isLeftWithTheFlagAlone() {
        var strict = JsonHelper.addStrictAdditionalProperties(JsonHelper.parseJson("{\"type\":\"object\"}"));

        assertFalse(strict.getBoolean("additionalProperties"));
        assertFalse(strict.containsKey("properties"));
    }

}
