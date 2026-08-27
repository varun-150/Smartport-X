# SMARTPORTX — PHASE 4 IMPLEMENTATION PLAN

> Status: ACTIVE (draft v1.0) — 2026-08-27
> Phase 4 = Implementation + Integration + Continuous Verification (per user brief, 2026-08-27).
> This document is the master implementation plan; it supersedes the earlier ROADMAP label
> "Phase 4 — Implementation Planning". Planning is now §1–§4 of this file; the remainder is execution.
> Source-of-truth hierarchy (§3 of brief): Architecture > Requirements > ADRs > Decisions > Code > Judgment.

--------------------------------------------------------------------------------
## 1. PHASE 4 SCOPE

Implement SmartPortX as a FUNCTIONAL, INTEGRATED, TESTABLE system per the APPROVED
Phase 3 architecture. No fake data, no placeholders, no mocked business logic, no
disconnected frontend screens.

**In scope (must be real and verified):**
- 8 services + Eureka + Gateway + React dashboard (per SERVICE-CATALOG.md)
- 7 PostgreSQL schemas / 33 tables (per DATA-ARCHITECTURE.md)
- RabbitMQ event topology (per COMMUNICATION-ARCHITECTURE.md)
- JWT RS256 + refresh rotation + RBAC (per SECURITY-ARCHITECTURE.md)
- Resilience4j + Micrometer + OTel (per RESILIENCE-/OBSERVABILITY-ARCHITECTURE.md)
- EDIFACT inbound COPRAR/COPARN, IoT sim, customs sim (per INTEGRATION-ARCHITECTURE.md)
- Docker Compose deployment + GitHub Actions CI (per DEPLOYMENT-ARCHITECTURE.md)
- Vertical-slice E2E workflows, unit/integration/API/contract/security tests

**Out of scope (approved deferrals):** outbound EDIFACT generation (COARRI/CODECO/IFTMIN —
INTEGRATION-ARCHITECTURE §3.5 "Future"), live IoT/customs external feeds (§4.3/§5.4), production
Kubernetes (§2.3), event sourcing with immutable log (requires Kafka — ADR-005/ARCHITECTURE-TRADEOFFS §12).

--------------------------------------------------------------------------------
## 2. REPOSITORY FOUNDATION (copy of §7 of brief — structural contract)

Monorepo, root = D:\SOA FINAL PROJECT. Backend services as independent Maven modules
referencing a shared parent POM. Docker build context per service matching DEPLOYMENT-ARCHITECTURE §4.

```
smartportx/
├── pom.xml                          # parent POM (Spring Boot BOM, Java 25, module list)
├── smartportx-common/               # shared: error model, tracing, health, events, JWT primitives
├── smartportx-eureka/               # discovery server (8761)
├── smartportx-gateway/              # Spring Cloud Gateway (8080)
├── smartportx-identity/             # identity service (8081)
├── smartportx-container/            # container service (8084)
├── smartportx-yard/                 # yard service (8082)
├── smartportx-gate/                 # gate service (8083)
├── smartportx-vessel/               # vessel service (8085)
├── smartportx-integration/          # integration service (8086)
├── smartportx-notification/         # notification service (8087)
├── smartportx-dashboard/            # React 18 + TypeScript (3000)
├── docker-compose.yml               # full topology (DEPLOYMENT-ARCHITECTURE §3.1)
├── .env.example                     # all env vars, no secrets
├── scripts/
│   ├── init-databases.sh            # create 7 schemas + roles on localhost PG
│   ├── seed-data.sql                # baseline realistic test data (D-010 generator)
│   └── dev-up.ps1                   # Windows: build + run native (no Docker, WSL2 fallback)
└── .github/workflows/ci.yml         # build/test/scan/docker (DEPLOYMENT-ARCHITECTURE §6.1)
```

**Naming:** services are `smartportx-<name>` for artifact/dir; Spring `spring.application.name`
= `<name>` (eureka ids registered via `smartportx-<name>`); Docker images `smartportx/<name>:latest`.
**Config:** one `application.yml` + `application-<env>.yml` per service, no duplicate shared config
(shared via smartportx-common modules and env vars). Secrets NEVER in git.

--------------------------------------------------------------------------------
## 3. EXECUTION MODEL & QUALITY GATES

