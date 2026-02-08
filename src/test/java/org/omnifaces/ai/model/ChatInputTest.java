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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatInput.Message;
import org.omnifaces.ai.model.ChatInput.Message.Role;

class ChatInputTest {

    private static final MimeType TEST_PNG = new MimeType() {
        @Override public String value() { return "image/png"; }
        @Override public String extension() { return "png"; }
    };

    private static final MimeType TEST_PDF = new MimeType() {
        @Override public String value() { return "application/pdf"; }
        @Override public String extension() { return "pdf"; }
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
        } catch (IOException e) {
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
        assertThrows(IllegalArgumentException.class, () -> ChatInput.newBuilder().message("").build());
        assertThrows(IllegalArgumentException.class, () -> ChatInput.newBuilder().message("   ").build());
        assertThrows(IllegalArgumentException.class, () -> ChatInput.newBuilder().message("\t\n").build());
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

        assertThrows(UnsupportedOperationException.class,
                () -> input.getImages().add(new Attachment(new byte[0], TEST_PNG, "test.png", null)));
    }

    @Test
    void getFiles_isImmutable() {
        var input = ChatInput.newBuilder()
                .message("Test")
                .attach(PDF_BYTES)
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> input.getFiles().add(new Attachment(new byte[0], TEST_PDF, "test.pdf", null)));
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

        var history = List.of(new Message(Role.USER, "Hi"), new Message(Role.ASSISTANT, "Hello"));
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

        input.withHistory(List.of(new Message(Role.USER, "old")));

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
        var attachment = new Attachment(content, TEST_PNG, "test.png", null);

        assertArrayEquals(content, attachment.content());
        assertEquals(TEST_PNG, attachment.mimeType());
        assertEquals("test.png", attachment.fileName());
    }

    @Test
    void attachment_base64() {
        var content = new byte[] { 1, 2, 3, 4, 5 };
        var attachment = new Attachment(content, TEST_PNG, "test.png", null);

        var base64 = attachment.toBase64();
        var decoded = Base64.getDecoder().decode(base64);
        assertArrayEquals(content, decoded);
    }

    @Test
    void attachment_dataUri() {
        var content = new byte[] { 1, 2, 3, 4, 5 };
        var attachment = new Attachment(content, TEST_PNG, "test.png", null);

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
    // Edge cases
    // =================================================================================================================

    @Test
    void attachment_emptyContent() {
        var attachment = new Attachment(new byte[0], TEST_PNG, "empty.png", null);

        assertEquals(0, attachment.content().length);
        assertNotNull(attachment.toBase64());
        assertNotNull(attachment.toDataUri());
    }
}
