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
package org.omnifaces.ai.service;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.tool.AITool;
import org.omnifaces.ai.tool.AIToolGroup;
import org.omnifaces.ai.tool.AIToolParam;
import org.omnifaces.ai.tool.ToolInvocation;
import org.omnifaces.ai.tool.ToolRegistry;
import org.opentest4j.TestAbortedException;

/**
 * Base class for IT on tool calling, which exercises the loop against the real provider: the AI has to pick a tool, supply its arguments, read the result and
 * answer from it. The tools return values which cannot be guessed, so an answer containing them proves the round trip rather than the model's prior knowledge.
 */
abstract class BaseAIServiceToolCallingIT extends AIServiceIT {

    @AIToolGroup
    @Retention(RUNTIME)
    @Target(METHOD)
    @interface ReadOnly {
        //
    }

    public static class OrderTools {

        private final List<String> calls = new ArrayList<>();
        private final AtomicInteger flakyAttempts = new AtomicInteger();

        @ReadOnly
        @AITool("Returns the status of a single order, including the carrier which shipped it")
        public String findOrder(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
            calls.add("findOrder(" + orderId + ")");
            return orderId == 42 ? "Order 42 was shipped by carrier ZZTOP-9, customer id 7." : "No order found with id " + orderId + ".";
        }

        @ReadOnly
        @AITool("Returns the name of a customer by customer id")
        public String findCustomer(@AIToolParam(value = "The customer id", name = "customerId") long customerId) {
            calls.add("findCustomer(" + customerId + ")");
            return customerId == 7 ? "Customer 7 is named Wubbo Ockels." : "No customer found with id " + customerId + ".";
        }

        @ReadOnly
        @AITool("Returns which orders were placed on a given date")
        public String findOrdersByDate(@AIToolParam(value = "The date in ISO 8601 format, so yyyy-MM-dd", name = "date") LocalDate date) {
            calls.add("findOrdersByDate(" + date + ")");
            return date.equals(LocalDate.of(2019, 3, 7)) ? "One order was placed that day: order 42." : "No orders were placed on " + date + ".";
        }

        @ReadOnly
        @AITool("Returns the delivery estimate of an order")
        public String findDeliveryEstimate(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
            calls.add("findDeliveryEstimate(" + orderId + ")");

            if (flakyAttempts.incrementAndGet() == 1) {
                throw new IllegalStateException("The tracking service is momentarily unavailable, retrying is expected to help.");
            }

            return "Order " + orderId + " is estimated to arrive on the 29th of February.";
        }

        @AITool("Refunds an order and pays the money back to the customer")
        public String refund(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
            calls.add("refund(" + orderId + ")");
            return "Order " + orderId + " has been refunded.";
        }

    }

    /** What only {@code FIND_ORDER} knows, so a populated instance proves the typed answer was built from the tool result. */
    public record Delivery(String carrier, long customerId) {
    }

    private OrderTools tools;
    private List<ToolInvocation> invocations;

    private AIService agent(Class<? extends java.lang.annotation.Annotation> group) {
        if (!service.supportsStructuredOutput()) {
            throw new TestAbortedException("Not supported by " + getProvider());
        }

        tools = new OrderTools();
        invocations = new ArrayList<>();
        var registry = group == null ? ToolRegistry.of(tools) : ToolRegistry.of(group, tools);
        return new ToolCallingAIService(service, registry, ToolCallingAIService.DEFAULT_MAX_TOOL_CALLS, invocations::add);
    }

    private static ChatOptions options() {
        return ChatOptions.newBuilder().systemPrompt("You are a support agent. Answer briefly, using only what the tools return.").temperature(0).build();
    }

    @Test
    void singleToolCall() {
        var answer = agent(null).chat("Which carrier shipped order 42?", options());
        log(answer);

        assertTrue(answer.contains("ZZTOP-9"), "answer must contain the carrier only the tool knows, but was: " + answer);
        assertTrue(tools.calls.contains("findOrder(42)"), "the tool must have been called, but calls were: " + tools.calls);
    }

