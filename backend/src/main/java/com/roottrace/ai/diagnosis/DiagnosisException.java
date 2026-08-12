package com.roottrace.ai.diagnosis;

/**
 * Thrown when the AI diagnosis pipeline fails (Gemini call, structured output parsing, etc.).
 * Maps to HTTP 502 Bad Gateway.
 */
public class DiagnosisException extends RuntimeException {

    public DiagnosisException(String message) {
        super(message);
    }

    public DiagnosisException(String message, Throwable cause) {
        super(message, cause);
    }
}
