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
/**
 * Tools which the AI may call on your own objects before it answers.
 * <p>
 * This package contains the annotations declaring them and the types collecting them:
 * <ul>
 * <li>{@link org.omnifaces.ai.tool.AITool} - marks a method as a tool the AI may call</li>
 * <li>{@link org.omnifaces.ai.tool.AIToolParam} - describes a parameter of such a method</li>
 * <li>{@link org.omnifaces.ai.tool.AIToolGroup} - meta-annotation declaring a tag which narrows a set of tools</li>
 * <li>{@link org.omnifaces.ai.tool.ToolParam} - a single parameter of a tool, and the type its argument is converted to</li>
 * <li>{@link org.omnifaces.ai.tool.ToolRegistry} - the set of tools a service offers the AI, and the schema constraining its choice</li>
 * <li>{@link org.omnifaces.ai.tool.ToolInvocation} - a single tool call, as reported to an observer</li>
 * <li>{@link org.omnifaces.ai.tool.ToolInvocationException} - thrown when a tool itself throws</li>
 * </ul>
 * Tools ride on the same provider-enforced structured outputs as {@link org.omnifaces.ai.AIService#chat(String, Class)} rather than on provider-native function
 * calling, so they behave identically on every supported provider. There is no classpath scanning: only the objects explicitly handed over are offered to the
 * AI. The loop itself lives in {@link org.omnifaces.ai.service.ToolCallingAIService}.
 *
 * @see org.omnifaces.ai.tool.AITool
 * @see org.omnifaces.ai.AIService#withTools(Object...)
 */
package org.omnifaces.ai.tool;
