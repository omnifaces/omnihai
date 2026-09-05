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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Text decoded from a file may open with a byte order mark, which is a marker of the encoding rather than content, so it is removed before the text is matched
 * against anything.
 */
class ByteOrderMarkTest {

    @Test
    void strip_textWithAByteOrderMark_removesIt() {
        assertEquals("{\"key\":1}", ByteOrderMark.strip("\uFEFF{\"key\":1}"));
    }

    @Test
    void strip_textWithoutAByteOrderMark_isLeftAsItIs() {
        assertEquals("{\"key\":1}", ByteOrderMark.strip("{\"key\":1}"));
    }

    @Test
    void strip_emptyText_hasNothingToRemove() {
        assertEquals("", ByteOrderMark.strip(""));
    }

}
