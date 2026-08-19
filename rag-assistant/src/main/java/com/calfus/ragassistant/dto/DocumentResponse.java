package com.calfus.ragassistant.dto;

import com.calfus.ragassistant.model.Document;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What the document endpoints actually return -- never the Document entity
 * itself (that would drag along the lazy `user` association and the disk
 * path, neither of which the frontend needs).
 */
@Getter
@AllArgsConstructor
public class DocumentResponse {

    private final UUID id;
    private final String filename;
    private final String status;
    private final Integer chunkCount;
    private final String errorMessage;
    private final LocalDateTime uploadedAt;

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getFilename(),
                document.getStatus().name(),
                document.getChunkCount(),
                document.getErrorMessage(),
                document.getUploadedAt());
    }
}
