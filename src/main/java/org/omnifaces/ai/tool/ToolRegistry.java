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

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.concat;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonObject;

/**
 * The set of tools a service offers the AI.
 * <p>
 * Tools come either from {@link AITool} annotated methods on objects you hand over, or from lambdas declared via {@link #newBuilder()}, and the two mix freely.
 * There is no classpath scanning either way, so exposing application code to model output is always a deliberate act.
 * <p>
 * Usage example with annotated methods:
 *
 * <pre>
 *
 * ToolRegistry tools = ToolRegistry.of(orderTools);
 * </pre>
 * <p>
 * Usage example with lambdas:
 *
 * <pre>
 *
 * ToolRegistry tools = ToolRegistry.newBuilder()
 *     .add("FIND_ORDER", "Looks up a single order by id", orders::findById, ToolParam.of(long.class, "orderId", "The order id"))
 *     .add("LIST_OPEN_ORDERS", "Lists all open orders", orders::findOpen)
 *     .add(shippingTools)
 *     .build();
 * </pre>
 *
 * @author Bauke Scholtz
 * @since 1.6
 * @see AITool
 * @see AIToolGroup
 */
public final class ToolRegistry {

    /** Naming conventions of the proxy generators used by the common CDI containers. */
    private static final List<String> PROXY_MARKERS = List.of("$$", "$Proxy", "_ClientProxy", "_Subclass");

    /** The tool name the AI uses to signal it is done and answering directly. */
    public static final String ANSWER = "ANSWER";

    /** The tools by name, in the order they are offered to the AI. */
    private final Map<String, Tool> tools;
    /** The manifest describing the tools, computed once as it must be identical from one call to the next. */
    private final String manifest;
    /** The schema offering every tool plus the answer. */
    private final JsonObject responseSchema;
    /** The schema offering only the answer, for the last turn of a loop. */
    private final JsonObject answerSchema;

    private ToolRegistry(Map<String, Tool> tools) {
        if (tools.isEmpty()) {
            throw new IllegalArgumentException("A tool registry needs at least one tool.");
        }

        this.tools = new LinkedHashMap<>(tools);
        this.manifest = buildManifest();
        this.responseSchema = buildResponseSchema(concat(this.tools.keySet().stream(), Stream.of(ANSWER)).toList());
        this.answerSchema = buildResponseSchema(List.of(ANSWER));
    }

    /**
     * Creates a registry of all {@link AITool} annotated methods declared by the given instances.
     *
     * @param instances The objects declaring {@link AITool} annotated methods.
     * @return The registry, never {@code null}.
     * @throws IllegalArgumentException If no tool methods are found, or if two instances declare the same tool name.
     */
    public static ToolRegistry of(Object... instances) {
        return of(null, instances);
    }

    /**
     * Creates a registry of the {@link AITool} annotated methods declared by the given instances and tagged with the given group.
     *
     * @param group The group to narrow to, or {@code null} for all tool methods.
     * @param instances The objects declaring {@link AITool} annotated methods.
     * @return The registry, never {@code null}.
     * @throws IllegalArgumentException If the group is not itself annotated with {@link AIToolGroup}, if no tool methods are found, or if two instances declare
     * the same tool name.
     */
    public static ToolRegistry of(Class<? extends Annotation> group, Object... instances) {
        var builder = new Builder();
        asList(instances).forEach(instance -> builder.add(group, instance));
        return builder.build();
    }

    /**
     * Returns a new builder for declaring tools programmatically, from lambdas, from annotated objects, or from both.
     *
     * @return A new builder, never {@code null}.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Returns the names of the tools in this registry, in the order they were added, with the methods of each annotated object sorted by name among themselves.
     * The order is stable across runs, so that the manifest built from it is too.
     *
     * @return The names of the tools in this registry, never {@code null}.
     */
    public Set<String> getToolNames() {
        return unmodifiableSet(tools.keySet());
    }

    /**
     * Returns the manifest describing the available tools, to be appended to the system prompt.
     *
     * @return The manifest describing the available tools, never {@code null}.
     */
    public String getManifest() {
        return manifest;
    }

    /**
     * Returns the given tool as a single manifest line, e.g. {@code - FIND_ORDER(orderId: The order id) -> Looks up a single order by id}.
     *
     * @param tool The tool to describe.
     * @return The given tool as a single manifest line.
     */
    static String toManifestLine(Tool tool) {
        return "- " + tool.getName() + "(" + tool.getParams().stream().map(ToolParam::toString).collect(joining(", ")) + ") -> " + tool.getDescription();
    }

