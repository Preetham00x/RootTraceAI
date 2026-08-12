-- =============================================================================
-- V3: Knowledge Ingestion schema
-- =============================================================================

CREATE TABLE knowledge_documents (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title               VARCHAR(500) NOT NULL,
    original_filename   VARCHAR(500) NOT NULL,
    source_type         VARCHAR(50)  NOT NULL,
    status              VARCHAR(50)  NOT NULL CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    created_by          UUID         NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_knowledge_documents_created_by ON knowledge_documents (created_by) WHERE deleted_at IS NULL;
CREATE INDEX idx_knowledge_documents_status ON knowledge_documents (status) WHERE deleted_at IS NULL;

CREATE TABLE knowledge_chunks (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id         UUID NOT NULL,
    chunk_index         INTEGER NOT NULL,
    content             TEXT NOT NULL,
    section_path        TEXT,
    embedding           vector(768),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_knowledge_chunks_document_id FOREIGN KEY (document_id) REFERENCES knowledge_documents (id) ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_chunks_document_id ON knowledge_chunks (document_id);
CREATE INDEX idx_knowledge_chunks_doc_idx ON knowledge_chunks (document_id, chunk_index);
