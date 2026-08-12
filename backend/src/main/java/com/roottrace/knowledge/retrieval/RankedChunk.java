package com.roottrace.knowledge.retrieval;

import java.util.UUID;

/**
 * A knowledge chunk after RRF fusion, carrying ranking metadata
 * from both semantic and keyword search.
 */
public record RankedChunk(
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
    public RetrievalCitation toCitation() {
        return new RetrievalCitation(documentId, documentTitle, chunkId, sectionPath);
    }
}
