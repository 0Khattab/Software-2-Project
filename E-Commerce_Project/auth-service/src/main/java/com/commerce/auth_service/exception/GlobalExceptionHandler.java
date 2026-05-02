package com.commerce.auth_service.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.commerce.auth_service.dto.error.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

// exception/GlobalExceptionHandler.java

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ─── 1. Handle each custom exception individually ────────────────────────

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex,
            HttpServletRequest request) {

        log.warn("Authentication failed at {}: {}", request.getRequestURI(), ex.getMessage());

        return buildResponse(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex,
            HttpServletRequest request) {

        log.warn("Duplicate email attempt at {}: {}", request.getRequestURI(), ex.getMessage());

        return buildResponse(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(TokenInvalidException.class)
    public ResponseEntity<ErrorResponse> handleTokenInvalid(
            TokenInvalidException ex,
            HttpServletRequest request) {

        log.warn("Token validation failed at {}: {}", request.getRequestURI(), ex.getMessage());

        return buildResponse(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Resource not found at {}: {}", request.getRequestURI(), ex.getMessage());

        return buildResponse(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
            ForbiddenException ex,
            HttpServletRequest request) {

        log.warn("Forbidden access at {}: {}", request.getRequestURI(), ex.getMessage());

        return buildResponse(ex.getStatus(), ex.getMessage(), request);
    }

    // ─── 2. Fallback for ALL other BaseApiException subclasses ───────────────
    // Any new custom exception you add gets handled here automatically

    @ExceptionHandler(BaseApiException.class)
    public ResponseEntity<ErrorResponse> handleBaseApiException(
            BaseApiException ex,
            HttpServletRequest request) {

        log.warn("API exception at {}: {}", request.getRequestURI(), ex.getMessage());

        return buildResponse(ex.getStatus(), ex.getMessage(), request);
    }

    // ─── 3. Handle Spring validation errors (@Valid on DTOs) ─────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        // Collect all field errors into one message
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation failed at {}: {}", request.getRequestURI(), message);

        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    // ─── 4. Catch-all — unexpected exceptions ────────────────────────────────
    // Hides internal details from the client (security best practice)

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        // Log the full stack trace internally — never expose it to the client
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.",
                request);
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotActive(
            AccountNotActiveException ex,
            HttpServletRequest request) {

        log.warn("Blocked login attempt at {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(ex.getStatus(), ex.getMessage(), request);
    }

    // ─── Private helper ───────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status,
            String message,
            HttpServletRequest request) {
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(status, message, request));
    }
}