CREATE SCHEMA IF NOT EXISTS smartportx_yard;

CREATE TABLE smartportx_yard.yard_blocks (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    block_code             VARCHAR(10)   NOT NULL UNIQUE,
    name                   VARCHAR(100)  NOT NULL,
    total_bays             INT           NOT NULL,
    total_rows             INT           NOT NULL,
    total_tiers            INT           NOT NULL,
    block_type             VARCHAR(20)   NOT NULL,  -- 'EXPORT','IMPORT','TRANSIT','REFRIGERATED','DANGEROUS','EMPTY' (D-441 superset)
    location_x             FLOAT         NOT NULL,
    location_y             FLOAT         NOT NULL,
    dangerous_goods_allowed BOOLEAN      NOT NULL DEFAULT FALSE,
    max_stack_weight       FLOAT         NULL,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE smartportx_yard.yard_bays (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    block_id    UUID          NOT NULL REFERENCES smartportx_yard.yard_blocks(id) ON DELETE CASCADE,
    bay_number  INT           NOT NULL,
    row_count   INT           NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (block_id, bay_number)
);

CREATE TABLE smartportx_yard.yard_rows (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bay_id      UUID          NOT NULL REFERENCES smartportx_yard.yard_bays(id) ON DELETE CASCADE,
    row_number  INT           NOT NULL,
    tier_count  INT           NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (bay_id, row_number)
);

CREATE TABLE smartportx_yard.yard_tiers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    row_id          UUID          NOT NULL REFERENCES smartportx_yard.yard_rows(id) ON DELETE CASCADE,
    tier_number     INT           NOT NULL,
    container_id    UUID          NULL,
    container_type  VARCHAR(20)   NULL,       -- cached: '20DC','40DC','40HC','45HC','20RF','40RF'
    occupied        BOOLEAN       NOT NULL DEFAULT FALSE,
    weight_kg       FLOAT         NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (row_id, tier_number)
);

CREATE TABLE smartportx_yard.yard_metrics (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    block_id              UUID          NOT NULL REFERENCES smartportx_yard.yard_blocks(id) ON DELETE CASCADE,
    date                  DATE          NOT NULL,
    total_slots           INT           NOT NULL,
    occupied_slots        INT           NOT NULL,
    utilization_rate      FLOAT         NOT NULL,
    avg_dwell_time_hours  FLOAT         NULL,
    reshuffling_count     INT           NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (block_id, date)
);

CREATE TABLE smartportx_yard.optimization_history (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    block_id                        UUID          NOT NULL REFERENCES smartportx_yard.yard_blocks(id) ON DELETE CASCADE,
    strategy                        VARCHAR(30)   NOT NULL,   -- 'FIFO','WEIGHT_CLUSTER','DEPARTURE_CLUSTER','BALANCED' (D-443 superset)
    status                          VARCHAR(20)   NOT NULL DEFAULT 'COMPLETED',  -- 'COMPLETED','FAILED','IN_PROGRESS'
    dry_run                         BOOLEAN       NOT NULL DEFAULT TRUE,
    container_count                 INT           NOT NULL,
    estimated_reshuffling_reduction FLOAT         NULL,
    actual_reshuffling_reduction    FLOAT         NULL,
    moves_completed                 INT           NULL,
    moves_failed                    INT           NULL,
    execution_time_ms               INT           NULL,
    executed_by                     UUID          NOT NULL,
    triggered_by                    VARCHAR(100)  NULL,
    created_at                      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    completed_at                    TIMESTAMPTZ   NULL
);

CREATE TABLE smartportx_yard.yard_equipment (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(20)   NOT NULL UNIQUE,
    equipment_type   VARCHAR(10)   NOT NULL,   -- 'RTG','ASC'
    assigned_block_id UUID         NULL REFERENCES smartportx_yard.yard_blocks(id) ON DELETE SET NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',  -- 'AVAILABLE','ASSIGNED','OUT_OF_SERVICE'
    moves_total      INT           NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_yard_bays_block_id ON smartportx_yard.yard_bays (block_id);
CREATE INDEX idx_yard_rows_bay_id ON smartportx_yard.yard_rows (bay_id);
CREATE INDEX idx_yard_tiers_row_id ON smartportx_yard.yard_tiers (row_id);
CREATE INDEX idx_yard_tiers_occupied ON smartportx_yard.yard_tiers (occupied);
CREATE INDEX idx_optimization_history_block_created ON smartportx_yard.optimization_history (block_id, created_at DESC);
CREATE INDEX idx_yard_equipment_block_id ON smartportx_yard.yard_equipment (assigned_block_id);

WITH new_block AS (
    INSERT INTO smartportx_yard.yard_blocks
        (block_code, name, total_bays, total_rows, total_tiers, block_type, location_x, location_y, dangerous_goods_allowed, max_stack_weight)
    VALUES ('B01', 'Block 1 - Export', 2, 2, 3, 'EXPORT', 0, 0, FALSE, 70000)
    RETURNING id
), new_bays AS (
    INSERT INTO smartportx_yard.yard_bays (block_id, bay_number, row_count)
    SELECT id, b.bay, 2 FROM new_block CROSS JOIN (VALUES (1), (2)) AS b(bay)
    RETURNING id
), new_rows AS (
    INSERT INTO smartportx_yard.yard_rows (bay_id, row_number, tier_count)
    SELECT nb.id, r.row_no, 3
    FROM new_bays nb CROSS JOIN (VALUES (1), (2)) AS r(row_no)
    RETURNING id
)
INSERT INTO smartportx_yard.yard_tiers (row_id, tier_number)
SELECT nr.id, g.tier
FROM new_rows nr CROSS JOIN (VALUES (1), (2), (3)) AS g(tier);

INSERT INTO smartportx_yard.yard_equipment (code, equipment_type, assigned_block_id, status)
SELECT 'RTG-01', 'RTG', b.id, 'ASSIGNED' FROM smartportx_yard.yard_blocks b WHERE b.block_code = 'B01';