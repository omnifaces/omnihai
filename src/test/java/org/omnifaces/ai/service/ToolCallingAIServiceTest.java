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

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIToolIterationException;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.tool.AITool;
import org.omnifaces.ai.tool.AIToolParam;
import org.omnifaces.ai.tool.ToolInvocation;
import org.omnifaces.ai.tool.ToolParam;
import org.omnifaces.ai.tool.ToolRegistry;

class ToolCallingAIServiceTest {

    public static class OrderTools {

        @AITool("Looks up a single order by id")
        public String findOrder(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
            return "order " + orderId + " is shipped";
        }

        @AITool("Always fails")
        public String explode() {
            throw new IllegalStateException("boom");
        }

        @AITool("Always fails hard")
        public String collapse() {
            throw new StackOverflowError("simulated");
        }

    }

    private final List<ChatInput> requests = new ArrayList<>();

    /**
     * Returns a service which replies with the given scripted responses in order, recording what it was asked.
     */
    private AIService scripted(String... responses) {
        var wrapped = mock(AIService.class);
        var remaining = new ArrayList<>(List.of(responses));

        when(wrapped.chatAsync(any(ChatInput.class), any(ChatOptions.class))).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            return completedFuture(remaining.remove(0));
        });

        when(wrapped.chat(any(ChatInput.class), any(ChatOptions.class))).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            return remaining.remove(0);
        });

        return wrapped;
    }

    private AIService withTools(AIService wrapped) {
        return new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools()));
    }

    private static String toolCall(String tool) {
        return "{\"tool\":\"" + tool + "\",\"arguments\":[],\"answer\":\"\"}";
    }

    private static String toolCall(String tool, String name, String value) {
        return "{\"tool\":\"" + tool + "\",\"arguments\":[{\"name\":\"" + name + "\",\"value\":\"" + value + "\"}],\"answer\":\"\"}";
    }

    private static String answer(String answer) {
        return "{\"tool\":\"ANSWER\",\"arguments\":[],\"answer\":\"" + answer + "\"}";
    }

    // Loop ------------------------------------------------------------------------------------------------------------

    @Test
    void chat_whenAiAnswersImmediately_returnsTheAnswer() {
        var agent = withTools(scripted(answer("42")));

        assertEquals("42", agent.chat("What is the answer?"));
        assertEquals(1, requests.size());
    }

    /**
     * A named tool is invoked and its return value is fed back, so the answer can be based on data the AI never had.
     */
    @Test
    void chat_whenAiCallsTool_feedsTheResultBack() {
        var agent = withTools(scripted(toolCall("OrderTools_findOrder", "orderId", "42"), answer("It is shipped.")));

        assertEquals("It is shipped.", agent.chat("Where is order 42?"));
        assertEquals(2, requests.size());
        assertTrue(
            requests.get(1).getMessage().contains("You called OrderTools_findOrder(orderId=42) and it returned: order 42 is shipped"),
            requests.get(1).getMessage()
        );
    }

    /**
     * A model which cannot see its own previous turn re-calls the tool it just called, so the result has to state that the call already happened and that
     * repeating it is pointless.
     */
    @Test
    void chat_tellsTheAiNotToRepeatACallItAlreadyMade() {
        var agent = withTools(scripted(toolCall("OrderTools_findOrder", "orderId", "42"), answer("It is shipped.")));

        agent.chat("Where is order 42?");

        assertTrue(requests.get(1).getMessage().contains("Do not call it again with the same arguments"), requests.get(1).getMessage());
    }

    /**
     * Reaching the cap without an answer is a failure, so the final turn asks for one rather than spending itself on another tool call.
     */
    @Test
    void chat_asksForAnAnswerOnTheFinalTurn() {
        var wrapped = scripted(toolCall("OrderTools_findOrder", "orderId", "42"), answer("It is shipped."));
        var agent = new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools()), 1, null);

        agent.chat("Where is order 42?");

        assertTrue(requests.get(1).getMessage().contains("This is your last turn"), requests.get(1).getMessage());
    }

    /**
     * A model which keeps calling tools cannot be talked out of it, so the final turn offers it no tool to name.
     */
    @Test
    void chat_offersNoToolOnTheFinalTurn() {
        var schemas = new ArrayList<JsonObject>();
        var wrapped = mock(AIService.class);
        var remaining = new ArrayList<>(List.of(toolCall("OrderTools_findOrder", "orderId", "42"), answer("It is shipped.")));

        when(wrapped.chat(any(ChatInput.class), any(ChatOptions.class))).thenAnswer(invocation -> {
            schemas.add(((ChatOptions) invocation.getArgument(1)).getJsonSchema());
            return remaining.remove(0);
        });

        new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools()), 1, null).chat("Where is order 42?");

        // Sorted by name, then ANSWER.
        assertEquals(List.of("OrderTools_collapse", "OrderTools_explode", "OrderTools_findOrder", "ANSWER"), toolNames(schemas.get(0)));
        assertEquals(List.of("ANSWER"), toolNames(schemas.get(1)));
    }

    private static List<String> toolNames(JsonObject schema) {
        return schema.getJsonObject("properties").getJsonObject("tool").getJsonArray("enum").getValuesAs(JsonString.class)
            .stream().map(JsonString::getString).toList();
    }

    /**
     * Both loops must constrain the final turn identically, as they are written twice and would otherwise drift.
     */
    @Test
    void chatAsync_offersNoToolOnTheFinalTurn() {
        var schemas = new ArrayList<JsonObject>();
        var wrapped = mock(AIService.class);
        var remaining = new ArrayList<>(List.of(toolCall("OrderTools_findOrder", "orderId", "42"), answer("It is shipped.")));

        when(wrapped.chatAsync(any(ChatInput.class), any(ChatOptions.class))).thenAnswer(invocation -> {
            schemas.add(((ChatOptions) invocation.getArgument(1)).getJsonSchema());
            return completedFuture(remaining.remove(0));
        });

        new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools()), 1, null).chatAsync("Where is order 42?").join();

        assertEquals(List.of("OrderTools_collapse", "OrderTools_explode", "OrderTools_findOrder", "ANSWER"), toolNames(schemas.get(0)));
        assertEquals(List.of("ANSWER"), toolNames(schemas.get(1)));
    }

    /**
     * The plain {@code chat(String)} overload must run the loop too; the decorator base class would otherwise delegate it straight past the tools.
     */
    @Test
    void chat_viaEveryPlainTextOverload_runsTheLoop() {
        var input = ChatInput.newBuilder().message("Where is order 42?").build();
        var agent = withTools(scripted(answer("one"), answer("two"), answer("three"), answer("four")));

        assertEquals("one", agent.chat("Where is order 42?"));
        assertEquals("two", agent.chat(input));
        assertEquals("three", agent.chat("Where is order 42?", ChatOptions.DEFAULT));
        assertEquals("four", agent.chat(input, ChatOptions.DEFAULT));
    }

    /**
     * A failing tool is reported back to the AI rather than aborting the call, so it can correct its arguments and try again.
     */
    @Test
    void chat_whenToolFails_feedsTheFailureBack() {
        var agent = withTools(scripted(toolCall("OrderTools_explode"), answer("Could not check.")));

        assertEquals("Could not check.", agent.chat("Explode please"));
        assertTrue(
            requests.get(1).getMessage().contains("You called OrderTools_explode() and it did not complete"),
            requests.get(1).getMessage()
        );
    }

    @Test
    void chat_whenArgumentIsUnconvertible_feedsTheFailureBack() {
        var agent = withTools(scripted(toolCall("OrderTools_findOrder", "orderId", "yesterday"), answer("Could not check.")));

        assertEquals("Could not check.", agent.chat("Where is my order?"));
        assertTrue(
            requests.get(1).getMessage().contains("You called OrderTools_findOrder(orderId=yesterday) and it was rejected"), requests.get(1).getMessage()
        );
    }

    /**
     * A conversation which keeps calling tools without converging is bounded, so it cannot run up latency and spend indefinitely.
     */
    @Test
    void chat_whenAiNeverAnswers_throwsAtTheIterationCap() {
        var wrapped = scripted(toolCall("OrderTools_findOrder", "orderId", "42"), toolCall("OrderTools_findOrder", "orderId", "42"));
        var agent = new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools()), 1, null);

        var exception = assertThrows(AIToolIterationException.class, () -> agent.chat("Where is order 42?"));

        assertEquals(1, exception.getMaxToolCalls());
    }

    public record Answer(String carrier) {
    }

    /**
     * A typed result is built from what the tools returned: the loop runs on its own schema, and the turn which would have answered is asked again for the
     * caller's type instead.
     */
    @Test
    void chat_withType_runsTheLoopAndAnswersTyped() {
        var wrapped = mock(AIService.class);
        when(wrapped.chat(any(ChatInput.class), any(ChatOptions.class))).thenReturn(toolCall("OrderTools_findOrder", "orderId", "42"));
        when(wrapped.chat(any(ChatInput.class), any(ChatOptions.class), eq(Answer.class))).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            return new Answer("ZZTOP-9");
        });

        var answer = new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools())).chat("Which carrier shipped order 42?", Answer.class);

        assertEquals(new Answer("ZZTOP-9"), answer);
        assertTrue(requests.get(0).getMessage().contains("You called OrderTools_findOrder(orderId=42)"), requests.get(0).getMessage());
    }

    /**
     * The asynchronous typed path must run the same loop and hand the tool results to the same extra call as the synchronous one.
     */
    @Test
    void chatAsync_withType_runsTheLoopAndAnswersTyped() {
        var wrapped = mock(AIService.class);
        var remaining = new ArrayList<>(List.of(toolCall("OrderTools_findOrder", "orderId", "42"), answer("done")));
        when(wrapped.chatAsync(any(ChatInput.class), any(ChatOptions.class))).thenAnswer(invocation -> completedFuture(remaining.remove(0)));
        when(wrapped.chatAsync(any(ChatInput.class), any(ChatOptions.class), eq(Answer.class))).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            return completedFuture(new Answer("ZZTOP-9"));
        });

        var agent = new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools()));

        assertEquals(new Answer("ZZTOP-9"), agent.chatAsync("Which carrier shipped order 42?", Answer.class).join());
        assertTrue(requests.get(0).getMessage().contains("You called OrderTools_findOrder(orderId=42)"), requests.get(0).getMessage());
    }

    /**
     * Reaching the cap forces the typed answer rather than throwing, as the typed call carries a schema which offers no tool to begin with.
     */
    @Test
    void chat_withType_whenAiNeverAnswers_answersTypedAtTheCap() {
        var wrapped = mock(AIService.class);
        when(wrapped.chat(any(ChatInput.class), any(ChatOptions.class))).thenReturn(toolCall("OrderTools_findOrder", "orderId", "42"));
        when(wrapped.chat(any(ChatInput.class), any(ChatOptions.class), eq(Answer.class))).thenReturn(new Answer("ZZTOP-9"));

        var agent = new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools()), 1, null);

        assertEquals(new Answer("ZZTOP-9"), agent.chat("Which carrier shipped order 42?", Answer.class));
    }

    /**
     * Memory carries the exchange, so the call which produces the type asks for the answer rather than repeating the question it would otherwise record twice.
     */
    @Test
    void chat_withType_andMemory_asksForTheAnswerWhileTheQuestionIsRemembered() {
        var options = ChatOptions.newBuilder().withMemory(50).build();
        options.recordMessage(Role.USER, "Where is order 42?");

        var typedTurn = typedTurnOf(options);

        assertTrue(typedTurn.getMessage().startsWith("Answer the question now"), typedTurn.getMessage());
    }

    /**
     * The history is a sliding window, so a long enough loop evicts the question. Restating it is then the only way the answering call still knows what was
     * asked, however much it duplicates.
     */
    @Test
    void chat_withType_andMemory_restatesTheQuestionOnceEvicted() {
        var typedTurn = typedTurnOf(ChatOptions.newBuilder().withMemory(50).build());

        assertEquals("Where is order 42?", typedTurn.getMessage());
    }

    private ChatInput typedTurnOf(ChatOptions options) {
        var typedTurns = new ArrayList<ChatInput>();
        var wrapped = mock(AIService.class);
        when(wrapped.chat(any(ChatInput.class), any(ChatOptions.class))).thenReturn(toolCall("OrderTools_findOrder", "orderId", "42"), answer("done"));
        when(wrapped.chat(any(ChatInput.class), any(ChatOptions.class), eq(Answer.class))).thenAnswer(invocation -> {
            typedTurns.add(invocation.getArgument(0));
            return new Answer("ZZTOP-9");
        });

        new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools())).chat("Where is order 42?", options, Answer.class);

        return typedTurns.get(0);
    }

    @Test
    void chatAsync_withType_whenAiNeverAnswers_answersTypedAtTheCap() {
        var wrapped = mock(AIService.class);
        when(wrapped.chatAsync(any(ChatInput.class), any(ChatOptions.class))).thenReturn(completedFuture(toolCall("OrderTools_findOrder", "orderId", "42")));
        when(wrapped.chatAsync(any(ChatInput.class), any(ChatOptions.class), eq(Answer.class))).thenReturn(completedFuture(new Answer("ZZTOP-9")));

        var agent = new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools()), 1, null);

        assertEquals(new Answer("ZZTOP-9"), agent.chatAsync("Which carrier shipped order 42?", Answer.class).join());
    }

    /**
     * A caller asking for its own structured output gets it, rather than the tool loop's schema.
     */
    @Test
    void chatAsync_whenCallerSuppliedItsOwnSchema_passesThrough() {
        var agent = withTools(scripted("{\"answer\":\"passed through\"}"));
        var options = ChatOptions.newBuilder().jsonSchema(jakarta.json.Json.createObjectBuilder().add("type", "object").build()).build();

        assertEquals("{\"answer\":\"passed through\"}", agent.chat("Anything", options));
    }

    // Observer --------------------------------------------------------------------------------------------------------

    @Test
    void chat_notifiesObserverOfEveryToolCall() {
        var invocations = new ArrayList<ToolInvocation>();
        var wrapped = scripted(toolCall("OrderTools_findOrder", "orderId", "42"), toolCall("OrderTools_explode"), answer("Done."));
        var agent = new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools()), 5, invocations::add);

        agent.chat("Where is order 42?");

        assertEquals(2, invocations.size());
        assertEquals("OrderTools_findOrder", invocations.get(0).toolName());
        assertFalse(invocations.get(0).hasFailed());
        assertEquals("42", invocations.get(0).arguments().get("orderId"));
        assertTrue(invocations.get(1).hasFailed());
        assertNotNull(invocations.get(1).failure());
    }

    // Programmatically declared tools ----------------------------------------------------------------------------------

    /**
     * The builder is the programmatic counterpart of the annotation, so a lambda declared tool behaves the same as an annotated method.
     */
    @Test
    void chat_withProgrammaticallyDeclaredTool_callsTheLambda() {
        var tools = ToolRegistry.newBuilder()
            .add(
                "OrderTools_findOrder", "Looks up a single order by id", (Long orderId) -> "order " + orderId,
                ToolParam.of(long.class, "orderId", "The order id")
            )
            .build();
        var agent = new ToolCallingAIService(scripted(toolCall("OrderTools_findOrder", "orderId", "42"), answer("Found it.")), tools);

        assertEquals("Found it.", agent.chat("Where is order 42?"));
        assertTrue(
            requests.get(1).getMessage().contains("You called OrderTools_findOrder(orderId=42) and it returned: order 42"), requests.get(1).getMessage()
        );
    }

    /**
     * A tool taking no arguments needs no metadata beyond its name and description, so a plain method reference suffices.
     */
    @Test
    void chat_withZeroArgumentMethodReference_callsIt() {
        var tools = ToolRegistry.newBuilder().add("NOW", "Returns the current status", () -> "all systems nominal").build();
        var agent = new ToolCallingAIService(scripted(toolCall("NOW"), answer("Nominal.")), tools);

        assertEquals("Nominal.", agent.chat("Status?"));
        assertTrue(requests.get(1).getMessage().contains("You called NOW() and it returned: all systems nominal"), requests.get(1).getMessage());
    }

    /**
     * The synchronous path must invoke the tool on the caller's own thread, as that is the only thread whose transaction, scope and security context the tool
     * can observe.
     */
    @Test
    void chat_invokesToolOnTheCallingThread() throws Exception {
        var toolThread = new AtomicReference<Thread>();
        var tools = ToolRegistry.newBuilder().add("NOW", "Returns the current status", () -> {
            toolThread.set(Thread.currentThread());
            return "nominal";
        }).build();

        var wrapped = mock(AIService.class);
        var remaining = new ArrayList<>(List.of(toolCall("NOW", "ignored", ""), answer("Done.")));
        var executor = Executors.newSingleThreadExecutor();

        try {
            // A provider whose response arrives on another thread, as every bundled provider's does.
            when(wrapped.chat(any(ChatInput.class), any(ChatOptions.class)))
                .thenAnswer(invocation -> executor.submit(() -> remaining.remove(0)).get());

            new ToolCallingAIService(wrapped, tools).chat("Status?");
        }
        finally {
            executor.shutdown();
        }

        assertSame(Thread.currentThread(), toolThread.get());
    }

    /**
     * A provider which does not enforce the schema may omit the arguments entirely; that must reach the AI as a missing argument it can correct, not as a
     * failed chat.
     */
    @Test
    void chat_whenArgumentsAreOmitted_feedsTheFailureBack() {
        var agent = withTools(scripted("{\"tool\":\"OrderTools_findOrder\",\"answer\":\"\"}", answer("Could not check.")));

        assertEquals("Could not check.", agent.chat("Where is my order?"));
        assertTrue(requests.get(1).getMessage().contains("You called OrderTools_findOrder() and it was rejected"), requests.get(1).getMessage());
    }

    /**
     * A provider which does not enforce the schema lets the model improvise the argument shape, and every shape which unambiguously carries the name and the
     * value must still reach the tool, as a rejected call otherwise burns an iteration on a weaker model without teaching it anything.
     */
    @ParameterizedTest
    @ValueSource(
        strings = {
            "[{\"name\":\"orderId\",\"value\":\"42\"}]", // As the schema asks.
            "[{\"name\":\"orderId\",\"value\":42}]", // Numeric value rather than the string the schema asks for.
            "[[\"orderId\",\"42\"]]", // Positional pair.
            "[{\"orderId\":\"42\"}]", // Keyed by parameter name.
            "{\"orderId\":\"42\"}" // A single object rather than an array.
        }
    )
    void chat_acceptsEveryUnambiguousArgumentShape(String arguments) {
        var agent = withTools(scripted("{\"tool\":\"OrderTools_findOrder\",\"arguments\":" + arguments + ",\"answer\":\"\"}", answer("Found it.")));

        assertEquals("Found it.", agent.chat("Where is order 42?"));
        assertTrue(
            requests.get(1).getMessage().contains("You called OrderTools_findOrder(orderId=42) and it returned: order 42 is shipped"),
            requests.get(1).getMessage()
        );
    }

    /**
     * A tool parameter called {@code name} collides with the key of the pair shape, so a pair is only a pair when it carries a value alongside it.
     */
    @Test
    void chat_withToolParameterCalledName_reachesTheTool() {
        var tools = ToolRegistry.newBuilder()
            .add("FIND_PERSON", "Looks up a person by name", (String name) -> "person " + name, ToolParam.of(String.class, "name", "The person name"))
            .build();
        var wrapped = scripted("{\"tool\":\"FIND_PERSON\",\"arguments\":[{\"name\":\"name\",\"value\":\"Wubbo\"}],\"answer\":\"\"}", answer("Found."));

        assertEquals("Found.", new ToolCallingAIService(wrapped, tools).chat("Who is Wubbo?"));
        assertTrue(requests.get(1).getMessage().contains("returned: person Wubbo"), requests.get(1).getMessage());
    }

    /**
     * A pair carries a name and a value and nothing else, so an object which merely happens to have those keys among others is read as keyed by parameter name.
     */
    @Test
    void chat_withThreeKeyedArgumentObject_readsItAsKeyedByParameterName() {
        var tools = ToolRegistry.newBuilder().add(
            "RECORD", "Records a measurement", (Object[] values) -> "recorded " + values[0] + "=" + values[1] + values[2],
            List.of(
                ToolParam.of(String.class, "name", "The name"), ToolParam.of(String.class, "value", "The value"),
                ToolParam.of(String.class, "unit", "The unit")
            )
        ).build();
        var wrapped = scripted("{\"tool\":\"RECORD\",\"arguments\":{\"name\":\"weight\",\"value\":\"42\",\"unit\":\"kg\"},\"answer\":\"\"}", answer("Done."));

        assertEquals("Done.", new ToolCallingAIService(wrapped, tools).chat("Record it"));
        assertTrue(requests.get(1).getMessage().contains("returned: recorded weight=42kg"), requests.get(1).getMessage());
    }

    /**
     * An exception thrown by a tool must not reach the AI verbatim, as it can carry database, schema or personal detail which the AI may repeat to a user.
     */
    @Test
    void chat_whenToolThrows_doesNotLeakTheExceptionDetailToTheAi() {
        var agent = withTools(scripted(toolCall("OrderTools_explode"), answer("Could not check.")));

        agent.chat("Explode please");

        assertFalse(requests.get(1).getMessage().contains("boom"), requests.get(1).getMessage());
        assertFalse(requests.get(1).getMessage().contains("IllegalStateException"), requests.get(1).getMessage());
    }

    /**
     * Attachments belong to the question, not to a single turn, so they must still be present once a tool result has been fed back.
     */
    @Test
    void chat_carriesAttachmentsIntoEveryTurn() {
        var input = ChatInput.newBuilder().message("What does this show?").attach("payload".getBytes()).build();
        var agent = withTools(scripted(toolCall("OrderTools_findOrder", "orderId", "42"), answer("Done.")));

        agent.chat(input, ChatOptions.DEFAULT);

        assertEquals(input.getFiles().size() + input.getImages().size(), requests.get(1).getFiles().size() + requests.get(1).getImages().size());
        assertEquals(1, requests.get(1).getFiles().size() + requests.get(1).getImages().size());
    }

    /**
     * The asynchronous path runs the same loop as the synchronous one, so it must feed a tool result back and answer from it just the same.
     */
    @Test
    void chatAsync_runsTheLoop() {
        var agent = withTools(scripted(toolCall("OrderTools_findOrder", "orderId", "42"), answer("It is shipped.")));

        assertEquals("It is shipped.", agent.chatAsync("Where is order 42?").join());
        assertEquals(2, requests.size());
    }

    /**
     * Both paths must bound the loop identically, as they otherwise drift.
     */
    @Test
    void chatAsync_whenAiNeverAnswers_throwsAtTheIterationCap() {
        var wrapped = scripted(toolCall("OrderTools_findOrder", "orderId", "42"), toolCall("OrderTools_findOrder", "orderId", "42"));
        var agent = new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools()), 1, null);

        var pending = agent.chatAsync("Where is order 42?");

        var exception = assertThrows(CompletionException.class, pending::join);

        assertInstanceOf(AIToolIterationException.class, exception.getCause());
    }

    /**
     * The tool the AI picks is only known once its reply is complete, so streaming cannot run the loop and must not quietly answer without the tools.
     */
    @Test
    void chatStream_throws() {
        var agent = withTools(scripted(answer("Never reached.")));

        Consumer<String> onToken = token -> {
            //
        };

        var input = ChatInput.newBuilder().message("x").build();

        assertThrows(UnsupportedOperationException.class, () -> agent.chatStream("Where is order 42?", onToken));
        assertThrows(UnsupportedOperationException.class, () -> agent.chatStream(input, onToken));
        assertThrows(UnsupportedOperationException.class, () -> agent.chatStream("Where is order 42?", ChatOptions.DEFAULT, onToken));
        assertThrows(UnsupportedOperationException.class, () -> agent.chatStream(input, ChatOptions.DEFAULT, onToken));
    }

    /**
     * An error is not something the AI can work around, so it must abort the loop rather than be reported as a failed call.
     */
    @Test
    void chat_whenToolThrowsAnError_propagates() {
        var lambda = ToolRegistry.newBuilder().add("BOOM", "Throws an error", () -> {
            throw new StackOverflowError("simulated");
        }).build();

        var lambdaAgent = new ToolCallingAIService(scripted(toolCall("BOOM")), lambda);
        var declaredAgent = withTools(scripted(toolCall("OrderTools_collapse")));

        assertThrows(StackOverflowError.class, () -> lambdaAgent.chat("Boom please"));
        assertThrows(StackOverflowError.class, () -> declaredAgent.chat("Collapse please"));
    }

    /**
     * Only some providers render the uploaded references which the history holds, so every turn carries the attachments itself, memory or not.
     */
    @Test
    void chat_withMemory_carriesAttachmentsIntoEveryTurn() {
        var input = ChatInput.newBuilder().message("What does this show?").attach("payload".getBytes()).build();
        var agent = withTools(scripted(toolCall("OrderTools_findOrder", "orderId", "42"), answer("Done.")));

        agent.chat(input, ChatOptions.newBuilder().withMemory(50).build());

        assertEquals(1, requests.get(1).getFiles().size() + requests.get(1).getImages().size(), "the tool turn must still carry the attachment");
    }

    /**
     * A provider which replays uploaded references from the history would otherwise upload the file again every turn and render one copy per turn it was
     * attached to, so there it is left to the history.
     */
    @Test
    void chat_withMemory_leavesFilesToTheHistoryWhenTheProviderReplaysThem() {
        var input = ChatInput.newBuilder().message("What does this show?").attach("payload".getBytes()).build();
        var wrapped = scripted(toolCall("OrderTools_findOrder", "orderId", "42"), answer("Done."));
        when(wrapped.supportsFileAttachmentsInHistory()).thenReturn(true);

        new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools())).chat(input, ChatOptions.newBuilder().withMemory(50).build());

        assertEquals(0, requests.get(1).getFiles().size(), "the history carries the reference, so the file must not travel again");
    }

    /**
     * Without memory the wrapped service sees each turn in isolation, so the exchange so far travels in the message; with memory it must not, as the history
     * carries it and the question would be recorded twice.
     */
    @Test
    void chat_carriesThePriorTurnOnlyWithoutMemory() {
        withTools(scripted(toolCall("OrderTools_findOrder", "orderId", "42"), answer("Done."))).chat("Where is order 42?", ChatOptions.newBuilder().build());
        var withoutMemory = requests.get(1).getMessage();

        requests.clear();
        withTools(scripted(toolCall("OrderTools_findOrder", "orderId", "42"), answer("Done.")))
            .chat("Where is order 42?", ChatOptions.newBuilder().withMemory(50).build());
        var withMemory = requests.get(1).getMessage();

        assertTrue(withoutMemory.startsWith("Where is order 42?"), withoutMemory);
        assertFalse(withMemory.contains("Where is order 42?"), withMemory);
    }

    /**
     * The call which produces the typed answer is the one which must see the attachment, whether or not the history replays its reference.
     */
    @Test
    void chat_withTypeAndMemory_carriesAttachmentsIntoTheTypedTurn() {
        var typedTurns = new ArrayList<ChatInput>();
        var input = ChatInput.newBuilder().message("What does this show?").attach("payload".getBytes()).build();
        var wrapped = mock(AIService.class);
        when(wrapped.chat(any(ChatInput.class), any(ChatOptions.class))).thenReturn(answer("done"));
        when(wrapped.chat(any(ChatInput.class), any(ChatOptions.class), eq(Answer.class))).thenAnswer(invocation -> {
            typedTurns.add(invocation.getArgument(0));
            return new Answer("ZZTOP-9");
        });

        new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools())).chat(input, ChatOptions.newBuilder().withMemory(50).build(), Answer.class);

        assertEquals(1, typedTurns.get(0).getFiles().size() + typedTurns.get(0).getImages().size());
    }

    /**
     * The cap counts tool calls and nothing else, so N permits exactly N of them. The extra provider call is the turn which is offered no tool at all, which a
     * provider ignoring its schema answers with a tool anyway, hence the exception.
     */
    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3 })
    void chat_permitsExactlyAsManyToolCallsAsTheCap(int maxToolCalls) {
        var invocations = new ArrayList<ToolInvocation>();
        var wrapped = mock(AIService.class);
        when(wrapped.chat(any(ChatInput.class), any(ChatOptions.class))).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            return toolCall("OrderTools_findOrder", "orderId", "42");
        });

        var agent = new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools()), maxToolCalls, invocations::add);

        assertThrows(AIToolIterationException.class, () -> agent.chat("Where is order 42?"));
        assertEquals(maxToolCalls, invocations.size(), "tool calls");
        assertEquals(maxToolCalls + 1, requests.size(), "provider calls");
    }

    /**
     * Asking for a type adds the call which produces it. Without memory the pending tool result travels to that call itself, so the cap costs no turn of its
     * own.
     */
    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3 })
    void chat_withType_permitsExactlyAsManyToolCallsAsTheCap(int maxToolCalls) {
        var invocations = typedRunToCap(maxToolCalls, ChatOptions.newBuilder().build());

        assertEquals(maxToolCalls, invocations.size(), "tool calls");
        assertEquals(maxToolCalls + 1, requests.size(), "provider calls");
    }

    /**
     * With memory the answer has to be recorded in the history, so the cap spends a turn on it before the type is asked for.
     */
    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3 })
    void chat_withTypeAndMemory_spendsOneTurnOnTheAnswerItRecords(int maxToolCalls) {
        var invocations = typedRunToCap(maxToolCalls, ChatOptions.newBuilder().withMemory(50).build());

        assertEquals(maxToolCalls, invocations.size(), "tool calls");
        assertEquals(maxToolCalls + 2, requests.size(), "provider calls");
    }

    private List<ToolInvocation> typedRunToCap(int maxToolCalls, ChatOptions options) {
        var invocations = new ArrayList<ToolInvocation>();
        var wrapped = mock(AIService.class);
        when(wrapped.chat(any(ChatInput.class), any(ChatOptions.class))).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            return toolCall("OrderTools_findOrder", "orderId", "42");
        });
        when(wrapped.chat(any(ChatInput.class), any(ChatOptions.class), eq(Answer.class))).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            return new Answer("ZZTOP-9");
        });

        var agent = new ToolCallingAIService(wrapped, ToolRegistry.of(new OrderTools()), maxToolCalls, invocations::add);

        assertEquals(new Answer("ZZTOP-9"), agent.chat("Where is order 42?", options, Answer.class));

        return invocations;
    }

    @Test
    void constructor_withMaxToolCallsBelowOne_throws() {
        var registry = ToolRegistry.of(new OrderTools());

        assertThrows(IllegalArgumentException.class, () -> new ToolCallingAIService(mock(AIService.class), registry, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new ToolCallingAIService(mock(AIService.class), registry, -1, null));
    }

}
