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

import java.util.Arrays;

/**
 * The magic bytes by which a MIME type is recognized: a prefix at a fixed offset, optionally narrowed by a submagic further into the content for the types
 * which share a container magic such as RIFF or FTYP.
 *
 * @author Bauke Scholtz
 * @since 1.7.1
 */
final class MagicNumber {

    /** The offset within the content at which the magic is expected. */
    final int offset;

    /** The magic bytes. */
    final byte[] magic;

    /** The offset within the content at which the submagic is expected. */
    final int subOffset;

    /** The submagic bytes, or {@code null} when the magic alone identifies the type. */
    final byte[] subMagic;

    /**
     * Creates a magic number which is identified by its magic bytes alone.
     *
     * @param offset The offset within the content at which the magic is expected.
     * @param magic The magic bytes.
     */
    MagicNumber(int offset, byte[] magic) {
        this(offset, magic, 0, null);
    }

    /**
     * Creates a magic number which is narrowed by a submagic further into the content.
     *
     * @param offset The offset within the content at which the magic is expected.
     * @param magic The magic bytes.
     * @param subOffset The offset within the content at which the submagic is expected.
     * @param subMagic The submagic bytes, or {@code null} when the magic alone identifies the type.
     */
    MagicNumber(int offset, byte[] magic, int subOffset, byte[] subMagic) {
        this.offset = offset;
        this.magic = magic;
        this.subOffset = subOffset;
        this.subMagic = subMagic;
    }

    /**
     * Returns whether the content carries this magic, and its submagic when there is one.
     *
     * @param content The content bytes to check.
     * @return {@code true} if the content carries this magic, {@code false} otherwise.
     */
    boolean matches(byte[] content) {
        if (!startsWith(content, offset, magic)) {
            return false;
        }

        if (subMagic != null) {
            return startsWith(content, subOffset, subMagic);
        }

        return true;
    }

    /**
     * Returns whether this magic number is based on the given magic bytes, which several MIME types may share.
     *
     * @param magic The magic bytes to match against.
     * @return {@code true} if this magic number is based on the given magic bytes, {@code false} otherwise.
     */
    boolean hasMagic(byte[] magic) {
        return Arrays.equals(this.magic, magic);
    }

    /**
     * Checks if the byte array starts with the given prefix at the specified offset.
     *
     * @param content The byte array to check.
     * @param offset The offset within the content to start checking.
     * @param prefix The prefix bytes to match.
     * @return {@code true} if content contains prefix at the given offset, {@code false} otherwise.
     */
    static boolean startsWith(byte[] content, int offset, byte[] prefix) {
        if (content.length < offset + prefix.length) {
            return false;
        }

        for (int i = 0; i < prefix.length; i++) {
            if (content[offset + i] != prefix[i]) {
                return false;
            }
        }

        return true;
    }

}