    private String buildManifest() {
        return tools.values().stream().map(ToolRegistry::toManifestLine).collect(
            joining(
                "\n", "Available tools:\n",
                "\n\nCall exactly one tool per turn, or answer directly once you have enough information. Never invent data a tool did not return."
                    + " There is nobody to ask for more information, so when a tool needs a value you do not have, obtain it from another tool first."
            )
        );
    }

    /**
     * Returns the JSON schema constraining the AI to either call one of the available tools or answer directly.
     * <p>
     * The tool name is an enumeration of this registry's tool names, so the AI cannot name a tool that is not in it. Arguments travel as name/value pairs
     * rather than a free-form object, as strict schema modes require every property of an object to be declared.
     *
     * @return The JSON schema constraining the AI, never {@code null}.
     */
    public JsonObject getResponseSchema() {
        return responseSchema;
    }

    /**
     * Returns the JSON schema constraining the AI to answer directly, leaving it no tool to call.
     * <p>
     * This is what the last turn of a tool loop is given: a model which keeps calling tools cannot be talked out of it, but it can be denied the tokens.
     *
     * @return The JSON schema constraining the AI to answer, never {@code null}.
     */
    public JsonObject getAnswerSchema() {
        return answerSchema;
    }

    private static JsonObject buildResponseSchema(List<String> toolNames) {
        var toolNameValues = Json.createArrayBuilder();
        toolNames.forEach(toolNameValues::add);

        var argument = Json.createObjectBuilder()
            .add("type", "object")
            .add(
                "properties", Json.createObjectBuilder()
                    .add("name", Json.createObjectBuilder().add("type", "string"))
                    .add("value", Json.createObjectBuilder().add("type", "string"))
            )
            .add("required", Json.createArrayBuilder().add("name").add("value"));

        return Json.createObjectBuilder()
            .add("type", "object")
            .add(
                "properties", Json.createObjectBuilder()
                    .add("tool", Json.createObjectBuilder().add("type", "string").add("enum", toolNameValues))
                    .add("arguments", Json.createObjectBuilder().add("type", "array").add("items", argument))
                    .add("answer", Json.createObjectBuilder().add("type", "string"))
            )
            .add("required", Json.createArrayBuilder().add("tool").add("arguments").add("answer"))
            .build();
    }

    /**
     * Invokes the named tool with the given raw arguments.
     *
     * @param toolName The name of the tool to invoke.
     * @param arguments The raw arguments, keyed by parameter name.
     * @return The value the tool returned, never {@code null}.
     * @throws IllegalArgumentException If the tool is not in this registry, or if an argument is missing or cannot be converted.
     * @throws ToolInvocationException If the tool itself throws.
     */
    public Object invoke(String toolName, Map<String, String> arguments) {
        var tool = tools.get(toolName);

        if (tool == null) {
            throw new IllegalArgumentException("There is no tool named " + toolName + " in this registry.");
        }

        return tool.invoke(arguments);
    }

    /**
     * Builder for a {@link ToolRegistry}, mixing {@link AITool} annotated objects and programmatically declared lambdas.
     *
     * @author Bauke Scholtz
     * @since 1.6
     */
    public static final class Builder {

        /** The tools added so far, by name. */
        private final Map<String, Tool> tools = new LinkedHashMap<>();

        private Builder() {
            //
        }

        /**
         * Adds all {@link AITool} annotated methods declared by the given instance.
         *
         * @param instance The object declaring {@link AITool} annotated methods.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException If the instance declares no tool methods, or one whose name is already taken.
         */
        public Builder add(Object instance) {
            return add(null, instance);
        }

        /**
         * Adds the {@link AITool} annotated methods declared by the given instance and tagged with the given group.
         *
         * @param group The group to narrow to, or {@code null} for all tool methods.
         * @param instance The object declaring {@link AITool} annotated methods.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException If the group is not itself annotated with {@link AIToolGroup}, if the instance declares no matching tool methods, or
         * one whose name is already taken.
         */
        public Builder add(Class<? extends Annotation> group, Object instance) {
            return add(group, toDeclaringClass(instance.getClass()), instance);
        }

