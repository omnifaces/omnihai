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
package org.omnifaces.ai.helper;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class DataUriHelperTest {

    private static final byte[] CONTENT = "the video".getBytes(UTF_8);
    private static final String BASE64 = Base64.getEncoder().encodeToString(CONTENT);

    @Test
    void toDataUri_prefixesTheMediaTypeAndBase64Marker() {
        assertEquals("data:video/mp4;base64," + BASE64, DataUriHelper.toDataUri("video/mp4", BASE64));
    }

    @Test
    void isDataUri_recognizesOnlyTheDataScheme() {
        assertTrue(DataUriHelper.isDataUri("data:video/mp4;base64," + BASE64));
        assertFalse(DataUriHelper.isDataUri("videos/job-1/content"));
        assertFalse(DataUriHelper.isDataUri(null));
    }

    @Test
    void decodeDataUri_answersTheOriginalContent() {
        assertArrayEquals(CONTENT, DataUriHelper.decodeDataUri(DataUriHelper.toDataUri("video/mp4", BASE64)));
    }

    @Test
    void decodeDataUri_ofDataUriWhichIsNotBase64_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> DataUriHelper.decodeDataUri("data:video/mp4,the video"));
    }

    @Test
    void decodeDataUri_ofBrokenBase64_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> DataUriHelper.decodeDataUri("data:video/mp4;base64,not base64!"));
    }

    @Test
    void decodeDataUri_ofMarkerInsideTheData_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> DataUriHelper.decodeDataUri("data:text/plain,hi;base64,QUJD"));
    }

    @Test
    void decodeDataUri_ofNull_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> DataUriHelper.decodeDataUri(null));
    }

    @Test
    void decodeDataUri_ofAnotherScheme_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> DataUriHelper.decodeDataUri("https://example.org/;base64,AAAA"));
    }

    @Test
    void redactDataUri_cutsOffTheDataButKeepsTheMediaType() {
        assertEquals("data:video/mp4;base64,...", DataUriHelper.redactDataUri("data:video/mp4;base64," + BASE64));
    }

    @Test
    void redactDataUri_ofDataUriWithoutComma_keepsOnlyTheScheme() {
        assertEquals("data:...", DataUriHelper.redactDataUri("data:" + BASE64));
    }

    @Test
    void redactDataUri_ofAnythingElse_answersItUnchanged() {
        assertEquals("videos/job-1/content", DataUriHelper.redactDataUri("videos/job-1/content"));
        assertNull(DataUriHelper.redactDataUri(null));
    }

}
