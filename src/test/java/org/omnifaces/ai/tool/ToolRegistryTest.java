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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolRegistryTest {

    @TempDir
    private Path tempDir;

    @AIToolGroup
    @Retention(RUNTIME)
    @Target(METHOD)
    @interface ReadOnly {
        //
    }

    @Retention(RUNTIME)
    @Target(METHOD)
    @interface NotAGroup {
        //
    }

    public static class OrderTools {

        @ReadOnly
        @AITool("Looks up a single order by id")
        public String findOrder(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
            return "order " + orderId;
        }

        @ReadOnly
        @AITool("Lists orders placed on a date")
        public String findOrdersByDate(@AIToolParam(value = "The order date", name = "date") LocalDate date) {
            return "orders on " + date;
        }

        @AITool("Issues a refund for an order")
        public String refund(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
            return "refunded " + orderId;
        }

        @AITool("Looks up a person by name")
        public String findPerson(@AIToolParam(value = "The person name", name = "name") String name) {
            return "person " + name;
        }

        @AITool("Always fails")
        public String explode() {
            throw new IllegalStateException("boom");
        }

    }

    public static class ShippingTools {

        @AITool("Looks up a shipment by order id")
        public String findShipment(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
            return "shipment for " + orderId;
        }

    }

    /** Stands in for another package declaring a class of a name already taken. */
    public static class Elsewhere {

        public static class OrderTools {

            @AITool("Looks up a single order by id")
            public String findOrder(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
                return "other order " + orderId;
            }

        }

    }

    public static class WithoutTools {
        //
    }

    /** Named as a container generates its client proxies, and overriding as one does. */
    public static class OrderTools_ClientProxy extends OrderTools {

        @Override
        public String findOrder(long orderId) {
            return "proxied " + super.findOrder(orderId);
        }

    }

    /** A tool inherited from a non-public class is reachable through its compiler-generated bridge alone. */
    static class HiddenBase {

        @AITool("Looks up a single order by id")
        public String findOrder(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
            return "hidden order " + orderId;
        }

    }

    public static class VisibleTools extends HiddenBase {
        //
    }

    /** A tool inherited from a non-public class, whose public subclass overloads its name. */
    public static class OverloadingSub extends HiddenBase {

        @AITool("Looks up an order by reference")
        public String findOrder(@AIToolParam(value = "The order reference", name = "reference") String reference) {
            return "order " + reference;
        }

    }

    interface Handled<T> {

        String handle(T value);

    }

    static class HiddenHandler implements Handled<String> {

        @Override
        @AITool("Handles a value")
        public String handle(@AIToolParam(value = "The value", name = "value") String value) {
            return "handled " + value;
        }

    }

    /** A non-public class implementing a generic interface: every reflective method of it is a bridge. */
    public static class VisibleHandler extends HiddenHandler {
        //
    }

    public static class OverloadedTools {

        @AITool("Looks up an order by numeric id")
        public String findOrder(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
            return "order " + orderId;
        }

        @AITool("Looks up an order by reference")
        public String findOrder(@AIToolParam(value = "The order reference", name = "reference") String reference) {
            return "order " + reference;
        }

    }

    public interface Handler<T> {

        String handle(T value);

    }

    public static class GenericHandler implements Handler<String> {

        @Override
        @AITool("Handles a value")
        public String handle(@AIToolParam(value = "The value", name = "value") String value) {
            return "handled " + value;
        }

    }

    // =================================================================================================================
    // Registration
    // =================================================================================================================

    @Test
    void of_derivesNamesFromTheClassAndMethodNames() {
        assertEquals(
            Set.of("OrderTools_findOrder", "OrderTools_findOrdersByDate", "OrderTools_findPerson", "OrderTools_refund", "OrderTools_explode"),
            Set.copyOf(ToolRegistry.of(new OrderTools()).getToolNames())
        );
    }

    @Test
    void of_withMultipleInstances_mergesTheirTools() {
        assertTrue(ToolRegistry.of(new OrderTools(), new ShippingTools()).getToolNames().contains("ShippingTools_findShipment"));
    }

    /**
     * A group narrows the set to the methods tagged with it, independently of the class they live in.
     */
    @Test
    void of_withGroup_narrowsToTaggedMethods() {
        assertEquals(
            Set.of("OrderTools_findOrder", "OrderTools_findOrdersByDate"), Set.copyOf(ToolRegistry.of(ReadOnly.class, new OrderTools()).getToolNames())
        );
    }

    /**
     * A method inherited from a non-public class carries its annotations on the bridge only, so dropping every bridge would lose the tool entirely.
     */
    @Test
    void of_withToolInheritedFromNonPublicClass_registersIt() {
        var registry = ToolRegistry.of(new VisibleTools());

        assertEquals(Set.of("VisibleTools_findOrder"), Set.copyOf(registry.getToolNames()));
        assertEquals("hidden order 42", registry.invoke("VisibleTools_findOrder", Map.of("orderId", "42")));
    }

    /**
     * Two methods of one name derive one tool name, so each overload carries its parameter types to keep it a tool of its own, on a name which does not shift
     * when another overload is added next to it.
     */
    @Test
    void of_withOverloadedToolMethods_namesThemAfterTheirParameterTypes() {
        var registry = ToolRegistry.of(new OverloadedTools());

        assertEquals(Set.of("OverloadedTools_findOrder_long", "OverloadedTools_findOrder_String"), Set.copyOf(registry.getToolNames()));
        assertEquals("order ref-1", registry.invoke("OverloadedTools_findOrder_String", Map.of("reference", "ref-1")));
        assertEquals("order 42", registry.invoke("OverloadedTools_findOrder_long", Map.of("orderId", "42")));
    }

    /**
     * Both reflective methods are bridges here, so the more specific one stands for the tool and the erased one is dropped rather than colliding with it.
     */
    @Test
    void of_withBridgeOnlyGenericImplementation_registersTheToolOnce() {
        var registry = ToolRegistry.of(new VisibleHandler());

        assertEquals(Set.of("VisibleHandler_handle"), Set.copyOf(registry.getToolNames()));
        assertEquals("handled x", registry.invoke("VisibleHandler_handle", Map.of("value", "x")));
    }

    /**
     * An inherited tool and an overload of its name derive the one tool name, and are told apart under the class handed over rather than under the one each of
     * them happens to be declared by.
     */
    @Test
    void of_withOverloadOfInheritedToolName_namesThemAfterTheirParameterTypes() {
        var registry = ToolRegistry.of(new OverloadingSub());

        assertEquals(Set.of("OverloadingSub_findOrder_long", "OverloadingSub_findOrder_String"), Set.copyOf(registry.getToolNames()));
        assertEquals("order ref-1", registry.invoke("OverloadingSub_findOrder_String", Map.of("reference", "ref-1")));
        assertEquals("hidden order 42", registry.invoke("OverloadingSub_findOrder_long", Map.of("orderId", "42")));
    }

    /**
     * An injected bean is a proxy whose overrides carry no parameter names of their own, so the class behind it is what must be scanned. The proxy is still
     * what the call lands on, as the method dispatches virtually.
     */
    @Test
    void of_withContainerProxy_scansTheClassBehindIt() {
        var registry = ToolRegistry.of(new OrderTools_ClientProxy());

        assertEquals(
            Set.of("OrderTools_findOrder", "OrderTools_findOrdersByDate", "OrderTools_findPerson", "OrderTools_refund", "OrderTools_explode"),
            Set.copyOf(registry.getToolNames())
        );
        assertEquals("proxied order 42", registry.invoke("OrderTools_findOrder", Map.of("orderId", "42")));
    }

    /**
     * A tag that is not itself declared as a group is a wiring mistake rather than an empty selection.
     */
    @Test
    void of_withUndeclaredGroup_throws() {
        var tools = new OrderTools();

        assertThrows(IllegalArgumentException.class, () -> ToolRegistry.of(NotAGroup.class, tools));
    }

    @Test
    void of_withoutAnyToolMethod_throws() {
        var tools = new WithoutTools();

        assertThrows(IllegalArgumentException.class, () -> ToolRegistry.of(tools));
    }

    /**
     * Two tools sharing a name would make the AI's choice ambiguous, so it must fail at registration rather than silently resolve to one of them.
     */
    @Test
    void of_withDuplicateToolName_throws() {
        var tools = new OrderTools();
        var duplicate = new OrderTools();

        assertThrows(IllegalArgumentException.class, () -> ToolRegistry.of(tools, duplicate));
    }

    /**
     * A tool name carries the simple class name, so two classes of that name coming from different packages claim the one tool name and must be rejected rather
     * than let the AI's choice land on either.
     */
    @Test
    void of_withToolsOfEquallyNamedClasses_throws() {
        var tools = new OrderTools();
        var equallyNamed = new Elsewhere.OrderTools();

        assertThrows(IllegalArgumentException.class, () -> ToolRegistry.of(tools, equallyNamed));
    }

    /**
     * The compiler copies the annotations onto the synthetic bridge method it generates for a generic override, so both would claim the same tool name.
     */
    @Test
    void of_withBridgeMethod_registersTheToolOnce() {
        assertEquals(Set.of("GenericHandler_handle"), Set.copyOf(ToolRegistry.of(new GenericHandler()).getToolNames()));
        assertEquals("handled x", ToolRegistry.of(new GenericHandler()).invoke("GenericHandler_handle", Map.of("value", "x")));
    }

    /**
     * Scanning a class the instance is not an instance of would surface per turn as an argument the AI cannot correct, so it is rejected up front.
     */
    @Test
    void add_withInstanceOfAnotherClass_throws() {
        var builder = ToolRegistry.newBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.add(null, OrderTools.class, "not an OrderTools"));
    }

    // =================================================================================================================
    // Schema
    // =================================================================================================================

    /**
     * The tool name is constrained by the schema, so a narrowed registry makes the omitted tools unreachable rather than merely unadvertised.
     */
    @Test
    void getResponseSchema_constrainsToolNameToTheRegistryPlusAnswer() {
        var toolNames = ToolRegistry.of(ReadOnly.class, new OrderTools()).getResponseSchema()
            .getJsonObject("properties").getJsonObject("tool").getJsonArray("enum").getValuesAs(jakarta.json.JsonString.class)
            .stream().map(jakarta.json.JsonString::getString).toList();

        assertEquals(List.of("OrderTools_findOrder", "OrderTools_findOrdersByDate", ToolRegistry.ANSWER), toolNames);
    }

    /**
     * Strict schema modes reject open ended objects, so arguments travel as name/value pairs.
     */
    @Test
    void getResponseSchema_declaresArgumentsAsNameValuePairs() {
        var arguments = ToolRegistry.of(new OrderTools()).getResponseSchema().getJsonObject("properties").getJsonObject("arguments");

        assertEquals("array", arguments.getString("type"));
        assertEquals(Set.of("name", "value"), arguments.getJsonObject("items").getJsonObject("properties").keySet());
    }

    @Test
    void getManifest_listsEveryToolWithItsParameters() {
        var manifest = ToolRegistry.of(ReadOnly.class, new OrderTools()).getManifest();

        assertTrue(manifest.contains("- OrderTools_findOrder(orderId: The order id) -> Looks up a single order by id"), manifest);
        assertTrue(manifest.contains("- OrderTools_findOrdersByDate(date: The order date) -> Lists orders placed on a date"), manifest);
    }

    // =================================================================================================================
    // Invocation
    // =================================================================================================================

    @Test
    void invoke_convertsArgumentsToTheDeclaredParameterTypes() {
        assertEquals("order 42", ToolRegistry.of(new OrderTools()).invoke("OrderTools_findOrder", Map.of("orderId", "42")));
        assertEquals("orders on 2026-08-11", ToolRegistry.of(new OrderTools()).invoke("OrderTools_findOrdersByDate", Map.of("date", "2026-08-11")));
    }

    /**
     * An unconvertible argument must be reported as such, so the loop can hand it back to the AI to correct.
     */
    @Test
    void invoke_withUnconvertibleArgument_throws() {
        var registry = ToolRegistry.of(new OrderTools());
        var arguments = Map.of("orderId", "yesterday");

        assertThrows(IllegalArgumentException.class, () -> registry.invoke("OrderTools_findOrder", arguments));
    }

    @Test
    void invoke_withMissingArgument_throws() {
        var registry = ToolRegistry.of(new OrderTools());

        assertThrows(IllegalArgumentException.class, () -> registry.invoke("OrderTools_findOrder", Map.of()));
    }

    @Test
    void invoke_withUnknownTool_throws() {
        var registry = ToolRegistry.of(new OrderTools());

        assertThrows(IllegalArgumentException.class, () -> registry.invoke("OrderTools_noSuchTool", Map.of()));
    }

    /**
     * A throwing tool method surfaces as a tool invocation exception carrying the tool name, so the loop can report which tool failed.
     */
    @Test
    void invoke_whenToolThrows_wrapsInToolInvocationException() {
        var registry = ToolRegistry.of(new OrderTools());

        var exception = assertThrows(ToolInvocationException.class, () -> registry.invoke("OrderTools_explode", Map.of()));

        assertEquals("OrderTools_explode", exception.getToolName());
        assertInstanceOf(IllegalStateException.class, exception.getCause());
    }

    /**
     * A throwing tool function surfaces the same way a throwing tool method does, as the loop cannot tell the two apart.
     */
    @Test
    void invoke_whenToolFunctionThrows_wrapsInToolInvocationException() {
        var registry = ToolRegistry.newBuilder().add("explode", "Always fails", () -> {
            throw new IllegalStateException("boom");
        }).build();

        var exception = assertThrows(ToolInvocationException.class, () -> registry.invoke("explode", Map.of()));

        assertEquals("explode", exception.getToolName());
        assertInstanceOf(IllegalStateException.class, exception.getCause());
    }

    /**
     * A tool which returns nothing answers an empty string, as the answer travels back to the AI as text and there is no null to send.
     */
    @Test
    void invoke_whenToolReturnsNothing_answersEmptyString() {
        var registry = ToolRegistry.newBuilder().add("silent", "Returns nothing", () -> null).build();

        assertEquals("", registry.invoke("silent", Map.of()));
    }

    // =================================================================================================================
    // Declaration errors
    // =================================================================================================================

    /**
     * A tool method invoked from outside its own package needs its class to be public, so a non-public one is rejected at registration rather than at the call
     * the AI makes later.
     */
    @Test
    void add_nonPublicClass_throws() {
        var builder = ToolRegistry.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.add(new NonPublicTools()));
        assertTrue(exception.getMessage().contains("must be public"));
    }

    /**
     * The AI is told what to pass by the parameter description, so a parameter without one cannot be offered.
     */
    @Test
    void add_parameterWithoutDescription_throws() {
        var builder = ToolRegistry.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.add(new UnannotatedParamTools()));
        assertTrue(exception.getMessage().contains("@AIToolParam"));
    }

    @Test
    void build_withoutAnyTool_throws() {
        var builder = ToolRegistry.newBuilder();

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    // =================================================================================================================
    // Container proxies
    // =================================================================================================================

    /**
     * A container hands out a generated subclass, whose own class carries neither the parameter names nor a stable identity, so the scan walks up to the class
     * which declares the tools.
     */
    @Test
    void add_containerProxy_scansTheClassItProxies() {
        var registry = ToolRegistry.newBuilder().add(new OrderTools_ClientProxy()).build();

        assertTrue(registry.getManifest().contains("findOrder"));
    }

    // =================================================================================================================
    // Two argument function tools
    // =================================================================================================================

    @Test
    void add_functionOfTwoArguments_passesThemInDeclarationOrder() {
        var registry = ToolRegistry.newBuilder()
            .add(
                "concat", "Joins two values", (String one, String other) -> one + "-" + other,
                ToolParam.of(String.class, "one", "The first value"), ToolParam.of(String.class, "other", "The second value")
            )
            .build();

        assertEquals("a-b", registry.invoke("concat", Map.of("one", "a", "other", "b")));
    }

    // =================================================================================================================
    // Errors versus exceptions
    // =================================================================================================================

    /**
     * An Error states that the JVM itself is in trouble, so it travels on rather than being reported to the AI as a tool which failed.
     */
    @Test
    void invoke_toolThrowingAnError_letsItThrough() {
        var registry = ToolRegistry.newBuilder().add(new ErroringTools()).build();

        assertThrows(StackOverflowError.class, () -> registry.invoke("ErroringTools_fail", Map.of()));
    }

    /**
     * A group narrows the selection, so a class declaring tools which none of them carries names the group it found nothing for.
     */
    @Test
    void add_groupMatchingNoToolMethod_namesTheGroup() {
        var builder = ToolRegistry.newBuilder();
        var shippingTools = new ShippingTools();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.add(ReadOnly.class, ShippingTools.class, shippingTools));
        assertTrue(exception.getMessage().contains("@" + ReadOnly.class.getSimpleName()), exception.getMessage());
    }

    /**
     * A container may hand out a proxy whose whole hierarchy is generated, in which case there is no declaring class to walk up to and the proxy itself is
     * scanned.
     */
    @Test
    void add_proxyWithoutADeclaringClassBehindIt_scansTheProxyItself() {
        var registry = ToolRegistry.newBuilder().add(new OrderTools_Subclass()).build();

        assertTrue(registry.getManifest().contains("findOrderProxied"), registry.getManifest());
    }

    /**
     * A tool declared on the class itself is reachable directly, so the bridge the compiler generates alongside it is not offered a second time.
     */
    @Test
    void add_toolDeclaredBesideItsOwnBridge_isOfferedOnce() {
        var registry = ToolRegistry.newBuilder().add(new PublicHandler()).build();

        assertEquals(1, registry.getManifest().lines().filter(line -> line.contains("PublicHandler_handle")).count(), registry.getManifest());
    }

    /**
     * A tool appears in the manifest the AI reads as its name, its parameters and what it does.
     */
    @Test
    void toString_ofATool_isItsManifestLine() {
        var tool = new ToolFunction("greet", "Greets someone", List.of(ToolParam.of(String.class, "name", "The name")), values -> "hi");

        assertEquals("- greet(name: The name) -> Greets someone", tool.toString());
    }

    /**
     * A narrowed return type makes the compiler generate a bridge beside the tool the class declares itself, and the bridge is not a second tool.
     */
    @Test
    void add_toolNarrowingItsReturnType_isOfferedOnce() {
        var registry = ToolRegistry.newBuilder().add(new CovariantSub()).build();

        assertEquals(1, registry.getManifest().lines().filter(line -> line.contains("CovariantSub_answer")).count(), registry.getManifest());
    }

    /** Stands in for a container proxy whose own hierarchy carries no declaring class to walk up to. */
    public static class OrderTools_Subclass {

        @AITool("Looks up a single order by id")
        public String findOrderProxied(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
            return "order " + orderId;
        }

    }

    public static class CovariantBase {

        @AITool("Answers something")
        public Object answer() {
            return "base";
        }

    }

    /** Narrows the return type of the tool it inherits, so the compiler generates a bridge beside it in this very class. */
    public static class CovariantSub extends CovariantBase {

        @Override
        @AITool("Answers something")
        public String answer() {
            return "sub";
        }

    }

    /** A public class implementing a generic interface, so the compiler generates a bridge beside the tool it declares itself. */
    public static class PublicHandler implements Handled<String> {

        @Override
        @AITool("Handles a value")
        public String handle(@AIToolParam(value = "The value", name = "value") String value) {
            return "handled " + value;
        }

    }

    // =================================================================================================================
    // Tools compiled without parameter names
    // =================================================================================================================

    /**
     * A parameter name is only in the class file when the tool was compiled with {@code -parameters}, which is not the default, so a tool declared without one
     * has to state the name in its annotation. This build compiles its own classes with that flag, so the tool is compiled here without it.
     */
    @Test
    void add_parameterWithoutANameAndCompiledWithoutParameterNames_throws() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "a JDK is needed to compile the tool without parameter names");

        var source = Files.writeString(tempDir.resolve("NamelessTools.java"), """
            public class NamelessTools {
                @org.omnifaces.ai.tool.AITool("Looks up an order")
                public String findOrder(@org.omnifaces.ai.tool.AIToolParam("The order id") long orderId) {
                    return "order " + orderId;
                }
            }
            """);

        var options = List.of("-classpath", System.getProperty("java.class.path"), "-d", tempDir.toString());
        assumeTrue(
            compiler.getTask(
                null, null, null, options, null, compiler.getStandardFileManager(null, null, null)
                    .getJavaFileObjects(source.toFile())
            ).call(), "the tool must compile"
        );

        try (var loader = new URLClassLoader(new URL[] { tempDir.toUri().toURL() }, getClass().getClassLoader())) {
            var instance = loader.loadClass("NamelessTools").getDeclaredConstructor().newInstance();
            var builder = ToolRegistry.newBuilder();

            var exception = assertThrows(IllegalArgumentException.class, () -> builder.add(instance));
            assertTrue(exception.getMessage().contains("@AIToolParam"), exception.getMessage());
        }
    }

    /** A tool class which is not public, whose methods are therefore not invocable from here. */
    static class NonPublicTools {

        @AITool("Looks up a single order by id")
        public String findOrderHidden(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
            return "order " + orderId;
        }

    }

    public static class UnannotatedParamTools {

        @AITool("Looks up a single order by id")
        public String findOrderUnannotated(long orderId) {
            return "order " + orderId;
        }

    }

    public static class ErroringTools {

        @AITool("Always fails hard")
        public String fail() {
            throw new StackOverflowError("boom");
        }

    }

}
