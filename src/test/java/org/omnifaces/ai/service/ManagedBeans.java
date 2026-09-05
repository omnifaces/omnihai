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

/**
 * The beans of this package which a CDI container test outside it must add to the container, as they are package-private.
 * <p>
 * {@link ExecutorServiceHelper} asks the container for a managed executor the first time anything runs asynchronously, so every container test must offer the
 * bean it asks for, whichever test happens to load that class first.
 */
public final class ManagedBeans {

    private ManagedBeans() {
        throw new AssertionError();
    }

    public static Class<?> executorServiceManager() {
        return ExecutorServiceManager.class;
    }

}
