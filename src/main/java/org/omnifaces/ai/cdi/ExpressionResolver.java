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

import static java.util.regex.Matcher.quoteReplacement;

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
class ExpressionResolver {

    private static final Pattern EL_PATTERN = Pattern.compile("([$#]\\{)([^}]+)(})");

    /**
     * Resolves EL expressions in the given value using the provided BeanManager.
     *
     * @param beanManager The BeanManager to use for EL resolution.
     * @param value The value containing EL expressions to resolve.
     * @return The value with EL expressions resolved.
     */
    static String resolveEL(BeanManager beanManager, String value) {
        var matcher = EL_PATTERN.matcher(value);
        var stringBuilder = new StringBuilder();
        var elProcessor = new ELProcessor();
        elProcessor.getELManager().addELResolver(((ELAwareBeanManager) beanManager).getELResolver());

        while (matcher.find()) {
            var expression = matcher.group(2).strip();

            try {
                var result = elProcessor.eval(expression);
                var replacement = (result != null) ? result.toString() : "";
                matcher.appendReplacement(stringBuilder, quoteReplacement(replacement));
            }
            catch (Exception e) {
                matcher.appendReplacement(stringBuilder, quoteReplacement(matcher.group()));
            }
        }

        matcher.appendTail(stringBuilder);
        return stringBuilder.toString();
    }
}
