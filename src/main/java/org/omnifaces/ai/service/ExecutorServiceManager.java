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

import java.util.concurrent.ExecutorService;

import javax.naming.InitialContext;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;

/**
 * Package-private helper bean to get hold of container managed executor service.
 *
 * @author Bauke Scholtz
 * @since 1.1
 */
@ApplicationScoped
class ExecutorServiceManager {

    private ExecutorService executorService;

    /**
     * First look up the JEE default managed executor service in JNDI.
     * If unavailable, then look up the MP default managed executor service in CDI.
     */
    @PostConstruct
    public void init() {
        try {
            InitialContext context = null;

            try {
                context = new InitialContext();
                executorService = (ExecutorService) context.lookup("java:comp/DefaultManagedExecutorService");
            }
            finally {
                if (context != null) {
                    context.close();
                }
            }
        }
        catch (Exception ignoreAndTryMicroProfile) {
            try {
                executorService = (ExecutorService) CDI.current().select(Class.forName("org.eclipse.microprofile.context.ManagedExecutor")).get();
            }
            catch (Exception ignoreAndGiveUp) {
                // Can happen when running on non-JEE-server (e.g. Tomcat) or when it is disabled in server config for some reason.
            }
        }
    }

    /**
     * Returns the managed executor service, or {@code null} if unavailable.
     * @return The managed executor service, or {@code null} if unavailable.
     */
    public ExecutorService getManagedExecutorService() {
        return executorService;
    }

    /**
     * Returns the CDI-managed instance of this bean.
     * @return The CDI-managed instance of this bean.
     */
    public static ExecutorServiceManager getCurrentInstance() {
        return CDI.current().select(ExecutorServiceManager.class).get();
    }
}
