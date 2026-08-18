package com.calfus.ragassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * No length rule on password here on purpose -- login must accept whatever
 * password an existing account was created with, even if the registration
 * password-length rule changes later. No @ToString, same reasoning as
 * RegisterRequest (this carries a raw password).
 */
@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "Identifier is required")
    private String identifier;

    @NotBlank(message = "Password is required")
    private String password;
}
