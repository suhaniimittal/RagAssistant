package com.calfus.ragassistant.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * Runs on EVERY incoming request (that's how Spring Security filters work --
 * one chain, applied to all endpoints). Its only job: look for our JWT in
 * the request's cookies (not an Authorization header, since we chose the
 * cookie-based design), validate it, and if valid, tell Spring Security
 * "this request belongs to this userId" so controllers can rely on it.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    // public so AuthController can build/clear the exact same cookie name
    // without the literal "auth_token" being duplicated in two places.
    public static final String COOKIE_NAME = "auth_token";

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = extractTokenFromCookie(request);

        if (token != null) {
            try {
                UUID userId = jwtUtil.validateAndGetUserId(token);

                // No password/roles needed here -- just marking "this userId
                // is authenticated" so @GetMapping("/me") etc. can read it.
                var authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception invalidOrExpiredToken) {
                // Bad/expired/tampered token -- treat as "not logged in"
                // rather than throwing, so the request still reaches the
                // controller and gets a clean 401 from there if needed.
                SecurityContextHolder.clearContext();
            }
        }

        // Always continue the chain -- whether or not a valid token was
        // found. Endpoints that require auth will reject unauthenticated
        // requests themselves (configured in SecurityConfig).
        filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
