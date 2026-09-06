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

import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.spi.InitialContextFactory;

/**
 * Stands in for the naming service of a Jakarta EE server, offering the one name the library looks up: the managed executor service. This is what makes the
 * server path testable without a server, and is installed by the {@code java.naming.factory.initial} property of its own surefire execution.
 */
public class StubInitialContextFactory implements InitialContextFactory {

    private static final String MANAGED_EXECUTOR_SERVICE_NAME = "java:comp/DefaultManagedExecutorService";

    /** The name of the thread the stubbed executor runs on, which is how a test tells it apart from the pool the library makes for itself. */
    static final String THREAD_NAME = "stub.managedExecutorService";

    /** The executor the stubbed lookup answers, so that a test can state that this is the one the library went on to use. */
    static final ExecutorService MANAGED_EXECUTOR_SERVICE = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, THREAD_NAME);
        thread.setDaemon(true);
        return thread;
    });

    /** Whether the naming service offers the managed executor at all, which a server without one does not. */
    private static final AtomicBoolean OFFERING = new AtomicBoolean(true);

    /** Runs the given task against a naming service which offers nothing, which is the runtime of a server managing no executor. */
    static void withoutManagedExecutorService(Runnable task) {
        OFFERING.set(false);

        try {
            task.run();
        }
        finally {
            OFFERING.set(true);
        }
    }

    @Override
    public Context getInitialContext(Hashtable<?, ?> environment) {
        return (Context) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { Context.class }, (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, arguments);
            }

            if ("close".equals(method.getName())) {
                return null;
            }

            if ("lookup".equals(method.getName()) && OFFERING.get() && MANAGED_EXECUTOR_SERVICE_NAME.equals(String.valueOf(arguments[0]))) {
                return MANAGED_EXECUTOR_SERVICE;
            }

            throw new NameNotFoundException("This naming service offers " + MANAGED_EXECUTOR_SERVICE_NAME + " alone");
        });
    }

}
