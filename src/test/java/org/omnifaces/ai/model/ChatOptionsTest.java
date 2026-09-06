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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;

import jakarta.json.Json;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.mime.MimeType;
import org.omnifaces.ai.model.ChatInput.Message;
import org.omnifaces.ai.model.ChatInput.Message.Role;
import org.omnifaces.ai.model.ChatInput.UploadedFile;
import org.omnifaces.ai.model.ChatOptions.ReasoningEffort;

class ChatOptionsTest {

    // =================================================================================================================
    // Preset instances tests
    // =================================================================================================================

    @Test
    void preset_default() {
        var options = ChatOptions.DEFAULT;

        assertNotNull(options);
        assertNull(options.getSystemPrompt());
        assertNull(options.getJsonSchema());
        assertEquals(ChatOptions.DEFAULT_TEMPERATURE, options.getTemperature());
        assertNull(options.getMaxTokens());
        assertEquals(ReasoningEffort.AUTO, options.getReasoningEffort());
        assertEquals(ChatOptions.DEFAULT_TOP_P, options.getTopP());
    }

    @Test
    void preset_creative_hasHigherTemperature() {
        assertEquals(ChatOptions.CREATIVE_TEMPERATURE, ChatOptions.CREATIVE.getTemperature());
    }

    @Test
    void preset_deterministic_hasZeroTemperature() {
        assertEquals(ChatOptions.DETERMINISTIC_TEMPERATURE, ChatOptions.DETERMINISTIC.getTemperature());
    }

    // =================================================================================================================
    // Builder tests - validation
    // =================================================================================================================

    @Test
    void builder_temperature_atBoundaries() {
        assertEquals(0.0, ChatOptions.newBuilder().temperature(0.0).build().getTemperature());
        assertEquals(2.0, ChatOptions.newBuilder().temperature(2.0).build().getTemperature());
    }

