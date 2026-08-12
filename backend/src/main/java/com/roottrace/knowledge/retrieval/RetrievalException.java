package com.roottrace.knowledge.retrieval;

/**
 * Thrown when the hybrid retrieval pipeline fails completely
 * (both FTS and semantic search are unavailable).
 * Maps to HTTP 502 Bad Gateway.
 */
public class RetrievalException extends RuntimeException {

    public RetrievalException(String message) {
        super(message);
    }

    public RetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