- Each increment is a **vertical slice**: DB migration → entity → repository → service →
  controller → security/roles → tests → (frontend where applicable) → verify → evidence.
- **TDD where practical:** write failing test → implement → refactor (brief §29).
- Status vocabulary ONLY (brief §42): NOT STARTED / SCAFFOLDED / IN PROGRESS / IMPLEMENTED /
  TESTED / VERIFIED / BLOCKED.
- Every completed slice must satisfy the Quality Gate (§41 of brief): implemented, integrated,
  unit tested, integration tested where required, API tested, security checked, failure cases
  considered, architecture reviewed, critic reviewed, evidence recorded.
- Continuously: PLAN → IMPLEMENT → TEST → REVIEW → ATTACK → FIX → VERIFY → DOCUMENT → EVIDENCE.
- Architecture change control (§4 of brief): any contract/architecture drift → STOP, create
  ADR, architect+security+critic review, update docs, then implement.

### Test commands (must pass before a milestone is marked complete)
- Backend:  `cd smartportx && mvn -q clean verify`  (unit + integration via real Postgres)
- Frontend: `cd smartportx-dashboard && npm run lint && npm run build && npm test`
- Native stack: `scripts/dev-up.ps1` then E2E journey scripts.
- CI: GitHub Actions `ci.yml` runs the same gates on every push.

--------------------------------------------------------------------------------
## 4. DECISION & CONFLICT BASELINE (recorded in PHASE-4-DECISIONS.md)

Conflicts found while reading the approved docs (per §3 source-of-truth rule — STOP, analyze,
record, proceed). See PHASE-4-FINDINGS.md F-401..F-406 for full analysis.

| # | Conflict (docs disagree) | Resolution (canonical) |
|---|--------------------------|-------------------------|
| C-1 | RabbitMQ topology differs | Adopt COMMUNICATION-ARCHITECTURE.md: 3 exchanges `smartportx.domain-events`, `smartportx.notifications`, `smartportx.audit-events`; queue `{service}.{event-type}`; DLQ `{queue}.dlq`; retry `{queue}.retry`; routing key `{service}.{event-type}`. SERVICE-CATALOG §4.2 single-exchange sketch superseded. |
| C-2 | Container status enum | Adopt DATA-ARCHITECTURE §7.1: REGISTERED, AT_GATE, IN_YARD, LOADED, DISCHARGED, AT_GATE_OUT, DEPARTED, HELD. ARCHITECTURE-OVERVIEW old enum superseded (see F-403). |
| C-3 | Role naming/enum | Canonical role claims: ADMIN, TERMINAL_OPERATOR, YARD_MANAGER, GATE_OPERATOR, READ_ONLY (+ ACTUATOR_READ for health). NFR/D-013 "5 roles" wins. Old variants (OPERATOR, GATE_AGENT, TERMINAL_OP, READONLY, CUSTOMS-as-user-role) mapped/retired. CUSTOMS is a *gate condition*, not a user role. |
| C-4 | EDIFACT parser (F-004 carryover) | Use **StAEDI (io.xlate:staedi v1.26.x)** for inbound COPRAR/COPARN streaming parse; hand-rolled segment mapping to domain model. Apache-2.0, active (2026), Maven Central. JEDII stale → rejected. |
| C-5 | Requirement counts (F5-005 carryover) | Use 46 FR / 16 NFR groups / 36 SR as canonical; stale "38/24/22" claims corrected in docs. |
| C-6 | Phase numbering | User brief "Phase 4 = Implementation" governs; ROADMAP Phase 4 = THIS plan+implementation; old "Phase 5 implementation / Phase 6 testing" labels folded into this phase's milestone M12 test gate. |

--------------------------------------------------------------------------------
## 5. MILESTONES & INCREMENTS (build order derived from dependency depth)

Dependency depth (SERVICE-CATALOG §3): Level 0 core = identity, container.
Level 1 primary = yard (→container, gate). Level 2 = gate, vessel, integration. Level 3 = gateway, notification.
Foundations first: repo (M0) → infra (M1) → common (M2) → core (M3/M4) → yard (M5) →
gate (M6) → vessel (M7) → integration (M8) → notification (M9) → gateway/dashboard (M10/M11) →
hardening/test reports (M12) → final gate (M13).

