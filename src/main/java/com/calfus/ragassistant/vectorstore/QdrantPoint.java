package com.calfus.ragassistant.vectorstore;

import java.util.List;
import java.util.Map;

/** One chunk's embedding + metadata, ready to upsert into Qdrant. */
public record QdrantPoint(String id, List<Float> vector, Map<String, Object> payload) {
}
