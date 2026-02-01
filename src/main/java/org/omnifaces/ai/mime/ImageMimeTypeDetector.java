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
import static org.omnifaces.ai.mime.AudioVideoMimeTypeDetector.startsWith;

import java.util.Optional;

/**
 * Provides image MIME type detection based on magic bytes.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
final class ImageMimeTypeDetector {

    private enum ImageMimeType implements MimeType {
        JPEG("image/jpeg", 0, new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF}, 0, null),
        PNG("image/png", 0, new byte[]{(byte)0x89, 'P', 'N', 'G'}, 0, null),
        GIF("image/gif", 0, new byte[]{'G', 'I', 'F', '8'}, 0, null),
        BMP("image/bmp", 0, new byte[]{'B', 'M'}, 0, null),
        WEBP("image/webp", 0, RIFF_MAGIC, 8, new byte[]{'W', 'E', 'B', 'P'}),
        ICO("image/x-icon", 0, new byte[]{0x00, 0x00, 0x01, 0x00}, 0, null),
        SVG("image/svg+xml", 0, new byte[]{'<', 's', 'v', 'g'}, 0, null), // Also handled as special case.
        HEIC("image/heic", 4, FTYP_MAGIC, 8, new byte[]{'h', 'e', 'i', 'c'}),
        MIF1("image/heif", 4, FTYP_MAGIC, 8, new byte[]{'m', 'i', 'f', '1'}),
        JXL("image/jxl", 0, new byte[]{(byte)0xFF, 0x0A}, 0, null),
        JXL_CODESTREAM("image/jxl", 0, new byte[]{'J', 'X', 'L', ' '}, 0, null),
        TIFF_LE("image/tiff", 0, new byte[]{'I', 'I', '*', 0}, 0, null),
        TIFF_BE("image/tiff", 0, new byte[]{'M', 'M', 0, '*'}, 0, null);

        private final String value;
        private final String extension;
        private final int magicOffset;
        private final byte[] magic;
        private final int subMagicOffset;
        private final byte[] subMagic;

        ImageMimeType(String value, int magicOffset, byte[] magic, int subMagicOffset, byte[] subMagic) {
            this.value = value;
            var subtype = value.substring(value.indexOf('/') + 1);
            this.extension = switch (subtype) {
                case "jpeg" -> "jpg";
                case "x-icon" -> "ico";
                case "svg+xml" -> "svg";
                default -> subtype;
            };
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

    private ImageMimeTypeDetector() {
        throw new AssertionError();
    }

    /**
     * Guesses the mime type of an image based on its magic bytes.
     * <p>
     * Recognized types: JPEG, PNG, GIF, BMP, WEBP, ICO, SVG, HEIC, MIF1, JXL, TIFF.
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

    private static boolean isLikelySvg(byte[] content) {
        var head = new String(content, 0, Math.min(1024, content.length), US_ASCII).toLowerCase();
        return head.startsWith("<?xml") && (head.contains("<svg") || head.contains("http://www.w3.org/2000/svg"));
    }
}