--------------------------------------------------------------------------------
### M0 — REPOSITORY FOUNDATION
**Goal:** buildable monorepo skeleton; every module compiles; CI green on empty scaffolds.
**DoD:** `mvn -q clean verify` succeeds at root (all modules), frontend scaffolds and builds, git initialized.

Increments:
- M0.1 Root layout, parent POM, .gitignore, .editorconfig, README (repo structure + commands)
- M0.2 `smartportx-common` scaffold (empty module + minimal shared classes: correlation-id MDC filter, RFC 9457 ProblemDetail base, health/actuator config)
- M0.3 Eight service scaffolds + eureka + gateway: each a compile-only Spring Boot 3.x app with `spring.application.name`, actuator, eureka client, empty controllers package
- M0.4 `smartportx-dashboard` scaffold: Vite + React 18 + TS + ESLint + Vitest smoke render
- M0.5 GitHub Actions `ci.yml` (build+test stages only; docker/scan stages activated in M12)
- M0.6 git initial commit (baseline)

Evidence per increment: build output, tests run, file list → EVIDENCE.md E-02x.

--------------------------------------------------------------------------------
### M1 — INFRASTRUCTURE (local-first, WSL2-independent where possible)
**Goal:** local Postgres with 7 schemas; RabbitMQ topology scriptable; compose file matches DEPLOYMENT-ARCHITECTURE §3.
**Note:** Docker daemon unavailable until WSL2 is enabled (R-007). Postgres 17.11 runs natively on
localhost:5432 → use it for dev/testing. RabbitMQ: `choco install rabbitmq` OR enable WSL2+Docker.
`scripts/init-databases.sh` targets localhost PG regardless of containerization.
**DoD:** `scripts/init-databases.sh` creates 7 schemas idempotently; RabbitMQ exchange/queue declaration
script idempotent; compose file lints (`docker compose config`).

Increments:
- M1.1 `scripts/init-databases.sh`: create DB `smartportx` + schemas identity/container/yard/gate/vessel/integration/notification + dev users; re-runnable
- M1.2 `docker-compose.yml` per DEPLOYMENT-ARCHITECTURE §3.1 (pg, rabbitmq, prometheus, grafana, jaeger, eureka, + 8 app services, dashboard)
- M1.3 `scripts/rabbit-topology.sh`: declare 3 exchanges, per-event queues+DLQ+retry per COMMUNICATION-ARCHITECTURE
- M1.4 `.env.example` with all vars from DEPLOYMENT-ARCHITECTURE §5.2 (no secrets)
- M1.5 Flyway convention: per-service `db/migration/V1__*.sql` skeleton files present (real DDL added in each service milestone)

--------------------------------------------------------------------------------
### M2 — smartportx-common (shared library)
**Goal:** one shared library: error model, security filter chain helpers, event envelope, observability/tracing, pagination DTOs.
**DoD:** unit tests for ProblemDetail mapper (hides stack traces), correlation filter (assigns/propagates trace id), page request binding.

Increments (each TDD):
- M2.1 RFC 9457 error model: `ApiError`, `ProblemDetail` builder, `GlobalExceptionHandler` (validation → 400 fields, not-found → 404, conflict → 409, server → 500 no leak). Tests: no stacktrace/password/secret leakage (sec test bilingual void).
- M2.2 Correlation ID: `CorrelationIdFilter` (X-Correlation-Id in/out, logs include it), `CorrelationContext`.
- M2.3 Pagination + sorting base DTOs; RFC 9457 validation errors (field:message map).
- M2.4 Event envelope: `DomainEvent<T>` (eventId, type, occurredAt, correlationId, payload), Avro-free JSON via Jackson; durable semantics doc.
- M2.5 Observability: Micrometer counters (http, events), health custom indicators, OpenTelemetry tracing auto-config (smartportx-common starter pattern for every service).
- M2.6 Shared spring-boot-starter `smartportx-starter-common` aggregating the above (used by all services) — OR module dependency; decide by build friction at M2.5.

--------------------------------------------------------------------------------
### M3 — IDENTITY SERVICE (Level 0 core; do early — unblocks auth everywhere)
**Source of truth:** SECURITY-ARCHITECTURE.md, DATA-ARCHITECTURE §5, API-CONTRACTS identity endpoints, ADR-007/009.
**DoD:** register/login/refresh/logout + RBAC enforced at method level + password history; JWT RS256 verified; security tests pass.

