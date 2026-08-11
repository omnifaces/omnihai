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
package org.omnifaces.ai.tool;

import java.util.Map;

/**
 * A single tool call the AI made, as reported to the observer of a tool calling service.
 * <p>
 * This is the audit point of the tool loop: it records which tool the AI chose, with which arguments, and what came back. Exactly one of {@code result} and
 * {@code failure} is set.
 *
 * @param toolName The name of the tool the AI chose.
 * @param arguments The raw arguments the AI supplied, keyed by parameter name.
 * @param result The value the tool returned, or {@code null} if it failed.
 * @param failure The reason the tool call failed, or {@code null} if it succeeded.
 * @author Bauke Scholtz
 * @since 1.6
 * @see AITool
 */
public record ToolInvocation(String toolName, Map<String, String> arguments, Object result, RuntimeException failure) {

    /**
     * Returns whether this tool call failed, either because the arguments could not be converted or because the tool method itself threw.
     *
     * @return Whether this tool call failed.
     */
    public boolean hasFailed() {
        return failure != null;
    }

}
