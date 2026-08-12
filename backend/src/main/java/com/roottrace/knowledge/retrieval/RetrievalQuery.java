package com.roottrace.knowledge.retrieval;

/**
 * Input query for hybrid knowledge retrieval.
 */
public record RetrievalQuery(
        String query,
        int topK,
        String service,
        String environment
) {
    public static RetrievalQuery of(String query, int topK) {
        return new RetrievalQuery(query, topK, null, null);
    }
}
