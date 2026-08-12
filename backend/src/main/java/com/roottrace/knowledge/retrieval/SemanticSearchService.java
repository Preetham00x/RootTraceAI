package com.roottrace.knowledge.retrieval;

import com.roottrace.ai.embedding.AiEmbeddingService;
import com.roottrace.knowledge.KnowledgeChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Performs semantic vector search using pgvector cosine similarity.
 * Generates query embeddings via the AI embedding abstraction (never directly).
 * Only returns chunks from READY, non-deleted documents.
 */
@Service
public class SemanticSearchService {

    private static final Logger logger = LoggerFactory.getLogger(SemanticSearchService.class);

    private final AiEmbeddingService embeddingService;
    private final KnowledgeChunkRepository chunkRepository;

    public SemanticSearchService(AiEmbeddingService embeddingService,
                                 KnowledgeChunkRepository chunkRepository) {
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
    }

    /**
     * Searches for semantically similar chunks.
     * The embedding call happens OUTSIDE any active transaction.
     *
     * @param query text query
     * @param limit maximum number of results
     * @return ranked list of retrieved chunks
     */
    @Transactional(readOnly = true)
    public List<RetrievedChunk> search(String query, int limit) {
        logger.debug("Semantic search: query='{}', limit={}", query, limit);

        float[] embedding = embeddingService.embed(query);
        String vectorString = toVectorString(embedding);

        List<Object[]> rows = chunkRepository.semanticSearch(vectorString, limit);
        List<RetrievedChunk> results = new ArrayList<>();
        for (Object[] row : rows) {
            results.add(mapRow(row));
        }

        logger.debug("Semantic search returned {} results", results.size());
        return results;
    }

    private RetrievedChunk mapRow(Object[] row) {
        UUID chunkId   = UUID.fromString(row[0].toString());
        UUID documentId = UUID.fromString(row[1].toString());
        String docTitle = row[2] != null ? row[2].toString() : "";
        String secPath  = row[3] != null ? row[3].toString() : "";
        String content  = row[4] != null ? row[4].toString() : "";
        double score    = row[5] != null ? ((Number) row[5]).doubleValue() : 0.0;
        return new RetrievedChunk(chunkId, documentId, docTitle, secPath, content, score);
    }

    /**
     * Converts a float[] embedding into the PostgreSQL vector literal format.
     * Example: [0.1,0.2,...,0.768]
     */
    static String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