Increments (TDD):
- M3.1 Flyway V1: users, roles, user_roles, refresh_tokens, password_history (DATA-ARCHITECTURE §5.1–5.5) + seed admin/operator/gate/yard/read-only test users (BCrypt hashes, no plaintext)
- M3.2 JPA entities + repositories (unique email, role enum, token — cryptographically random, hashed storage for refresh tokens)
- M3.3 Registration: email+password, BCrypt, validation, duplicate → 409 (RFC 9457), audit log write
- M3.4 Login: verify BCrypt → issue RS256 access (15 min) + signed JTI refresh (7 days, one-time use) + store hash + rotate-on-refresh w/ revocation on reuse (SECURITY-ARCHITECTURE)
- M3.5 Refresh flow: rotation, reuse detection → revoke family, lock account; logout revokes
- M3.6 User admin: list/create/assign role/disable (ADMIN only, method-security @PreAuthorize)
- M3.7 Identity→(all) contract tests for `/auth/*` (Spring Cloud Contract provider tests)
- M3.8 Security tests: missing/expired/malformed/forged token, IDOR, password policy, rate-limit login (Resilience4j/Reactor rate limiter per SECURITY-ARCHITECTURE), lockout after N fails

--------------------------------------------------------------------------------
### M4 — CONTAINER SERVICE (Level 0 core — central domain)
**Source of truth:** DATA-ARCHITECTURE §7, API-CONTRACTS container endpoints, COMMUNICATION-ARCHITECTURE events, INTEGRATION-ARCHITECTURE §8 outbound events.
**DoD:** register; state machine (REGISTERED→AT_GATE→IN_YARD→LOADED→DISCHARGED→AT_GATE_OUT→DEPARTED; HELD allowed from several); movements + status history + event log; events published; invalid transitions rejected 409.

Increments (TDD):
- M4.1 Flyway V1: containers, container_movements, container_events, container_status_history (§7.1–7.4) + indexes
- M4.2 Entities/repos + validations (container_number UNIQUE ISO 6346 checksum test, container_type enum, weight ranges, IMDG fields)
- M4.3 Register container (ADMIN/TERMINAL_OPERATOR), duplicate → 409, event `ContainerRegistered`
- M4.4 State machine service (pure domain): allowed-transition table, rejected → 409 with prior/new state in error body; status_history append
- M4.5 Movement recording: GATE_IN/OUT, YARD_MOVE, VESSEL_LOAD/DISCHARGE with from/to location, performed_by audit
- M4.6 Query: get by number/barcode, history/replay, filter+pagination (M2.3)
- M4.7 Event publisher: `ContainerStatusChanged`, `ContainerMoved`, `ContainerRemoved` → smartportx.domain-events (routing `container.{type}`), idempotent consumers later
- M4.8 API tests incl. unauthorized/IDOR; contract tests (provider) for `/api/containers/*`

--------------------------------------------------------------------------------
### M5 — YARD SERVICE (Level 1 — owns slots)
**Source of truth:** DATA-ARCHITECTURE §6, API-CONTRACTS yard endpoints, COMMUNICATION-ARCHITECTURE yard events, RESILIENCE-ARCHITECTURE.
**DoD:** block/bay/row/tier inventory; `assignSlot` returns concrete position, conflict → 409; optimization baseline (= D-006 simplified heuristics) with explainable reason; `YardPositionAssigned/Freed` events; integration → container status + gate available.

Increments (TDD):
- M5.1 Flyway V1: yard_blocks, yard_bays, yard_rows, yard_tiers, yard_metrics, optimization_history (§6.1–6.6) + sample block seed
- M5.2 Slot model: full location key BLOCK-BAY-ROW-TIER; constraints (IMDG segregation, reefer power bays option), occupancy query (JPA query vs cache; N+1 guard)
- M5.3 `assignSlot(container)` — nearest-walk heuristic + IMDG separation; returns position or 409 if none; `YardPositionAssigned` event
- M5.4 `freeSlot`, `moveContainer` (single/dispatch), conflict check (two moves same slot → 409), `YardPositionFreed`
- M5.5 Optimization baseline `yard.optimization-run`: count reshuffles, suggest moves for a block; **explainable** (`reason` string: nearest block / IMDG segregation / reshuffle-reduction); stores optimization_history row
- M5.6 Yard API tests + contract tests (provider) for `/api/yard/*`
- M5.7 Client stubs: yard→container, yard→gate (Resilience4j, 5s/1 retry) — callable, verified via contract consumer tests

