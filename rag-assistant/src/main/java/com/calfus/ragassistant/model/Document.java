package com.calfus.ragassistant.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per uploaded PDF, scoped to the user who uploaded it. The actual
 * searchable content (chunks + embeddings) lives in Qdrant, not here -- this
 * table is just the "what did this user upload, and what's its status"
 * record, plus the original file's path on disk so it can be looked up
 * again later if needed.
 *
 * Same Lombok choice as User/ChatHistory: @Getter/@Setter only, no
 * @ToString/@EqualsAndHashCode (lazy `user` association).
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(nullable = false)
    private String filename;

    // NOT marked nullable = false on purpose: DocumentIngestionService saves
    // this row once right away (status=PROCESSING, no path yet) before it
    // has actually written the file to disk and knows the path -- so this
    // column is genuinely empty for a brief moment. It gets filled in with a
    // second save() a few lines later, once the file is actually on disk.
    @Column(name = "stored_path")
    private String storedPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();
}
