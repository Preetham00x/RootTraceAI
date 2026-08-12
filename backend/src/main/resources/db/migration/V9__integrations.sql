-- =============================================================================
-- V9: External Integrations, Webhooks, Ticketing & Runbook Executions
-- =============================================================================

CREATE TABLE integration_configs (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    provider       VARCHAR(50) NOT NULL,
    type           VARCHAR(50) NOT NULL,
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    config_metadata JSONB NOT NULL DEFAULT '{}',
    created_by_id  UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE webhook_events (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    provider          VARCHAR(50) NOT NULL,
    external_event_id VARCHAR(255) NOT NULL,
    event_type        VARCHAR(100),
    payload           TEXT NOT NULL,
    processing_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    incident_id       UUID REFERENCES incidents(id) ON DELETE SET NULL,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at      TIMESTAMPTZ,
    error_message     TEXT,
    CONSTRAINT uq_webhook_provider_event UNIQUE (provider, external_event_id)
);

CREATE TABLE external_tickets (
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id        UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    action_item_id     UUID REFERENCES postmortem_action_items(id) ON DELETE CASCADE,
    provider           VARCHAR(50) NOT NULL,
    external_ticket_id VARCHAR(100) NOT NULL,
    external_url       VARCHAR(500),
    status             VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_external_ticket_action UNIQUE (provider, action_item_id)
);

CREATE TABLE runbook_executions (
    id                     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id            UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    investigation_step_id  UUID REFERENCES investigation_steps(id) ON DELETE SET NULL,
    command                VARCHAR(1000) NOT NULL,
    execution_status       VARCHAR(50) NOT NULL DEFAULT 'REQUESTED',
    requested_by_id        UUID NOT NULL REFERENCES users(id),
    approved_by_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    output                 TEXT,
    error_output           TEXT,
    started_at             TIMESTAMPTZ,
    completed_at           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_events_provider_event ON webhook_events (provider, external_event_id);
CREATE INDEX idx_webhook_events_incident ON webhook_events (incident_id);
CREATE INDEX idx_external_tickets_incident ON external_tickets (incident_id);
CREATE INDEX idx_external_tickets_action_item ON external_tickets (action_item_id);
CREATE INDEX idx_runbook_executions_incident ON runbook_executions (incident_id);
CREATE INDEX idx_runbook_executions_status ON runbook_executions (execution_status);
