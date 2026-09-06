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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.model.Sse.Event;
import org.omnifaces.ai.model.Sse.Event.Type;

/**
 * A server-sent event stream names its fields {@code id}, {@code event} and {@code data}, and an event carries the one field it was parsed from.
 */
class SseTest {

    @Test
    void type_namesTheThreeFieldsOfTheProtocol() {
        assertEquals("ID", Type.ID.name());
        assertEquals("EVENT", Type.EVENT.name());
        assertEquals("DATA", Type.DATA.name());
        assertEquals(3, Type.values().length);
    }

    @Test
    void event_carriesItsTypeAndValue() {
        var event = new Event(Type.DATA, "{\"delta\":\"Hi\"}");

        assertEquals(Type.DATA, event.type());
        assertEquals("{\"delta\":\"Hi\"}", event.value());
    }

}
