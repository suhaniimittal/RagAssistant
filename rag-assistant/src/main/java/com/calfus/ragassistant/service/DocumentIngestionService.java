package com.calfus.ragassistant.service;

import com.calfus.ragassistant.exception.RagException;
import com.calfus.ragassistant.model.Document;
import com.calfus.ragassistant.model.DocumentStatus;
import com.calfus.ragassistant.model.User;
import com.calfus.ragassistant.repository.DocumentRepository;
import com.calfus.ragassistant.repository.UserRepository;
import com.calfus.ragassistant.vectorstore.QdrantClient;
import com.calfus.ragassistant.vectorstore.QdrantPoint;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The whole ingestion pipeline in one place: PDF upload -> parse/clean
 * (existing Step 1-3 code) -> chunk -> embed -> store in Qdrant. This is
 * Steps 4-6 of the mission's pipeline, built on top of the already-verified
 * PdfIngestionService rather than re-doing parsing here.
 */
@Service
public class DocumentIngestionService {

    // Ceiling per chunk, and overlap between consecutive chunks, both in
    // tokens -- DocumentSplitters.recursive splits at paragraph/sentence/word
    // boundaries under this ceiling, it's not a fixed chunk size.
    private static final int MAX_CHUNK_TOKENS = 500;
    private static final int CHUNK_OVERLAP_TOKENS = 50;

    private final PdfIngestionService pdfIngestionService;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final EmbeddingModel embeddingModel;
    private final QdrantClient qdrantClient;
    private final String uploadDir;

    public DocumentIngestionService(
            PdfIngestionService pdfIngestionService,
            DocumentRepository documentRepository,
            UserRepository userRepository,
            EmbeddingModel embeddingModel,
            QdrantClient qdrantClient,
            @Value("${app.upload-dir}") String uploadDir) {
        this.pdfIngestionService = pdfIngestionService;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.embeddingModel = embeddingModel;
        this.qdrantClient = qdrantClient;
        this.uploadDir = uploadDir;
    }

    public Document ingest(UUID userId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new RagException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new RagException(HttpStatus.BAD_REQUEST, "Only PDF files are supported");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RagException(HttpStatus.UNAUTHORIZED, "User not found"));

        Document document = new Document();
        document.setUser(user);
        document.setFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf");
        document.setStatus(DocumentStatus.PROCESSING);
        document = documentRepository.save(document);

        File savedFile;
        try {
            savedFile = saveToDisk(userId, document.getId(), file);
            document.setStoredPath(savedFile.getPath());
        } catch (IOException e) {
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage("Could not save the uploaded file: " + e.getMessage());
            return documentRepository.save(document);
        }

        try {
            // Kept per-page (not merged into one string) so every chunk can be
            // tagged with the page it actually came from, for citations.
            List<String> cleanedPages = pdfIngestionService.parseAndCleanPerPage(savedFile);

            DocumentSplitter splitter = DocumentSplitters.recursive(MAX_CHUNK_TOKENS, CHUNK_OVERLAP_TOKENS);

            // Split each page separately, remembering which page every
            // resulting segment came from (chunks never span two pages this
            // way -- a simple, reliable trade-off for accurate page citations).
            List<TextSegment> segments = new ArrayList<>();
            List<Integer> segmentPageNumbers = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < cleanedPages.size(); pageIndex++) {
                String pageText = cleanedPages.get(pageIndex);
                if (pageText.isBlank()) {
                    continue;
                }
                // Fully-qualified on purpose -- this is LangChain4j's Document
                // type, distinct from our own com.calfus.ragassistant.model.Document.
                List<TextSegment> pageSegments =
                        splitter.split(dev.langchain4j.data.document.Document.from(pageText));
                for (TextSegment segment : pageSegments) {
                    segments.add(segment);
                    segmentPageNumbers.add(pageIndex + 1); // pages are 1-indexed for humans
                }
            }

            if (segments.isEmpty()) {
                throw new IllegalStateException("No extractable text found in this PDF");
            }

            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

            List<QdrantPoint> points = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("userId", userId);
                payload.put("documentId", document.getId());
                payload.put("filename", document.getFilename());
                payload.put("chunkIndex", i);
                payload.put("pageNumber", segmentPageNumbers.get(i));
                payload.put("text", segments.get(i).text());

                points.add(new QdrantPoint(
                        UUID.randomUUID().toString(),
                        toFloatList(embeddings.get(i).vector()),
                        payload));
            }
            qdrantClient.upsertPoints(points);

            document.setStatus(DocumentStatus.READY);
            document.setChunkCount(segments.size());
        } catch (Throwable e) {
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }

        return documentRepository.save(document);
    }

    public List<Document> listForUser(UUID userId) {
        return documentRepository.findByUserIdOrderByUploadedAtDesc(userId);
    }

    public void delete(UUID userId, UUID documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new RagException(HttpStatus.NOT_FOUND, "Document not found"));

        qdrantClient.deleteByDocumentId(documentId);

        if (document.getStoredPath() != null) {
            try {
                Files.deleteIfExists(Path.of(document.getStoredPath()));
            } catch (IOException e) {
                // Not fatal -- a leftover file on disk is harmless clutter,
                // not something worth failing the whole delete over.
            }
        }

        documentRepository.delete(document);
    }

    private File saveToDisk(UUID userId, UUID documentId, MultipartFile file) throws IOException {
        Path userDir = Path.of(uploadDir, "user-" + userId);
        Files.createDirectories(userDir);
        Path target = userDir.resolve("doc-" + documentId + ".pdf");
        file.transferTo(target);
        return target.toFile();
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float value : vector) {
            list.add(value);
        }
        return list;
    }
}
