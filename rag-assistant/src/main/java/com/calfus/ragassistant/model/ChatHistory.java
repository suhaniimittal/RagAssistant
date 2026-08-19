package com.calfus.ragassistant.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per question/answer pair. Used as this app's "chat memory" -- when
 * the user asks a follow-up question, ChatService looks up earlier rows for
 * the SAME sessionId so it can understand what "it" or "that" refers to.
 *
 * sessionId groups turns into one conversation: the frontend generates a
 * fresh random id each time the Dashboard page loads (see Dashboard.jsx),
 * so refreshing the page starts a new conversation, but asking several
 * questions in a row without refreshing keeps them linked together.
 *
 * The table itself is not hand-written in a schema.sql; spring.jpa.hibernate.ddl-auto=update
 * (application.yml) creates/updates it straight from this entity and User on startup.
 */
@Entity
@Table(name = "chat_history")
@Getter
@Setter
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    // Mirrors the ON DELETE CASCADE the old schema.sql had: deleting a user
    // deletes their chat history with them at the database level, not just
    // by remembering to do it in application code.
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    // Not a foreign key to anything -- just a random string the frontend
    // makes up per browser session, used purely to group rows together.
    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
