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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatInput.Message;
import org.omnifaces.ai.model.ChatInput.Message.Role;

class ChatInputTest {

    @TempDir
    private Path tempDir;

    private static final MimeType TEST_PNG = new MimeType() {

        @Override
        public String value() {
            return "image/png";
        }

        @Override
        public String extension() {
            return "png";
        }

    };

    private static final MimeType TEST_MP4 = new MimeType() {

        @Override
        public String value() {
            return "video/mp4";
        }

        @Override
        public String extension() {
            return "mp4";
        }

    };

    private static final MimeType TEST_PDF = new MimeType() {

        @Override
        public String value() {
            return "application/pdf";
        }

        @Override
        public String extension() {
            return "pdf";
        }

    };

    private static final byte[] PNG_BYTES = createTestImage("PNG");
    private static final byte[] JPEG_BYTES = createTestImage("JPEG");
    private static final byte[] GIF_BYTES = createTestImage("GIF");

    private static byte[] createTestImage(String format) {
        try {
            var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, 0xFF0000); // red pixel
            var baos = new ByteArrayOutputStream();
            ImageIO.write(image, format, baos);
            return baos.toByteArray();
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to create test " + format + " image", e);
        }
    }

    private static final byte[] PDF_BYTES = { '%', 'P', 'D', 'F', '-', '1', '.', '4' };

    // =================================================================================================================
    // Builder tests - message
    // =================================================================================================================

    @Test
    void builder_messageOnly() {
        var input = ChatInput.newBuilder()
            .message("Hello, AI!")
            .build();

        assertEquals("Hello, AI!", input.getMessage());
        assertTrue(input.getImages().isEmpty());
        assertTrue(input.getFiles().isEmpty());
    }

    @Test
    void builder_message_null_throwsException() {
        var builder = ChatInput.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(exception.getMessage().contains("message"));
    }

    @Test
    void builder_message_blank_throwsException() {
        for (var blank : List.of("", "   ", "\t\n")) {
            var builder = ChatInput.newBuilder().message(blank);

            assertThrows(IllegalArgumentException.class, builder::build);
        }
    }

    // =================================================================================================================
    // Builder tests - attach images
    // =================================================================================================================

    @Test
    void builder_attachPngImage() {
        var input = ChatInput.newBuilder()
            .message("What's in this image?")
            .attach(PNG_BYTES)
            .build();

        assertEquals(1, input.getImages().size());
        assertTrue(input.getFiles().isEmpty());

        var attachment = input.getImages().get(0);
        assertEquals("image/png", attachment.mimeType().value());
        assertEquals("image1.png", attachment.fileName());
    }

    @Test
    void builder_attachMultipleImages() {
        var input = ChatInput.newBuilder()
            .message("Compare these images")
            .attach(PNG_BYTES, JPEG_BYTES, GIF_BYTES)
            .build();

        assertEquals(3, input.getImages().size());
        assertEquals("image1.png", input.getImages().get(0).fileName());
        assertTrue(input.getImages().get(1).fileName().startsWith("image2"));
        assertTrue(input.getImages().get(2).fileName().startsWith("image3"));
    }

    // =================================================================================================================
    // Builder tests - attach files
    // =================================================================================================================

    @Test
    void builder_attachPdfFile() {
        var input = ChatInput.newBuilder()
            .message("Summarize this document")
            .attach(PDF_BYTES)
            .build();

        assertTrue(input.getImages().isEmpty());
        assertEquals(1, input.getFiles().size());

        var attachment = input.getFiles().get(0);
        assertEquals("application/pdf", attachment.mimeType().value());
        assertEquals("file1.pdf", attachment.fileName());
    }

    // =================================================================================================================
    // Builder tests - mixed attachments
    // =================================================================================================================

    @Test
    void builder_attachMixedImagesAndFiles() {
        var input = ChatInput.newBuilder()
            .message("Analyze all of these")
            .attach(PNG_BYTES, PDF_BYTES, JPEG_BYTES)
            .build();

        assertEquals(2, input.getImages().size());
        assertEquals(1, input.getFiles().size());
    }

    @Test
    void builder_attachCalledMultipleTimes() {
        var input = ChatInput.newBuilder()
            .message("Multiple attachments")
            .attach(PNG_BYTES)
            .attach(JPEG_BYTES)
            .attach(PDF_BYTES)
            .build();

        assertEquals(2, input.getImages().size());
        assertEquals(1, input.getFiles().size());
    }

    // =================================================================================================================
    // Immutability tests
    // =================================================================================================================

    @Test
    void getImages_isImmutable() {
        var input = ChatInput.newBuilder()
            .message("Test")
            .attach(PNG_BYTES)
            .build();

        var images = input.getImages();
        var image = new Attachment(new byte[0], TEST_PNG, "test.png", emptyMap());

        assertThrows(UnsupportedOperationException.class, () -> images.add(image));
    }

    @Test
    void getFiles_isImmutable() {
        var input = ChatInput.newBuilder()
            .message("Test")
            .attach(PDF_BYTES)
            .build();

        var files = input.getFiles();
        var file = new Attachment(new byte[0], TEST_PDF, "test.pdf", emptyMap());

        assertThrows(UnsupportedOperationException.class, () -> files.add(file));
    }

    // =================================================================================================================
    // withHistory tests
    // =================================================================================================================

    @Test
    void withHistory_preservesMessageAndAttachments() {
        var input = ChatInput.newBuilder()
            .message("Current message")
            .attach(PNG_BYTES, PDF_BYTES)
            .build();

        var history = List.of(new Message(Role.USER, "Hi", emptyList()), new Message(Role.ASSISTANT, "Hello", emptyList()));
        var withHistory = input.withHistory(history);

        assertNotSame(input, withHistory);
        assertEquals("Current message", withHistory.getMessage());
        assertEquals(1, withHistory.getImages().size());
        assertEquals(1, withHistory.getFiles().size());
        assertEquals(2, withHistory.getHistory().size());
        assertEquals(Role.USER, withHistory.getHistory().get(0).role());
        assertEquals("Hi", withHistory.getHistory().get(0).content());
    }

    @Test
    void withHistory_originalUnchanged() {
        var input = ChatInput.newBuilder()
            .message("Test")
            .build();

        input.withHistory(List.of(new Message(Role.USER, "old", emptyList())));

        assertTrue(input.getHistory().isEmpty());
    }

    @Test
    void builder_defaultHistory_isEmpty() {
        var input = ChatInput.newBuilder()
            .message("Test")
            .build();

        assertTrue(input.getHistory().isEmpty());
    }

    // =================================================================================================================
    // Attachment record tests
    // =================================================================================================================

    @Test
    void attachment_content() {
        var content = new byte[] { 1, 2, 3, 4, 5 };
        var attachment = new Attachment(content, TEST_PNG, "test.png", emptyMap());

        assertArrayEquals(content, attachment.content());
        assertEquals(TEST_PNG, attachment.mimeType());
        assertEquals("test.png", attachment.fileName());
    }

    @Test
    void attachment_base64() {
        var content = new byte[] { 1, 2, 3, 4, 5 };
        var attachment = new Attachment(content, TEST_PNG, "test.png", emptyMap());

        var base64 = attachment.toBase64();
        var decoded = Base64.getDecoder().decode(base64);
        assertArrayEquals(content, decoded);
    }

    @Test
    void attachment_dataUri() {
        var content = new byte[] { 1, 2, 3, 4, 5 };
        var attachment = new Attachment(content, TEST_PNG, "test.png", emptyMap());

        var dataUri = attachment.toDataUri();
        assertTrue(dataUri.startsWith("data:image/png;base64,"));
    }

    // =================================================================================================================
    // Serialization tests
    // =================================================================================================================

    @Test
    void implementsSerializable() {
        assertTrue(Serializable.class.isAssignableFrom(ChatInput.class));
        assertTrue(Serializable.class.isAssignableFrom(Attachment.class));
    }

    @Test
    void serialization_preservesAllFields() throws Exception {
        var original = ChatInput.newBuilder()
            .message("Test with attachments")
            .attach(PNG_BYTES, PDF_BYTES)
            .build();

        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        var bais = new ByteArrayInputStream(baos.toByteArray());
        try (var ois = new ObjectInputStream(bais)) {
            var deserialized = (ChatInput) ois.readObject();

            assertEquals(original.getMessage(), deserialized.getMessage());
            assertEquals(original.getImages().size(), deserialized.getImages().size());
            assertEquals(original.getFiles().size(), deserialized.getFiles().size());
        }
    }

    // =================================================================================================================
    // Video options
    // =================================================================================================================

    @Test
    void attachment_videoOptions_nullByDefault() {
        var attachment = new Attachment(new byte[] { 1 }, TEST_MP4, "video.mp4");

        assertNull(attachment.videoOptions());
    }

    @Test
    void attachment_withVideoOptions_copiesAttachment() {
        var attachment = new Attachment(new byte[] { 1 }, TEST_MP4, "video.mp4");
        var videoOptions = AnalyzeVideoOptions.newBuilder().fps(2).build();

        var copy = attachment.withVideoOptions(videoOptions);

        assertNotSame(attachment, copy);
        assertNull(attachment.videoOptions());
        assertEquals(videoOptions, copy.videoOptions());
        assertEquals(attachment.fileName(), copy.fileName());
        assertArrayEquals(attachment.content(), copy.content());
    }

    @Test
    void attachment_withMetadata_preservesVideoOptions() {
        var videoOptions = AnalyzeVideoOptions.newBuilder().fps(2).build();
        var attachment = new Attachment(new byte[] { 1 }, TEST_MP4, "video.mp4").withVideoOptions(videoOptions);

        assertEquals(videoOptions, attachment.withMetadata("purpose", "user_data").videoOptions());
        assertEquals(videoOptions, attachment.withMetadata(Map.of("purpose", "user_data")).videoOptions());
    }

    @Test
    void attachment_withVideoOptions_nonVideo_throwsException() {
        var attachment = new Attachment(new byte[] { 1 }, TEST_PDF, "dummy.pdf");

        assertThrows(IllegalArgumentException.class, () -> attachment.withVideoOptions(AnalyzeVideoOptions.DEFAULT));
    }

    // =================================================================================================================
    // Edge cases
    // =================================================================================================================

    @Test
    void attachment_emptyContent() {
        var attachment = new Attachment(new byte[0], TEST_PNG, "empty.png", emptyMap());

        assertEquals(0, attachment.content().length);
        assertNotNull(attachment.toBase64());
        assertNotNull(attachment.toDataUri());
    }

    // =================================================================================================================
    // Attachment - byte[] versus path backed
    // =================================================================================================================

    /**
     * An attachment reports the length of what it carries, whether that is held in memory or still on disk.
     */
    @Test
    void size_followsWhereTheContentLives() throws IOException {
        var file = Files.write(tempDir.resolve("test.png"), new byte[] { 1, 2, 3 });

        assertEquals(3, new Attachment(new byte[] { 1, 2, 3 }, TEST_PNG, "test.png", emptyMap()).size());
        assertEquals(3, new Attachment(file).size());
    }

    @Test
    void toBase64_pathBackedAttachment_readsTheFile() throws IOException {
        var file = Files.write(tempDir.resolve("test.png"), new byte[] { 1, 2, 3 });

        assertEquals(Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 }), new Attachment(file).toBase64());
    }

    /**
     * A path backed attachment reads its file when it is asked for the content, which is not when it was attached, so the file may be gone by then.
     */
    @Test
    void toBase64_pathWhichWentAway_namesTheFileItCouldNotRead() throws IOException {
        var file = Files.write(tempDir.resolve("vanishing.png"), new byte[] { 1, 2, 3 });
        var attachment = new Attachment(file);
        Files.delete(file);

        var exception = assertThrows(UncheckedIOException.class, attachment::toBase64);
        assertTrue(exception.getMessage().contains("vanishing.png"));
    }

    /**
     * A path is not serializable, so it travels as its string form and comes back as a path again.
     */
    @Test
    void serialization_pathBackedAttachment_restoresTheSource() throws Exception {
        var file = Files.write(tempDir.resolve("test.png"), new byte[] { 1, 2, 3 });

        var bytes = new ByteArrayOutputStream();

        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(new Attachment(file));
        }

        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(file, ((Attachment) input.readObject()).source());
        }
    }

    /**
     * Metadata is handed to the AI provider as request fields, so an entry without a name or without a value is dropped rather than sent empty.
     */
    @Test
    void metadata_blankNameOrValue_isDropped() {
        var metadata = new HashMap<String, String>();
        metadata.put("kept", "value");
        metadata.put(" ", "value without a name");
        metadata.put("name without a value", " ");

        var attachment = new Attachment(new byte[0], TEST_PNG, "test.png", metadata);

        assertEquals(Map.of("kept", "value"), attachment.metadata());
    }

    // =================================================================================================================
    // Attachment - toString
    // =================================================================================================================

    /**
     * An attachment is filed by what it carries: an image joins the images so it can be inlined, anything else joins the files so it can be uploaded.
     */
    @Test
    void attach_filesTheAttachmentByItsMimeType() {
        var input = ChatInput.newBuilder()
            .message("Look")
            .attach(new Attachment(new byte[0], TEST_PNG, "test.png", emptyMap()), new Attachment(new byte[0], TEST_PDF, "test.pdf", emptyMap()))
            .build();

        assertEquals(1, input.getImages().size());
        assertEquals(1, input.getFiles().size());
    }

    @Test
    void toString_byteBackedAttachment_namesTheContentAsBytes() {
        var attachment = new Attachment(new byte[] { 1, 2, 3 }, TEST_PNG, "test.png", emptyMap());

        assertEquals("Attachment[fileName=test.png, mimeType=image/png, contentLength=3, source=byte[]]", attachment.toString());
    }

    @Test
    void toString_pathBackedAttachment_namesTheSource() throws IOException {
        var file = Files.write(tempDir.resolve("test.png"), new byte[] { 1, 2, 3 });

        assertTrue(new Attachment(file).toString().contains("source=" + file));
    }

    @Test
    void toString_withMetadata_namesIt() {
        var attachment = new Attachment(new byte[0], TEST_PNG, "test.png", Map.of("purpose", "avatar"));

        assertTrue(attachment.toString().contains("metadata={purpose=avatar}"));
    }

    @Test
    void toString_withoutMetadata_omitsIt() {
        assertFalse(new Attachment(new byte[0], TEST_PNG, "test.png", emptyMap()).toString().contains("metadata"));
    }

    /**
     * Video options which state nothing beyond the defaults tell the reader nothing either, so they stay out of the description.
     */
    @Test
    void toString_withNonDefaultVideoOptions_namesThem() {
        var attachment = new Attachment(new byte[0], TEST_MP4, "test.mp4", emptyMap());

        assertFalse(attachment.withVideoOptions(AnalyzeVideoOptions.DEFAULT).toString().contains("videoOptions"));
        assertTrue(attachment.withVideoOptions(AnalyzeVideoOptions.newBuilder().fps(2).build()).toString().contains("videoOptions"));
    }

    /**
     * An image attached by path is read and sanitized just as one attached by content is, so that what reaches the AI does not depend on how it was handed
     * over.
     */
    @Test
    void attach_imageByPath_isReadAndSanitized() throws IOException {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", bytes);
        var file = Files.write(tempDir.resolve("image.png"), bytes.toByteArray());

        var input = ChatInput.newBuilder().message("Look").attach(file).build();

        assertEquals(1, input.getImages().size());
        assertTrue(input.getImages().get(0).size() > 0);
    }

}
