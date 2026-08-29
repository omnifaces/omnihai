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
import static org.omnifaces.ai.mime.AudioVideoMimeTypeDetector.FTYP_MAGIC;
import static org.omnifaces.ai.mime.AudioVideoMimeTypeDetector.RIFF_MAGIC;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Provides image MIME type detection based on magic bytes.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
final class ImageMimeTypeDetector {

    /** The number of leading bytes which the SVG markup is looked for in. */
    private static final int SVG_HEAD_LENGTH = 1024;

    private static final String SVG_TAG = "<svg";
    private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";

    private enum ImageMimeType implements MimeType {

        JPEG("image/jpeg", new MagicNumber(0, new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF })),
        PNG("image/png", new MagicNumber(0, new byte[] { (byte) 0x89, 'P', 'N', 'G' })),
        GIF("image/gif", new MagicNumber(0, new byte[] { 'G', 'I', 'F', '8' })),
        BMP("image/bmp", new MagicNumber(0, new byte[] { 'B', 'M' })),
        WEBP("image/webp", new MagicNumber(0, RIFF_MAGIC, 8, new byte[] { 'W', 'E', 'B', 'P' })),
        ICO("image/x-icon", new MagicNumber(0, new byte[] { 0x00, 0x00, 0x01, 0x00 })),
        SVG("image/svg+xml", new MagicNumber(0, new byte[] { '<', 's', 'v', 'g' })), // Also handled as special case.
        HEIC("image/heic", new MagicNumber(4, FTYP_MAGIC, 8, new byte[] { 'h', 'e', 'i', 'c' })),
        HEIX("image/heic", new MagicNumber(4, FTYP_MAGIC, 8, new byte[] { 'h', 'e', 'i', 'x' })),
        HEVC("image/heic", new MagicNumber(4, FTYP_MAGIC, 8, new byte[] { 'h', 'e', 'v', 'c' })),
        HEVX("image/heic", new MagicNumber(4, FTYP_MAGIC, 8, new byte[] { 'h', 'e', 'v', 'x' })),
        MIF1("image/heif", new MagicNumber(4, FTYP_MAGIC, 8, new byte[] { 'm', 'i', 'f', '1' })),
        MSF1("image/heif", new MagicNumber(4, FTYP_MAGIC, 8, new byte[] { 'm', 's', 'f', '1' })),
        AVIF("image/avif", new MagicNumber(4, FTYP_MAGIC, 8, new byte[] { 'a', 'v', 'i', 'f' })),
        AVIS("image/avif", new MagicNumber(4, FTYP_MAGIC, 8, new byte[] { 'a', 'v', 'i', 's' })),
        JXL_BOXED("image/jxl", new MagicNumber(4, FTYP_MAGIC, 8, new byte[] { 'j', 'x', 'l', ' ' })),
        JXL("image/jxl", new MagicNumber(0, new byte[] { (byte) 0xFF, 0x0A })),
        JXL_CODESTREAM("image/jxl", new MagicNumber(0, new byte[] { 'J', 'X', 'L', ' ' })),
        TIFF_LE("image/tiff", new MagicNumber(0, new byte[] { 'I', 'I', '*', 0 })),
        TIFF_BE("image/tiff", new MagicNumber(0, new byte[] { 'M', 'M', 0, '*' }));

        private final String value;
        private final String extension;
        private final MagicNumber magicNumber;

        ImageMimeType(String value, MagicNumber magicNumber) {
            this.value = value;
            var subtype = value.substring(value.indexOf('/') + 1);
            this.extension = switch (subtype) {
                case "jpeg" -> "jpg";
                case "x-icon" -> "ico";
                case "svg+xml" -> "svg";
                default -> subtype;
            };
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

    }

    private ImageMimeTypeDetector() {
        throw new AssertionError();
    }

    /**
     * Guesses the mime type of an image based on its magic bytes.
     *
     * @param content The content bytes to check.
     * @return An {@link Optional} containing the mime type if recognized as an image, or empty if not.
     */
    static Optional<MimeType> guessImageMimeType(byte[] content) {
        if (content == null || content.length < 4) {
            return Optional.empty();
        }

        for (var type : ImageMimeType.values()) {
            if (type.matches(content)) {
                return Optional.of(type);
            }
        }

        if (isLikelySvg(content)) {
            return Optional.of(ImageMimeType.SVG);
        }

        return Optional.empty();
    }

    /**
     * Looks up an image MIME type by its string value (e.g. {@code "image/png"}).
     *
     * @param value The MIME type string to match against.
     * @return An {@link Optional} containing the matching image MIME type, or empty if no match is found.
     * @since 1.4
     */
    static Optional<MimeType> lookupImageMimeType(String value) {
        return Arrays.stream(ImageMimeType.values()).filter(type -> type.value.equals(value)).map(MimeType.class::cast).findFirst();
    }

    /**
     * Returns whether the content is likely an SVG, which unlike every other image type carries no magic bytes of its own and must therefore be recognized by
     * its markup. The markup is looked for only when the content opens as XML, as decoding arbitrary bytes as text yields a string in which the tag may occur
     * by coincidence.
     */
    private static boolean isLikelySvg(byte[] content) {
        if (!startsAsXml(content)) {
            return false;
        }

        var head = new String(content, 0, Math.min(SVG_HEAD_LENGTH, content.length), US_ASCII).toLowerCase(Locale.ROOT);
        return head.contains(SVG_TAG) || head.contains(SVG_NAMESPACE);
    }

    /**
     * Returns whether the content opens as an XML document, i.e. its first byte other than a byte order mark or leading whitespace is {@code <}. Binary content
     * carrying the bytes of an SVG tag in its head, such as an MP4 whose {@code uuid} box holds an XML manifest, is thereby not mistaken for an SVG.
     */
    private static boolean startsAsXml(byte[] content) {
        var index = ByteOrderMark.length(content);

        while (index < content.length && isXmlWhitespace(content[index])) {
            index++;
        }

        return index < content.length && content[index] == '<';
    }

    private static boolean isXmlWhitespace(byte value) {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

}