--------------------------------------------------------------------------------
### M6 — GATE SERVICE (Level 2 — appointments + check-in/out)
**Source of truth:** DATA-ARCHITECTURE §8, API-CONTRACTS gate endpoints, WORKFLOWS.md, INTEGRATION-ARCHITECTURE customs gate-out.
**DoD:** quota-based appointment booking; check-in validates appointment+container(container svc)+customs status (integration svc); check-out validates yard position; queue_status; GateCheckIn/Out + Appointment… events.

Increments (TDD):
- M6.1 Flyway V1: appointments, gate_transactions, queue_status, appointment_quotas (§8.1–8.4) + timezone rules
- M6.2 Appointment booking: quota per slot (peak/off-peak), validation (time window, truck/plate/container ref; container need not exist yet for pre-announce), cancel, no-show policy; `AppointmentBooked/Confirmed/Cancelled`
- M6.3 Truck check-in (GATE_AGENT): find appointment, call container svc (M4) to confirm existence+state, call integration svc customs status (must be PENDING→ok or CLEARED; HELD blocks gate-out); create gate_transaction GATE_IN; container→AT_GATE; `GateCheckIn`
- M6.4 Queue: append truck, update queue_status (len/wait), `GateQueueUpdated`
- M6.5 Truck check-out: verify position assigned (yard svc), container not HELD, create GATE_OUT transaction, container→AT_GATE_OUT→DEPARTED; `GateCheckOut`
- M6.6 Duplicate/late/repeated handling: same appointment second check-in → 409; late arrival policy (grace window) → reschedule path; unauthorized truck → 403
- M6.7 Gate API + contract tests (provider), consumer contract tests for container/yard/integration calls

--------------------------------------------------------------------------------
### M7 — VESSEL SERVICE (Level 2)
**Source of truth:** DATA-ARCHITECTURE §9, API-CONTRACTS vessel endpoints.
**DoD:** vessel registry, schedules (arrival/departure/cutoff), discharge plans with containers; events VesselArrived / discharge progress.

Increments (TDD):
- M7.1 Flyway V1: vessels, vessel_schedules, discharge_plans, discharge_containers (§9.1–9.4) + seed
- M7.2 Vessel CRUD + schedule (ETA/ETD, cutoff time, berth)
- M7.3 Discharge plan: create from container list (calls container svc M4 verify states), status, progress tracking; `VesselArrived`, `DischargeProgressUpdated`
- M7.4 Vessel→container/yard client stubs (Resilience4j); discharge triggers container DISCHARGED→YARD position via yard svc
- M7.5 Vessel API + contract tests

--------------------------------------------------------------------------------
### M8 — INTEGRATION SERVICE (Level 3 — EDIFACT, IoT, customs simulation)
**Source of truth:** INTEGRATION-ARCHITECTURE.md (all), DATA-ARCHITECTURE §10, API-CONTRACTS integration endpoints, D-007/D-008.
**DoD:** inbound COPRAR + COPARN parse → domain events; CODECO/COARRI generation STUB-only (deferred gen is out of scope); IoT sim producing normalized readings; customs sim (PENDING→CLEARED/REJECTED w/ rate); CSv vault; every inbound message validated + retried + DLQ'd; idempotent.

