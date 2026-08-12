package com.roottrace.knowledge.ingestion;

import com.roottrace.knowledge.exception.UnsupportedDocumentException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentParserFactoryTest {

    private final DocumentParserFactory factory = new DocumentParserFactory(
            List.of(new MarkdownDocumentParser(), new TextDocumentParser())
    );

    @Test
    void testGetParser_Markdown() {
        DocumentParser parser = factory.getParser("doc.md", null);
        assertInstanceOf(MarkdownDocumentParser.class, parser);
    }

    @Test
    void testGetParser_Text() {
        DocumentParser parser = factory.getParser("doc.txt", null);
        assertInstanceOf(TextDocumentParser.class, parser);
    }

    @Test
    void testGetParser_Unsupported() {
        assertThrows(UnsupportedDocumentException.class, () -> factory.getParser("doc.pdf", null));
    }
}
