package com.roottrace.knowledge.dto;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocumentSummaryResponse(
        UUID id,
        String title,
        String sourceType,
        String status,
        Instant createdAt
) {
}
