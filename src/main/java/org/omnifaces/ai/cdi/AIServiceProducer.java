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

import static java.util.Collections.emptyMap;
import static org.omnifaces.ai.cdi.BaseExpressionResolver.looksLikeExpression;
import static org.omnifaces.ai.cdi.BaseExpressionResolver.looksLikeMicroProfileConfigExpression;
import static org.omnifaces.ai.cdi.ELExpressionResolver.resolveELExpression;
import static org.omnifaces.ai.cdi.MicroProfileConfigExpressionResolver.resolveMicroProfileConfigExpression;
import static org.omnifaces.ai.helper.TextHelper.stripToNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;

import org.omnifaces.ai.AIAudioHandler;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIImageHandler;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;
import org.omnifaces.ai.AIStrategy;
import org.omnifaces.ai.AITextHandler;

/**
 * CDI producer for {@link AIService} instances based on the {@link AI} qualifier annotation.
 * <p>
 * This producer resolves expressions in the annotation attributes and caches service instances by their resolved {@link AIConfig}.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AI
 * @see AIProvider
 * @see AIConfig
 * @see AIService
 */
@ApplicationScoped
class AIServiceProducer {

    private final Map<AIConfig, AIService> serviceCache = new ConcurrentHashMap<>();

    /**
     * Produces an {@link AIService} instance based on the {@link AI} qualifier annotation.
     *
     * @param injectionPoint The injection point.
     * @param beanManager The CDI bean manager.
     * @return The configured AI service instance.
     * @throws IllegalArgumentException If {@link AIProvider#CUSTOM} is used via {@code @AI} annotation (use {@code serviceClass} instead).
     * @throws UnsupportedOperationException If a required runtime dependency is not available (jakarta.json-api, jakarta.enterprise.cdi-el-api, or jakarta.el-api).
     */
    @Produces
    @Dependent
    @AI
    public AIService produce(InjectionPoint injectionPoint, BeanManager beanManager) {
        var annotation = injectionPoint.getAnnotated().getAnnotation(AI.class);

        if (annotation.provider() == AIProvider.CUSTOM) {
            throw new IllegalArgumentException("AIProvider.CUSTOM is not supported via @AI annotation. Use serviceClass instead of provider.");
        }

        if (!isJsonAvailable()) {
            throw new UnsupportedOperationException("You need a runtime implementation of jakarta.json-api in order for default AI services to work."
                    + " E.g. org.eclipse.parsson:parsson:1.1.7 or simply a Jakarta EE-compatible runtime such as WildFly");
        }

        var provider = annotation.serviceClass() == AIService.class ? annotation.provider().name() : annotation.serviceClass().getName();
        var apiKey = resolveExpressionsIfNecessary(beanManager, annotation.apiKey());
        var model = resolveExpressionsIfNecessary(beanManager, annotation.model());
        var endpoint = resolveExpressionsIfNecessary(beanManager, annotation.endpoint());
        var prompt = resolveExpressionsIfNecessary(beanManager, annotation.prompt());
        var textHandler = annotation.textHandler() == AITextHandler.class ? null : annotation.textHandler();
        var imageHandler = annotation.imageHandler() == AIImageHandler.class ? null : annotation.imageHandler();
        var audioHandler = annotation.audioHandler() == AIAudioHandler.class ? null : annotation.audioHandler();
        var config = new AIConfig(provider, apiKey, model, endpoint, prompt, AIStrategy.of(textHandler, imageHandler, audioHandler), emptyMap());

        return serviceCache.computeIfAbsent(config, AIConfig::createService);
    }

    /**
     * Resolves expressions in the given value if necessary.
     * @param beanManager The CDI bean manager for EL resolution.
     * @param value The value, possibly containing an expression.
     * @return The resolved value, or {@code null} if the input is blank.
     * @throws IllegalArgumentException If the expression is malformed (e.g. missing closing brace).
     * @throws UnsupportedOperationException If a required runtime dependency for expression resolution is not available.
     */
    private static String resolveExpressionsIfNecessary(BeanManager beanManager, String value) {
        var stripped = stripToNull(value);

        if (!looksLikeExpression(stripped)) {
            return stripped;
        }

        if (!stripped.contains("}")) {
            throw new IllegalArgumentException("The expression '" + stripped + "' in an @AI annotation attribute appears corrupted, it is missing the trailing '}'.");
        }

        var resolved = stripped;

        if (looksLikeMicroProfileConfigExpression(resolved)) {
            if (!isMicroProfileConfigAvailable()) {
                throw new UnsupportedOperationException("You need a runtime implementation of microprofile-config-api in order for MP config resolution in @AI attributes to work."
                    + " E.g. io.smallrye.config:smallrye-config:3.15.1 or simply a MicroProfile-compatible runtime such as Quarkus");
            }

            resolved = resolveMicroProfileConfigExpression(resolved);
        }

        if (looksLikeExpression(resolved)) {
            if (!isELAwareBeanManagerAvailable(beanManager)) {
                throw new UnsupportedOperationException("You need a runtime implementation of jakarta.enterprise.cdi-el-api in order for EL resolution in @AI attributes to work."
                    + " E.g. org.jboss.weld.servlet:weld-servlet-shaded:6.0.0.Final or org.jboss.weld.se:weld-se-core:6.0.0.Final or simply a Jakarta EE-compatible runtime such as WildFly");
            }

            if (!isELProcessorAvailable()) {
                throw new UnsupportedOperationException("You need a runtime implementation of jakarta.el-api in order for EL resolution in @AI attributes to work."
                    + " E.g. org.glassfish.expressly:expressly:6.0.0 or simply a Jakarta EE-compatible runtime such as WildFly");
            }

            resolved = resolveELExpression(beanManager, resolved);
        }

        return resolved;
    }

    private static boolean isJsonAvailable() {
        try {
            return Class.forName("jakarta.json.spi.JsonProvider").getMethod("provider").invoke(null) != null;
        }
        catch (Exception ignore) {
            return false;
        }
    }

    private static boolean isMicroProfileConfigAvailable() {
        try {
            return Class.forName("org.eclipse.microprofile.config.ConfigProvider").getMethod("getConfig").invoke(null) != null;
        }
        catch (Exception ignore) {
            return false;
        }
    }

    private static boolean isELAwareBeanManagerAvailable(BeanManager beanManager) {
        try {
            return Class.forName("jakarta.enterprise.inject.spi.el.ELAwareBeanManager").isInstance(beanManager);
        }
        catch (Exception ignore) {
            return false;
        }
    }

    private static boolean isELProcessorAvailable() {
        try {
            return Class.forName("jakarta.el.ELProcessor").getDeclaredConstructor().newInstance() != null;
        }
        catch (Exception ignore) {
            return false;
        }
    }
}
