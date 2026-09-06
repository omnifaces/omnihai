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

import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A runtime may manage its executor through MicroProfile Context Propagation rather than through the naming service, which is what a MicroProfile runtime
 * offers where a Jakarta EE server offers the other. The library takes whichever it finds, and this states the second, with a naming service offering nothing.
 */
@ExtendWith(WeldJunit5Extension.class)
class ExecutorServiceMicroProfileManagedExecutorTest {

    private static final ExecutorService DELEGATE = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "stub.microProfileManagedExecutor");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * A managed executor is an executor service with a few futures bolted on, and the library uses it as the former alone, so the rest need not be there.
     */
    private static final ManagedExecutor STUB = (ManagedExecutor) Proxy.newProxyInstance(
        ManagedExecutor.class.getClassLoader(), new Class[] { ManagedExecutor.class }, (proxy, method, arguments) -> {
            var declaringClass = method.getDeclaringClass();

            if (
                declaringClass == ExecutorService.class || declaringClass == Executor.class || declaringClass == AutoCloseable.class
                    || declaringClass == Object.class
            ) {
                return method.invoke(DELEGATE, arguments);
            }

            throw new UnsupportedOperationException("This executor is managed in name alone: " + method.getName());
        }
    );

    @WeldSetup
    WeldInitiator weld = WeldInitiator.from(ExecutorServiceManager.class, StubManagedExecutorProducer.class).activate(ApplicationScoped.class).build();

    @Test
    void getCurrentInstance_runtimeManagingItsExecutorThroughMicroProfile_answersThatOne() {
        StubInitialContextFactory.withoutManagedExecutorService(
            () -> assertSame(STUB, ExecutorServiceManager.getCurrentInstance().getManagedExecutorService())
        );
    }

    @ApplicationScoped
    public static class StubManagedExecutorProducer {

        @Produces
        ManagedExecutor produce() {
            return STUB;
        }

    }

}
