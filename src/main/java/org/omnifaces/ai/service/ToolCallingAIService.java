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

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.joining;
import static org.omnifaces.ai.helper.JsonHelper.parseJson;
import static org.omnifaces.ai.tool.ToolRegistry.ANSWER;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.exception.AIToolIterationException;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.tool.AITool;
import org.omnifaces.ai.tool.ToolInvocation;
import org.omnifaces.ai.tool.ToolInvocationException;
import org.omnifaces.ai.tool.ToolRegistry;

/**
 * A decorator which lets the AI call {@link AITool} annotated methods on your own objects before it answers.
 * <p>
 * Each turn the AI either names a tool to call or answers directly. A named tool is invoked, its return value is fed back, and the next turn begins, until the
 * AI answers or {@link #getMaxToolCalls()} is exhausted. A tool which fails is reported back to the AI rather than aborting the call, so it can correct its
 * arguments and try again.
 * <p>
 * This rides on structured output rather than on provider-native function calling, so it behaves identically on every provider. The consequences are that the
 * AI calls one tool per turn, that arguments are converted from strings rather than typed on the wire, and that a caller supplied JSON schema on the untyped
 * methods takes precedence: such a chat passes straight through to the wrapped service without any tool calling. The typed methods do run the loop, and spend
 * one further call on producing the type from what the tools returned.
 * <p>
 * The synchronous {@code chat} methods invoke the tools on the calling thread, so they observe its transaction, scope and security context. The asynchronous
 * ones invoke them on whichever thread completes the provider call, where none of that is in scope; use the synchronous ones for tools which touch a database
 * or a scoped bean.
 * <p>
 * Every turn of the loop is a chat call of its own, so with a {@link ChatOptions#hasMemory() memory-enabled} options each tool call and each tool result lands
 * in the conversation history, and a single question can therefore fill the sliding window. Give the loop its own options when the history is meant to hold
 * only the questions and answers.
 * <p>
 * Although {@link AIService} is {@link java.io.Serializable}, a tool calling service is not: it holds bound methods and an observer. Do not keep one in a
 * passivating scope; inject the plain service and derive this one per call instead.
 * <p>
 * Usage example:
 *
 * <pre>
 *
 * AIService agent = ai.withTools(orderTools);
 * String answer = agent.chat("Where is order 42?");
 * </pre>
 * <p>
 * A tool registry binds live bean instances and the reflective methods it invokes on them, so a tool calling service is not passivation capable. Hold it in a
 * {@code @RequestScoped} or {@code @ApplicationScoped} bean, not in a {@code @SessionScoped} or {@code @ConversationScoped} one. An {@code @ApplicationScoped}
 * holder is safe with request scoped tools, as the registry invokes each tool through its container reference and therefore observes its scope on every call.
 *
 * @author Bauke Scholtz
 * @since 1.6
 * @see AITool
 * @see ToolRegistry
 * @see AIService#withTools(Object...)
 */
public class ToolCallingAIService extends AIServiceWrapper {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(ToolCallingAIService.class.getPackageName());

    /**
     * Default maximum number of tool calls: {@value}, after which the AI must answer.
     * <p>
     * Five covers the chains which occur in practice, such as a lookup, a correction after a failed argument, and a cross-check, while a conversation which is
     * not converging still fails in seconds rather than grinding on. A conversation therefore takes at most six provider calls, and seven when a type is asked
     * for of a memory-enabled one, as the answer it records is then a call of its own.
     */
    public static final int DEFAULT_MAX_TOOL_CALLS = 5;

    /** What the AI is asked on the call which produces the typed answer, when memory already carries the exchange. */
    private static final String TYPED_ANSWER_PROMPT = "Answer the question now, using only what this conversation established.";

    /** The tools offered to the AI. */
    private final ToolRegistry registry;
    /** The maximum number of tool calls before the AI must answer. */
    private final int maxToolCalls;
    /** The observer notified of every tool call, or {@code null} for none. */
    private final Consumer<ToolInvocation> observer;

