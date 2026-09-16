-- smartportx_integration -- M8 integration slice (EDIFACT / IoT / customs / audit).
-- Superset DDL: DATA-ARCHITECTURE A10.1-10.4 is the canonical base; superset columns
-- (sender/receiver/direction/interchange_ref/message_ref, retry_count, error_message,
-- iot value_numeric/value_text/device_timestamp, customs cleared_at/reviewed_by)
-- reconcile INTEGRATION-ARCHITECTURE A3.4/4.4/5 + idempotency/uniqueness requirements.
-- UTC instants; ddl-auto: validate runs against this exact DDL.

CREATE TABLE edifact_messages (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    message_type       VARCHAR(10) NOT NULL,
    raw_message        TEXT        NOT NULL,
    parsed_data        JSONB,
    sender             VARCHAR(50),
    receiver           VARCHAR(50),
    direction          VARCHAR(10) NOT NULL DEFAULT 'INBOUND',
    interchange_ref    VARCHAR(50),
    message_ref        VARCHAR(50),
    correlation_id     UUID        NOT NULL,
    validation_errors  JSONB,
    status             VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    error_message      TEXT,
    retry_count        INTEGER     NOT NULL DEFAULT 0,
    received_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    processed_at       TIMESTAMP,
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_edifact_messages_type ON edifact_messages(message_type);
CREATE INDEX idx_edifact_messages_status ON edifact_messages(status);
CREATE INDEX idx_edifact_messages_correlation ON edifact_messages(correlation_id);

-- Idempotency (M8.5): a (sender, receiver, interchange_ref, message_ref) tuple must be unique;
-- replaying the same interchange returns the existing message instead of processing twice.
CREATE UNIQUE INDEX uq_edifact_interchange_message
    ON edifact_messages(sender, receiver, interchange_ref, message_ref)
    WHERE interchange_ref IS NOT NULL AND message_ref IS NOT NULL;

CREATE TABLE iot_readings (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    container_number  VARCHAR(11) NOT NULL,
    sensor_type       VARCHAR(20) NOT NULL,
    value             VARCHAR(100) NOT NULL,
    value_numeric     DOUBLE PRECISION,
    value_text        VARCHAR(50),
    unit              VARCHAR(20),
    latitude          DOUBLE PRECISION,
    longitude         DOUBLE PRECISION,
    accuracy_meters   DOUBLE PRECISION,
    signal_strength   INTEGER,
    battery_level     INTEGER,
    firmware_version  VARCHAR(20),
    device_id         VARCHAR(80),
    raw_payload       JSONB,
    device_timestamp  TIMESTAMP,
    timestamp         TIMESTAMP   NOT NULL,
    received_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_iot_container_sensor ON iot_readings(container_number, sensor_type, timestamp DESC);
CREATE INDEX idx_iot_timestamp ON iot_readings(timestamp);

CREATE TABLE customs_status (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    container_number  VARCHAR(11) NOT NULL UNIQUE,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason            TEXT,
    cleared_by        VARCHAR(100),
    cleared_at        TIMESTAMP,
    hold_until        TIMESTAMP,
    reviewed_by       VARCHAR(100),
    declaration_number VARCHAR(50),
    duty_status       VARCHAR(20),
    created_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customs_container ON customs_status(container_number);

CREATE TABLE integration_logs (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name    VARCHAR(50) NOT NULL,
    operation       VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    request_data    JSONB,
    response_data   JSONB,
    error_message   TEXT,
    timestamp       TIMESTAMP   NOT NULL DEFAULT NOW(),
    correlation_id  UUID        NOT NULL
);

CREATE INDEX idx_integration_logs_correlation ON integration_logs(correlation_id);
CREATE INDEX idx_integration_logs_service ON integration_logs(service_name, timestamp DESC);
