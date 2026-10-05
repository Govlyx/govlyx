package com.JanSahayak.AI.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource (Post, SocialPost, Comment, User, etc.)
 * cannot be found in the database.
 *
 * Automatically maps to HTTP 404 Not Found via GlobalExceptionHandler.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceName;
    private final String fieldName;
    private final Object fieldValue;

    // ── Constructor 1: simple message (used in controllers/services) ──────────
    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceName = null;
        this.fieldName    = null;
        this.fieldValue   = null;
    }

    // ── Constructor 2: structured — resource + field + value ─────────────────
    // Generates a consistent message: "Post not found with id: 42"
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName    = fieldName;
        this.fieldValue   = fieldValue;
    }

    // ── Constructor 3: with cause ─────────────────────────────────────────────
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.resourceName = null;
        this.fieldName    = null;
        this.fieldValue   = null;
    }

    public String getResourceName() { return resourceName; }
    public String getFieldName()    { return fieldName; }
    public Object getFieldValue()   { return fieldValue; }
}
