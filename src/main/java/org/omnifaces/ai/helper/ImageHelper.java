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

import static org.omnifaces.ai.mime.MimeType.guessMimeType;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

import javax.imageio.ImageIO;

import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.mime.MimeType;

/**
 * Provides image sanitization for AI model compatibility (removing alpha channels, converting legacy formats to PNG).
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public final class ImageHelper {

    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp", "image/svg+xml");
    private static final Set<String> NEEDS_ALPHA_CHANNEL_REMOVAL = Set.of("image/png", "image/gif");
    private static final Set<String> NEEDS_PNG_CONVERSION = Set.of("image/gif", "image/bmp");
    private static final String ERROR_UNSUPPORTED_IMAGE = "%s. Please try a different image, preferably WEBP, JPEG or PNG without transparency.";

    private ImageHelper() {
        throw new AssertionError();
    }

    /**
     * Checks whether the given mime type is supported as image attachment.
     * <p>
     * Supported types: JPEG, PNG, GIF, BMP, WEBP, SVG (you'll still need to use {@link #sanitizeImageAttachment(byte[])} afterwards).
     *
     * @param mimeType The mime type.
     * @return {@code true} if the given mime type is supported as image attachment, {@code false} otherwise.
     */
    public static boolean isSupportedAsImageAttachment(MimeType mimeType) {
        return SUPPORTED_IMAGE_TYPES.contains(mimeType.value());
    }

    /**
     * Sanitizes the given image for use as an AI attachment.
     * <p>
     * This will automatically remove any alpha channel from PNG and GIF images, and convert legacy formats
     * like GIF and BMP to PNG for broader AI model compatibility.
     *
     * @param image The image bytes.
     * @return The sanitized image bytes (may be converted to PNG).
     * @throws AIException when image format is not supported.
     */
    public static byte[] sanitizeImageAttachment(byte[] image) {
        var mimeType = guessMimeType(image);

        if (!isSupportedAsImageAttachment(mimeType)) {
            throw new AIException(ERROR_UNSUPPORTED_IMAGE.formatted("Unsupported image"));
        }

        var sanitized = image;

        if (NEEDS_ALPHA_CHANNEL_REMOVAL.contains(mimeType.value())) {
            sanitized = removeAnyAlphaChannel(sanitized);
        }
        else if (NEEDS_PNG_CONVERSION.contains(mimeType.value())) {
            sanitized = convertToPng(sanitized);
        }

        return sanitized;
    }

    /**
     * Some models can't deal with alpha channels found in some PNG/GIF images, so we make sure we remove it beforehand (and we convert GIF to PNG immediately).
     */
    private static byte[] removeAnyAlphaChannel(byte[] image) {
        try {
            var input = ImageIO.read(new ByteArrayInputStream(image));
            var rgbOnly = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = rgbOnly.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgbOnly.getWidth(), rgbOnly.getHeight());
            graphics.drawImage(input, 0, 0, null);
            graphics.dispose();
            return saveAsPng(rgbOnly);
        }
        catch (Exception e) {
            throw new AIException(ERROR_UNSUPPORTED_IMAGE.formatted("Cannot remove alpha channel from image"), e);
        }
    }

    /**
     * Some models don't support legacy image formats like GIF/BMP, so we proactively convert these to PNG.
     */
    private static byte[] convertToPng(byte[] image) {
        try {
            return saveAsPng(ImageIO.read(new ByteArrayInputStream(image)));
        }
        catch (Exception e) {
            throw new AIException(ERROR_UNSUPPORTED_IMAGE.formatted("Cannot convert image to PNG"), e);
        }
    }

    private static byte[] saveAsPng(BufferedImage image) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        return output.toByteArray();
    }
}
