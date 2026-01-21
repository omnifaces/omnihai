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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;

import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.AIService;

/**
 * CDI producer for {@link AIService} instances based on the {@link AI} qualifier annotation.
 * <p>
 * This producer resolves EL expressions in the annotation attributes and caches service instances by their resolved configuration.
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
                    + " E.g. org.eclipse.parsson:parsson:1.1.7");
        }

        var provider = annotation.serviceClass() == AIService.class ? annotation.provider().name() : annotation.serviceClass().getName();
        var apiKey = resolveELIfNecessary(beanManager, annotation.apiKey());
        var model = resolveELIfNecessary(beanManager, annotation.model());
        var endpoint = resolveELIfNecessary(beanManager, annotation.endpoint());
        var prompt = resolveELIfNecessary(beanManager, annotation.prompt());
        var config = new AIConfig(provider, apiKey, model, endpoint, prompt, emptyMap());

        return serviceCache.computeIfAbsent(config, AIConfig::createService);
    }

    /**
     * Resolves EL expressions in the given value.
     * @param beanManager The CDI bean manager for EL resolution.
     * @param value The value, possibly containing an EL expression.
     * @return The resolved value, or {@code null} if the input is blank.
     */
    private static String resolveELIfNecessary(BeanManager beanManager, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        var stripped = value.strip();

        if (!stripped.contains("#{") && !stripped.contains("${")) {
            return stripped;
        }

        if (!isELAwareBeanManagerAvailable(beanManager)) {
            throw new UnsupportedOperationException("You need a runtime implementation of jakarta.enterprise.cdi-el-api in order for EL resolution in @AI attributes to work."
                    + " E.g. org.jboss.weld.servlet:weld-servlet-shaded:6.0.0.Final or org.jboss.weld.se:weld-se-core:6.0.0.Final");
        }

        if (!isELProcessorAvailable()) {
            throw new UnsupportedOperationException("You need a runtime implementation of jakarta.el-api in order for EL resolution in @AI attributes to work."
                    + " E.g. org.glassfish.expressly:expressly:6.0.0");
        }

        return ExpressionResolver.resolveEL(beanManager, stripped);
    }

    private static boolean isJsonAvailable() {
        try {
            return Class.forName("jakarta.json.spi.JsonProvider").getMethod("provider").invoke(null) != null;
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
