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

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.util.Optional;

/**
 * Provides audio and video MIME type detection based on magic bytes.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public final class AudioVideoMimeTypeDetector {

    private static final byte[] MKV_MAGIC = {0x1A, 'E', (byte)0xDF, (byte)0xA3};
    /** The RIFF magic (to be shared with {@link ImageMimeTypeDetector}). */
    static final byte[] RIFF_MAGIC = {'R', 'I', 'F', 'F'};
    /** The FTYP magic (to be shared with {@link ImageMimeTypeDetector}). */
    static final byte[] FTYP_MAGIC = {'f', 't', 'y', 'p'};

    private enum AudioVideoMimeType implements MimeType {
        MP3("audio/mpeg", "mp3", 0, new byte[]{(byte)0xFF, (byte)0xE0}, 0, null), // Also handled as special case.
        MP3_ID3("audio/mpeg", "mp3", 0, new byte[]{'I', 'D', '3'}, 0, null),
        FLAC("audio/flac", "flac", 0, new byte[]{'f', 'L', 'a', 'C'}, 0, null),
        OGG("audio/ogg", "ogg", 0, new byte[]{'O', 'g', 'g', 'S'}, 0, null),
        MKV("video/x-matroska", "mkv", 0, MKV_MAGIC, 0, null),
        WEBM("video/webm", "webm", 0, MKV_MAGIC, 0, null), // Handled as special case.
        AVI("video/x-msvideo", "avi", 0, RIFF_MAGIC, 8, new byte[]{'A', 'V', 'I', ' '}),
        WAV("audio/wav", "wav", 0, RIFF_MAGIC, 8, new byte[]{'W', 'A', 'V', 'E'}),
        MOV("video/quicktime", "mov", 4, FTYP_MAGIC, 8, new byte[]{'q', 't', ' ', ' '}),
        M4A("audio/mp4", "m4a", 4, FTYP_MAGIC, 8, new byte[]{'M', '4', 'A', ' '}),
        MP4("video/mp4", "mp4", 4, FTYP_MAGIC, 8, null); // Handled as special case.

        private final String value;
        private final String extension;
        private final int magicOffset;
        private final byte[] magic;
        private final int subMagicOffset;
        private final byte[] subMagic;

        AudioVideoMimeType(String value, String extension, int magicOffset, byte[] magic, int subMagicOffset, byte[] subMagic) {
            this.value = value;
            this.extension = extension;
            this.magicOffset = magicOffset;
            this.magic = magic;
            this.subMagicOffset = subMagicOffset;
            this.subMagic = subMagic;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public String extension() {
            return extension;
        }

        boolean matches(byte[] content) {
            if (!startsWith(content, magicOffset, magic)) {
                return false;
            }

            if (subMagic != null) {
                return startsWith(content, subMagicOffset, subMagic);
            }

            return true;
        }
    }

    private AudioVideoMimeTypeDetector() {
        throw new AssertionError();
    }

    /**
     * Guesses the MIME type of audio/video content based on its magic bytes.
     * <p>
     * Recognized types: MP3, FLAC, OGG, MKV, WEBM, AVI, WAV, MP4, MOV, M4A.
     *
     * @param content The content bytes to check.
     * @return An {@link Optional} containing the MIME type if recognized as audio/video, or empty if not.
     */
    public static Optional<MimeType> guessAudioVideoMimeType(byte[] content) {
        if (content == null || content.length < 8) {
            return Optional.empty();
        }

        // Special case: MP3 without ID3 tag (frame syncword)
        if ((content[0] & AudioVideoMimeType.MP3.magic[0]) == AudioVideoMimeType.MP3.magic[0] && (content[1] & AudioVideoMimeType.MP3.magic[1]) == AudioVideoMimeType.MP3.magic[1]) {
            return Optional.of(AudioVideoMimeType.MP3);
        }

        for (var type : AudioVideoMimeType.values()) {
            if (type.matches(content)) {

                // Special case: WEBM submagic can appear "anywhere" in the beginning.
                if (type.magic == MKV_MAGIC && new String(content, 0, Math.min(1024, content.length), US_ASCII).contains("webm")) {
                    return Optional.of(AudioVideoMimeType.WEBM);
                }

                // Special case: MP4 magic has multiple possible submagics as "brands".
                if (type == AudioVideoMimeType.MP4 && content.length >= 12) {
                    var brand = new String(content, 8, 4, US_ASCII);

                    return switch (brand) {
                        case "isom", "iso2", "mp41", "mp42", "avc1", "3gp4", "f4v ", "kddi" -> Optional.of(AudioVideoMimeType.MP4);
                        default -> Optional.empty();
                    };
                }

                return Optional.of(type);
            }
        }

        return Optional.empty();
    }


    // Common helper ---------------------------------------------------------------------------------------------------

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
