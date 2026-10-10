package com.JanSahayak.AI.exception;

/**
 * Custom validation exception for application-specific validation errors
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
