package com.roottrace.ai.exception;

public class AiUnavailableException extends AiServiceException {
    public AiUnavailableException(String message) {
        super(message);
    }
    
    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
