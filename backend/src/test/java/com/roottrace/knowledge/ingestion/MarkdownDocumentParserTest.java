package com.roottrace.knowledge.ingestion;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownDocumentParserTest {

    private final MarkdownDocumentParser parser = new MarkdownDocumentParser();

    @Test
    void testSupports() {
        assertTrue(parser.supports("doc.md", null));
        assertTrue(parser.supports("doc.markdown", null));
        assertTrue(parser.supports("doc", "text/markdown"));
        assertFalse(parser.supports("doc.txt", null));
        assertFalse(parser.supports("doc.pdf", null));
    }

    @Test
    void testParse() {
        String content = "# Main Title\n\nSome text.";
        ByteArrayInputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        
        ParsedDocument result = parser.parse(is, "doc.md");
        
        assertEquals("Main Title", result.title());
        assertEquals(content, result.content());
        assertEquals("MARKDOWN", result.sourceType());
    }

    @Test
    void testParse_NoHeading_FallbackToFilename() {
        String content = "Just some text without heading.";
        ByteArrayInputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        
        ParsedDocument result = parser.parse(is, "my_document.md");
        
        assertEquals("my_document", result.title());
        assertEquals("MARKDOWN", result.sourceType());
    }
}
