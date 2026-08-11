-- =============================================================================
-- V1: RootTraceAI initial schema
-- =============================================================================

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- =============================================================================
-- Incidents
-- =============================================================================
CREATE TABLE incidents (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title       VARCHAR(500)  NOT NULL,
    description TEXT          NOT NULL,
    service     VARCHAR(255)  NOT NULL,
    severity    VARCHAR(20)   NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status      VARCHAR(20)   NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'CLOSED')),
    environment VARCHAR(100),
    created_by  VARCHAR(255)  NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    resolution  TEXT,
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_incidents_status     ON incidents (status)      WHERE deleted_at IS NULL;
CREATE INDEX idx_incidents_severity   ON incidents (severity)    WHERE deleted_at IS NULL;
CREATE INDEX idx_incidents_service    ON incidents (service)     WHERE deleted_at IS NULL;
CREATE INDEX idx_incidents_created_at ON incidents (created_at)  WHERE deleted_at IS NULL;
CREATE INDEX idx_incidents_created_by ON incidents (created_by)  WHERE deleted_at IS NULL;

-- =============================================================================
-- Audit Events
-- =============================================================================
CREATE TABLE audit_events (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_type  VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id   VARCHAR(255),
    actor       VARCHAR(255),
    details     TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_events_type       ON audit_events (event_type);
CREATE INDEX idx_audit_events_entity     ON audit_events (entity_type, entity_id);
CREATE INDEX idx_audit_events_created_at ON audit_events (created_at);
