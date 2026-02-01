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
package org.omnifaces.ai.model;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static org.omnifaces.ai.helper.ImageHelper.isSupportedAsImageAttachment;
import static org.omnifaces.ai.helper.ImageHelper.sanitizeImageAttachment;
import static org.omnifaces.ai.helper.TextHelper.isBlank;
import static org.omnifaces.ai.helper.TextHelper.requireNonBlank;
import static org.omnifaces.ai.mime.MimeType.guessMimeType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.omnifaces.ai.helper.ImageHelper;
import org.omnifaces.ai.mime.MimeType;

/**
 * Input for chat-based AI interactions.
 * <p>
 * This class encapsulates the user input for AI chat operations, including the text message and optional image and/or file attachments.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see org.omnifaces.ai.AIService#chat(ChatInput, ChatOptions)
 * @see ChatOptions
 */
public class ChatInput implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Represents an attached file.
     * @param content The content bytes.
     * @param mimeType The mime type.
     * @param fileName The file name.
     * @param metadata Additional provider-specific metadata to use in upload request.
     */
    public final record Attachment(byte[] content, MimeType mimeType, String fileName, Map<String, String> metadata) implements Serializable {

        /**
         * Validates and normalizes the record components by stripping whitespace and filtering blank properties.
         *
         * @param content The content bytes.
         * @param mimeType The mime type.
         * @param fileName The file name.
         * @param metadata Additional provider-specific metadata to use in upload request.
         */
        public Attachment {
            content = requireNonNull(content, "content");
            mimeType = requireNonNull(mimeType, "mimeType");
            fileName = requireNonBlank(fileName, "fileName");
            metadata = metadata == null ? emptyMap() : metadata.entrySet().stream()
                    .filter(e -> !isBlank(e.getKey()) && !isBlank(e.getValue()))
                    .collect(toUnmodifiableMap(e -> e.getKey().strip(), e -> e.getValue().strip()));
        }

        /**
         * Converts this attachment to a Base64 encoded string.
         * @return The Base64 encoded content.
         */
        public String toBase64() {
            return Base64.getEncoder().encodeToString(content);
        }

        /**
         * Converts this attachment to a data URI.
         * @return The data URI string in the format {@code data:<media-type>;base64,<data>}.
         */
        public String toDataUri() {
            return "data:" + mimeType.value() + ";base64," + toBase64();
        }

        /**
         * Returns a copy of this attachment with the specified additional metadata.
         *
         * @param name Name of additional provider-specific metadata to use in upload request.
         * @param value Value of additional provider-specific metadata to use in upload request.
         * @return A new attachment instance with the specified additional metadata.
         */
        public Attachment withMetadata(String name, String value) {
            var newHeaders = new HashMap<>(metadata);
            newHeaders.put(requireNonBlank(name, "name").strip(), requireNonBlank(value, "value").strip());
            return new Attachment(content, mimeType, fileName, metadata);
        }
    }

    /** The user message. */
    private final String message;
    /** The image attachments. */
    private final List<Attachment> images;
    /** The file attachments. */
    private final List<Attachment> files;

    private ChatInput(Builder builder) {
        this.message = builder.message;
        this.images = unmodifiableList(builder.images);
        this.files = unmodifiableList(builder.files);
    }

    private ChatInput(String message, List<Attachment> images) {
        this.message = message;
        this.images = unmodifiableList(images);
        this.files = emptyList();
    }

    /**
     * Gets the user message text.
     * @return The message string.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the list of images associated with this input.
     * @return An unmodifiable list of images, or an empty list if no images are attached.
     */
    public List<Attachment> getImages() {
        return images;
    }

    /**
     * Gets the list of files associated with this input.
     * @return An unmodifiable list of files, or an empty list if no files are attached.
     */
    public List<Attachment> getFiles() {
        return files;
    }

    /**
     * Returns a copy of this input without any files.
     * <p>
     * This is useful for providers that handle files separately from the main chat payload.
     *
     * @return A new {@code ChatInput} containing only the message and images.
     */
    public ChatInput withoutFiles() {
        return new ChatInput(message, images);
    }

    /**
     * Creates a new builder for constructing {@link ChatInput} instances. For example:
     * <pre>
     * ChatInput input = ChatInput.newBuilder()
     *     .message("What do you see in these images?")
     *     .attach(image1, image2)
     *     .build();
     * </pre>
     *
     * @return A new {@code ChatInput.Builder} instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for creating {@link ChatInput} instances.
     * <p>
     * Use {@link ChatInput#newBuilder()} to obtain a new builder instance.
     */
    public static class Builder {
        private String message;
        private List<Attachment> images = new ArrayList<>();
        private List<Attachment> files = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the user message text.
         * @param message The message string.
         * @return This builder instance for chaining.
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * Attaches files to this input.
         * <p>
         * Files are automatically classified based on their content:
         * <ul>
         *   <li>Supported image formats (JPEG, PNG, GIF, BMP, WEBP, SVG) are added as images and sanitized for AI compatibility.</li>
         *   <li>All other files are added as files with their MIME type auto-detected.</li>
         * </ul>
         * @param files The file content bytes to attach.
         * @return This builder instance for chaining.
         * @see ImageHelper#isSupportedAsImageAttachment(MimeType)
         * @see ImageHelper#sanitizeImageAttachment(byte[])
         */
        public Builder attach(byte[]... files) {
            for (var content : files) {
                var mimeType = guessMimeType(content);
                var isImage = isSupportedAsImageAttachment(mimeType);
                var processedContent = isImage ? sanitizeImageAttachment(content) : content;
                var prefix = isImage ? "image" : "file";
                var list = isImage ? this.images : this.files;
                var fileName = String.format("%s%d.%s", prefix, list.size() + 1, mimeType.extension());
                list.add(new Attachment(processedContent, mimeType, fileName, null));
            }

            return this;
        }

        /**
         * Finalizes the configuration and creates a {@link ChatInput} instance.
         * @return A fully configured {@code ChatInput} object.
         * @throws IllegalArgumentException if message is blank.
         */
        public ChatInput build() {
            requireNonBlank(message, "message");
            return new ChatInput(this);
        }
    }
}
