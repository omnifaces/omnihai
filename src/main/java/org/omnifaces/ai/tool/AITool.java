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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method as a tool the AI may choose to call.
 * <p>
 * The tool name is derived from the method signature, so {@code findOrder} of {@code OrderTools} becomes {@code OrderTools#findOrder}, and overloads of it
 * become {@code OrderTools#findOrder1} and {@code OrderTools#findOrder2}. The description is what the AI reads when deciding whether the tool applies, so state
 * what it returns and when it is appropriate.
 * <p>
 * Usage example:
 *
 * <pre>
 *
 * &#64;AITool("Looks up a single order by id")
 * public String findOrder(&#64;AIToolParam("The order id") long orderId) {
 *     return orders.findById(orderId).map(Order::toSummary).orElse("No order found with that id.");
 * }
 * </pre>
 * <p>
 * Java does not inherit method annotations, so an override needs its own {@code @AITool} to stay a tool.
 * <p>
 * The method may declare any return type; its {@link Object#toString()} is what the AI receives. A method that throws is reported back to the AI as a failed
 * tool call, so it can correct the arguments and try again.
 *
 * @author Bauke Scholtz
 * @since 1.6
 * @see AIToolParam
 * @see AIToolGroup
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface AITool {

    /**
     * The description of what this tool does, as read by the AI.
     *
     * @return The description of what this tool does.
     */
    String value();

}
