CREATE SCHEMA IF NOT EXISTS smartportx_identity;

CREATE TABLE smartportx_identity.users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username              VARCHAR(50)   NOT NULL UNIQUE,
    email                 VARCHAR(100)  NOT NULL UNIQUE,
    display_name          VARCHAR(100)  NOT NULL,
    password_hash         VARCHAR(255)  NOT NULL,
    role                  VARCHAR(20)   NOT NULL DEFAULT 'READONLY',
    enabled               BOOLEAN       NOT NULL DEFAULT TRUE,
    failed_login_attempts INT           NOT NULL DEFAULT 0,
    lockout_until         TIMESTAMPTZ   NULL,
    last_login_at         TIMESTAMPTZ   NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE smartportx_identity.roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(20)   NOT NULL UNIQUE,
    description VARCHAR(255)  NULL
);

CREATE TABLE smartportx_identity.user_roles (
    user_id UUID NOT NULL REFERENCES smartportx_identity.users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES smartportx_identity.roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE smartportx_identity.refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID           NOT NULL REFERENCES smartportx_identity.users(id) ON DELETE CASCADE,
    family_id   UUID           NOT NULL,
    token_hash  VARCHAR(255)   NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ    NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    used_at     TIMESTAMPTZ    NULL,
    revoked     BOOLEAN        NOT NULL DEFAULT FALSE
);

CREATE TABLE smartportx_identity.password_history (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID           NOT NULL REFERENCES smartportx_identity.users(id) ON DELETE CASCADE,
    password_hash VARCHAR(255)   NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_users_username ON smartportx_identity.users (username);
CREATE UNIQUE INDEX idx_users_email ON smartportx_identity.users (email);
CREATE INDEX idx_refresh_tokens_user ON smartportx_identity.refresh_tokens (user_id);
CREATE UNIQUE INDEX idx_refresh_tokens_hash ON smartportx_identity.refresh_tokens (token_hash);
CREATE INDEX idx_password_history_user ON smartportx_identity.password_history (user_id);

INSERT INTO smartportx_identity.roles (name, description) VALUES
    ('ADMIN', 'Full platform access, user management, system configuration'),
    ('TERMINAL_OP', 'Vessel operations, container management, yard read/write, gate read'),
    ('YARD_MANAGER', 'Yard layout management, container positioning, stacking config, yard reports'),
    ('GATE_OPERATOR', 'Gate operations, truck appointments, gate queue management'),
    ('READONLY', 'Dashboard viewing, report generation, no modifications')
ON CONFLICT (name) DO NOTHING;