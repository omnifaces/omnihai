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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.ChatOptions;

/**
 * CDI qualifier annotation for injecting configured {@link AIService} instances.
 * <p>
 * Usage example with built-in provider:
 * <pre>
 * &#64;Inject
 * &#64;AI(provider = ANTHROPIC, apiKey = "#{config.anthropicApiKey}")
 * private AIService ai;
 * </pre>
 * <p>
 * Usage example with custom service class:
 * <pre>
 * &#64;Inject
 * &#64;AI(serviceClass = MyCustomAIService.class)
 * private AIService ai;
 * </pre>
 * <p>
 * All string attributes support EL expressions, e.g. <code>#{bean.property}</code>, <code>#{initParam['com.example.CONTEXT_PARAM_NAME']}</code>, etc.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIService
 * @see AIProvider
 * @see AIServiceProducer
 */
@Qualifier
@Retention(RUNTIME)
@Target({TYPE, METHOD, FIELD, PARAMETER, ANNOTATION_TYPE})
public @interface AI {

    /**
     * The AI provider to use. Defaults to {@link AIProvider#OPENAI}. Do not use {@link AIProvider#CUSTOM}, use {@link #serviceClass()} instead.
     *
     * @return The AI provider.
     */
    @Nonbinding
    AIProvider provider() default AIProvider.OPENAI;

    /**
     * Custom AIService implementation class.
     * Use this instead of {@link #provider()} when you have a custom implementation.
     * If specified (not {@link AIService AIService.class}), the {@link #provider()} is ignored.
     *
     * @return The custom service class, or {@link AIService AIService.class} if not specified.
     */
    @Nonbinding
    Class<? extends AIService> serviceClass() default AIService.class;

    /**
     * The API key. Supports EL expressions.
     * Required for most providers.
     *
     * @return The API key or EL expression.
     */
    @Nonbinding
    String apiKey() default "";

    /**
     * The model to use. Supports EL expressions.
     * If empty, uses the provider's default model as per {@link AIProvider#getDefaultModel()}.
     *
     * @return The model name or EL expression.
     */
    @Nonbinding
    String model() default "";

    /**
     * The API endpoint URL. Supports EL expressions.
     * If empty, uses the provider's default endpoint as per {@link AIProvider#getDefaultEndpoint()}.
     *
     * @return The endpoint URL or EL expression.
     */
    @Nonbinding
    String endpoint() default "";

    /**
     * The default system prompt to provide high-level instructions to the model. Supports EL expressions.
     * <p>
     * This is used as the {@link ChatOptions#getSystemPrompt()} when calling {@link AIService#chat(String)} or {@link AIService#chatAsync(String)} without explicit options.
     * When you call {@link AIService#chat(String, ChatOptions)} or {@link AIService#chatAsync(String, ChatOptions)} with explicit options,
     * this value is ignored in favor of the options' system prompt.
     *
     * @return The default system prompt or EL expression.
     */
    @Nonbinding
    String prompt() default "";
}
