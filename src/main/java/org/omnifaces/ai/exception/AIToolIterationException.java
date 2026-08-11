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
package org.omnifaces.ai.exception;

/**
 * Thrown when the AI keeps calling tools without arriving at an answer within the configured number of tool calls.
 * <p>
 * The cap bounds both latency and spend, so reaching it means the conversation is not converging, not that the tools failed.
 *
 * @author Bauke Scholtz
 * @since 1.6
 */
public class AIToolIterationException extends AIException {

    private static final long serialVersionUID = 1L;

    /** The maximum number of tool calls that was exhausted. */
    private final int maxToolCalls;
    /** The tool the AI wanted to call next. */
    private final String requestedTool;

    /**
     * Constructs a new tool iteration exception for the given cap and the tool the AI wanted to call next.
     *
     * @param maxToolCalls The maximum number of tool calls that was exhausted.
     * @param requestedTool The tool the AI wanted to call next.
     */
    public AIToolIterationException(int maxToolCalls, String requestedTool) {
        super("The AI did not arrive at an answer within " + maxToolCalls + " tool calls; it wanted to call " + requestedTool + " next.");
        this.maxToolCalls = maxToolCalls;
        this.requestedTool = requestedTool;
    }

    /**
     * Returns the maximum number of tool calls that was exhausted.
     *
     * @return The maximum number of tool calls that was exhausted.
     */
    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    /**
     * Returns the tool the AI wanted to call next, which is where the conversation was heading when it was cut off.
     *
     * @return The tool the AI wanted to call next.
     */
    public String getRequestedTool() {
        return requestedTool;
    }

}
