package com.commerce.auth_service.exception;

import org.springframework.http.HttpStatus;

// exception/BaseApiException.java

/**
 * All custom exceptions extend this.
 * The handler catches this type as a fallback,
 * so you never need to add every subclass to the handler individually
 * if you add more exceptions later.
 */
public abstract class BaseApiException extends RuntimeException {

    private final HttpStatus status;

    protected BaseApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}