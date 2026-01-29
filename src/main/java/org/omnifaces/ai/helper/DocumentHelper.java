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

import static java.nio.charset.CodingErrorAction.REPORT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class for document operations.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public final class DocumentHelper {

    // Media types.
    private static final String PDF_MEDIA_TYPE = "application/pdf";
    private static final String DOCX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PPTX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String ZIP_MEDIA_TYPE = "application/zip";
    private static final String CSV_MEDIA_TYPE = "text/csv";
    private static final String JSON_MEDIA_TYPE = "application/json";
    private static final String HTML_MEDIA_TYPE = "text/html";
    private static final String XML_MEDIA_TYPE = "application/xml";
    private static final String MARKDOWN_MEDIA_TYPE = "text/markdown";
    private static final String TEXT_MEDIA_TYPE = "text/plain";
    private static final String BINARY_MEDIA_TYPE = "application/octet-stream";

    // Magic bytes.
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46}; // %PDF
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04}; // PK..

    private DocumentHelper() {
        throw new AssertionError();
    }

    /**
     * Guesses the media type of a document based on its magic bytes and content.
     * <p>
     * Supported formats: PDF, DOCX, XLSX, PPTX, CSV, JSON, HTML, XML, Markdown, and plain text.
     *
     * @param content The content bytes to check.
     * @return The guessed media type, or {@code text/plain} for text content, or {@code application/octet-stream} for unknown binary content.
     */
    public static String guessMediaType(byte[] content) {
        if (content == null || content.length == 0) {
            return BINARY_MEDIA_TYPE;
        }

        if (startsWith(content, 0, PDF_MAGIC)) {
            return PDF_MEDIA_TYPE;
        }

        if (startsWith(content, 0, ZIP_MAGIC)) {
            return guessZipMediaType(content);
        }

        if (isLikelyText(content)) {
            return guessTextMediaType(content);
        }

        return BINARY_MEDIA_TYPE;
    }

    /**
     * Converts the given content to a Base64 string.
     *
     * @param content The content to be encoded.
     * @return The Base64 encoded string.
     */
    public static String encodeBase64(byte[] content) {
        return Base64.getEncoder().encodeToString(content);
    }

    /**
     * Converts the given content to a data URI.
     *
     * @param content The content bytes.
     * @return The data URI string in the format {@code data:<media-type>;base64,<data>}.
     */
    public static String toDataUri(byte[] content) {
        return toDataUri(guessMediaType(content), content);
    }

    static String toDataUri(String mediaType, byte[] content) {
        return "data:" + mediaType + ";base64," + encodeBase64(content);
    }

    static boolean startsWith(byte[] content, int offset, byte[] prefix) {
        if (content.length < offset + prefix.length) {
            return false;
        }

        for (int i = 0; i < prefix.length; i++) {
            if (content[offset + i] != prefix[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Guesses the media type for ZIP-based formats (docx, xlsx, pptx).
     * We peek inside the ZIP to find characteristic files.
     * Falls back to {@code application/zip}.
     */
    private static String guessZipMediaType(byte[] content) {
        try (var zis = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                var name = entry.getName();

                if (name.startsWith("word/")) {
                    return DOCX_MEDIA_TYPE;
                }

                if (name.startsWith("xl/")) {
                    return XLSX_MEDIA_TYPE;
                }

                if (name.startsWith("ppt/")) {
                    return PPTX_MEDIA_TYPE;
                }
            }

            return ZIP_MEDIA_TYPE;
        }
        catch (Exception ignore) {
            // Not a valid ZIP or error reading - fall through.
        }

        return BINARY_MEDIA_TYPE;
    }

    /**
     * Checks if content is likely text (not binary).
     * Validates UTF-8 encoding and rejects control characters.
     */
    private static boolean isLikelyText(byte[] content) {
        var decoder = UTF_8.newDecoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT);

        try {
            var text = decoder.decode(ByteBuffer.wrap(content, 0, Math.min(content.length, 1024))).toString();

            for (int i = 0; i < text.length(); i++) {
                int codePoint = text.codePointAt(i);

                if (Character.isSupplementaryCodePoint(codePoint)) {
                    i++; // Skip low surrogate of surrogate pair.
                }

                if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                    return false; // Reject control characters except whitespace.
                }
            }

            return true;
        }
        catch (CharacterCodingException ignore) {
            return false; // Invalid UTF-8 - likely binary.
        }
    }

    /**
     * Guesses the media type for text-based content.
     * Falls back to {@code text/plain}.
     */
    private static String guessTextMediaType(byte[] content) {
        var text = new String(content, UTF_8).strip();

        if (looksLikeJson(text)) {
            return JSON_MEDIA_TYPE;
        }

        if (looksLikeXml(text)) {
            if (looksLikeHtml(text)) {
                return HTML_MEDIA_TYPE;
            }

            return XML_MEDIA_TYPE;
        }

        if (looksLikeCsv(text)) {
            return CSV_MEDIA_TYPE;
        }

        if (looksLikeMarkdown(text)) {
            return MARKDOWN_MEDIA_TYPE;
        }

        return TEXT_MEDIA_TYPE;
    }

    /**
     * Check if starts with { or [ and ends with } or ].
     */
    private static boolean looksLikeJson(String text) {
        return (text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"));
    }

    /**
     * Check if starts with < and ends with >.
     */
    private static boolean looksLikeXml(String text) {
        return text.startsWith("<") && text.endsWith(">");
    }

    /**
     * Check if xml contains doctype or html/head/body tags.
     */
    private static boolean looksLikeHtml(String xml) {
        var lower = xml.substring(0, Math.min(xml.length(), 1024)).toLowerCase();
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
        return text.startsWith("# ") || text.startsWith("## ") || text.contains("\n# ") || text.contains("\n## ") || text.contains("](") || text.contains("```");
    }
}
