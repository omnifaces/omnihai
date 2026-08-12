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

import static java.util.Arrays.stream;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The reflective metadata of a single {@link AITool} annotated method, bound to the object it is invoked on.
 * <p>
 * The metadata is cached per declaring class, so the reflection happens once rather than on every registry build; binding it to an instance is a field
 * assignment.
 *
 * @author Bauke Scholtz
 * @since 1.6
 * @see AITool
 * @see ToolRegistry
 */
final class ToolMethod implements Tool {

    /**
     * The unbound metadata per declaring class, so that the reflection happens once. A {@link ClassValue} keeps the association on the class itself rather than
     * in a map of its own, so that a class stays collectable and this cache cannot pin an application's classloader when the library is deployed as a shared
     * one rather than inside the archive.
     */
    private static final ClassValue<List<ToolMethod>> CACHE = new ClassValue<>() {

        @Override
        protected List<ToolMethod> computeValue(Class<?> type) {
            return scan(type);
        }

    };

    /** The annotated method. */
    private final Method method;
    /** The tool name as read by the AI. */
    private final String name;
    /** The tool description as read by the AI. */
    private final String description;
    /** The parameters of the annotated method. */
    private final List<ToolParam> params;
    /** The object the method is invoked on, or {@code null} while this is unbound metadata. */
    private final Object instance;

    private ToolMethod(Method method, String name) {
        this.method = method;
        this.name = name;
        this.description = method.getAnnotation(AITool.class).value();
        this.params = stream(method.getParameters()).map(parameter -> toToolParam(method, parameter)).collect(toUnmodifiableList());
        this.instance = null;
    }

    private ToolMethod(ToolMethod metadata, Object instance) {
        this.method = metadata.method;
        this.name = metadata.name;
        this.description = metadata.description;
        this.params = metadata.params;
        this.instance = instance;
    }

    /**
     * Returns the tool methods declared by the given class, bound to the given instance, optionally narrowed to those tagged with the given group.
     * <p>
     * The class is scanned rather than {@code instance.getClass()}, as the instance may be a container proxy, on which parameter names are absent even when the
     * bean class itself was compiled with {@code -parameters}.
     *
     * @param instance The object to invoke the tool methods on.
     * @param declaringClass The class declaring {@link AITool} annotated methods.
     * @param group The group to narrow to, or {@code null} for all tool methods.
     * @return The bound tool methods, never {@code null}.
     * @throws IllegalArgumentException If the group is not itself annotated with {@link AIToolGroup}, or if a parameter has neither an explicit name nor a
     * reflective one.
     */
    static List<Tool> of(Object instance, Class<?> declaringClass, Class<? extends Annotation> group) {
        if (group != null && !group.isAnnotationPresent(AIToolGroup.class)) {
            throw new IllegalArgumentException("The tool group " + group.getName() + " must itself be annotated with @AIToolGroup.");
        }

        return CACHE.get(declaringClass).stream()
            .filter(toolMethod -> group == null || toolMethod.method.isAnnotationPresent(group))
            .map(toolMethod -> (Tool) new ToolMethod(toolMethod, instance))
            .collect(toUnmodifiableList());
    }

    private static List<ToolMethod> scan(Class<?> type) {
        var toolMethods = stream(type.getMethods()).filter(method -> method.isAnnotationPresent(AITool.class))
            .collect(groupingBy(Method::getName)).values().stream()
            .flatMap(sameNamed -> toToolMethods(type, sameNamed))
            .sorted(comparing(ToolMethod::getName)).collect(toUnmodifiableList());

        if (!toolMethods.isEmpty() && !Modifier.isPublic(type.getModifiers())) {
            throw new IllegalArgumentException(
                "The class " + type.getName() + " must be public for its @AITool annotated methods to be invocable from outside its own package."
            );
        }

        return toolMethods;
    }

    /**
     * Returns the tools of the given same-named methods of the given class, named after both, e.g. {@code OrderTools_findOrder}. Overloads would share that one
     * name, so each carries its parameter types as well, as in {@code OrderTools_findOrder_long}, which keeps a given overload on the name it already had when
     * another one is added next to it.
     */
    private static Stream<ToolMethod> toToolMethods(Class<?> type, List<Method> sameNamed) {
        var methods = withoutBridges(sameNamed).toList();
        return methods.stream().map(method -> new ToolMethod(method, toName(type, method, methods.size() > 1)));
    }

    private static String toName(Class<?> type, Method method, boolean overloaded) {
        var name = type.getSimpleName() + "_" + method.getName();
        return overloaded ? name + stream(method.getParameterTypes()).map(ToolMethod::toTypeName).collect(joining("_", "_", "")) : name;
    }

    /** Returns the given parameter type as a name fragment, where an array becomes {@code long[]} to {@code longArray} rather than carrying its brackets. */
    private static String toTypeName(Class<?> type) {
        return type.getSimpleName().replace("[]", "Array");
    }

    /**
     * Returns the given same-named methods without the synthetic bridges which merely stand in front of one of the others.
     * <p>
     * A bridge generated for a generic or covariant override forwards to a method of its own class, which is the one to keep. A bridge generated to widen the
     * visibility of a method inherited from a non-public class forwards to that superclass method, which reflection does not otherwise offer, so it is the only
     * handle on that tool and is kept. Genuine overloads are all kept, so that each becomes a tool of its own rather than one of them disappearing.
     */
    private static Stream<Method> withoutBridges(List<Method> sameNamed) {
        return sameNamed.stream().filter(method -> !method.isBridge() || isInheritedThroughBridge(method));
    }

    private static boolean isInheritedThroughBridge(Method bridge) {
        if (declares(bridge.getDeclaringClass(), bridge)) {
            return false;
        }

        for (var type = bridge.getDeclaringClass().getSuperclass(); type != null; type = type.getSuperclass()) {
            if (declares(type, bridge)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns whether the given class itself declares the method the given bridge forwards to, being one of the same name and the very same parameter types.
     */
    private static boolean declares(Class<?> type, Method bridge) {
        return stream(type.getDeclaredMethods()).anyMatch(
            method -> !method.isBridge() && method.getName().equals(bridge.getName()) && Arrays.equals(method.getParameterTypes(), bridge.getParameterTypes())
        );
    }

    private static ToolParam toToolParam(Method method, Parameter parameter) {
        var annotation = parameter.getAnnotation(AIToolParam.class);

        if (annotation == null) {
            throw new IllegalArgumentException(
                "The parameter " + parameter.getName() + " of tool method " + method + " must be annotated with @AIToolParam."
            );
        }

        var name = annotation.name();

        if (name.isEmpty()) {
            if (!parameter.isNamePresent()) {
                throw new IllegalArgumentException(
                    "The parameter " + parameter.getName() + " of tool method " + method
                        + " needs an explicit @AIToolParam name, as the class was not compiled with -parameters."
                );
            }

            name = parameter.getName();
        }

        return ToolParam.of(parameter.getType(), name, annotation.value());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public List<ToolParam> getParams() {
        return params;
    }

    @Override
    public Object invoke(Map<String, String> arguments) {
        var values = params.stream().map(param -> param.convert(arguments)).toArray();

        try {
            var result = method.invoke(instance, values);
            return result == null ? "" : result;
        }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }

            throw new ToolInvocationException(name, e.getCause());
        }
        catch (ReflectiveOperationException e) {
            throw new ToolInvocationException(name, e);
        }
    }

    @Override
    public String toString() {
        return ToolRegistry.toManifestLine(this);
    }

}
