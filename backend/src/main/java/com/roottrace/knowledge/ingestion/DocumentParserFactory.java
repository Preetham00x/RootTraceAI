package com.roottrace.knowledge.ingestion;

import com.roottrace.knowledge.exception.UnsupportedDocumentException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentParserFactory {

    private final List<DocumentParser> parsers;

    @Autowired
    public DocumentParserFactory(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    public DocumentParser getParser(String filename, String contentType) {
        for (DocumentParser parser : parsers) {
            if (parser.supports(filename, contentType)) {
                return parser;
            }
        }
        throw new UnsupportedDocumentException("No supported parser found for file: " + filename + " with type: " + contentType);
    }
}
