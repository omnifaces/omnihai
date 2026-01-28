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

import static java.util.Optional.ofNullable;

import java.util.regex.Pattern;

import jakarta.el.ELProcessor;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.el.ELAwareBeanManager;

/**
 * Helper class to resolve EL expressions in the annotation attributes of the {@link AI} qualifier annotation.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIServiceProducer
 */
class ELExpressionResolver extends BaseExpressionResolver {

    private static final Pattern EL_PATTERN = Pattern.compile("([$#]\\{)([^}]+)(})");

    private ELExpressionResolver() {
        throw new AssertionError();
    }

    /**
     * Resolves EL expressions in the given value using the provided BeanManager.
     *
     * @param beanManager The BeanManager to use for EL resolution.
     * @param value The value containing EL expressions to resolve.
     * @return The value with EL expressions resolved.
     */
    static String resolveEL(BeanManager beanManager, String value) {
        var elProcessor = new ELProcessor();
        elProcessor.getELManager().addELResolver(((ELAwareBeanManager) beanManager).getELResolver());
        return resolve(EL_PATTERN, value, expr -> ofNullable(elProcessor.eval(expr)).map(Object::toString).orElse(""));
    }
}
