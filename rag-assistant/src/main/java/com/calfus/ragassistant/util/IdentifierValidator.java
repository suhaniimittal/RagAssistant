package com.calfus.ragassistant.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates and normalizes the "identifier" field, which can be EITHER a
 * plain username OR an email address. Decides which rule to apply based on
 * whether an "@" is present.
 */
public class IdentifierValidator {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9_]{3,20}$");

    /**
     * Trims whitespace, and lowercases the identifier IF it's an email
     * (email addresses are conventionally treated as case-insensitive;
     * usernames are left exactly as typed, since their casing is meaningful
     * and the existing username rules already constrain their charset).
     *
     * Call this BEFORE isValid()/existsByIdentifier() so "User@Example.com"
     * and "user@example.com" are treated as the same account -- otherwise
     * duplicate-identifier checks and login lookups could miss a match
     * purely due to casing.
     */
    public static String normalize(String identifier) {
        if (identifier == null) {
            return null;
        }
        String trimmed = identifier.trim();
        return trimmed.contains("@") ? trimmed.toLowerCase(Locale.ROOT) : trimmed;
    }

    public static boolean isValid(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        if (identifier.contains("@")) {
            return EMAIL_PATTERN.matcher(identifier).matches();
        }
        return USERNAME_PATTERN.matcher(identifier).matches();
    }

    public static String validationMessage() {
        return "Identifier must be a valid email address, or a username of 3-20 letters/numbers/underscores";
    }
}