    /**
     * Creates a tool calling decorator around the given service, capped at {@value #DEFAULT_MAX_TOOL_CALLS} tool calls.
     *
     * @param wrapped The service to decorate.
     * @param registry The tools to offer the AI.
     */
    public ToolCallingAIService(AIService wrapped, ToolRegistry registry) {
        this(wrapped, registry, DEFAULT_MAX_TOOL_CALLS, null);
    }

    /**
     * Creates a tool calling decorator around the given service.
     *
     * @param wrapped The service to decorate.
     * @param registry The tools to offer the AI.
     * @param maxToolCalls The maximum number of tool calls before the AI must answer, at least 1. Exceeding it throws {@link AIToolIterationException} on the
     * untyped methods, and forces the typed answer on the typed ones.
     * @param observer The observer notified of every tool call, or {@code null} for none.
     * @throws IllegalArgumentException If {@code maxToolCalls} is less than 1.
     */
    public ToolCallingAIService(AIService wrapped, ToolRegistry registry, int maxToolCalls, Consumer<ToolInvocation> observer) {
        super(wrapped);

        if (maxToolCalls < 1) {
            throw new IllegalArgumentException("The maxToolCalls must be at least 1, but it was " + maxToolCalls + ".");
        }

        this.registry = requireNonNull(registry, "registry");
        this.maxToolCalls = maxToolCalls;
        this.observer = observer;
    }

    /**
     * Returns the tools offered to the AI.
     *
     * @return The tools offered to the AI, never {@code null}.
     */
    public ToolRegistry getRegistry() {
        return registry;
    }

    /**
     * Returns the maximum number of tool calls before the AI must answer. Exceeding it throws {@link AIToolIterationException} on the untyped methods, and
     * forces the typed answer on the typed ones, as those carry a schema which offers no tool to begin with.
     *
     * @return The maximum number of tool calls before the AI must answer.
     */
    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    @Override
    public CompletableFuture<String> chatAsync(ChatInput input, ChatOptions options) throws AIException {
        if (options.getJsonSchema() != null) {
            return getWrapped().chatAsync(input, options);
        }

        return chatAsync(input, input, toToolOptions(options), 1);
    }

    // The wrapper base class delegates every overload straight to the wrapped service, so each plain text one is routed back through the loop here.

    @Override
    public String chat(String message) throws AIException {
        return chat(ChatInput.newBuilder().message(message).build());
    }

    @Override
    public String chat(ChatInput input) throws AIException {
        return chat(input, ChatOptions.DEFAULT.withSystemPrompt(getChatPrompt()));
    }

    @Override
    public String chat(String message, ChatOptions options) throws AIException {
        return chat(ChatInput.newBuilder().message(message).build(), options);
    }

    @Override
    public String chat(ChatInput input, ChatOptions options) throws AIException {
        return options.getJsonSchema() != null ? getWrapped().chat(input, options) : chatLoop(input, options);
    }

    @Override
    public CompletableFuture<String> chatAsync(String message) throws AIException {
        return chatAsync(ChatInput.newBuilder().message(message).build());
    }

    @Override
    public CompletableFuture<String> chatAsync(ChatInput input) throws AIException {
        return chatAsync(input, ChatOptions.DEFAULT.withSystemPrompt(getChatPrompt()));
    }

    @Override
    public CompletableFuture<String> chatAsync(String message, ChatOptions options) throws AIException {
        return chatAsync(ChatInput.newBuilder().message(message).build(), options);
    }

    @Override
    public <T> T chat(String message, Class<T> type) throws AIException {
        return chat(ChatInput.newBuilder().message(message).build(), type);
    }

    @Override
    public <T> T chat(ChatInput input, Class<T> type) throws AIException {
        return chat(input, ChatOptions.DEFAULT.withSystemPrompt(getChatPrompt()), type);
    }

