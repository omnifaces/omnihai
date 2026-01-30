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

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;

import java.beans.Introspector;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Utility class for generating JSON schemas from Java records and beans, and parsing JSON back into typed objects.
 * <p>
 * This class provides bidirectional conversion between Java types and JSON:
 * <ul>
 * <li>{@link #buildJsonSchema(Class)} - generates a JSON schema from a Java type</li>
 * <li>{@link #fromJson(String, Class)} - parses JSON into a typed Java object</li>
 * </ul>
 * <p>
 * Supported types:
 * <ul>
 * <li>Records (via {@link Class#getRecordComponents()})</li>
 * <li>Beans (via {@link Introspector})</li>
 * </ul>
 * <p>
 * Supported properties:
 * <ul>
 * <li>Primitive types and their wrappers</li>
 * <li>Strings, enums, and common numeric types</li>
 * <li>Instant, LocalTime, LocalDate, LocalDateTime and ZonedDateTime</li>
 * <li>Collections and arrays</li>
 * <li>Maps (with {@code additionalProperties} for value type)</li>
 * <li>Nested complex types (recursive)</li>
 * <li>{@link Optional} fields (excluded from "required", parsed as {@code Optional.empty()} when null)</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * record ProductReview(String sentiment, int rating, List&lt;String&gt; pros, List&lt;String&gt; cons) {}
 *
 * // Generate schema for AI structured output
 * JsonObject schema = JsonSchemaHelper.buildJsonSchema(ProductReview.class);
 *
 * // Parse AI response back to typed object
 * ProductReview review = JsonSchemaHelper.fromJson(responseJson, ProductReview.class);
 * </pre>
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public final class JsonSchemaHelper {

    private static final String ERROR_INVALID_BEAN = "Cannot introspect type '%s'.";
    private static final String ERROR_INSTANTIATION = "Cannot instantiate '%s'.";
    private static final String ERROR_SET_PROPERTY = "Cannot set property '%s' on bean '%s'.";

    private static final Map<Class<?>, String> TYPE_MAPPING = new HashMap<>();
    private static final Map<Class<?>, Function<JsonValue, Object>> PARSERS = new HashMap<>();

    static {
        register(List.of(String.class, char.class, Character.class), "string", v -> ((JsonString) v).getString());
        register(List.of(boolean.class, Boolean.class), "boolean", v -> v.getValueType() == JsonValue.ValueType.TRUE);
        register(List.of(int.class, Integer.class), "integer", v -> ((JsonNumber) v).intValue());
        register(List.of(long.class, Long.class), "integer", v -> ((JsonNumber) v).longValue());
        register(List.of(short.class, Short.class), "integer", v -> (short) ((JsonNumber) v).intValue());
        register(List.of(byte.class, Byte.class), "integer", v -> (byte) ((JsonNumber) v).intValue());
        register(List.of(BigInteger.class), "integer", v -> ((JsonNumber) v).bigIntegerValue());
        register(List.of(double.class, Double.class), "number", v -> ((JsonNumber) v).doubleValue());
        register(List.of(float.class, Float.class), "number", v -> (float) ((JsonNumber) v).doubleValue());
        register(List.of(BigDecimal.class), "number", v -> ((JsonNumber) v).bigDecimalValue());
    }

    private static void register(List<Class<?>> classes, String jsonType, Function<JsonValue, Object> parser) {
        classes.forEach(c -> {
            TYPE_MAPPING.put(c, jsonType);
            PARSERS.put(c, parser);
        });
    }

    private JsonSchemaHelper() {
        throw new AssertionError();
    }

    /**
     * Generates a JSON schema for the given type.
     * <p>
     * Supported types:
     * <ul>
     * <li>Records (via {@link Class#getRecordComponents()})</li>
     * <li>Beans (via {@link Introspector})</li>
     * </ul>
     * <p>
     * Supported properties:
     * <ul>
     * <li>Primitive types and their wrappers</li>
     * <li>Strings, enums, and common numeric types</li>
     * <li>Instant, LocalTime, LocalDate, LocalDateTime and ZonedDateTime</li>
     * <li>Collections and arrays</li>
     * <li>Maps (with {@code additionalProperties} for value type)</li>
     * <li>Nested complex types (recursive)</li>
     * <li>{@link Optional} fields (excluded from "required", parsed as {@code Optional.empty()} when null)</li>
     * </ul>
     * <p>
     * Example usage:
     * <pre>
     * record ProductReview(String sentiment, int rating, List&lt;String&gt; pros, List&lt;String&gt; cons) {}
     *
     * // Generate schema for AI structured output
     * JsonObject schema = JsonSchemaHelper.buildJsonSchema(ProductReview.class);
     * </pre>
     *
     * @param type The Java class to generate a JSON schema for.
     * @return The JSON schema as a {@link JsonObject}.
     */
    public static JsonObject buildJsonSchema(Class<?> type) {
        return buildObjectSchema(type, new HashSet<>()).build();
    }

    private static JsonObjectBuilder buildObjectSchema(Class<?> clazz, Set<Class<?>> visited) {
        if (visited.contains(clazz)) {
            return Json.createObjectBuilder().add("type", "object");
        }

        visited.add(clazz);

        var properties = Json.createObjectBuilder();
        var required = Json.createArrayBuilder();

        for (var prop : getProperties(clazz)) {
            properties.add(prop.name, buildTypeSchema(prop.genericType, prop.rawType, visited));

            if (prop.rawType != Optional.class) {
                required.add(prop.name);
            }
        }

        return Json.createObjectBuilder().add("type", "object").add("properties", properties).add("required", required);
    }

    private static JsonObjectBuilder buildTypeSchema(Type genericType, Class<?> rawType, Set<Class<?>> visited) {
        if (rawType == Optional.class) {
            var innerType = getGenericArgument(genericType, 0);
            return buildTypeSchema(innerType, getRawType(innerType), visited);
        }

        if (TYPE_MAPPING.containsKey(rawType)) {
            return Json.createObjectBuilder().add("type", TYPE_MAPPING.get(rawType));
        }

        if (Temporal.class.isAssignableFrom(rawType)) {
            var dateFormat = rawType == LocalDate.class ? "date" : (rawType == LocalTime.class ? "time" : "date-time");
            return Json.createObjectBuilder().add("type", "string").add("format", dateFormat);
        }

        if (rawType.isEnum()) {
            var enumValues = Json.createArrayBuilder();
            stream(rawType.getEnumConstants()).forEach(c -> enumValues.add(((Enum<?>) c).name()));
            return Json.createObjectBuilder().add("type", "string").add("enum", enumValues);
        }

        if (rawType.isArray() || Collection.class.isAssignableFrom(rawType)) {
            var componentType = rawType.isArray() ? rawType.getComponentType() : getGenericArgument(genericType, 0);
            return Json.createObjectBuilder().add("type", "array").add("items", buildTypeSchema(componentType, getRawType(componentType), visited));
        }

        if (Map.class.isAssignableFrom(rawType)) {
            var valType = getGenericArgument(genericType, 1);
            return Json.createObjectBuilder().add("type", "object").add("additionalProperties", buildTypeSchema(valType, getRawType(valType), visited));
        }

        return buildObjectSchema(rawType, visited);
    }

    /**
     * Parses a JSON string into an instance of the specified type.
     * <p>
     * This method is the inverse of {@link #buildJsonSchema(Class)}.
     * It converts JSON that conforms to the generated schema back into a Java object.
     * <p>
     * Supported types:
     * <ul>
     * <li>Records (via {@link Class#getRecordComponents()})</li>
     * <li>Beans (via {@link Introspector})</li>
     * </ul>
     * <p>
     * Supported properties:
     * <ul>
     * <li>Primitive types and their wrappers</li>
     * <li>Strings, enums, and common numeric types</li>
     * <li>Instant, LocalTime, LocalDate, LocalDateTime and ZonedDateTime</li>
     * <li>Collections and arrays</li>
     * <li>Maps (with {@code additionalProperties} for value type)</li>
     * <li>Nested complex types (recursive)</li>
     * <li>{@link Optional} fields (excluded from "required", parsed as {@code Optional.empty()} when null)</li>
     * </ul>
     * <p>
     * Example usage:
     *
     * <pre>
     * record ProductReview(String sentiment, int rating, List&lt;String&gt; pros, List&lt;String&gt; cons) {}
     *
     * String json = "{\"sentiment\":\"positive\",\"rating\":5,\"pros\":[\"great\"],\"cons\":[]}";
     * ProductReview review = JsonSchemaHelper.fromJson(json, ProductReview.class);
     * </pre>
     *
     * @param <T>  The target type.
     * @param json The JSON string to parse.
     * @param type The target class.
     * @return An instance of the target type populated from the JSON.
     * @throws IllegalArgumentException If the JSON cannot be parsed or the type cannot be instantiated.
     */
    public static <T> T fromJson(String json, Class<T> type) {
        return parseValue(parseJson(json), type, type);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <T> T parseValue(JsonValue value, Class<T> rawType, Type genericType) {
        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            return (rawType == Optional.class) ? (T) Optional.empty() : (T) getDefaultPrimitiveValue(rawType);
        }

        if (rawType == Optional.class) {
            var inner = getGenericArgument(genericType, 0);
            return (T) Optional.ofNullable(parseValue(value, getRawType(inner), inner));
        }

        if (PARSERS.containsKey(rawType)) {
            return (T) PARSERS.get(rawType).apply(value);
        }

        if (rawType == char.class || rawType == Character.class) {
            return (T) (Character) ((JsonString) value).getString().charAt(0);
        }

        if (rawType.isEnum()) {
            return (T) Enum.valueOf((Class<Enum>) rawType, ((JsonString) value).getString());
        }

        if (Temporal.class.isAssignableFrom(rawType)) {
            var text = ((JsonString) value).getString();

            if (rawType == Instant.class) {
                return (T) Instant.parse(text);
            }

            if (rawType == LocalDate.class) {
                return (T) LocalDate.parse(text);
            }

            if (rawType == LocalTime.class) {
                return (T) LocalTime.parse(text);
            }

            if (rawType == LocalDateTime.class) {
                return (T) LocalDateTime.parse(text);
            }

            return (T) ZonedDateTime.parse(text);
        }

        if (rawType.isArray()) {
            return (T) parseArray((JsonArray) value, rawType.getComponentType());
        }

        if (Collection.class.isAssignableFrom(rawType)) {
            return (T) parseCollection((JsonArray) value, rawType, genericType);
        }

        if (Map.class.isAssignableFrom(rawType)) {
            return (T) parseMap((JsonObject) value, rawType, genericType);
        }

        return (T) parseObject((JsonObject) value, rawType);
    }

    private static Object parseObject(JsonObject json, Class<?> rawType) {
        var properties = getProperties(rawType);
        Object bean;

        try {
            if (rawType.isRecord()) {
                var components = rawType.getRecordComponents();
                var args = new Object[components.length];

                for (int i = 0; i < components.length; i++) {
                    args[i] = parseValue(json.get(components[i].getName()), components[i].getType(), components[i].getGenericType());
                }

                return rawType.getDeclaredConstructor(stream(components).map(RecordComponent::getType).toArray(Class[]::new)).newInstance(args);
            }

            bean = rawType.getDeclaredConstructor().newInstance();
        }
        catch (Exception e) {
            throw new IllegalArgumentException(format(ERROR_INSTANTIATION, rawType.getName()), e);
        }

        for (var property : properties) {
            if (property.writeMethod != null && json.containsKey(property.name)) {
                try {
                    property.writeMethod.invoke(bean, parseValue(json.get(property.name), property.rawType, property.genericType));
                }
                catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalArgumentException(format(ERROR_SET_PROPERTY, property.name, rawType), e);
                }
            }
        }

        return bean;
    }

    private record Property(String name, Class<?> rawType, Type genericType, Method writeMethod) {}

    private static List<Property> getProperties(Class<?> clazz) {
        if (clazz.isRecord()) {
            return stream(clazz.getRecordComponents()).map(c -> new Property(c.getName(), c.getType(), c.getGenericType(), null)).toList();
        }

        try {
            return stream(Introspector.getBeanInfo(clazz).getPropertyDescriptors())
                    .filter(descriptor -> descriptor.getReadMethod() != null && !"class".equals(descriptor.getName()))
                    .map(descriptor -> new Property(descriptor.getName(), descriptor.getPropertyType(), descriptor.getReadMethod().getGenericReturnType(), descriptor.getWriteMethod()))
                    .toList();
        }
        catch (Exception e) {
            throw new IllegalArgumentException(format(ERROR_INVALID_BEAN, clazz.getName()), e);
        }
    }

    private static Type getGenericArgument(Type genericType, int index) {
        if (genericType instanceof ParameterizedType type && type.getActualTypeArguments().length > index) {
            return type.getActualTypeArguments()[index];
        }

        return Object.class;
    }

    private static Class<?> getRawType(Type genericType) {
        if (genericType instanceof Class<?> clazz) {
            return clazz;
        }

        if (genericType instanceof GenericArrayType genericArray) {
            return Array.newInstance(getRawType(genericArray.getGenericComponentType()), 0).getClass();
        }

        return Object.class;
    }

    private static Object getDefaultPrimitiveValue(Class<?> rawType) {
        if (rawType == boolean.class) {
            return false;
        }

        if (rawType.isPrimitive()) {
            return (rawType == char.class) ? '\0' : 0;
        }

        return null;
    }

    private static Object parseArray(JsonArray json, Class<?> componentType) {
        var array = Array.newInstance(componentType, json.size());

        for (int i = 0; i < json.size(); i++) {
            Array.set(array, i, parseValue(json.get(i), componentType, componentType));
        }

        return array;
    }

    private static Collection<?> parseCollection(JsonArray json, Class<?> rawType, Type genericType) {
        var collection = Set.class.isAssignableFrom(rawType) ? (rawType == TreeSet.class ? new TreeSet<>() : new LinkedHashSet<>()) : new ArrayList<>();
        var itemType = getGenericArgument(genericType, 0);
        json.stream().forEach(item -> collection.add(parseValue(item, getRawType(itemType), itemType)));
        return collection;
    }

    private static Map<?, ?> parseMap(JsonObject json, Class<?> rawType, Type genericType) {
        var map = (rawType == TreeMap.class) ? new TreeMap<>() : new LinkedHashMap<>();
        var valueType = getGenericArgument(genericType, 1);
        json.entrySet().stream().forEach(entry -> map.put(entry.getKey(), parseValue(entry.getValue(), getRawType(valueType), valueType)));
        return map;
    }
}