Increments (TDD):
- M8.1 Flyway V1: edifact_messages, iot_readings, customs_status (§10.1–10.3) + indexes on message_ref/correlation
- M8.2 StAEDI integration: declare dependency io.xlate:staedi 1.26.x; parser wraps StAEDI events → canonical segment model; **no networking**, pure transformer (F-004 decided)
- M8.3 COPRAR parser mapping: UNH->LIN (equipment ref), segments → ContainerDischargeOrder (discharge/load order); validation errors → RFC 9457 ProblemDetail + edifact_message error row; malformed → retry then DLQ
- M8.4 COPARN mapping: container announcement → validation+normalize → publish `ContainerArrivalAnnounced`/`MTL` event (INTEGRATION-ARCHITECTURE §8.1)
- M8.5 **IDEMPOTENCY**: dedupe by (interchange_ref, message_ref) → duplicate 409/ignore; tested with replay
- M8.6 IoT simulation: config-driven generator (gate/reefer/yard sensors), normalized `iot_readings`, alerts on threshold (reefer temp, gate pressure) → notification event
- M8.7 Customs simulation: config rates (PENDING→CLEARED 70%/REJECTED), customs_status per container, gate-out validation API (container→integration "is gate-out allowed?")
- M8.8 EDIFACT message audit trail (INTEGRATION-ARCHITECTURE §3.4) + retry/DLQ wiring (`{service}.{event-type}` retry queue)
- M8.9 Integration API + contract tests (provider for custom status; consumer for container/yard/gate)

--------------------------------------------------------------------------------
### M9 — NOTIFICATION SERVICE (Level 3 — WebSocket + alerts)
**Source of truth:** COMMUNICATION-ARCHITECTURE (WebSocket §, topics table), API-CONTRACTS notification endpoints, INTEGRATION-ARCHITECTURE events.
**DoD:** consumes domain/notification exchanges → pushes to WebSocket topics (12 topics); alert dedupe; dashboard KPI stream; auth filter on WS (token query param).

Increments (TDD):
- M9.1 Flyway V1: notifications (optional persisted inbox) if defined in DATA-ARCHITECTURE §11 (else event-in/out only; confirm — see F-407)
- M9.2 WebSocket infrastructure: STOMP endpoint `/ws`, simple broker `/topic`, JWT handshake filter (validates RS256), channel interceptor role check where topics are role-scoped (COMMUNICATION-ARCHITECTURE roles table)
- M9.3 Rabbit→WS bridge: consumer `notification.domain-events` → dispatch to `/topic/yard-status`, `/topic/gate-status`, `/topic/containers/{containerNumber}`, `/topic/vessel-status`, `/topic/dashboard/summary`, `/topic/alerts{/severity}` etc. (12 topics exactly)
- M9.4 Alert engine: severity mapping, dedupe window, push `/topic/alerts`
- M9.5 WS tests: connect/auth fail, subscribe allowed/denied per role, message received on event

--------------------------------------------------------------------------------
### M10 — DISCOVERY + GATEWAY
**Source of truth:** DEPLOYMENT-ARCHITECTURE §4.3 (eureka dockerfile), SERVICE-CATALOG §2.1/§2.2, COMMUNICATION-ARCHITECTURE routes, API-CONTRACTS.
**DoD:** eureka registers all services; gateway routes `/api/*` → services; JWT validation at edge; role-based route whitelist (COMMUNICATION-ARCHITECTURE role-routes table); correlation propagation; 5s timeout; rate limit; RFC 9457 normalization. NO business logic in gateway.

Increments (TDD):
- M10.1 Eureka server (8761) with security (basic admin via env), dashboard
- M10.2 Service registration: each service registers (application.yml eureka config in M0.3 revisited)
- M10.3 Gateway routes: `/api/identity/**` → identity(8081), `/api/container/**` → container(8084), `/api/yard/**` → yard(8082), `/api/gate/**` → gate(8083), `/api/vessel/**` → vessel(8085), `/api/integration/**` → integration(8086), `/api/notification/**` → notification(8087); discovery-based lb
- M10.4 Edge JWT filter (RS256, injects X-User-Id/X-User-Role to downstream), public whitelist (login/refresh), role-route map
- M10.5 Correlation propagation (X-Correlation-Id across hops), trace id → MDC
- M10.6 Timeout (5s), Resilience4j circuit breaker on route, rate limiting (token bucket per IP/route)
- M10.7 Gateway error normalization to RFC 9457; actuator health; contract tests for a read route (identity ‖ container)

--------------------------------------------------------------------------------
### M11 — DASHBOARD (React 18 + TS)
**Source of truth:** D-009/D-026 Frontend, FR-006 real-time dashboards, COMMUNICATION-ARCHITECTURE WS topics, design in ARCHITECTURE-OVERVIEW §UI.
**No fake data, no static JSON. Every screen hits real backend or real WS topics.**
**DoD:** login works against real identity; dashboard KPIs live via `/topic/dashboard/summary`; container/yard/gate/vessel/tracking screens functional with token auth; loading/empty/error/unauthorized states handled.

