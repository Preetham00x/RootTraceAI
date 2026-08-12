package com.roottrace.ai.diagnosis;

/**
 * Thrown when there is insufficient evidence in the knowledge base
 * to produce a meaningful diagnosis.
 * Maps to HTTP 422 Unprocessable Entity.
 */
public class InsufficientEvidenceException extends RuntimeException {

    public InsufficientEvidenceException(String message) {
        super(message);
    }

    public InsufficientEvidenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
