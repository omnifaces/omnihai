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

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

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

    @Test
    void guessDocumentMimeType_pdf_withContent() {
        var content = "%PDF-1.7\n1 0 obj\n<<\n>>\nendobj".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("pdf", result.extension());
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
    // Test guessDocumentMimeType - JSON detection
    // =================================================================================================================

    @Test
    void guessDocumentMimeType_json_object() {
        var content = "{\"key\": \"value\"}".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("json", result.extension());
    }

    @Test
    void guessDocumentMimeType_json_array() {
        var content = "[1, 2, 3]".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("json", result.extension());
    }

    @Test
    void guessDocumentMimeType_json_nested() {
        var content = "{\"nested\": {\"array\": [1, 2, 3]}}".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("json", result.extension());
    }

    @Test
    void guessDocumentMimeType_json_emptyObject() {
        var content = "{}".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("json", result.extension());
    }

    @Test
    void guessDocumentMimeType_json_emptyArray() {
        var content = "[]".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("json", result.extension());
    }

    // =================================================================================================================
    // Test guessDocumentMimeType - XML detection
    // =================================================================================================================

    @Test
    void guessDocumentMimeType_xml() {
        var content = "<root><child/></root>".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("xml", result.extension());
    }

    @Test
    void guessDocumentMimeType_xml_withDeclaration() {
        var content = "<?xml version=\"1.0\"?><root/>".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("xml", result.extension());
    }

    @Test
    void guessDocumentMimeType_xml_withNamespaces() {
        var content = "<root xmlns=\"http://example.com\"><child/></root>".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("xml", result.extension());
    }

    // =================================================================================================================
    // Test guessDocumentMimeType - HTML detection
    // =================================================================================================================

    @Test
    void guessDocumentMimeType_html_withDoctype() {
        var content = "<!DOCTYPE html><html><body></body></html>".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("html", result.extension());
    }

    @Test
    void guessDocumentMimeType_html_withHtmlTag() {
        var content = "<html><head></head><body></body></html>".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("html", result.extension());
    }

    @Test
    void guessDocumentMimeType_html_withHeadTag() {
        var content = "<head><title>Test</title></head>".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("html", result.extension());
    }

    @Test
    void guessDocumentMimeType_html_withBodyTag() {
        var content = "<body><p>Content</p></body>".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("html", result.extension());
    }

    @Test
    void guessDocumentMimeType_html_caseInsensitive() {
        var content = "<!DOCTYPE HTML><HTML><BODY></BODY></HTML>".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("html", result.extension());
    }

    // =================================================================================================================
    // Test guessDocumentMimeType - CSV detection
    // =================================================================================================================

    @Test
    void guessDocumentMimeType_csv_commaDelimited() {
        var content = "name,age,city\nJohn,30,NYC\nJane,25,LA\n".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("csv", result.extension());
    }

    @Test
    void guessDocumentMimeType_csv_semicolonDelimited() {
        var content = "name;age;city\nJohn;30;NYC\nJane;25;LA\n".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("csv", result.extension());
    }

    @Test
    void guessDocumentMimeType_csv_manyColumns() {
        var content = "a,b,c,d,e,f\n1,2,3,4,5,6\n7,8,9,10,11,12\n".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("csv", result.extension());
    }

    @Test
    void guessDocumentMimeType_notCsv_singleLine() {
        var content = "name,age,city".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("txt", result.extension());
    }

    @Test
    void guessDocumentMimeType_notCsv_inconsistentDelimiters() {
        var content = "a,b,c\na,b\na,b,c,d\n".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("txt", result.extension());
    }

    @Test
    void guessDocumentMimeType_notCsv_noDelimiters() {
        var content = "line1\nline2\nline3\n".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("txt", result.extension());
    }

    // =================================================================================================================
    // Test guessDocumentMimeType - Markdown detection
    // =================================================================================================================

    @Test
    void guessDocumentMimeType_markdown_h1() {
        var content = "# Heading 1\nSome content".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("md", result.extension());
    }

    @Test
    void guessDocumentMimeType_markdown_h2() {
        var content = "## Heading 2\nSome content".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("md", result.extension());
    }

    @Test
    void guessDocumentMimeType_markdown_h3() {
        var content = "### Heading 3\nSome content".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("md", result.extension());
    }

    @Test
    void guessDocumentMimeType_markdown_headingInMiddle() {
        var content = "Some intro\n\n# Main Heading\n\nContent".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("md", result.extension());
    }

    @Test
    void guessDocumentMimeType_markdown_link() {
        var content = "Check out [this link](https://example.com) for more info.".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("md", result.extension());
    }

    @Test
    void guessDocumentMimeType_markdown_codeBlock() {
        var content = "Here is some code:\n```java\npublic class Test {}\n```".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("md", result.extension());
    }

    // =================================================================================================================
    // Test guessDocumentMimeType - plain text fallback
    // =================================================================================================================

    @Test
    void guessDocumentMimeType_plainText() {
        var content = "This is just plain text without any special formatting.".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("txt", result.extension());
    }

    @Test
    void guessDocumentMimeType_plainText_multiline() {
        var content = "Line 1\nLine 2\nLine 3".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("txt", result.extension());
    }

    @Test
    void guessDocumentMimeType_plainText_withUnicode() {
        var content = "Hello 世界! Привет мир! مرحبا بالعالم".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("txt", result.extension());
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

    @Test
    void guessDocumentMimeType_textWithWhitespace_shouldNotBeBinary() {
        var content = "Line1\t\tTabbed\r\nLine2\nLine3".getBytes(UTF_8);
        var result = DocumentMimeTypeDetector.guessDocumentMimeType(content);
        assertEquals("txt", result.extension());
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

}
