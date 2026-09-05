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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.ByteArrayOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DocumentMimeTypeDetectorTest {

    // =================================================================================================================
    // Test guessDocumentMimeType - PDF detection
    // =================================================================================================================

    @Test
    void guessDocumentMimeType_pdf() {
        var content = new byte[] { '%', 'P', 'D', 'F', '-', '1', '.', '4' };
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("pdf", result.extension());
    }

    /**
     * The text based types are recognized by their content alone, so one table states every shape which must map to a type.
     */
    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource
    void guessDocumentMimeType_ofTextContent(String description, String extension, String content) {
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content.getBytes(UTF_8));
        assertEquals(extension, result.extension());
    }

    static Stream<Arguments> guessDocumentMimeType_ofTextContent() {
        return Stream.of(
            arguments("pdf_withContent", "pdf", "%PDF-1.7\n1 0 obj\n<<\n>>\nendobj"),
            arguments("json_object", "json", "{\"key\": \"value\"}"),
            arguments("json_array", "json", "[1, 2, 3]"),
            arguments("json_nested", "json", "{\"nested\": {\"array\": [1, 2, 3]}}"),
            arguments("json_emptyObject", "json", "{}"),
            arguments("json_emptyArray", "json", "[]"),
            arguments("xml", "xml", "<root><child/></root>"),
            arguments("xml_withDeclaration", "xml", "<?xml version=\"1.0\"?><root/>"),
            arguments("xml_withNamespaces", "xml", "<root xmlns=\"http://example.com\"><child/></root>"),
            arguments("html_withDoctype", "html", "<!DOCTYPE html><html><body></body></html>"),
            arguments("html_withHtmlTag", "html", "<html><head></head><body></body></html>"),
            arguments("html_withHeadTag", "html", "<head><title>Test</title></head>"),
            arguments("html_withBodyTag", "html", "<body><p>Content</p></body>"),
            arguments("html_caseInsensitive", "html", "<!DOCTYPE HTML><HTML><BODY></BODY></HTML>"),
            arguments("csv_commaDelimited", "csv", "name,age,city\nJohn,30,NYC\nJane,25,LA\n"),
            arguments("csv_semicolonDelimited", "csv", "name;age;city\nJohn;30;NYC\nJane;25;LA\n"),
            arguments("csv_manyColumns", "csv", "a,b,c,d,e,f\n1,2,3,4,5,6\n7,8,9,10,11,12\n"),
            arguments("notCsv_singleLine", "txt", "name,age,city"),
            arguments("notCsv_inconsistentDelimiters", "txt", "a,b,c\na,b\na,b,c,d\n"),
            arguments("notCsv_noDelimiters", "txt", "line1\nline2\nline3\n"),
            arguments("markdown_h1", "md", "# Heading 1\nSome content"),
            arguments("markdown_h2", "md", "## Heading 2\nSome content"),
            arguments("markdown_h3", "md", "### Heading 3\nSome content"),
            arguments("markdown_headingInMiddle", "md", "Some intro\n\n# Main Heading\n\nContent"),
            arguments("markdown_link", "md", "Check out [this link](https://example.com) for more info."),
            arguments("markdown_codeBlock", "md", "Here is some code:\n```java\npublic class Test {}\n```"),
            arguments("plainText", "txt", "This is just plain text without any special formatting."),
            arguments("plainText_multiline", "txt", "Line 1\nLine 2\nLine 3"),
            arguments("plainText_withUnicode", "txt", "Hello 世界! Привет мир! مرحبا بالعالم"),
            arguments("textWithWhitespace_shouldNotBeBinary", "txt", "Line1\t\tTabbed\r\nLine2\nLine3")
        );
    }

    // =================================================================================================================
    // Test guessDocumentMimeType - ZIP-based formats
    // =================================================================================================================

    @Test
    void guessDocumentMimeType_zip() throws Exception {
        var content = createZipWithEntry("test.txt", "Hello".getBytes(UTF_8));
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("zip", result.extension());
    }

    @Test
    void guessDocumentMimeType_docx() throws Exception {
        var content = createZipWithEntry("word/document.xml", "<document/>".getBytes(UTF_8));
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("docx", result.extension());
    }

    @Test
    void guessDocumentMimeType_xlsx() throws Exception {
        var content = createZipWithEntry("xl/workbook.xml", "<workbook/>".getBytes(UTF_8));
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("xlsx", result.extension());
    }

    @Test
    void guessDocumentMimeType_pptx() throws Exception {
        var content = createZipWithEntry("ppt/presentation.xml", "<presentation/>".getBytes(UTF_8));
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("pptx", result.extension());
    }

    // =================================================================================================================
    // Test guessDocumentMimeType - binary fallback
    // =================================================================================================================

    @Test
    void guessDocumentMimeType_binary() {
        var content = new byte[] { 0x00, 0x01, 0x02, 0x03, (byte) 0xFF, (byte) 0xFE };
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("bin", result.extension());
    }

    @Test
    void guessDocumentMimeType_binary_withControlCharacters() {
        var content = new byte[] { 'H', 'e', 'l', 'l', 'o', 0x07, 'W', 'o', 'r', 'l', 'd' };
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("bin", result.extension());
    }

    @Test
    void guessDocumentMimeType_binary_invalidUtf8() {
        var content = new byte[] { (byte) 0xC0, (byte) 0x80 };
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("bin", result.extension());
    }

    // =================================================================================================================
    // Test guessDocumentMimeType - edge cases
    // =================================================================================================================

    @Test
    void guessDocumentMimeType_null_shouldReturnBinary() {
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(null);
        assertEquals("bin", result.extension());
    }

    @Test
    void guessDocumentMimeType_empty_shouldReturnBinary() {
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(new byte[0]);
        assertEquals("bin", result.extension());
    }

    @Test
    void guessDocumentMimeType_corruptedZip_shouldReturnBinary() {
        var content = new byte[] {
            'P', 'K', 0x03, 0x04, // Local file header signature
            0x00, 0x00, // Version needed
            0x00, 0x00, // Flags
            0x00, 0x00, // Compression method
            0x00, 0x00, // Last mod time
            0x00, 0x00, // Last mod date
            0x00, 0x00, 0x00, 0x00, // CRC-32
            0x00, 0x00, 0x00, 0x00, // Compressed size
            0x00, 0x00, 0x00, 0x00, // Uncompressed size
            (byte) 0xFF, (byte) 0xFF, // File name length = 65535 (but data is truncated, hence corrupted)
            0x00, 0x00 // Extra field length
        };
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("bin", result.extension());
    }

    // =================================================================================================================
    // Helper methods
    // =================================================================================================================

    private static byte[] createZipWithEntry(String entryName, byte[] content) throws Exception {
        var baos = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    // =================================================================================================================
    // Test guessDocumentMimeType - byte order mark
    // =================================================================================================================

    @Test
    void guessDocumentMimeType_xml_withByteOrderMark() {
        assertEquals("application/xml", guessWithByteOrderMark("<?xml version=\"1.0\"?><root/>").value());
    }

    @Test
    void guessDocumentMimeType_html_withByteOrderMark() {
        assertEquals("text/html", guessWithByteOrderMark("<!doctype html><html></html>").value());
    }

    @Test
    void guessDocumentMimeType_json_withByteOrderMark() {
        assertEquals("application/json", guessWithByteOrderMark("{\"key\":\"value\"}").value());
    }

    private static MimeType guessWithByteOrderMark(String text) {
        var content = ("\uFEFF" + text).getBytes(UTF_8);
        return DocumentMimeTypeDetector.guessDocumentMimeType(content);
    }

    // =================================================================================================================
    // Text shapes which are recognized by their opening characters
    // =================================================================================================================

    @Test
    void guessDocumentMimeType_jsonArray_isJson() {
        assertEquals("application/json", DocumentMimeTypeDetector.guessDocumentMimeType("[{\"a\":1}]".getBytes(UTF_8)).value());
    }

    /**
     * An opening brace alone does not make a document JSON, as the text may merely start with one.
     */
    @Test
    void guessDocumentMimeType_unclosedJson_isPlainText() {
        assertEquals("text/plain", DocumentMimeTypeDetector.guessDocumentMimeType("{ this never closes".getBytes(UTF_8)).value());
    }

    @Test
    void guessDocumentMimeType_unclosedJsonArray_isPlainText() {
        assertEquals("text/plain", DocumentMimeTypeDetector.guessDocumentMimeType("[ this never closes".getBytes(UTF_8)).value());
    }

    @Test
    void guessDocumentMimeType_markdownHeadersOfEveryDepth_areMarkdown() {
        assertEquals("text/markdown", DocumentMimeTypeDetector.guessDocumentMimeType("# Title".getBytes(UTF_8)).value());
        assertEquals("text/markdown", DocumentMimeTypeDetector.guessDocumentMimeType("## Title".getBytes(UTF_8)).value());
        assertEquals("text/markdown", DocumentMimeTypeDetector.guessDocumentMimeType("### Title".getBytes(UTF_8)).value());
        assertEquals("text/markdown", DocumentMimeTypeDetector.guessDocumentMimeType("Intro\n# Title".getBytes(UTF_8)).value());
        assertEquals("text/markdown", DocumentMimeTypeDetector.guessDocumentMimeType("Intro\n## Title".getBytes(UTF_8)).value());
    }

    @Test
    void guessDocumentMimeType_unclosedTag_isPlainText() {
        assertEquals("text/plain", DocumentMimeTypeDetector.guessDocumentMimeType("< this never closes".getBytes(UTF_8)).value());
    }

    @Test
    void guessDocumentMimeType_markdownHeaderBeyondTheFirstLine_isMarkdown() {
        assertEquals("text/markdown", DocumentMimeTypeDetector.guessDocumentMimeType("Intro\n### Heading".getBytes(UTF_8)).value());
    }

    @Test
    void guessDocumentMimeType_markdownLink_isMarkdown() {
        assertEquals("text/markdown", DocumentMimeTypeDetector.guessDocumentMimeType("see [the docs](https://omnihai.org)".getBytes(UTF_8)).value());
    }

    @Test
    void guessDocumentMimeType_markdownCodeFence_isMarkdown() {
        assertEquals("text/markdown", DocumentMimeTypeDetector.guessDocumentMimeType("Example:\n```\ncode\n```".getBytes(UTF_8)).value());
    }

}
