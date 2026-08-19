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

import static jakarta.json.stream.JsonParser.Event.END_ARRAY;
import static jakarta.json.stream.JsonParser.Event.END_OBJECT;
import static jakarta.json.stream.JsonParser.Event.KEY_NAME;
import static jakarta.json.stream.JsonParser.Event.START_ARRAY;
import static jakarta.json.stream.JsonParser.Event.START_OBJECT;
import static jakarta.json.stream.JsonParser.Event.VALUE_STRING;
import static java.lang.Math.min;
import static java.nio.file.Files.size;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toCollection;
import static org.omnifaces.ai.helper.FileHelper.newOffsetInputStream;
import static org.omnifaces.ai.helper.TextHelper.isBlank;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Function;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;

import org.omnifaces.ai.exception.AIResponseException;

/**
 * Utility class for JSON operations.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public final class JsonHelper {

    private static final int FILE_PROBE_MAX_BUFFER_LENGTH = 1024;

    private JsonHelper() {
        throw new AssertionError();
    }

    // In-memory -------------------------------------------------------------------------------------------------------

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
            // Some chat APIs stubbornly put JSON in markdown formatting like ```json\n{...}\n``` when asking for JSON-only output.
            var sanitizedJson = json.substring(json.indexOf('{'), json.lastIndexOf('}') + 1);

            try (var reader = Json.createReader(new StringReader(sanitizedJson))) {
                return reader.readObject();
            }
        }
        catch (Exception e) {
            throw new AIResponseException("Cannot parse json", json, e);
        }
    }

    /**
     * Checks for error messages at the specified paths.
     * <p>
     * If an error message is found at any of the paths, an {@link AIResponseException} is thrown.
     *
     * @param responseJson The API response JSON.
     * @param errorMessagePaths Paths to check for error messages (e.g., {@code "error.message"}, {@code "error"}).
     * @throws AIResponseException If parsing fails or an error message is found.
     * @since 1.3
     */
    public static void checkErrors(JsonObject responseJson, List<String> errorMessagePaths) throws AIResponseException {
        findFirstNonBlankByPaths(responseJson, errorMessagePaths).ifPresent(errorMessage -> {
            throw new AIResponseException(errorMessage, responseJson);
        });
    }

    /**
     * Finds all string values from a JSON object found at the given dot-separated path.
     * <p>
     * Supports array indexing with bracket notation, e.g. {@code "choices[0].message.content"}. Also supports wildcard array indexes, e.g.
     * {@code "output[*].content[*].text"}.
     *
     * @param root JSON root value (usually a {@link JsonObject})
     * @param path dot-separated path, may contain {@code [index]} or {@code [*]} segments
     * @return a {@link List} all string values, or empty if none found
     */
    public static List<String> findAllByPath(JsonValue root, String path) {
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

    /**
     * Finds the string value from a JSON object found at the given dot-separated path.
     * <p>
     * Supports array indexing with bracket notation, e.g. {@code "choices[0].message.content"}. Also supports wildcard array indexes, e.g.
     * {@code "output[*].content[*].text"}.
     *
     * @param root JSON root value (usually a {@link JsonObject})
     * @param path dot-separated path, may contain {@code [index]} or {@code [*]} segments
     * @return an {@link Optional} containing the string value, or empty if not found
     * @deprecated Since 1.7. Use {@link #findFirstByPath(JsonObject, String)} instead, which names which of the matches it returns.
     */
    @Deprecated(since = "1.7", forRemoval = true)
    public static Optional<String> findByPath(JsonObject root, String path) {
        return findFirstByPath(root, path);
    }

    /**
     * Finds the string value from a JSON object found at the given dot-separated path.
     * <p>
     * Supports array indexing with bracket notation, e.g. {@code "choices[0].message.content"}. Also supports wildcard array indexes, e.g.
     * {@code "output[*].content[*].text"}.
     *
     * @param root JSON root value (usually a {@link JsonObject})
     * @param path dot-separated path, may contain {@code [index]} or {@code [*]} segments
     * @return an {@link Optional} containing the string value, or empty if not found
     * @since 1.7
     */
    public static Optional<String> findFirstByPath(JsonObject root, String path) {
        return findAllByPath(root, path).stream().findFirst();
    }

    /**
     * Finds the non-blank string value from a JSON object found at the given dot-separated path.
     * <p>
     * Supports array indexing with bracket notation, e.g. {@code "choices[0].message.content"}. Also supports wildcard array indexes, e.g.
     * {@code "output[*].content[*].text"}.
     *
     * @param root JSON root value (usually a {@link JsonObject})
     * @param path dot-separated path, may contain {@code [index]} or {@code [*]} segments
     * @return an {@link Optional} containing the non-blank string value, or empty if not found
     * @since 1.1
     * @deprecated Since 1.7. Use {@link #findFirstNonBlankByPath(JsonObject, String)} instead, which names which of the matches it returns.
     */
    @Deprecated(since = "1.7", forRemoval = true)
    public static Optional<String> findNonBlankByPath(JsonObject root, String path) {
        return findFirstNonBlankByPath(root, path);
    }

    /**
     * Finds the non-blank string value from a JSON object found at the given dot-separated path.
     * <p>
     * Supports array indexing with bracket notation, e.g. {@code "choices[0].message.content"}. Also supports wildcard array indexes, e.g.
     * {@code "output[*].content[*].text"}.
     *
     * @param root JSON root value (usually a {@link JsonObject})
     * @param path dot-separated path, may contain {@code [index]} or {@code [*]} segments
     * @return an {@link Optional} containing the non-blank string value, or empty if not found
     * @since 1.7
     */
    public static Optional<String> findFirstNonBlankByPath(JsonObject root, String path) {
        return findFirstByPath(root, path).map(TextHelper::stripToNull).filter(Objects::nonNull);
    }

    /**
     * Finds the first non-blank string value from a JSON object by trying multiple paths in order.
     * <p>
     * This is useful when different API versions or providers return data at different paths.
     *
     * @param root JSON root object to search.
     * @param paths list of dot-separated paths to try, in order of preference.
     * @return an {@link Optional} containing the first non-blank value found, or empty if no path matches.
     * @see #findFirstByPath(JsonObject, String)
     */
    public static Optional<String> findFirstNonBlankByPaths(JsonObject root, List<String> paths) {
        return paths.stream().map(path -> findFirstNonBlankByPath(root, path)).flatMap(Optional::stream).findFirst();
    }

    /**
     * Finds the last non-blank string value from a JSON object at the given dot-separated path, which is the one to have where a wildcard path matches a
     * sequence whose last entry is the relevant one, such as the message items of an agentic turn.
     * <p>
     * Supports array indexing with bracket notation, e.g. {@code "choices[0].message.content"}. Also supports wildcard array indexes, e.g.
     * {@code "output[*].content[*].text"}.
     *
     * @param root JSON root value (usually a {@link JsonObject})
     * @param path dot-separated path, may contain {@code [index]} or {@code [*]} segments
     * @return an {@link Optional} containing the last non-blank string value, or empty if not found
     * @since 1.7
     * @see #findFirstNonBlankByPath(JsonObject, String)
     */
    public static Optional<String> findLastNonBlankByPath(JsonObject root, String path) {
        return findAllByPath(root, path).stream().map(TextHelper::stripToNull).filter(Objects::nonNull).reduce((first, last) -> last);
    }

    /**
     * Finds the last non-blank string value from a JSON object at the first of the given paths which matches at all.
     * <p>
     * The paths are tried in order of preference exactly as {@link #findFirstNonBlankByPaths(JsonObject, List)} does; it is within the matching path that the
     * last value is taken rather than the first, which is the one to have where a wildcard path matches a sequence whose last entry is the relevant one.
     *
     * @param root JSON root object to search.
     * @param paths list of dot-separated paths to try, in order of preference.
     * @return an {@link Optional} containing the last non-blank value at the first matching path, or empty if no path matches.
     * @since 1.7
     * @see #findLastNonBlankByPath(JsonObject, String)
     */
    public static Optional<String> findLastNonBlankByPaths(JsonObject root, List<String> paths) {
        return paths.stream().map(path -> findLastNonBlankByPath(root, path)).flatMap(Optional::stream).findFirst();
    }

    /**
     * Recursively adds {@code "additionalProperties": false} to all object-type schemas in the given JSON schema.
     * <p>
     * This is required by some AI providers (e.g., OpenAI, Anthropic) to enforce strict schema validation, ensuring the model only returns the specified
     * properties.
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
                else if (
                    value instanceof JsonObject object && "array".equals(object.getString("type", null))
                        && object.containsKey("items") && "object".equals(object.getJsonObject("items").getString("type", null))
                ) {
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

    // Streaming -------------------------------------------------------------------------------------------------------

    /**
     * Checks for error messages at the specified paths.
     * <p>
     * If an error message is found at any of the paths, an {@link AIResponseException} is thrown.
     *
     * @param jsonFile The JSON file to read from, must not be {@code null}.
     * @param errorMessagePaths Paths to check for error messages (e.g., {@code "error.message"}, {@code "error"}).
     * @throws AIResponseException If parsing fails or an error message is found.
     * @since 1.3
     */
    public static void checkErrors(Path jsonFile, List<String> errorMessagePaths) throws AIResponseException {
        errorMessagePaths.stream().map(path -> findFirstByPath(jsonFile, path).map(TextHelper::stripToNull).orElse(null)).filter(Objects::nonNull).findFirst()
            .ifPresent(errorMessage -> {
                throw new AIResponseException(errorMessage, jsonFile);
            });
    }

    /**
     * Finds the string value from a JSON file at the given dot-separated path within the file at {@code source}, without loading the whole file into memory.
     * <p>
     * Supports array indexing with bracket notation, e.g. {@code "choices[0].message.content"}. Also supports wildcard array indexes, e.g.
     * {@code "output[*].content[*].text"}.
     *
     * @param jsonFile The JSON file to read from, must not be {@code null}.
     * @param path dot-separated path, may contain {@code [index]} or {@code [*]} segments
     * @return an {@link Optional} containing the string value, or empty if not found
     * @throws AIResponseException If the file cannot be read or parsed.
     * @since 1.3
     * @deprecated Since 1.7. Use {@link #findFirstByPath(Path, String)} instead, which names which of the matches it returns.
     */
    @Deprecated(since = "1.7", forRemoval = true)
    public static Optional<String> findByPath(Path jsonFile, String path) {
        return findFirstByPath(jsonFile, path);
    }

    /**
     * Finds the string value from a JSON file at the given dot-separated path within the file at {@code source}, without loading the whole file into memory.
     * <p>
     * Supports array indexing with bracket notation, e.g. {@code "choices[0].message.content"}. Also supports wildcard array indexes, e.g.
     * {@code "output[*].content[*].text"}.
     *
     * @param jsonFile The JSON file to read from, must not be {@code null}.
     * @param path dot-separated path, may contain {@code [index]} or {@code [*]} segments
     * @return an {@link Optional} containing the string value, or empty if not found
     * @throws AIResponseException If the file cannot be read or parsed.
     * @since 1.3
     */
    public static Optional<String> findFirstByPath(Path jsonFile, String path) {
        return ofNullable(findFirstByPath(jsonFile, path, parser -> {
            parser.next();
            return parser.getString();
        }));
    }

    /**
     * Opens an {@link InputStream} over the raw bytes of the JSON value found at the given dot-separated path within the file at {@code source}, without
     * loading the whole file into memory.
     * <p>
     * Supports array indexing with bracket notation, e.g. {@code "choices[0].message.content"}. Also supports wildcard array indexes, e.g.
     * {@code "output[*].content[*].text"}. For string values, the surrounding double-quotes are stripped so the caller receives only the value content. The
     * caller is responsible for closing the returned stream.
     *
     * @param jsonFile The JSON file to read from, must not be {@code null}.
     * @param path dot-separated path, may contain {@code [index]} or {@code [*]} segments
     * @return An {@link InputStream} over the raw bytes of the located value, or {@code null} if the path does not match.
     * @throws AIResponseException If the file cannot be read or parsed.
     * @since 1.3
     */
    public static InputStream streamByPath(Path jsonFile, String path) {
        return findFirstByPath(jsonFile, path, parser -> {
            long startOffset = parser.getLocation().getStreamOffset(); // after key
            var event = parser.next();
            long endOffset = parser.getLocation().getStreamOffset(); // after value

            try {
                try (var tempStream = newOffsetInputStream(jsonFile, startOffset, min(size(jsonFile), startOffset + FILE_PROBE_MAX_BUFFER_LENGTH))) {
                    int ch;

                    while ((ch = tempStream.read()) != -1) {
                        if (ch == ':' || Character.isWhitespace(ch)) {
                            startOffset++; // skip any semicolon and whitespace between key and value
                        }
                        else {
                            break;
                        }
                    }
                }

                if (event == VALUE_STRING) {
                    startOffset++; // doublequote before string value
                    endOffset--; // doublequote after string value
                }

                return newOffsetInputStream(jsonFile, startOffset, endOffset);
            }
            catch (IOException e) {
                throw new AIResponseException("Cannot read JSON file", jsonFile, e);
            }
        });
    }

    // Internal --------------------------------------------------------------------------------------------------------

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

    private static <R> R findFirstByPath(Path jsonFile, String path, Function<JsonParser, R> processor) {
        try (var inputStream = new FileInputStream(jsonFile.toFile())) {
            try (var jsonStream = Json.createParser(inputStream)) {
                var segments = stream(path.split("[\\.\\[\\]]")).filter(not(String::isBlank)).collect(toCollection(LinkedList::new));
                return findFirstByPath(jsonStream, segments, processor);
            }
        }
        catch (AIResponseException e) {
            throw e;
        }
        catch (Exception e) {
            throw new AIResponseException("Cannot parse JSON file", jsonFile, e);
        }
    }

    private static <R> R findFirstByPath(JsonParser parser, Queue<String> segments, Function<JsonParser, R> processor) {
        if (segments.isEmpty()) {
            return null;
        }

        var segment = segments.poll();
        var isWildcard = "*".equals(segment);
        var isNumeric = !isWildcard && segment.matches("\\d+");
        int targetIndex = isNumeric ? Integer.parseInt(segment) : -1;
        int currentIndex = -1;

        while (parser.hasNext()) {
            var event = parser.next();
            if (isNumeric || isWildcard) {
                if (event == START_OBJECT || event == START_ARRAY) {
                    currentIndex++;

                    if (isWildcard || currentIndex == targetIndex) {
                        if (segments.isEmpty()) {
                            throw new IllegalArgumentException("Path must point to a value, not to an object or array.");
                        }

                        var result = findFirstByPath(parser, isWildcard ? new LinkedList<>(segments) : segments, processor);

                        if (result != null || !isWildcard) {
                            return result;
                        }
                    }
                    else if (event == START_OBJECT) {
                        parser.skipObject();
                    }
                    else {
                        parser.skipArray();
                    }
                }
                else if (event == END_ARRAY) {
                    return null;
                }
            }
            else if (event == KEY_NAME && segment.equals(parser.getString())) {
                if (segments.isEmpty()) {
                    return processor.apply(parser);
                }

                return findFirstByPath(parser, segments, processor);
            }
            else if (event == END_OBJECT) {
                return null;
            }
        }

        return null;
    }

}
