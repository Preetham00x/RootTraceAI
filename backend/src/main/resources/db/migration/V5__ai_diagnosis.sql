-- =============================================================================
-- V5: AI Diagnosis — diagnosis, evidence, citations, feedback tables
-- =============================================================================

-- AI diagnoses generated for incidents
CREATE TABLE ai_diagnoses (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id         UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    summary             TEXT NOT NULL,
    probable_root_cause TEXT NOT NULL,
    confidence          NUMERIC(4, 3) NOT NULL CHECK (confidence >= 0.0 AND confidence <= 1.0),
    contributing_factors JSONB NOT NULL DEFAULT '[]',
    recommended_actions  JSONB NOT NULL DEFAULT '[]',
    created_by_id       UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_diagnoses_incident_id ON ai_diagnoses (incident_id);
CREATE INDEX idx_ai_diagnoses_created_at  ON ai_diagnoses (created_at);
CREATE INDEX idx_ai_diagnoses_created_by  ON ai_diagnoses (created_by_id);

-- Evidence records linking diagnoses to specific knowledge chunks used as context
CREATE TABLE ai_diagnosis_evidence (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    diagnosis_id    UUID NOT NULL REFERENCES ai_diagnoses(id) ON DELETE CASCADE,
    chunk_id        UUID NOT NULL REFERENCES knowledge_chunks(id) ON DELETE CASCADE,
    relevance_score NUMERIC(5, 4),
    reason          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_diagnosis_evidence_diagnosis ON ai_diagnosis_evidence (diagnosis_id);

-- Citation records linking diagnoses to knowledge documents
CREATE TABLE ai_diagnosis_citations (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    diagnosis_id   UUID NOT NULL REFERENCES ai_diagnoses(id) ON DELETE CASCADE,
    document_id    UUID NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
    chunk_id       UUID REFERENCES knowledge_chunks(id) ON DELETE SET NULL,
    document_title VARCHAR(500),
    section_path   TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_diagnosis_citations_diagnosis ON ai_diagnosis_citations (diagnosis_id);
CREATE INDEX idx_ai_diagnosis_citations_document  ON ai_diagnosis_citations (document_id);

-- Feedback on AI diagnoses from users
CREATE TABLE ai_diagnosis_feedback (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    diagnosis_id UUID NOT NULL REFERENCES ai_diagnoses(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users(id),
    helpful      BOOLEAN NOT NULL,
    comment      TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_feedback_diagnosis_user UNIQUE (diagnosis_id, user_id)
);

CREATE INDEX idx_ai_diagnosis_feedback_diagnosis ON ai_diagnosis_feedback (diagnosis_id);
CREATE INDEX idx_ai_diagnosis_feedback_user      ON ai_diagnosis_feedback (user_id);
