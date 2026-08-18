package com.calfus.ragassistant.controller;

import com.calfus.ragassistant.dto.LoginRequest;
import com.calfus.ragassistant.dto.RegisterRequest;
import com.calfus.ragassistant.exception.AuthException;
import com.calfus.ragassistant.security.JwtAuthFilter;
import com.calfus.ragassistant.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Authentication endpoints only -- register/login/logout. All business logic
 * (validation, password hashing, token issuance) lives in AuthService; this
 * controller's job is translating HTTP <-> service calls and managing the
 * auth cookie.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    // true in production (HTTPS); must stay false for local http://localhost
    // dev, since browsers silently drop a Secure cookie sent over plain HTTP.
    @Value("${app.security.cookie-secure:false}")
    private boolean cookieSecure;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok("Registration successful. Please log in.");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        String token = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildAuthCookie(token, Duration.ofMillis(jwtExpirationMs)).toString());
        return ResponseEntity.ok("Login successful");
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletResponse response) {
        // Always succeeds, even if the caller wasn't actually logged in --
        // logout just means "make sure this cookie is gone", not a protected
        // action, so it doesn't need to require authentication.
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildAuthCookie("", Duration.ZERO).toString());
        return ResponseEntity.ok("Logged out");
    }

    /**
     * Builds the auth cookie with the attributes shared by login (sets it)
     * and logout (clears it -- same attributes, maxAge=0). Browsers only
     * delete a cookie if the clearing Set-Cookie matches Path/SameSite/Secure,
     * so these MUST stay identical between the two call sites; that's why
     * both go through this one method instead of building cookies separately.
     */
    private ResponseCookie buildAuthCookie(String value, Duration maxAge) {
        return ResponseCookie.from(JwtAuthFilter.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<String> handleAuthException(AuthException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ex.getMessage());
    }

    /**
     * Keeps validation failures (blank fields, password mismatch, etc.) in
     * the same plain-text response shape as every other error here, instead
     * of falling back to Spring's default JSON validation-error body.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(message);
    }
}
