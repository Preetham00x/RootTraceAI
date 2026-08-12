package com.roottrace.knowledge.ingestion;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextDocumentParserTest {

    private final TextDocumentParser parser = new TextDocumentParser();

    @Test
    void testSupports() {
        assertTrue(parser.supports("doc.txt", null));
        assertTrue(parser.supports("doc.TXT", null));
        assertTrue(parser.supports("doc", "text/plain"));
        assertFalse(parser.supports("doc.md", null));
        assertFalse(parser.supports("doc.pdf", null));
    }

    @Test
    void testParse() {
        String content = "Just plain text.";
        ByteArrayInputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        
        ParsedDocument result = parser.parse(is, "my_file.txt");
        
        assertEquals("my_file", result.title());
        assertEquals(content, result.content());
        assertEquals("TXT", result.sourceType());
    }
}
