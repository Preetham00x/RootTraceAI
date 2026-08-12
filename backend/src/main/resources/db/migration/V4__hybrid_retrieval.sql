-- =============================================================================
-- V4: Hybrid retrieval — FTS + pgvector HNSW index
-- =============================================================================

-- Add denormalized document_title to knowledge_chunks for FTS without cross-table joins
ALTER TABLE knowledge_chunks ADD COLUMN IF NOT EXISTS document_title VARCHAR(500);

-- Backfill document_title from parent documents
UPDATE knowledge_chunks kc
SET document_title = kd.title
FROM knowledge_documents kd
WHERE kc.document_id = kd.id;

-- Add generated tsvector column for full-text search
-- Combines: document title + section path + content
-- English config preserves stop-word filtering while maintaining technical token support
ALTER TABLE knowledge_chunks
    ADD COLUMN IF NOT EXISTS search_vector tsvector
        GENERATED ALWAYS AS (
            to_tsvector('english',
                coalesce(document_title, '') || ' ' ||
                coalesce(section_path, '') || ' ' ||
                coalesce(content, '')
            )
        ) STORED;

-- GIN index for fast full-text search
CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_search
    ON knowledge_chunks USING GIN (search_vector);

-- HNSW index for approximate nearest-neighbor vector search (cosine similarity)
-- m=16: number of connections per layer (good balance of recall/speed)
-- ef_construction=64: dynamic candidate list size during build
CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_embedding
    ON knowledge_chunks USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Composite index to efficiently filter by document_id + status in retrieval
CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_doc_title
    ON knowledge_chunks (document_id, document_title);