        /**
         * Returns the class which declares the tool methods of the given runtime class, skipping any container proxy in between. A proxy overrides every method
         * it forwards, and those overrides carry no parameter names, so scanning one would demand an explicit {@link AIToolParam#name()} on every parameter.
         * <p>
         * Detection is by the naming conventions of the common proxy generators, so hand this builder the declaring class explicitly via
         * {@link #add(Class, Class, Object)} when a container is not covered.
         */
        private static Class<?> toDeclaringClass(Class<?> type) {
            for (var candidate = type; candidate.getSuperclass() != null; candidate = candidate.getSuperclass()) {
                if (!isProxy(candidate)) {
                    return candidate;
                }
            }

            return type;
        }

        private static boolean isProxy(Class<?> type) {
            var name = type.getName();
            return type.isSynthetic() || PROXY_MARKERS.stream().anyMatch(name::contains);
        }

        /**
         * Adds the {@link AITool} annotated methods declared by the given class and tagged with the given group, invoking them on the given instance.
         * <p>
         * Use this when the instance is a container proxy, whose own class carries neither the parameter names nor a stable identity to cache the scan under.
         *
         * @param group The group to narrow to, or {@code null} for all tool methods.
         * @param declaringClass The class declaring {@link AITool} annotated methods.
         * @param instance The object to invoke the tool methods on.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException If the group is not itself annotated with {@link AIToolGroup}, if the class declares no matching tool methods, or
         * one whose name is already taken.
         */
        public Builder add(Class<? extends Annotation> group, Class<?> declaringClass, Object instance) {
            if (!declaringClass.isInstance(instance)) {
                throw new IllegalArgumentException("The given " + instance.getClass().getName() + " is not an instance of " + declaringClass.getName() + ".");
            }

            var toolMethods = ToolMethod.of(instance, declaringClass, group);

            if (toolMethods.isEmpty()) {
                throw new IllegalArgumentException(
                    declaringClass.getName() + " declares no @AITool annotated method"
                        + (group == null ? "." : " tagged with @" + group.getSimpleName() + ".")
                );
            }

            toolMethods.forEach(this::put);
            return this;
        }

        /**
         * Adds a tool taking no arguments, which therefore accepts a plain method reference.
         *
         * @param name The tool name as read by the AI.
         * @param description The tool description as read by the AI.
         * @param function What the tool does.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException If the name is already taken.
         */
        public Builder add(String name, String description, Supplier<?> function) {
            return put(new ToolFunction(name, description, List.of(), values -> function.get()));
        }

        /**
         * Adds a tool taking a single argument.
         *
         * @param <P> The parameter type.
         * @param name The tool name as read by the AI.
         * @param description The tool description as read by the AI.
         * @param function What the tool does.
         * @param param The parameter the AI must supply.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException If the name is already taken.
         */
        @SuppressWarnings("unchecked")
        public <P> Builder add(String name, String description, Function<P, ?> function, ToolParam param) {
            return put(new ToolFunction(name, description, List.of(param), values -> function.apply((P) values[0])));
        }

        /**
         * Adds a tool taking two arguments.
         *
         * @param <P1> The first parameter type.
         * @param <P2> The second parameter type.
         * @param name The tool name as read by the AI.
         * @param description The tool description as read by the AI.
         * @param function What the tool does.
         * @param param1 The first parameter the AI must supply.
         * @param param2 The second parameter the AI must supply.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException If the name is already taken.
         */
        @SuppressWarnings("unchecked")
        public <P1, P2> Builder add(String name, String description, BiFunction<P1, P2, ?> function, ToolParam param1, ToolParam param2) {
            return put(new ToolFunction(name, description, List.of(param1, param2), values -> function.apply((P1) values[0], (P2) values[1])));
        }

        /**
         * Adds a tool taking any number of arguments, which arrive in declaration order.
         *
         * @param name The tool name as read by the AI.
         * @param description The tool description as read by the AI.
         * @param function What the tool does.
         * @param params The parameters the AI must supply, in the order the function expects them.
         * @return This builder instance for chaining.
         * @throws IllegalArgumentException If the name is already taken.
         */
        public Builder add(String name, String description, Function<Object[], ?> function, List<ToolParam> params) {
            return put(new ToolFunction(name, description, params, function::apply));
        }

        private Builder put(Tool tool) {
            if (tools.put(tool.getName(), tool) != null) {
                throw new IllegalArgumentException("The tool name " + tool.getName() + " is already taken.");
            }

            return this;
        }

        /**
         * Builds the registry.
         *
         * @return The registry, never {@code null}.
         * @throws IllegalArgumentException If no tools were added.
         */
        public ToolRegistry build() {
            return new ToolRegistry(tools);
        }

    }

}