Increments:
- M11.1 App shell: routing, auth context (persist access/refresh, silent refresh), axios interceptor (401→refresh→retry once → logout), env config
- M11.2 Login page (+ register if approved) against real `/auth/login`; role-aware default landing
- M11.3 Dashboard: WebSocket hook to `/topic/dashboard/summary` + alerts; live KPIs (gate waiting, yard occupancy, container movements); empty/loading/error states
- M11.4 Containers screen: search/list (real API), detail + tracking timeline (events), state badge
- M11.5 Yard screen: block/bay view (real data), optimization panel ("why" reason), move request
- M11.6 Gate screen: appointment booking (real API), live queue via `/topic/gate-status` + `/topic/gate-lane/{laneId}`, check-in/out buttons (per role)
- M11.7 Vessel screen: schedules, discharge progress live via `/topic/vessel/{vesselId}`
- M11.8 Alerts + admin screen (user mgmt, roles); READ_ONLY sees read-only surfaces (frontend hiding is NOT authz — backend still enforces)
- M11.9 a11y/state audit (building-accessible-interfaces): focus, contrast, reduced motion; Playwright smoke E2E (login→dashboard)

--------------------------------------------------------------------------------
### M12 — HARDENING & TEST REPORTS (Phase 4 test gate)
**Goal:** the E2E, SECURITY, PERFORMANCE test reports required by brief §50; concurrency + failure evidence; CI with docker/scan stages turned on.
**DoD:** all three reports written with REAL results; CI green; critic/QA/security sign-off; no critical blocker.

Increments:
- M12.1 E2E journeys (Playwright + rest clients): LOGIN→CREATE APPOINTMENT→TRUCK ARRIVAL→GATE VALIDATION→CONTAINER PROCESSING→YARD OPERATION→STATE CHANGE→DASHBOARD UPDATE→NOTIFICATION→AUDIT (brief §33); also vessel discharge flow; EDIFACT COPRAR→yard pre-alloc flow
- M12.2 Failure testing (brief §34): service down (gateway 502 + circuit open), DB down (service 503 + health RED), timeout, duplicate event (idempotency), expired token, invalid transition, DLQ route
- M12.3 Concurrency tests (brief §36): two users same container, two trucks same appointment, two moves same slot, duplicate events — assert no silent corruption (pessimistic/optimistic locking per concurrency analysis in ADR/FOLLOW-UP)
- M12.4 Security tests (brief §35 + SECURITY-TEST-REPORT.md): JWT attack matrix, password policy, IDOR sweep, injection/input sweep, secrets scan (gitleaks) — Security agent sign-off
- M12.5 Performance tests (PERFORMANCE-TEST-REPORT.md): NFR 2.x latency under load (e.g., 100 concurrent), p95/p99, memory, DB query plan N+1 audit; thresholds per NON-FUNCTIONAL-REQUIREMENTS
- M12.6 CI/CD finalization: docker build stage, security scan stage (DEPLOYMENT-ARCHITECTURE §6.4/6.5), deploy stage notes
- M12.7 Requirement traceability sweep: 46 FR / 16 NFR / 36 SR → each VERIFIED with evidence ID (EVIDENCE.md), gaps → IMPLEMENTED-awaiting-verification flagged honestly

--------------------------------------------------------------------------------
### M13 — FINAL PHASE 4 GATE + REAL-WORLD DEMO
- M13.1 One complete realistic workflow demoed on the running native stack (dev-up.ps1) with real frontend/API/services/DB/auth/state/events/observability (brief §51)
- M13.2 PHASE-4-REPORT.md (brief §52 template, 30 sections), scoring per §53
- M13.3 Architect/Critic/QA/Security signature pass; complete gate checklist (§54)
- M13.4 Final decision: READY FOR PHASE 5 (hardening 5.B) or NOT READY (with blocker table §55)

