package com.calfus.ragassistant.controller;

import com.calfus.ragassistant.dto.UserProfileResponse;
import com.calfus.ragassistant.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * User-resource endpoints (as opposed to auth actions). Currently just the
 * profile lookup React calls on every page load/refresh.
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Spring Security's anyRequest().authenticated() rule (SecurityConfig)
     * already blocks this without a valid cookie, and JwtAuthFilter is what
     * populates `authentication` from that cookie's userId -- the null/empty
     * checks below are just defensive.
     */
    @GetMapping
    public ResponseEntity<UserProfileResponse> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        UUID userId = (UUID) authentication.getPrincipal();
        return userService.getProfile(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
