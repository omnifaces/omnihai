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

import java.util.Base64;

/**
 * Utility class for Base64 {@code data:} URIs in the format {@code data:<media-type>;base64,<data>}.
 *
 * @author Bauke Scholtz
 * @since 1.7.4
 */
public final class DataUriHelper {

    private static final String DATA_URI_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";
    private static final String REDACTED_DATA = "...";

    private DataUriHelper() {
        throw new AssertionError();
    }

    /**
     * Builds a Base64 data URI of the given media type and Base64 encoded data.
     *
     * @param mediaType The media type, e.g. {@code video/mp4}.
     * @param base64 The Base64 encoded data.
     * @return The data URI.
     */
    public static String toDataUri(String mediaType, String base64) {
        return DATA_URI_PREFIX + mediaType + BASE64_MARKER + base64;
    }

    /**
     * Returns whether the given text is a data URI.
     *
     * @param text The text to check, may be {@code null}.
     * @return Whether the given text starts with {@code data:}.
     */
    public static boolean isDataUri(String text) {
        return text != null && text.startsWith(DATA_URI_PREFIX);
    }

    /**
     * Decodes the data of the given Base64 data URI.
     *
     * @param dataUri The data URI.
     * @return The decoded data.
     * @throws IllegalArgumentException If the given data URI is not Base64 encoded, or its data is not valid Base64.
     */
    public static byte[] decodeDataUri(String dataUri) {
        var dataIndex = isDataUri(dataUri) ? dataUri.indexOf(',') + 1 : 0;

        if (dataIndex < BASE64_MARKER.length() || !dataUri.startsWith(BASE64_MARKER, dataIndex - BASE64_MARKER.length())) {
            throw new IllegalArgumentException("Not a Base64 data URI: " + redactDataUri(dataUri));
        }

        return Base64.getDecoder().decode(dataUri.substring(dataIndex));
    }

    /**
     * Returns the given text with the data of a data URI cut off, so that it can be printed. Any other text is returned unchanged.
     *
     * @param text The text to redact, may be {@code null}.
     * @return The data URI up to and including its comma followed by {@code ...}, or the given text if it is not a data URI.
     */
    public static String redactDataUri(String text) {
        if (!isDataUri(text)) {
            return text;
        }

        var commaIndex = text.indexOf(',');
        return (commaIndex < 0 ? DATA_URI_PREFIX : text.substring(0, commaIndex + 1)) + REDACTED_DATA;
    }

}
