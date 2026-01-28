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

import java.util.regex.Pattern;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Helper class to resolve MP config expressions in the annotation attributes of the {@link AI} qualifier annotation.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIServiceProducer
 */
class MicroprofileConfigExpressionResolver extends BaseExpressionResolver {

    private static final Pattern CONFIG_PATTERN = Pattern.compile("(\\$\\{config:)([^}]+)(})");

    private MicroprofileConfigExpressionResolver() {
        throw new AssertionError();
    }

    /**
     * Resolves MP config expressions in the given value.
     *
     * @param value The value containing MP config expressions to resolve.
     * @return The value with MP config expressions resolved.
     */
    static String resolveConfig(String value) {
        var config = ConfigProvider.getConfig();
        return resolve(CONFIG_PATTERN, value, key -> config.getOptionalValue(key, String.class).orElse(""));
    }
}