    @Test
    void builder_temperature_belowZero_throwsException() {
        var builder = ChatOptions.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.temperature(-0.1));
        assertEquals("Temperature must be between 0.0 and 2.0", exception.getMessage());
    }

    @Test
    void builder_temperature_aboveMax_throwsException() {
        var builder = ChatOptions.newBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.temperature(2.1));
    }

    @Test
    void builder_maxTokens_positive() {
        assertEquals(1, ChatOptions.newBuilder().maxTokens(1).build().getMaxTokens());
        assertEquals(500, ChatOptions.newBuilder().maxTokens(500).build().getMaxTokens());
    }

    @Test
    void builder_maxTokens_zero_throwsException() {
        var builder = ChatOptions.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.maxTokens(0));
        assertEquals("Max tokens must be positive", exception.getMessage());
    }

    @Test
    void builder_maxTokens_negative_throwsException() {
        var builder = ChatOptions.newBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.maxTokens(-1));
    }

    @Test
    void builder_topP_atBoundaries() {
        assertEquals(0.0, ChatOptions.newBuilder().topP(0.0).build().getTopP());
        assertEquals(1.0, ChatOptions.newBuilder().topP(1.0).build().getTopP());
    }

    @Test
    void builder_topP_belowZero_throwsException() {
        var builder = ChatOptions.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.topP(-0.1));
        assertEquals("Top-P must be between 0.0 and 1.0", exception.getMessage());
    }

    @Test
    void builder_topP_aboveOne_throwsException() {
        var builder = ChatOptions.newBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.topP(1.1));
    }

    // =================================================================================================================
    // Builder tests - chaining
    // =================================================================================================================

    @Test
    void builder_chaining_allOptions() {
        var schema = Json.createObjectBuilder().add("type", "object").build();

        var options = ChatOptions.newBuilder()
            .systemPrompt("You are helpful.")
            .jsonSchema(schema)
            .temperature(0.5)
            .maxTokens(1000)
            .topP(0.8)
            .build();

        assertEquals("You are helpful.", options.getSystemPrompt());
        assertEquals(schema, options.getJsonSchema());
        assertEquals(0.5, options.getTemperature());
        assertEquals(1000, options.getMaxTokens());
        assertEquals(0.8, options.getTopP());
    }

    // =================================================================================================================
    // Persistent chat tests
    // =================================================================================================================

    @Test
    void preset_default_isNotPersistent() {
        assertFalse(ChatOptions.DEFAULT.hasMemory());
        assertFalse(ChatOptions.CREATIVE.hasMemory());
        assertFalse(ChatOptions.DETERMINISTIC.hasMemory());
    }

    @Test
    void builder_withMemory() {
        var options = ChatOptions.newBuilder().withMemory().build();

        assertTrue(options.hasMemory());
        assertTrue(options.getHistory().isEmpty());
    }

    @Test
    void builder_nonMemory_getHistory_throwsException() {
        var options = ChatOptions.newBuilder().build();

        assertThrows(IllegalStateException.class, options::getHistory);
    }

    @Test
    void builder_nonMemory_recordMessage_throwsException() {
        var options = ChatOptions.newBuilder().build();

        assertThrows(IllegalStateException.class, () -> options.recordMessage(Role.USER, "test"));
    }

    @Test
    void withMemory_recordMessage_updatesHistory() {
        var options = ChatOptions.newBuilder().withMemory().build();

        options.recordMessage(Role.USER, "Hello");
        options.recordMessage(Role.ASSISTANT, "Hi there");

        var history = options.getHistory();
        assertEquals(2, history.size());
        assertEquals(Role.USER, history.get(0).role());
        assertEquals("Hello", history.get(0).content());
        assertEquals(Role.ASSISTANT, history.get(1).role());
        assertEquals("Hi there", history.get(1).content());
    }

    @Test
    void withMemory_getHistory_isImmutable() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "test");

        var history = options.getHistory();

        assertThrows(UnsupportedOperationException.class, history::clear);
    }

    @Test
    void builder_nonMemory_getMaxHistory_throwsException() {
        var options = ChatOptions.newBuilder().build();

        assertThrows(IllegalStateException.class, options::getMaxHistory);
    }

    @Test
    void withMemory_defaultMaxHistory() {
        var options = ChatOptions.newBuilder().withMemory().build();

        assertEquals(ChatOptions.DEFAULT_MAX_HISTORY, options.getMaxHistory());
    }

    @Test
    void withMemory_customMaxHistory() {
        var options = ChatOptions.newBuilder().withMemory(10).build();

        assertTrue(options.hasMemory());
        assertEquals(10, options.getMaxHistory());
    }

    @Test
    void withMemory_customMaxHistory_zero_throwsException() {
        var builder = ChatOptions.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.withMemory(0));
        assertEquals("Max history must be positive", exception.getMessage());
    }

    @Test
    void withMemory_customMaxHistory_negative_throwsException() {
        var builder = ChatOptions.newBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.withMemory(-1));
    }

    @Test
    void withMemory_slidingWindow_dropsOldestMessages() {
        var options = ChatOptions.newBuilder().withMemory(4).build();

        options.recordMessage(Role.USER, "msg1");
        options.recordMessage(Role.ASSISTANT, "reply1");
        options.recordMessage(Role.USER, "msg2");
        options.recordMessage(Role.ASSISTANT, "reply2");

        assertEquals(4, options.getHistory().size());

        options.recordMessage(Role.USER, "msg3");
        options.recordMessage(Role.ASSISTANT, "reply3");

        var history = options.getHistory();
        assertEquals(4, history.size());
        assertEquals("msg2", history.get(0).content());
        assertEquals("reply2", history.get(1).content());
        assertEquals("msg3", history.get(2).content());
        assertEquals("reply3", history.get(3).content());
    }

    // =================================================================================================================
    // Builder history tests
    // =================================================================================================================

    @Test
    void builder_history_restoresMessages() {
        var saved = List.of(
            new Message(Role.USER, "Hello", emptyList()),
            new Message(Role.ASSISTANT, "Hi there", emptyList()),
            new Message(Role.USER, "How are you?", emptyList())
        );

        var options = ChatOptions.newBuilder().withMemory().history(saved).build();

        assertTrue(options.hasMemory());
        var history = options.getHistory();
        assertEquals(3, history.size());
        assertEquals(Role.USER, history.get(0).role());
        assertEquals("Hello", history.get(0).content());
        assertEquals(Role.ASSISTANT, history.get(1).role());
        assertEquals("Hi there", history.get(1).content());
        assertEquals(Role.USER, history.get(2).role());
        assertEquals("How are you?", history.get(2).content());
    }

    @Test
    void builder_history_implicitlyEnablesMemory() {
        var saved = List.of(
            new Message(Role.USER, "Hello", emptyList()),
            new Message(Role.ASSISTANT, "Hi", emptyList())
        );

        var options = ChatOptions.newBuilder().history(saved).build();

        assertTrue(options.hasMemory());
        assertEquals(ChatOptions.DEFAULT_MAX_HISTORY, options.getMaxHistory());
        assertEquals(2, options.getHistory().size());
    }

    @Test
    void builder_history_respectsSlidingWindow() {
        var saved = List.of(
            new Message(Role.USER, "msg1", emptyList()),
            new Message(Role.ASSISTANT, "reply1", emptyList()),
            new Message(Role.USER, "msg2", emptyList()),
            new Message(Role.ASSISTANT, "reply2", emptyList()),
            new Message(Role.USER, "msg3", emptyList()),
            new Message(Role.ASSISTANT, "reply3", emptyList())
        );

        var options = ChatOptions.newBuilder().withMemory(4).history(saved).build();

        var history = options.getHistory();
        assertEquals(4, history.size());
        assertEquals("msg2", history.get(0).content());
        assertEquals("reply2", history.get(1).content());
        assertEquals("msg3", history.get(2).content());
        assertEquals("reply3", history.get(3).content());
    }

    @Test
    void builder_history_restoresUploadedFiles() {
        var saved = List.of(
            new Message(Role.USER, "Analyze this", List.of(new UploadedFile("file-1", TEST_PDF, null))),
            new Message(Role.ASSISTANT, "It contains data", emptyList()),
            new Message(Role.USER, "And this", List.of(new UploadedFile("file-2", TEST_PNG, null), new UploadedFile("file-3", TEST_PDF, null))),
            new Message(Role.ASSISTANT, "Got it", emptyList())
        );

        var options = ChatOptions.newBuilder().withMemory().history(saved).build();

        var history = options.getHistory();
        assertEquals(4, history.size());
        assertEquals(1, history.get(0).uploadedFiles().size());
        assertEquals("file-1", history.get(0).uploadedFiles().get(0).id());
        assertTrue(history.get(1).uploadedFiles().isEmpty());
        assertEquals(2, history.get(2).uploadedFiles().size());
        assertEquals("file-2", history.get(2).uploadedFiles().get(0).id());
        assertEquals("file-3", history.get(2).uploadedFiles().get(1).id());
        assertTrue(history.get(3).uploadedFiles().isEmpty());
    }

    @Test
    void builder_history_slidingWindowCleansUpUploadedFiles() {
        var saved = List.of(
            new Message(Role.USER, "msg1", List.of(new UploadedFile("file-1", TEST_PDF, null))),
            new Message(Role.ASSISTANT, "reply1", emptyList()),
            new Message(Role.USER, "msg2", List.of(new UploadedFile("file-2", TEST_PNG, null))),
            new Message(Role.ASSISTANT, "reply2", emptyList())
        );

        var options = ChatOptions.newBuilder().withMemory(2).history(saved).build();

        var history = options.getHistory();
        assertEquals(2, history.size());
        assertEquals("msg2", history.get(0).content());
        assertEquals(1, history.get(0).uploadedFiles().size());
        assertEquals("file-2", history.get(0).uploadedFiles().get(0).id());
        assertEquals("reply2", history.get(1).content());
    }

    @Test
    void builder_history_continuingConversationAfterRestore() {
        var saved = List.of(
            new Message(Role.USER, "Hello", emptyList()),
            new Message(Role.ASSISTANT, "Hi there", emptyList())
        );

        var options = ChatOptions.newBuilder().withMemory().history(saved).build();
        options.recordMessage(Role.USER, "Follow up");
        options.recordMessage(Role.ASSISTANT, "Sure");

        var history = options.getHistory();
        assertEquals(4, history.size());
        assertEquals("Hello", history.get(0).content());
        assertEquals("Hi there", history.get(1).content());
        assertEquals("Follow up", history.get(2).content());
        assertEquals("Sure", history.get(3).content());
    }

    @Test
    void builder_history_null_throwsException() {
        var builder = ChatOptions.newBuilder().withMemory();

        assertThrows(NullPointerException.class, () -> builder.history(null));
    }

    @Test
    void builder_history_emptyList() {
        var options = ChatOptions.newBuilder().withMemory().history(emptyList()).build();

        assertTrue(options.hasMemory());
        assertTrue(options.getHistory().isEmpty());
    }

    // =================================================================================================================
    // withJsonSchema tests
    // =================================================================================================================

    @Test
    void withJsonSchema_copiesAllFields() {
        var original = ChatOptions.newBuilder()
            .systemPrompt("Test prompt")
            .temperature(0.5)
            .maxTokens(500)
            .topP(0.8)
            .build();

        var schema = Json.createObjectBuilder().add("type", "object").build();
        var copy = original.withJsonSchema(schema);

        assertEquals("Test prompt", copy.getSystemPrompt());
        assertEquals(schema, copy.getJsonSchema());
        assertEquals(0.5, copy.getTemperature());
        assertEquals(500, copy.getMaxTokens());
        assertEquals(0.8, copy.getTopP());
    }

    @Test
    void withJsonSchema_setsNewSchema_originalUnchanged() {
        var original = ChatOptions.newBuilder().build();
        var schema = Json.createObjectBuilder().add("type", "object").build();

        var copy = original.withJsonSchema(schema);

        assertNull(original.getJsonSchema());
        assertEquals(schema, copy.getJsonSchema());
    }

    @Test
    void withJsonSchema_sharesMemory() {
        var original = ChatOptions.newBuilder().withMemory().build();
        var schema = Json.createObjectBuilder().add("type", "object").build();
        var copy = original.withJsonSchema(schema);

        assertTrue(copy.hasMemory());
        copy.recordMessage(Role.USER, "Hello");

        assertEquals(1, original.getHistory().size());
        assertEquals("Hello", original.getHistory().get(0).content());
    }

    // =================================================================================================================
    // Serialization tests
    // =================================================================================================================

    @Test
    void implementsSerializable() {
        assertTrue(Serializable.class.isAssignableFrom(ChatOptions.class));
    }

    @Test
    void serialization_preservesAllFields() throws Exception {
        var schema = Json.createObjectBuilder().add("type", "object").build();

        var original = ChatOptions.newBuilder()
            .systemPrompt("Test prompt")
            .jsonSchema(schema)
            .temperature(0.8)
            .maxTokens(500)
            .topP(0.7)
            .build();

        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        var bais = new ByteArrayInputStream(baos.toByteArray());
        try (var ois = new ObjectInputStream(bais)) {
            var deserialized = (ChatOptions) ois.readObject();

            assertEquals(original.getSystemPrompt(), deserialized.getSystemPrompt());
            assertNotNull(deserialized.getJsonSchema());
            assertEquals(original.getJsonSchema().toString(), deserialized.getJsonSchema().toString());
            assertEquals(original.getTemperature(), deserialized.getTemperature());
            assertEquals(original.getMaxTokens(), deserialized.getMaxTokens());
            assertEquals(original.getTopP(), deserialized.getTopP());
        }
    }

    @Test
    void serialization_withNullJsonSchema() throws Exception {
        var original = ChatOptions.newBuilder().systemPrompt("Test").build();

        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        var bais = new ByteArrayInputStream(baos.toByteArray());
        try (var ois = new ObjectInputStream(bais)) {
            var deserialized = (ChatOptions) ois.readObject();

            assertNull(deserialized.getJsonSchema());
        }
    }

    // =================================================================================================================
    // Message record tests
    // =================================================================================================================

    @Test
    void message_validConstruction() {
        var message = new Message(Role.USER, "Hello", emptyList());

        assertEquals(Role.USER, message.role());
        assertEquals("Hello", message.content());
    }

    @Test
    void message_assistantRole() {
        var message = new Message(Role.ASSISTANT, "Hi there", emptyList());

        assertEquals(Role.ASSISTANT, message.role());
        assertEquals("Hi there", message.content());
    }

    @Test
    void message_nullRole_throwsException() {
        assertThrows(NullPointerException.class, () -> new Message(null, "Hello", emptyList()));
    }

    @Test
    void message_nullContent_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new Message(Role.USER, null, emptyList()));
    }

    @Test
    void message_blankContent_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new Message(Role.USER, "", emptyList()));
        assertThrows(IllegalArgumentException.class, () -> new Message(Role.USER, "   ", emptyList()));
        assertThrows(IllegalArgumentException.class, () -> new Message(Role.USER, "\t\n", emptyList()));
    }

    @Test
    void message_implementsSerializable() {
        assertTrue(Serializable.class.isAssignableFrom(Message.class));
    }

    // =================================================================================================================
    // UploadedFile record tests
    // =================================================================================================================

    private static final MimeType TEST_MP4 = MimeType.of("video/mp4");

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

    @Test
    void uploadedFile_validConstruction() {
        var uploadedFile = new UploadedFile("file-123", TEST_PDF, null);

        assertEquals("file-123", uploadedFile.id());
        assertEquals(TEST_PDF, uploadedFile.mimeType());
    }

    @Test
    void uploadedFile_nullId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new UploadedFile(null, TEST_PDF, null));
    }

    @Test
    void uploadedFile_blankId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new UploadedFile("", TEST_PDF, null));
        assertThrows(IllegalArgumentException.class, () -> new UploadedFile("   ", TEST_PDF, null));
    }

    @Test
    void uploadedFile_nullMimeType_throwsException() {
        assertThrows(NullPointerException.class, () -> new UploadedFile("file-123", null, null));
    }

    @Test
    void uploadedFile_implementsSerializable() {
        assertTrue(Serializable.class.isAssignableFrom(UploadedFile.class));
    }

    // =================================================================================================================
    // getHistory - returns full history
    // =================================================================================================================

    @Test
    void getHistory_returnsAllRecordedMessages() {
        var options = ChatOptions.newBuilder().withMemory().build();

        options.recordMessage(Role.USER, "msg1");
        options.recordMessage(Role.ASSISTANT, "reply1");
        options.recordMessage(Role.USER, "msg2");

        var history = options.getHistory();
        assertEquals(3, history.size());
        assertEquals(Role.USER, history.get(0).role());
        assertEquals("msg1", history.get(0).content());
        assertEquals(Role.ASSISTANT, history.get(1).role());
        assertEquals("reply1", history.get(1).content());
        assertEquals(Role.USER, history.get(2).role());
        assertEquals("msg2", history.get(2).content());
    }

    // =================================================================================================================
    // recordUploadedFile tests
    // =================================================================================================================

    @Test
    void recordUploadedFile_associatesWithLastUserMessage() {
        var options = ChatOptions.newBuilder().withMemory().build();

        options.recordMessage(Role.USER, "Analyze this file");
        options.recordUploadedFile(new UploadedFile("file-123", TEST_PDF));

        var history = options.getHistory();
        assertEquals(1, history.size());
        var uploadedFiles = history.get(0).uploadedFiles();
        assertEquals(1, uploadedFiles.size());
        assertEquals("file-123", uploadedFiles.get(0).id());
        assertEquals(TEST_PDF, uploadedFiles.get(0).mimeType());
    }

    @Test
    void recordUploadedFile_multipleFilesOnSameMessage() {
        var options = ChatOptions.newBuilder().withMemory().build();

        options.recordMessage(Role.USER, "Analyze these files");
        options.recordUploadedFile(new UploadedFile("file-1", TEST_PDF));
        options.recordUploadedFile(new UploadedFile("file-2", TEST_PNG));

        var uploadedFiles = options.getHistory().get(0).uploadedFiles();
        assertEquals(2, uploadedFiles.size());
        assertEquals("file-1", uploadedFiles.get(0).id());
        assertEquals("file-2", uploadedFiles.get(1).id());
    }

    @Test
    @SuppressWarnings("removal")
    void recordUploadedFile_fileIdAndMimeType_recordsAFileWithoutOptions() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "Describe it");
        options.recordUploadedFile("file-1", TEST_PDF);

        var uploadedFile = options.getHistory().get(0).uploadedFiles().get(0);
        assertEquals("file-1", uploadedFile.id());
        assertEquals(TEST_PDF, uploadedFile.mimeType());
        assertNull(uploadedFile.videoOptions());
    }

    @Test
    void recordUploadedFile_keepsTheVideoOptionsForReplay() {
        var options = ChatOptions.newBuilder().withMemory().build();
        var videoOptions = AnalyzeVideoOptions.newBuilder().fps(2).startOffset(Duration.ofSeconds(30)).build();
        options.recordMessage(Role.USER, "Describe it");
        options.recordUploadedFile(new UploadedFile("file-1", TEST_MP4, videoOptions));

        assertEquals(videoOptions, options.getHistory().get(0).uploadedFiles().get(0).videoOptions());
    }

    @Test
    void toJson_roundTripsTheVideoOptionsOfAnUploadedFile() {
        var options = ChatOptions.newBuilder().withMemory().build();
        var videoOptions = AnalyzeVideoOptions.newBuilder().fps(0.5).startOffset(Duration.ofSeconds(30)).endOffset(Duration.ofSeconds(90)).build();
        options.recordMessage(Role.USER, "Describe it");
        options.recordUploadedFile(new UploadedFile("file-1", TEST_MP4, videoOptions));

        var restored = ChatOptions.fromJson(options.toJson()).getHistory().get(0).uploadedFiles().get(0).videoOptions();

        assertEquals(videoOptions.getFps(), restored.getFps());
        assertEquals(videoOptions.getStartOffset(), restored.getStartOffset());
        assertEquals(videoOptions.getEndOffset(), restored.getEndOffset());
    }

    @Test
    void recordUploadedFile_findsLastUserMessageAfterAssistant() {
        var options = ChatOptions.newBuilder().withMemory().build();

        options.recordMessage(Role.USER, "First message");
        options.recordMessage(Role.ASSISTANT, "Reply");
        options.recordMessage(Role.USER, "Second message");
        options.recordUploadedFile(new UploadedFile("file-123", TEST_PDF));

        var history = options.getHistory();
        assertTrue(history.get(0).uploadedFiles().isEmpty());
        assertEquals(1, history.get(2).uploadedFiles().size());
    }

    @Test
    void recordUploadedFile_noUserMessage_throwsException() {
        var options = ChatOptions.newBuilder().withMemory().build();
        var uploadedFile = new UploadedFile("file-123", TEST_PDF);

        assertThrows(IllegalStateException.class, () -> options.recordUploadedFile(uploadedFile));
    }

    @Test
    void recordUploadedFile_nonMemory_throwsException() {
        var options = ChatOptions.newBuilder().build();
        var uploadedFile = new UploadedFile("file-123", TEST_PDF);

        assertThrows(IllegalStateException.class, () -> options.recordUploadedFile(uploadedFile));
    }

    @Test
    void recordUploadedFile_duplicateMessageContent_associatesWithCorrectMessage() {
        var options = ChatOptions.newBuilder().withMemory().build();

        options.recordMessage(Role.USER, "yes");
        options.recordMessage(Role.ASSISTANT, "Got it");
        options.recordMessage(Role.USER, "yes");
        options.recordUploadedFile(new UploadedFile("file-1", TEST_PDF));

        var history = options.getHistory();
        assertTrue(history.get(0).uploadedFiles().isEmpty());
        assertEquals(1, history.get(2).uploadedFiles().size());
        assertEquals("file-1", history.get(2).uploadedFiles().get(0).id());
    }

    // =================================================================================================================
    // recordMessage / discardPendingUserMessage
    // =================================================================================================================

    @Test
    void recordMessage_identicalConsecutiveUserMessages_appendsBoth() {
        var options = ChatOptions.newBuilder().withMemory().build();

        options.recordMessage(Role.USER, "yes");
        options.recordMessage(Role.USER, "yes");

        assertEquals(2, options.getHistory().size());
    }

    @Test
    void discardPendingUserMessage_trailingUserMessage_removesIt() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "yes");

        options.discardPendingUserMessage();

        assertTrue(options.getHistory().isEmpty());
    }

    @Test
    void discardPendingUserMessage_trailingAssistantMessage_isNoOp() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "yes");
        options.recordMessage(Role.ASSISTANT, "Got it");

        options.discardPendingUserMessage();

        assertEquals(2, options.getHistory().size());
    }

    @Test
    void discardPendingUserMessage_emptyHistory_isNoOp() {
        var options = ChatOptions.newBuilder().withMemory().build();

        options.discardPendingUserMessage();

        assertTrue(options.getHistory().isEmpty());
    }

    @Test
    void discardPendingUserMessage_nonMemoryOptions_throwsException() {
        var options = ChatOptions.newBuilder().build();

        assertThrows(IllegalStateException.class, options::discardPendingUserMessage);
    }

    // =================================================================================================================
    // getHistory - uploaded files inline
    // =================================================================================================================

    @Test
    void getHistory_noFilesForMessage_returnsEmptyUploadedFiles() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "No files here");

        assertTrue(options.getHistory().get(0).uploadedFiles().isEmpty());
    }

    // =================================================================================================================
    // recordUsage / getLastUsage
    // =================================================================================================================

    @Test
    void getLastUsage_initial_returnsNull() {
        var options = ChatOptions.newBuilder().build();

        assertNull(options.getLastUsage());
    }

    @Test
    void recordUsage_validUsage_setsLastUsage() {
        var options = ChatOptions.newBuilder().build();
        var usage = new ChatUsage(100, 50, -1, -1);

        options.recordUsage(usage);

        assertEquals(usage, options.getLastUsage());
    }

    @Test
    void recordUsage_null_clearsLastUsage() {
        var options = ChatOptions.newBuilder().build();
        options.recordUsage(new ChatUsage(100, 50, -1, -1));

        options.recordUsage(null);

        assertNull(options.getLastUsage());
    }

    @Test
    void recordUsage_staleScenario_latestCallWins() {
        var options = ChatOptions.newBuilder().build();
        var first = new ChatUsage(100, 50, -1, -1);
        var second = new ChatUsage(200, 75, -1, -1);

        options.recordUsage(first);
        options.recordUsage(second);

        assertEquals(second, options.getLastUsage());
    }

    // =================================================================================================================
    // isDefault / copy
    // =================================================================================================================

    @Test
    void isDefault_sharedConstants_returnsTrue() {
        assertTrue(ChatOptions.DEFAULT.isDefault());
        assertTrue(ChatOptions.CREATIVE.isDefault());
        assertTrue(ChatOptions.DETERMINISTIC.isDefault());
    }

    @Test
    void isDefault_builderInstance_returnsFalse() {
        assertFalse(ChatOptions.newBuilder().build().isDefault());
    }

    @Test
    void isDefault_withXxxCopy_returnsFalse() {
        assertFalse(ChatOptions.DEFAULT.withSystemPrompt("test").isDefault());
    }

    @Test
    void recordUsage_onDefault_throwsISE() {
        var usage = new ChatUsage(1, 1, -1, -1);

        assertThrows(IllegalStateException.class, () -> ChatOptions.DEFAULT.recordUsage(usage));
    }

    @Test
    void getLastUsage_onDefault_throwsISE() {
        assertThrows(IllegalStateException.class, ChatOptions.DEFAULT::getLastUsage);
        assertThrows(IllegalStateException.class, ChatOptions.CREATIVE::getLastUsage);
        assertThrows(IllegalStateException.class, ChatOptions.DETERMINISTIC::getLastUsage);
    }

    @Test
    void copy_preservesSettings() {
        var copy = ChatOptions.DEFAULT.copy();

        assertFalse(copy.isDefault());
        assertEquals(ChatOptions.DEFAULT_TEMPERATURE, copy.getTemperature());
        assertNull(copy.getSystemPrompt());
        assertNull(copy.getLastUsage());
    }

    @Test
    void copy_isMutable() {
        var copy = ChatOptions.DEFAULT.copy();
        var usage = new ChatUsage(100, 50, -1, -1);

        copy.recordUsage(usage);

        assertEquals(usage, copy.getLastUsage());
    }

    @Test
    void copy_ofCreative_preservesTemperature() {
        var copy = ChatOptions.CREATIVE.copy();

        assertFalse(copy.isDefault());
        assertEquals(ChatOptions.CREATIVE_TEMPERATURE, copy.getTemperature());
    }

    // =================================================================================================================
    // Web search - Location
    // =================================================================================================================

    @Test
    void location_global_isGlobal() {
        assertTrue(ChatOptions.Location.GLOBAL.isGlobal());
    }

    @Test
    void location_allNulls_isGlobal() {
        assertTrue(new ChatOptions.Location(null, null, null).isGlobal());
    }

    @Test
    void location_withCountry_isNotGlobal() {
        assertFalse(new ChatOptions.Location("US", null, null).isGlobal());
    }

    @Test
    void location_withRegion_isNotGlobal() {
        assertFalse(new ChatOptions.Location(null, "Florida", null).isGlobal());
    }

    @Test
    void location_withCity_isNotGlobal() {
        assertFalse(new ChatOptions.Location(null, null, "Miami").isGlobal());
    }

    @Test
    void location_accessors() {
        var location = new ChatOptions.Location("US", "Florida", "Miami");
        assertEquals("US", location.country());
        assertEquals("Florida", location.region());
        assertEquals("Miami", location.city());
    }

    // =================================================================================================================
    // Web search - Location#toString
    // =================================================================================================================

    @Test
    void location_toString_global_returnsGlobal() {
        assertEquals("global", ChatOptions.Location.GLOBAL.toString());
    }

    @Test
    void location_toString_allFieldsSet_returnsCityRegionCountry() {
        assertEquals("Miami, Florida, US", new ChatOptions.Location("US", "Florida", "Miami").toString());
    }

    @Test
    void location_toString_cityAndCountryOnly_omitsNullRegion() {
        assertEquals("Miami, US", new ChatOptions.Location("US", null, "Miami").toString());
    }

    @Test
    void location_toString_countryOnly_returnsCountry() {
        assertEquals("US", new ChatOptions.Location("US", null, null).toString());
    }

    // =================================================================================================================
    // Web search - Builder and useWebSearch / getWebSearchLocation
    // =================================================================================================================

    @Test
    void useWebSearch_notConfigured_returnsFalse() {
        assertFalse(ChatOptions.newBuilder().build().useWebSearch());
    }

    @Test
    void getWebSearchLocation_notConfigured_returnsNull() {
        assertNull(ChatOptions.newBuilder().build().getWebSearchLocation());
    }

    @Test
    void webSearch_builderGlobal_enablesWebSearch() {
        var options = ChatOptions.newBuilder().webSearch().build();
        assertTrue(options.useWebSearch());
        assertEquals(ChatOptions.Location.GLOBAL, options.getWebSearchLocation());
    }

    @Test
    void webSearch_builderWithLocation_enablesLocalized() {
        var miami = new ChatOptions.Location("US", "Florida", "Miami");
        var options = ChatOptions.newBuilder().webSearch(miami).build();
        assertTrue(options.useWebSearch());
        assertEquals(miami, options.getWebSearchLocation());
    }

    @Test
    void webSearch_builderWithNullLocation_throwsNPE() {
        var builder = ChatOptions.newBuilder();

        assertThrows(NullPointerException.class, () -> builder.webSearch(null));
    }

    @Test
    void withWebSearch_enablesWebSearch() {
        var miami = new ChatOptions.Location("US", "Florida", "Miami");
        var options = ChatOptions.DEFAULT.withWebSearch(miami);
        assertTrue(options.useWebSearch());
        assertEquals(miami, options.getWebSearchLocation());
    }

    @Test
    void withWebSearch_null_disablesWebSearch() {
        var options = ChatOptions.newBuilder().webSearch().build().withWebSearch(null);
        assertFalse(options.useWebSearch());
        assertNull(options.getWebSearchLocation());
    }

    @Test
    void withWebSearch_preservesOtherSettings() {
        var options = ChatOptions.newBuilder().systemPrompt("test").temperature(0.7).build();
        var withSearch = options.withWebSearch(ChatOptions.Location.GLOBAL);
        assertEquals("test", withSearch.getSystemPrompt());
        assertEquals(0.7, withSearch.getTemperature());
    }

    // =================================================================================================================
    // Reasoning effort
    // =================================================================================================================

    @Test
    void reasoningEffort_default_isAuto() {
        assertEquals(ReasoningEffort.AUTO, ChatOptions.newBuilder().build().getReasoningEffort());
    }

    @Test
    void reasoningEffort_builder_roundTripsAllValues() {
        for (var effort : ReasoningEffort.values()) {
            assertEquals(effort, ChatOptions.newBuilder().reasoningEffort(effort).build().getReasoningEffort());
        }
    }

    @Test
    void reasoningEffort_builder_null_throwsNPE() {
        var builder = ChatOptions.newBuilder();
        assertThrows(NullPointerException.class, () -> builder.reasoningEffort(null));
    }

    @Test
    void withReasoningEffort_preservesOtherSettings() {
        var options = ChatOptions.newBuilder()
            .systemPrompt("test")
            .temperature(0.3)
            .maxTokens(500)
            .topP(0.8)
            .build();

        var withEffort = options.withReasoningEffort(ReasoningEffort.HIGH);

        assertEquals(ReasoningEffort.HIGH, withEffort.getReasoningEffort());
        assertEquals("test", withEffort.getSystemPrompt());
        assertEquals(0.3, withEffort.getTemperature());
        assertEquals(500, withEffort.getMaxTokens());
        assertEquals(0.8, withEffort.getTopP());
        assertEquals(ReasoningEffort.AUTO, options.getReasoningEffort()); // original unchanged
    }

    @Test
    void withReasoningEffort_null_throwsNPE() {
        var options = ChatOptions.newBuilder().build();
        assertThrows(NullPointerException.class, () -> options.withReasoningEffort(null));
    }

    @Test
    void copy_preservesReasoningEffort() {
        var original = ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.HIGH).build();
        assertEquals(ReasoningEffort.HIGH, original.copy().getReasoningEffort());
    }

    @Test
    void withJsonSchema_preservesReasoningEffort() {
        var original = ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.MEDIUM).build();
        var schema = Json.createObjectBuilder().add("type", "object").build();
        assertEquals(ReasoningEffort.MEDIUM, original.withJsonSchema(schema).getReasoningEffort());
    }

    @Test
    void withSystemPrompt_preservesReasoningEffort() {
        var original = ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.LOW).build();
        assertEquals(ReasoningEffort.LOW, original.withSystemPrompt("hello").getReasoningEffort());
    }

    @Test
    void withWebSearch_preservesReasoningEffort() {
        var original = ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.NONE).build();
        assertEquals(ReasoningEffort.NONE, original.withWebSearch(ChatOptions.Location.GLOBAL).getReasoningEffort());
    }

    @Test
    void reasoningEffort_serialization_roundTrips() throws Exception {
        var original = ChatOptions.newBuilder().reasoningEffort(ReasoningEffort.HIGH).build();

        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        try (var ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            var deserialized = (ChatOptions) ois.readObject();
            assertEquals(ReasoningEffort.HIGH, deserialized.getReasoningEffort());
        }
    }

    // =================================================================================================================
    // copy_preservesWebSearchLocation
    // =================================================================================================================

    @Test
    void copy_preservesWebSearchLocation() {
        var miami = new ChatOptions.Location("US", "Florida", "Miami");
        var original = ChatOptions.newBuilder().webSearch(miami).build();
        var copy = original.copy();
        assertTrue(copy.useWebSearch());
        assertEquals(miami, copy.getWebSearchLocation());
    }

    // =================================================================================================================
    // Sliding window - uploaded file cleanup
    // =================================================================================================================

    @Test
    void slidingWindow_removesUploadedFilesWithEvictedMessages() {
        var options = ChatOptions.newBuilder().withMemory(4).build();

        options.recordMessage(Role.USER, "msg1");
        options.recordUploadedFile(new UploadedFile("file-1", TEST_PDF));
        options.recordMessage(Role.ASSISTANT, "reply1");
        options.recordMessage(Role.USER, "msg2");
        options.recordUploadedFile(new UploadedFile("file-2", TEST_PNG));
        options.recordMessage(Role.ASSISTANT, "reply2");

        // History is now full (4 messages). Verify files are accessible.
        var history = options.getHistory();
        assertEquals(1, history.get(0).uploadedFiles().size());
        assertEquals(1, history.get(2).uploadedFiles().size());

        // Add two more messages, causing msg1 and reply1 to be evicted.
        options.recordMessage(Role.USER, "msg3");
        options.recordMessage(Role.ASSISTANT, "reply3");

        // msg1's uploaded file should be cleaned up; msg2's should remain.
        history = options.getHistory();
        assertEquals(4, history.size());
        assertEquals("msg2", history.get(0).content());
        assertEquals(1, history.get(0).uploadedFiles().size());
        assertEquals("file-2", history.get(0).uploadedFiles().get(0).id());
        assertTrue(history.get(2).uploadedFiles().isEmpty()); // msg3 has no files
    }

    // =================================================================================================================
    // JSON export/import
    // =================================================================================================================

    @Test
    void toJson_default_isValidJsonObject() {
        var json = ChatOptions.DEFAULT.toJson();
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
    }

    @Test
    void toJson_omitsNullFields() {
        var json = ChatOptions.DEFAULT.toJson();
        assertFalse(json.contains("systemPrompt"));
        assertFalse(json.contains("jsonSchema"));
        assertFalse(json.contains("maxTokens"));
        assertFalse(json.contains("webSearchLocation"));
        assertFalse(json.contains("history"));
    }

    @Test
    void toJson_includesAlwaysPresentFields() {
        var json = ChatOptions.DEFAULT.toJson();
        assertTrue(json.contains("temperature"));
        assertTrue(json.contains("topP"));
        assertTrue(json.contains("reasoningEffort"));
    }

    @Test
    void jsonRoundTrip_allScalarFields() {
        var schema = Json.createObjectBuilder().add("type", "object").build();
        var original = ChatOptions.newBuilder()
            .systemPrompt("You are helpful.")
            .jsonSchema(schema)
            .temperature(0.4)
            .maxTokens(1234)
            .reasoningEffort(ReasoningEffort.HIGH)
            .topP(0.9)
            .build();

        var restored = ChatOptions.fromJson(original.toJson());

        assertEquals(original.getSystemPrompt(), restored.getSystemPrompt());
        assertEquals(original.getJsonSchema().toString(), restored.getJsonSchema().toString());
        assertEquals(original.getTemperature(), restored.getTemperature());
        assertEquals(original.getMaxTokens(), restored.getMaxTokens());
        assertEquals(original.getReasoningEffort(), restored.getReasoningEffort());
        assertEquals(original.getTopP(), restored.getTopP());
    }

    @Test
    void jsonRoundTrip_webSearchGlobal() {
        var original = ChatOptions.newBuilder().webSearch().build();
        var restored = ChatOptions.fromJson(original.toJson());

        assertTrue(restored.useWebSearch());
        assertTrue(restored.getWebSearchLocation().isGlobal());
    }

    @Test
    void jsonRoundTrip_webSearchLocalized() {
        var miami = new ChatOptions.Location("US", "Florida", "Miami");
        var original = ChatOptions.newBuilder().webSearch(miami).build();
        var restored = ChatOptions.fromJson(original.toJson());

        assertTrue(restored.useWebSearch());
        assertEquals(miami, restored.getWebSearchLocation());
    }

    @Test
    void jsonRoundTrip_memoryAndHistory() {
        var original = ChatOptions.newBuilder().withMemory(6).build();
        original.recordMessage(Role.USER, "Hello");
        original.recordMessage(Role.ASSISTANT, "Hi there");
        original.recordMessage(Role.USER, "How are you?");

        var restored = ChatOptions.fromJson(original.toJson());

        assertTrue(restored.hasMemory());
        assertEquals(6, restored.getMaxHistory());
        var history = restored.getHistory();
        assertEquals(3, history.size());
        assertEquals(Role.USER, history.get(0).role());
        assertEquals("Hello", history.get(0).content());
        assertEquals(Role.ASSISTANT, history.get(1).role());
        assertEquals("Hi there", history.get(1).content());
        assertEquals("How are you?", history.get(2).content());
    }

    @Test
    void jsonRoundTrip_uploadedFiles() {
        var original = ChatOptions.newBuilder().withMemory().build();
        original.recordMessage(Role.USER, "Analyze these");
        original.recordUploadedFile(new UploadedFile("file-1", TEST_PDF));
        original.recordUploadedFile(new UploadedFile("file-2", TEST_PNG));
        original.recordMessage(Role.ASSISTANT, "Done");

        var restored = ChatOptions.fromJson(original.toJson());
        var files = restored.getHistory().get(0).uploadedFiles();

        assertEquals(2, files.size());
        assertEquals("file-1", files.get(0).id());
        assertEquals("application/pdf", files.get(0).mimeType().value());
        assertEquals("file-2", files.get(1).id());
        assertEquals("image/png", files.get(1).mimeType().value());
    }

    @Test
    void jsonRoundTrip_uploadedFiles_rehydratesKnownMimeTypeAsEnum() {
        var original = ChatOptions.newBuilder().withMemory().build();
        original.recordMessage(Role.USER, "x");
        original.recordUploadedFile(new UploadedFile("f", TEST_PDF));

        var restored = ChatOptions.fromJson(original.toJson());
        var mimeType = restored.getHistory().get(0).uploadedFiles().get(0).mimeType();

        assertTrue(mimeType.getClass().isEnum(), "Expected known MIME type to rehydrate as enum but was " + mimeType.getClass());
    }

    @Test
    void jsonRoundTrip_uploadedFiles_rehydratesUnknownMimeTypeAsFallback() {
        var jsonWithUnknownMime = "{\"temperature\":0.7,\"reasoningEffort\":\"AUTO\",\"topP\":1.0,\"maxHistory\":20,"
            + "\"history\":[{\"role\":\"USER\",\"content\":\"x\",\"uploadedFiles\":[{\"id\":\"f\",\"mimeType\":\"application/x-custom\"}]}]}";

        var restored = ChatOptions.fromJson(jsonWithUnknownMime);
        var mimeType = restored.getHistory().get(0).uploadedFiles().get(0).mimeType();

        assertEquals("application/x-custom", mimeType.value());
        assertEquals("x-custom", mimeType.extension());
    }

    @Test
    void jsonRoundTrip_emptyHistoryWithMemory() {
        var original = ChatOptions.newBuilder().withMemory(8).build();
        var restored = ChatOptions.fromJson(original.toJson());

        assertTrue(restored.hasMemory());
        assertEquals(8, restored.getMaxHistory());
        assertTrue(restored.getHistory().isEmpty());
    }

    @Test
    void jsonRoundTrip_preservesDefaults() {
        var original = ChatOptions.newBuilder().build();
        var restored = ChatOptions.fromJson(original.toJson());

        assertEquals(original.getTemperature(), restored.getTemperature());
        assertEquals(original.getTopP(), restored.getTopP());
        assertEquals(original.getReasoningEffort(), restored.getReasoningEffort());
        assertNull(restored.getSystemPrompt());
        assertNull(restored.getMaxTokens());
        assertFalse(restored.hasMemory());
        assertFalse(restored.useWebSearch());
    }

    @Test
    void fromJson_sharedDefault_returnsMutableCopy() {
        var restored = ChatOptions.fromJson(ChatOptions.DEFAULT.toJson());

        assertFalse(restored.isDefault());
        restored.recordUsage(new ChatUsage(1, 1, -1, -1));
        assertNotNull(restored.getLastUsage());
    }

    @Test
    void fromJson_emptyObject_usesDefaults() {
        var restored = ChatOptions.fromJson("{}");

        assertEquals(ChatOptions.DEFAULT_TEMPERATURE, restored.getTemperature());
        assertEquals(ChatOptions.DEFAULT_TOP_P, restored.getTopP());
        assertEquals(ReasoningEffort.AUTO, restored.getReasoningEffort());
        assertNull(restored.getSystemPrompt());
        assertFalse(restored.hasMemory());
    }

    @Test
    void fromJson_null_throwsNPE() {
        assertThrows(NullPointerException.class, () -> ChatOptions.fromJson(null));
    }

    @Test
    void fromJson_invalidJson_throwsException() {
        assertThrows(Exception.class, () -> ChatOptions.fromJson("not json"));
    }

    @Test
    void fromJson_invalidReasoningEffort_throwsIAE() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ChatOptions.fromJson("{\"reasoningEffort\":\"TURBO\"}")
        );
    }

    // =================================================================================================================
    // Pricing
    // =================================================================================================================

    @Test
    void pricing_default_isNull() {
        assertNull(ChatOptions.newBuilder().build().getPricing());
    }

    @Test
    void builder_pricing_setsValue() {
        var pricing = ChatPricing.of(new BigDecimal("3.00"), new BigDecimal("15.00"));
        var options = ChatOptions.newBuilder().pricing(pricing).build();

        assertEquals(pricing, options.getPricing());
    }

    @Test
    void builder_pricing_null_clearsPricing() {
        var options = ChatOptions.newBuilder().pricing(null).build();

        assertNull(options.getPricing());
    }

    @Test
    void withPricing_setsNewPricing_originalUnchanged() {
        var pricing = ChatPricing.of(new BigDecimal("3.00"), new BigDecimal("15.00"));
        var original = ChatOptions.newBuilder().build();

        var copy = original.withPricing(pricing);

        assertNull(original.getPricing());
        assertEquals(pricing, copy.getPricing());
    }

    @Test
    void withPricing_null_clearsPricing() {
        var options = ChatOptions.newBuilder().pricing(ChatPricing.of(BigDecimal.ONE, BigDecimal.ONE)).build();

        var cleared = options.withPricing(null);

        assertNull(cleared.getPricing());
    }

    @Test
    void withPricing_preservesOtherSettings() {
        var pricing = ChatPricing.of(new BigDecimal("3.00"), new BigDecimal("15.00"));
        var options = ChatOptions.newBuilder()
            .systemPrompt("test")
            .temperature(0.5)
            .maxTokens(500)
            .reasoningEffort(ReasoningEffort.HIGH)
            .topP(0.8)
            .build();

        var withPricing = options.withPricing(pricing);

        assertEquals("test", withPricing.getSystemPrompt());
        assertEquals(0.5, withPricing.getTemperature());
        assertEquals(500, withPricing.getMaxTokens());
        assertEquals(ReasoningEffort.HIGH, withPricing.getReasoningEffort());
        assertEquals(0.8, withPricing.getTopP());
    }

    @Test
    void withPricing_sharesMemory() {
        var original = ChatOptions.newBuilder().withMemory().build();
        var copy = original.withPricing(ChatPricing.of(BigDecimal.ONE, BigDecimal.ONE));

        assertTrue(copy.hasMemory());
        copy.recordMessage(Role.USER, "Hello");

        assertEquals(1, original.getHistory().size());
    }

    @Test
    void copy_preservesPricing() {
        var pricing = ChatPricing.of(new BigDecimal("3.00"), new BigDecimal("15.00"));
        var original = ChatOptions.newBuilder().pricing(pricing).build();

        assertEquals(pricing, original.copy().getPricing());
    }

    @Test
    void withJsonSchema_preservesPricing() {
        var pricing = ChatPricing.of(new BigDecimal("3.00"), new BigDecimal("15.00"));
        var schema = Json.createObjectBuilder().add("type", "object").build();
        var original = ChatOptions.newBuilder().pricing(pricing).build();

        assertEquals(pricing, original.withJsonSchema(schema).getPricing());
    }

    @Test
    void withSystemPrompt_preservesPricing() {
        var pricing = ChatPricing.of(new BigDecimal("3.00"), new BigDecimal("15.00"));
        var original = ChatOptions.newBuilder().pricing(pricing).build();

        assertEquals(pricing, original.withSystemPrompt("x").getPricing());
    }

    @Test
    void withWebSearch_preservesPricing() {
        var pricing = ChatPricing.of(new BigDecimal("3.00"), new BigDecimal("15.00"));
        var original = ChatOptions.newBuilder().pricing(pricing).build();

        assertEquals(pricing, original.withWebSearch(ChatOptions.Location.GLOBAL).getPricing());
    }

    @Test
    void withReasoningEffort_preservesPricing() {
        var pricing = ChatPricing.of(new BigDecimal("3.00"), new BigDecimal("15.00"));
        var original = ChatOptions.newBuilder().pricing(pricing).build();

        assertEquals(pricing, original.withReasoningEffort(ReasoningEffort.HIGH).getPricing());
    }

    // =================================================================================================================
    // getLastCost
    // =================================================================================================================

    @Test
    void getLastCost_noPricing_returnsNull() {
        var options = ChatOptions.newBuilder().build();
        options.recordUsage(new ChatUsage(100, 50, -1, -1));

        assertNull(options.getLastCost());
    }

    @Test
    void getLastCost_noUsage_returnsNull() {
        var options = ChatOptions.newBuilder().pricing(ChatPricing.of(BigDecimal.ONE, BigDecimal.ONE)).build();

        assertNull(options.getLastCost());
    }

    @Test
    void getLastCost_pricingAndUsageSet_computesCost() {
        var usd = Currency.getInstance("USD");
        var pricing = new ChatPricing(new BigDecimal("3.00"), new BigDecimal("0.30"), new BigDecimal("15.00"), usd);
        var options = ChatOptions.newBuilder().pricing(pricing).build();
        options.recordUsage(new ChatUsage(1_000_000, 2_000_000, -1, 500_000));

        var cost = options.getLastCost();

        assertNotNull(cost);
        assertEquals(usd, cost.currency());
        assertEquals(0, new BigDecimal("31.65").compareTo(cost.totalCost())); // 1.5 + 0.15 + 30
    }

    @Test
    void getLastCost_usageWithoutReportedTokens_returnsNull() {
        var options = ChatOptions.newBuilder().pricing(ChatPricing.of(BigDecimal.ONE, BigDecimal.ONE)).build();
        options.recordUsage(new ChatUsage(-1, -1, -1, -1));

        assertNull(options.getLastCost());
    }

    @Test
    void getLastCost_onDefault_throwsISE() {
        assertThrows(IllegalStateException.class, ChatOptions.DEFAULT::getLastCost);
    }

    // =================================================================================================================
    // Pricing JSON round-trip
    // =================================================================================================================

    @Test
    void jsonRoundTrip_pricingAllFields() {
        var usd = Currency.getInstance("USD");
        var pricing = new ChatPricing(new BigDecimal("3.00"), new BigDecimal("0.30"), new BigDecimal("15.00"), usd);
        var original = ChatOptions.newBuilder().pricing(pricing).build();

        var restored = ChatOptions.fromJson(original.toJson());

        assertNotNull(restored.getPricing());
        assertEquals(0, new BigDecimal("3.00").compareTo(restored.getPricing().inputTokenPrice()));
        assertEquals(0, new BigDecimal("0.30").compareTo(restored.getPricing().cachedInputTokenPrice()));
        assertEquals(0, new BigDecimal("15.00").compareTo(restored.getPricing().outputTokenPrice()));
        assertEquals(usd, restored.getPricing().currency());
    }

    @Test
    void jsonRoundTrip_pricingWithoutOptionalFields() {
        var pricing = ChatPricing.of(new BigDecimal("3.00"), new BigDecimal("15.00"));
        var original = ChatOptions.newBuilder().pricing(pricing).build();

        var restored = ChatOptions.fromJson(original.toJson());

        assertNotNull(restored.getPricing());
        assertEquals(0, new BigDecimal("3.00").compareTo(restored.getPricing().inputTokenPrice()));
        assertNull(restored.getPricing().cachedInputTokenPrice());
        assertEquals(0, new BigDecimal("15.00").compareTo(restored.getPricing().outputTokenPrice()));
        assertNull(restored.getPricing().currency());
    }

    @Test
    void toJson_omitsPricingWhenUnset() {
        var json = ChatOptions.DEFAULT.toJson();

        assertFalse(json.contains("pricing"));
    }

    // =================================================================================================================
    // Sliding window section end
    // =================================================================================================================

    @Test
    void jsonRoundTrip_slidingWindowTruncatesOnRestore() {
        var json = "{\"temperature\":0.7,\"reasoningEffort\":\"AUTO\",\"topP\":1.0,\"maxHistory\":2,"
            + "\"history\":["
            + "{\"role\":\"USER\",\"content\":\"msg1\"},"
            + "{\"role\":\"ASSISTANT\",\"content\":\"reply1\"},"
            + "{\"role\":\"USER\",\"content\":\"msg2\"},"
            + "{\"role\":\"ASSISTANT\",\"content\":\"reply2\"}"
            + "]}";

        var restored = ChatOptions.fromJson(json);
        var history = restored.getHistory();

        assertEquals(2, history.size());
        assertEquals("msg2", history.get(0).content());
        assertEquals("reply2", history.get(1).content());
    }

    // =================================================================================================================
    // Budget cap
    // =================================================================================================================

    private static ChatPricing flatPricing() {
        return new ChatPricing(new BigDecimal("1000000"), null, new BigDecimal("1000000"), Currency.getInstance("USD")); // $1/token
    }

    @Test
    void pricing_twoArg_setsBoth() {
        var pricing = flatPricing();
        var cap = new BigDecimal("5.00");
        var options = ChatOptions.newBuilder().pricing(pricing, cap).build();

        assertEquals(pricing, options.getPricing());
        assertEquals(cap, options.getMaxTotalCost());
        assertEquals(BigDecimal.ZERO, options.getTotalCost());
    }

    @Test
    void pricing_twoArg_nullPricing_throwsNPE() {
        var builder = ChatOptions.newBuilder();

        assertThrows(NullPointerException.class, () -> builder.pricing(null, BigDecimal.ONE));
    }

    @Test
    void pricing_twoArg_nullCap_throwsNPE() {
        var builder = ChatOptions.newBuilder();
        var pricing = flatPricing();

        assertThrows(NullPointerException.class, () -> builder.pricing(pricing, null));
    }

    @Test
    void pricing_twoArg_zeroCap_throwsIAE() {
        var builder = ChatOptions.newBuilder();
        var pricing = flatPricing();

        assertThrows(IllegalArgumentException.class, () -> builder.pricing(pricing, BigDecimal.ZERO));
    }

    @Test
    void pricing_twoArg_negativeCap_throwsIAE() {
        var builder = ChatOptions.newBuilder();
        var pricing = flatPricing();
        var negativeCap = new BigDecimal("-1");

        assertThrows(IllegalArgumentException.class, () -> builder.pricing(pricing, negativeCap));
    }

    @Test
    void pricing_singleArg_clearsPreviouslySetCap() {
        var options = ChatOptions.newBuilder().pricing(flatPricing(), new BigDecimal("5.00")).pricing(flatPricing()).build();

        assertNull(options.getMaxTotalCost());
    }

    @Test
    void getMaxTotalCost_defaultsToNull() {
        var options = ChatOptions.newBuilder().build();

        assertNull(options.getMaxTotalCost());
    }

    @Test
    void getTotalCost_defaultsToZero() {
        var options = ChatOptions.newBuilder().build();

        assertEquals(BigDecimal.ZERO, options.getTotalCost());
    }

    @Test
    void withPricing_twoArg_setsBoth() {
        var original = ChatOptions.newBuilder().build();
        var pricing = flatPricing();
        var cap = new BigDecimal("5.00");

        var copy = original.withPricing(pricing, cap);

        assertEquals(pricing, copy.getPricing());
        assertEquals(cap, copy.getMaxTotalCost());
    }

    @Test
    void withPricing_twoArg_nullPricing_throwsNPE() {
        var options = ChatOptions.newBuilder().build();

        assertThrows(NullPointerException.class, () -> options.withPricing(null, BigDecimal.ONE));
    }

    @Test
    void withPricing_twoArg_nullCap_throwsNPE() {
        var options = ChatOptions.newBuilder().build();
        var pricing = flatPricing();

        assertThrows(NullPointerException.class, () -> options.withPricing(pricing, null));
    }

    @Test
    void withPricing_twoArg_nonPositiveCap_throwsIAE() {
        var options = ChatOptions.newBuilder().build();
        var pricing = flatPricing();

        assertThrows(IllegalArgumentException.class, () -> options.withPricing(pricing, BigDecimal.ZERO));
    }

    @Test
    void withPricing_singleArg_clearsCap() {
        var original = ChatOptions.newBuilder().pricing(flatPricing(), new BigDecimal("5.00")).build();

        var cleared = original.withPricing(flatPricing());

        assertNull(cleared.getMaxTotalCost());
    }

    @Test
    void recordUsage_withPricing_accumulatesTotalCost() {
        var pricing = new ChatPricing(new BigDecimal("1000000"), null, new BigDecimal("1000000"), null); // $1/token
        var options = ChatOptions.newBuilder().pricing(pricing).build();

        options.recordUsage(new ChatUsage(1, 2, 0, 0));
        options.recordUsage(new ChatUsage(3, 4, 0, 0));

        assertEquals(0, new BigDecimal("10").compareTo(options.getTotalCost()));
    }

    @Test
    void recordUsage_withoutPricing_doesNotAccumulate() {
        var options = ChatOptions.newBuilder().build();

        options.recordUsage(new ChatUsage(10, 20, 0, 0));

        assertEquals(BigDecimal.ZERO, options.getTotalCost());
    }

    @Test
    void recordUsage_unreportedTokens_doesNotAccumulate() {
        var pricing = flatPricing();
        var options = ChatOptions.newBuilder().pricing(pricing).build();

        options.recordUsage(new ChatUsage(-1, -1, -1, -1));

        assertEquals(BigDecimal.ZERO, options.getTotalCost());
    }

    @Test
    void recordUsage_nullUsage_doesNotAccumulate() {
        var options = ChatOptions.newBuilder().pricing(flatPricing()).build();

        options.recordUsage(null);

        assertEquals(BigDecimal.ZERO, options.getTotalCost());
    }

    @Test
    void checkBudget_noCap_noOp() {
        var options = ChatOptions.newBuilder().pricing(flatPricing()).build();

        options.recordUsage(new ChatUsage(1_000_000, 1_000_000, 0, 0));
        options.checkBudget();
    }

    @Test
    void checkBudget_underCap_passes() {
        var options = ChatOptions.newBuilder().pricing(flatPricing(), new BigDecimal("5.00")).build();

        options.recordUsage(new ChatUsage(1, 1, 0, 0)); // $2

        options.checkBudget();
    }

    @Test
    void checkBudget_atCap_throws() {
        var options = ChatOptions.newBuilder().pricing(flatPricing(), new BigDecimal("2.00")).build();

        options.recordUsage(new ChatUsage(1, 1, 0, 0)); // $2 == cap

        var thrown = assertThrows(
            org.omnifaces.ai.exception.AIBudgetExceededException.class,
            options::checkBudget
        );
        assertEquals(0, new BigDecimal("2").compareTo(thrown.getTotalCost()));
        assertEquals(new BigDecimal("2.00"), thrown.getMaxTotalCost());
        assertEquals(Currency.getInstance("USD"), thrown.getCurrency());
    }

    @Test
    void checkBudget_overCap_throws() {
        var options = ChatOptions.newBuilder().pricing(flatPricing(), new BigDecimal("1.00")).build();

        options.recordUsage(new ChatUsage(5, 5, 0, 0)); // $10 >> $1 cap

        assertThrows(org.omnifaces.ai.exception.AIBudgetExceededException.class, options::checkBudget);
    }

    @Test
    void checkBudget_softCap_lastCallThatPushesOverSucceeds() {
        // Simulates the pre-call check: the call that pushes over the cap completes; the NEXT call is refused.
        var options = ChatOptions.newBuilder().pricing(flatPricing(), new BigDecimal("2.00")).build();

        options.checkBudget();
        options.recordUsage(new ChatUsage(1, 0, 0, 0)); // total = $1, under cap

        options.checkBudget();
        options.recordUsage(new ChatUsage(5, 0, 0, 0)); // total = $6, over cap

        assertThrows(org.omnifaces.ai.exception.AIBudgetExceededException.class, options::checkBudget);
    }

    @Test
    void resetBudget_resetsTotalCost_leavesCap() {
        var cap = new BigDecimal("2.00");
        var options = ChatOptions.newBuilder().pricing(flatPricing(), cap).build();
        options.recordUsage(new ChatUsage(5, 0, 0, 0));

        options.resetBudget();

        assertEquals(BigDecimal.ZERO, options.getTotalCost());
        assertEquals(cap, options.getMaxTotalCost());
        options.checkBudget();
    }

    @Test
    void resetBudget_onDefault_throwsISE() {
        assertThrows(IllegalStateException.class, ChatOptions.DEFAULT::resetBudget);
    }

    @Test
    void copy_resetsTotalCost() {
        var options = ChatOptions.newBuilder().pricing(flatPricing(), new BigDecimal("100")).build();
        options.recordUsage(new ChatUsage(5, 5, 0, 0));

        var copy = options.copy();

        assertEquals(BigDecimal.ZERO, copy.getTotalCost());
        assertEquals(new BigDecimal("100"), copy.getMaxTotalCost());
    }

    @Test
    void withPricing_twoArg_preservesOtherSettings() {
        var options = ChatOptions.newBuilder()
            .systemPrompt("test")
            .temperature(0.5)
            .maxTokens(500)
            .build();

        var withPricing = options.withPricing(flatPricing(), new BigDecimal("5.00"));

        assertEquals("test", withPricing.getSystemPrompt());
        assertEquals(0.5, withPricing.getTemperature());
        assertEquals(500, withPricing.getMaxTokens());
    }

    // =================================================================================================================
    // Budget cap JSON round-trip
    // =================================================================================================================

    @Test
    void jsonRoundTrip_maxTotalCost() {
        var cap = new BigDecimal("12.50");
        var original = ChatOptions.newBuilder().pricing(flatPricing(), cap).build();

        var restored = ChatOptions.fromJson(original.toJson());

        assertEquals(0, cap.compareTo(restored.getMaxTotalCost()));
    }

    @Test
    void toJson_omitsMaxTotalCostWhenUnset() {
        var json = ChatOptions.newBuilder().pricing(flatPricing()).build().toJson();

        assertFalse(json.contains("maxTotalCost"));
    }

    @Test
    void toJson_omitsTotalCostRuntimeState() {
        var options = ChatOptions.newBuilder().pricing(flatPricing(), new BigDecimal("100")).build();
        options.recordUsage(new ChatUsage(1, 1, 0, 0));

        var json = options.toJson();

        assertFalse(json.contains("totalCost\""));
    }

    @Test
    void serialization_totalCostResetsToZero() throws Exception {
        var options = ChatOptions.newBuilder().pricing(flatPricing(), new BigDecimal("100")).build();
        options.recordUsage(new ChatUsage(5, 5, 0, 0));

        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(options);
        }

        try (var ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            var deserialized = (ChatOptions) ois.readObject();
            assertEquals(BigDecimal.ZERO, deserialized.getTotalCost());
            assertEquals(new BigDecimal("100"), deserialized.getMaxTotalCost());
        }
    }

    @Test
    void fromJson_priorMaxTotalCost_restoresAsNull() {
        var json = "{\"temperature\":0.7,\"reasoningEffort\":\"AUTO\",\"topP\":1.0,\"pricing\":"
            + "{\"inputTokenPrice\":1000000,\"outputTokenPrice\":1000000,\"currency\":\"USD\"}}";

        var restored = ChatOptions.fromJson(json);

        assertNull(restored.getMaxTotalCost());
    }

    // =================================================================================================================
    // Accounting shared across derived instances
    // =================================================================================================================

    /**
     * A {@code withXxx} method derives another view on the same conversation, so usage recorded through the derived instance is visible on the original. This
     * is what keeps token and cost tracking working when a decorator has to derive an instance to make the call, such as to impose a JSON schema.
     */
    @Test
    void withJsonSchema_sharesUsageWithTheOriginal() {
        var options = ChatOptions.newBuilder().build();
        var derived = options.withJsonSchema(Json.createObjectBuilder().add("type", "object").build());

        derived.recordUsage(new ChatUsage(10, 20, 0, 0));

        assertEquals(options.getLastUsage(), derived.getLastUsage());
        assertNotNull(options.getLastUsage());
    }

    /**
     * A budget cap bounds the conversation, not the instance, so spending through a derived instance counts toward the original's cap.
     */
    @Test
    void withJsonSchema_accumulatesTotalCostOnTheOriginal() {
        var pricing = ChatPricing.of(new BigDecimal("1000000"), new BigDecimal("1000000"));
        var options = ChatOptions.newBuilder().pricing(pricing, new BigDecimal("100")).build();
        var derived = options.withJsonSchema(Json.createObjectBuilder().add("type", "object").build());

        derived.recordUsage(new ChatUsage(10, 20, 0, 0));

        assertEquals(options.getTotalCost(), derived.getTotalCost());
        assertEquals(0, options.getTotalCost().compareTo(new BigDecimal("30")));
    }

    /**
     * Setting a new cap starts a new budget window, as documented, rather than inheriting what was already spent.
     */
    @Test
    void withPricing_andCap_startsAFreshTotalCost() {
        var pricing = ChatPricing.of(new BigDecimal("1000000"), new BigDecimal("1000000"));
        var options = ChatOptions.newBuilder().pricing(pricing).build();
        options.recordUsage(new ChatUsage(10, 20, 0, 0));

        var recapped = options.withPricing(pricing, new BigDecimal("100"));

        assertEquals(BigDecimal.ZERO, recapped.getTotalCost());
    }

    /**
     * The shared constants are one instance serving every caller, so each instance derived from one is a conversation of its own.
     */
    @Test
    void withSystemPrompt_onSharedDefault_doesNotShareAccountingBetweenDerivedInstances() {
        var one = ChatOptions.DEFAULT.withSystemPrompt("one");
        var other = ChatOptions.DEFAULT.withSystemPrompt("other");

        one.recordUsage(new ChatUsage(10, 20, 0, 0));

        assertNotNull(one.getLastUsage());
        assertNull(other.getLastUsage());
    }

    @Test
    void copy_startsAFreshAccounting() {
        var options = ChatOptions.newBuilder().build();
        options.recordUsage(new ChatUsage(10, 20, 0, 0));

        assertNull(options.copy().getLastUsage());
    }

    // =================================================================================================================
    // JSON round trip - partially stated video options
    // =================================================================================================================

    /**
     * Only the video options which differ from the defaults are written, so a replay states what the original call stated and nothing more.
     */
    @Test
    void toJson_videoOptionsStatingOneFieldAlone_writesThatFieldAlone() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "Describe it");
        options.recordUploadedFile(new UploadedFile("file-1", TEST_MP4, AnalyzeVideoOptions.newBuilder().startOffset(Duration.ofSeconds(30)).build()));

        var restored = ChatOptions.fromJson(options.toJson()).getHistory().get(0).uploadedFiles().get(0).videoOptions();

        assertEquals(Duration.ofSeconds(30), restored.getStartOffset());
        assertNull(restored.getEndOffset());
        assertEquals(AnalyzeVideoOptions.DEFAULT_FPS, restored.getFps());
    }

    @Test
    void toJson_videoOptionsStatingNothingBeyondTheDefaults_writesNone() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "Describe it");
        options.recordUploadedFile(new UploadedFile("file-1", TEST_MP4, AnalyzeVideoOptions.DEFAULT));

        assertNull(ChatOptions.fromJson(options.toJson()).getHistory().get(0).uploadedFiles().get(0).videoOptions());
    }

    @Test
    void toJson_uploadedFileWithoutVideoOptions_roundTripsWithoutThem() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "Read it");
        options.recordUploadedFile(new UploadedFile("file-1", TEST_PDF));

        var restored = ChatOptions.fromJson(options.toJson()).getHistory().get(0).uploadedFiles().get(0);

        assertEquals("file-1", restored.id());
        assertNull(restored.videoOptions());
    }

    /**
     * A window size without a history states an empty conversation of that size, which is what a caller replays a fresh conversation from.
     */
    @Test
    void fromJson_maxHistoryWithoutHistory_restoresAnEmptyMemory() {
        var restored = ChatOptions.fromJson("{\"maxHistory\":7}");

        assertTrue(restored.hasMemory());
        assertEquals(7, restored.getMaxHistory());
        assertTrue(restored.getHistory().isEmpty());
    }

    // =================================================================================================================
    // Builder - no maximum
    // =================================================================================================================

    /**
     * A null maximum leaves the ceiling to the AI provider rather than stating one, so it is not rejected as a non-positive number.
     */
    @Test
    void maxTokens_null_leavesTheCeilingToTheProvider() {
        assertNull(ChatOptions.newBuilder().maxTokens(null).build().getMaxTokens());
    }

    // =================================================================================================================
    // JSON round trip - partially stated fields
    // =================================================================================================================

    /**
     * A field the caller never set is not written, and a field written as null is not read back as a value either.
     */
    @Test
    void fromJson_explicitlyNullMaxTokens_leavesTheCeilingToTheProvider() {
        assertNull(ChatOptions.fromJson("{\"maxTokens\":null}").getMaxTokens());
    }

    @Test
    void toJson_videoOptionsStatingTheRateAlone_writesThatRateAlone() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.USER, "Describe it");
        options.recordUploadedFile(new UploadedFile("file-1", TEST_MP4, AnalyzeVideoOptions.newBuilder().fps(2).build()));

        var restored = ChatOptions.fromJson(options.toJson()).getHistory().get(0).uploadedFiles().get(0).videoOptions();

        assertEquals(2, restored.getFps());
        assertNull(restored.getStartOffset());
        assertNull(restored.getEndOffset());
    }

    // =================================================================================================================
    // recordUploadedFile without a user message
    // =================================================================================================================

    /**
     * An uploaded file belongs to the message which sent it, so there has to be one to attach it to.
     */
    @Test
    void recordUploadedFile_historyWithoutAUserMessage_throws() {
        var options = ChatOptions.newBuilder().withMemory().build();
        options.recordMessage(Role.ASSISTANT, "How can I help?");
        var uploadedFile = new UploadedFile("file-1", TEST_PDF);

        assertThrows(IllegalStateException.class, () -> options.recordUploadedFile(uploadedFile));
    }

}
