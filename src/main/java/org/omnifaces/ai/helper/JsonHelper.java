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
        var sanitizedJson = json.substring(json.indexOf('{'), json.lastIndexOf('}') + 1); // Some APIs stubbornly put JSON in markdown formatting like ```json\n{...}\n```.

        try (var reader = Json.createReader(new StringReader(sanitizedJson))) {
            return reader.readObject();
        }
        catch (Exception e) {
            throw new AIApiResponseException("Cannot parse json " + json, e);
        }
    }

    /**
     * Extracts a string value from a JSON object using a dot-separated path.
     * <p>
     * Supports array indexing with bracket notation, e.g. {@code "choices[0].message.content"}.
     *
     * @param root The JSON object to extract from.
     * @param path The dot-separated path to the value, with optional array indices in brackets.
     * @return The stripped string value at the path, or {@code null} if the path doesn't exist, is null, or is empty.
     */
    public static String extractByPath(JsonObject root, String path) {
        JsonValue current = root;

        for (var segment : path.split("\\.")) {
            if (!(current instanceof JsonObject || current instanceof JsonArray)) {
                return null;
            }

            var startBracket = segment.indexOf('[');

            if (startBracket >= 0) {
                var key = segment.substring(0, startBracket);
                var endBracket = segment.indexOf(']', startBracket);
                var index = Integer.parseInt(segment.substring(startBracket + 1, endBracket));
                var jsonObject = current.asJsonObject();

                if (!jsonObject.containsKey(key) || jsonObject.isNull(key)) {
                    return null;
                }

                var array = jsonObject.getJsonArray(key);

                if (array == null || index < 0 || index >= array.size()) {
                    return null;
                }

                current = array.get(index);
            }
            else {
                var jsonObject = current.asJsonObject();

                if (!jsonObject.containsKey(segment) || jsonObject.isNull(segment)) {
                    return null;
                }

                current = jsonObject.get(segment);
            }
        }

        if (current == null) {
            return null;
        }

        var string = current instanceof JsonString jsonString ? jsonString.getString() : current.toString();
        return isBlank(string) ? null : string.strip();
    }
}
