package com.roottrace.knowledge.dto;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocumentResponse(
        UUID id,
        String title,
        String originalFilename,
        String sourceType,
        String status,
        int chunkCount,
        Instant createdAt
) {
}
