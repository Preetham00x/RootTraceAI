package com.roottrace.knowledge.ingestion;

import java.io.InputStream;

public interface DocumentParser {
    boolean supports(String filename, String contentType);
    ParsedDocument parse(InputStream inputStream, String filename);
}
