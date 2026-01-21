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

import static org.omnifaces.ai.helper.TextHelper.isBlank;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.omnifaces.ai.exception.AIApiResponseException;

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
     */
    public static boolean isEmpty(JsonValue value) {
        if (value == null) {
            return true;
        }
        else if (value instanceof JsonObject object) {
            return object.isEmpty();
        }
        else if (value instanceof JsonArray array) {
            return array.isEmpty();
        }
        else if (value instanceof JsonString string) {
            return isBlank(string.getString());
        }
        else {
            throw new UnsupportedOperationException("Not implemented yet, just add a new else-if block here");
        }
    }

    /**
     * Parses given string to a {@link JsonObject}.
     *
     * @param json The JSON string to parse.
     * @return The parsed JSON object.
     * @throws AIApiResponseException If the string cannot be parsed as JSON.
     */
    public static JsonObject parseJson(String json) throws AIApiResponseException {
        var sanitizedJson = json.substring(json.indexOf('{'), json.lastIndexOf('}') + 1); // Some chat APIs stubbornly put JSON in markdown formatting like ```json\n{...}\n``` when asking for JSON-only output.

        try (var reader = Json.createReader(new StringReader(sanitizedJson))) {
            return reader.readObject();
        }
        catch (Exception e) {
            throw new AIApiResponseException("Cannot parse json", json, e);
        }
    }

    /**
     * Extracts the first non-blank string value from a JSON object found at the given dot-separated path.
     * <p>
     * Supports array indexing with bracket notation, e.g. {@code "choices[0].message.content"}.
     * Also supports wildcard array indexes, e.g. {@code "output[*].content[*].text"}.
     *
     * @param root JSON root value (usually a {@link JsonObject})
     * @param path dot-separated path, may contain {@code [index]} or {@code [*]} segments
     * @return first non-blank stripped string value, or {@code null} if no value was found
     */
    public static String extractByPath(JsonObject root, String path) {
        var values = extractAllByPath(root, path);
        return values.isEmpty() ? null : values.get(0);
    }

    private static List<String> extractAllByPath(JsonValue root, String path) {
        if (root == null || path == null) {
            return List.of();
        }

        var currentNodes = List.of(root);

        for (var segment : path.split("\\.")) {
            var nextNodes = new ArrayList<JsonValue>();

            for (var node : currentNodes) {
                collectNextLevelValues(node, segment, nextNodes);
            }

            if (nextNodes.isEmpty()) {
                return List.of();
            }

            currentNodes = nextNodes;
        }

        var result = new ArrayList<String>();

        for (var terminal : currentNodes) {
            if (terminal == null) {
                return null;
            }

            var string = terminal instanceof JsonString jsonString ? jsonString.getString() : terminal.toString();

            if (!isBlank(string)) {
                result.add(string.strip());
            }
        }

        return result;
    }

    private static void collectNextLevelValues(JsonValue current, String segment, List<JsonValue> collector) {
        if (current == null) {
            return;
        }

        if (!(current instanceof JsonObject || current instanceof JsonArray)) {
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
            for (var item : array) {
                collector.add(item);
            }
        } else {
            int index = Integer.parseInt(indexPart);

            if (index < array.size()) {
                collector.add(array.get(index));
            }
        }
    }
}
