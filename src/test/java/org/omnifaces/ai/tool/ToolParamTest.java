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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The AI hands every tool argument over as a string, so each one is converted to the type its parameter declares before the method is called. A value which
 * does not fit that type is rejected by name, as the AI can correct itself from that and not from a conversion stack trace.
 */
class ToolParamTest {

    private static final String NAME = "value";
    private static final String DESCRIPTION = "The value";

    @Test
    void convert_booleanParameter() {
        assertEquals(true, convert(boolean.class, "true"));
        assertEquals(false, convert(boolean.class, "false"));
        assertEquals(true, convert(Boolean.class, "true"));
    }

    /**
     * Anything the AI writes which is not literally {@code true} counts as false, which is what the JSON boolean it stands for means.
     */
    @Test
    void convert_booleanParameter_unrecognizedValue_isFalse() {
        assertEquals(false, convert(boolean.class, "yes"));
    }

    @Test
    void convert_numberParameters() {
        assertEquals(42L, convert(long.class, "42"));
        assertEquals(42, convert(int.class, "42"));
        assertEquals(4.5, convert(double.class, "4.5"));
        assertEquals(42L, convert(Long.class, "42"));
    }

    @Test
    void convert_charParameter_isTheFirstCharacterOfTheArgument() {
        assertEquals('x', convert(char.class, "x"));
        assertEquals('y', convert(Character.class, "y"));
    }

    @Test
    void convert_stringParameter() {
        assertEquals("anything", convert(String.class, "anything"));
    }

    @Test
    void convert_parameterOfAnotherType_isParsedFromItsTextForm() {
        assertEquals(LocalDate.of(2026, 1, 31), convert(LocalDate.class, "2026-01-31"));
    }

    @Test
    void convert_valueWhichDoesNotFitTheType_namesTheParameterAndTheValue() {
        var exception = assertThrows(IllegalArgumentException.class, () -> convert(long.class, "not a number"));

        assertTrue(exception.getMessage().contains(NAME));
        assertTrue(exception.getMessage().contains("not a number"));
        assertTrue(exception.getMessage().contains("long"));
    }

    @Test
    void convert_missingArgument_namesTheParameter() {
        var param = ToolParam.of(long.class, NAME, DESCRIPTION);

        var exception = assertThrows(IllegalArgumentException.class, () -> param.convert(Map.of()));

        assertTrue(exception.getMessage().contains(NAME));
    }

    /**
     * A parameter appears in the manifest the AI reads, so it names itself and what it is for.
     */
    @Test
    void toString_namesTheParameterAndItsDescription() {
        assertEquals(NAME + ": " + DESCRIPTION, ToolParam.of(String.class, NAME, DESCRIPTION).toString());
    }

    private static Object convert(Class<?> type, String argument) {
        return ToolParam.of(type, NAME, DESCRIPTION).convert(Map.of(NAME, argument));
    }

}
