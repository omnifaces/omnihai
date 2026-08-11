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

import static java.lang.System.lineSeparator;
import static java.util.Arrays.stream;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIService;

/**
 * Validates that {@link InterceptingAIServiceWrapper} funnels every work-performing operation through its interception hooks:
 * <ul>
 * <li>every {@link AIServiceWrapper} operation except the pass-through metadata methods is overridden (not inherited)</li>
 * <li>synchronous operations route through {@link InterceptingAIServiceWrapper#intercept(Function)}</li>
 * <li>asynchronous operations route through {@link InterceptingAIServiceWrapper#interceptAsync(Function)}</li>
 * <li>each operation invokes the exact same overload on the service supplied to the hook</li>
 * </ul>
 * The interceptable set is derived from {@link AIServiceWrapper} rather than from {@link InterceptingAIServiceWrapper}'s own declared methods, so a new
 * {@code AIService} method that {@link InterceptingAIServiceWrapper} forgets to override &mdash; and would therefore be silently inherited as a plain
 * pass-through, bypassing interception &mdash; is detected as a missing override rather than going unnoticed.
 */
class InterceptingAIServiceWrapperTest {

    /** The {@link AIServiceWrapper} methods that are intentionally not intercepted; they retain plain metadata pass-through. */
    private static final Set<String> METADATA_METHODS = Set.of(
        "getName", "getProviderName", "getModelName", "getChatPrompt", "getModelVersion",
        "supportsStreaming", "supportsFileAttachments", "supportsStructuredOutput", "supportsWebSearch", "supportsReasoningEffort", "supportsModality",
        "supportsFileAttachmentsInHistory"
    );

    @Test
    void everyOperationIsOverriddenAndFunnelsThroughHookInvokingSameOverload() {
        var overriddenSignatures = getInterceptedMethodSignatures();
        var failures = new ArrayList<String>();

        for (var method : getInterceptableMethods()) {
            var signature = toSignature(method);

            if (!overriddenSignatures.contains(signature)) {
                failures.add(signature + ": not overridden by InterceptingAIServiceWrapper (inherited pass-through bypasses interception)");
                continue;
            }

            var invokedOnService = new ArrayList<Method>();
            var interceptCalled = new boolean[] { false };
            var interceptAsyncCalled = new boolean[] { false };

            var recordingService = (AIService) Proxy.newProxyInstance(
                AIService.class.getClassLoader(),
                new Class[] { AIService.class },
                (proxy, invoked, args) -> {
                    invokedOnService.add(invoked);
                    return defaultReturnValue(invoked.getReturnType());
                }
            );

            var service = new InterceptingAIServiceWrapper(recordingService) {

                private static final long serialVersionUID = 1L;

                @Override
                protected <R> R intercept(Function<AIService, R> operation) {
                    interceptCalled[0] = true;
                    return operation.apply(getWrapped());
                }

                @Override
                protected <R> CompletableFuture<R> interceptAsync(Function<AIService, CompletableFuture<R>> operation) {
                    interceptAsyncCalled[0] = true;
                    return operation.apply(getWrapped());
                }

            };

            try {
                method.invoke(service, defaultArgs(method.getParameterTypes()));
            }
            catch (InvocationTargetException e) {
                failures.add(signature + ": threw unexpectedly: " + e.getCause());
                continue;
            }
            catch (IllegalAccessException e) {
                fail("Could not invoke " + signature + ": " + e);
            }

            var expectAsync = CompletableFuture.class.isAssignableFrom(method.getReturnType());
            var hookCalled = expectAsync ? interceptAsyncCalled[0] : interceptCalled[0];
            var actualSignatures = invokedOnService.stream().map(InterceptingAIServiceWrapperTest::toSignature).toList();

            if (!hookCalled) {
                failures.add(signature + ": does not funnel through " + (expectAsync ? "interceptAsync()" : "intercept()"));
            }
            else if (!actualSignatures.contains(signature)) {
                failures.add(signature + ": delegates to " + actualSignatures + " instead of itself");
            }
        }

        if (!failures.isEmpty()) {
            fail(
                "Interception issues in InterceptingAIServiceWrapper:" + lineSeparator()
                    + failures.stream()
                        .sorted()
                        .map(failure -> "  - AIService#" + failure)
                        .collect(joining(lineSeparator()))
            );
        }
    }

    private static Set<Method> getInterceptableMethods() {
        return stream(AIServiceWrapper.class.getDeclaredMethods())
            .filter(not(InterceptingAIServiceWrapperTest::isPrivateMethod))
            .filter(not(Method::isSynthetic))
            .filter(not(Method::isBridge))
            .filter(method -> !"getWrapped".equals(method.getName()))
            .filter(method -> !METADATA_METHODS.contains(method.getName()))
            .collect(toSet());
    }

    private static Set<String> getInterceptedMethodSignatures() {
        return stream(InterceptingAIServiceWrapper.class.getDeclaredMethods())
            .filter(not(InterceptingAIServiceWrapperTest::isPrivateMethod))
            .filter(not(InterceptingAIServiceWrapperTest::isAbstractMethod))
            .filter(not(Method::isSynthetic))
            .filter(not(Method::isBridge))
            .map(InterceptingAIServiceWrapperTest::toSignature)
            .collect(toSet());
    }

    private static boolean isPrivateMethod(Method method) {
        return Modifier.isPrivate(method.getModifiers());
    }

    private static boolean isAbstractMethod(Method method) {
        return Modifier.isAbstract(method.getModifiers());
    }

    private static String toSignature(Method method) {
        return method.getName() + '(' + stream(method.getParameterTypes()).map(Class::getSimpleName).collect(joining(", ")) + ')';
    }

    private static Object[] defaultArgs(Class<?>[] types) {
        return stream(types).map(InterceptingAIServiceWrapperTest::defaultReturnValue).toArray();
    }

    private static Object defaultReturnValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0.0;
        }
        if (returnType == CompletableFuture.class) {
            return CompletableFuture.completedFuture(null); // The streaming overloads compose onto the returned future.
        }
        return null;
    }

}
