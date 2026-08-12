package com.roottrace.knowledge.dto;

import java.util.UUID;

public record KnowledgeSearchResult(
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        String sectionPath,
        String content,
        double semanticScore,
        int keywordRank,
        int semanticRank,
        double rrfScore
) {
}
