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
import java.util.function.Function;

/**
 * A {@link Tool} declared programmatically from a lambda or method reference rather than from an {@link AITool} annotated method.
 * <p>
 * A method reference cannot carry a name, a description or parameter names of its own, as those are erased, so they are stated explicitly. This is the
 * programmatic counterpart of the annotation, in the same way {@link org.omnifaces.ai.AIConfig} is the programmatic counterpart of the {@code @AI} qualifier.
 *
 * @author Bauke Scholtz
 * @since 1.6
 * @see ToolRegistry#newBuilder()
 */
final class ToolFunction extends BaseTool {

    /** What the tool does, taking the converted arguments in declaration order. */
    private final Function<Object[], Object> function;

    ToolFunction(String name, String description, List<ToolParam> params, Function<Object[], Object> function) {
        super(name, description, params);
        this.function = requireNonNull(function, "function");
    }

    @Override
    Object call(Object[] values) {
        try {
            return function.apply(values);
        }
        catch (RuntimeException e) {
            throw new ToolInvocationException(getName(), e);
        }
    }

}
