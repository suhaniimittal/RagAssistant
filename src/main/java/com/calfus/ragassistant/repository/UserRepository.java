package com.calfus.ragassistant.repository;

import com.calfus.ragassistant.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByIdentifier(String identifier);
    boolean existsByIdentifier(String identifier);
}