    @Override
    public <T> T chat(String message, ChatOptions options, Class<T> type) throws AIException {
        return chat(ChatInput.newBuilder().message(message).build(), options, type);
    }

    @Override
    public <T> T chat(ChatInput input, ChatOptions options, Class<T> type) throws AIException {
        var toolOptions = toToolOptions(options);
        var turn = input;

        for (var iteration = 1;; iteration++) {
            if (iteration > maxToolCalls && !options.hasMemory()) {
                return getWrapped().chat(turn, options, type);
            }

            var step = parseJson(getWrapped().chat(turn, toTurnOptions(toolOptions, iteration)));
            var tool = step.getString("tool", ANSWER);

            if (ANSWER.equals(tool) || iteration > maxToolCalls) {
                return getWrapped().chat(toTypedTurn(input, turn, options), options, type);
            }

            turn = toToolResult(tool, toArguments(step), input, turn, toolOptions, iteration == maxToolCalls);
        }
    }

    @Override
    public <T> CompletableFuture<T> chatAsync(String message, Class<T> type) throws AIException {
        return chatAsync(ChatInput.newBuilder().message(message).build(), type);
    }

    @Override
    public <T> CompletableFuture<T> chatAsync(ChatInput input, Class<T> type) throws AIException {
        return chatAsync(input, ChatOptions.DEFAULT.withSystemPrompt(getChatPrompt()), type);
    }

    @Override
    public <T> CompletableFuture<T> chatAsync(String message, ChatOptions options, Class<T> type) throws AIException {
        return chatAsync(ChatInput.newBuilder().message(message).build(), options, type);
    }

    @Override
    public <T> CompletableFuture<T> chatAsync(ChatInput input, ChatOptions options, Class<T> type) throws AIException {
        return chatAsync(input, input, options, toToolOptions(options), type, 1);
    }

    private <T> CompletableFuture<T> chatAsync(ChatInput input, ChatInput turn, ChatOptions options, ChatOptions toolOptions, Class<T> type, int iteration) {
        if (iteration > maxToolCalls && !options.hasMemory()) {
            return getWrapped().chatAsync(turn, options, type);
        }

        return getWrapped().chatAsync(turn, toTurnOptions(toolOptions, iteration)).thenCompose(response -> {
            var step = parseJson(response);
            var tool = step.getString("tool", ANSWER);

            if (ANSWER.equals(tool) || iteration > maxToolCalls) {
                return getWrapped().chatAsync(toTypedTurn(input, turn, options), options, type);
            }

            var next = toToolResult(tool, toArguments(step), input, turn, toolOptions, iteration == maxToolCalls);
            return chatAsync(input, next, options, toolOptions, type, iteration + 1);
        });
    }

    /**
     * Always throws, as the tool the AI picks is only known once its reply is complete. Stream from the wrapped service for a chat without tools.
     *
     * @throws UnsupportedOperationException Always.
     */
    @Override
    public CompletableFuture<Void> chatStream(String message, Consumer<String> onToken) throws AIException {
        throw newStreamingUnsupported();
    }

    /**
     * Always throws, as the tool the AI picks is only known once its reply is complete. Stream from the wrapped service for a chat without tools.
     *
     * @throws UnsupportedOperationException Always.
     */
    @Override
    public CompletableFuture<Void> chatStream(ChatInput input, Consumer<String> onToken) throws AIException {
        throw newStreamingUnsupported();
    }

    /**
     * Always throws, as the tool the AI picks is only known once its reply is complete. Stream from the wrapped service for a chat without tools.
     *
     * @throws UnsupportedOperationException Always.
     */
    @Override
    public CompletableFuture<Void> chatStream(String message, ChatOptions options, Consumer<String> onToken) throws AIException {
        throw newStreamingUnsupported();
    }

