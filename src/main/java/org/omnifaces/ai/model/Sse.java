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
package org.omnifaces.ai.model;

import java.io.Serializable;

/**
 * Server-Sent Events (SSE) model.
 * <p>
 * Contains the {@link Event} record representing individual SSE fields.
 */
public final class Sse {

    /**
     * Represents a single SSE field (id, event, or data).
     * @param type The SSE event type.
     * @param value The SSE event value.
     */
    public final record Event(Type type, String value) implements Serializable {

        /**
         * Represents an SSE event type.
         */
        public enum Type {
            /** Event of type "id". */
            ID,
            /** Event of type "event". */
            EVENT,
            /** Event of type "data". */
            DATA;
        }
    }
}
