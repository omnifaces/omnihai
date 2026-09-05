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
package org.omnifaces.ai;

import static java.lang.System.lineSeparator;
import static java.util.Arrays.stream;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omnifaces.ai.model.AnalyzeVideoOptions;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.Location;
import org.omnifaces.ai.model.GenerateAudioOptions;
import org.omnifaces.ai.model.GenerateImageOptions;
import org.omnifaces.ai.model.GenerateVideoOptions;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.VideoGeneration;
import org.omnifaces.ai.model.VideoGeneration.Job;
import org.omnifaces.ai.model.VideoGeneration.Status;
import org.omnifaces.ai.service.ToolCallingAIService;
import org.omnifaces.ai.tool.AITool;
import org.omnifaces.ai.tool.AIToolGroup;
import org.omnifaces.ai.tool.AIToolParam;
import org.omnifaces.ai.tool.ToolRegistry;

/**
 * Nearly every operation of {@link AIService} is a convenience overload which fills in a default and hands the call on, so that an implementation has a handful
 * of operations to write rather than a hundred. The contract of those overloads is that they delegate: one which computes an answer of its own would leave an
 * implementation unable to override the behavior at its own operation.
 */
class AIServiceDefaultMethodsTest {

    /**
     * The default methods which answer without asking the service, and are therefore covered one by one below.
     */
    private static final Set<String> NON_DELEGATING = Set.of(
        "withTools(Object[])", "withTools(Class, Object[])", "withTools(ToolRegistry)", "getModelVersion()",
        "supportsStreaming()", "supportsFileAttachments()", "supportsFileAttachmentsInHistory()", "supportsStructuredOutput()",
        "supportsWebSearch()", "supportsReasoningEffort()", "supportsSamplingParameters()"
    );

    @TempDir
    private Path tempDir;

    @Test
    void everyDelegatingDefaultMethod_handsTheCallOnToAnotherOperation() {
        var failures = new ArrayList<String>();

        for (var method : delegatingDefaultMethods()) {
            var calls = new ArrayList<Method>();
            var service = newRecordingService(method, calls);

            try {
                method.invoke(service, stream(method.getParameterTypes()).map(this::cannedArgument).toArray());
            }
            catch (IllegalAccessException | InvocationTargetException e) {
                failures.add(toSignature(method) + ": threw unexpectedly: " + e.getCause());
                continue;
            }

            if (calls.isEmpty()) {
                failures.add(toSignature(method) + ": answers of its own instead of handing the call on");
            }
        }

        if (!failures.isEmpty()) {
            fail("Delegation issues in AIService:" + lineSeparator() + failures.stream().sorted().map(f -> "  - " + f).collect(joining(lineSeparator())));
        }
    }

    @Test
    void withTools_instances_wrapsTheServiceInAToolCallingOne() {
        AIService service = newRecordingService(null, new ArrayList<>());
        assertInstanceOf(ToolCallingAIService.class, service.withTools(new OrderTools()));
    }

    @Test
    void withTools_group_wrapsTheServiceInAToolCallingOne() {
        AIService service = newRecordingService(null, new ArrayList<>());
        assertInstanceOf(ToolCallingAIService.class, service.withTools(ReadOnly.class, new OrderTools()));
    }

    @Test
    void withTools_registry_wrapsTheServiceInAToolCallingOne() {
        AIService service = newRecordingService(null, new ArrayList<>());
        assertInstanceOf(ToolCallingAIService.class, service.withTools(ToolRegistry.of(new OrderTools())));
    }

    /**
     * A service which states no capability of its own has to be taken as serving none, as announcing one it cannot honor would let a caller build a request the
     * provider rejects. The sampling parameters are the exception: every chat model takes them, so an implementation states only that it does not.
     */
    @Test
    void capabilityDefaults_areUnsupportedApartFromTheSamplingParameters() {
        var service = newRecordingService(null, new ArrayList<>());

        assertFalse(service.supportsStreaming());
        assertFalse(service.supportsFileAttachments());
        assertFalse(service.supportsFileAttachmentsInHistory());
        assertFalse(service.supportsStructuredOutput());
        assertFalse(service.supportsWebSearch());
        assertFalse(service.supportsReasoningEffort());
        assertTrue(service.supportsSamplingParameters());
    }

    @Test
    void getModelVersion_isDerivedFromTheModelName() {
        assertEquals(AIModelVersion.of("canned"), newRecordingService(null, new ArrayList<>()).getModelVersion());
    }

    private List<Method> delegatingDefaultMethods() {
        return stream(AIService.class.getMethods())
            .filter(Method::isDefault)
            .filter(not(method -> NON_DELEGATING.contains(toSignature(method))))
            .sorted((one, other) -> toSignature(one).compareTo(toSignature(other)))
            .toList();
    }

    /**
     * Answers a service which runs the body of the given method for real and answers every further operation with a canned value, so that one body at a time is
     * exercised and every operation it reaches is recorded rather than run. A {@code null} method runs every default method for real instead.
     */
    private static AIService newRecordingService(Method methodUnderTest, List<Method> calls) {
        var entered = new boolean[] { false };

        return (AIService) Proxy.newProxyInstance(
            AIService.class.getClassLoader(), new Class[] { AIService.class }, (proxy, method, args) -> {
                if (method.isDefault() && (methodUnderTest == null || (method.equals(methodUnderTest) && !entered[0]))) {
                    entered[0] = true;
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }

                calls.add(method);
                return cannedReturnValue(method.getReturnType());
            }
        );
    }

