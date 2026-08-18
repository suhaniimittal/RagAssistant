package com.calfus.ragassistant.controller;

import com.calfus.ragassistant.dto.DocumentResponse;
import com.calfus.ragassistant.service.DocumentIngestionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Upload PDFs and list what a user has already uploaded. Asking questions about them is ChatController's job. */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    public DocumentController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        var document = ingestionService.ingest(userId, file);
        return ResponseEntity.ok(DocumentResponse.from(document));  //This converts the internal document/entity into a DTO suitable for the API response.
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> list(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        List<DocumentResponse> documents = ingestionService.listForUser(userId).stream()
                .map(DocumentResponse::from)
                .toList();
        return ResponseEntity.ok(documents);
    }
}