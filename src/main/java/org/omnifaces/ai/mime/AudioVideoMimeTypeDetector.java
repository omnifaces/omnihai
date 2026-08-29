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

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Provides audio and video MIME type detection based on magic bytes.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
final class AudioVideoMimeTypeDetector {

    private static final byte[] MKV_MAGIC = { 0x1A, 'E', (byte) 0xDF, (byte) 0xA3 };
    private static final byte[] FORM_MAGIC = { 'F', 'O', 'R', 'M' };
    /** The RIFF magic (to be shared with {@link ImageMimeTypeDetector}). */
    static final byte[] RIFF_MAGIC = { 'R', 'I', 'F', 'F' };
    /** The FTYP magic (to be shared with {@link ImageMimeTypeDetector}). */
    static final byte[] FTYP_MAGIC = { 'f', 't', 'y', 'p' };

    /**
     * The ISO base media file format brand families which denote video, each covering its numbered members such as {@code isom}, {@code iso5} and {@code mp42}.
     */
    private static final Set<String> MP4_BRAND_PREFIXES = Set.of("iso", "mp4", "3gp");

    /**
     * The remaining ISO base media file format brands which denote video. Audio-only brands such as {@code M4A } and image brands such as {@code heic} share
     * the same FTYP magic and must therefore stay out of both this set and {@link #MP4_BRAND_PREFIXES}.
     */
    private static final Set<String> MP4_BRANDS = Set.of("avc1", "dash", "f4v ", "kddi", "mmp4", "msnv", "M4V ");

    private enum AudioVideoMimeType implements MimeType {

        AAC("audio/aac", "aac", new MagicNumber(0, new byte[] { (byte) 0xFF, (byte) 0xF1 })),
        AAC_ADTS("audio/aac", "aac", new MagicNumber(0, new byte[] { (byte) 0xFF, (byte) 0xF9 })), // Also handled as special case.
        MP3("audio/mpeg", "mp3", new MagicNumber(0, new byte[] { (byte) 0xFF, (byte) 0xE0 })), // Also handled as special case.
        MP3_ID3("audio/mpeg", "mp3", new MagicNumber(0, new byte[] { 'I', 'D', '3' })),
        FLAC("audio/flac", "flac", new MagicNumber(0, new byte[] { 'f', 'L', 'a', 'C' })),
        OGG("audio/ogg", "ogg", new MagicNumber(0, new byte[] { 'O', 'g', 'g', 'S' })),
        MKV("video/x-matroska", "mkv", new MagicNumber(0, MKV_MAGIC)),
        WEBM("video/webm", "webm", new MagicNumber(0, MKV_MAGIC)), // Handled as special case.
        AIFF("audio/x-aiff", "aif", new MagicNumber(0, FORM_MAGIC, 8, new byte[] { 'A', 'I', 'F', 'F' })),
        AVI("video/x-msvideo", "avi", new MagicNumber(0, RIFF_MAGIC, 8, new byte[] { 'A', 'V', 'I', ' ' })),
        WAV("audio/wav", "wav", new MagicNumber(0, RIFF_MAGIC, 8, new byte[] { 'W', 'A', 'V', 'E' })),
        MOV("video/quicktime", "mov", new MagicNumber(4, FTYP_MAGIC, 8, new byte[] { 'q', 't', ' ', ' ' })),
        M4A("audio/mp4", "m4a", new MagicNumber(4, FTYP_MAGIC, 8, new byte[] { 'M', '4', 'A', ' ' })),
        MP4("video/mp4", "mp4", new MagicNumber(4, FTYP_MAGIC)); // Handled as special case.

        private final String value;
        private final String extension;
        private final MagicNumber magicNumber;

        AudioVideoMimeType(String value, String extension, MagicNumber magicNumber) {
            this.value = value;
            this.extension = extension;
            this.magicNumber = magicNumber;
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
            return magicNumber.matches(content);
        }

        boolean hasMagic(byte[] magic) {
            return magicNumber.hasMagic(magic);
        }

    }

    private AudioVideoMimeTypeDetector() {
        throw new AssertionError();
    }

    /**
     * Guesses the MIME type of audio/video content based on its magic bytes.
     *
     * @param content The content bytes to check.
     * @return An {@link Optional} containing the MIME type if recognized as audio/video, or empty if not.
     */
    static Optional<MimeType> guessAudioVideoMimeType(byte[] content) {
        if (content == null || content.length < 8) {
            return Optional.empty();
        }

        var frameHeaderType = guessByFrameHeader(content);

        if (frameHeaderType.isPresent()) {
            return frameHeaderType;
        }

        for (var type : AudioVideoMimeType.values()) {
            if (type.matches(content)) {
                return resolveMagicMatch(type, content);
            }
        }

        return Optional.empty();
    }

    /**
     * Guesses the MIME type of a raw audio frame, which opens on a sync word rather than on a magic prefix and therefore matches no entry of
     * {@link AudioVideoMimeType}.
     */
    private static Optional<MimeType> guessByFrameHeader(byte[] content) {
        if (content[0] != (byte) 0xFF) {
            return Optional.empty();
        }

        // Special case: AAC ADTS frame (12-bit syncword 0xFFF, layer bits = 00)
        if ((content[1] & 0xF6) == 0xF0) {
            return Optional.of(AudioVideoMimeType.AAC);
        }

        // Special case: MP3 without ID3 tag (11-bit syncword 0xFFE, layer bits != 00)
        if ((content[1] & 0xE0) == 0xE0) {
            return Optional.of(AudioVideoMimeType.MP3);
        }

        return Optional.empty();
    }

    /**
     * Answers the MIME type of content whose magic bytes matched the given type, which for a container magic shared by several types depends on a submagic
     * further into the content.
     */
    private static Optional<MimeType> resolveMagicMatch(AudioVideoMimeType type, byte[] content) {

        // Special case: WEBM submagic can appear "anywhere" in the beginning.
        if (type.hasMagic(MKV_MAGIC) && new String(content, 0, Math.min(128, content.length), US_ASCII).contains("webm")) {
            return Optional.of(AudioVideoMimeType.WEBM);
        }

        // Special case: MP4 magic has multiple possible submagics as "brands".
        if (type == AudioVideoMimeType.MP4 && content.length >= 12) {
            var brand = new String(content, 8, 4, US_ASCII);

            return isVideoBrand(brand) ? Optional.of(AudioVideoMimeType.MP4) : Optional.empty();
        }

        return Optional.of(type);
    }

    private static boolean isVideoBrand(String brand) {
        return MP4_BRANDS.contains(brand) || MP4_BRAND_PREFIXES.stream().anyMatch(brand::startsWith);
    }

    /**
     * Looks up an audio/video MIME type by its string value (e.g. {@code "audio/mpeg"}).
     *
     * @param value The MIME type string to match against.
     * @return An {@link Optional} containing the matching audio/video MIME type, or empty if no match is found.
     * @since 1.4
     */
    static Optional<MimeType> lookupAudioVideoMimeType(String value) {
        return Arrays.stream(AudioVideoMimeType.values()).filter(type -> type.value.equals(value)).map(MimeType.class::cast).findFirst();
    }

}