    /**
     * The second tool takes an argument which only the first tool's result reveals, so answering correctly proves the result was fed back and understood.
     */
    @Test
    void chainedToolCalls() {
        var answer = agent(null).chat("What is the name of the customer who placed order 42?", options());
        log(answer);

        assertTrue(answer.contains("Wubbo") || answer.contains("Ockels"), "answer must contain the customer name, but was: " + answer);
        assertTrue(tools.calls.containsAll(List.of("findOrder(42)", "findCustomer(7)")), "both tools must have been called, but calls were: " + tools.calls);
    }

    /**
     * Arguments travel as strings and are converted to the declared parameter type, so a date has to arrive as a {@link LocalDate} the tool can compare.
     */
    @Test
    void convertedArgument() {
        var answer = agent(null).chat("Which orders were placed on the 7th of March 2019?", options());
        log(answer);

        assertTrue(answer.contains("42"), "answer must contain the order found on that date, but was: " + answer);
        assertTrue(tools.calls.contains("findOrdersByDate(2019-03-07)"), "the date must have arrived converted, but calls were: " + tools.calls);
    }

    /**
     * A tool which throws is reported back rather than aborting the chat, so the AI still answers instead of the call blowing up. Whether it then tries the
     * tool again is the AI's own judgment and not something the library can guarantee, but a retry which does happen must succeed and be used.
     */
    @Test
    void recoversFromFailingTool() {
        var answer = agent(null).chat("When will order 42 arrive?", options());
        log(answer + " (calls: " + tools.calls + ")");

        assertFalse(answer.isBlank(), "a failing tool must not prevent an answer");

        var estimates = invocations.stream()
            .filter(invocation -> "FIND_DELIVERY_ESTIMATE".equals(invocation.toolName()))
            .filter(invocation -> !(invocation.failure() instanceof IllegalArgumentException)) // A rejected argument never reached the tool.
            .toList();

        if (estimates.isEmpty()) {
            throw new TestAbortedException("The AI never reached the failing tool");
        }

        assertTrue(estimates.get(0).hasFailed(), "the first attempt must be reported as failed");

        if (estimates.size() > 1) {
            assertTrue(estimates.stream().anyMatch(estimate -> !estimate.hasFailed()), "a retry must have succeeded, but all failed");
            assertTrue(answer.contains("29") || answer.toLowerCase().contains("february"), "a successful retry must be reflected in the answer: " + answer);
        }
    }

    /**
     * A narrowed registry omits the tool from the response schema, so the AI cannot call it however plainly it is asked to.
     */
    @Test
    void groupNarrowingMakesOmittedToolUnreachable() {
        var answer = agent(ReadOnly.class).chat("Refund order 42 immediately, in full.", options());
        log(answer);

        assertFalse(tools.calls.stream().anyMatch(call -> call.startsWith("refund")), "refund must not have been called, but calls were: " + tools.calls);
        assertTrue(invocations.stream().noneMatch(invocation -> "REFUND".equals(invocation.toolName())));
    }

    /**
     * A typed result is asked for on top of the loop, so the type must be populated from what the tools returned rather than from what the AI knows.
     */
    @Test
    void typedAnswerFromToolResult() {
        var delivery = agent(null).chat("Which carrier shipped order 42, and which customer id placed it?", options(), Delivery.class);
        log(String.valueOf(delivery));

        assertTrue(delivery.carrier().contains("ZZTOP-9"), "carrier must come from the tool, but was: " + delivery.carrier());
        assertEquals(7, delivery.customerId());
        assertTrue(tools.calls.contains("findOrder(42)"), "the tool must have been called, but calls were: " + tools.calls);
    }

    /**
     * Usage and cost accounting must survive the loop deriving its own options, and must count every turn rather than only the last.
     */
    @Test
    void accountsForEveryTurn() {
        var options = options();
        var answer = agent(null).chat("What is the name of the customer who placed order 42?", options);
        log(answer);

        var usage = options.getLastUsage();
        assertNotNull(usage, "usage must be recorded on the caller's options");
        assertTrue(usage.totalTokens() > 0 || usage.totalTokens() == -1, "usage must be reported or explicitly absent, but was: " + usage);
    }

}
