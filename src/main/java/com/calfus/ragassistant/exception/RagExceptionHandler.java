package com.calfus.ragassistant.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Shared by DocumentController and ChatController (unlike AuthController,
 * which handles its own exceptions locally -- here it's two controllers that
 * would otherwise need the exact same two handler methods duplicated).
 * Keeps error responses as plain text, same convention as the auth endpoints,
 * instead of Spring's default JSON error body.
 */
@RestControllerAdvice
public class RagExceptionHandler {

    @ExceptionHandler(RagException.class)
    public ResponseEntity<String> handleRagException(RagException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(message);
    }
}
