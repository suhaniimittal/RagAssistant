package com.calfus.ragassistant.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /** BCrypt is what actually hashes passwords before they hit PostgreSQL. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF is about a browser being tricked into firing a request
            // that rides on credentials it already holds for this site.
            // We ARE cookie-authenticated, so that risk exists regardless of
            // origin -- but the cookie is set with SameSite=Lax (see
            // AuthController), which is what actually stops browsers from
            // attaching it to cross-site requests in the first place.
            // Spring's CSRF-token mechanism is built for classic session-
            // cookie + server-rendered-form apps: adopting it would mean the
            // React app also has to read an XSRF-TOKEN cookie and echo it
            // back as a header on every call, for no real gain on top of
            // SameSite given today's endpoints -- register/login require no
            // prior auth to attack, logout only clears a cookie, and the one
            // authenticated endpoint (/api/user) is a read-only GET with no
            // side effect. Revisit this (add real CSRF tokens) if a
            // state-changing authenticated endpoint (e.g. saving chat
            // history) is added later.
            .csrf(AbstractHttpConfigurer::disable)
            // No server-side HTTP session -- the JWT cookie IS the session.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
                // Everything else under /api/** needs a valid cookie (this
                // is what actually protects /api/user, and any future
                // authenticated endpoint under /api/**).
                .requestMatchers("/api/**").authenticated()
                // The React app itself (index.html, JS/CSS bundles, and its
                // client-side routes like /login or /dashboard) is served as
                // plain static files by Spring Boot now that both live in
                // one app -- it has to be reachable before anyone is logged
                // in, so it stays public. The actual protection is still the
                // API: ProtectedRoute in React is just a UX redirect, not a
                // security boundary, and every real check happens above on
                // /api/**.
                .anyRequest().permitAll()
            )
            // Our custom filter runs before Spring's default username/password
            // filter, so by the time any endpoint logic runs, the userId (if
            // any) is already resolved from the cookie.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
