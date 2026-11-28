-- smartportx_notification — baseline schema (FR-014, M9).
--
-- Sources (superset): DATA-ARCHITECTURE §11.1/§11.2 verbatim base columns,
-- extended with API-CONTRACTS §9.1/§9.2 superset columns:
--   preferences: email_address, sms_enabled, sms_number, push_enabled, quiet_hours,
--                digest_enabled, digest_frequency
--   history:     severity, action_url, data (JSONB)
-- Reconciliations recorded as finding F-431:
--   * notification_preferences.categories stores the API-CONTRACTS §9.2 8-flag boolean map
--     (default '{}' object) instead of the §11.1 array-of-strings default '[]'.
--   * notification_history.user_id is NULLABLE: domain-event pushes are broadcast to a topic
--     and are not bound to a single user (superset deviation from §11.2 NOT NULL).
CREATE SCHEMA IF NOT EXISTS smartportx_notification;

CREATE TABLE smartportx_notification.notification_preferences (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID          NOT NULL,  -- logical reference to Identity Service
    email_enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    email_address     VARCHAR(255)  NULL,
    sms_enabled       BOOLEAN       NOT NULL DEFAULT FALSE,
    sms_number        VARCHAR(20)   NULL,
    websocket_enabled BOOLEAN       NOT NULL DEFAULT TRUE,
    push_enabled      BOOLEAN       NOT NULL DEFAULT FALSE,
    -- §9.2 category -> enabled boolean map (YARD_UPDATE, GATE_EVENT, VESSEL_UPDATE,
    -- CONTAINER_EVENT, APPOINTMENT, ALERT, CUSTOMS, IOT).
    categories        JSONB         NOT NULL DEFAULT '{}',
    -- { enabled, start, end, timezone } when a delivery quiet window is active.
    quiet_hours       JSONB         NOT NULL DEFAULT '{"enabled":false,"start":"22:00","end":"06:00","timezone":"UTC"}',
    digest_enabled    BOOLEAN       NOT NULL DEFAULT FALSE,
    digest_frequency  VARCHAR(10)   NOT NULL DEFAULT 'DAILY',  -- DAILY | WEEKLY
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_notification_preferences_user UNIQUE (user_id)
);

CREATE TABLE smartportx_notification.notification_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- NULL for broadcast pushes; populated when the emitted event identifies a target user.
    user_id     UUID          NULL,
    category    VARCHAR(30)   NOT NULL,
    severity    VARCHAR(10)   NOT NULL DEFAULT 'INFO',  -- INFO | WARNING | ERROR | CRITICAL
    title       VARCHAR(200)  NOT NULL,
    message     TEXT          NOT NULL,
    channel     VARCHAR(20)   NOT NULL,  -- 'EMAIL', 'WEBSOCKET', 'BOTH'
    status      VARCHAR(20)   NOT NULL DEFAULT 'SENT',  -- 'SENT', 'DELIVERED', 'FAILED'
    action_url  VARCHAR(500)  NULL,
    data        JSONB         NULL,  -- context snapshot of the originating domain event
    sent_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    read_at     TIMESTAMP     NULL
);

CREATE INDEX idx_notification_history_user_sent
    ON smartportx_notification.notification_history (user_id, sent_at DESC);