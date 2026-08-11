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

import java.util.List;
import java.util.Map;

/**
 * A single thing the AI can call: a name, a description, the parameters it takes, and what it does.
 * <p>
 * There are two ways to obtain one, mirroring how an {@link org.omnifaces.ai.AIService} is obtained either from the {@code @AI} qualifier or from
 * {@link org.omnifaces.ai.AIConfig}: annotate a method with {@link AITool} and hand over the object, or declare it programmatically via
 * {@link ToolRegistry#newBuilder()}. Both end up in the same {@link ToolRegistry}, and can be mixed freely. Neither hands one out, so this stays internal.
 *
 * @author Bauke Scholtz
 * @since 1.6
 * @see AITool
 * @see ToolRegistry
 */
interface Tool {

    /**
     * Returns the tool name as read by the AI.
     *
     * @return The tool name as read by the AI.
     */
    String getName();

    /**
     * Returns the tool description as read by the AI.
     *
     * @return The tool description as read by the AI.
     */
    String getDescription();

    /**
     * Returns the parameters of this tool.
     *
     * @return The parameters of this tool, never {@code null}.
     */
    List<ToolParam> getParams();

    /**
     * Invokes this tool with the given raw arguments, converting each to its declared parameter type.
     *
     * @param arguments The raw arguments, keyed by parameter name.
     * @return The value this tool returned, or an empty string if it returned {@code null} or nothing at all.
     * @throws IllegalArgumentException If an argument is missing or cannot be converted to the declared parameter type.
     * @throws ToolInvocationException If this tool itself throws.
     */
    Object invoke(Map<String, String> arguments);

}
