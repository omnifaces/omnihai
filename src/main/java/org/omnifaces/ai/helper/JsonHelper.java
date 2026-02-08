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

import static java.util.Collections.emptyList;
import static java.util.function.Predicate.not;
import static org.omnifaces.ai.helper.TextHelper.isBlank;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.omnifaces.ai.exception.AIResponseException;

/**
 * Utility class for JSON operations.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public final class JsonHelper {

    private JsonHelper() {
        throw new AssertionError();
    }

    /**
     * Returns whether the given JSON value is empty. Supports currently only {@link JsonObject}, {@link JsonArray} and {@link JsonString}.
     *
     * @param value JSON value to check.
     * @return Whether the given JSON value is empty.
     * @throws UnsupportedOperationException If the value type is not yet supported.
     */
    public static boolean isEmpty(JsonValue value) {
        if (value == null) {
            return true;
        }
        if (value instanceof JsonObject object) {
            return object.isEmpty();
        }
        if (value instanceof JsonArray array) {
            return array.isEmpty();
        }
        if (value instanceof JsonString string) {
            return isBlank(string.getString());
        }
        throw new UnsupportedOperationException("Unsupported type " + value.getClass());
    }

    /**
     * Parses given string to a {@link JsonObject}.
     *
     * @param json The JSON string to parse.
     * @return The parsed JSON object.
     * @throws AIResponseException If the string cannot be parsed as JSON.
     */
    public static JsonObject parseJson(String json) throws AIResponseException {
        try {
            var sanitizedJson = json.substring(json.indexOf('{'), json.lastIndexOf('}') + 1); // Some chat APIs stubbornly put JSON in markdown formatting like ```json\n{...}\n``` when asking for JSON-only output.

            try (var reader = Json.createReader(new StringReader(sanitizedJson))) {
                return reader.readObject();
            }
        }
        catch (Exception e) {
            throw new AIResponseException("Cannot parse json", json, e);
        }
    }

    /**
     * Parses the response body as JSON and checks for error messages at the specified paths.
     * <p>
     * If an error message is found at any of the paths, an {@link AIResponseException} is thrown.
     *
     * @param responseBody The API response body to parse.
     * @param errorMessagePaths Paths to check for error messages (e.g., {@code "error.message"}, {@code "error"}).
     * @return The parsed JSON object if no errors were found.
     * @throws AIResponseException If parsing fails or an error message is found.
     */
    public static JsonObject parseAndCheckErrors(String responseBody, List<String> errorMessagePaths) throws AIResponseException {
        var responseJson = parseJson(responseBody);
        findFirstNonBlankByPath(responseJson, errorMessagePaths).ifPresent(errorMessage -> {
            throw new AIResponseException(errorMessage, responseBody);
        });
        return responseJson;
    }

    /**
     * Finds the first non-empty string value from a JSON object found at the given dot-separated path.
     * <p>
     * Supports array indexing with bracket notation, e.g. {@code "choices[0].message.content"}.
     * Also supports wildcard array indexes, e.g. {@code "output[*].content[*].text"}.
     *
     * @param root JSON root value (usually a {@link JsonObject})
     * @param path dot-separated path, may contain {@code [index]} or {@code [*]} segments
     * @return an {@link Optional} containing the first non-empty string value, or empty if not found
     */
    public static Optional<String> findByPath(JsonObject root, String path) {
        var values = findAllByPath(root, path);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    /**
     * Finds the first non-blank string value from a JSON object by trying multiple paths in order.
     * <p>
     * This is useful when different API versions or providers return data at different paths.
     *
     * @param root JSON root object to search.
     * @param paths list of dot-separated paths to try, in order of preference.
     * @return an {@link Optional} containing the first non-blank value found, or empty if no path matches.
     * @see #findByPath(JsonObject, String)
     */
    public static Optional<String> findFirstNonBlankByPath(JsonObject root, List<String> paths) {
        for (var path : paths) {
            var value = findByPath(root, path).map(String::strip);

            if (value.filter(not(String::isEmpty)).isPresent()) {
                return value;
            }
        }

        return Optional.empty();
    }

    private static List<String> findAllByPath(JsonValue root, String path) {
        if (root == null || path == null) {
            return emptyList();
        }

        var currentNodes = List.of(root);

        for (var segment : path.split("\\.")) {
            var nextNodes = new ArrayList<JsonValue>();

            for (var node : currentNodes) {
                collectNextLevelValues(node, segment, nextNodes);
            }

            if (nextNodes.isEmpty()) {
                return emptyList();
            }

            currentNodes = nextNodes;
        }

        var result = new ArrayList<String>();

        for (var terminal : currentNodes) {
            if (terminal == null) {
                return emptyList();
            }

            var string = terminal instanceof JsonString jsonString ? jsonString.getString() : terminal.toString();

            if (!string.isEmpty()) { // Do not use isBlank! Whitespace can be significant.
                result.add(string);
            }
        }

        return result;
    }

    private static void collectNextLevelValues(JsonValue current, String segment, List<JsonValue> collector) {
        if ((current == null) || (!(current instanceof JsonObject) && !(current instanceof JsonArray))) {
            return;
        }

        int startBracket = segment.indexOf('[');

        if (startBracket < 0) {
            if (current instanceof JsonObject obj && obj.containsKey(segment) && !obj.isNull(segment)) {
                collector.add(obj.get(segment));
            }

            return;
        }

        var propertyName = segment.substring(0, startBracket);
        var indexPart = segment.substring(startBracket + 1, segment.indexOf(']', startBracket));

        if (!(current instanceof JsonObject object) || !object.containsKey(propertyName) || object.isNull(propertyName)) {
            return;
        }

        var array = object.getJsonArray(propertyName);

        if (array == null) {
            return;
        }

        if ("*".equals(indexPart)) {
            collector.addAll(array);
        }
        else {
            int index = Integer.parseInt(indexPart);

            if (index < array.size()) {
                collector.add(array.get(index));
            }
        }
    }

    /**
     * Recursively adds {@code "additionalProperties": false} to all object-type schemas in the given JSON schema.
     * <p>
     * This is required by some AI providers (e.g., OpenAI, Anthropic) to enforce strict schema validation,
     * ensuring the model only returns the specified properties.
     *
     * @param schema The JSON schema object to transform.
     * @return A new JSON schema with {@code additionalProperties: false} added to all object schemas.
     */
    public static JsonObject addStrictAdditionalProperties(JsonObject schema) {
        var builder = Json.createObjectBuilder(schema).add("additionalProperties", false);

        if (schema.containsKey("properties")) {
            var properties = Json.createObjectBuilder();

            schema.getJsonObject("properties").forEach((key, value) -> {
                if (value instanceof JsonObject object && "object".equals(object.getString("type", null))) {
                    properties.add(key, addStrictAdditionalProperties(object));
                }
                else if (value instanceof JsonObject object && "array".equals(object.getString("type", null))
                        && object.containsKey("items") && "object".equals(object.getJsonObject("items").getString("type", null))) {
                    properties.add(key, Json.createObjectBuilder(object).add("items", addStrictAdditionalProperties(object.getJsonObject("items"))));
                }
                else {
                    properties.add(key, value);
                }
            });

            builder.add("properties", properties);
        }

        return builder.build();
    }

    /**
     * Creates a new {@link JsonObjectBuilder} from the given object with one field replaced.
     * <p>
     * All entries from the original object are copied, with the specified field's value replaced by the new value.
     *
     * @param object The source JSON object.
     * @param field The field name to replace.
     * @param newValue The new value for the field.
     * @return A {@link JsonObjectBuilder} containing all entries with the specified field replaced.
     */
    public static JsonObjectBuilder replaceField(JsonObject object, String field, JsonValue newValue) {
        var builder = Json.createObjectBuilder();

        for (var entry : object.entrySet()) {
            builder.add(entry.getKey(), entry.getKey().equals(field) ? newValue : entry.getValue());
        }

        return builder;
    }
}
