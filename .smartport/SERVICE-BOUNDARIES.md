# SmartPortX — Service Boundary Analysis

> **Version:** 1.0.0
> **Architecture:** Service-Based Architecture (SBA)
> **Stack:** Java 25 · Spring Boot 3.x · PostgreSQL 17.11 · RabbitMQ
> **Scope:** This document defines the **boundaries BETWEEN services** — what each service owns, what it explicitly does NOT own, and how those boundaries are enforced. It complements the [Service Catalog](SERVICE-CATALOG.md) (which lists what each service does) by answering the harder question: **where does one service's responsibility end and another's begin?**

---

## Table of Contents

1. [Boundary Principles](#1-boundary-principles)
2. [The Bounded Context Map](#2-the-bounded-context-map)
   - 2.1 [smartportx-gateway](#21-smartportx-gateway)
   - 2.2 [smartportx-identity](#22-smartportx-identity)
   - 2.3 [smartportx-yard](#23-smartportx-yard)
   - 2.4 [smartportx-gate](#24-smartportx-gate)
   - 2.5 [smartportx-container](#25-smartportx-container)
   - 2.6 [smartportx-vessel](#26-smartportx-vessel)
   - 2.7 [smartportx-integration](#27-smartportx-integration)
   - 2.8 [smartportx-notification](#28-smartportx-notification)
3. [Boundary Enforcement Mechanisms](#3-boundary-enforcement-mechanisms)
4. [Ambiguity Resolutions (Critical)](#4-ambiguity-resolutions-critical)
5. [Cross-Boundary Data Flow Summary](#5-cross-boundary-data-flow-summary)
6. [Common Boundary Anti-Patterns to Avoid](#6-common-boundary-anti-patterns-to-avoid)
7. [Service Evolution Guidelines](#7-service-evolution-guidelines)

---

## 1. Boundary Principles

The following principles are the **constitution** of the SmartPortX architecture. Every boundary decision, every refactor, and every new feature must be consistent with these rules. When a conflict arises between expediency and a principle, the principle wins — violating a boundary now pays compounding interest in coupling later.

### 1.1 Single Responsibility

> **Each service owns ONE domain capability.**

A service exists because it is the authoritative owner of a single, well-defined slice of the port domain. If you cannot describe a service's responsibility in one sentence without listing more than one verb, the boundary is suspect.

| Service | One-sentence responsibility |
|---------|-----------------------------|
| gateway | Route and gate external requests. |
| identity | Manage users, roles, and access. |
| yard | Manage physical container placement in the yard. |
| gate | Manage truck appointments and gate transactions. |
| container | Manage the container as an entity and its lifecycle. |
| vessel | Manage vessels, schedules, and discharge operations. |
| integration | Translate external formats and ingest external data. |
| notification | Deliver notifications to users. |

**Test:** A design change should only touch a service whose responsibility it actually changes. If a change ripples into multiple services, either the change crosses a real domain boundary (expected) or the boundary was drawn in the wrong place (needs correction).

### 1.2 Data Ownership

> **Each service exclusively owns its database schema.**

Every schema (`smartportx_identity`, `smartportx_yard`, etc.) has exactly one **owner** service. The owner is the only service that may read *and* write that schema's tables. Other services may not even connect to it directly.

**Corollary — the owner defines the truth:** The owning service is the source of truth for its entities. When another service needs that data, it must ask via API or receive it via event. It may cache a *projection* locally, but only the owner's version is authoritative.

### 1.3 No Shared Tables

> **Cross-service data flows via events or API only — never through a shared table.**

There are **no** cross-schema foreign keys and **no** tables shared between services. A foreign key may only reference rows within the owning service's own schema. When two services reference the same real-world object (e.g., a container), they reference it by **shared ID** (typically a UUID) but each stores only the attributes relevant to its own concern.

**Example:** `container_id` appears in `smartportx_gate`, `smartportx_yard`, and `smartportx_vessel` schemas. But that is a plain UUID column, **not** a foreign key to `smartportx_container`. It is an *identifier*, not a *pointer* into another schema.

### 1.4 No Circular Dependencies

> **The inter-service dependency graph must be acyclic.**

Services may depend on one another (via API calls or event subscriptions), but the dependency graph must never contain a cycle. A cycle means two services mutually depend on each other's truth, which is a smell that the boundary between them was drawn incorrectly.

The dependency graph for SmartPortX is a layered DAG:

```
Level 0 (core)      : identity, container
Level 1 (primary)   : yard        (→ container, gate)
Level 2 (secondary) : gate, vessel, integration
Level 3 (edge)      : gateway, notification
```

**Rule of thumb:** If you find yourself tempted to add a cyclic dependency, stop and ask *"which of these two should be the authority for this piece of state?"* Whichever service answers that is the owner, and the other must consume it one-directionally.

### 1.5 High Cohesion

> **Related operations live in the same service.**

Operations that share the same data, the same domain rules, and the same lifecycle belong together. Splitting them across services forces every related operation to cross a network boundary, adding latency, complexity, and failure modes. Cohesion within a service keeps a boundary *meaningful* rather than arbitrary.

### 1.6 Loose Coupling

> **Services interact via well-defined contracts, not via knowledge of each other's internals.**

A service knows *what* another service does (its contract) but not *how* it does it (its internals). Communication is through:
- **Synchronous:** REST API calls with stable, versioned contracts.
- **Asynchronous:** Events published to RabbitMQ with a schema envelope.

A service must never reach into another service's implementation, its database, or its internal state beyond what the contract exposes.

---

## 2. The Bounded Context Map

Each subsection describes one service's bounded context using a consistent template:
- **Boundary Statement** — what the service owns.
- **Inside the Boundary (Included)** — specific responsibilities.
- **Outside the Boundary (Excluded)** — explicitly NOT this service's job.
- **Shared Data** — what crosses the boundary and how.
- **Anti-Corruption Layer** — how external/foreign formats are isolated.

---

### 2.1 smartportx-gateway

#### Boundary Statement

> The gateway **owns request ingress**: it is the single entry point for all external traffic. It owns routing, JWT validation, rate limiting, CORS, correlation-id propagation, and WebSocket proxying.

#### Inside the Boundary (Included)

- **Request routing** — path-prefix → downstream service mapping (reactive Spring Cloud Gateway).
- **JWT validation** — validate the `Authorization: Bearer` token against the identity public key on every request; reject invalid/expired tokens with `401`.
- **Rate limiting** — token-bucket enforcement (100 req/min/IP → `429`).
- **CORS** — whitelist origins, methods, headers; handle preflight `OPTIONS` without forwarding.
- **Correlation-id propagation** — inject `X-Correlation-Id` for distributed tracing.
- **Circuit breaking** — Resilience4j breaker per route with standardized `503` fallback.
- **Response sanitization** — strip internal headers (`X-Internal-*`) before returning to clients.
- **WebSocket proxying** — transparent upgrade/proxy to the notification service's STOMP endpoint.

#### Outside the Boundary (Excluded)

- **Business logic** — the gateway applies **zero** business rules. It never decides where a container goes, whether a gate-out is allowed, or what a discharge plan contains.
- **Data storage** — the gateway has **no database** and persists nothing.
- **Authentication decisions** — the gateway validates that a token is *well-formed and signed*, but does not decide *what a user is allowed to do* (that is identity/authorization policy).
- **Aggregation/composition** — the gateway does not join data from multiple services to build composite responses. If a client needs a composite, that is either done by the invoking workflow or delegated to the owning service (see anti-pattern #1).

#### Shared Data

- **Passes user context to downstream services** as propagated headers: `X-User-Id`, `X-User-Roles`, `X-User-Permissions`, `X-Correlation-Id`.
- The gateway **does not originate** business data; it only relays authentication context and correlates requests.

#### Anti-Corruption Layer

- The gateway translates the **external client's** request/response conventions into the **internal** service contract conventions. It does not let client-specific concerns (authn headers, pagination preferences, legacy formats) leak into downstream services unsanitized.

---

### 2.2 smartportx-identity

#### Boundary Statement

> **Identity owns the user, role, permission, and authentication/token domain.** It is the authoritative source of "who is allowed to do what."

#### Inside the Boundary (Included)

- **User accounts** — create/update/deactivate users; username/email uniqueness.
- **Roles & permissions** — RBAC model (`ADMIN > OPERATOR > VIEWER`), fine-grained permissions, role↔permission mappings.
- **Authentication** — login/refresh/logout, bcrypt password hashing (cost 12), JWT issuance (15-min access, rotated refresh tokens).
- **Password policy** — complexity, history (last 5), account lockout after failed attempts.
- **Session management** — active-session tracking, per-session and all-session revocation.
- **Token signing keys** — RSA-256 private key storage and public-key distribution (consumed by the gateway for validation).

#### Outside the Boundary (Excluded)

- **Business entities** — identity does NOT store containers, appointments, yard positions, vessels, or customs status. It knows nothing about the port's operational domain.
- **Audit logging** — identity does *not* host the system-wide audit trail. Auditing of login events is delegated/logged via the `integration` audit log and observability; identity does not own a business audit service.
- **Container / gate / yard / vessel data** — none of these live in `smartportx_identity`.
- **Authorization enforcement inside other services** — identity defines *permissions*, but service-level enforcement (should this user edit this appointment?) is implemented in the owning service using the propagated permission headers.

#### Shared Data

- **Crosses: none directly.** Other services never query identity at runtime. They rely on the **gateway** to validate tokens and propagate `X-User-Roles` / `X-User-Permissions`.
- The **public signing key** is shared (as a configuration artifact) with the gateway for stateless JWT validation.

#### Anti-Corruption Layer

- Identity only ever receives credentials over a contract (`/auth/*`); it never accepts foreign identity formats. External SSO/federation, if introduced, would be adapted into this service's internal user/permission model behind its own boundary, not bolted onto others.

---

### 2.3 smartportx-yard

#### Boundary Statement

> **Yard owns the physical yard: its layout (blocks/bays/rows/tiers), the placement of containers within it, utilization metrics, stacking optimization, reshuffling prediction, and equipment status.**

#### Inside the Boundary (Included)

- **Yard layout** — authoritative structure: blocks → bays → rows → tiers, with capacities and status.
- **Container positioning** — assign a container to a specific yard slot (validating capacity and stacking rules before placement). Yard is the *decider* of where a container physically sits.
- **Yard utilization metrics** — `occupied_tiers / total_tiers × 100%`, aggregate + per-block time series.
- **Stacking optimization** — heuristic minimizer of predicted reshuffles (reads departure schedules).
- **Reshuffling prediction** — estimate moves required to retrieve a buried container; flag high reshuffle candidates.
- **IMDG segregation rules** — placement logic that keeps incompatible dangerous-goods classes separated (see [Ambiguity Resolution](#42-why-yard-owns-imdg-segregation-rules-instead-of-container)).
- **Equipment status** — status of cranes and yard equipment (relevant to vessel discharge coordination and crane scheduling).

#### Outside the Boundary (Excluded)

- **Container metadata** — container number, type, owner, weight, seal, DG *flags* — all owned by `smartportx-container`. Yard stores a `container_id` and any attributes needed for *placement* (e.g., DG class for segregation), not the container's master record.
- **Appointments** — appointment booking, quotas, and queue status belong to `smartportx-gate`. Yard reads gate's appointment schedule to inform optimization but does not manage appointments.
- **Vessel plans** — discharge plans and schedules belong to `smartportx-vessel`. Yard provides *position availability* to vessel, but does not create discharge plans.
- **Gate transactions** — gate-in/out processing belongs to `smartportx-gate`.

#### Shared Data

- **Receives** container information via `ContainerCreated` / `ContainerUpdated` events (from `smartportx-container`) so it knows what is arriving and can plan placement.
- **Publishes** `YardPositionAssigned` (block/bay/row/tier) on placement.
- **Queries** `smartportx-gate` for the appointment schedule during stacking optimization (to align placement with expected departures).

#### Anti-Corruption Layer

- Yard consumes container **event projections** — not the container service's internal model. It maintains its own view of "a container exists with these placement-relevant attributes" in its own schema. If the container service's internal representation changes, yard is insulated by the event contract.

---

### 2.4 smartportx-gate

#### Boundary Statement

> **Gate owns the truck-facing operational domain: appointments, quotas, queue status, gate-in/gate-out transactions, and Equipment Interchange Receipt (EIR) issuance.**

#### Inside the Boundary (Included)

- **Appointments** — booking, rescheduling, cancellation, validation against available quotas.
- **Quotas** — dynamic per-time-slot capacity that adapts to yard utilization, vessel schedules, and traffic history.
- **Queue status** — live visibility of trucks waiting, average wait time, throughput.
- **Gate-in processing** — document verification, OCR of container/seal number, inspection status, orchestration of the check-in workflow.
- **Gate-out processing** — release-order verification, customs-block check, EIR issuance, status transition.
- **Dual transaction** — handle a combined drop-off + pickup as a single gate transaction covering both moves.
- **Customs-block enforcement** — query `smartportx-integration` customs status before gate-out; block if status is `HELD`.

#### Outside the Boundary (Excluded)

- **Yard position assignment** — gate does NOT decide where a container goes. It **requests** a position from `smartportx-yard` during gate-in and relays the answer to the truck driver.
- **Container lifecycle** — gate does not own the container's state machine. It records gate transactions and publishes events; the container service owns lifecycle transitions.
- **Customs status** — gate does not compute or store customs status; it reads it from `smartportx-integration` at the point of need.
- **Yard layout / utilization metrics** — owned by `smartportx-yard`.

#### Shared Data

- **Calls** the `smartportx-container` API to verify a container exists and is valid before processing check-in/out.
- **Calls** the `smartportx-yard` API to fetch/assign a yard position for the truck at check-in.
- **Publishes** `GateEvent` (truck-in/truck-out) to RabbitMQ — consumed by `notification` (and informational consumers).
- **Subscribes** to `CustomsStatusUpdated` (from `integration`) so it can reflect customs state and block gate-out when a container is `HELD`.

#### Anti-Corruption Layer

- Gate orchestrates workflows across multiple services (container, yard, integration) but never embeds their business rules. Each foreign service is reached only through its public API; the gate's own model is the gate-domain view (appointments, quotas, transactions). If a downstream service's API shape changes, only gate's adapter layer changes.

---

### 2.5 smartportx-container

#### Boundary Statement

> **Container owns the container entity itself: its identity and metadata (number, type, owner, weight, seal, DG flags), its lifecycle state machine, its movement history, and search over that data.**

#### Inside the Boundary (Included)

- **Container entity** — master record: ISO 6346 number (validation), size, type, owner, operator, weight, tare, seal, PO number.
- **Lifecycle state machine** — authoritative transitions (`CREATED → EXPECTED → RECEIVED → IN_YARD → POSITIONED → READY → LOADED / RELEASED`); rejects invalid transitions with `409`.
- **Movement history** — immutable log of every movement/event for a container.
- **Search** — multi-criteria search across container records.
- **DG metadata storage** — stores the DG *data* (UN number, class, packing group — see [segregation resolution](#42-why-yard-owns-imdg-segregation-rules-instead-of-container)).
- **Current-location projection** — stores a `current_location` reflecting the container's present position, updated from Yard events.

#### Outside the Boundary (Excluded)

- **Physical location assignment** — container does NOT decide where a container sits in the yard. Yard owns placement; container merely *records* the resulting position in `current_location` when it receives the Yard event.
- **Vessel plans** — container records `vessel_id` references but does not own schedules or discharge plans (`smartportx-vessel` does).
- **Customs status** — customs clearance state belongs to `smartportx-integration`; container may reflect a flag but does not own the customs state machine.
- **Appointments / gate transactions** — owned by `smartportx-gate` (container stores no appointment rows).

#### Shared Data

- **Publishes** `ContainerCreated`, `ContainerUpdated`, `ContainerMoved`, `ContainerStatusChanged` events to RabbitMQ.
- These are consumed by `smartportx-yard` (placement), `smartportx-integration` (customs/audit correlation), and `smartportx-notification` (user alerts).

#### Anti-Corruption Layer

- Container encodes its lifecycle state machine and ISO validation internally so foreign actors (gate, integration, yard) cannot drive the container into an illegal state. External systems that push a container update must go through the container contract, which validates and normalizes before mutating state.

---

### 2.6 smartportx-vessel

#### Boundary Statement

> **Vessel owns the vessel registry, schedules, discharge plans, and discharge progress.**

#### Inside the Boundary (Included)

- **Vessel registry** — IMO number, name, flag, LOA, beam, max TEU, vessel type (ISO validation).
- **Schedules** — arrival/departure, berth allocation, overlap detection.
- **Discharge plans** — creation of the plan mapping vessel stowage (bay/row/tier) to a target yard position.
- **Discharge progress** — real-time tracking: discharged vs. remaining, estimated completion, crane productivity.
- **Vessel lifecycle** — `SCHEDULED → ARRIVED → DISCHARGING → LOADING → DEPARTED`.

#### Outside the Boundary (Excluded)

- **Stowage planning** — the design of how containers are stowed *on the vessel* is **out of scope** for SmartPortX (explicitly excluded; handled by an external stevedore system / upstream of this platform).
- **Crane scheduling** — crane/equipment scheduling belongs to `smartportx-yard` (equipment status). Vessel tracks discharge *rates* but does not schedule cranes.

#### Shared Data

- **Calls** `smartportx-container` API to validate containers during discharge-plan creation.
- **Calls** `smartportx-yard` API for position availability when mapping discharge targets.
- **Publishes** `VesselArrived`, `DischargeProgress` events to RabbitMQ (consumed by `notification`).

#### Anti-Corruption Layer

- Discharge plans reference containers by shared `container_id` and yard positions by shared IDs, but vessel never reaches into the container or yard schemas. It holds its own discharge-table view (its own `discharge_containers`), translating external stowage input into its plan model.

---

### 2.7 smartportx-integration

#### Boundary Statement

> **Integration owns the translation of external formats and the ingestion of external data: EDIFACT parsing, IoT ingestion, customs status (simulated), and the adapter interface layer to external systems.**

#### Inside the Boundary (Included)

- **EDIFACT parsing** — COPRAR, COARRI, CODECO, COPARN message types → structured data.
- **IoT ingestion** — synthetic sensor readings (temp, humidity, GPS, shock) for reefer containers and yard equipment.
- **Customs status simulation** — the `PENDING → DOCUMENTS_SUBMITTED → UNDER_REVIEW → CLEARED / HOLDS_RELEASED` state per container.
- **Adapter interface** — pluggable `IntegrationAdapter` contract for port-community systems, customs authorities, shipping lines.
- **Raw message persistence** — stores raw inbound/outbound messages and audit logs in **its own** schema.
- **Integration audit log** — inbound/outbound message trail (supports audit requirements across the platform).

#### Outside the Boundary (Excluded)

- **Business entities** — integration owns **none** of the primary business entities. Every service owns its own entities. Integration translates and dispatches, but does not become a persistent store of truth for containers, yard positions, appointments, or vessels.
- **Notification delivery** — it correlates events but does not push to users (`notification` owns delivery).
- **Business decisions** — integration parses/ingests and publishes; it does not decide placement, gate release, or vessel discharge.

#### Shared Data

- **Publishes** parsed events to `smartportx-container`, `smartportx-yard`, `smartportx-gate` (e.g., container arrival from EDIFACT, customs status).
- **Receives queries** for customs status (from `smartportx-gate` before gate-out).
- **Stores raw messages** in its own schema for audit and replay — never in another service's schema.

#### Anti-Corruption Layer

- Integration is the **primary anti-corruption layer** of the platform. It absorbs the chaotic, variant-laden external world (EDIFACT dialects, proprietary IoT formats, customs message shapes) and normalizes everything into the platform's canonical event contracts before anything crosses into a business service. No business service sees raw EDIFACT; they see clean parsed events.

---

### 2.8 smartportx-notification

#### Boundary Statement

> **Notification owns the user-facing delivery concern: notification preferences, real-time WebSocket delivery, and delivery history.**

#### Inside the Boundary (Included)

- **Notification preferences** — per-user configuration of event types, channels (WebSocket/email/SMS — simulated), quiet hours, priority filter.
- **Delivery (WebSocket)** — STOMP broker over WebSocket (`/ws/notifications`), topic routing.
- **History** — persisted sent-notification log with delivered/read status.
- **Template rendering** — human-readable message formatting from event data.

#### Outside the Boundary (Excluded)

- **Business data** — notification does **NOT** store containers, appointments, yard positions, vessels, or customs status. It holds only delivery artifacts (preferences, history, rendered messages).
- **Business decisions** — it does not decide operational outcomes.

#### Shared Data

- **Subscribes to ALL domain events** via RabbitMQ (from container, yard, gate, vessel, integration).
- **Pushes** user-facing messages to connected WebSocket clients.
- Because it consumes events but owns no business entities, it is a **pure leaf consumer** in the dependency graph.

#### Anti-Corruption Layer

- Notification has the *simplest* relationship to other services: it only reads events and pushes to users. It converts event payloads into rendered, per-user messages via its template layer, so the user-facing wording is decoupled from the raw event schema. Event schema changes are absorbed in the template/rendering layer.

---

## 3. Boundary Enforcement Mechanisms

Boundaries are not just documentation — they must be **mechanically enforced** so that a developer cannot accidentally (or intentionally) violate them. SmartPortX uses the following enforcement layers, from the most concrete (database) to the most abstract (tests).

### 3.1 Schema Isolation (Database Level)

- Each service connects to **its own PostgreSQL schema** using **its own database credentials**.
- No service is provisioned with privileges on another service's schema. The HikariCP pool for each service uses credentials scoped to exactly one schema (typically `{service}_app` with `SELECT/INSERT/UPDATE/DELETE` on that schema and **no cross-schema grants**).
- **Result:** even a buggy or malicious query cannot touch another service's schema — the database itself enforces the boundary.

| Service | Owned schema | DB principal (example) |
|---------|--------------|------------------------|
| gateway | *(none)*     | *(no DB access)*       |
| identity | `smartportx_identity` | `identity_app` |
| yard | `smartportx_yard` | `yard_app` |
| gate | `smartportx_gate` | `gate_app` |
| container | `smartportx_container` | `container_app` |
| vessel | `smartportx_vessel` | `vessel_app` |
| integration | `smartportx_integration` | `integration_app` |
| notification | `smartportx_notification` | `notification_app` |

### 3.2 API Contracts (Contract Level)

- Each service exposes an **OpenAPI spec** describing its public endpoints.
- These specs are treated as **shared contracts** — published artifacts that other services (and the gateway) bind against.
- Contract changes are **versioned**: a breaking change ships as a new version rather than silently breaking consumers.

### 3.3 Event Contracts (Message Level)

- All async messages use a **CloudEvents envelope** schema.
- The envelope is **versioned** (`specversion`, `type`, `source`, `id`, `time`, `data`).
- Event types are namespaced by producer: `container.created`, `yard.position-assigned`, etc.
- Consumers bind to the envelope/type contract, not to ad-hoc payloads.

### 3.4 No Direct Cross-Service DB Access (Runtime Level)

- Enforced via per-service HikariCP pools with schema-scoped credentials (see §3.1).
- A service that needs foreign data has exactly two legal paths:
  1. **Query** the owning service's REST API (sync), or
  2. **Consume** the owning service's event (async).
- There is *no* third path through the database.

### 3.5 Dependency Rules (Graph Level)

- **Gateway → all services** (routes to each). This is the only permitted "fan-out" and it is orthogonal (routing only).
- **No service → gateway.** No service calls the gateway; the gateway is not a business dependency.
- The graph must remain **acyclic** (see §1.4). Cycles are blocked at review time and by graph-level architectural tests.

### 3.6 Architectural Tests (Automation Level)

- **ArchUnit tests** are the enforcement backstop. They verify package-level dependency rules:
  - A service's domain/application package may only reference the owning service's infrastructure packages.
  - No service may import another service's `repository` or `entity` classes (this would signal DB/domain leakage).
  - The dependency graph among service packages is acyclic (guards against accidental cycles).
  - Gateway code does not contain business logic (guarded by checks that gateway packages do not depend on domain packages of other services).
- These tests run in CI and **fail the build** on violation — boundaries are enforced by the pipeline, not goodwill.

---

## 4. Ambiguity Resolutions (Critical)

The fuzzy gray areas — the places where two services could both legitimately claim a responsibility — are the most dangerous to leave unstated. The following rulings are **authoritative**. If you encounter a situation not covered here, resolve it consistently with these precedents rather than re-arguing the boundary.

### 4.1 Who assigns a container to a yard position?

> **Yard Service** owns placement. It has the layout, the capacity rules, the stacking logic, and the equipment — it is the only service with enough context to decide *where* a container physically goes. The Container Service does **not** decide placement; it merely **records** the resulting movement when the Yard event arrives.

**Sequence:** Gate requests a position from Yard → Yard assigns → Yard publishes `YardPositionAssigned` → Container subscribes and records the movement + updates `current_location`.

### 4.2 Who tracks container location?

> **Container Service** stores `current_location` (so container search/read can answer "where is this container?"). **Yard Service** owns the slot assignment (the authoritative physical state).

Both hold location-ish data, but for **different purposes**:
- Yard's `container_positions` is the **authoritative physical truth** (drives layout, utilization, optimization).
- Container's `current_location` is a **read-model projection** for queries/reporting, fed from Yard events.

The Yard is the source of truth; the Container projection is derived. They are reconciled by events, never by two services independently deciding location.

### 4.3 Who processes gate-out when customs has a block?

> **Gate Service** orchestrates gate-out. Before releasing, it **calls Integration Service's customs lookup**. If the status is `HELD`, **Gate blocks** the gate-out and does not issue release/EIR.

Gate owns the gate-out transaction; Integration owns the customs knowledge. Gate is the *decider* of the operational action (release vs. block); Integration is the *source* of the hold state. Gate also subscribes to `CustomsStatusUpdated` to proactively reflect holds.

### 4.4 Who informs the truck driver of the yard location?

> **Gate Service** returns the yard position to the driver **at check-in**, after **fetching it from Yard**.

The driver interacts with the gate; Yard is never directly contacted by the driver. Gate relays the Yard-assigned slot into the gate-in response / EIR so the driver knows where to go. This keeps the driver's interface a single, controlled gateway to the yard.

### 4.5 Who handles a dual transaction (drop-off + pickup)?

> **Gate Service** handles both moves as **a single gate transaction** covering the combined operation.

A dual transaction is a *gate* concern (a truck arriving with one container and departing with another). Gate owns the transaction record and orchestrates both the drop-off position request and the pickup position fetch. Container/Yard each update their own state via events; Gate is the single coordinator so the truck is processed once with one EIR.

### 4.6 Who owns IMDG segregation rules?

> **Yard Service** owns segregation (placement logic). **Container Service** stores the DG metadata (UN number, class, packing group).

Division of responsibility:
- **Container** stores the *facts* about the dangerous goods (the DG data attributes).
- **Yard** owns the *rules* that act on those facts (placing incompatible classes apart, enforcing minimum separation distances).

This is a clean split: *data* in Container, *logic* in Yard. Yard consumes the DG facts via the Container contract/events and applies its segregation rules at placement time.

### 4.7 Who computes yard utilization?

> **Yard Service** owns yard metrics (utilization, occupancy). Dashboards/UI read these via the Yard API.

Utilization is computed from Yard's own authoritative positions — no other service has the data. UI does not compute utilization from scattered position data; it calls `GET /yard/metrics` / `GET /yard/utilization`.

### 4.8 Who creates a discharge plan mapping stowage to yard?

> **Vessel Service** owns the discharge plan. It coordinates by **calling Yard** for position availability and **Container** for validation, but the plan itself is a Vessel-domain artifact.

Yard provides *input* (which positions are available) but does not own the plan. Vessel is the coordinator and owner of the plan's lifecycle. (If a future split is warranted, see [§7.3 split criteria](#72-when-to-split-a-service).)

### 4.9 Who is responsible when a container arrives via EDIFACT?

> **Integration Service** parses the message and **publishes** a clean event (`ContainerCreated`, etc.). **Container Service** is the owner of the container entity and ultimately records it. **Yard** plans placement from that event.

Integration translates; Container is the authority on the container record. Neither steps on the other: Integration never writes to Container's schema; Container never parses EDIFACT.

### 4.10 What happens if two services disagree about a fact?

> The **owning service wins** for its domain. Because data is owned exclusively, there is normally no disagreement — only one authority. If a discrepancy is observed between a projection (e.g., Container's `current_location`) and the authority (Yard's position), the **authority wins** and the projection reconciles from the next event/query.

---

## 5. Cross-Boundary Data Flow Summary

The table enumerates every meaningful data flow between services. Each row names the source, the destination, what data crosses, the mechanism (sync REST vs async event), the direction, and whether it is real-time.

| # | From Service | To Service | Data | Mechanism | Direction | Real-time? |
|---|--------------|------------|------|-----------|-----------|------------|
| 1 | gateway | identity | auth routes, login/refresh proxied | REST (proxy) | → | Yes |
| 2 | identity | gateway | public signing key (config) | static artifact | → | N/A |
| 3 | container | yard | `ContainerCreated` / `ContainerUpdated` | Event (RabbitMQ) | → | Yes |
| 4 | container | integration | `ContainerMoved` / `StatusChanged` | Event (RabbitMQ) | → | Yes |
| 5 | container | notification | lifecycle events | Event (RabbitMQ) | → | Yes |
| 6 | yard | container | `YardPositionAssigned` (→ records movement) | Event (RabbitMQ) | → | Yes |
| 7 | yard | notification | position-assignment / optimization events | Event (RabbitMQ) | → | Yes |
| 8 | yard | gate | appointment schedule (for optimization) | REST query | ← | No (batch) |
| 9 | gate | container | container verification (check-in/out) | REST query | → | Yes |
| 10 | gate | yard | request/fetch yard position (check-in) | REST query | → | Yes |
| 11 | gate | yard | ask for position during dual transaction | REST query | → | Yes |
| 12 | gate | integration | customs status lookup (pre gate-out) | REST query | → | Yes |
| 13 | gate | integration | `GateEvent` (truck-in/out) | Event (RabbitMQ) | → | Yes |
| 14 | gate | notification | gate events | Event (RabbitMQ) | → | Yes |
| 15 | integration | gate | `CustomsStatusUpdated` (blocks gate-out) | Event (RabbitMQ) | → | Yes |
| 16 | integration | container | parsed container data (EDIFACT) | Event (RabbitMQ) | → | Yes |
| 17 | integration | yard | parsed arrival / IoT events | Event (RabbitMQ) | → | Yes |
| 18 | integration | notification | customs / ingest events | Event (RabbitMQ) | → | Yes |
| 19 | vessel | container | container validation (discharge plan) | REST query | → | Yes |
| 20 | vessel | yard | position availability (discharge plan) | REST query | → | Yes |
| 21 | vessel | notification | `VesselArrived` / `DischargeProgress` | Event (RabbitMQ) | → | Yes |
| 22 | container | vessel | (`vessel_id` reference — via container API/search) | REST query | ← | No |

*Direction: `→` means the source sends/initiates data toward the destination; `←` for pull/queries. In all cases the **owning service** remains authoritative over the data that originated in its domain.*

---

## 6. Common Boundary Anti-Patterns to Avoid

These are the failure modes that turn a well-bounded architecture into a distributed monolith. Treat each as a **smell** that requires justification before being tolerated.

### 6.1 Service Acted as a Gateway to Another Service's Data (Pass-Through)

> A service that merely relays another service's data without adding value.

**Symptom:** "Just add a `GET /yard/positions` endpoint to Gate because the client is already talking to us." The gate becomes a proxy for yard, creating unnecessary coupling and two sources of the "same" data path.

**Rule:** If a service exposes a resource, it must **add business value** to it, not just forward it. A client that needs yard data should call the Yard service. If a composite really is needed, the orchestrating service transforms/combines — not pass-through.

### 6.2 Shared Schema Tables

> Two services reading/writing the same table, or cross-schema foreign keys.

**Symptom:** A `JOIN` across `smartportx_gate` and `smartportx_container`, or a shared `port_events` table written by everyone.

**Rule:** Forbidden. No cross-schema FKs, no shared tables. If two services truly need the same rows, one is the owner and the other consumes via contract/event (§1.3, §3.1).

### 6.3 Smart Events (Full-State Payloads)

> Events that carry the entire business state instead of describing a change.

**Symptom:** `ContainerUpdated` carries the complete container record plus yard position plus customs status — because it was easier to dump everything.

**Rule:** Events describe a **change** (`data: {container_id, old_status, new_status}`), not a state snapshot. Full state is for querying via API, not for broadcasting. Smart events couple producers to every consumer's needs and make versioning impossible. (CloudEvents envelope content should be minimal and change-focused.)

### 6.4 Anemic Services (CRUD-Only)

> A service that is just a thin CRUD wrapper around a table — the boundary has no business meaning.

**Symptom:** A "service" that only offers `POST/GET/PUT/DELETE` on a table and makes no domain decisions. It owns a schema but no rules.

**Rule:** Each service must **add value** beyond storage — it must enforce domain rules (lifecycle state machines, validation, optimization, orchestration). A pure CRUD service should be merged into an owning service that gives it meaning, or the boundary reconsidered.

### 6.5 Distributed Monolith

> All services always deployed together and only talking to each other via synchronous REST.

**Symptom:** Every operation fans out through several synchronous REST calls; no async decoupling; services cannot run independently; a single service failure cascades.

**Rule:** SmartPortX uses **async events** (RabbitMQ) for decoupled, fire-and-forget propagation (notification, projection, integration) and **sync REST only where a true query/result is needed**. If a sync dependency is optional for the caller's outcome, move it to an event. Independent deployability is preserved — services do not all ship in lockstep.

### 6.6 Chatty Communication

> Many small calls instead of a batch or filtered query.

**Symptom:** Gate makes 20 individual container lookups to process one batch of arrivals, or loops over appointments issuing one position query each.

**Rule:** Design contracts for **fetch what you need in one call**. Prefer batch endpoints, filter/query params, and pagination over N+1 remote round-trips. Chatty sync communication reintroduces coupling and latency that the boundaries were meant to avoid.

---

## 7. Service Evolution Guidelines

Boundaries are not set in stone — they must evolve as the domain and requirements change. The following guidance governs *how* boundaries may be changed safely.

### 7.1 How Boundaries Change (Style of Change)

Boundaries change through **contract evolution**, never through silent internal coupling.

- **Rename events** — introduce the new event name alongside a deprecation window; keep a translation bridge so consumers can migrate; remove old name only after all consumers have moved.
- **Extend events** — add new fields/attributes to the CloudEvents envelope **additively**. Consumers must ignore unknown fields (forward compatibility). Never delete or reinterpret an existing field in place.
- **Version APIs** — a breaking API change ships as a new OpenAPI version (e.g., `/v2/yard/positions`); the old version is deprecated and retired on a schedule. The gateway routes to the version the client requested.
- **Contract-first** — any change to an API or event is a **contract change** first, reviewed and versioned before implementation. Consumers and producers reference the same published contract artifact.

### 7.2 When to Split a Service

Split a service when **one** of the following holds (especially more than one):

- **>3 unrelated responsibilities** — the service's one-line responsibility statement has become a list, and the responsibilities share no common data or domain rules.
- **Different scaling needs** — one responsibility scales horizontally in a very different way from the rest (e.g., IoT/telemetry ingestion spikes unrelated to appointment CRUD).
- **Different team ownership** — a distinct group needs to evolve, deploy, and own a responsibility on their own cadence (team-locality of change).
- **Independent failure isolation** — a responsibility's failure should not take down the rest of the service.

**Candidate for SmartPortX:** `smartportx-integration` mixes several *translation* concerns (EDIFACT, IoT, customs, adapters). These are currently cohesive under "external translation," but if IoT ingestion or customs begins to scale/throttle independently or gain a separate owner, it is the first candidate for a split (`smartportx-iot`, `smartportx-customs`).

### 7.3 When to Merge Services

Merge two services when **one** of the following holds (especially more than one):

- **Tight coupling** — the two services constantly orchestrate each other or share the same domain concept so closely that the boundary is artificial.
- **Constant cross-service calls** — every meaningful operation in service A requires a synchronous round-trip to service B; the split is costing more than it isolates.
- **Atomic operation requirement** — an operation must update data in both services *atomically* (no acceptable eventual consistency), which a boundary fundamentally prevents. If two "services" cannot tolerate eventual consistency between them, they are really one bounded context.

**Rule of caution:** Merging usually signals the boundary was drawn in the wrong place, not that SOA is failing. Prefer **re-drawing the boundary correctly** over merging two *cohesive* services. Only merge when the cohesion is genuine and the split clearly costs more than it provides.

---

## Appendix A — Boundary Checklist

Use this checklist to gate any decision that touches more than one service:

- [ ] Does each service still have a **one-line** responsibility statement?
- [ ] Is each schema owned by **exactly one** service (no shared/foreign cross-schema access)?
- [ ] Is the dependency graph still **acyclic**? Does any service depend on the gateway?
- [ ] Does cross-service data cross via **API or event** (never a shared table)?
- [ ] Are events **change-focused** (not full-state "smart events")?
- [ ] Does every service **add business value** (not pure pass-through or anemic CRUD)?
- [ ] Are sync calls **batch/filtered** (not chatty N+1)?
- [ ] Are contract changes **versioned** and additive, with a deprecation path?
- [ ] Do ArchUnit tests still **pass** (package dependency rules)?

If any box is unchecked, the boundary is drifting — fix it before it compounds.

---

## Appendix B — Source of Truth Hierarchy

```
OWNING SERVICE  ──►  authoritative for its domain
      │
      ├── exposed via  REST API  (sync queries / commands)
      ├── broadcast via RABBITMQ (async events / projections)
      └── reflected into      OTHER services' read models (derived, non-authoritative)
```

- The **owner's schema** is the only mutable source of truth for its entities.
- **Projections** (e.g., Container's `current_location`) are derived and reconcilable, never authoritative.
- When in doubt about who owns a fact, ask: **"Which service would a change to that fact originate from?"** That service owns it.
