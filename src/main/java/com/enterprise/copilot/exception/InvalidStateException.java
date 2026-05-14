package com.enterprise.copilot.exception;

/**
 * Thrown when a workflow action is invalid for its current state.
 * Example: approving a PENDING workflow (only ESCALATED can be reviewed).
 *
 * HTTP 409 CONFLICT mapping is handled by GlobalExceptionHandler.
 *
 * ⚠ DO NOT add @ResponseStatus here — it bypasses GlobalExceptionHandler.
 */
public class InvalidStateException extends RuntimeException {

    public InvalidStateException(String message) {
        super(message);
    }
}