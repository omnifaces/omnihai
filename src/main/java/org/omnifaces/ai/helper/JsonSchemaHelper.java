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

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * Utility class for generating JSON schemas from Java records and beans.
 * <p>
 * This class introspects Java types and generates corresponding JSON schemas following the JSON Schema specification.
 * It supports:
 * <ul>
 * <li>Records (via {@link Class#getRecordComponents()})</li>
 * <li>Beans (via {@link Introspector})</li>
 * <li>Primitive types and their wrappers</li>
 * <li>Strings, enums, and common numeric types</li>
 * <li>Collections and arrays</li>
 * <li>Maps (with {@code additionalProperties} for value type)</li>
 * <li>Nested complex types (recursive)</li>
 * <li>{@link Optional} fields (excluded from "required")</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * record ProductReview(String sentiment, int rating, List&lt;String&gt; pros, List&lt;String&gt; cons) {}
 *
 * JsonObject schema = JsonSchemaHelper.buildJsonSchema(ProductReview.class);
 * </pre>
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public final class JsonSchemaHelper {

    private static final String ERROR_INVALID_BEAN = "Cannot introspect type '%s' as bean.";

    private JsonSchemaHelper() {
        throw new AssertionError();
    }

    /**
     * Builds a JSON schema for the given type.
     *
     * @param type The Java class to generate a JSON schema for.
     * @return The JSON schema as a {@link JsonObject}.
     */
    public static JsonObject buildJsonSchema(Class<?> type) {
        return buildObjectSchema(type, new HashSet<>());
    }

    private static JsonObject buildObjectSchema(Class<?> clazz, Set<Class<?>> visited) {
        if (visited.contains(clazz)) {
            return Json.createObjectBuilder().add("type", "object").build();
        }

        visited.add(clazz);

        var properties = Json.createObjectBuilder();
        var required = Json.createArrayBuilder();

        if (clazz.isRecord()) {
            for (var component : clazz.getRecordComponents()) {
                addRecordComponent(component, properties, required, visited);
            }
        }
        else {
            addBeanProperties(clazz, properties, required, visited);
        }

        return Json.createObjectBuilder()
            .add("type", "object")
            .add("properties", properties)
            .add("required", required)
            .build();
    }

    private static void addRecordComponent(RecordComponent component, JsonObjectBuilder properties, JsonArrayBuilder required, Set<Class<?>> visited) {
        var name = component.getName();
        var genericType = component.getGenericType();
        var rawType = component.getType();

        properties.add(name, buildTypeSchema(genericType, rawType, visited));

        if (!isOptional(rawType)) {
            required.add(name);
        }
    }

    private static void addBeanProperties(Class<?> clazz, JsonObjectBuilder properties, JsonArrayBuilder required, Set<Class<?>> visited) {
        PropertyDescriptor[] descriptors;

        try {
            descriptors = Introspector.getBeanInfo(clazz).getPropertyDescriptors();
        }
        catch (IntrospectionException e) {
            throw new IllegalArgumentException(format(ERROR_INVALID_BEAN, clazz), e);
        }

        for (var descriptor : descriptors) {
            if (descriptor.getReadMethod() == null || "class".equals(descriptor.getName())) {
                continue;
            }

            var name = descriptor.getName();
            var genericType = descriptor.getReadMethod().getGenericReturnType();
            var rawType = descriptor.getPropertyType();

            properties.add(name, buildTypeSchema(genericType, rawType, visited));

            if (!isOptional(rawType)) {
                required.add(name);
            }
        }
    }

    private static JsonObjectBuilder buildTypeSchema(Type genericType, Class<?> rawType, Set<Class<?>> visited) {
        if (isOptional(rawType) && genericType instanceof ParameterizedType parameterized) {
            var typeArg = parameterized.getActualTypeArguments()[0];
            var innerRaw = typeArg instanceof Class<?> c ? c : Object.class;
            return buildTypeSchema(typeArg, innerRaw, visited);
        }

        if (rawType == String.class || rawType == char.class || rawType == Character.class) {
            return Json.createObjectBuilder().add("type", "string");
        }

        if (rawType == boolean.class || rawType == Boolean.class) {
            return Json.createObjectBuilder().add("type", "boolean");
        }

        if (rawType == int.class || rawType == Integer.class ||
            rawType == long.class || rawType == Long.class ||
            rawType == short.class || rawType == Short.class ||
            rawType == byte.class || rawType == Byte.class ||
            rawType == BigInteger.class) {
            return Json.createObjectBuilder().add("type", "integer");
        }

        if (rawType == double.class || rawType == Double.class ||
            rawType == float.class || rawType == Float.class ||
            rawType == BigDecimal.class) {
            return Json.createObjectBuilder().add("type", "number");
        }

        if (rawType.isEnum()) {
            return buildEnumSchema(rawType);
        }

        if (rawType.isArray()) {
            return buildArraySchema(rawType.getComponentType(), rawType.getComponentType(), visited);
        }

        if (Collection.class.isAssignableFrom(rawType)) {
            return buildCollectionSchema(genericType, visited);
        }

        if (Map.class.isAssignableFrom(rawType)) {
            return buildMapSchema(genericType, visited);
        }

        var nestedSchema = buildObjectSchema(rawType, visited);
        return Json.createObjectBuilder()
            .add("type", "object")
            .add("properties", nestedSchema.getJsonObject("properties"))
            .add("required", nestedSchema.getJsonArray("required"));
    }

    private static JsonObjectBuilder buildEnumSchema(Class<?> enumType) {
        var enumValues = Json.createArrayBuilder();

        for (var constant : enumType.getEnumConstants()) {
            enumValues.add(((Enum<?>) constant).name());
        }

        return Json.createObjectBuilder()
            .add("type", "string")
            .add("enum", enumValues);
    }

    private static JsonObjectBuilder buildArraySchema(Type elementType, Class<?> elementRawType, Set<Class<?>> visited) {
        return Json.createObjectBuilder()
            .add("type", "array")
            .add("items", buildTypeSchema(elementType, elementRawType, visited));
    }

    private static JsonObjectBuilder buildCollectionSchema(Type genericType, Set<Class<?>> visited) {
        if (genericType instanceof ParameterizedType parameterized) {
            var typeArgs = parameterized.getActualTypeArguments();

            if (typeArgs.length > 0) {
                var elementType = typeArgs[0];
                var elementRawType = elementType instanceof Class<?> c ? c : Object.class;
                return buildArraySchema(elementType, elementRawType, visited);
            }
        }

        return Json.createObjectBuilder()
            .add("type", "array")
            .add("items", Json.createObjectBuilder().add("type", "object"));
    }

    private static JsonObjectBuilder buildMapSchema(Type genericType, Set<Class<?>> visited) {
        if (genericType instanceof ParameterizedType parameterized) {
            var typeArgs = parameterized.getActualTypeArguments();

            if (typeArgs.length > 1) {
                var valueType = typeArgs[1];
                var valueRawType = valueType instanceof Class<?> c ? c : Object.class;
                return Json.createObjectBuilder()
                    .add("type", "object")
                    .add("additionalProperties", buildTypeSchema(valueType, valueRawType, visited));
            }
        }

        return Json.createObjectBuilder().add("type", "object");
    }

    private static boolean isOptional(Class<?> type) {
        return type == Optional.class;
    }
}
