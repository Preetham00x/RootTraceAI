-- =============================================================================
-- V6: Incident embeddings for semantic similarity search
-- =============================================================================

-- Add embedding column to incidents table
-- 768 dimensions matches text-embedding-004 (same as knowledge_chunks)
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS embedding vector(768);

-- HNSW index for fast approximate nearest-neighbor on non-deleted incidents
-- Only active (non-deleted) incidents participate in similarity search
CREATE INDEX IF NOT EXISTS idx_incidents_embedding
    ON incidents USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64)
    WHERE deleted_at IS NULL;
