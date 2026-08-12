package com.roottrace.knowledge.retrieval;

import java.util.UUID;

/**
 * Citation metadata for a retrieved chunk, answering "where did this evidence come from?"
 */
public record RetrievalCitation(
        UUID documentId,
        String documentTitle,
        UUID chunkId,
        String sectionPath
) {
}
