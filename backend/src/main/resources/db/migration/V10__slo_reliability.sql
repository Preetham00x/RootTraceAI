-- =============================================================================
-- V10: SLOs, Error Budgets & Reliability Governance
-- =============================================================================

CREATE TABLE service_slos (
    id                            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    service_name                  VARCHAR(255) NOT NULL,
    name                          VARCHAR(255) NOT NULL,
    description                   TEXT,
    slo_type                      VARCHAR(50) NOT NULL,
    target_percentage             DECIMAL(6,3) NOT NULL,
    window_days                   INTEGER NOT NULL DEFAULT 30,
    warning_threshold_percentage  DECIMAL(6,3) NOT NULL DEFAULT 99.0,
    critical_threshold_percentage DECIMAL(6,3) NOT NULL DEFAULT 95.0,
    enabled                       BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_id                 UUID NOT NULL REFERENCES users(id),
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_service_slo_name UNIQUE(service_name, name),
    CONSTRAINT chk_target_percentage CHECK (target_percentage > 0 AND target_percentage <= 100),
    CONSTRAINT chk_window_days CHECK (window_days > 0)
);

CREATE TABLE sli_measurements (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    slo_id           UUID NOT NULL REFERENCES service_slos(id) ON DELETE CASCADE,
    measurement_time TIMESTAMPTZ NOT NULL,
    total_events     BIGINT NOT NULL,
    good_events      BIGINT NOT NULL,
    bad_events       BIGINT NOT NULL,
    value            DECIMAL(12,6) NOT NULL,
    source           VARCHAR(100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_total_events CHECK (total_events >= 0),
    CONSTRAINT chk_good_events CHECK (good_events >= 0),
    CONSTRAINT chk_bad_events CHECK (bad_events >= 0),
    CONSTRAINT chk_events_sum CHECK (good_events + bad_events <= total_events)
);

CREATE INDEX idx_service_slos_service ON service_slos (service_name);
CREATE INDEX idx_service_slos_enabled ON service_slos (enabled);
CREATE INDEX idx_sli_measurements_slo ON sli_measurements (slo_id);
CREATE INDEX idx_sli_measurements_time ON sli_measurements (measurement_time);
CREATE INDEX idx_sli_measurements_slo_time ON sli_measurements (slo_id, measurement_time);