    private static Object cannedReturnValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == String.class) {
            return "canned";
        }
        if (type == CompletableFuture.class) {
            return completedFuture(null);
        }
        if (type == List.class) {
            return List.of();
        }
        if (type == Map.class) {
            return Map.of();
        }
        return null;
    }

    private Object cannedArgument(Class<?> type) {
        if (type == int.class) {
            return 1;
        }
        if (type == String.class) {
            return "canned";
        }
        if (type == String[].class) {
            return new String[] { "label" };
        }
        if (type == byte[].class) {
            return new byte[] { 1 };
        }
        if (type == Class.class) {
            return Answer.class;
        }
        if (type == Path.class) {
            return tempDir.resolve("output.bin");
        }
        if (type == List.class) {
            return List.of("label");
        }
        if (type == Consumer.class) {
            return (Consumer<String>) token -> {
                /* the token is of no interest here */ };
        }
        if (type == ChatInput.class) {
            return ChatInput.newBuilder().message("canned").build();
        }
        if (type == ChatOptions.class) {
            return ChatOptions.DEFAULT;
        }
        if (type == Location.class) {
            return Location.GLOBAL;
        }
        if (type == ModerationOptions.class) {
            return ModerationOptions.DEFAULT;
        }
        if (type == GenerateImageOptions.class) {
            return GenerateImageOptions.DEFAULT;
        }
        if (type == GenerateAudioOptions.class) {
            return GenerateAudioOptions.DEFAULT;
        }
        if (type == AnalyzeVideoOptions.class) {
            return AnalyzeVideoOptions.DEFAULT;
        }
        if (type == GenerateVideoOptions.class) {
            return GenerateVideoOptions.DEFAULT;
        }
        return null;
    }

    private static String toSignature(Method method) {
        return method.getName() + "(" + stream(method.getParameterTypes()).map(Class::getSimpleName).collect(joining(", ")) + ")";
    }

    /** The structured output type which the typed overloads build a schema of. */
    public record Answer(String text) {
    }

    @AIToolGroup
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    public @interface ReadOnly {
    }

    public static class OrderTools {

        @AITool("Returns the status of an order.")
        @ReadOnly
        public String getOrderStatus(@AIToolParam("The order id.") String orderId) {
            return "shipped";
        }

    }

    // =================================================================================================================
    // Typed answers
    // =================================================================================================================

    /**
     * A typed overload asks for the answer as JSON and hands back the object it states, so the caller never sees the JSON.
     */
    @Test
    void chat_typedOverloads_parseTheAnswerIntoTheRequestedType() {
        var service = newAnsweringService();

        assertEquals(new Answer("hi"), service.chat("Say hi", ChatOptions.DEFAULT, Answer.class));
        assertEquals(new Answer("hi"), service.chat(ChatInput.newBuilder().message("Say hi").build(), ChatOptions.DEFAULT, Answer.class));
    }

    @Test
    void webSearch_typedOverload_parsesTheAnswerIntoTheRequestedType() {
        assertEquals(new Answer("hi"), newAnsweringService().webSearch("Say hi", Location.GLOBAL, Answer.class));
    }

    /**
     * A generated video is written out where the caller asked for it, so the caller never handles the stream itself.
     */
    @Test
    void generateVideo_toAPath_writesTheGeneratedVideoThere() throws IOException {
        var content = new byte[] { 1, 2, 3 };
        var job = new Job("job-1", Status.COMPLETED, null, null, null);
        var generation = new VideoGeneration(job, GenerateVideoOptions.DEFAULT, new StubVideoSource(content));
        var path = tempDir.resolve("generated.mp4");

        newVideoService(generation).generateVideo("A cat", path);

        assertArrayEquals(content, Files.readAllBytes(path));
    }

    private static AIService newVideoService(VideoGeneration generation) {
        return (AIService) Proxy.newProxyInstance(
            AIService.class.getClassLoader(), new Class[] { AIService.class }, (proxy, method, args) -> {
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }

                return method.getReturnType() == CompletableFuture.class ? completedFuture(generation) : cannedReturnValue(method.getReturnType());
            }
        );
    }

    /** Hands out the generated video as a stream, standing in for the download the AI provider serves. */
    private record StubVideoSource(byte[] content) implements VideoGeneration.Source {

        @Override
        public Job pollVideo(Job job) {
            return job;
        }

        @Override
        public InputStream downloadVideo(Job job) {
            return new ByteArrayInputStream(content);
        }

        @Override
        public CompletableFuture<Job> awaitVideoCompletion(Job job, GenerateVideoOptions options) {
            return completedFuture(job);
        }

    }

    /**
     * Answers every operation the typed overloads reach with a JSON object stating the answer, so that the parsing step runs for real.
     */
    private static AIService newAnsweringService() {
        return (AIService) Proxy.newProxyInstance(
            AIService.class.getClassLoader(), new Class[] { AIService.class }, (proxy, method, args) -> {
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }

                return method.getReturnType() == CompletableFuture.class ? completedFuture("{\"text\":\"hi\"}") : cannedReturnValue(method.getReturnType());
            }
        );
    }

}
