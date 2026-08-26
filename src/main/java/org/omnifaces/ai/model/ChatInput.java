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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * This class encapsulates the user input for AI chat operations, including the text message and optional file attachments.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see org.omnifaces.ai.AIService#chat(ChatInput, ChatOptions)
 * @see ChatOptions
 */
public class ChatInput implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Represents a single message in a conversation history.
     *
     * @param role The message role.
     * @param content The message content text.
     * @param uploadedFiles The uploaded files.
     * @since 1.0
     * @see ChatInput#getHistory()
     */
    public final record Message(Role role, String content, List<UploadedFile> uploadedFiles) implements Serializable {

        /**
         * Validates the record components.
         *
         * @param role The message role, may not be null.
         * @param content The message content text, may not be blank.
         * @param uploadedFiles The uploaded files, may not be null.
         */
        public Message {
            requireNonNull(role, "role");
            requireNonBlank(content, "content");
            uploadedFiles = unmodifiableList(requireNonNull(uploadedFiles, "uploadedFiles"));
        }

        /**
         * The role of a message in a conversation.
         */
        public enum Role {

            /** A message sent by the user. */
            USER,

            /** A response from the AI assistant. */
            ASSISTANT
        }

    }

    /**
     * Represents a reference to a file uploaded during a conversation turn.
     *
     * @param id The provider-assigned file ID.
     * @param mimeType The MIME type of the uploaded file.
     * @param videoOptions The video analysis options the file was uploaded with, or {@code null} if there were none.
     * @since 1.1
     * @see ChatInput#getHistory()
     */
    public final record UploadedFile(String id, MimeType mimeType, AnalyzeVideoOptions videoOptions) implements Serializable {

        /**
         * Validates the record components.
         *
         * @param id The provider-assigned file ID, may not be blank.
         * @param mimeType The MIME type of the uploaded file, may not be null.
         * @param videoOptions The video analysis options the file was uploaded with, may be null.
         * @since 1.7
         */
        public UploadedFile {
            requireNonBlank(id, "id");
            requireNonNull(mimeType, "mimeType");
        }

        /**
         * Creates an uploaded file which carries no video analysis options, as anything but a video does.
         *
         * @param id The provider-assigned file ID, may not be blank.
         * @param mimeType The MIME type of the uploaded file, may not be null.
         * @since 1.1
         */
        public UploadedFile(String id, MimeType mimeType) {
            this(id, mimeType, null);
        }

    }

    /**
     * Represents an attached file.
     *
     * @see ChatInput.Builder#attach(Path...)
     * @see ChatInput.Builder#attach(byte[]...)
     * @see ChatInput#getImages()
     * @see ChatInput#getFiles()
     * @since 1.0
     */
    public static final class Attachment implements Serializable {

        private static final long serialVersionUID = 1L;

        /** The content bytes, or {@code null} if backed by a {@link #source() Path}. */
        private final byte[] content;
        /** The source path, or {@code null} if backed by {@link #content() byte[]}. */
        private transient Path source;
        /** The MIME type. */
        private final MimeType mimeType;
        /** The file name. */
        private final String fileName;
        /** Additional provider-specific metadata. */
        private final Map<String, String> metadata;
        /** The video analysis options. */
        private final AnalyzeVideoOptions videoOptions;

        /**
         * Creates a new attachment with the given content, MIME type, and file name.
         *
         * @param content The content bytes, must not be {@code null}.
         * @param mimeType The MIME type, must not be {@code null}.
         * @param fileName The file name, must not be blank.
         * @since 1.2
         */
        public Attachment(byte[] content, MimeType mimeType, String fileName) {
            this(content, mimeType, fileName, emptyMap());
        }

        /**
         * Creates a new attachment with the given content, MIME type, file name, and metadata.
         *
         * @param content The content bytes, must not be {@code null}.
         * @param mimeType The MIME type, must not be {@code null}.
         * @param fileName The file name, must not be blank.
         * @param metadata Additional provider-specific metadata to use in upload request, must not be {@code null}.
         */
        public Attachment(byte[] content, MimeType mimeType, String fileName, Map<String, String> metadata) {
            this(requireNonNull(content, "content"), null, mimeType, requireNonBlank(fileName, "fileName"), metadata);
        }

        /**
         * Creates a new attachment backed by a source path for memory-efficient streaming.
         * <p>
         * The file name is derived from the path and the MIME type is detected by reading the first kilobyte of magic bytes. The file content is initially
         * <strong>not</strong> loaded into memory; it will be streamed when the AI service builds the request payload and supports a {@code files} API for file
         * attachments. It is only loaded into memory when the AI service does not support any {@code files} API.
         *
         * @param source The source path, must not be {@code null}.
         * @throws UncheckedIOException if the source cannot be read for MIME type detection.
         * @since 1.2
         */
        public Attachment(Path source) {
            this(source, emptyMap());
        }

        /**
         * Creates a new attachment backed by a source path for memory-efficient streaming.
         * <p>
         * The file name is derived from the path and the MIME type is detected by reading the first kilobyte of magic bytes. The file content is initially
         * <strong>not</strong> loaded into memory; it will be streamed when the AI service builds the request payload and supports a {@code files} API for file
         * attachments. It is only loaded into memory when the AI service does not support any {@code files} API.
         *
         * @param source The source path, must not be {@code null}.
         * @param metadata Additional provider-specific metadata to use in upload request, must not be {@code null}.
         * @throws UncheckedIOException if the source cannot be read for MIME type detection.
         * @since 1.2
         */
        public Attachment(Path source, Map<String, String> metadata) {
            this(null, requireNonNull(source, "source"), guessMimeType(source), null, metadata);
        }

        private Attachment(byte[] content, Path source, MimeType mimeType, String fileName, Map<String, String> metadata) {
            this(content, source, mimeType, fileName, metadata, null);
        }

        private Attachment(
            byte[] content, Path source, MimeType mimeType, String fileName, Map<String, String> metadata, AnalyzeVideoOptions videoOptions
        )
        {
            this.content = content;
            this.source = source;
            this.mimeType = requireNonNull(mimeType, "mimeType");
            this.fileName = fileName != null ? fileName : ("file." + mimeType.extension());
            this.metadata = requireNonNull(metadata, "metadata").entrySet().stream()
                .filter(e -> !isBlank(e.getKey()) && !isBlank(e.getValue()))
                .collect(toUnmodifiableMap(e -> e.getKey().strip(), e -> e.getValue().strip()));
            this.videoOptions = videoOptions;
        }

        /**
         * Custom serialization to handle non-serializable {@link Path}.
         *
         * @param output The object output stream.
         * @throws IOException If an I/O error occurs.
         */
        private void writeObject(ObjectOutputStream output) throws IOException {
            output.defaultWriteObject();
            output.writeObject(source != null ? source.toString() : null);
        }

        /**
         * Custom deserialization to restore {@link Path} from its string representation.
         *
         * @param input The object input stream.
         * @throws IOException If an I/O error occurs.
         * @throws ClassNotFoundException If the class of a serialized object cannot be found.
         */
        private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
            input.defaultReadObject();
            var sourceString = (String) input.readObject();
            if (sourceString != null) {
                source = Path.of(sourceString);
            }
        }

        /**
         * Gets the content bytes, or {@code null} if this attachment is backed by a {@link #source() Path}.
         *
         * @return The content bytes, or {@code null} for Path-backed attachments.
         */
        public byte[] content() {
            return content;
        }

        /**
         * Gets the source path, or {@code null} if this attachment is backed by {@link #content() byte[]}.
         *
         * @return The source path, or {@code null} for byte[]-backed attachments.
         * @since 1.2
         */
        public Path source() {
            return source;
        }

        /**
         * Gets the MIME type.
         *
         * @return The MIME type.
         */
        public MimeType mimeType() {
            return mimeType;
        }

        /**
         * Gets the file name.
         *
         * @return The file name.
         */
        public String fileName() {
            return fileName;
        }

        /**
         * Gets the additional provider-specific metadata.
         *
         * @return An unmodifiable map of metadata.
         */
        public Map<String, String> metadata() {
            return metadata;
        }

        /**
         * Gets the video analysis options.
         *
         * @return The video analysis options, or {@code null} if none are set.
         * @since 1.7
         * @see #withVideoOptions(AnalyzeVideoOptions)
         */
        public AnalyzeVideoOptions videoOptions() {
            return videoOptions;
        }

        /**
         * Gets the content length in bytes, regardless of whether this attachment is backed by {@link #content() byte[]} or a {@link #source() Path}.
         *
         * @return The content length in bytes.
         * @since 1.7
         */
        public long size() {
            return content != null ? content.length : source.toFile().length();
        }

        /**
         * Converts this attachment to a Base64 encoded string.
         *
         * @return The Base64 encoded content.
         */
        public String toBase64() {
            return Base64.getEncoder().encodeToString(content != null ? content : readAllBytes(source));
        }

        /**
         * Converts this attachment to a data URI.
         *
         * @return The data URI string in the format {@code data:<media-type>;base64,<data>}.
         * @throws IllegalStateException if this attachment is backed by a {@link #source() Path}.
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
            var newMetadata = new HashMap<>(metadata);
            newMetadata.put(requireNonBlank(name, "name").strip(), requireNonBlank(value, "value").strip());
            return new Attachment(content, source, mimeType, fileName, newMetadata, videoOptions);
        }

        /**
         * Returns a copy of this attachment with the specified additional metadata.
         *
         * @param metadata Additional provider-specific metadata to use in upload request.
         * @return A new attachment instance with the specified additional metadata.
         * @since 1.1
         */
        public Attachment withMetadata(Map<String, String> metadata) {
            var newMetadata = new HashMap<>(this.metadata);
            newMetadata.putAll(metadata);
            return new Attachment(content, source, mimeType, fileName, newMetadata, videoOptions);
        }

        /**
         * Returns a copy of this attachment with the specified video analysis options, which instruct the AI provider at which rate to sample frames and which
         * clip of the video to analyze.
         *
         * @param videoOptions The video analysis options, or {@code null} to leave them to the AI provider.
         * @return A new attachment instance with the specified video analysis options.
         * @throws IllegalArgumentException if this attachment is not a video.
         * @since 1.7
         */
        public Attachment withVideoOptions(AnalyzeVideoOptions videoOptions) {
            if (!mimeType.isVideo()) {
                throw new IllegalArgumentException("Video options are only applicable to a video attachment, not to " + mimeType.value());
            }

            return new Attachment(content, source, mimeType, fileName, metadata, videoOptions);
        }

        @Override
        public String toString() {
            var stringBuilder = new StringBuilder("Attachment[fileName=").append(fileName)
                .append(", mimeType=").append(mimeType.value());

            stringBuilder.append(", contentLength=").append(size());
            stringBuilder.append(", source=").append(source != null ? source : "byte[]");

            if (!metadata.isEmpty()) {
                stringBuilder.append(", metadata=").append(metadata);
            }

            if (videoOptions != null && !videoOptions.isDefault()) {
                stringBuilder.append(", videoOptions=").append(videoOptions);
            }

            return stringBuilder.append(']').toString();
        }

    }

    /** The user message. */
    private final String message;
    /** The image attachments. */
    private final List<Attachment> images;
    /** The file attachments. */
    private final List<Attachment> files;
    /** The conversation history. */
    private final List<Message> history;

    private ChatInput(Builder builder) {
        this(builder.message, builder.images, builder.files, emptyList());
    }

    private ChatInput(String message, List<Attachment> images, List<Attachment> files, List<Message> history) {
        this.message = message;
        this.images = unmodifiableList(images);
        this.files = unmodifiableList(files);
        this.history = history;
    }

    /**
     * Gets the user message text.
     *
     * @return The message string.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the list of images associated with this input. This does not include non-image files; these are available via {@link #getFiles()}.
     *
     * @return An unmodifiable list of images, or an empty list if no images are attached.
     */
    public List<Attachment> getImages() {
        return images;
    }

    /**
     * Gets the list of files associated with this input. This does not include image files; these are available via {@link #getImages()}.
     *
     * @return An unmodifiable list of files, or an empty list if no files are attached.
     */
    public List<Attachment> getFiles() {
        return files;
    }

    /**
     * Gets the conversation history preceding this input.
     *
     * @return An unmodifiable list of prior messages, or an empty list if no history is present.
     */
    public List<Message> getHistory() {
        return history;
    }

    /**
     * Returns a copy of this input with the specified conversation history.
     * <p>
     * This is used to include prior messages in the AI request payload for multi-turn conversations.
     *
     * @param history The conversation history to include.
     * @return A new {@code ChatInput} containing the same message, images, and files, but with the given history.
     */
    public ChatInput withHistory(List<Message> history) {
        return new ChatInput(message, images, files, unmodifiableList(history));
    }

    /**
     * Creates a new builder for constructing {@link ChatInput} instances. For example:
     *
     * <pre>
     *
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

        private Builder() {
        }

        /**
         * Sets the user message text.
         *
         * @param message The message string.
         * @return This builder instance for chaining.
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * Attaches file contents to this input.
         * <p>
         * File contents are automatically classified based on their content:
         * <ul>
         * <li>Supported image formats (JPEG, PNG, GIF, BMP, WEBP, SVG) are added as images and sanitized for AI compatibility.</li>
         * <li>All other files are added as files with their MIME type auto-detected.</li>
         * </ul>
         *
         * @param contents The file contents to attach.
         * @return This builder instance for chaining.
         * @see ImageHelper#isSupportedAsImageAttachment(MimeType)
         * @see ImageHelper#sanitizeImageAttachment(byte[])
         */
        public Builder attach(byte[]... contents) {
            for (var content : contents) {
                processAndAttach(content, null, guessMimeType(content));
            }

            return this;
        }

        /**
         * Attaches source paths to this input.
         * <p>
         * Source paths are automatically classified based on their content:
         * <ul>
         * <li>Supported image formats (JPEG, PNG, GIF, BMP, WEBP, SVG) are added as images and sanitized for AI compatibility.</li>
         * <li>All other files are added as files with their MIME type auto-detected.</li>
         * </ul>
         *
         * @param sources The source paths to attach.
         * @return This builder instance for chaining.
         * @see ImageHelper#isSupportedAsImageAttachment(MimeType)
         * @see ImageHelper#sanitizeImageAttachment(byte[])
         * @since 1.2
         */
        public Builder attach(Path... sources) {
            for (var source : sources) {
                processAndAttach(null, source, guessMimeType(source));
            }

            return this;
        }

        /**
         * Attaches already processed attachments to this input, as obtained from {@link ChatInput#getImages()} or {@link ChatInput#getFiles()}.
         * <p>
         * They are classified by their own MIME type and are neither re-sanitized nor re-read, so that carrying them from one input to the next costs nothing.
         *
         * @param attachments The attachments to attach.
         * @return This builder instance for chaining.
         * @since 1.6
         */
        public Builder attach(Attachment... attachments) {
            for (var attachment : attachments) {
                (isSupportedAsImageAttachment(attachment.mimeType()) ? images : files).add(attachment);
            }

            return this;
        }

        private void processAndAttach(byte[] content, Path source, MimeType mimeType) {
            var isImage = isSupportedAsImageAttachment(mimeType);
            var list = isImage ? this.images : this.files;
            var prefix = isImage ? "image" : "file";
            var fileName = String.format("%s%d.%s", prefix, list.size() + 1, mimeType.extension());

            byte[] finalContent;
            Path finalSource;

            if (isImage) {
                finalContent = sanitizeImageAttachment(content != null ? content : readAllBytes(source));
                finalSource = null;
            }
            else {
                finalContent = content;
                finalSource = source;
            }

            list.add(new Attachment(finalContent, finalSource, mimeType, fileName, emptyMap()));
        }

        /**
         * Finalizes the configuration and creates a {@link ChatInput} instance.
         *
         * @return A fully configured {@code ChatInput} object.
         * @throws IllegalArgumentException if message is blank.
         */
        public ChatInput build() {
            requireNonBlank(message, "message");
            return new ChatInput(this);
        }

    }

    private static byte[] readAllBytes(Path source) {
        try {
            return Files.readAllBytes(source);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Cannot read all bytes from " + source, e);
        }
    }

}
