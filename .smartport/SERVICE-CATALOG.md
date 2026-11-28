# SmartPortX — Service Catalog

> **Version:** 1.0.0
> **Architecture:** Service-Based Architecture (SBA)
> **Stack:** Java 25 · Spring Boot 3.x · PostgreSQL 17.11
> **Schema Strategy:** Schema-per-service (database-per-service isolation)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Detailed Service Profiles](#2-detailed-service-profiles)
   - 2.1 [smartportx-gateway](#21-smartportx-gateway)
   - 2.2 [smartportx-identity](#22-smartportx-identity)
   - 2.3 [smartportx-yard](#23-smartportx-yard)
   - 2.4 [smartportx-gate](#24-smartportx-gate)
   - 2.5 [smartportx-container](#25-smartportx-container)
   - 2.6 [smartportx-vessel](#26-smartportx-vessel)
   - 2.7 [smartportx-integration](#27-smartportx-integration)
   - 2.8 [smartportx-notification](#28-smartportx-notification)
3. [Inter-Service Dependency Matrix](#3-inter-service-dependency-matrix)
4. [Service Communication Patterns](#4-service-communication-patterns)
5. [Database Schema Summary per Service](#5-database-schema-summary-per-service)

---

## 1. Overview

SmartPortX is a Service-Oriented Architecture (SOA) platform for container port logistics
operations. It decomposes the port domain into eight independently deployable services, each
owning a dedicated PostgreSQL schema and exposing a bounded-context API.

### Service Summary Table

| # | Service                   | Responsibility                                              | Port | Database                  | Framework                        |
|---|---------------------------|-------------------------------------------------------------|------|---------------------------|----------------------------------|
| 1 | `smartportx-gateway`      | API Gateway — routing, auth, rate limiting, CORS, WS proxy  | 8080 | None (stateless)          | Spring Cloud Gateway (reactive)  |
| 2 | `smartportx-identity`     | User management, RBAC, JWT auth, password management        | 8081 | `smartportx_identity`     | Spring Boot 3.x + Security       |
| 3 | `smartportx-yard`         | Yard layout, container positioning, utilization, stacking   | 8082 | `smartportx_yard`         | Spring Boot 3.x + JPA            |
| 4 | `smartportx-gate`         | Truck appointments, gate check-in/out, queue, quotas        | 8083 | `smartportx_gate`         | Spring Boot 3.x + JPA            |
| 5 | `smartportx-container`    | Container lifecycle, search, movement history, DG flagging  | 8084 | `smartportx_container`    | Spring Boot 3.x + JPA            |
| 6 | `smartportx-vessel`       | Vessel schedules, discharge plans, discharge progress       | 8085 | `smartportx_vessel`       | Spring Boot 3.x + JPA            |
| 7 | `smartportx-integration`  | EDIFACT parsing, IoT ingestion, external adapters, customs  | 8086 | `smartportx_integration`  | Spring Boot 3.x + Integration    |
| 8 | `smartportx-notification` | Real-time WebSocket push, event-driven notifications        | 8087 | `smartportx_notification` | Spring Boot 3.x + WebSocket      |

### Domain Boundary Map

```
┌──────────────────────────────────────────────────────────────────────┐
│                        External Clients                             │
│                  (Browsers, Mobile, EDI Partners)                   │
└──────────────────────────┬───────────────────────────────────────────┘
                           │ HTTPS / WSS
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│  smartportx-gateway  (:8080)                                        │
│  ─────────────────────────────                                      │
│  JWT validation · Rate limiting · CORS · WebSocket proxy            │
└───┬────────┬────────┬────────┬────────┬────────┬────────┬───────────┘
    │        │        │        │        │        │        │
    ▼        ▼        ▼        ▼        ▼        ▼        ▼
┌────────┐┌────────┐┌────────┐┌────────┐┌────────┐┌────────┐┌────────┐
│identity││  yard  ││  gate  ││containe││ vessel ││integrat││notific │
│ :8081  ││ :8082  ││ :8083  ││ :8084  ││ :8085  ││ :8086  ││ :8087  │
└────────┘└────────┘└────────┘└────────┘└────────┘└────────┘└────────┘
                                    │
                           RabbitMQ Event Bus
```

---

## 2. Detailed Service Profiles

---

### 2.1 smartportx-gateway

| Field             | Value                                                             |
|-------------------|-------------------------------------------------------------------|
| **Name**          | `smartportx-gateway`                                              |
| **Responsibility**| API Gateway — request routing, authentication enforcement, rate limiting, CORS handling, WebSocket proxy |
| **Technology**    | Spring Cloud Gateway (reactive, non-blocking, WebFlux)            |
| **Port**          | 8080                                                              |
| **Database**      | None — stateless                                                  |
| **Dependencies**  | All other services (routes to each)                               |
| **Health Check**  | `/actuator/health`                                                |

#### Owned Endpoints

All external-facing endpoints pass through this gateway. The gateway itself does not
expose business endpoints; it proxies requests to downstream services.

| Route Pattern                | Target Service         | Method |
|------------------------------|------------------------|--------|
| `/auth/**`                   | `smartportx-identity`  | *      |
| `/users/**`                  | `smartportx-identity`  | *      |
| `/roles/**`                  | `smartportx-identity`  | *      |
| `/yard/**`                   | `smartportx-yard`      | *      |
| `/appointments/**`           | `smartportx-gate`      | *      |
| `/gate/**`                   | `smartportx-gate`      | *      |
| `/containers/**`             | `smartportx-container` | *      |
| `/vessels/**`                | `smartportx-vessel`    | *      |
| `/integration/**`            | `smartportx-integration` | *    |
| `/notifications/**`          | `smartportx-notification` | *    |
| `/ws/notifications`          | `smartportx-notification` | WS   |

#### Key Features

- **JWT Validation** — Intercepts all requests, validates the `Authorization: Bearer <token>` header
  against the identity service's public signing key. Rejects expired, malformed, or missing tokens
  with `401 Unauthorized`.
- **Rate Limiting** — Enforces 100 requests per minute per client IP using a token-bucket algorithm
  implemented with Reactor's `RequestRateLimiterGatewayFilter`. Returns `429 Too Many Requests`
  when exceeded.
- **Request Routing** — Predicate-based route definitions in `application.yml` that map URI
  path prefixes to downstream service URLs via service discovery or static configuration.
- **CORS Policy** — Configurable CORS filter allowing whitelisted origins, methods, and headers.
  Preflight `OPTIONS` requests are handled at the gateway level without forwarding downstream.
- **WebSocket Proxy** — Transparent upgrade and proxy of `wss://` connections to the notification
  service's STOMP endpoint at `/ws/notifications`. Maintains connection state through the reactive stack.
- **Circuit Breaker Integration** — Resilience4j circuit breaker filter on each route. Opens the
  circuit after 5 consecutive failures with a 60-second half-open window. Falls back to a
  `503 Service Unavailable` JSON response.
- **Request Correlation** — Injects a UUID `X-Correlation-Id` header into every forwarded request
  for distributed tracing across services.
- **Response Rewriting** — Strips internal service headers (`X-Internal-*`) before returning
  responses to external clients.

#### Configuration Highlights

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: identity-route
          uri: http://smartportx-identity:8081
          predicates:
            - Path=/auth/**,/users/**,/roles/**
        - id: yard-route
          uri: http://smartportx-yard:8082
          predicates:
            - Path=/yard/**
      default-filters:
        - CircuitBreaker=name:globalCircuitBreaker
        - name: RequestRateLimiter
          args:
            redis-rate-limiter.replenishRate: 100
            redis-rate-limiter.burstCapacity: 100
```

#### Data Entities

None — stateless gateway with no persistent data.

---

### 2.2 smartportx-identity

| Field             | Value                                                             |
|-------------------|-------------------------------------------------------------------|
| **Name**          | `smartportx-identity`                                             |
| **Responsibility**| User management, role-based access control (RBAC), JWT authentication/authorization, password management |
| **Technology**    | Spring Boot 3.x, Spring Security, Spring Data JPA                |
| **Port**          | 8081                                                              |
| **Database**      | `smartportx_identity`                                             |
| **Dependencies**  | None (core foundational service)                                  |
| **Health Check**  | `/actuator/health`                                                |

#### Owned Endpoints

| Method | Path                  | Description                              |
|--------|-----------------------|------------------------------------------|
| POST   | `/auth/login`         | Authenticate user, return JWT pair       |
| POST   | `/auth/refresh`       | Rotate refresh token, issue new access   |
| POST   | `/auth/logout`        | Revoke refresh token                     |
| GET    | `/users`              | List users (paginated, filterable)       |
| POST   | `/users`              | Create a new user                        |
| GET    | `/users/{id}`         | Get user by ID                           |
| PUT    | `/users/{id}`         | Update user profile                      |
| DELETE | `/users/{id}`         | Soft-delete a user                       |
| GET    | `/roles`              | List all roles                           |
| POST   | `/roles`              | Create a new role with permissions       |

#### Key Features

- **JWT Token Generation** — Issues access tokens (15-minute expiry) signed with RSA-256. Tokens
  carry claims: `sub`, `roles`, `permissions`, `iat`, `exp`, `jti`.
- **Refresh Token Rotation** — On each `/auth/refresh` call, the existing refresh token is
  invalidated and a new one is issued. Prevents token replay attacks.
- **bcrypt Password Hashing** — All passwords are hashed with bcrypt (cost factor 12) before
  storage. Plain-text passwords are never persisted.
- **Account Lockout** — After 10 consecutive failed login attempts, the account is locked for
  30 minutes. An unlock email notification is triggered.
- **Session Management** — Tracks active sessions per user. Users can revoke individual sessions
  or all sessions from any device.
- **RBAC Model** — Hierarchical role system: `ADMIN > OPERATOR > VIEWER`. Each role maps to a set
  of fine-grained permissions (e.g., `YARD_WRITE`, `GATE_CHECK_IN`, `CONTAINER_READ`).
- **Password History** — Stores the last 5 password hashes per user to prevent password reuse.

#### Data Entities

| Entity              | Description                                         |
|---------------------|-----------------------------------------------------|
| `users`             | User accounts (id, username, email, password_hash, enabled, locked, created_at, updated_at) |
| `roles`             | Named roles (id, name, description)                 |
| `user_roles`        | Many-to-many user↔role mapping                      |
| `permissions`       | Fine-grained permission identifiers (id, name, resource, action) |
| `role_permissions`  | Many-to-many role↔permission mapping                |
| `refresh_tokens`    | Active refresh tokens (token, user_id, expires_at, revoked) |
| `password_history`  | Previous password hashes per user (user_id, password_hash, created_at) |

---

### 2.3 smartportx-yard

| Field             | Value                                                             |
|-------------------|-------------------------------------------------------------------|
| **Name**          | `smartportx-yard`                                                 |
| **Responsibility**| Yard layout management, container positioning, yard utilization metrics, stacking optimization, reshuffling prediction |
| **Technology**    | Spring Boot 3.x, Spring Data JPA                                 |
| **Port**          | 8082                                                              |
| **Database**      | `smartportx_yard`                                                 |
| **Dependencies**  | `smartportx-container` (container data), `smartportx-gate` (appointment data for optimization) |
| **Health Check**  | `/actuator/health`                                                |

#### Owned Endpoints

| Method | Path                                                       | Description                          |
|--------|------------------------------------------------------------|--------------------------------------|
| GET    | `/yard/layout`                                             | Full yard hierarchy (blocks→bays→rows→tiers) |
| GET    | `/yard/blocks/{blockId}/bays/{bayId}/rows/{rowId}/tiers/{tierId}` | Get specific tier             |
| PUT    | `/yard/blocks/{blockId}/bays/{bayId}/rows/{rowId}/tiers/{tierId}` | Update tier status/contents   |
| POST   | `/yard/optimize`                                           | Trigger stacking optimization       |
| GET    | `/yard/metrics`                                            | Yard-wide utilization metrics       |
| GET    | `/yard/utilization`                                        | Per-block utilization breakdown     |

#### Key Features

- **Yard Layout CRUD** — Full CRUD operations for the hierarchical yard structure:
  Block → Bay → Row → Tier. Each level supports metadata annotations.
- **Container Position Assignment** — Assigns a container to a specific yard position
  (block/bay/row/tier). Validates capacity and stacking rules before placement.
- **Yard Utilization Calculation** — Computes real-time utilization as
  `occupied_tiers / total_tiers × 100%`. Exposes aggregate and per-block metrics.
- **Stacking Optimization Algorithm** — Heuristic-based optimizer that minimizes predicted
  reshuffles by analyzing container departure schedules and stacking compatibility. Runs
  asynchronously with status polling.
- **Reshuffling Prediction** — Predicts the number of container moves required to retrieve
  a target container from its current stack position. Flags high-reshuffle candidates.
- **Heat Map Data** — Provides color-coded yard map data for frontend visualization:
  utilization percentages per bay/row rendered as a 2D grid.

#### Data Entities

| Entity                  | Description                                           |
|-------------------------|-------------------------------------------------------|
| `yard_blocks`           | Top-level yard divisions (id, name, description, total_bays, created_at) |
| `yard_bays`             | Columns within a block (id, block_id, bay_number, total_rows) |
| `yard_rows`             | Rows within a bay (id, bay_id, row_number, total_tiers) |
| `yard_tiers`            | Vertical stack positions (id, row_id, tier_number, container_id, status) |
| `container_positions`   | Current container-to-position mapping (container_id, block_id, bay_id, row_id, tier_id, placed_at) |
| `yard_metrics`          | Time-series utilization snapshots (id, block_id, total_tiers, occupied_tiers, utilization_pct, recorded_at) |
| `optimization_history`  | Optimization run logs (id, started_at, completed_at, moves_suggested, reshuffles_reduced, status) |

---

### 2.4 smartportx-gate

| Field             | Value                                                             |
|-------------------|-------------------------------------------------------------------|
| **Name**          | `smartportx-gate`                                                 |
| **Responsibility**| Truck appointment booking, gate check-in/check-out processing, queue management, dynamic appointment quotas |
| **Technology**    | Spring Boot 3.x, Spring Data JPA                                 |
| **Port**          | 8083                                                              |
| **Database**      | `smartportx_gate`                                                 |
| **Dependencies**  | `smartportx-container` (container data for check-in/out), `smartportx-yard` (yard position for truck routing), `smartportx-identity` (user auth) |
| **Health Check**  | `/actuator/health`                                                |

#### Owned Endpoints

| Method | Path                          | Description                              |
|--------|-------------------------------|------------------------------------------|
| POST   | `/appointments`               | Book a new truck appointment             |
| GET    | `/appointments`               | List appointments (filterable, paginated)|
| PUT    | `/appointments/{id}`          | Update/cancel an appointment             |
| POST   | `/gate/check-in`              | Process truck gate-in                    |
| POST   | `/gate/check-out`             | Process truck gate-out                   |
| GET    | `/gate/queue`                 | Current gate queue status                |
| GET    | `/gate/quotas`                | List appointment quotas per time slot    |
| PUT    | `/gate/quotas/{id}`           | Update a specific quota                  |

#### Key Features

- **Appointment CRUD** — Full lifecycle management for truck appointments: create, read, update,
  cancel. Validates against available quotas before confirming.
- **Dynamic Quota Calculation** — Quotas adjust based on yard utilization, vessel discharge
  schedules, and historical traffic patterns. Time slots are divided into 30-minute windows.
- **Gate-In Processing** — Multi-step gate-in workflow:
  1. Document verification (bill of lading, booking reference)
  2. OCR capture of container number and seal number
  3. Container inspection status recording
  4. Yard position assignment (via `smartportx-yard`)
  5. Container status update (via `smartportx-container`)
- **Gate-Out Processing** — Outbound workflow:
  1. Release order verification
  2. EIR (Equipment Interchange Receipt) issuance
  3. Container status transition to `RELEASED`
  4. Gate transaction recording
- **Dual Transaction Support** — Gate-in and gate-out each execute as a distributed transaction
  with compensating actions. Failure at any step triggers rollback of prior steps in the workflow.
- **Queue Status** — Real-time queue visibility: number of trucks waiting, average wait time,
  estimated processing time per truck, current throughput rate.

#### Data Entities

| Entity               | Description                                             |
|----------------------|---------------------------------------------------------|
| `appointments`       | Truck appointments (id, truck_plate, driver_name, container_id, booking_ref, time_slot, status, created_at, updated_at) |
| `gate_transactions`  | Gate movement records (id, appointment_id, container_id, direction [IN/OUT], gate_lane, timestamp, documents_verified, ocr_result, inspection_status) |
| `queue_status`       | Live queue snapshot (id, lane_id, trucks_waiting, avg_wait_minutes, throughput_per_hour, last_updated) |
| `appointment_quotas` | Quota definitions per time slot (id, date, time_slot, max_appointments, current_bookings, override_flag) |

---

### 2.5 smartportx-container

| Field             | Value                                                             |
|-------------------|-------------------------------------------------------------------|
| **Name**          | `smartportx-container`                                            |
| **Responsibility**| Container entity lifecycle, container search, movement history, dangerous goods flagging, container metadata |
| **Technology**    | Spring Boot 3.x, Spring Data JPA                                 |
| **Port**          | 8084                                                              |
| **Database**      | `smartportx_container`                                            |
| **Dependencies**  | None (core foundational service)                                  |
| **Health Check**  | `/actuator/health`                                                |

#### Owned Endpoints

| Method | Path                            | Description                         |
|--------|---------------------------------|-------------------------------------|
| POST   | `/containers`                   | Register a new container            |
| GET    | `/containers`                   | List containers (paginated)         |
| GET    | `/containers/{id}`              | Get container by ID                 |
| PUT    | `/containers/{id}`              | Update container metadata           |
| GET    | `/containers/{id}/movements`    | Movement history for a container    |
| GET    | `/containers/{id}/events`       | Event log for a container           |
| POST   | `/containers/search`            | Advanced search (multi-field)       |

#### Key Features

- **CRUD Operations** — Full lifecycle management for container entities. Supports all standard
  container sizes (20ft, 40ft, 45ft) and types (dry, reefer, open-top, flat-rack, tank).
- **Lifecycle State Machine** — Enforces valid state transitions:
  ```
  CREATED → EXPECTED → RECEIVED → IN_YARD → POSITIONED → READY → LOADED
                                                        ↘ RELEASED
  ```
  Invalid transitions are rejected with `409 Conflict`.
- **ISO 6346 Validation** — Validates container numbers against the ISO 6346 standard:
  4 uppercase letters + 7 digits + 1 check digit. Rejects invalid formats at registration.
- **IMDG Dangerous Goods Flagging** — Containers carrying dangerous goods are flagged with
  their UN number, proper shipping name, hazard class, and packing group. Queries return a
  `dangerousGoods` object when applicable.
- **Movement Event Recording** — Every state transition and physical movement is recorded as an
  immutable event. Events include timestamp, source, destination, operator, and event type.
- **Advanced Search** — Multi-criteria search supporting: container number, size, type, status,
  dangerous goods flag, date range, vessel, and free-text search across metadata fields.

#### Data Entities

| Entity                  | Description                                           |
|-------------------------|-------------------------------------------------------|
| `containers`            | Container master record (id, container_number, size, type, status, owner, operator, vessel_id, weight, tare_weight, created_at, updated_at) |
| `container_movements`   | Movement log (id, container_id, from_position, to_position, movement_type, timestamp, operator_id) |
| `container_events`      | Immutable event log (id, container_id, event_type, payload_json, source_service, recorded_at) |
| `dangerous_goods`       | DG metadata (id, container_id, un_number, proper_shipping_name, hazard_class, packing_group, flash_point, ems_number) |

---

### 2.6 smartportx-vessel

| Field             | Value                                                             |
|-------------------|-------------------------------------------------------------------|
| **Name**          | `smartportx-vessel`                                               |
| **Responsibility**| Vessel schedule management, discharge plan creation, discharge progress tracking |
| **Technology**    | Spring Boot 3.x, Spring Data JPA                                 |
| **Port**          | 8085                                                              |
| **Database**      | `smartportx_vessel`                                               |
| **Dependencies**  | `smartportx-container` (container data for discharge), `smartportx-yard` (yard positions for placement) |
| **Health Check**  | `/actuator/health`                                                |

#### Owned Endpoints

| Method | Path                                | Description                       |
|--------|-------------------------------------|-----------------------------------|
| POST   | `/vessels`                          | Register a new vessel             |
| GET    | `/vessels`                          | List all vessels                  |
| GET    | `/vessels/{id}`                     | Get vessel details                |
| POST   | `/vessels/{id}/discharge-plan`      | Create discharge plan for arrival |
| GET    | `/vessels/{id}/discharge-plan`      | Get active discharge plan         |
| PUT    | `/vessels/{id}/discharge-progress`  | Update discharge progress         |
| GET    | `/vessels/schedule`                 | Upcoming vessel schedule          |

#### Key Features

- **Vessel CRUD** — Register and manage vessel metadata: IMO number, name, flag, LOA, beam,
  max TEU capacity, vessel type (containership, general cargo, tanker).
- **Schedule Management** — Maintain vessel arrival and departure schedules. Supports
  recurring schedules and one-off visits. Overlap detection prevents double-booking berths.
- **Discharge Plan Creation** — Generates a discharge plan from the vessel's stowage plan.
  Maps each container's bay/row/tier position on the vessel to a target yard position
  (coordinated with `smartportx-yard`).
- **Discharge Progress Tracking** — Real-time tracking of discharge operations: containers
  discharged vs. remaining, estimated completion time, crane productivity rate.
- **Vessel Status** — Tracks vessel lifecycle: `SCHEDULED → ARRIVED → DISCHARGING →
  LOADING → DEPARTED`. Status changes are published as events.

#### Data Entities

| Entity                  | Description                                           |
|-------------------------|-------------------------------------------------------|
| `vessels`               | Vessel master (id, imo_number, name, flag, loa_meters, beam_meters, max_teu, vessel_type, created_at) |
| `vessel_schedules`      | Schedule entries (id, vessel_id, arrival_date, departure_date, berth, status) |
| `discharge_plans`       | Plans per vessel visit (id, vessel_id, schedule_id, total_containers, created_at, status) |
| `discharge_containers`  | Container-level discharge items (id, plan_id, container_id, vessel_bay, vessel_row, vessel_tier, target_block, target_bay, status, discharged_at) |

---

### 2.7 smartportx-integration

| Field             | Value                                                             |
|-------------------|-------------------------------------------------------------------|
| **Name**          | `smartportx-integration`                                          |
| **Responsibility**| EDIFACT message parsing, IoT data ingestion (simulated), external system adapters, customs workflow |
| **Technology**    | Spring Boot 3.x, Spring Integration (message routing)            |
| **Port**          | 8086                                                              |
| **Database**      | `smartportx_integration`                                          |
| **Dependencies**  | `smartportx-container`, `smartportx-yard`, `smartportx-gate` (publishes events to them) |
| **Health Check**  | `/actuator/health`                                                |

#### Owned Endpoints

| Method | Path                                  | Description                       |
|--------|---------------------------------------|-----------------------------------|
| POST   | `/integration/edifact/parse`          | Parse an EDIFACT message          |
| POST   | `/integration/iot/data`               | Ingest simulated IoT sensor data  |
| GET    | `/integration/customs/status`         | Get customs clearance status      |
| PUT    | `/integration/customs/status/{containerId}` | Update customs status for a container |

#### Key Features

- **EDIFACT Parser** — Parses the following EDIFACT message types used in port logistics:
  - **COPRAR** — Container Pre-Arrival Report (incoming container list)
  - **COARRI** — Container Discharge/Loading Report (movement confirmation)
  - **CODECO** — Container Gate-In/Gate-Out Report (gate transaction)
  - **COPARN** — Container Announcement (booking notification)
  Each parser extracts structured data from the EDIFACT segment hierarchy and persists it.
- **Simulated IoT Data Generator** — Produces synthetic sensor readings (temperature, humidity,
  GPS coordinates, shock/vibration) for reefer containers and yard equipment. Simulates GPS
  drift, temperature excursions, and impact events for demo/testing.
- **Customs Status Tracking** — Maintains customs clearance status per container: `PENDING →
  DOCUMENTS_SUBMITTED → UNDER_REVIEW → CLEARED → HOLDS_RELEASED`. Exposes status for
  downstream consumers.
- **Integration Event Logging** — Every inbound message, outbound message, and system event
  is logged with full payload, timestamp, source, and processing result. Supports audit
  trail requirements.
- **External System Adapters** — Pluggable adapter pattern for integrating with port community
  systems, customs authorities, and shipping line APIs. Each adapter implements a common
  `IntegrationAdapter` interface.

#### Data Entities

| Entity                | Description                                           |
|-----------------------|-------------------------------------------------------|
| `edifact_messages`    | Parsed EDIFACT messages (id, message_type, sender, receiver, raw_payload, parsed_payload_json, status, received_at, processed_at) |
| `iot_readings`        | Sensor readings (id, device_id, container_id, reading_type, value, unit, latitude, longitude, recorded_at) |
| `customs_status`      | Customs clearance tracking (id, container_id, status, submission_date, review_date, clearance_date, holds_json) |
| `integration_logs`    | Audit trail (id, service_name, direction [INBOUND/OUTBOUND], message_type, payload_summary, status, timestamp) |

---

### 2.8 smartportx-notification

| Field             | Value                                                             |
|-------------------|-------------------------------------------------------------------|
| **Name**          | `smartportx-notification`                                         |
| **Responsibility**| Real-time WebSocket notifications, appointment status updates, yard/gate status push |
| **Technology**    | Spring Boot 3.x, Spring WebSocket (STOMP over WebSocket)         |
| **Port**          | 8087                                                              |
| **Database**      | `smartportx_notification`                                         |
| **Dependencies**  | Subscribes to events from all other services via RabbitMQ         |
| **Health Check**  | `/actuator/health`                                                |

#### Owned Endpoints

| Method / Protocol | Path                      | Description                          |
|-------------------|---------------------------|--------------------------------------|
| WS (STOMP)        | `/ws/notifications`       | WebSocket endpoint for real-time push |
| GET               | `/notifications/preferences`   | Get user notification preferences |
| PUT               | `/notifications/preferences`   | Update user notification preferences |

#### Key Features

- **WebSocket STOMP Broker** — Full STOMP broker relay over WebSocket. Clients connect to
  `/ws/notifications` and subscribe to topic destinations (e.g., `/topic/yard-updates`,
  `/topic/appointments`, `/topic/gate-status`).
- **Event-Driven Notifications** — Listens to RabbitMQ exchange for events published by all
  services. Routes events to appropriate STOMP topics based on event type and routing key.
- **Notification Preferences** — Per-user configuration for:
  - Which event types to receive (appointment, gate, yard, vessel, customs)
  - Delivery channels (WebSocket, email, SMS — email/SMS simulated in MVP)
  - Quiet hours (no notifications during specified time ranges)
  - Priority filtering (only HIGH priority notifications)
- **Notification History** — Persists all sent notifications with delivery status, read status,
  and timestamp. Supports "mark as read" and "clear all" operations.
- **Template System** — Notification payloads use a lightweight template engine to format
  human-readable messages from event data (e.g., "Container MSCU1234567 has been positioned
  at Block A, Bay 12, Row 3, Tier 2").

#### Data Entities

| Entity                      | Description                                         |
|-----------------------------|-----------------------------------------------------|
| `notification_preferences`  | Per-user settings (id, user_id, event_types_json, channels_json, quiet_hours_start, quiet_hours_end, priority_filter, updated_at) |
| `notification_history`      | Sent notifications (id, user_id, event_type, title, message, priority, channel, delivered, read, created_at) |

---

## 3. Inter-Service Dependency Matrix

The matrix below shows which services depend on which. A check mark (✓) indicates the
service in the **row** calls the service in the **column**.

| Caller ↓ / Dependency → | identity | container | yard | gate | vessel | integration | notification | gateway |
|--------------------------|----------|-----------|------|------|--------|-------------|--------------|---------|
| **gateway**              | ✓        | —         | —    | —    | —      | —           | —            | —       |
| **identity**             | —        | —         | —    | —    | —      | —           | —            | —       |
| **yard**                 | —        | ✓         | —    | ✓    | —      | —           | —            | —       |
| **gate**                 | ✓        | ✓         | ✓    | —    | —      | —           | —            | —       |
| **container**            | —        | —         | —    | —    | —      | —           | —            | —       |
| **vessel**               | —        | ✓         | ✓    | —    | —      | —           | —            | —       |
| **integration**          | —        | ✓         | ✓    | ✓    | —      | —           | —            | —       |
| **notification**         | —        | —         | —    | —    | —      | —           | —            | —       |

### Dependency Classification

| Category             | Description                                                        |
|----------------------|--------------------------------------------------------------------|
| **Core (no deps)**   | `identity`, `container` — foundational services with no outbound calls |
| **Leaf consumers**   | `gateway`, `notification` — depend on discovery/routing, not business APIs |
| **Orchestrators**    | `gate`, `vessel`, `integration` — coordinate across multiple core services |

### Dependency Depth

```
Level 0 (core):     identity, container
Level 1 (primary):  yard (→ container, gate)
Level 2 (secondary): gate (→ identity, container, yard)
                     vessel (→ container, yard)
                     integration (→ container, yard, gate)
Level 3 (edge):     gateway (→ all), notification (→ RabbitMQ from all)
```

---

## 4. Service Communication Patterns

### 4.1 Synchronous Communication

| Pattern         | Used Between                          | Protocol     | Notes                                      |
|-----------------|---------------------------------------|--------------|--------------------------------------------|
| REST API Call   | gateway → all services                | HTTP/HTTPS   | Cloud Gateway proxy, circuit breaker       |
| REST API Call   | gate → container, yard                | HTTP         | Direct service-to-service for check-in/out |
| REST API Call   | vessel → container, yard              | HTTP         | Discharge plan creation                    |
| REST API Call   | yard → container, gate                | HTTP         | Optimization reads                         |
| REST API Call   | integration → container, yard, gate   | HTTP         | Status queries and updates                 |

**Timeouts:** All synchronous calls use a 5-second timeout with 1 retry attempt.
**Circuit Breaker:** Resilience4j circuit breaker with failure threshold of 5,
open-state duration of 60 seconds, half-open max calls of 3.

### 4.2 Asynchronous Communication

| Pattern          | Producer                   | Consumer                   | Mechanism   | Notes                          |
|------------------|----------------------------|----------------------------|-------------|--------------------------------|
| Event Publishing | `container`                | `integration`, `notification` | RabbitMQ | Lifecycle state changes         |
| Event Publishing | `gate`                     | `notification`             | RabbitMQ    | Gate-in/out events              |
| Event Publishing | `yard`                     | `notification`             | RabbitMQ    | Position assignment events      |
| Event Publishing | `vessel`                   | `notification`             | RabbitMQ    | Discharge progress updates      |
| Event Publishing | `integration`              | `container`, `yard`, `gate` | RabbitMQ  | EDIFACT parsed data ingestion   |

**RabbitMQ Topology:**
- **Exchange:** `smartportx.events` (topic exchange)
- **Routing Key Pattern:** `{service}.{event-type}` (e.g., `container.state-changed`, `gate.truck-in`)
- **Dead Letter Queue:** `smartportx.events.dlx` for failed message processing
- **Message Persistence:** All events are persisted to RabbitMQ with durable queues
- **Consumer Acknowledgement:** Manual ACK after successful processing; NACK + requeue on failure

### 4.3 Event Types

| Event                    | Producer       | Payload Summary                                        |
|--------------------------|----------------|--------------------------------------------------------|
| `container.created`      | container      | container_id, container_number, size, type             |
| `container.state-changed`| container      | container_id, old_state, new_state, timestamp          |
| `container.movement`     | container      | container_id, from, to, movement_type                  |
| `gate.truck-in`          | gate           | appointment_id, container_id, timestamp                |
| `gate.truck-out`         | gate           | appointment_id, container_id, eir_number               |
| `yard.position-assigned` | yard           | container_id, block, bay, row, tier                    |
| `yard.optimization-run`  | yard           | moves_suggested, reshuffles_reduced                    |
| `vessel.discharge-update`| vessel         | vessel_id, plan_id, discharged_count, remaining_count  |
| `customs.status-changed` | integration    | container_id, old_status, new_status                   |

### 4.4 Error Handling

| Scenario                   | Strategy                                                    |
|----------------------------|-------------------------------------------------------------|
| Synchronous timeout        | Retry once (5s timeout), then circuit breaker opens         |
| Circuit breaker open       | Return `503 Service Unavailable` with retry-after header    |
| Message processing failure | NACK to RabbitMQ, requeue with exponential backoff (3 attempts) |
| Dead letter exhaustion     | Alert operations, log to integration_logs for manual review |
| Partial workflow failure   | Compensating transactions for gate-in/out multi-step flows  |

---

## 5. Database Schema Summary per Service

### 5.1 smartportx_identity

```
smartportx_identity
├── users
│   ├── id                  UUID PRIMARY KEY
│   ├── username            VARCHAR(50) UNIQUE NOT NULL
│   ├── email               VARCHAR(255) UNIQUE NOT NULL
│   ├── password_hash       VARCHAR(255) NOT NULL
│   ├── enabled             BOOLEAN DEFAULT TRUE
│   ├── locked              BOOLEAN DEFAULT FALSE
│   ├── failed_attempts     INTEGER DEFAULT 0
│   ├── locked_until        TIMESTAMP NULL
│   ├── created_at          TIMESTAMP NOT NULL
│   └── updated_at          TIMESTAMP NOT NULL
├── roles
│   ├── id                  UUID PRIMARY KEY
│   ├── name                VARCHAR(50) UNIQUE NOT NULL
│   └── description         VARCHAR(255)
├── user_roles
│   ├── user_id             UUID FK → users.id
│   └── role_id             UUID FK → roles.id
├── permissions
│   ├── id                  UUID PRIMARY KEY
│   ├── name                VARCHAR(100) UNIQUE NOT NULL
│   ├── resource            VARCHAR(100) NOT NULL
│   └── action              VARCHAR(50) NOT NULL
├── role_permissions
│   ├── role_id             UUID FK → roles.id
│   └── permission_id       UUID FK → permissions.id
├── refresh_tokens
│   ├── id                  UUID PRIMARY KEY
│   ├── token               VARCHAR(512) UNIQUE NOT NULL
│   ├── user_id             UUID FK → users.id
│   ├── expires_at          TIMESTAMP NOT NULL
│   ├── revoked             BOOLEAN DEFAULT FALSE
│   └── created_at          TIMESTAMP NOT NULL
└── password_history
    ├── id                  UUID PRIMARY KEY
    ├── user_id             UUID FK → users.id
    ├── password_hash       VARCHAR(255) NOT NULL
    └── created_at          TIMESTAMP NOT NULL
```

**Indexes:** `users(username)`, `users(email)`, `refresh_tokens(token)`, `refresh_tokens(user_id)`

---

### 5.2 smartportx_yard

```
smartportx_yard
├── yard_blocks
│   ├── id                  UUID PRIMARY KEY
│   ├── name                VARCHAR(50) UNIQUE NOT NULL
│   ├── description         VARCHAR(255)
│   ├── total_bays          INTEGER NOT NULL
│   └── created_at          TIMESTAMP NOT NULL
├── yard_bays
│   ├── id                  UUID PRIMARY KEY
│   ├── block_id            UUID FK → yard_blocks.id
│   ├── bay_number          INTEGER NOT NULL
│   ├── total_rows          INTEGER NOT NULL
│   └── UNIQUE(block_id, bay_number)
├── yard_rows
│   ├── id                  UUID PRIMARY KEY
│   ├── bay_id              UUID FK → yard_bays.id
│   ├── row_number          INTEGER NOT NULL
│   ├── total_tiers         INTEGER NOT NULL
│   └── UNIQUE(bay_id, row_number)
├── yard_tiers
│   ├── id                  UUID PRIMARY KEY
│   ├── row_id              UUID FK → yard_rows.id
│   ├── tier_number         INTEGER NOT NULL
│   ├── container_id        UUID NULL
│   ├── status              VARCHAR(20) DEFAULT 'EMPTY'
│   └── UNIQUE(row_id, tier_number)
├── container_positions
│   ├── id                  UUID PRIMARY KEY
│   ├── container_id        UUID NOT NULL
│   ├── block_id            UUID FK → yard_blocks.id
│   ├── bay_id              UUID FK → yard_bays.id
│   ├── row_id              UUID FK → yard_rows.id
│   ├── tier_id             UUID FK → yard_tiers.id
│   └── placed_at           TIMESTAMP NOT NULL
├── yard_metrics
│   ├── id                  UUID PRIMARY KEY
│   ├── block_id            UUID FK → yard_blocks.id
│   ├── total_tiers         INTEGER NOT NULL
│   ├── occupied_tiers      INTEGER NOT NULL
│   ├── utilization_pct     DECIMAL(5,2) NOT NULL
│   └── recorded_at         TIMESTAMP NOT NULL
└── optimization_history
    ├── id                  UUID PRIMARY KEY
    ├── started_at          TIMESTAMP NOT NULL
    ├── completed_at        TIMESTAMP NULL
    ├── moves_suggested     INTEGER DEFAULT 0
    ├── reshuffles_reduced  INTEGER DEFAULT 0
    └── status              VARCHAR(20) NOT NULL
```

**Indexes:** `container_positions(container_id)`, `container_positions(block_id)`, `yard_metrics(block_id, recorded_at)`

---

### 5.3 smartportx_gate

```
smartportx_gate
├── appointments
│   ├── id                  UUID PRIMARY KEY
│   ├── truck_plate         VARCHAR(20) NOT NULL
│   ├── driver_name         VARCHAR(100) NOT NULL
│   ├── driver_license      VARCHAR(50)
│   ├── container_id        UUID NOT NULL
│   ├── booking_ref         VARCHAR(50) NOT NULL
│   ├── time_slot           TIMESTAMP NOT NULL
│   ├── status              VARCHAR(20) DEFAULT 'PENDING'
│   ├── created_at          TIMESTAMP NOT NULL
│   └── updated_at          TIMESTAMP NOT NULL
├── gate_transactions
│   ├── id                  UUID PRIMARY KEY
│   ├── appointment_id      UUID FK → appointments.id
│   ├── container_id        UUID NOT NULL
│   ├── direction           VARCHAR(10) NOT NULL  -- IN or OUT
│   ├── gate_lane           VARCHAR(20) NOT NULL
│   ├── timestamp           TIMESTAMP NOT NULL
│   ├── documents_verified  BOOLEAN DEFAULT FALSE
│   ├── ocr_result          JSONB NULL
│   ├── inspection_status   VARCHAR(20) DEFAULT 'PENDING'
│   └── eir_number          VARCHAR(50) NULL
├── queue_status
│   ├── id                  UUID PRIMARY KEY
│   ├── lane_id             VARCHAR(20) NOT NULL
│   ├── trucks_waiting      INTEGER DEFAULT 0
│   ├── avg_wait_minutes    DECIMAL(5,1) DEFAULT 0
│   ├── throughput_per_hour INTEGER DEFAULT 0
│   └── last_updated        TIMESTAMP NOT NULL
└── appointment_quotas
    ├── id                  UUID PRIMARY KEY
    ├── date                DATE NOT NULL
    ├── time_slot           TIME NOT NULL
    ├── max_appointments    INTEGER NOT NULL
    ├── current_bookings    INTEGER DEFAULT 0
    ├── override_flag       BOOLEAN DEFAULT FALSE
    └── UNIQUE(date, time_slot)
```

**Indexes:** `appointments(time_slot)`, `appointments(status)`, `gate_transactions(container_id)`, `gate_transactions(direction)`

---

### 5.4 smartportx_container

```
smartportx_container
├── containers
│   ├── id                  UUID PRIMARY KEY
│   ├── container_number    VARCHAR(11) UNIQUE NOT NULL  -- ISO 6346
│   ├── size                VARCHAR(10) NOT NULL          -- 20ft, 40ft, 45ft
│   ├── type                VARCHAR(20) NOT NULL          -- dry, reefer, open-top, etc.
│   ├── status              VARCHAR(30) NOT NULL          -- lifecycle state machine
│   ├── owner               VARCHAR(100)
│   ├── operator            VARCHAR(100)
│   ├── vessel_id           UUID NULL
│   ├── weight_kg           DECIMAL(10,2)
│   ├── tare_weight_kg      DECIMAL(10,2)
│   ├── seal_number         VARCHAR(50)
│   ├── po_number           VARCHAR(50)
│   ├── created_at          TIMESTAMP NOT NULL
│   └── updated_at          TIMESTAMP NOT NULL
├── container_movements
│   ├── id                  UUID PRIMARY KEY
│   ├── container_id        UUID FK → containers.id
│   ├── from_position       VARCHAR(100)
│   ├── to_position         VARCHAR(100)
│   ├── movement_type       VARCHAR(30) NOT NULL          -- YARD_MOVE, GATE_IN, GATE_OUT, VESSEL_LOAD, VESSEL_DISCHARGE
│   ├── timestamp           TIMESTAMP NOT NULL
│   └── operator_id         UUID NULL
├── container_events
│   ├── id                  UUID PRIMARY KEY
│   ├── container_id        UUID FK → containers.id
│   ├── event_type          VARCHAR(50) NOT NULL
│   ├── payload_json        JSONB NOT NULL
│   ├── source_service      VARCHAR(50) NOT NULL
│   └── recorded_at         TIMESTAMP NOT NULL
└── dangerous_goods
    ├── id                  UUID PRIMARY KEY
    ├── container_id        UUID FK → containers.id UNIQUE
    ├── un_number           VARCHAR(10) NOT NULL
    ├── proper_shipping_name VARCHAR(255) NOT NULL
    ├── hazard_class        VARCHAR(10) NOT NULL
    ├── packing_group       VARCHAR(5) NOT NULL
    ├── flash_point_celsius DECIMAL(5,1) NULL
    └── ems_number          VARCHAR(20) NULL
```

**Indexes:** `containers(container_number)`, `containers(status)`, `containers(vessel_id)`, `container_movements(container_id)`, `container_events(container_id, event_type)`

---

### 5.5 smartportx_vessel

```
smartportx_vessel
├── vessels
│   ├── id                  UUID PRIMARY KEY
│   ├── imo_number          VARCHAR(10) UNIQUE NOT NULL
│   ├── name                VARCHAR(100) NOT NULL
│   ├── flag                VARCHAR(5) NOT NULL           -- ISO 3166 alpha-2
│   ├── loa_meters          DECIMAL(7,2)
│   ├── beam_meters         DECIMAL(5,2)
│   ├── max_teu             INTEGER
│   ├── vessel_type         VARCHAR(30) DEFAULT 'containership'
│   └── created_at          TIMESTAMP NOT NULL
├── vessel_schedules
│   ├── id                  UUID PRIMARY KEY
│   ├── vessel_id           UUID FK → vessels.id
│   ├── arrival_date        TIMESTAMP NOT NULL
│   ├── departure_date      TIMESTAMP NULL
│   ├── berth               VARCHAR(50)
│   ├── status              VARCHAR(20) DEFAULT 'SCHEDULED'
│   └── UNIQUE(vessel_id, arrival_date)
├── discharge_plans
│   ├── id                  UUID PRIMARY KEY
│   ├── vessel_id           UUID FK → vessels.id
│   ├── schedule_id         UUID FK → vessel_schedules.id
│   ├── total_containers    INTEGER NOT NULL
│   ├── created_at          TIMESTAMP NOT NULL
│   └── status              VARCHAR(20) DEFAULT 'DRAFT'
└── discharge_containers
    ├── id                  UUID PRIMARY KEY
    ├── plan_id             UUID FK → discharge_plans.id
    ├── container_id        UUID NOT NULL
    ├── vessel_bay          VARCHAR(10)
    ├── vessel_row          VARCHAR(10)
    ├── vessel_tier         VARCHAR(10)
    ├── target_block        VARCHAR(50)
    ├── target_bay          VARCHAR(10)
    ├── status              VARCHAR(20) DEFAULT 'PLANNED'
    └── discharged_at       TIMESTAMP NULL
```

**Indexes:** `vessels(imo_number)`, `vessel_schedules(arrival_date)`, `discharge_plans(schedule_id)`, `discharge_containers(plan_id)`

---

### 5.6 smartportx_integration

```
smartportx_integration
├── edifact_messages
│   ├── id                  UUID PRIMARY KEY
│   ├── message_type        VARCHAR(10) NOT NULL          -- COPRAR, COARRI, CODECO, COPARN
│   ├── sender              VARCHAR(50) NOT NULL
│   ├── receiver            VARCHAR(50) NOT NULL
│   ├── raw_payload         TEXT NOT NULL
│   ├── parsed_payload_json JSONB NOT NULL
│   ├── status              VARCHAR(20) DEFAULT 'RECEIVED'
│   ├── received_at         TIMESTAMP NOT NULL
│   └── processed_at        TIMESTAMP NULL
├── iot_readings
│   ├── id                  UUID PRIMARY KEY
│   ├── device_id           VARCHAR(50) NOT NULL
│   ├── container_id        UUID NULL
│   ├── reading_type        VARCHAR(30) NOT NULL          -- temperature, humidity, gps, shock
│   ├── value               DECIMAL(10,4)
│   ├── unit                VARCHAR(10)
│   ├── latitude            DECIMAL(9,6) NULL
│   ├── longitude           DECIMAL(9,6) NULL
│   └── recorded_at         TIMESTAMP NOT NULL
├── customs_status
│   ├── id                  UUID PRIMARY KEY
│   ├── container_id        UUID UNIQUE NOT NULL
│   ├── status              VARCHAR(30) NOT NULL
│   ├── submission_date     TIMESTAMP NULL
│   ├── review_date         TIMESTAMP NULL
│   ├── clearance_date      TIMESTAMP NULL
│   └── holds_json          JSONB NULL
└── integration_logs
    ├── id                  UUID PRIMARY KEY
    ├── service_name        VARCHAR(50) NOT NULL
    ├── direction           VARCHAR(10) NOT NULL          -- INBOUND, OUTBOUND
    ├── message_type        VARCHAR(50) NOT NULL
    ├── payload_summary     TEXT
    ├── status              VARCHAR(20) NOT NULL
    └── timestamp           TIMESTAMP NOT NULL
```

**Indexes:** `edifact_messages(message_type, received_at)`, `iot_readings(container_id)`, `iot_readings(device_id)`, `customs_status(container_id)`, `integration_logs(service_name, timestamp)`

---

### 5.7 smartportx_notification

```
smartportx_notification
├── notification_preferences
│   ├── id                  UUID PRIMARY KEY
│   ├── user_id             UUID UNIQUE NOT NULL
│   ├── event_types_json    JSONB NOT NULL                -- ["appointment","gate","yard","vessel","customs"]
│   ├── channels_json       JSONB NOT NULL                -- ["websocket","email","sms"]
│   ├── quiet_hours_start   TIME NULL
│   ├── quiet_hours_end     TIME NULL
│   ├── priority_filter     VARCHAR(10) DEFAULT 'ALL'     -- ALL, HIGH
│   └── updated_at          TIMESTAMP NOT NULL
└── notification_history
    ├── id                  UUID PRIMARY KEY
    ├── user_id             UUID NOT NULL
    ├── event_type          VARCHAR(50) NOT NULL
    ├── title               VARCHAR(255) NOT NULL
    ├── message             TEXT NOT NULL
    ├── priority            VARCHAR(10) DEFAULT 'NORMAL'  -- LOW, NORMAL, HIGH
    ├── channel             VARCHAR(20) NOT NULL
    ├── delivered           BOOLEAN DEFAULT FALSE
    ├── read                BOOLEAN DEFAULT FALSE
    └── created_at          TIMESTAMP NOT NULL
```

**Indexes:** `notification_preferences(user_id)`, `notification_history(user_id, created_at)`, `notification_history(event_type)`

---

## Appendix A: Port Allocation

| Port | Service                   | Protocol |
|------|---------------------------|----------|
| 8080 | smartportx-gateway        | HTTP/WS  |
| 8081 | smartportx-identity       | HTTP     |
| 8082 | smartportx-yard           | HTTP     |
| 8083 | smartportx-gate           | HTTP     |
| 8084 | smartportx-container      | HTTP     |
| 8085 | smartportx-vessel         | HTTP     |
| 8086 | smartportx-integration    | HTTP     |
| 8087 | smartportx-notification   | HTTP/WS  |

## Appendix B: Technology Stack Summary

| Layer              | Technology                                                |
|--------------------|-----------------------------------------------------------|
| API Gateway        | Spring Cloud Gateway (reactive/WebFlux)                   |
| Microservices      | Spring Boot 3.x, Java 25                                 |
| Security           | Spring Security, RSA-256 JWT, bcrypt                      |
| Persistence        | Spring Data JPA, Hibernate 7, PostgreSQL 17.11            |
| Messaging          | RabbitMQ (topic exchange, durable queues)                 |
| WebSocket          | Spring WebSocket, STOMP protocol                          |
| Integration        | Spring Integration (message channels, adapters)           |
| Resilience         | Resilience4j (circuit breaker, rate limiter)              |
| Schema Isolation   | Schema-per-service (each service owns its own schema)     |

## Appendix C: Container Lifecycle State Machine

```
                 ┌─────────────┐
                 │  CREATED    │  Container registered in system
                 └──────┬──────┘
                        │
                        ▼
                 ┌─────────────┐
                 │  EXPECTED   │  Container expected (EDIFACT COPRAR received)
                 └──────┬──────┘
                        │
                        ▼
                 ┌─────────────┐
                 │  RECEIVED   │  Container physically received at gate
                 └──────┬──────┘
                        │
                        ▼
                 ┌─────────────┐
                 │  IN_YARD    │  Container moved into yard
                 └──────┬──────┘
                        │
                        ▼
                 ┌─────────────┐
                 │  POSITIONED │  Container assigned final yard position
                 └──────┬──────┘
                        │
                        ▼
                 ┌─────────────┐
                 │  READY      │  Container ready for next operation
                 └───┬─────┬───┘
                     │     │
            ┌────────┘     └────────┐
            ▼                       ▼
     ┌─────────────┐        ┌─────────────┐
     │   LOADED    │        │  RELEASED   │
     │ (onto vessel)│        │ (trucked out)│
     └─────────────┘        └─────────────┘
```

---

*End of Service Catalog*
