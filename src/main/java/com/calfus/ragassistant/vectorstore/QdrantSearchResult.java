package com.calfus.ragassistant.vectorstore;

import java.util.Map;

/** One match returned from a Qdrant search -- its similarity score plus whatever payload was stored with it. */
public record QdrantSearchResult(String id, double score, Map<String, Object> payload) {
}
