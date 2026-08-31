package com.schwab.notificationservice.api;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(this::formatFieldError)
                .orElse("Validation failed");
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedRequest(HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (ex.getMessage() != null) {
            if (ex.getMessage().startsWith("Notification not found")) {
                status = HttpStatus.NOT_FOUND;
            } else if (ex.getMessage().startsWith("Duplicate idempotency key")) {
                status = HttpStatus.CONFLICT;
            }
        }
        return buildResponse(status, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + " " + fieldError.getDefaultMessage();
    }

    private ResponseEntity<ApiError> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, Instant.now().toString()));
    }

    public record ApiError(int status, String error, String timestamp) {
    }
}
