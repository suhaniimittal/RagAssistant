package com.calfus.ragassistant.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * NOTE: deliberately no @ToString / @EqualsAndHashCode here. This DTO carries
 * a raw password + confirmPassword, and a Lombok-generated toString() would
 * print both of them in plain text the moment this object ends up in a log
 * line (e.g. an unhandled exception logging its request body).
 */
@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Identifier is required")
    private String identifier;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;

    /**
     * Bean Validation calls any method named isXxx()/getXxx() that returns
     * boolean and treats it as a synthetic property -- so @AssertTrue here
     * is enough to get "passwords must match" enforced as part of the same
     * @Valid check that already runs on @NotBlank/@Size, with no separate
     * annotation or validator class needed for a rule this small.
     */
    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordsMatching() {
        if (password == null || confirmPassword == null) {
            return true; // let @NotBlank report missing fields on their own
        }
        return password.equals(confirmPassword);
    }
}
