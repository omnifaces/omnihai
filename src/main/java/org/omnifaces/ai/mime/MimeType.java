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

import static org.omnifaces.ai.mime.AudioVideoMimeTypeDetector.guessAudioVideoMimeType;
import static org.omnifaces.ai.mime.DocumentMimeTypeDetector.guessDocumentMimeType;
import static org.omnifaces.ai.mime.ImageMimeTypeDetector.guessImageMimeType;

/**
 * Represents a MIME type with its associated file extension.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public interface MimeType {

    /**
     * Returns the MIME type string.
     * @return The MIME type string (e.g., "application/pdf").
     */
    String value();

    /**
     * Returns the file extension.
     * @return The file extension without a leading dot (e.g., "pdf").
     */
    String extension();

    /**
     * Returns whether this is an image mime type.
     * @return whether this is an image mime type.
     * @since 1.1
     */
    default boolean isImage() {
        return value().startsWith("image/");
    }

    /**
     * Returns whether this is an audio mime type.
     * @return whether this is an audio mime type.
     * @since 1.1
     */
    default boolean isAudio() {
        return value().startsWith("audio/");
    }

    /**
     * Returns whether this is a video mime type.
     * @return whether this is a video mime type.
     * @since 1.1
     */
    default boolean isVideo() {
        return value().startsWith("video/");
    }

    /**
     * Guesses the MIME type of the given content based on magic bytes.
     * <p>
     * Detection order: images first, then audio/video, then documents. Falls back to {@code application/octet-stream}
     * for unrecognized binary content or {@code text/plain} for unrecognized text content.
     *
     * @param content The content bytes to analyze.
     * @return The detected MIME type, never {@code null}.
     */
    static MimeType guessMimeType(byte[] content) {
        return guessImageMimeType(content).or(() -> guessAudioVideoMimeType(content)).orElseGet(() -> guessDocumentMimeType(content));
    }
}
