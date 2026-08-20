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

import static org.omnifaces.ai.mime.AudioVideoMimeTypeDetector.startsWith;

/**
 * The UTF-8 byte order mark, which a text document may lead with and which the detection of its type must therefore look past.
 * <p>
 * A byte order mark is neither malformed UTF-8 nor a control character, so it survives every check that tells text from binary and then defeats the anchored
 * prefix which each text type is recognized by, such as the {@code <} of XML.
 *
 * @author Bauke Scholtz
 * @since 1.7
 */
final class ByteOrderMark {

    private static final byte[] UTF_8_BYTES = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
    private static final char CHARACTER = '\uFEFF';

    private ByteOrderMark() {
        throw new AssertionError();
    }

    /**
     * Returns the number of leading bytes which the byte order mark of the given content occupies.
     *
     * @param content The content to inspect.
     * @return The length of the byte order mark, or {@code 0} if the content has none.
     */
    static int length(byte[] content) {
        return startsWith(content, 0, UTF_8_BYTES) ? UTF_8_BYTES.length : 0;
    }

    /**
     * Returns the given text without its leading byte order mark.
     *
     * @param text The text to strip.
     * @return The given text, guaranteed to not start with a byte order mark.
     */
    static String strip(String text) {
        return !text.isEmpty() && text.charAt(0) == CHARACTER ? text.substring(1) : text;
    }

}
