package com.calfus.ragassistant.exception;

import org.springframework.http.HttpStatus;

/**
 * Same purpose as AuthException, kept as a separate class rather than
 * reusing/renaming that one -- this is for the document/chat endpoints
 * specifically, and there's no reason to couple the two feature areas
 * together just to share one exception type.
 */
public class RagException extends RuntimeException {

    private final HttpStatus status;

    public RagException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
