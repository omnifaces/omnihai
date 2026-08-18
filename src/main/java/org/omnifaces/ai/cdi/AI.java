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
package org.omnifaces.ai.cdi;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

import org.omnifaces.ai.AIAudioHandler;
import org.omnifaces.ai.AIImageHandler;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.AITextHandler;
import org.omnifaces.ai.AIVideoHandler;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.service.RetryingAIService;
import org.omnifaces.ai.service.ToolCallingAIService;
import org.omnifaces.ai.tool.AITool;
import org.omnifaces.ai.tool.AIToolGroup;

/**
 * CDI qualifier annotation for injecting configured {@link AIService} instances.
 * <p>
 * Usage example with built-in provider:
 *
 * <pre>
 *
 * &#64;Inject
 * &#64;AI(provider = ANTHROPIC, apiKey = "#{config.anthropicApiKey}")
 * private AIService ai;
 * </pre>
 * <p>
 * Usage example with custom service class:
 *
 * <pre>
 *
 * &#64;Inject
 * &#64;AI(serviceClass = MyCustomAIService.class)
 * private AIService ai;
 * </pre>
 *
 * Usage example with custom OpenAI text handler for request tracking:
 *
 * <pre>
 *
 * public class TrackingTextHandler extends OpenAITextHandler {
 *
 *     &#64;Override
 *     public JsonObject buildChatPayload(AIService service, ChatInput input, ChatOptions options, boolean streaming) {
 *         return Json.createObjectBuilder(super.buildChatPayload(service, input, options, streaming))
 *             .add("safety_identifier", getCurrentUserHash())
 *             .build();
 *     }
 *
 * }
 *
 * &#64;Inject
 * &#64;AI(provider = OPENAI, apiKey = "#{config.openaiApiKey}", textHandler = TrackingTextHandler.class)
 * private AIService ai;
 * </pre>
 *
 * Usage example with retry on transient failures:
 *
 * <pre>
 *
 * &#64;Inject
 * &#64;AI(provider = OPENAI, apiKey = "#{config.openaiApiKey}", maxAttempts = 5)
 * private AIService ai;
 * </pre>
 * <p>
 * All string attributes support EL and/or MicroProfile Config expressions, e.g. <code>#{bean.property}</code>,
 * <code>#{initParam['com.example.CONTEXT_PARAM_NAME']}</code>, <code>${config:openai.api-key}</code> etc.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIService
 * @see AIProvider
 * @see AIServiceProducer
 */
@Qualifier
@Retention(RUNTIME)
@Target({ TYPE, METHOD, FIELD, PARAMETER, ANNOTATION_TYPE })
public @interface AI {

    /**
     * The AI provider to use. Defaults to {@link AIProvider#OPENAI}. Do not use {@link AIProvider#CUSTOM}, use {@link #serviceClass()} instead.
     *
     * @return The AI provider.
     */
    @Nonbinding
    AIProvider provider() default AIProvider.OPENAI;

    /**
     * Custom {@link AIService} implementation class. Use this instead of {@link #provider()} when you have a custom implementation. If specified (not
     * {@link AIService AIService.class}), the {@link #provider()} is ignored.
     *
     * @return The custom service class, or {@link AIService AIService.class} if not specified.
     */
    @Nonbinding
    Class<? extends AIService> serviceClass() default AIService.class;

    /**
     * The API key. Supports EL and/or MicroProfile Config expressions. Required for most providers.
     *
     * @return The API key or EL expression.
     */
    @Nonbinding
    String apiKey() default "";

    /**
     * The model to use. Supports EL and/or MicroProfile Config expressions. If empty, uses the provider's default model as per
     * {@link AIProvider#getDefaultModel()}.
     *
     * @return The model name or EL expression.
     */
    @Nonbinding
    String model() default "";

    /**
     * The API endpoint URL. Supports EL and/or MicroProfile Config expressions. If empty, uses the provider's default endpoint as per
     * {@link AIProvider#getDefaultEndpoint()}.
     *
     * @return The endpoint URL or EL expression.
     */
    @Nonbinding
    String endpoint() default "";

