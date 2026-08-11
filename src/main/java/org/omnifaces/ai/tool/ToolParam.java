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
package org.omnifaces.ai.tool;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonValue;

import org.omnifaces.ai.helper.JsonSchemaHelper;

/**
 * A single parameter of a tool: what the AI must supply, and the Java type it is converted to.
 *
 * @param type The Java type the raw argument is converted to.
 * @param name The name as read by the AI.
 * @param description The description as read by the AI.
 * @author Bauke Scholtz
 * @since 1.6
 * @see ToolRegistry
 * @see AIToolParam
 */
public record ToolParam(Class<?> type, String name, String description) {

    /**
     * Creates a tool parameter of the given type, name and description.
     *
     * @param type The Java type the raw argument is converted to.
     * @param name The name as read by the AI.
     * @param description The description as read by the AI.
     * @return The tool parameter, never {@code null}.
     */
    public static ToolParam of(Class<?> type, String name, String description) {
        return new ToolParam(requireNonNull(type, "type"), requireNonNull(name, "name"), requireNonNull(description, "description"));
    }

    /**
     * Converts the raw argument for this parameter into its declared type.
     *
     * @param arguments The raw arguments, keyed by parameter name.
     * @return The converted value.
     * @throws IllegalArgumentException If the argument is missing or cannot be converted to the declared type.
     */
    Object convert(Map<String, String> arguments) {
        var argument = arguments.get(name);

        if (argument == null) {
            throw new IllegalArgumentException("Missing argument " + name + ".");
        }

        try {
            return JsonSchemaHelper.fromJson(toJsonValue(argument), type);
        }
        catch (RuntimeException e) {
            throw new IllegalArgumentException("Argument " + name + " with value '" + argument + "' is not a valid " + type.getSimpleName() + ".", e);
        }
    }

    private JsonValue toJsonValue(String argument) {
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(argument) ? JsonValue.TRUE : JsonValue.FALSE;
        }

        if (Number.class.isAssignableFrom(type) || (type.isPrimitive() && type != char.class)) {
            return Json.createValue(new BigDecimal(argument));
        }

        return Json.createValue(argument);
    }

    /**
     * Returns this parameter as it appears in the manifest, e.g. {@code orderId: The order id}.
     */
    @Override
    public String toString() {
        return name + ": " + description;
    }

}
