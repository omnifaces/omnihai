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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ToolRegistryTest {

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

    // Registration ----------------------------------------------------------------------------------------------------

    @Test
    void of_derivesNamesFromTheClassAndMethodNames() {
        assertEquals(
            Set.of("OrderTools#findOrder", "OrderTools#findOrdersByDate", "OrderTools#findPerson", "OrderTools#refund", "OrderTools#explode"),
            Set.copyOf(ToolRegistry.of(new OrderTools()).getToolNames())
        );
    }

    @Test
    void of_withMultipleInstances_mergesTheirTools() {
        assertTrue(ToolRegistry.of(new OrderTools(), new ShippingTools()).getToolNames().contains("ShippingTools#findShipment"));
    }

    /**
     * A group narrows the set to the methods tagged with it, independently of the class they live in.
     */
    @Test
    void of_withGroup_narrowsToTaggedMethods() {
        assertEquals(
            Set.of("OrderTools#findOrder", "OrderTools#findOrdersByDate"), Set.copyOf(ToolRegistry.of(ReadOnly.class, new OrderTools()).getToolNames())
        );
    }

    /**
     * A method inherited from a non-public class carries its annotations on the bridge only, so dropping every bridge would lose the tool entirely.
     */
    @Test
    void of_withToolInheritedFromNonPublicClass_registersIt() {
        var registry = ToolRegistry.of(new VisibleTools());

        assertEquals(Set.of("VisibleTools#findOrder"), Set.copyOf(registry.getToolNames()));
        assertEquals("hidden order 42", registry.invoke("VisibleTools#findOrder", Map.of("orderId", "42")));
    }

    /**
     * Two methods of one name derive one tool name, so each overload is numbered to keep it a tool of its own, on the number its parameter types put it at
     * rather than on the one reflection order happened to hand out.
     */
    @Test
    void of_withOverloadedToolMethods_numbersThem() {
        var registry = ToolRegistry.of(new OverloadedTools());

        assertEquals(Set.of("OverloadedTools#findOrder1", "OverloadedTools#findOrder2"), Set.copyOf(registry.getToolNames()));
        assertEquals("order ref-1", registry.invoke("OverloadedTools#findOrder1", Map.of("reference", "ref-1")));
        assertEquals("order 42", registry.invoke("OverloadedTools#findOrder2", Map.of("orderId", "42")));
    }

    /**
     * Both reflective methods are bridges here, so the more specific one stands for the tool and the erased one is dropped rather than colliding with it.
     */
    @Test
    void of_withBridgeOnlyGenericImplementation_registersTheToolOnce() {
        var registry = ToolRegistry.of(new VisibleHandler());

        assertEquals(Set.of("VisibleHandler#handle"), Set.copyOf(registry.getToolNames()));
        assertEquals("handled x", registry.invoke("VisibleHandler#handle", Map.of("value", "x")));
    }

    /**
     * An inherited tool and an overload of its name derive the one tool name, and are numbered under the class handed over rather than under the one each of
     * them happens to be declared by.
     */
    @Test
    void of_withOverloadOfInheritedToolName_numbersThem() {
        var registry = ToolRegistry.of(new OverloadingSub());

        assertEquals(Set.of("OverloadingSub#findOrder1", "OverloadingSub#findOrder2"), Set.copyOf(registry.getToolNames()));
        assertEquals("order ref-1", registry.invoke("OverloadingSub#findOrder1", Map.of("reference", "ref-1")));
        assertEquals("hidden order 42", registry.invoke("OverloadingSub#findOrder2", Map.of("orderId", "42")));
    }

    /**
     * An injected bean is a proxy whose overrides carry no parameter names of their own, so the class behind it is what must be scanned. The proxy is still
     * what the call lands on, as the method dispatches virtually.
     */
    @Test
    void of_withContainerProxy_scansTheClassBehindIt() {
        var registry = ToolRegistry.of(new OrderTools_ClientProxy());

        assertEquals(
            Set.of("OrderTools#findOrder", "OrderTools#findOrdersByDate", "OrderTools#findPerson", "OrderTools#refund", "OrderTools#explode"),
            Set.copyOf(registry.getToolNames())
        );
        assertEquals("proxied order 42", registry.invoke("OrderTools#findOrder", Map.of("orderId", "42")));
    }

    /**
     * A tag that is not itself declared as a group is a wiring mistake rather than an empty selection.
     */
    @Test
    void of_withUndeclaredGroup_throws() {
        assertThrows(IllegalArgumentException.class, () -> ToolRegistry.of(NotAGroup.class, new OrderTools()));
    }

    @Test
    void of_withoutAnyToolMethod_throws() {
        assertThrows(IllegalArgumentException.class, () -> ToolRegistry.of(new WithoutTools()));
    }

    /**
     * Two tools sharing a name would make the AI's choice ambiguous, so it must fail at registration rather than silently resolve to one of them.
     */
    @Test
    void of_withDuplicateToolName_throws() {
        assertThrows(IllegalArgumentException.class, () -> ToolRegistry.of(new OrderTools(), new OrderTools()));
    }

    /**
     * A tool name carries the simple class name, so two classes of that name coming from different packages claim the one tool name and must be rejected rather
     * than let the AI's choice land on either.
     */
    @Test
    void of_withToolsOfEquallyNamedClasses_throws() {
        assertThrows(IllegalArgumentException.class, () -> ToolRegistry.of(new OrderTools(), new Elsewhere.OrderTools()));
    }

    /**
     * The compiler copies the annotations onto the synthetic bridge method it generates for a generic override, so both would claim the same tool name.
     */
    @Test
    void of_withBridgeMethod_registersTheToolOnce() {
        assertEquals(Set.of("GenericHandler#handle"), Set.copyOf(ToolRegistry.of(new GenericHandler()).getToolNames()));
        assertEquals("handled x", ToolRegistry.of(new GenericHandler()).invoke("GenericHandler#handle", Map.of("value", "x")));
    }

    /**
     * Scanning a class the instance is not an instance of would surface per turn as an argument the AI cannot correct, so it is rejected up front.
     */
    @Test
    void add_withInstanceOfAnotherClass_throws() {
        var builder = ToolRegistry.newBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.add(null, OrderTools.class, "not an OrderTools"));
    }

    // Schema ----------------------------------------------------------------------------------------------------------

    /**
     * The tool name is constrained by the schema, so a narrowed registry makes the omitted tools unreachable rather than merely unadvertised.
     */
    @Test
    void getResponseSchema_constrainsToolNameToTheRegistryPlusAnswer() {
        var toolNames = ToolRegistry.of(ReadOnly.class, new OrderTools()).getResponseSchema()
            .getJsonObject("properties").getJsonObject("tool").getJsonArray("enum").getValuesAs(jakarta.json.JsonString.class)
            .stream().map(jakarta.json.JsonString::getString).toList();

        assertEquals(List.of("OrderTools#findOrder", "OrderTools#findOrdersByDate", ToolRegistry.ANSWER), toolNames);
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

        assertTrue(manifest.contains("- OrderTools#findOrder(orderId: The order id) -> Looks up a single order by id"), manifest);
        assertTrue(manifest.contains("- OrderTools#findOrdersByDate(date: The order date) -> Lists orders placed on a date"), manifest);
    }

    // Invocation ------------------------------------------------------------------------------------------------------

    @Test
    void invoke_convertsArgumentsToTheDeclaredParameterTypes() {
        assertEquals("order 42", ToolRegistry.of(new OrderTools()).invoke("OrderTools#findOrder", Map.of("orderId", "42")));
        assertEquals("orders on 2026-08-11", ToolRegistry.of(new OrderTools()).invoke("OrderTools#findOrdersByDate", Map.of("date", "2026-08-11")));
    }

    /**
     * An unconvertible argument must be reported as such, so the loop can hand it back to the AI to correct.
     */
    @Test
    void invoke_withUnconvertibleArgument_throws() {
        assertThrows(IllegalArgumentException.class, () -> ToolRegistry.of(new OrderTools()).invoke("OrderTools#findOrder", Map.of("orderId", "yesterday")));
    }

    @Test
    void invoke_withMissingArgument_throws() {
        assertThrows(IllegalArgumentException.class, () -> ToolRegistry.of(new OrderTools()).invoke("OrderTools#findOrder", Map.of()));
    }

    @Test
    void invoke_withUnknownTool_throws() {
        assertThrows(IllegalArgumentException.class, () -> ToolRegistry.of(new OrderTools()).invoke("OrderTools#noSuchTool", Map.of()));
    }

    /**
     * A throwing tool method surfaces as a tool invocation exception carrying the tool name, so the loop can report which tool failed.
     */
    @Test
    void invoke_whenToolThrows_wrapsInToolInvocationException() {
        var exception = assertThrows(ToolInvocationException.class, () -> ToolRegistry.of(new OrderTools()).invoke("OrderTools#explode", Map.of()));

        assertEquals("OrderTools#explode", exception.getToolName());
        assertInstanceOf(IllegalStateException.class, exception.getCause());
    }

}
