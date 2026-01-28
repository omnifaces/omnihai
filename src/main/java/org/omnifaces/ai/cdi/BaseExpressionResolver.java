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

import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Base helper class to resolve expressions in the annotation attributes of the {@link AI} qualifier annotation.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIServiceProducer
 * @see ELExpressionResolver
 * @see MicroprofileConfigExpressionResolver
 */
abstract class BaseExpressionResolver {

    /**
     * Resolves expressions in the given value using the given regular expression pattern and evaluator function.
     * <p>
     * The pattern is expected to define the following capturing groups:
     * <ul>
     *   <li>Group 1: the expression prefix (e.g. <code>${</code> or <code>#{</code>)</li>
     *   <li>Group 2: the expression body to be evaluated</li>
     *   <li>Group 3: the expression suffix (e.g. <code>}</code>)</li>
     * </ul>
     * <p>
     * For every matched expression, the evaluator function will be invoked with the extracted expression body and its result will replace the original expression in the returned value.
     * If the evaluator throws an exception, then the original expression will be retained as-is.
     *
     * @param pattern The regular expression pattern to identify expressions to be resolved.
     * @param value The value containing expressions to resolve.
     * @param evaluator The function to evaluate the extracted expression body and return its replacement value.
     * @return The value with all matching expressions resolved using the given evaluator.
     */
    static String resolve(Pattern pattern, String value, Function<String, String> evaluator) {
        var matcher = pattern.matcher(value);
        var stringBuilder = new StringBuilder();

        while (matcher.find()) {
            var expression = matcher.group(2).strip();

            try {
                var replacement = evaluator.apply(expression);
                matcher.appendReplacement(stringBuilder, quoteReplacement(replacement != null ? replacement : ""));
            }
            catch (Exception e) {
                matcher.appendReplacement(stringBuilder, quoteReplacement(matcher.group()));
            }
        }

        matcher.appendTail(stringBuilder);
        return stringBuilder.toString();
    }
}