    /**
     * Always throws, as the tool the AI picks is only known once its reply is complete. Stream from the wrapped service for a chat without tools.
     *
     * @throws UnsupportedOperationException Always.
     */
    @Override
    public CompletableFuture<Void> chatStream(ChatInput input, ChatOptions options, Consumer<String> onToken) throws AIException {
        throw newStreamingUnsupported();
    }

    private static UnsupportedOperationException newStreamingUnsupported() {
        return new UnsupportedOperationException(
            "Streaming cannot be combined with tool calling, as the AI's choice of tool is only known once its reply is complete."
                + " Stream from the wrapped service for a chat without tools."
        );
    }

    /**
     * Returns the input for the call which produces the typed answer.
     * <p>
     * Without memory the turn is the only thing carrying the exchange, so it travels on as it is. With memory the history carries it instead and repeating the
     * turn would record the same question twice, unless the sliding window has meanwhile evicted that question, in which case restating it is the only way the
     * answering call still knows what was asked.
     */
    private ChatInput toTypedTurn(ChatInput input, ChatInput turn, ChatOptions options) {
        if (!options.hasMemory()) {
            return turn;
        }

        return attach(ChatInput.newBuilder().message(isQuestionRemembered(input, options) ? TYPED_ANSWER_PROMPT : input.getMessage()), input, options).build();
    }

    private static boolean isQuestionRemembered(ChatInput input, ChatOptions options) {
        return options.getHistory().stream().anyMatch(message -> message.role() == Role.USER && message.content().equals(input.getMessage()));
    }

    /**
     * Carries the input's attachments onto the given turn. Images are never replayed from the history by any provider, so they always travel again. A file
     * travels again only where the history will not carry it: a provider which replays uploaded references would otherwise both upload it once per turn and
     * render one copy per turn it was attached to.
     */
    private ChatInput.Builder attach(ChatInput.Builder builder, ChatInput input, ChatOptions options) {
        input.getImages().forEach(builder::attach);

        if (!options.hasMemory() || !supportsFileAttachmentsInHistory()) {
            input.getFiles().forEach(builder::attach);
        }

        return builder;
    }

    /**
     * Returns the options for the given turn. The turn after the last permitted tool call is constrained to answering, as a model which keeps calling tools
     * ignores being asked to stop but cannot emit a tool name which the schema does not offer it.
     */
    private ChatOptions toTurnOptions(ChatOptions toolOptions, int iteration) {
        return iteration <= maxToolCalls ? toolOptions : toolOptions.withJsonSchema(registry.getAnswerSchema());
    }

    private ChatOptions toToolOptions(ChatOptions options) {
        var systemPrompt = options.getSystemPrompt() == null ? registry.getManifest() : options.getSystemPrompt() + "\n\n" + registry.getManifest();
        return options.withSystemPrompt(systemPrompt).withJsonSchema(registry.getResponseSchema());
    }

    /**
     * Runs the loop synchronously, so that the tools are invoked on the caller's own thread and therefore observe its transaction, scope and security context.
     */
    private String chatLoop(ChatInput input, ChatOptions options) {
        var toolOptions = toToolOptions(options);
        var turn = input;

        for (var iteration = 1;; iteration++) {
            var step = parseJson(getWrapped().chat(turn, toTurnOptions(toolOptions, iteration)));
            var tool = step.getString("tool", ANSWER);

            if (ANSWER.equals(tool)) {
                return step.getString("answer", "");
            }

            if (iteration > maxToolCalls) {
                throw new AIToolIterationException(maxToolCalls, tool);
            }

            turn = toToolResult(tool, toArguments(step), input, turn, toolOptions, iteration == maxToolCalls);
        }
    }

