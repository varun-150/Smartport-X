-- smartportx_gate — M6 gate service DDL (DATA-ARCHITECTURE §8.1-8.4).
-- Timezone rule: all TIMESTAMP columns store UTC instants; external interfaces exchange ISO-8601 UTC.
-- Superset columns vs DATA-ARCHITECTURE: appointments.gate_lane, appointments.special_requirements,
--   gate_transactions.gate_lane, appointment_quotas.gate_lane, appointment_quotas.adjustment_reason
--   (see PHASE-4-DECISIONS D-446; required by API-CONTRACTS §6.1/§6.2/§6.3).

CREATE TABLE appointments (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    confirmation_number   VARCHAR(20)   NOT NULL UNIQUE,              -- APT-YYYYMMDD-NNNN
    trucking_company      VARCHAR(100)  NOT NULL,
    truck_license_plate   VARCHAR(20)   NOT NULL,
    driver_name           VARCHAR(100)  NOT NULL,
    driver_license        VARCHAR(30)   NOT NULL,
    appointment_type      VARCHAR(10)   NOT NULL,                     -- 'PICKUP', 'DROP_OFF', 'BOTH' (D-446)
    container_number      VARCHAR(11)   NOT NULL,
    preferred_time_slot   TIMESTAMP     NOT NULL,                     -- UTC
    actual_arrival        TIMESTAMP     NULL,                         -- UTC
    duration_minutes      INT           NOT NULL DEFAULT 30,
    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING',   -- PENDING/CONFIRMED/CHECKED_IN/IN_PROGRESS/COMPLETED/CANCELLED/NO_SHOW
    yard_location         VARCHAR(50)   NULL,
    gate_lane             VARCHAR(20)   NULL,                         -- assigned at booking (superset D-446)
    special_requirements  JSONB         NULL,                         -- hazmatCertified/reeferConnection/doubleStack (superset D-446)
    notes                 TEXT          NULL,
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_appointments_time_slot ON appointments (preferred_time_slot);
CREATE INDEX idx_appointments_status    ON appointments (status);
CREATE INDEX idx_appointments_type      ON appointments (appointment_type);
CREATE INDEX idx_appointments_company   ON appointments (trucking_company);

CREATE TABLE gate_transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id      UUID          NOT NULL REFERENCES appointments(id),
    gate_type           VARCHAR(10)   NOT NULL,        -- 'INBOUND', 'OUTBOUND'
    truck_license_plate VARCHAR(20)   NOT NULL,
    driver_name         VARCHAR(100)  NOT NULL,
    driver_license      VARCHAR(30)   NOT NULL,
    container_number    VARCHAR(11)   NOT NULL,
    seal_number         VARCHAR(20)   NULL,
    documents           JSONB         NULL,            -- document verification results
    check_in_time       TIMESTAMP     NOT NULL,        -- UTC
    check_out_time      TIMESTAMP     NULL,            -- UTC
    yard_location       VARCHAR(50)   NULL,
    gate_lane           VARCHAR(20)   NULL,            -- superset D-446
    eir_number          VARCHAR(30)   NULL,            -- Equipment Interchange Receipt
    status              VARCHAR(20)   NOT NULL DEFAULT 'CHECKED_IN',  -- CHECKED_IN -> CHECKED_OUT
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gate_transactions_appointment ON gate_transactions (appointment_id);
CREATE INDEX idx_gate_transactions_status_plate ON gate_transactions (status, truck_license_plate);
CREATE INDEX idx_gate_transactions_container   ON gate_transactions (container_number);

CREATE TABLE queue_status (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_time           TIMESTAMP     NOT NULL,   -- UTC
    waiting_trucks          INT           NOT NULL DEFAULT 0,
    pickup_count            INT           NOT NULL DEFAULT 0,
    dropoff_count           INT           NOT NULL DEFAULT 0,
    avg_wait_time_minutes   FLOAT         NULL,
    estimated_clearance     TIMESTAMP     NULL         -- UTC
);

CREATE INDEX idx_queue_status_snapshot ON queue_status (snapshot_time DESC);

CREATE TABLE appointment_quotas (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    time_window_start     TIMESTAMP     NOT NULL,      -- UTC
    time_window_end       TIMESTAMP     NOT NULL,      -- UTC
    max_appointments      INT           NOT NULL,
    current_bookings      INT           NOT NULL DEFAULT 0,
    appointment_type      VARCHAR(10)   NOT NULL,      -- 'PICKUP', 'DROP_OFF' (quota rows; D-446)
    gate_lane             VARCHAR(20)   NOT NULL DEFAULT 'ALL',  -- superset D-446
    dynamic_adjustment    BOOLEAN       NOT NULL DEFAULT FALSE,
    adjusted_by           UUID          NULL,          -- admin user ID
    adjustment_reason     VARCHAR(200)  NULL,          -- superset D-446
    version               BIGINT        NOT NULL DEFAULT 0,  -- optimistic locking for quota races
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_appointment_quotas_window_type ON appointment_quotas (time_window_start, appointment_type);