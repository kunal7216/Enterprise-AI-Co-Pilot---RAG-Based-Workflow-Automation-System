package com.enterprise.copilot.exception;

/**
 * Thrown when a requested entity is not found in the database.
 * HTTP 404 mapping is handled by GlobalExceptionHandler.
 *
 * ⚠ DO NOT add @ResponseStatus here — it bypasses GlobalExceptionHandler
 *   and returns an empty body instead of our ApiErrorResponse JSON.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /** Convenience: "User not found with id: 5" */
    public ResourceNotFoundException(String entityName, Long id) {
        super(entityName + " not found with id: " + id);
    }

    /** Convenience: "User not found with username: john" */
    public ResourceNotFoundException(String entityName, String field, String value) {
        super(entityName + " not found with " + field + ": " + value);
    }
}