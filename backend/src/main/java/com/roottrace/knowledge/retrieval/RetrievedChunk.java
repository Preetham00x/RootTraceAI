package com.roottrace.knowledge.retrieval;

import java.util.UUID;

/**
 * A single chunk retrieved from either semantic or FTS search,
 * before RRF fusion.
 */
public record RetrievedChunk(
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        String sectionPath,
        String content,
        double score
) {
}
