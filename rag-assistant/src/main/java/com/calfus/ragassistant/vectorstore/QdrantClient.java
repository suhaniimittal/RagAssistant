package com.calfus.ragassistant.vectorstore;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Talks to Qdrant directly over its REST API (per the original "Qdrant via
 * REST" design), rather than through a LangChain4j store module. Qdrant is
 * its own server process -- this class is just an HTTP client for it, the
 * same way JdbcTemplate/JPA are just clients for the separate Postgres
 * process. Locally that means: `docker run -p 6333:6333 qdrant/qdrant`
 * needs to actually be running before this app starts.
 *
 * All users' chunks live in ONE collection; isolation between users is
 * enforced by filtering every search on a "userId" field stored in each
 * point's payload (see search()) -- not by separate collections per user.
 *
 * Kept deliberately simple: just a dense VECTOR search. An earlier version
 * of this class also did a keyword search + result merging (hybrid search),
 * but that meant more Qdrant setup and more code for not much real benefit
 * on a small document set -- vector search alone already handles both exact
 * wording and "meaning" matches well enough for this project.
 */
@Component
public class QdrantClient {

    // Must match the output size of the embedding model configured in
    // AiModelsConfig (text-embedding-3-small -> 1536 dimensions). If that
    // model name ever changes, this needs to change too.
    private static final int VECTOR_SIZE = 1536;

    private final RestClient restClient;
    private final String collectionName;

    public QdrantClient(
            @Value("${qdrant.host}") String host,
            @Value("${qdrant.port}") int port,
            @Value("${qdrant.collection-name}") String collectionName) {
        this.restClient = RestClient.builder()
                .baseUrl("http://" + host + ":" + port)
                .build();
        this.collectionName = collectionName;
    }

    /**
     * Runs once on startup. Qdrant (unlike some other vector stores) doesn't
     * auto-create a collection the first time you write to it -- it has to
     * exist first, or every upload would fail on a brand new Qdrant instance.
     */
    @PostConstruct
    public void ensureCollectionExists() {
        try {
            restClient.get()
                    .uri("/collections/{name}", collectionName)
                    .retrieve()
                    .toBodilessEntity();
            // Collection already exists -- nothing to do.
        } catch (HttpClientErrorException.NotFound notFound) {
            restClient.put()
                    .uri("/collections/{name}", collectionName)
                    .body(Map.of("vectors", Map.of("size", VECTOR_SIZE, "distance", "Cosine")))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    public void upsertPoints(List<QdrantPoint> points) {
        if (points.isEmpty()) {
            return;
        }
        List<Map<String, Object>> pointBodies = points.stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.id(),
                        "vector", p.vector(),
                        "payload", p.payload()))
                .toList();

        restClient.put()
                .uri("/collections/{name}/points?wait=true", collectionName)
                .body(Map.of("points", pointBodies))
                .retrieve()
                .toBodilessEntity();
    }

    /** Deletes every point belonging to one document (used if a document upload fails partway through). */
    public void deleteByDocumentId(UUID documentId) {
        Map<String, Object> filter = Map.of(
                "must", List.of(Map.of("key", "documentId", "match", Map.of("value", documentId))));

        restClient.post()
                .uri("/collections/{name}/points/delete?wait=true", collectionName)
                .body(Map.of("filter", filter))
                .retrieve()
                .toBodilessEntity();
    }

    /** Vector similarity search, restricted to one user's own points via a payload filter. */
    public List<QdrantSearchResult> search(List<Float> queryVector, int limit, UUID userId) {
        Map<String, Object> filter = Map.of(
                "must", List.of(
                        Map.of(
                                "key", "userId",
                                "match", Map.of("value", userId)
                        )
                )
        );

        Map<String, Object> body = Map.of(
                "vector", queryVector,
                "limit", limit,
                "filter", filter,
                "with_payload", true);

        QdrantSearchResponse response = restClient.post()
                .uri("/collections/{name}/points/search", collectionName)
                .body(body)
                .retrieve()
                .body(QdrantSearchResponse.class);

        return response == null ? new ArrayList<>() : response.result();
    }
}