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

import java.util.Map;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.el.PropertyNotWritableException;

/**
 * Resolves a top level bean name straight out of a map, standing in for the beans a container would offer an expression. Nested property access such as
 * {@code config.apiKey} is left to the built-in {@code MapELResolver} when the bean is a map, or to {@code BeanELResolver} when it is an object.
 */
class StubBeanELResolver extends ELResolver {

    private final Map<String, ?> beans;

    StubBeanELResolver(Map<String, ?> beans) {
        this.beans = beans;
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        if (base == null && property != null && beans.containsKey(property.toString())) {
            context.setPropertyResolved(true);
            return beans.get(property.toString());
        }

        return null;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        return null;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        throw new PropertyNotWritableException();
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        return true;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return null;
    }

}
