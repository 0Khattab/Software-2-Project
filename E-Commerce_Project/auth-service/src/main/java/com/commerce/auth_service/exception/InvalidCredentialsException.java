package com.commerce.auth_service.exception;

import org.springframework.http.HttpStatus;

// exception/InvalidCredentialsException.java

public class InvalidCredentialsException extends BaseApiException {

    public InvalidCredentialsException() {
        super("Invalid email or password", HttpStatus.UNAUTHORIZED);
    }

    // Overload if you ever need a custom message
    public InvalidCredentialsException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
