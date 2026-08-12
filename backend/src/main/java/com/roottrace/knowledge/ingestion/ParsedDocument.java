package com.roottrace.knowledge.ingestion;

public record ParsedDocument(
        String title,
        String content,
        String sourceType
) {
}
