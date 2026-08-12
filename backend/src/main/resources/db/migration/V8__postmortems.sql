-- =============================================================================
-- V8: Postmortems & Preventive Action Items
-- =============================================================================

CREATE TABLE postmortems (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id         UUID NOT NULL UNIQUE REFERENCES incidents(id) ON DELETE CASCADE,
    title               VARCHAR(500) NOT NULL,
    summary             TEXT NOT NULL,
    impact_summary      TEXT NOT NULL,
    root_cause_analysis TEXT NOT NULL,
    resolution_summary  TEXT NOT NULL,
    timeline            JSONB NOT NULL DEFAULT '[]',
    lessons_learned     JSONB NOT NULL DEFAULT '[]',
    status              VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    downtime_minutes    BIGINT,
    created_by_id       UUID NOT NULL REFERENCES users(id),
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE postmortem_action_items (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    postmortem_id  UUID NOT NULL REFERENCES postmortems(id) ON DELETE CASCADE,
    title          VARCHAR(255) NOT NULL,
    description    TEXT NOT NULL,
    category       VARCHAR(50) NOT NULL DEFAULT 'PREVENT',
    priority       VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    status         VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    assigned_to_id UUID REFERENCES users(id) ON DELETE SET NULL,
    due_date       TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_postmortems_incident_id ON postmortems (incident_id);
CREATE INDEX idx_postmortems_status ON postmortems (status);
CREATE INDEX idx_postmortem_action_items_postmortem ON postmortem_action_items (postmortem_id);
CREATE INDEX idx_postmortem_action_items_status ON postmortem_action_items (status);
CREATE INDEX idx_postmortem_action_items_category ON postmortem_action_items (category);
