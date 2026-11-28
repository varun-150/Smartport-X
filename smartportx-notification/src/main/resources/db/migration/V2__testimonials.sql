-- smartportx_notification — testimonials table (FR add-on, Phase 6 "final direction").
--
-- Hosted in the notification service (decision, PLANNER/ARCHITECT): testimonials are
-- lightweight external-facing content (name, role, message, 1-5 rating) with no
-- container/vessel/yard coupling, so they require no ORCHESTRATION to assemble.
-- A dedicated standalone resource service would be over-engineering for one table.
--
-- Classification: DESIGN PROPOSAL (see docs/decision/001-testimonials-host.md).
--
-- Column notes:
--   * rating is INTEGER (not SMALLINT) so Hibernate's `ddl-auto: validate` accepts
--     the mapped Java int (int4) without a smallint/int4 JDBC mismatch; the CHECK
--     constraint still enforces the 1-5 range at the database.
--   * created_at/updated_at follow the V1__init.sql convention (TIMESTAMP DEFAULT NOW()).
CREATE TABLE smartportx_notification.testimonials (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(120)  NOT NULL,
    role       VARCHAR(80)   NULL,
    message    TEXT          NOT NULL,
    rating     INTEGER       NOT NULL CHECK (rating BETWEEN 1 AND 5),
    created_at TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_testimonials_created_at
    ON smartportx_notification.testimonials (created_at DESC);