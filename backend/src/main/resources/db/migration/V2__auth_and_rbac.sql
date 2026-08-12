-- =============================================================================
-- V2: Authentication and RBAC
-- =============================================================================

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL CHECK (role IN ('ADMIN', 'ENGINEER', 'VIEWER')),
    organization_id UUID,
    team_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_users_email ON users (email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_org ON users (organization_id) WHERE deleted_at IS NULL;

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(500) NOT NULL UNIQUE,
    expiry_date TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- Insert system user (dummy hash)
INSERT INTO users (id, email, password_hash, first_name, last_name, role)
VALUES ('00000000-0000-0000-0000-000000000000', 'system@roottrace.com', '$2a$10$dummyhashnotrealpassword0123456789', 'System', 'User', 'ADMIN');

-- Update incidents table
ALTER TABLE incidents ADD COLUMN created_by_id UUID;

-- Map existing incidents to system user
UPDATE incidents SET created_by_id = '00000000-0000-0000-0000-000000000000';

-- Make created_by_id not null and add foreign key
ALTER TABLE incidents ALTER COLUMN created_by_id SET NOT NULL;
ALTER TABLE incidents ADD CONSTRAINT fk_incidents_users FOREIGN KEY (created_by_id) REFERENCES users(id);

-- Drop old created_by column
ALTER TABLE incidents DROP COLUMN created_by;

-- Create new index for created_by_id
CREATE INDEX idx_incidents_created_by_id ON incidents (created_by_id) WHERE deleted_at IS NULL;
