package com.enterprise.copilot.exception;

import com.enterprise.copilot.Dto.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        log.debug("404 - Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), null);
    }

    // ── 409 Conflict ──────────────────────────────────────────────────────────

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidState(InvalidStateException ex) {
        log.debug("409 - Invalid state: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage(), null);
    }

    // ── 400 Validation failures ───────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (first, second) -> first
                ));

        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed — check fieldErrors for details",
                fieldErrors
        );
    }

    // ── 400 Wrong type / enum mismatch ────────────────────────────────────────

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = String.format(
                "Parameter '%s' must be of type %s",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
        );
        return build(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", msg, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("400 - Illegal argument: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), null);
    }

    // ── 400 Missing request param ─────────────────────────────────────────────

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(
                HttpStatus.BAD_REQUEST,
                "MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing",
                null
        );
    }

    // ── 401 Unauthorized ──────────────────────────────────────────────────────

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ApiErrorResponse> handleAuth(Exception ex) {
        return build(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Authentication failed: invalid username or password",
                null
        );
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(AccessDeniedException ex) {
        return build(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "You do not have permission to perform this action",
                null
        );
    }

    // ── 413 File too large ────────────────────────────────────────────────────

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleFileSize(MaxUploadSizeExceededException ex) {
        return build(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_TOO_LARGE",
                "Uploaded file exceeds the 50 MB limit",
                null
        );
    }

    // ── 415 Unsupported file type ─────────────────────────────────────────────

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupported(UnsupportedOperationException ex) {
        return build(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_FORMAT",
                ex.getMessage(),
                null
        );
    }

    // ── 502/500 AI or downstream failures ─────────────────────────────────────
    // Your Ollama parsing / downstream API failures often surface as RuntimeException

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(RuntimeException ex) {
        log.error("Runtime exception: {}", ex.getMessage(), ex);

        String msg = ex.getMessage() != null ? ex.getMessage() : "Runtime failure";

        if (msg.contains("Ollama")
                || msg.contains("JSON parse failed")
                || msg.contains("Embedding API failed")
                || msg.contains("Invalid embedding response")
                || msg.contains("Failed to parse Ollama response")) {

            return build(
                    HttpStatus.BAD_GATEWAY,
                    "AI_SERVICE_ERROR",
                    "AI processing service is temporarily unavailable or returned an invalid response.",
                    null
            );
        }

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.",
                null
        );
    }

    // ── 500 Catch-all ─────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAll(Exception ex) {
        log.error("500 - Unhandled [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.",
                null
        );
    }

    // ─── Private builder ──────────────────────────────────────────────────────

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String error,
            String message,
            Map<String, String> fieldErrors) {

        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.builder()
                        .status(status.value())
                        .error(error)
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .fieldErrors(fieldErrors)
                        .build());
    }
}