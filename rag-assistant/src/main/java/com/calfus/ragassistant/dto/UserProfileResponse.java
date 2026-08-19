package com.calfus.ragassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * What GET /api/user actually returns -- never the User entity itself, so
 * passwordHash and any future internal-only fields never risk being
 * serialized to the frontend. No sensitive fields here, so @ToString would
 * be harmless, but it's left off since nothing needs it; kept immutable
 * (no setters) since this is a read-only response shape.
 */
@Getter
@AllArgsConstructor
public class UserProfileResponse {

    private final UUID id;
    private final String identifier;
}
