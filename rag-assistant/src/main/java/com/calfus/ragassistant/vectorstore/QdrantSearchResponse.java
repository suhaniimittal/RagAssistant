package com.calfus.ragassistant.vectorstore;

import java.util.List;

/** Shape of Qdrant's own /points/search response body -- only used to deserialize it. */
record QdrantSearchResponse(List<QdrantSearchResult> result) {
}
