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

import static java.nio.charset.CodingErrorAction.REPORT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.omnifaces.ai.mime.AudioVideoMimeTypeDetector.startsWith;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Provides document MIME type detection based on magic bytes and content analysis.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
final class DocumentMimeTypeDetector {

    private enum DocumentMimeType implements MimeType {
        PDF("application/pdf", "pdf"),
        DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
        PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
        ZIP("application/zip", "zip"),
        CSV("text/csv", "csv"),
        JSON("application/json", "json"),
        HTML("text/html", "html"),
        XML("application/xml", "xml"),
        MARKDOWN("text/markdown", "md"),
        TEXT("text/plain", "txt"),
        BINARY("application/octet-stream", "bin");

        private final String value;
        private final String extension;

        DocumentMimeType(String value, String extension) {
            this.value = value;
            this.extension = extension;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public String extension() {
            return extension;
        }
    }

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};
    private static final byte[] ZIP_MAGIC = {'P', 'K', 0x03, 0x04};

    private DocumentMimeTypeDetector() {
        throw new AssertionError();
    }

    /**
     * Guesses the mime type of a document based on its magic bytes and content.
     * <p>
     * Recognized types: PDF, DOCX, XLSX, PPTX, CSV, JSON, HTML, XML, MD, with fallback to either TXT or BIN.
     *
     * @param content The content bytes to check.
     * @return The guessed mime type, never {@code null}.
     */
    static MimeType guessDocumentMimeType(byte[] content) {
        if (content == null || content.length == 0) {
            return DocumentMimeType.BINARY;
        }

        if (startsWith(content, 0, PDF_MAGIC)) {
            return DocumentMimeType.PDF;
        }

        if (startsWith(content, 0, ZIP_MAGIC)) {
            return guessZipMimeType(content);
        }

        var likelyText = findLikelyText(content);

        if (likelyText.isPresent()) {
            return guessTextMimeType(likelyText.get());
        }

        return DocumentMimeType.BINARY;
    }

    /**
     * Guesses the mime type for ZIP-based formats (docx, xlsx, pptx).
     * We peek inside the ZIP to find characteristic files.
     * Falls back to {@code application/zip}.
     */
    private static MimeType guessZipMimeType(byte[] content) {
        try (var zis = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                var name = entry.getName();

                if (name.startsWith("word/")) {
                    return DocumentMimeType.DOCX;
                }

                if (name.startsWith("xl/")) {
                    return DocumentMimeType.XLSX;
                }

                if (name.startsWith("ppt/")) {
                    return DocumentMimeType.PPTX;
                }
            }

            return DocumentMimeType.ZIP;
        }
        catch (Exception ignore) {
            return DocumentMimeType.BINARY;
        }
    }

    /**
     * Checks if content is likely text (not binary).
     * Validates UTF-8 encoding and rejects control characters.
     */
    private static Optional<String> findLikelyText(byte[] content) {
        var decoder = UTF_8.newDecoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT);

        try {
            var text = decoder.decode(ByteBuffer.wrap(content, 0, Math.min(content.length, 8192))).toString();

            for (int i = 0; i < text.length(); i++) {
                int codePoint = text.codePointAt(i);

                if (Character.isSupplementaryCodePoint(codePoint)) {
                    i++; // Skip low surrogate of surrogate pair.
                }

                if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                    return Optional.empty(); // Reject control characters except whitespace.
                }
            }

            return Optional.of(text);
        }
        catch (CharacterCodingException ignore) {
            return Optional.empty(); // Invalid UTF-8 - likely binary.
        }
    }

    /**
     * Guesses the mime type for text-based content.
     * Falls back to {@code text/plain}.
     */
    private static MimeType guessTextMimeType(String text) {
        if (looksLikeJson(text)) {
            return DocumentMimeType.JSON;
        }

        if (looksLikeXml(text)) {
            if (looksLikeHtml(text)) {
                return DocumentMimeType.HTML;
            }

            return DocumentMimeType.XML;
        }

        if (looksLikeCsv(text)) {
            return DocumentMimeType.CSV;
        }

        if (looksLikeMarkdown(text)) {
            return DocumentMimeType.MARKDOWN;
        }

        return DocumentMimeType.TEXT;
    }

    /**
     * Check if starts with { or [ and contains } or ].
     */
    private static boolean looksLikeJson(String text) {
        return (text.startsWith("{") && text.contains("}")) || (text.startsWith("[") && text.contains("]"));
    }

    /**
     * Check if starts with < and contains >.
     */
    private static boolean looksLikeXml(String text) {
        return text.startsWith("<") && text.contains(">");
    }

    /**
     * Check if xml contains doctype or html/head/body tags.
     */
    private static boolean looksLikeHtml(String xml) {
        var lower = xml.toLowerCase();
        return lower.contains("<!doctype html") || lower.contains("<html") || lower.contains("<head") || lower.contains("<body");
    }

    /**
     * Check for consistent comma/semicolon separated values.
     */
    private static boolean looksLikeCsv(String text) {
        var lines = text.split("\n", 10);

        if (lines.length < 2) {
            return false;
        }

        char delimiter = lines[0].contains(";") ? ';' : ',';
        int expectedCount = countChar(lines[0], delimiter);

        if (expectedCount == 0) {
            return false;
        }

        int consistentLines = 0;

        for (var line : lines) {
            if (!line.isBlank() && countChar(line, delimiter) == expectedCount) {
                consistentLines++;
            }
        }

        return consistentLines >= Math.min(lines.length, 3);
    }

    private static int countChar(String s, char c) {
        int count = 0;

        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                count++;
            }
        }

        return count;
    }

    /**
     * Check for common markdown patterns: headers, links, code blocks.
     */
    private static boolean looksLikeMarkdown(String text) {
        return text.startsWith("# ") || text.startsWith("## ") || text.startsWith("### ") || text.contains("\n# ") || text.contains("\n## ") || text.contains("\n### ") || text.contains("](") || text.contains("```");
    }
}
