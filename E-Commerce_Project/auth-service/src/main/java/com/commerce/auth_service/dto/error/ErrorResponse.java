package com.commerce.auth_service.dto.error;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;

import jakarta.servlet.http.HttpServletRequest;
import lombok.*;

// dto/error/ErrorResponse.java

@Getter
@Builder // lets us build it cleanly in the handler
public class ErrorResponse {

    private final LocalDateTime timestamp;
    private final int status;
    private final String error; // e.g. "Unauthorized"
    private final String message; // human-readable detail
    private final String path; // request URI

    // Static factory — used in GlobalExceptionHandler
    public static ErrorResponse of(HttpStatus httpStatus,
            String message,
            HttpServletRequest request) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(httpStatus.value())
                .error(httpStatus.getReasonPhrase()) // "Unauthorized", "Bad Request" …
                .message(message)
                .path(request.getRequestURI())
                .build();
    }
}