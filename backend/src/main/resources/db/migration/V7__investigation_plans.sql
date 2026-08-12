-- =============================================================================
-- V7: Investigation Plans & Steps
-- =============================================================================

CREATE TABLE investigation_plans (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id         UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    source_diagnosis_id UUID REFERENCES ai_diagnoses(id) ON DELETE SET NULL,
    title               VARCHAR(500) NOT NULL,
    created_by_id       UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE investigation_steps (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_id        UUID NOT NULL REFERENCES investigation_plans(id) ON DELETE CASCADE,
    step_order     INT NOT NULL,
    title          VARCHAR(255) NOT NULL,
    description    TEXT NOT NULL,
    status         VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    evidence       TEXT,
    assigned_to_id UUID REFERENCES users(id) ON DELETE SET NULL,
    completed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_investigation_plans_incident_id ON investigation_plans (incident_id);
CREATE INDEX idx_investigation_steps_plan_id ON investigation_steps (plan_id);
CREATE INDEX idx_investigation_steps_status ON investigation_steps (status);
