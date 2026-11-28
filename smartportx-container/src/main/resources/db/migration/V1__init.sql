CREATE SCHEMA IF NOT EXISTS smartportx_container;

CREATE TABLE smartportx_container.containers (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    container_number      VARCHAR(11)   NOT NULL UNIQUE,
    container_type        VARCHAR(10)   NOT NULL,
    owner                 VARCHAR(100)  NULL,
    consignee             VARCHAR(100)  NULL,
    shipper               VARCHAR(100)  NULL,
    weight_kg             FLOAT         NULL,
    tare_weight_kg        FLOAT         NULL,
    max_payload_kg        FLOAT         NULL,
    seal_number           VARCHAR(20)   NULL,
    bill_of_lading        VARCHAR(50)   NULL,
    origin_port           VARCHAR(100)  NULL,
    destination_port      VARCHAR(100)  NULL,
    expected_arrival      TIMESTAMPTZ   NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'REGISTERED',
    current_location      VARCHAR(50)   NULL,
    dangerous_goods       BOOLEAN       NOT NULL DEFAULT FALSE,
    imdg_class            VARCHAR(10)   NULL,
    un_number             VARCHAR(10)   NULL,
    proper_shipping_name  VARCHAR(100)  NULL,
    packing_group         VARCHAR(5)    NULL,
    flash_point           VARCHAR(20)   NULL,
    emergency_contact     VARCHAR(100)  NULL,
    removed_at            TIMESTAMPTZ   NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE smartportx_container.container_movements (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    container_id  UUID           NOT NULL REFERENCES smartportx_container.containers(id) ON DELETE CASCADE,
    movement_type VARCHAR(30)    NOT NULL,
    from_location VARCHAR(50)    NULL,
    to_location   VARCHAR(50)    NULL,
    timestamp     TIMESTAMPTZ    NOT NULL,
    performed_by  VARCHAR(100)   NOT NULL,
    reason        VARCHAR(255)   NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE smartportx_container.container_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    container_id   UUID         NOT NULL REFERENCES smartportx_container.containers(id) ON DELETE CASCADE,
    event_type     VARCHAR(30)  NOT NULL,
    event_data     JSONB        NOT NULL,
    correlation_id UUID         NOT NULL,
    timestamp      TIMESTAMPTZ  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE smartportx_container.container_status_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    container_id    UUID         NOT NULL REFERENCES smartportx_container.containers(id) ON DELETE CASCADE,
    previous_status VARCHAR(20)  NULL,
    new_status      VARCHAR(20)  NOT NULL,
    changed_by      UUID         NOT NULL,
    reason          VARCHAR(255) NULL,
    changed_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_containers_type ON smartportx_container.containers (container_type);
CREATE INDEX idx_containers_status ON smartportx_container.containers (status);
CREATE INDEX idx_containers_created ON smartportx_container.containers (created_at DESC);
CREATE INDEX idx_movements_container_time ON smartportx_container.container_movements (container_id, timestamp DESC);
CREATE INDEX idx_movements_type ON smartportx_container.container_movements (movement_type);
CREATE INDEX idx_events_container_time ON smartportx_container.container_events (container_id, timestamp DESC);
CREATE INDEX idx_events_type ON smartportx_container.container_events (event_type);
CREATE INDEX idx_events_correlation ON smartportx_container.container_events (correlation_id);
CREATE INDEX idx_status_history_container_time ON smartportx_container.container_status_history (container_id, changed_at DESC);