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
package org.omnifaces.ai.mime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MagicNumberTest {

    private static final byte[] RIFF = { 'R', 'I', 'F', 'F' };

    // =================================================================================================================
    // Test matches
    // =================================================================================================================

    @Test
    void matches_magicOnly_shouldReturnTrue() {
        var content = new byte[] { 'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00 };
        assertTrue(new MagicNumber(0, RIFF).matches(content));
    }

    @Test
    void matches_magicOnlyAtOffset_shouldReturnTrue() {
        var content = new byte[] { 0x00, 0x00, 0x00, 0x00, 'f', 't', 'y', 'p' };
        assertTrue(new MagicNumber(4, new byte[] { 'f', 't', 'y', 'p' }).matches(content));
    }

    @Test
    void matches_otherMagic_shouldReturnFalse() {
        var content = new byte[] { 'F', 'O', 'R', 'M', 0x00, 0x00, 0x00, 0x00 };
        assertFalse(new MagicNumber(0, RIFF).matches(content));
    }

    @Test
    void matches_subMagic_shouldReturnTrue() {
        var content = new byte[] { 'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'A', 'V', 'E' };
        assertTrue(new MagicNumber(0, RIFF, 8, new byte[] { 'W', 'A', 'V', 'E' }).matches(content));
    }

    @Test
    void matches_otherSubMagic_shouldReturnFalse() {
        var content = new byte[] { 'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'A', 'V', 'E' };
        assertFalse(new MagicNumber(0, RIFF, 8, new byte[] { 'W', 'E', 'B', 'P' }).matches(content));
    }

    @Test
    void matches_subMagicBeyondContent_shouldReturnFalse() {
        var content = new byte[] { 'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00 };
        assertFalse(new MagicNumber(0, RIFF, 8, new byte[] { 'W', 'A', 'V', 'E' }).matches(content));
    }

    // =================================================================================================================
    // Test hasMagic
    // =================================================================================================================

    @Test
    void hasMagic_equalBytes_shouldReturnTrue() {
        assertTrue(new MagicNumber(0, RIFF).hasMagic(new byte[] { 'R', 'I', 'F', 'F' }));
    }

    @Test
    void hasMagic_otherBytes_shouldReturnFalse() {
        assertFalse(new MagicNumber(0, RIFF).hasMagic(new byte[] { 'F', 'O', 'R', 'M' }));
    }

    // =================================================================================================================
    // Test startsWith
    // =================================================================================================================

    @Test
    void startsWith_matchAtOffset0_shouldReturnTrue() {
        var content = new byte[] { 'A', 'B', 'C', 'D' };
        assertTrue(MagicNumber.startsWith(content, 0, new byte[] { 'A', 'B' }));
    }

    @Test
    void startsWith_matchAtOffset2_shouldReturnTrue() {
        var content = new byte[] { 'A', 'B', 'C', 'D' };
        assertTrue(MagicNumber.startsWith(content, 2, new byte[] { 'C', 'D' }));
    }

    @Test
    void startsWith_noMatch_shouldReturnFalse() {
        var content = new byte[] { 'A', 'B', 'C', 'D' };
        assertFalse(MagicNumber.startsWith(content, 0, new byte[] { 'X', 'Y' }));
    }

    @Test
    void startsWith_contentTooShort_shouldReturnFalse() {
        var content = new byte[] { 'A', 'B' };
        assertFalse(MagicNumber.startsWith(content, 0, new byte[] { 'A', 'B', 'C', 'D' }));
    }

    @Test
    void startsWith_offsetTooLarge_shouldReturnFalse() {
        var content = new byte[] { 'A', 'B', 'C', 'D' };
        assertFalse(MagicNumber.startsWith(content, 10, new byte[] { 'A' }));
    }

}
