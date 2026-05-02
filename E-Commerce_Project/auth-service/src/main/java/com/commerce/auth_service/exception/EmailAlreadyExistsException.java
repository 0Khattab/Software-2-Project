package com.commerce.auth_service.exception;

import org.springframework.http.HttpStatus;

// exception/EmailAlreadyExistsException.java

public class EmailAlreadyExistsException extends BaseApiException {

    public EmailAlreadyExistsException(String email) {
        super("Email is already registered: " + email, HttpStatus.CONFLICT);
    }
}
