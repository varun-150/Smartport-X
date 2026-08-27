# SmartPortX — Data Architecture

> **Version:** 1.0  
> **Last Updated:** 2026-08-25  
> **Status:** Active  
> **Stack:** PostgreSQL 17.11 · Java 25 · Spring Boot 3.x · Spring Data JPA

---

## Table of Contents

1. [Overview](#1-overview)
2. [Data Architecture Principles](#2-data-architecture-principles)
3. [Technology Stack & Constraints](#3-technology-stack--constraints)
4. [Schema-per-Service Inventory](#4-schema-per-service-inventory)
5. [Schema: smartportx_identity](#5-schema-smartportx_identity)
6. [Schema: smartportx_yard](#6-schema-smartportx_yard)
7. [Schema: smartportx_container](#7-schema-smartportx_container)
8. [Schema: smartportx_gate](#8-schema-smartportx_gate)
9. [Schema: smartportx_vessel](#9-schema-smartportx_vessel)
10. [Schema: smartportx_integration](#10-schema-smartportx_integration)
11. [Schema: smartportx_notification](#11-schema-smartportx_notification)
12. [Cross-Service Data Access Patterns](#12-cross-service-data-access-patterns)
13. [Domain Event Architecture](#13-domain-event-architecture)
14. [Event Type Catalog](#14-event-type-catalog)
15. [Data Consistency Strategy](#15-data-consistency-strategy)
16. [Data Retention & Archival Policy](#16-data-retention--archival-policy)
17. [Schema Migration Strategy](#17-schema-migration-strategy)
18. [Indexing Strategy](#18-indexing-strategy)
19. [Backup & Recovery](#19-backup--recovery)
20. [Security & Access Control](#20-security--access-control)
21. [Monitoring & Observability](#21-monitoring--observability)

---

## 1. Overview

SmartPortX is a Service-Oriented Architecture (SOA) container port logistics platform composed of **8 microservices**, each owning its own PostgreSQL schema within a **single PostgreSQL 17.11 instance**. This document defines the complete data architecture: schemas, relationships, cross-service access patterns, domain event contracts, consistency strategies, and data lifecycle policies.

### Architecture Diagram (Logical)

```
┌─────────────────────────────────────────────────────────────────────┐
│                        PostgreSQL 17.11                             │
│                                                                     │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────┐ │
│  │ smartportx_  │ │ smartportx_  │ │ smartportx_  │ │ smartportx_│ │
│  │   identity   │ │     yard     │ │  container   │ │    gate    │ │
│  └──────────────┘ └──────────────┘ └──────────────┘ └────────────┘ │
│                                                                     │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────┐ │
│  │ smartportx_  │ │ smartportx_  │ │ smartportx_  │ │ smartportx_│ │
│  │    vessel    │ │ integration  │ │ notification │ │   (spare)  │ │
│  └──────────────┘ └──────────────┘ └──────────────┘ └────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

Each schema is logically isolated. Services connect to their own schema via dedicated database credentials. No cross-schema queries are permitted at the SQL level.

---

## 2. Data Architecture Principles

### Principle 1: Schema-per-Service

Each microservice owns a dedicated PostgreSQL schema. Schemas are named `smartportx_<service>` to enforce clear ownership boundaries. Services connect using schema-scoped database users with permissions limited to their own schema.

**Rules:**
- No shared tables across services.
- No cross-schema JOINs or foreign keys.
- No direct cross-schema reads via SQL.
- Each service manages its own migrations and DDL.

### Principle 2: Event Sourcing for Audit

All state changes are recorded as domain events. Every mutation produces an immutable event record containing timestamp, acting user, action type, and before/after values. This provides a complete audit trail for compliance and debugging.

**Rules:**
- Every state-mutating operation emits at least one domain event.
- Event records are append-only; no updates or deletes.
- Events carry `correlationId` for distributed tracing.
- Events carry `userId` for accountability.

### Principle 3: Eventual Consistency

Cross-service data consistency is achieved through asynchronous domain events, not distributed transactions (2PC/Saga). Services maintain local read models derived from events.短暂 inconsistencies are acceptable and handled at the application layer.

**Rules:**
- No distributed transactions.
- No synchronous cross-service calls for write operations.
- Read models may lag behind the source of truth by seconds to minutes.
- Idempotent event consumers prevent duplicate processing.

### Principle 4: No Orphaned Data

The **container** entity is the central domain object across the platform. Services reference containers by ID but do **not** duplicate container metadata. When a service needs container details, it either:
1. Subscribes to container events and maintains a local cache/projection, or
2. Calls the Container Service REST API for real-time lookups.

**Rules:**
- Container metadata is owned exclusively by `smartportx_container`.
- Other services store only the `container_number` or `container_id` as a foreign reference.
- No denormalized copies of container owner, consignee, or weight outside the container schema.

---

## 3. Technology Stack & Constraints

| Component | Technology | Version |
|---|---|---|
| Database | PostgreSQL | 17.11 |
| Language | Java | 25 (LTS) |
| Framework | Spring Boot | 3.x |
| ORM | Spring Data JPA / Hibernate | 6.x |
| Migration | Flyway | Latest |
| Event Bus | Apache Kafka | Latest |
| Serialization | JSON (Jackson) | Latest |

### Constraints

- **Single PostgreSQL instance** with schema-per-service isolation (not separate databases).
- **UUID primary keys** on all tables for distributed ID generation.
- **Timestamps in UTC** — all `TIMESTAMP` columns store UTC.
- **Soft deletes disabled** — hard deletes with event logging for audit.
- **No stored procedures** — business logic lives in the service layer.
- **No materialized views across schemas** — cross-service aggregations handled via events.

---

## 4. Schema-per-Service Inventory

| # | Schema | Service | Owner | Tables |
|---|---|---|---|---|
| 1 | `smartportx_identity` | Identity Service | Auth team | 5 |
| 2 | `smartportx_yard` | Yard Service | Yard ops team | 6 |
| 3 | `smartportx_container` | Container Service | Container ops team | 4 |
| 4 | `smartportx_gate` | Gate Service | Gate ops team | 4 |
| 5 | `smartportx_vessel` | Vessel Service | Marine ops team | 4 |
| 6 | `smartportx_integration` | Integration Service | Integration team | 4 |
| 7 | `smartportx_notification` | Notification Service | Platform team | 2 |
| 8 | *(reserved)* | *(future service)* | — | — |

---

## 5. Schema: smartportx_identity

**Purpose:** User authentication, authorization, role management, and session token lifecycle.

### Tables

#### 5.1 users

Stores registered platform users with authentication credentials and account state.

```sql
CREATE TABLE smartportx_identity.users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username              VARCHAR(50)   NOT NULL UNIQUE,
    email                 VARCHAR(100)  NOT NULL UNIQUE,
    password_hash         VARCHAR(255)  NOT NULL,
    role                  VARCHAR(20)   NOT NULL DEFAULT 'OPERATOR',
    enabled               BOOLEAN       NOT NULL DEFAULT TRUE,
    failed_login_attempts INT           NOT NULL DEFAULT 0,
    lockout_until         TIMESTAMP     NULL,
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

**Constraints:**
- `username` and `email` are globally unique within the identity schema.
- `failed_login_attempts` increments on each failed login; resets on success.
- `lockout_until` is set when `failed_login_attempts >= MAX_ATTEMPTS` (application-configurable, default 5).

#### 5.2 roles

Platform roles for RBAC (Role-Based Access Control).

```sql
CREATE TABLE smartportx_identity.roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(20)   NOT NULL UNIQUE,
    description VARCHAR(255)  NULL
);
```

**Standard Roles:**
- `ADMIN` — Full platform access, user management.
- `OPERATOR` — Standard port operations.
- `GATE_AGENT` — Gate check-in/check-out only.
- `YARD_PLANNER` — Yard management and optimization.
- `VESSEL_PLANNER` — Vessel scheduling and discharge planning.
- `VIEWER` — Read-only access.

#### 5.3 user_roles

Many-to-many relationship between users and roles.

```sql
CREATE TABLE smartportx_identity.user_roles (
    user_id UUID NOT NULL REFERENCES smartportx_identity.users(id),
    role_id UUID NOT NULL REFERENCES smartportx_identity.roles(id),
    PRIMARY KEY (user_id, role_id)
);
```

#### 5.4 refresh_tokens

JWT refresh token management for session lifecycle.

```sql
CREATE TABLE smartportx_identity.refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID           NOT NULL REFERENCES smartportx_identity.users(id),
    token_hash  VARCHAR(255)   NOT NULL,
    expires_at  TIMESTAMP      NOT NULL,
    created_at  TIMESTAMP      NOT NULL DEFAULT NOW(),
    revoked     BOOLEAN        NOT NULL DEFAULT FALSE
);
```

**Lifecycle:**
- Tokens are issued on login; hashed before storage (never stored in plaintext).
- Revoked tokens remain in the table until purged by the retention job.
- Only non-expired, non-revoked tokens are valid.

#### 5.5 password_history

Tracks previous password hashes to enforce password reuse policies.

```sql
CREATE TABLE smartportx_identity.password_history (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID           NOT NULL REFERENCES smartportx_identity.users(id),
    password_hash VARCHAR(255)   NOT NULL,
    created_at    TIMESTAMP      NOT NULL DEFAULT NOW()
);
```

**Policy:** Users cannot reuse the last 5 passwords (configurable). History is checked on password change.

---

## 6. Schema: smartportx_yard

**Purpose:** Yard block/bay/row/tier hierarchy, occupancy tracking, utilization metrics, and optimization history.

### Tables

#### 6.1 yard_blocks

Top-level yard block definitions.

```sql
CREATE TABLE smartportx_yard.yard_blocks (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    block_code    VARCHAR(10)   NOT NULL UNIQUE,
    name          VARCHAR(100)  NOT NULL,
    total_bays    INT           NOT NULL,
    total_rows    INT           NOT NULL,
    total_tiers   INT           NOT NULL,
    block_type    VARCHAR(20)   NOT NULL,  -- 'REEFER', 'DRY', 'SOC', 'HAZMAT'
    location_x    FLOAT         NOT NULL,
    location_y    FLOAT         NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

**Block Types:**
- `DRY` — Standard dry container storage.
- `REEFER` — Temperature-controlled reefer slots.
- `SOC` — Shipper-owned container area.
- `HAZMAT` — Dangerous goods storage ( IMDG-compliant area).

#### 6.2 yard_bays

Bays within a yard block.

```sql
CREATE TABLE smartportx_yard.yard_bays (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    block_id    UUID          NOT NULL REFERENCES smartportx_yard.yard_blocks(id),
    bay_number  INT           NOT NULL,
    row_count   INT           NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE (block_id, bay_number)
);
```

#### 6.3 yard_rows

Rows within a bay.

```sql
CREATE TABLE smartportx_yard.yard_rows (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bay_id      UUID          NOT NULL REFERENCES smartportx_yard.yard_bays(id),
    row_number  INT           NOT NULL,
    tier_count  INT           NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE (bay_id, row_number)
);
```

#### 6.4 yard_tiers

Individual storage slots. The fundamental unit of yard space.

```sql
CREATE TABLE smartportx_yard.yard_tiers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    row_id          UUID          NOT NULL REFERENCES smartportx_yard.yard_rows(id),
    tier_number     INT           NOT NULL,
    container_id    UUID          NULL,       -- FK reference (logical, not SQL FK)
    container_type  VARCHAR(20)   NULL,       -- cached: '20DC', '40DC', '40HC', '45HC', '20RF', '40RF'
    occupied        BOOLEAN       NOT NULL DEFAULT FALSE,
    weight_kg       FLOAT         NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE (row_id, tier_number)
);
```

**Design Notes:**
- `container_id` references a container in `smartportx_container.containers` but is **not** a SQL foreign key — it is a logical reference maintained via events.
- `container_type` and `weight_kg` are cached from Container Service events to avoid cross-schema lookups.
- The `occupied` boolean is the single source of truth for slot availability.

#### 6.5 yard_metrics

Daily aggregated metrics per block for analytics and reporting.

```sql
CREATE TABLE smartportx_yard.yard_metrics (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    block_id              UUID          NOT NULL REFERENCES smartportx_yard.yard_blocks(id),
    date                  DATE          NOT NULL,
    total_slots           INT           NOT NULL,
    occupied_slots        INT           NOT NULL,
    utilization_rate      FLOAT         NOT NULL,  -- occupied_slots / total_slots
    avg_dwell_time_hours  FLOAT         NULL,
    reshuffling_count     INT           NOT NULL DEFAULT 0,
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE (block_id, date)
);
```

**Computation:** Metrics are calculated by a nightly batch job that queries `yard_tiers` occupancy and `yard_metrics` historical data.

#### 6.6 optimization_history

Records each yard optimization run for audit and performance comparison.

```sql
CREATE TABLE smartportx_yard.optimization_history (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    block_id                       UUID          NOT NULL REFERENCES smartportx_yard.yard_blocks(id),
    strategy                       VARCHAR(30)   NOT NULL,  -- 'GREEDY', 'DYNAMIC', 'ML_PREDICTIVE'
    container_count                INT           NOT NULL,
    estimated_reshuffling_reduction FLOAT        NULL,
    execution_time_ms              INT           NULL,
    executed_by                    UUID          NOT NULL,  -- user ID from Identity Service
    created_at                     TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

---

## 7. Schema: smartportx_container

**Purpose:** Container registry, movement tracking, event sourcing, and status history. This is the **central domain schema** — the source of truth for all container data.

### Tables

#### 7.1 containers

Master container record. Single source of truth for container metadata.

```sql
CREATE TABLE smartportx_container.containers (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    container_number      VARCHAR(11)   NOT NULL UNIQUE,
    container_type        VARCHAR(10)   NOT NULL,  -- '20DC', '40DC', '40HC', '45HC', '20RF', '40RF'
    owner                 VARCHAR(100)  NULL,
    consignee             VARCHAR(100)  NULL,
    shipper               VARCHAR(100)  NULL,
    weight_kg             FLOAT         NULL,
    tare_weight_kg        FLOAT         NULL,
    seal_number           VARCHAR(20)   NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'REGISTERED',  -- see status enum
    current_location      VARCHAR(50)   NULL,  -- free-text location reference (e.g., 'BLOCK-A-BAY-03-ROW-02-TIER-01')
    dangerous_goods       BOOLEAN       NOT NULL DEFAULT FALSE,
    imdg_class            VARCHAR(10)   NULL,
    un_number             VARCHAR(10)   NULL,
    proper_shipping_name  VARCHAR(100)  NULL,
    packing_group         VARCHAR(5)    NULL,
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

**Container Status Enum:**
| Status | Description |
|---|---|
| `REGISTERED` | Container entered the system but not yet at port |
| `AT_GATE` | Container at gate check-in |
| `IN_YARD` | Container stored in yard |
| `LOADED` | Container loaded onto vessel |
| `DISCHARGED` | Container discharged from vessel |
| `AT_GATE_OUT` | Container at gate for exit |
| `DEPARTED` | Container has left the port |
| `HELD` | Container on customs hold |

**Container Type Codes:**
| Code | Description |
|---|---|
| `20DC` | 20ft Dry Container |
| `40DC` | 40ft Dry Container |
| `40HC` | 40ft High Cube |
| `45HC` | 45ft High Cube |
| `20RF` | 20ft Reefer |
| `40RF` | 40ft Reefer |

#### 7.2 container_movements

Immutable log of all physical container movements.

```sql
CREATE TABLE smartportx_container.container_movements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    container_id    UUID          NOT NULL REFERENCES smartportx_container.containers(id),
    movement_type   VARCHAR(30)   NOT NULL,  -- 'GATE_IN', 'GATE_OUT', 'YARD_MOVE', 'VESSEL_LOAD', 'VESSEL_DISCHARGE'
    from_location   VARCHAR(50)  NULL,
    to_location     VARCHAR(50)  NULL,
    timestamp       TIMESTAMP    NOT NULL,
    performed_by    VARCHAR(100) NOT NULL,  -- operator name or system identifier
    reason          VARCHAR(255) NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

#### 7.3 container_events

Domain event log (event sourcing). Append-only table.

```sql
CREATE TABLE smartportx_container.container_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    container_id    UUID          NOT NULL REFERENCES smartportx_container.containers(id),
    event_type      VARCHAR(30)   NOT NULL,
    event_data      JSONB         NOT NULL,  -- full event payload
    timestamp       TIMESTAMP     NOT NULL,
    correlation_id  UUID          NOT NULL,  -- distributed tracing ID
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

**Index:** `(container_id, timestamp DESC)` for efficient event replay per container.

#### 7.4 container_status_history

Tracks every status transition with before/after values.

```sql
CREATE TABLE smartportx_container.container_status_history (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    container_id     UUID          NOT NULL REFERENCES smartportx_container.containers(id),
    previous_status  VARCHAR(20)   NULL,  -- NULL for initial registration
    new_status       VARCHAR(20)   NOT NULL,
    changed_by       UUID          NOT NULL,  -- user ID from Identity Service
    reason           VARCHAR(255)  NULL,
    changed_at       TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

---

## 8. Schema: smartportx_gate

**Purpose:** Gate appointment scheduling, truck check-in/check-out transactions, queue management, and appointment quota enforcement.

### Tables

#### 8.1 appointments

Scheduled gate appointments for trucks.

```sql
CREATE TABLE smartportx_gate.appointments (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    confirmation_number   VARCHAR(20)   NOT NULL UNIQUE,
    trucking_company      VARCHAR(100)  NOT NULL,
    truck_license_plate   VARCHAR(20)   NOT NULL,
    driver_name           VARCHAR(100)  NOT NULL,
    driver_license        VARCHAR(30)   NOT NULL,
    appointment_type      VARCHAR(10)   NOT NULL,  -- 'PICKUP', 'DROPOFF'
    container_number      VARCHAR(11)   NOT NULL,
    preferred_time_slot   TIMESTAMP     NOT NULL,
    actual_arrival        TIMESTAMP     NULL,
    duration_minutes      INT           NOT NULL DEFAULT 30,
    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- see status enum
    yard_location         VARCHAR(50)   NULL,
    notes                 TEXT          NULL,
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

**Appointment Status Enum:**
| Status | Description |
|---|---|
| `PENDING` | Created, awaiting confirmation |
| `CONFIRMED` | Time slot confirmed |
| `CHECKED_IN` | Truck has arrived at gate |
| `IN_PROGRESS` | Loading/unloading in progress |
| `COMPLETED` | Transaction finished |
| `CANCELLED` | Cancelled by operator or system |
| `NO_SHOW` | Truck did not arrive within grace period |

#### 8.2 gate_transactions

Physical gate check-in/check-out records.

```sql
CREATE TABLE smartportx_gate.gate_transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id      UUID          NOT NULL REFERENCES smartportx_gate.appointments(id),
    gate_type           VARCHAR(10)   NOT NULL,  -- 'INBOUND', 'OUTBOUND'
    truck_license_plate VARCHAR(20)   NOT NULL,
    driver_name         VARCHAR(100)  NOT NULL,
    driver_license      VARCHAR(30)   NOT NULL,
    container_number    VARCHAR(11)   NOT NULL,
    seal_number         VARCHAR(20)   NULL,
    documents           JSONB         NULL,  -- document verification results
    check_in_time       TIMESTAMP     NOT NULL,
    check_out_time      TIMESTAMP     NULL,
    yard_location       VARCHAR(50)   NULL,
    eir_number          VARCHAR(30)   NULL,  -- Equipment Interchange Receipt
    status              VARCHAR(20)   NOT NULL DEFAULT 'CHECKED_IN',
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

#### 8.3 queue_status

Periodic snapshots of gate queue state.

```sql
CREATE TABLE smartportx_gate.queue_status (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_time           TIMESTAMP     NOT NULL,
    waiting_trucks          INT           NOT NULL DEFAULT 0,
    pickup_count            INT           NOT NULL DEFAULT 0,
    dropoff_count           INT           NOT NULL DEFAULT 0,
    avg_wait_time_minutes   FLOAT         NULL,
    estimated_clearance     TIMESTAMP     NULL
);
```

**Capture Frequency:** Updated every 5 minutes by a scheduled task or on every gate transaction.

#### 8.4 appointment_quotas

Time-window-based appointment capacity management.

```sql
CREATE TABLE smartportx_gate.appointment_quotas (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    time_window_start     TIMESTAMP     NOT NULL,
    time_window_end       TIMESTAMP     NOT NULL,
    max_appointments      INT           NOT NULL,
    current_bookings      INT           NOT NULL DEFAULT 0,
    appointment_type      VARCHAR(10)   NOT NULL,  -- 'PICKUP', 'DROPOFF'
    dynamic_adjustment    BOOLEAN       NOT NULL DEFAULT FALSE,
    adjusted_by           UUID          NULL,  -- admin user ID
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

**Dynamic Adjustment:** Admins can override quotas in real-time to handle peak congestion. When `dynamic_adjustment = TRUE`, the `adjusted_by` user is recorded for audit.

---

## 9. Schema: smartportx_vessel

**Purpose:** Vessel registry, scheduling, discharge planning, and container-by-container discharge tracking.

### Tables

#### 9.1 vessels

Master vessel registry.

```sql
CREATE TABLE smartportx_vessel.vessels (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imo_number    VARCHAR(10)   NOT NULL UNIQUE,
    vessel_name   VARCHAR(100)  NOT NULL,
    vessel_type   VARCHAR(20)   NOT NULL,  -- 'CONTAINER', 'BULK', 'TANKER', 'RO-RO'
    length_m      FLOAT         NULL,
    beam_m        FLOAT         NULL,
    draft_m       FLOAT         NULL,
    teu_capacity  INT           NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

#### 9.2 vessel_schedules

Scheduled vessel calls (arrivals/departures).

```sql
CREATE TABLE smartportx_vessel.vessel_schedules (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vessel_id     UUID          NOT NULL REFERENCES smartportx_vessel.vessels(id),
    eta           TIMESTAMP     NOT NULL,  -- Estimated Time of Arrival
    etd           TIMESTAMP     NULL,      -- Estimated Time of Departure
    berth_number  INT           NULL,
    priority      VARCHAR(10)   NOT NULL DEFAULT 'NORMAL',  -- 'HIGH', 'NORMAL', 'LOW'
    status        VARCHAR(20)   NOT NULL DEFAULT 'SCHEDULED',  -- see status enum
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

**Schedule Status Enum:**
| Status | Description |
|---|---|
| `SCHEDULED` | Vessel call planned |
| `IN_TRANSIT` | Vessel en route to port |
| `ARRIVED` | Vessel at berth |
| `WORKING` | Discharge/loading in progress |
| `DEPARTED` | Vessel has left |
| `CANCELLED` | Call cancelled |

#### 9.3 discharge_plans

Top-level discharge plan per vessel call.

```sql
CREATE TABLE smartportx_vessel.discharge_plans (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vessel_id         UUID          NOT NULL REFERENCES smartportx_vessel.vessels(id),
    schedule_id       UUID          NOT NULL REFERENCES smartportx_vessel.vessel_schedules(id),
    plan_name         VARCHAR(100)  NOT NULL,
    total_containers  INT           NOT NULL DEFAULT 0,
    status            VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',  -- 'DRAFT', 'APPROVED', 'IN_PROGRESS', 'COMPLETED'
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

#### 9.4 discharge_containers

Per-container discharge assignment and tracking.

```sql
CREATE TABLE smartportx_vessel.discharge_containers (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    discharge_plan_id     UUID          NOT NULL REFERENCES smartportx_vessel.discharge_plans(id),
    container_number      VARCHAR(11)   NOT NULL,
    vessel_position       VARCHAR(10)   NOT NULL,  -- bay-row-tier position on vessel
    yard_target           VARCHAR(50)   NOT NULL,   -- target yard location
    priority              VARCHAR(10)   NOT NULL DEFAULT 'NORMAL',
    status                VARCHAR(20)   NOT NULL DEFAULT 'PLANNED',  -- see status enum
    actual_yard_position  VARCHAR(50)   NULL,
    discharged_at         TIMESTAMP     NULL,
    stacked_at            TIMESTAMP     NULL,
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

**Discharge Status Enum:**
| Status | Description |
|---|---|
| `PLANNED` | Container assigned to discharge plan |
| `DISCHARGING` | Being lifted off vessel |
| `DISCHARGED` | Off vessel, in transit to yard |
| `STACKED` | Placed in yard position |
| `MISROUTED` | Placed in wrong position (exception) |

---

## 10. Schema: smartportx_integration

**Purpose:** EDIFACT message processing, IoT sensor data ingestion, customs status tracking, and integration audit logs.

### Tables

#### 10.1 edifact_messages

Inbound/outbound EDIFACT message processing.

```sql
CREATE TABLE smartportx_integration.edifact_messages (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_type      VARCHAR(10)   NOT NULL,  -- 'IFTMIN', 'IFTSTA', 'CODINT', etc.
    raw_message       TEXT          NOT NULL,
    parsed_data       JSONB         NULL,       -- structured extraction
    correlation_id    UUID          NOT NULL,
    validation_errors JSONB         NULL,       -- array of validation error objects
    status            VARCHAR(20)   NOT NULL DEFAULT 'RECEIVED',  -- 'RECEIVED', 'PARSED', 'PROCESSED', 'ERROR'
    received_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    processed_at      TIMESTAMP     NULL
);
```

**Common EDIFACT Message Types:**
| Code | Name | Purpose |
|---|---|---|
| `IFTMIN` | Instructions for Transport | Inbound shipment instructions |
| `IFTSTA` | Transport Status | Shipment status update |
| `CODINT` | Container Discharge Instruction | Discharge command |
| `COARRI` | Container Discharge/Loading Report | Completion report |
| `IFTCCA` | Transport Cost Accounting | Cost data |
| `CUSRES` | Customs Response | Customs clearance data |

#### 10.2 iot_readings

IoT sensor data from container-mounted sensors.

```sql
CREATE TABLE smartportx_integration.iot_readings (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    container_number  VARCHAR(11)   NOT NULL,
    sensor_type       VARCHAR(20)   NOT NULL,  -- 'TEMPERATURE', 'HUMIDITY', 'GPS', 'SHOCK', 'LIGHT'
    value             VARCHAR(100)  NOT NULL,
    unit              VARCHAR(20)   NULL,
    timestamp         TIMESTAMP     NOT NULL,
    battery_level     INT           NULL,       -- percentage 0-100
    signal_strength   INT           NULL,       -- dBm
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

**Index:** `(container_number, sensor_type, timestamp DESC)` for efficient sensor queries.

#### 10.3 customs_status

Customs hold and clearance status per container.

```sql
CREATE TABLE smartportx_integration.customs_status (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    container_number  VARCHAR(11)   NOT NULL UNIQUE,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- see status enum
    reason            TEXT          NULL,
    cleared_by        VARCHAR(100)  NULL,
    cleared_at        TIMESTAMP     NULL,
    hold_until        TIMESTAMP     NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

**Customs Status Enum:**
| Status | Description |
|---|---|
| `PENDING` | Awaiting customs clearance |
| `CLEARED` | Released by customs |
| `HELD` | Under customs hold |
| `EXAMINED` | Physical examination required |
| `RELEASED` | Hold released, cleared for movement |

#### 10.4 integration_logs

Audit trail for all external integration calls.

```sql
CREATE TABLE smartportx_integration.integration_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name    VARCHAR(50)   NOT NULL,
    operation       VARCHAR(50)   NOT NULL,
    status          VARCHAR(20)   NOT NULL,  -- 'SUCCESS', 'FAILED', 'TIMEOUT'
    request_data    JSONB         NULL,
    response_data   JSONB         NULL,
    error_message   TEXT          NULL,
    timestamp       TIMESTAMP     NOT NULL DEFAULT NOW(),
    correlation_id  UUID          NOT NULL
);
```

---

## 11. Schema: smartportx_notification

**Purpose:** User notification preferences and delivery history.

### Tables

#### 11.1 notification_preferences

Per-user notification channel and category preferences.

```sql
CREATE TABLE smartportx_notification.notification_preferences (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID          NOT NULL,  -- logical reference to Identity Service
    email_enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    websocket_enabled BOOLEAN       NOT NULL DEFAULT TRUE,
    categories        JSONB         NOT NULL DEFAULT '[]',  -- array of subscribed category strings
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);
```

**Notification Categories:**
- `GATE` — Gate appointment and transaction events.
- `YARD` — Yard optimization and occupancy alerts.
- `VESSEL` — Vessel arrival, departure, discharge progress.
- `CUSTOMS` — Customs hold/clearance updates.
- `IOT` — IoT sensor alerts (temperature excursion, tamper, etc.).
- `SYSTEM` — System-wide announcements and maintenance windows.

#### 11.2 notification_history

Delivered notification records.

```sql
CREATE TABLE smartportx_notification.notification_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL,
    category    VARCHAR(30)   NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    message     TEXT          NOT NULL,
    channel     VARCHAR(20)   NOT NULL,  -- 'EMAIL', 'WEBSOCKET', 'BOTH'
    status      VARCHAR(20)   NOT NULL DEFAULT 'SENT',  -- 'SENT', 'DELIVERED', 'FAILED'
    sent_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    read_at     TIMESTAMP     NULL
);
```

---

## 12. Cross-Service Data Access Patterns

Services must never execute cross-schema SQL queries. All cross-service data access follows one of two patterns:

### Pattern A: Event-Driven Cache (Asynchronous)

Service subscribes to domain events published by another service and maintains a local projection/cache of the needed data.

```
┌──────────────────┐    Kafka Event     ┌──────────────────┐
│ Container Service│ ─────────────────> │ Yard Service     │
│ (publishes)      │                    │ (subscribes,     │
│                  │                    │  caches in       │
│                  │                    │  yard_tiers)     │
└──────────────────┘                    └──────────────────┘
```

**Use Cases:**
1. **Yard Service ← Container Service:** Subscribes to `ContainerRegistered`, `ContainerStatusChanged`, `ContainerMoved` events. Caches `container_id`, `container_type`, and `weight_kg` in `yard_tiers` to enable fast occupancy queries without cross-schema joins.

### Pattern B: REST API Call (Synchronous)

Service calls another service's REST API for real-time data that cannot tolerate staleness.

```
┌──────────────────┐   REST API Call    ┌──────────────────┐
│ Gate Service     │ ─────────────────> │ Container Service│
│ (calls)          │ <───────────────── │ (responds)       │
│                  │   container data   │                  │
└──────────────────┘                    └──────────────────┘
```

**Use Cases:**
2. **Gate Service → Container Service:** Calls Container Service REST API to validate `container_number` exists and retrieve current status before creating gate transactions. Required for real-time validation.
3. **Vessel Service → Container Service:** Calls Container Service REST API during discharge planning to retrieve container dimensions, weight, and special handling requirements.

### Pattern C: Event Fan-Out (Notification)

All services publish events; Notification Service subscribes to all of them for real-time push delivery.

```
┌──────────┐  events  ┌──────────────────────────┐  push  ┌──────────┐
│ All      │ ───────> │ Notification Service      │ ────> │ End User │
│ Services │          │ (subscribes to all,       │        │ (email,  │
│          │          │  fans out to users)       │        │  WS)     │
└──────────┘          └──────────────────────────┘        └──────────┘
```

**Use Cases:**
4. **All Services → Notification Service:** Every service publishes domain events. Notification Service filters events by user preferences and delivers via email or WebSocket.

### Pattern D: Integration Event Distribution

Integration Service parses external messages and distributes structured data to relevant services.

```
┌──────────────────┐   EDIFACT parsed   ┌──────────────────┐
│ Integration      │ ─────────────────> │ Yard Service     │
│ Service          │ ─────────────────> │ Container Service│
│ (subscribes to   │ ─────────────────> │ Gate Service     │
│  external systems)│                   └──────────────────┘
└──────────────────┘
```

**Use Cases:**
5. **Integration Service → All Services:** Publishes `EDIFACTParsed` events containing structured shipment data. Yard, Container, and Gate services subscribe to receive parsed EDIFACT instructions.

### Summary Matrix

| Consumer | Provider | Pattern | Data Accessed | Latency Tolerance |
|---|---|---|---|---|
| Yard Service | Container Service | Event-Driven Cache | container_id, type, weight | Seconds |
| Gate Service | Container Service | REST API Call | Container existence, status | Milliseconds |
| Vessel Service | Container Service | REST API Call | Container dimensions, weight | Milliseconds |
| Notification Service | All Services | Event Fan-Out | Event data | Seconds |
| Yard/Gate/Container | Integration Service | Event Distribution | EDIFACT parsed data | Seconds |

---

## 13. Domain Event Architecture

### Event Bus

All inter-service communication flows through **Apache Kafka**. Each service publishes to its own topic and subscribes to topics of interest.

### Kafka Topic Naming

```
smartportx.events.<aggregate-type>.<event-type>
```

**Examples:**
- `smartportx.events.container.container-moved`
- `smartportx.events.gate.gate-check-in`
- `smartportx.events.vessel.vessel-arrived`

### Event Envelope Schema

Every domain event follows a standard envelope format:

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "ContainerMoved",
  "aggregateId": "550e8400-e29b-41d4-a716-446655440001",
  "aggregateType": "Container",
  "timestamp": "2026-08-25T14:30:00Z",
  "version": 1,
  "data": {
    "containerNumber": "MSKU1234567",
    "containerType": "40HC",
    "fromLocation": "BLOCK-A-BAY-03-ROW-02-TIER-01",
    "toLocation": "BLOCK-B-BAY-01-ROW-01-TIER-03",
    "movementType": "YARD_MOVE",
    "reason": "Optimization reshuffle"
  },
  "metadata": {
    "correlationId": "660e8400-e29b-41d4-a716-446655440002",
    "userId": "770e8400-e29b-41d4-a716-446655440003",
    "source": "yard-service"
  }
}
```

### Envelope Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `eventId` | UUID | Yes | Unique event identifier (idempotency key) |
| `eventType` | String | Yes | Domain event type name (PascalCase) |
| `aggregateId` | UUID | Yes | ID of the aggregate root this event relates to |
| `aggregateType` | String | Yes | Aggregate type: `Container`, `Appointment`, `Vessel`, `YardBlock`, etc. |
| `timestamp` | ISO 8601 | Yes | Event creation time (UTC) |
| `version` | Int | Yes | Schema version for forward compatibility |
| `data` | Object | Yes | Event-specific payload |
| `metadata.correlationId` | UUID | Yes | Distributed tracing correlation ID |
| `metadata.userId` | UUID | Yes | User who triggered the action (or system user ID) |
| `metadata.source` | String | Yes | Publishing service name |

### Event Versioning

When an event schema changes:
1. Bump the `version` field.
2. Consumers must handle both old and new versions during a transition period.
3. Deprecated fields are marked with `@Deprecated` in the Java record/class but remain in the payload.
4. After the transition period (default: 30 days), the old version is removed.

---

## 14. Event Type Catalog

### Container Events

| Event Type | Trigger | Payload Highlights |
|---|---|---|
| `ContainerRegistered` | New container enters system | containerNumber, type, owner, weight |
| `ContainerStatusChanged` | Status transition | previousStatus, newStatus, reason |
| `ContainerMoved` | Physical location change | fromLocation, toLocation, movementType |
| `ContainerRemoved` | Container exits system | reason, finalLocation |

### Yard Events

| Event Type | Trigger | Payload Highlights |
|---|---|---|
| `YardPositionAssigned` | Container placed in tier | blockCode, bayNumber, rowNumber, tierNumber, containerId |
| `YardPositionFreed` | Container removed from tier | blockCode, bayNumber, rowNumber, tierNumber |
| `YardOptimizationRun` | Optimization algorithm completes | strategy, containerCount, reshufflingReduction |

### Gate Events

| Event Type | Trigger | Payload Highlights |
|---|---|---|
| `AppointmentBooked` | New appointment created | confirmationNumber, truckingCompany, containerNumber, timeSlot |
| `AppointmentConfirmed` | Appointment confirmed | confirmationNumber, assignedYardLocation |
| `AppointmentCancelled` | Appointment cancelled | confirmationNumber, reason |
| `AppointmentCompleted` | Transaction finished | confirmationNumber, duration |
| `GateCheckIn` | Truck enters gate | truckLicensePlate, containerNumber, gateType |
| `GateCheckOut` | Truck exits gate | truckLicensePlate, eirNumber, duration |
| `GateQueueUpdated` | Queue state changed | waitingTrucks, avgWaitTime |

### Vessel Events

| Event Type | Trigger | Payload Highlights |
|---|---|---|
| `VesselArrived` | Vessel berths | vesselName, imoNumber, berthNumber |
| `VesselDeparted` | Vessel leaves port | vesselName, imoNumber, departedAt |
| `DischargePlanCreated` | New discharge plan | planName, totalContainers, vesselId |
| `DischargeProgressUpdated` | Discharge milestone | planId, dischargedCount, totalCount |

### Integration Events

| Event Type | Trigger | Payload Highlights |
|---|---|---|
| `EDIFACTReceived` | Raw EDIFACT message ingested | messageType, rawMessage, correlationId |
| `EDIFACTParsed` | EDIFACT successfully parsed | messageType, parsedData |
| `EDIFACTError` | EDIFACT parsing/validation failed | messageType, validationErrors |

### IoT Events

| Event Type | Trigger | Payload Highlights |
|---|---|---|
| `IoTSensorData` | Sensor reading received | containerNumber, sensorType, value, unit |
| `IoTAlert` | Threshold breach detected | containerNumber, sensorType, alertLevel, value |

### Customs Events

| Event Type | Trigger | Payload Highlights |
|---|---|---|
| `CustomsStatusUpdated` | Customs status changed | containerNumber, previousStatus, newStatus |
| `CustomsHoldPlaced` | Container held by customs | containerNumber, reason, holdUntil |
| `CustomsHoldReleased` | Hold lifted | containerNumber, clearedBy, clearedAt |

---

## 15. Data Consistency Strategy

### Write Path (No Cross-Service Writes)

Services never write to another service's schema. All writes are local to the owning service.

### Read Path (Eventual Consistency)

When Service A needs data owned by Service B for a read operation:

1. **Accept staleness:** If data can be up to a few seconds old, use the event-driven cache pattern.
2. **Call REST API:** If data must be current (e.g., gate validation), call the owning service's REST API synchronously.

### Idempotency

All event consumers must be idempotent:
- Events carry a unique `eventId` (UUID).
- Consumers track processed `eventId`s in a local `processed_events` table.
- Duplicate delivery is detected and silently ignored.

```sql
-- Example: processed_events table (in each consumer's schema)
CREATE TABLE smartportx_<service>.processed_events (
    event_id    UUID PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Outbox Pattern

To ensure events are published reliably (atomicity between DB write and event publish):

1. Service writes domain change + event record to its own database in a single transaction.
2. A background poller reads unpublished events and publishes to Kafka.
3. Events are marked as published after successful Kafka acknowledgment.

```sql
-- Example: outbox table (in each publisher's schema)
CREATE TABLE smartportx_<service>.event_outbox (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type    VARCHAR(100) NOT NULL,
    payload       JSONB        NOT NULL,
    published     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    published_at  TIMESTAMP    NULL
);
```

### Conflict Resolution

Since services never write to each other's schemas, write conflicts do not arise. Read conflicts (stale data) are resolved by:
- Accepting eventual consistency for non-critical reads.
- Falling back to REST API calls for critical real-time reads.
- Retry logic with exponential backoff for transient failures.

---

## 16. Data Retention & Archival Policy

| Data Type | Schema | Retention Period | Archival Strategy | Deletion Method |
|---|---|---|---|---|
| Container records | `smartportx_container` | Indefinite | None | Hard delete only on explicit request (GDPR/data subject) |
| Container movements | `smartportx_container` | 12 months | Compress old records to summary | Scheduled batch delete + insert summary |
| Container events | `smartportx_container` | 24 months | Immutable, no deletion | None (audit requirement) |
| Container status history | `smartportx_container` | 24 months | Immutable, no deletion | None (audit requirement) |
| Gate transactions | `smartportx_gate` | 12 months | Archive to cold storage (S3/GCS) | Batch delete after archive confirmation |
| Appointments | `smartportx_gate` | 6 months | Archive completed/cancelled | Batch delete after archive |
| Queue status snapshots | `smartportx_gate` | 3 months | Aggregate and delete raw | Nightly aggregation job |
| Appointment quotas | `smartportx_gate` | Indefinite | None | Hard delete on explicit admin action |
| IoT readings | `smartportx_integration` | 3 months | Aggregate hourly/daily summaries | Batch delete raw, retain aggregates |
| EDIFACT messages | `smartportx_integration` | 12 months | Archive raw, keep parsed data | Batch delete raw TEXT after archive |
| Integration logs | `smartportx_integration` | 6 months | Archive to cold storage | Batch delete after archive |
| Customs status | `smartportx_integration` | Indefinite | None | Hard delete on explicit request |
| Notifications | `smartportx_notification` | 6 months | Delete read notifications | Nightly cleanup job |
| Notification preferences | `smartportx_notification` | Indefinite | None | Delete on user account deletion |
| Refresh tokens | `smartportx_identity` | 30 days | None | Nightly cleanup of expired/revoked |
| Password history | `smartportx_identity` | 2 years | None | Retain for compliance |
| Audit events | All schemas | 24 months | Immutable, no deletion | None (regulatory requirement) |
| Optimization history | `smartportx_yard` | Indefinite | None | Hard delete on explicit admin action |
| Yard metrics | `smartportx_yard` | 24 months | Aggregate to monthly summaries | Batch delete after aggregation |

### Retention Job Execution

- **Nightly:** Expired refresh tokens, old queue status snapshots, read notifications.
- **Weekly:** IoT raw readings older than 3 months, old EDIFACT raw messages.
- **Monthly:** Container movements older than 12 months, gate transactions older than 12 months, integration logs older than 6 months.
- **Quarterly:** Compliance review of immutable audit tables.

---

## 17. Schema Migration Strategy

### Tool: Flyway

All schema changes are managed via Flyway migrations. Each service runs its own Flyway instance against its own schema.

### Naming Convention

```
V{version}__{description}.sql
```

Examples:
- `V1__create_containers_table.sql`
- `V2__add_dangerous_goods_columns.sql`
- `V3__create_container_events_index.sql`

### Migration Rules

1. **Never modify a committed migration.** Create a new one instead.
2. **Migrations must be backward-compatible.** Old code must work with the new schema during rolling deployments.
3. **Column additions** are preferred over column renames (which require multi-step migration).
4. **Column renames** follow: add new → copy data → update code → drop old.
5. **Table renames** follow: create new → copy data → update code → drop old.
6. **All migrations must include rollback notes** in the migration file comments (Flyway doesn't auto-rollback).

### Validation

- `flyway validate` runs in CI before merge.
- `flyway info` verifies migration state before deployment.
- Schema checksum comparison between environments catches drift.

---

## 18. Indexing Strategy

### General Principles

- Index all foreign key columns (even UUIDs).
- Index frequently queried columns (status, timestamps, container_number).
- Use composite indexes for multi-column WHERE clauses.
- Avoid over-indexing — each index impacts write performance.

### Indexes per Schema

#### smartportx_container
```sql
CREATE INDEX idx_containers_status ON smartportx_container.containers(status);
CREATE INDEX idx_containers_container_number ON smartportx_container.containers(container_number);
CREATE INDEX idx_containers_current_location ON smartportx_container.containers(current_location);
CREATE INDEX idx_movements_container_id ON smartportx_container.container_movements(container_id);
CREATE INDEX idx_movements_timestamp ON smartportx_container.container_movements(timestamp DESC);
CREATE INDEX idx_events_container_id_timestamp ON smartportx_container.container_events(container_id, timestamp DESC);
CREATE INDEX idx_status_history_container_id ON smartportx_container.container_status_history(container_id);
```

#### smartportx_yard
```sql
CREATE INDEX idx_yard_tiers_row_id ON smartportx_yard.yard_tiers(row_id);
CREATE INDEX idx_yard_tiers_occupied ON smartportx_yard.yard_tiers(occupied);
CREATE INDEX idx_yard_tiers_container_id ON smartportx_yard.yard_tiers(container_id);
CREATE INDEX idx_yard_metrics_block_date ON smartportx_yard.yard_metrics(block_id, date DESC);
CREATE INDEX idx_optimization_history_block ON smartportx_yard.optimization_history(block_id, created_at DESC);
```

#### smartportx_gate
```sql
CREATE INDEX idx_appointments_status ON smartportx_gate.appointments(status);
CREATE INDEX idx_appointments_time_slot ON smartportx_gate.appointments(preferred_time_slot);
CREATE INDEX idx_appointments_container_number ON smartportx_gate.appointments(container_number);
CREATE INDEX idx_gate_transactions_appointment ON smartportx_gate.gate_transactions(appointment_id);
CREATE INDEX idx_gate_transactions_container ON smartportx_gate.gate_transactions(container_number);
CREATE INDEX idx_gate_transactions_check_in ON smartportx_gate.gate_transactions(check_in_time DESC);
```

#### smartportx_vessel
```sql
CREATE INDEX idx_vessel_schedules_vessel ON smartportx_vessel.vessel_schedules(vessel_id);
CREATE INDEX idx_vessel_schedules_eta ON smartportx_vessel.vessel_schedules(eta);
CREATE INDEX idx_discharge_plans_schedule ON smartportx_vessel.discharge_plans(schedule_id);
CREATE INDEX idx_discharge_containers_plan ON smartportx_vessel.discharge_containers(discharge_plan_id);
CREATE INDEX idx_discharge_containers_container ON smartportx_vessel.discharge_containers(container_number);
```

#### smartportx_integration
```sql
CREATE INDEX idx_edifact_status ON smartportx_integration.edifact_messages(status);
CREATE INDEX idx_edifact_correlation ON smartportx_integration.edifact_messages(correlation_id);
CREATE INDEX idx_iot_container_sensor ON smartportx_integration.iot_readings(container_number, sensor_type, timestamp DESC);
CREATE INDEX idx_iot_timestamp ON smartportx_integration.iot_readings(timestamp);
CREATE INDEX idx_customs_container ON smartportx_integration.customs_status(container_number);
CREATE INDEX idx_integration_logs_correlation ON smartportx_integration.integration_logs(correlation_id);
CREATE INDEX idx_integration_logs_service ON smartportx_integration.integration_logs(service_name, timestamp DESC);
```

#### smartportx_identity
```sql
CREATE INDEX idx_users_username ON smartportx_identity.users(username);
CREATE INDEX idx_users_email ON smartportx_identity.users(email);
CREATE INDEX idx_refresh_tokens_user ON smartportx_identity.refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON smartportx_identity.refresh_tokens(token_hash);
CREATE INDEX idx_password_history_user ON smartportx_identity.password_history(user_id);
```

---

## 19. Backup & Recovery

### Backup Strategy

| Component | Method | Frequency | Retention |
|---|---|---|---|
| Full database | `pg_dump` to compressed file | Daily at 02:00 UTC | 30 days |
| WAL archiving | Continuous WAL shipping | Continuous | 7 days |
| Schema-level dumps | Per-schema `pg_dump` | Weekly | 90 days |
| Logical replication | Streaming to standby | Continuous | Real-time |

### Recovery Objectives

| Metric | Target |
|---|---|
| RPO (Recovery Point Objective) | < 5 minutes (WAL-based) |
| RTO (Recovery Time Objective) | < 30 minutes |

### Recovery Procedures

1. **Point-in-time recovery:** Use WAL archives to restore to any point within the 7-day window.
2. **Schema-level restore:** Restore individual service schemas without affecting others.
3. **Full restore:** Last resort; restore entire instance from daily dump + WAL replay.

---

## 20. Security & Access Control

### Database User Per Service

Each microservice connects with a dedicated PostgreSQL user scoped to its schema:

```sql
-- Example: Container Service database user
CREATE USER smartportx_container_svc WITH PASSWORD '<encrypted>';
GRANT USAGE ON SCHEMA smartportx_container TO smartportx_container_svc;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA smartportx_container TO smartportx_container_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA smartportx_container GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO smartportx_container_svc;
```

### Access Rules

- **No cross-schema access:** Service users cannot `SELECT` from other schemas.
- **Read-only users** for reporting/analytics: `GRANT SELECT ON ALL TABLES IN SCHEMA smartportx_<service> TO smartportx_<service>_readonly;`
- **No DDL grants** for application users — migrations run via a separate admin user.
- **Password rotation:** Database passwords rotated every 90 days via secrets manager.

### Encryption

- **At rest:** PostgreSQL Transparent Data Encryption (TDE) or disk-level encryption.
- **In transit:** All connections require SSL/TLS (`sslmode=verify-full`).
- **Sensitive columns:** `password_hash` uses bcrypt (cost factor 12). `token_hash` uses SHA-256. `seal_number` and `driver_license` are encrypted at the application level (AES-256-GCM).

### Row-Level Security

Not implemented at the database level — access control is handled at the application layer via Spring Security RBAC.

---

## 21. Monitoring & Observability

### Database Metrics

| Metric | Alert Threshold | Tool |
|---|---|---|
| Connection pool utilization | > 80% | Micrometer + Prometheus |
| Query latency (p99) | > 500ms | pg_stat_statements |
| Deadlocks | > 0 | PostgreSQL log + Prometheus |
| Replication lag | > 30 seconds | pg_stat_replication |
| Disk usage | > 85% | Node exporter |
| Cache hit ratio | < 95% | pg_stat_database |

### Slow Query Detection

- Enable `log_min_duration_statement = 1000` (queries > 1 second).
- Review `pg_stat_statements` weekly for query optimization.
- EXPLAIN ANALYZE for any query exceeding 500ms.

### Schema Drift Detection

- Flyway's schema checksum catches unauthorized DDL changes.
- Weekly comparison of production schema against migration history.
- Alerts on schema mismatch.

---

*End of Data Architecture Document — SmartPortX v1.0*
