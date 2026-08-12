package com.roottrace.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {

    void deleteByDocumentId(UUID documentId);

    int countByDocumentId(UUID documentId);

    /**
     * Semantic vector search using cosine similarity (pgvector).
     * Only retrieves chunks from READY, non-deleted documents.
     * Returns: chunk_id, document_id, document_title, section_path, content, similarity_score
     */
    @Query(value = """
            SELECT kc.id           AS chunk_id,
                   kc.document_id  AS document_id,
                   kc.document_title AS document_title,
                   kc.section_path AS section_path,
                   kc.content      AS content,
                   (1.0 - (kc.embedding <=> CAST(:queryEmbedding AS vector))) AS similarity
            FROM knowledge_chunks kc
            JOIN knowledge_documents kd ON kd.id = kc.document_id
            WHERE kd.status = 'READY'
              AND kd.deleted_at IS NULL
              AND kc.embedding IS NOT NULL
            ORDER BY kc.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> semanticSearch(
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit
    );

    /**
     * Full-text search using PostgreSQL tsvector.
     * Uses websearch_to_tsquery for natural language + plainto_tsquery for technical terms.
     * Only retrieves chunks from READY, non-deleted documents.
     */
    @Query(value = """
            SELECT kc.id            AS chunk_id,
                   kc.document_id   AS document_id,
                   kc.document_title AS document_title,
                   kc.section_path  AS section_path,
                   kc.content       AS content,
                   ts_rank_cd(kc.search_vector, query) AS rank_score
            FROM knowledge_chunks kc
            JOIN knowledge_documents kd ON kd.id = kc.document_id,
                 websearch_to_tsquery('english', :query) query
            WHERE kd.status = 'READY'
              AND kd.deleted_at IS NULL
              AND kc.search_vector @@ query
            ORDER BY rank_score DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> fullTextSearch(
            @Param("query") String query,
            @Param("limit") int limit
    );
}