    private CompletableFuture<String> chatAsync(ChatInput input, ChatInput turn, ChatOptions toolOptions, int iteration) {
        return getWrapped().chatAsync(turn, toTurnOptions(toolOptions, iteration)).thenCompose(response -> {
            var step = parseJson(response);
            var tool = step.getString("tool", ANSWER);

            if (ANSWER.equals(tool)) {
                return completedFuture(step.getString("answer", ""));
            }

            if (iteration > maxToolCalls) {
                return failedFuture(new AIToolIterationException(maxToolCalls, tool));
            }

            var next = toToolResult(tool, toArguments(step), input, turn, toolOptions, iteration == maxToolCalls);
            return chatAsync(input, next, toolOptions, iteration + 1);
        });
    }

    private ChatInput toToolResult(String tool, Map<String, String> arguments, ChatInput input, ChatInput turn, ChatOptions options, boolean lastTurn) {
        String message;

        try {
            var result = registry.invoke(tool, arguments);
            notifyObserver(new ToolInvocation(tool, arguments, result, null));
            message = "You called " + tool + toSignature(arguments) + " and it returned: " + result
                + "\nDo not call it again with the same arguments. Answer the question now if this is enough, otherwise call another tool.";
        }
        catch (IllegalArgumentException e) {
            notifyObserver(new ToolInvocation(tool, arguments, null, e));
            // Only ever echoes what the AI itself supplied, so it can be quoted back to it verbatim.
            message = "You called " + tool + toSignature(arguments) + " and it was rejected: " + e.getMessage() + "\nCorrect the arguments and try again.";
        }
        catch (ToolInvocationException e) {
            notifyObserver(new ToolInvocation(tool, arguments, null, e));
            logger.log(WARNING, e, () -> "The tool " + tool + " threw, reporting it to the AI without its detail.");
            message = "You called " + tool + toSignature(arguments) + " and it did not complete."
                + "\nThis may be temporary, so you may try it once more, otherwise answer without it.";
        }

        if (lastTurn) {
            message += "\nThis is your last turn, so answer the question now with whatever you have. Do not call another tool.";
        }

        // Without memory the wrapped service sees each turn in isolation, so the exchange so far has to travel along in the message itself.
        return attach(ChatInput.newBuilder().message(options.hasMemory() ? message : turn.getMessage() + "\n\n" + message), input, options).build();
    }

    private static String toSignature(Map<String, String> arguments) {
        return arguments.entrySet().stream().map(argument -> argument.getKey() + "=" + argument.getValue()).collect(joining(", ", "(", ")"));
    }

    /**
     * Collects the arguments the AI supplied.
     * <p>
     * The schema asks for name/value pairs, but a provider which does not enforce it lets the model improvise, so an object keyed by parameter name and a pair
     * of positional values are accepted just as well. Values are taken as text whatever their JSON type, as a model often answers a numeric parameter with a
     * number rather than with the string the schema asks for.
     */
    private static Map<String, String> toArguments(JsonObject step) {
        var arguments = new LinkedHashMap<String, String>();
        var declared = step.get("arguments");

        if (declared instanceof JsonObject named) {
            putArgument(arguments, named);
        }
        else if (declared instanceof JsonArray array) {
            array.forEach(element -> putArgument(arguments, element));
        }

        return arguments;
    }

    private static void putArgument(Map<String, String> arguments, JsonValue element) {
        if (element instanceof JsonObject argument) {
            if (argument.containsKey("name") && argument.containsKey("value") && argument.size() == 2) {
                arguments.put(toText(argument.get("name")), toText(argument.get("value")));
            }
            else {
                argument.forEach((name, value) -> arguments.put(name, toText(value)));
            }
        }
        else if (element instanceof JsonArray pair && pair.size() >= 2) {
            arguments.put(toText(pair.get(0)), toText(pair.get(1)));
        }
    }

    private static String toText(JsonValue value) {
        if (value.getValueType() == ValueType.NULL) {
            return "";
        }

        return value instanceof JsonString text ? text.getString() : value.toString();
    }

    private void notifyObserver(ToolInvocation invocation) {
        if (observer != null) {
            observer.accept(invocation);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + registry.getToolNames() + " via " + getWrapped() + "]";
    }

}
