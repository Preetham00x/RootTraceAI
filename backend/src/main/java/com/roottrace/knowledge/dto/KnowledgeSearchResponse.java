package com.roottrace.knowledge.dto;

import java.util.List;

public record KnowledgeSearchResponse(
        String query,
        List<KnowledgeSearchResult> results,
        int totalResults,
        boolean semanticAvailable,
        boolean keywordAvailable
) {
}
