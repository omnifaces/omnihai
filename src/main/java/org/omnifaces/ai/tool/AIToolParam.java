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

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Describes a parameter of an {@link AITool} method.
 * <p>
 * Parameter names are only present in the class file when it is compiled with {@code -parameters}, which is not something a library can require of its users.
 * Specify {@link #name()} whenever that flag is absent, else registration fails with an explanatory error rather than exposing {@code arg0} to the AI.
 *
 * @author Bauke Scholtz
 * @since 1.6
 * @see AITool
 */
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface AIToolParam {

    /**
     * The description of this parameter, as read by the AI.
     *
     * @return The description of this parameter.
     */
    String value();

    /**
     * The name of this parameter, as read by the AI. If empty, the reflective parameter name is used, which requires compilation with {@code -parameters}.
     *
     * @return The name of this parameter, or an empty string to use the reflective parameter name.
     */
    String name() default "";

}
