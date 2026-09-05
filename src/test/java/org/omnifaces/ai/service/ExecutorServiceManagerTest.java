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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ExecutorServiceManagerTest {

    /**
     * Neither a JNDI managed executor service nor a MicroProfile one is reachable outside a container, and that is a supported deployment rather than an error:
     * the caller falls back to its own pool when the lookup answers nothing.
     */
    @Test
    void init_outsideAContainer_leavesTheManagedExecutorServiceUnset() {
        var manager = new ExecutorServiceManager();
        assertDoesNotThrow(manager::init);
        assertNull(manager.getManagedExecutorService());
    }

    @Test
    void getManagedExecutorService_beforeInit_isNull() {
        assertNull(new ExecutorServiceManager().getManagedExecutorService());
    }

}
