package com.roottrace.knowledge.retrieval;

import java.util.List;

/**
 * Result of a hybrid retrieval operation, including the ranked chunks
 * and metadata about how many results each source contributed.
 */
public record RetrievalResult(
        String query,
        List<RankedChunk> results,
        int semanticCount,
        int keywordCount,
        int fusedCount,
        boolean semanticAvailable,
        boolean keywordAvailable
) {
    public static RetrievalResult empty(String query) {
        return new RetrievalResult(query, List.of(), 0, 0, 0, false, false);
    }
}
