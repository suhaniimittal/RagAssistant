package com.calfus.ragassistant.exception;

import org.springframework.http.HttpStatus;

/**
 * Carries an HTTP status alongside a plain-text message for auth failures
 * (bad identifier, duplicate account, wrong credentials). AuthController
 * catches this and turns it back into a ResponseEntity<String>, which keeps
 * the existing response shape (plain text body) instead of switching to
 * Spring's default JSON error body.
 */
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    public AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
