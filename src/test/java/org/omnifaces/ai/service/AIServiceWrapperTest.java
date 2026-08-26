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
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIService;

/**
 * Validates that {@link AIServiceWrapper} adheres to the decorator pattern:
 * <ul>
 * <li>every method is implemented</li>
 * <li>every method delegates via {@link AIServiceWrapper#getWrapped()}</li>
 * <li>every method invokes the exact same overload on the wrapped instance</li>
 * </ul>
 */
class AIServiceWrapperTest {

    /**
     * Validates that AIServiceWrapper implements all non-default methods declared in AIService.
     * <p>
     * This test uses reflection to compare all non-default methods declared in the AIService interface against the methods implemented in AIServiceWrapper.
     * When new non-default methods are added to AIService, this test will fail until AIServiceWrapper is updated to implement them.
     */
    @Test
    void testImplementsAllNonDefaultAIServiceMethods() {
        var wrapperMethods = getNonPrivateMethodSignatures(AIServiceWrapper.class);
        var requiredMethods = getNonDefaultInterfaceMethodSignatures(AIService.class);
        var missingMethods = new HashSet<>(requiredMethods);
        missingMethods.removeAll(wrapperMethods);

        if (!missingMethods.isEmpty()) {
            fail(
                "AIServiceWrapper is missing an implementation for the following AIService methods:" + lineSeparator()
                    + missingMethods.stream()
                        .sorted()
                        .map(methodSignature -> "  - AIService#" + methodSignature)
                        .collect(joining(lineSeparator()))
            );
        }
    }

    /**
     * Validates that every delegate method in AIServiceWrapper calls {@code getWrapped()} method (and thus not the {@code wrapped} field) and delegates to the
     * exact same method on the instance returned by {@code getWrapped()}.
     * <p>
     * This test uses a Proxy-based recording service and an anonymous subclass that overrides {@code getWrapped()} to track calls, so neither the field nor any
     * other method on the wrapped service is invoked.
     */
    @Test
    void testAllDelegateMethodsCallGetWrappedAndInvokeSameName() throws IllegalAccessException {
        var wrapperMethods = getNonPrivateMethods(AIServiceWrapper.class);
        var failures = new ArrayList<String>();

        for (var wrapperMethod : wrapperMethods) {
            var calledOnWrapped = new ArrayList<Method>();
            var getWrappedCalled = new boolean[] { false };
            var recordingService = (AIService) Proxy.newProxyInstance(
                AIService.class.getClassLoader(),
                new Class[] { AIService.class },
                (proxy, method, args) -> {
                    calledOnWrapped.add(method);
                    return defaultReturnValue(method.getReturnType());
                }
            );

            var wrapper = new AIServiceWrapper(recordingService) {

                private static final long serialVersionUID = 1L;

                @Override
                public AIService getWrapped() {
                    getWrappedCalled[0] = true;
                    return super.getWrapped();
                }

            };

            var wrapperMethodSignature = toSignature(wrapperMethod);

            try {
                wrapperMethod.invoke(wrapper, defaultArgs(wrapperMethod.getParameterTypes()));
            }
            catch (InvocationTargetException e) {
                failures.add(wrapperMethodSignature + ": threw unexpectedly: " + e.getCause());
                continue;
            }

            var actualSignatures = calledOnWrapped.stream().map(AIServiceWrapperTest::toSignature).toList();

            if (!getWrappedCalled[0]) {
                failures.add(wrapperMethodSignature + ": does not call getWrapped()");
            }
            else if (!actualSignatures.contains(wrapperMethodSignature)) {
                failures.add(wrapperMethodSignature + ": delegates to " + actualSignatures + " instead of itself");
            }
        }

        if (!failures.isEmpty()) {
            fail(
                "Delegation issues in AIServiceWrapper:" + lineSeparator()
                    + failures.stream()
                        .sorted()
                        .map(failure -> "  - AIService#" + failure)
                        .collect(joining(lineSeparator()))
            );
        }
    }

    private static Set<Method> getNonPrivateMethods(Class<?> clazz) {
        return stream(clazz.getDeclaredMethods())
            .filter(not(AIServiceWrapperTest::isPrivateMethod))
            .filter(m -> !"getWrapped".equals(m.getName()))
            .collect(toSet());
    }

    private static Set<String> getNonPrivateMethodSignatures(Class<?> clazz) {
        return getNonPrivateMethods(clazz).stream()
            .map(AIServiceWrapperTest::toSignature)
            .collect(toSet());
    }

    private static Set<String> getNonDefaultInterfaceMethodSignatures(Class<?> iface) {
        if (!iface.isInterface()) {
            throw new IllegalArgumentException();
        }

        return stream(iface.getMethods())
            .filter(not(Method::isDefault))
            .filter(not(AIServiceWrapperTest::isObjectMethod))
            .map(AIServiceWrapperTest::toSignature)
            .collect(toSet());
    }

    private static boolean isPrivateMethod(Method method) {
        return Modifier.isPrivate(method.getModifiers());
    }

    private static boolean isObjectMethod(Method method) {
        try {
            Object.class.getMethod(method.getName(), method.getParameterTypes());
            return true;
        }
        catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static String toSignature(Method method) {
        var signature = new StringBuilder();
        signature.append(method.getName());
        signature.append('(');
        signature.append(stream(method.getParameterTypes()).map(Class::getSimpleName).collect(joining(", ")));
        signature.append(')');
        return signature.toString();
    }

    private static Object[] defaultArgs(Class<?>[] types) {
        return stream(types).map(AIServiceWrapperTest::defaultReturnValue).toArray();
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
        return null;
    }

}