--------------------------------------------------------------------------------
## 6. CONTRACT TESTING (from day one — Phase 3 recommendation §12)
- Framework: **Spring Cloud Contract** (Verifier + Stub Runner), fits the BOM, no extra broker.
- Provider: identity `/auth`, container `/api/containers`, yard `/api/yard`, gate `/api/gate`, vessel `/api/vessel`, integration `/api/integration/customs` → publish contracts in repo `contracts/` dirs by service.
- Consumers: gateway (edge read), gate→container/yard, yard→container/gate, vessel→container/yard, integration→container/yard/gate.
- Plus: `wiremock` for circuit tests; Playwright for browser E2E.

--------------------------------------------------------------------------------
## 7. OBSERVABILITY & AUDIT (implemented per service, verified continuously)
- Structured JSON logs + MDC (correlation id, user id, service) — OBSERVABILITY-ARCHITECTURE §logging
- Micrometer: `/actuator/metrics` http/event counters; custom indicators per service
- OpenTelemetry traces exported to Jaeger (:16686) — D-025
- Prometheus scrape on `:9090`; Grafana `:3001` dashboards (service UPTIME, HTTP, RABBIT, PG) per OBSERVABILITY-ARCHITECTURE
- Audit: identity writes `audit_events` (who/what/when/ip/outcome); service events published to `smartportx.audit-events`

--------------------------------------------------------------------------------
## 8. SECURITY OPERATING RULES (Security agent has BLOCKING authority — brief §35)
- BCrypt (≥12) for passwords; no plaintext ever; secrets only via env; `.env` NOT committed
- JWT RS256, keys generated offline & provided via env in dev seed; 15m access / 7d rotating refresh with reuse detection
- Method-level `@PreAuthorize` on every mutating + sensitive endpoint; gateway role-route as defense-in-depth, NOT the authorization source
- Input validation everywhere (bean validation + service-level); SQL via JPA (parameterized); CSRF n/a (token-based) but CORS locked to dashboard origin
- TLS 1.3 for prod compose (cert env note); rate limiting on /auth/* and gateway (SECURITY-ARCHITECTURE)
- Security test added for EVERY auth-sensitive slice (no exceptions)

--------------------------------------------------------------------------------
## 9. RESILIENCE (per RESILIENCE-ARCHITECTURE)
- Resilience4j: timeout 5s, retry 1, circuit breaker (failureThreshold=5, open 60s, halfOpen 3) on all inter-service clients — SERVICE-CATALOG §4.1
- RabbitMQ: manual ack, retry queue with TTL, DLQ after max attempts; consumers idempotent by eventId (dedupe table or idempotency key per consumer)
- DB: optimistic locking (`@Version`) on mutable aggregates (appointments slot, container position, quotas), unique constraints as backstop; Flyway for schema (no manual DDL)
- Service down → gateway 503/502 RFC 9457; dashboard shows degraded state, not blank

--------------------------------------------------------------------------------
## 10. REPORTING CADENCE
- After each milestone: update IMPLEMENTATION-STATUS.md (per-service/feature statuses), EVIDENCE.md (E-IDs), PHASE-4-FIXES.md (bugs found+fixed with regression test proof), RISKS.md (deltas)
- Findings/decisions appended to PHASE-4-FINDINGS.md / PHASE-4-DECISIONS.md as they occur
- Milestone summaries emitted with real test counts and evidence IDs — no fabricated claims

--------------------------------------------------------------------------------
## 11. OPEN ENVIRONMENT BLOCKERS (tracked; must be surfaced, not hidden)
- **R-007/WSL2:** Docker daemon needs `wsl --install` (Administrator) + restart. Local Postgres is the
  active dev DB; RabbitMQ needed for event integration → fallback `choco install rabbitmq; rabbitmq-plugins enable rabbitmq_management`
  OR enable WSL2. Milestones M1.3/M4.7/M6-M8 depend on a running broker; gate/dashboard E2E need both.
- **F-407 (data catalog gap):** Notification schema not confirmed in DATA-ARCHITECTURE §10→11 range — verify §11 before M9.1 (recorded as finding, resolved by doc read during M9).
- **Docker image node:20-alpine vs local Node 24** — non-issue (container pins build image).

--------------------------------------------------------------------------------
## 12. APPROVAL GATES
1. **EOD-1 (after M0):** repo skeleton compiles; user confirms directory layout aliasing — NOT blocked.
2. **EOD-2 (after M3):** identity/auth working + tested → architecture change control absent.
3. **FINAL GATE (M13):** STOP. No Phase 5 until explicit user approval (brief §49.7, §56).