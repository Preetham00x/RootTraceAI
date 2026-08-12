package com.roottrace.knowledge.ingestion;

import com.roottrace.knowledge.exception.DocumentProcessingException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Component
public class MarkdownDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String filename, String contentType) {
        return filename != null && (filename.toLowerCase().endsWith(".md") || filename.toLowerCase().endsWith(".markdown"))
                || (contentType != null && contentType.startsWith("text/markdown"));
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String filename) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            String title = extractTitle(content, filename);
            return new ParsedDocument(title, content, "MARKDOWN");
        } catch (Exception e) {
            throw new DocumentProcessingException("Failed to parse markdown document: " + filename, e);
        }
    }

    private String extractTitle(String content, String filename) {
        // Try to find the first H1 tag
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("# ")) {
                return line.trim().substring(2).trim();
            }
        }
        
        // Fallback to filename
        if (filename != null) {
            return filename.replaceAll("(?i)\\.(md|markdown)$", "");
        }
        
        return "Untitled Markdown Document";
    }
}