    /**
     * The default system prompt to provide high-level instructions to the model. Supports EL and/or MicroProfile Config expressions.
     * <p>
     * This is used as the {@link ChatOptions#getSystemPrompt()} when calling {@link AIService#chat(String)} or {@link AIService#chatAsync(String)} without
     * explicit options. When you call {@link AIService#chat(String, ChatOptions)} or {@link AIService#chatAsync(String, ChatOptions)} with explicit options,
     * this value is ignored in favor of the options' system prompt.
     *
     * @return The default system prompt or EL expression.
     */
    @Nonbinding
    String prompt() default "";

    /**
     * Custom {@link AITextHandler} implementation class. If not specified, uses the provider's default text handler as per
     * {@link AIProvider#getDefaultTextHandler()}.
     *
     * @return The custom text handler class, or {@link AITextHandler AITextHandler.class} if not specified.
     */
    @Nonbinding
    Class<? extends AITextHandler> textHandler() default AITextHandler.class;

    /**
     * Custom {@link AIImageHandler} implementation class. If not specified, uses the provider's default image handler as per
     * {@link AIProvider#getDefaultImageHandler()}.
     *
     * @return The custom image handler class, or {@link AIImageHandler AIImageHandler.class} if not specified.
     */
    @Nonbinding
    Class<? extends AIImageHandler> imageHandler() default AIImageHandler.class;

    /**
     * Custom {@link AIAudioHandler} implementation class. If not specified, uses the provider's default audio handler as per
     * {@link AIProvider#getDefaultAudioHandler()}.
     *
     * @return The custom audio handler class, or {@link AIAudioHandler AIAudioHandler.class} if not specified.
     * @since 1.1
     */
    @Nonbinding
    Class<? extends AIAudioHandler> audioHandler() default AIAudioHandler.class;

    /**
     * Custom {@link AIVideoHandler} implementation class. If not specified, uses the provider's default video handler as per
     * {@link AIProvider#getDefaultVideoHandler()}.
     *
     * @return The custom video handler class, or {@link AIVideoHandler AIVideoHandler.class} if not specified.
     * @since 1.7
     */
    @Nonbinding
    Class<? extends AIVideoHandler> videoHandler() default AIVideoHandler.class;

    /**
     * The maximum number of attempts, counting the initial attempt plus retries. Anything above 1 decorates the produced service with a
     * {@link RetryingAIService}, which transparently re-attempts a call that failed with a {@link RetryingAIService#DEFAULT_RETRY_ON retryable} error, using
     * exponential backoff with full jitter. The default of 1 means a single attempt, hence no retrying.
     * <p>
     * Backoff, maximum duration and the retry condition are not expressible as annotation constants. Use {@link RetryingAIService#newBuilder(AIService)} when
     * you need to tune those.
     *
     * @return The maximum number of attempts, at least 1.
     * @since 1.6
     */
    @Nonbinding
    int maxAttempts() default 1;

    /**
     * The classes declaring the {@link AITool} annotated methods the AI may call before it answers. Each is resolved as a CDI bean, which must be normal-scoped
     * so that its own scope and interceptors apply on every call.
     * <p>
     * The produced service composes tool calling around retrying, so that a {@link #maxAttempts() retry} re-attempts a single provider call rather than
     * replaying the whole tool loop and every side effect it already caused.
     *
     * @return The classes declaring the tools the AI may call.
     * @since 1.6
     * @see AIService#withTools(Object...)
     */
    @Nonbinding
    Class<?>[] tools() default {};

    /**
     * The group to narrow {@link #tools()} to, itself annotated with {@link AIToolGroup}. Defaults to {@link Annotation} to signify no narrowing.
     *
     * @return The group to narrow the tools to.
     * @since 1.6
     * @see AIToolGroup
     */
    @Nonbinding
    Class<? extends Annotation> toolGroup() default Annotation.class;

    /**
     * The maximum number of tool calls before the AI must answer. Exceeding it throws {@link org.omnifaces.ai.exception.AIToolIterationException} on the
     * untyped chat methods, and forces the typed answer on the typed ones. Ignored when {@link #tools()} is empty.
     *
     * @return The maximum number of tool calls before the AI must answer, at least 1.
     * @since 1.6
     */
    @Nonbinding
    int maxToolCalls() default ToolCallingAIService.DEFAULT_MAX_TOOL_CALLS;

}
