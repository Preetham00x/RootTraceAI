package com.roottrace.knowledge.ingestion;

import com.roottrace.knowledge.exception.DocumentProcessingException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Component
public class TextDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String filename, String contentType) {
        return filename != null && filename.toLowerCase().endsWith(".txt") 
                || (contentType != null && contentType.startsWith("text/plain"));
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String filename) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            String title = filename != null ? filename.replaceAll("(?i)\\.txt$", "") : "Untitled Text Document";
            return new ParsedDocument(title, content, "TXT");
        } catch (Exception e) {
            throw new DocumentProcessingException("Failed to parse text document: " + filename, e);
        }
    }
}
