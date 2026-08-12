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

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Meta-annotation declaring that an annotation tags {@link AITool} methods as belonging to a group, so that a subset of tools can be selected independently of
 * the class they live in.
 * <p>
 * Usage example:
 *
 * <pre>
 *
 * &#64;AIToolGroup
 * &#64;Retention(RUNTIME)
 * &#64;Target(METHOD)
 * public &#64;interface ReadOnly {
 * }
 *
 * &#64;ReadOnly
 * &#64;AITool("Looks up a single order by id")
 * public String findOrder(&#64;AIToolParam("The order id") long orderId) { ... }
 *
 * &#64;AITool("Issues a refund for an order")
 * public String refund(&#64;AIToolParam("The order id") long orderId) { ... }
 * </pre>
 * <p>
 * Selecting the group narrows the tools the AI is offered. The narrowing is part of the generated response schema rather than a check applied afterwards, so a
 * service restricted to {@code ReadOnly} cannot name the {@code refund} tool at all.
 *
 * @author Bauke Scholtz
 * @since 1.6
 * @see AITool
 */
@Retention(RUNTIME)
@Target(ANNOTATION_TYPE)
public @interface AIToolGroup {
    //
}
