package com.roottrace.knowledge.retrieval;

import com.roottrace.knowledge.KnowledgeChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Performs full-text search against the knowledge_chunks.search_vector column.
 * Uses PostgreSQL's websearch_to_tsquery to support natural language queries
 * while preserving technical identifiers like NullPointerException, HikariPool-1.
 */
@Service
public class FullTextSearchService {

    private static final Logger logger = LoggerFactory.getLogger(FullTextSearchService.class);

    private final KnowledgeChunkRepository chunkRepository;

    public FullTextSearchService(KnowledgeChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    /**
     * Searches using PostgreSQL full-text search.
     * websearch_to_tsquery handles both natural language and technical terms:
     * - Quoted phrases: "HikariPool-1"
     * - Negation: -error
     * - OR: term1 OR term2
     * Technical identifiers like "HikariPool-1" are preserved because PostgreSQL's
     * simple/english parsers index hyphenated tokens as sub-tokens.
     *
     * @param query text query (may include technical terms)
     * @param limit maximum number of results
     * @return ranked list of retrieved chunks
     */
    @Transactional(readOnly = true)
    public List<RetrievedChunk> search(String query, int limit) {
        logger.debug("FTS search: query='{}', limit={}", query, limit);

        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<Object[]> rows = chunkRepository.fullTextSearch(query.trim(), limit);
        List<RetrievedChunk> results = new ArrayList<>();
        for (Object[] row : rows) {
            results.add(mapRow(row));
        }

        logger.debug("FTS search returned {} results", results.size());
        return results;
    }

    private RetrievedChunk mapRow(Object[] row) {
        UUID chunkId    = UUID.fromString(row[0].toString());
        UUID documentId  = UUID.fromString(row[1].toString());
        String docTitle  = row[2] != null ? row[2].toString() : "";
        String secPath   = row[3] != null ? row[3].toString() : "";
        String content   = row[4] != null ? row[4].toString() : "";
        double score     = row[5] != null ? ((Number) row[5]).doubleValue() : 0.0;
        return new RetrievedChunk(chunkId, documentId, docTitle, secPath, content, score);
    }
}
