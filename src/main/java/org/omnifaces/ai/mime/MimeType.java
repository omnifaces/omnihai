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

import static java.lang.Math.min;
import static java.nio.file.Files.size;
import static org.omnifaces.ai.helper.FileHelper.newOffsetInputStream;
import static org.omnifaces.ai.helper.TextHelper.requireNonBlank;
import static org.omnifaces.ai.mime.AudioVideoMimeTypeDetector.guessAudioVideoMimeType;
import static org.omnifaces.ai.mime.AudioVideoMimeTypeDetector.lookupAudioVideoMimeType;
import static org.omnifaces.ai.mime.DocumentMimeTypeDetector.guessDocumentMimeType;
import static org.omnifaces.ai.mime.DocumentMimeTypeDetector.lookupDocumentMimeType;
import static org.omnifaces.ai.mime.ImageMimeTypeDetector.guessImageMimeType;
import static org.omnifaces.ai.mime.ImageMimeTypeDetector.lookupImageMimeType;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Represents a MIME type with its associated file extension.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public interface MimeType {

    /**
     * Returns the MIME type string.
     *
     * @return The MIME type string (e.g., "application/pdf").
     */
    String value();

    /**
     * Returns the file extension.
     *
     * @return The file extension without a leading dot (e.g., "pdf").
     */
    String extension();

    /**
     * Returns whether this is an image mime type.
     *
     * @return whether this is an image mime type.
     * @since 1.1
     */
    default boolean isImage() {
        return value().startsWith("image/");
    }

    /**
     * Returns whether this is an audio mime type.
     *
     * @return whether this is an audio mime type.
     * @since 1.1
     */
    default boolean isAudio() {
        return value().startsWith("audio/");
    }

    /**
     * Returns whether this is a video mime type.
     *
     * @return whether this is a video mime type.
     * @since 1.1
     */
    default boolean isVideo() {
        return value().startsWith("video/");
    }

    /**
     * Guesses the MIME type of the given content based on magic bytes.
     * <p>
     * Detection order: images first, then audio/video, then documents. Falls back to {@code application/octet-stream} for unrecognized binary content or
     * {@code text/plain} for unrecognized text content.
     *
     * @param content The content bytes to analyze.
     * @return The detected MIME type, never {@code null}.
     */
    static MimeType guessMimeType(byte[] content) {
        return guessImageMimeType(content).or(() -> guessAudioVideoMimeType(content)).orElseGet(() -> guessDocumentMimeType(content));
    }

    /**
     * Guesses the MIME type of the given source based on magic bytes.
     * <p>
     * Detection order: images first, then audio/video, then documents. Falls back to {@code application/octet-stream} for unrecognized binary content or
     * {@code text/plain} for unrecognized text content.
     *
     * @param source The source path to analyze, must not be {@code null}.
     * @return The detected MIME type, never {@code null}.
     * @since 1.4
     */
    static MimeType guessMimeType(Path source) {
        try (var stream = newOffsetInputStream(source, 0L, min(size(source), 1024))) {
            return guessMimeType(stream.readAllBytes());
        }
        catch (IOException e) {
            throw new UncheckedIOException("Cannot read magic bytes from " + source, e);
        }
    }

    /**
     * Returns a {@link MimeType} for the given MIME type string.
     * <p>
     * If the string matches a known MIME type (image, audio/video, or document), the corresponding built-in instance is returned. Otherwise a
     * forward-compatible fallback instance is returned whose {@link #extension()} is derived from the MIME subtype (e.g. {@code "application/x-custom"} yields
     * extension {@code "x-custom"}).
     * <p>
     * This is primarily used when rehydrating a {@link org.omnifaces.ai.model.ChatOptions} from JSON, where the MIME type is identified by its string value.
     *
     * @param value The MIME type string, must not be {@code null} or blank.
     * @return A {@link MimeType} instance for the given value, never {@code null}.
     * @throws IllegalArgumentException if {@code value} is {@code null} or blank.
     * @since 1.4
     */
    static MimeType of(String value) {
        var sanitized = requireNonBlank(value, "value").strip();
        return lookupImageMimeType(sanitized).or(() -> lookupAudioVideoMimeType(sanitized)).or(() -> lookupDocumentMimeType(sanitized))
            .orElseGet(() -> new UnknownMimeType(sanitized));
    }

    /**
     * Fallback implementation of {@link MimeType} for values that do not match a known built-in type.
     * <p>
     * The {@link #extension()} is derived from the subtype portion of the MIME string (after the slash), stripping any parameters after a semicolon.
     *
     * @param value The MIME type string, must not be {@code null} or blank.
     * @param extension The file extension without a leading dot (e.g., "pdf"), must not be {@code null} or blank.
     * @since 1.4
     * @see MimeType#of(String)
     */
    final record UnknownMimeType(String value, String extension) implements MimeType, Serializable {

        /**
         * Validates the record components.
         *
         * @param value The MIME type string, must not be {@code null} or blank.
         * @param extension The file extension without a leading dot (e.g., "pdf"), must not be {@code null} or blank.
         * @throws IllegalArgumentException if the value or the extension is {@code null} or blank.
         * @since 1.7
         */
        public UnknownMimeType {
            value = requireNonBlank(value, "value");
            extension = requireNonBlank(extension, "extension");
        }

        /**
         * Creates an unknown MIME type whose extension is derived from the subtype portion of the given value.
         *
         * @param value The MIME type string, must not be {@code null} or blank.
         * @throws IllegalArgumentException if the value is {@code null} or blank, or states no subtype to derive an extension from.
         * @since 1.4
         */
        UnknownMimeType(String value) {
            this(value, deriveExtension(value));
        }

        /**
         * Derives the file extension from the subtype portion of the given MIME type string, being whatever follows the slash, up to any parameter.
         *
         * @param value The MIME type string.
         * @return The derived file extension.
         */
        private static String deriveExtension(String value) {
            var subtype = value.contains("/") ? value.substring(value.indexOf('/') + 1) : value;
            return subtype.contains(";") ? subtype.substring(0, subtype.indexOf(';')).strip() : subtype;
        }

    }

}
