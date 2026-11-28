-- smartportx_vessel — M7 baseline.
-- Superset decision D-447: merges DATA-ARCHITECTURE §9 tables with the
-- API-CONTRACTS §7 vocabulary (vessel_type incl. ROLL_ON_ROLL_OFF, priority
-- HIGH/MEDIUM/LOW, voyage_number on discharge_plans, gross_tonnage/flag/
-- operator/call_sign on vessels, port_of_call/next_port/estimated_teus/
-- actual_teus on vessel_schedules). See PHASE-4-FINDINGS F-423 for the
-- DATA-ARCHITECTURE-vs-API-CONTRACTS deltas.

CREATE TABLE vessels (
    id            UUID PRIMARY KEY,
    imo_number    VARCHAR(10) NOT NULL,
    vessel_name   VARCHAR(100) NOT NULL,
    vessel_type   VARCHAR(20)  NOT NULL,
    length_m      DOUBLE PRECISION NOT NULL,
    beam_m        DOUBLE PRECISION NOT NULL,
    draft_m       DOUBLE PRECISION NOT NULL,
    teu_capacity  INTEGER,
    gross_tonnage DOUBLE PRECISION,
    call_sign     VARCHAR(10),
    flag          VARCHAR(50),
    operator      VARCHAR(100),
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_vessels_imo UNIQUE (imo_number)
);

COMMENT ON TABLE vessels IS 'Vessel registry. DATA-ARCHITECTURE §9.1, API-CONTRACTS §7.1.';

CREATE TABLE vessel_schedules (
    id              UUID PRIMARY KEY,
    vessel_id       UUID NOT NULL REFERENCES vessels(id),
    voyage_number   VARCHAR(20) NOT NULL,
    berth_number    VARCHAR(20) NOT NULL,
    priority        VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    eta             TIMESTAMPTZ NOT NULL,
    etd             TIMESTAMPTZ NOT NULL,
    port_of_call    VARCHAR(100),
    next_port       VARCHAR(100),
    estimated_teus  INTEGER,
    actual_teus     INTEGER,
    arrived_at      TIMESTAMPTZ,
    departed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_vessel_schedules_voyage UNIQUE (vessel_id, voyage_number),
    CONSTRAINT ck_vessel_schedules_etd CHECK (etd >= eta)
);

COMMENT ON TABLE vessel_schedules IS 'Vessel call / berth schedule. DATA-ARCHITECTURE §9.2, API-CONTRACTS §7.2.';
CREATE INDEX ix_vessel_schedules_vessel ON vessel_schedules (vessel_id);
CREATE INDEX ix_vessel_schedules_status ON vessel_schedules (status);

CREATE TABLE discharge_plans (
    id            UUID PRIMARY KEY,
    vessel_id     UUID NOT NULL REFERENCES vessels(id),
    schedule_id   UUID REFERENCES vessel_schedules(id),
    voyage_number VARCHAR(20),
    status        VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);

COMMENT ON TABLE discharge_plans IS 'Discharge plan for a vessel voyage. DATA-ARCHITECTURE §9.3, API-CONTRACTS §7.3.';
CREATE INDEX ix_discharge_plans_vessel ON discharge_plans (vessel_id);

CREATE TABLE discharge_containers (
    id                   UUID PRIMARY KEY,
    discharge_plan_id    UUID NOT NULL REFERENCES discharge_plans(id) ON DELETE CASCADE,
    container_number     VARCHAR(11) NOT NULL,
    container_type       VARCHAR(10) NOT NULL,
    weight_kg            DOUBLE PRECISION,
    dangerous_goods      BOOLEAN NOT NULL DEFAULT FALSE,
    reefer               BOOLEAN NOT NULL DEFAULT FALSE,
    vessel_position      VARCHAR(10) NOT NULL,
    yard_target          VARCHAR(20),
    priority             VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
    status               VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    actual_yard_position VARCHAR(20),
    equipment_id         VARCHAR(20),
    operator             VARCHAR(50),
    notes                TEXT,
    discharged_at        TIMESTAMPTZ,
    stacked_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_discharge_containers_plan_container UNIQUE (discharge_plan_id, container_number)
);

COMMENT ON TABLE discharge_containers IS 'Containers on a discharge plan. DATA-ARCHITECTURE §9.4, API-CONTRACTS §7.3.';
CREATE INDEX ix_discharge_containers_plan ON discharge_containers (discharge_plan_id);
CREATE INDEX ix_discharge_containers_vessel ON discharge_containers (discharge_plan_id, container_number);

-- Seed registry (fixed ids so other services can reference the fleet in E2E).
INSERT INTO vessels (id, imo_number, vessel_name, vessel_type, length_m, beam_m, draft_m,
                     teu_capacity, gross_tonnage, call_sign, flag, operator, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'IMO9876543', 'MSC Diana', 'CONTAINER', 366.00, 48.20, 14.50,
        14400, 156907.00, '3FJU7', 'PA', 'Mediterranean Shipping', NOW(), NOW()),
       ('22222222-2222-2222-2222-222222222222', 'IMO7654321', 'Sunrise Trader', 'BULK', 199.00, 32.26, 12.00,
        NULL, 62298.00, '9VRT2', 'LR', 'Dry Bulk Lines', NOW(), NOW());

INSERT INTO vessel_schedules (id, vessel_id, voyage_number, berth_number, priority, status, eta, etd,
                              port_of_call, next_port, estimated_teus, actual_teus, arrived_at, departed_at,
                              created_at, updated_at)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '22222222-2222-2222-2222-222222222222', 'V-2025-104', 'B01',
        'NORMAL', 'DEPARTED', '2026-05-01T00:00:00Z', '2026-05-01T12:00:00Z', 'Port A', 'Port B',
        NULL, NULL, '2026-05-01T00:00:00Z', '2026-05-01T11:40:00Z', NOW(), NOW());