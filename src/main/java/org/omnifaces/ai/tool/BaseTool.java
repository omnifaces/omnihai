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

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;

/**
 * What every {@link Tool} has in common: the name, description and parameters it is read by, and the conversion of the raw arguments which precedes the call.
 * <p>
 * A subclass states only what it is that gets called, and answers for whatever that throws by wrapping it in a {@link ToolInvocationException}.
 *
 * @author Bauke Scholtz
 * @since 1.8
 * @see ToolRegistry
 */
abstract class BaseTool implements Tool {

    /** The tool name as read by the AI. */
    private final String name;
    /** The tool description as read by the AI. */
    private final String description;
    /** The parameters the AI must supply. */
    private final List<ToolParam> params;

    /**
     * Constructs a new tool with the given name, description and parameters.
     *
     * @param name The tool name as read by the AI.
     * @param description The tool description as read by the AI.
     * @param params The parameters the AI must supply.
     * @throws NullPointerException If any argument is null.
     */
    BaseTool(String name, String description, List<ToolParam> params) {
        this.name = requireNonNull(name, "name");
        this.description = requireNonNull(description, "description");
        this.params = List.copyOf(requireNonNull(params, "params"));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public List<ToolParam> getParams() {
        return params;
    }

    @Override
    public Object invoke(Map<String, String> arguments) {
        var result = call(params.stream().map(param -> param.convert(arguments)).toArray());
        return result == null ? "" : result;
    }

    /**
     * Calls what this tool stands for with the given converted arguments, in declaration order.
     *
     * @param values The converted arguments, in declaration order.
     * @return The value the call returned, or {@code null} if it returned nothing at all.
     * @throws ToolInvocationException If the call itself throws.
     */
    abstract Object call(Object[] values);

    @Override
    public String toString() {
        return ToolRegistry.toManifestLine(this);
    }

}
