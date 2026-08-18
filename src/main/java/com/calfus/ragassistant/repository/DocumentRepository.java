package com.calfus.ragassistant.repository;

import com.calfus.ragassistant.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByUserIdOrderByUploadedAtDesc(UUID userId); //used by GET /api/documents endpoint-Find all documents belonging to this user and order them by upload time, newest first.

    /** Ownership-scoped lookup -- one user can never fetch another's document by id. */
    Optional<Document> findByIdAndUserId(UUID id, UUID userId);
}