package com.calfus.ragassistant.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per registered account. "identifier" is deliberately generic --
 * it holds either a plain username or a full email address, since both are
 * accepted at registration/login (validated/normalized via IdentifierValidator).
 *
 * Only @Getter/@Setter here on purpose -- no @Data/@ToString/@EqualsAndHashCode:
 * this entity holds passwordHash, and a generated toString() would happily
 * print it straight into logs; a generated equals()/hashCode() on a JPA
 * entity is also generally unsafe (id is null until persisted, and it can
 * misbehave across lazy proxies), so it's left to default Object identity.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String identifier;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
