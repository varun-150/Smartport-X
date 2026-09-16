# SMARTPORTX — IMPLEMENTATION STATUS

> Living tracker. Updated at every milestone close (plan §10). Status vocabulary (brief §42):
> NOT STARTED / SCAFFOLDED / IN PROGRESS / IMPLEMENTED / TESTED / VERIFIED / BLOCKED.
> Evidence IDs refer to EVIDENCE.md. Requirements reference the canonical counts:
> 46 FR (REQUIREMENTS.md) / 16 NFR groups (NON-FUNCTIONAL-REQUIREMENTS.md) / 36 SR (SECURITY-REQUIREMENTS.md).
> Last updated: 2026-08-31 (M8 integration verified; M7 vessel verified; M6 gate verified).

--------------------------------------------------------------------------------
## 1. SERVICE STATUS

| Service | Dir | Port | DB schema | Impl | Unit | Integ | API/Contract | Sec | E2E | Critic | Evidence |
|---------|-----|------|-----------|------|------|-------|--------------|-----|-----|--------|----------|
| smartportx-common | smartportx-common | — | — | TESTED | TESTED | — | — | — | — | — | E-023 |
| smartportx-identity | smartportx-identity | 8081 | smartportx_identity | SCAFFOLDED | — | — | — | — | — | — | E-021 |
| smartportx-container | smartportx-container | 8084 | smartportx_container | SCAFFOLDED | — | — | — | — | — | — | E-021 |
| smartportx-yard | smartportx-yard | 8082 | smartportx_yard | VERIFIED | — | 37 | — | — | — | — | E-027 |
| smartportx-gate | smartportx-gate | 8083 | smartportx_gate | VERIFIED | — | 63 | — | — | — | — | E-028 |
| smartportx-vessel | smartportx-vessel | 8085 | smartportx_vessel | VERIFIED | — | 22 | — | — | — | — | E-029 |
| smartportx-integration | smartportx-integration | 8086 | smartportx_integration | VERIFIED | — | 31 | — | — | — | — | E-030 |
| smartportx-notification | smartportx-notification | 8087 | smartportx_notification | SCAFFOLDED | — | — | — | — | — | — | E-021 |
| smartportx-gateway | smartportx-gateway | 8080 | — | SCAFFOLDED | — | — | — | — | — | — | E-021 |
| smartportx-eureka | smartportx-eureka | 8761 | — | SCAFFOLDED | — | — | — | — | — | — | E-021 |
| smartportx-dashboard | smartportx-dashboard | 3000 | — | TESTED | TESTED | — | — | — | — | — | E-021 |

## 2. INFRASTRUCTURE

| Item | Spec source | Status | Evidence |
|------|-------------|--------|----------|
| Local Postgres 17.11 + 7 schemas (init script) | DEPLOYMENT-ARCHITECTURE §3.3, DATA-ARCHITECTURE §2 | VERIFIED (idempotent, schemas live on local PG) | E-022 |
| RabbitMQ 3 topology (6 exchanges, 24 queues, DLQ/retry) | COMMUNICATION-ARCHITECTURE §3.2–3.4 | IMPLEMENTED (script syntax-checked; live verify PENDING — R-007) | E-022 |
| docker-compose.yml | DEPLOYMENT-ARCHITECTURE §3.1 (D-424) | VERIFIED (config parse exit 0) | E-022 |
| .env.example (no secrets) | DEPLOYMENT-ARCHITECTURE §5.2 | VERIFIED | E-022 |
| Prometheus / Grafana / Jaeger | OBSERVABILITY-ARCHITECTURE | SCAFFOLDED (configs written; runs on compose) | E-022 |
| Flyway migrations (per service V1) | DATA-ARCHITECTURE all schemas | SCAFFOLDED (V1 skeletons; real DDL per service milestone) | E-022 |
| GitHub Actions CI | DEPLOYMENT-ARCHITECTURE §6 | UPDATED (backend job provisions postgres:17 service + init-databases.sh so integration tests can run; docker/scan stages `if: false` until M12; green GitHub run still PENDING — not executable locally) | E-021/E-025 |
| Seed data generator (D-010) | INTEGRATION-ARCHITECTURE §7, D-010 | NOT STARTED | — |

## 3. FUNCTIONAL REQUIREMENTS TRACE (46 FR — slice mapping)

> Requirement text lives in REQUIREMENTS.md. Full standing table maintained E2E in
> REQUIREMENT-ARCHITECTURE-TRACEABILITY.md and finalized in PHASE-4-REPORT.md §24. Here we keep
> the roll-up by milestone group; FR rows get their final state (VERIFIED + evidence ID) at M12.7.

| FR group (REQUIREMENTS.md) | Milestone | Status | Evidence |
|----------------------------|-----------|--------|----------|
| FR-001 authentication/registration | M3 | VERIFIED | E-024/E-025 |
| FR-002 user profiles/roles | M3 | VERIFIED | E-024/E-025 |
| FR-003 container registry + tracking | M4 | VERIFIED | E-026 |
| FR-004 container state machine | M4 | VERIFIED | E-026 |
| FR-005 appointment booking + quotas | M6 | VERIFIED | E-028 |
| FR-006 real-time role-based dashboards | M9+M11 | NOT STARTED | — |
| FR-007 gate check-in/check-out | M6 | VERIFIED | E-028 |
| FR-001 Container Yard Management (FR-001.1–8) | M5 | VERIFIED | E-027 |
| FR-002 AI-Driven Stacking Optimization (FR-002.1–6) | M5 | VERIFIED | E-027 |
| FR-008 Equipment Management (FR-008.1–4) | M5 | VERIFIED | E-027 |
| FR-010 vessel schedules + discharge | M7 | VERIFIED | E-029 |
| FR-011 EDIFACT inbound | M8 | VERIFIED | E-030 |
| FR-012 IoT sim | M8 | VERIFIED | E-030 |
| FR-013 customs sim + gate-out check | M8 | VERIFIED | E-030 |
| FR-014 notifications/alerts | M9 | VERIFIED | E-031 |
| FR-015 admin user management | M3+M11 | PARTIAL (M3 API VERIFIED; admin dashboard part in M11) | E-024/E-025 |
| FR-016..FR-046 remaining (see REQUIREMENTS.md) | per milestone | NOT STARTED | — |

> **Numbering note (F-419):** pre-M5 rows (FR-001…FR-007, FR-010+) use the internal milestone-slice numbering
> (identity = FR-001/FR-002, container = FR-003/FR-004, etc.). From M5 the rows use the canonical
> REQUIREMENTS.md IDs (FR-001 Yard, FR-002 Optimization, FR-008 Equipment). Duplicate FR numbers across M3/M4/M5
> rows are therefore different requirement sets until the full reconciliation at M12.7.

## 4. NON-FUNCTIONAL REQUIREMENTS (16 groups)
Tracked in: NFR → milestone | status | evidence | measurement source. Fully populated at M12.

| NFR group | Milestone | Status | Evidence |
|-----------|-----------|--------|----------|
| NFR-1 Performance/latency | M12.5 | NOT STARTED | — |
| NFR-2 Concurrency | M12.3 | NOT STARTED | — |
| NFR-3 Availability/resilience | M9/M12.2 | NOT STARTED | — |
| NFR-4 Security | every slice | NOT STARTED | — |
| NFR-5 Observability/monitoring | M2/M9/M12 | NOT STARTED | — |
| NFR-6..16 (see NON-FUNCTIONAL-REQUIREMENTS.md) | per group | NOT STARTED | — |

## 5. SECURITY REQUIREMENTS (36 SR)
Security status rolled per service `XSecurityTest` (TEST-STRATEGY §7). Full map in SECURITY-TEST-REPORT.md.

## 6. MILESTONE CLOSE-OUT LOG
| Milestone | Date | Outcome (real counts) | Evidence | Signed-off (Arch/QA/Sec/Critic) |
|-----------|------|-----------------------|----------|----------------------------------|
| M0 Repository Foundation | 2026-08-27 | Parent POM + 10 scaffolds + dashboard + CI; backend 11/11 BUILD SUCCESS; dashboard lint/test/build green | E-021 | — (M0.6 git commit pending user auth) |
| M1 Infrastructure | 2026-08-27 | init-databases idempotent (7 schemas live, E-022); compose parse-clean; rabbit-topology 6 exc/24 queues (live verify at M4.7); .env.example; Flyway skeletons; monitoring configs | E-022 | — |
| M2 common | 2026-08-27 | RFC 9457 error model + no-leak 500, correlation filter, pagination/sort, DomainEvent, metrics/health/tracing, auto-config starter (17 tests + identity context smoke green) | E-023 | — |
| M3 identity | 2026-08-30 | Auth + profiles + admin mgmt slice live: JWT + refresh rotation (ADR-009), 5-role RBAC (D-429/D-430), password history, cursor paging, rate limit; 15 core + 8 contract-conformance tests = 23/23 (identity) + 17/17 (common), BUILD SUCCESS | E-024/E-025 | — |
| M4 container | 2026-08-30 | Registry + lifecycle slice live: CRUD (ISO 6346 + DL check digit), 15-edge state machine (D-434), movements + status history, event log REGISTERED/STATUS_CHANGE/MOVEMENT/REMOVED + Rabbit/Log transports (D-438), RBAC writes, cursor paging; 14 unit + 36 integration = 50/50; reactor 11/11 BUILD SUCCESS; SC-11/12/13 closed; API-CONTRACTS §5 + DATA-ARCHITECTURE §7.1 doc-fix sweep applied | E-026 | — |
| M5 yard | 2026-08-31 | Yard slice live: layout/bays/blocks/tier update + container assign/remove with constraint checks (FR-001), explainable optimizer (4 strategies, dry-run, history, executed-move counters) (FR-002), equipment registry + status + OUT_OF_SERVICE 409 + moves metric (FR-008); domain events POSITION_ASSIGNED/FREED/OPTIMIZATION_RUN/EQUIPMENT_STATUS_CHANGED (Log+Rabbit transports, typed payloads); RBAC role guards; 37/37 integration tests, verify BUILD SUCCESS; F-419 applied, F-420/F-421 recorded, D-445; API-CONTRACTS §4.4 + DATA-ARCHITECTURE §6.7 + §6.1/§6.6 comment sweep applied | E-027 | — |
| M6 gate | 2026-08-31 | Gate slice live: appointment booking + quota windows + dynamic adjustment (FR-005), check-in/check-out with document/seal/weight rules + EIR (FR-007), live queue + estimate (FR-003.6), dual-transaction (FR-003.7), typed `gate.*` domain events (Log+Rabbit transports), fail-closed provider tunnels (container/yard/customs, JDK-HttpServer contract suite), RBAC role guards; 17+25+7 integration + 14 provider-contract tests = 63/63 (+17/17 common), reactor BUILD SUCCESS; D-446 recorded, F-422 recorded non-blocking, F-419 note applies; API-CONTRACTS §6 + DATA-ARCHITECTURE §8.1-8.4 superset DDL applied | E-028 | — |
| M7 vessel | 2026-08-31 | Vessel slice live: vessel registry CRUD with duplicate-IMO 409 (FR-004.1), schedule lifecycle SCHEDULED→IN_TRANSIT→ARRIVED→WORKING→DEPARTED/CANCELLED with state guard + ARRIVED/DEPARTED events (FR-004.2), discharge plan create with fail-closed container verification + progress PLANNED→DISCHARGED→IN_TRANSIT→{STACKED|MISROUTED} + plan COMPLETED when all off-vessel (FR-004.3, internal FR-010); typed `vessel.*` domain events (Log+Rabbit transports); advisory best-effort container/yard clients + fail-closed planning verification (D-448, JDK-HttpServer contract suite); superset DDL + DOUBLE PRECISION numeric columns (D-447/F-423); RBAC role guards; 7+3+12 integration/contract tests = 22/22 (+17/17 common), reactor BUILD SUCCESS; E-029 recorded | E-029 | — |
| M8 integration | 2026-08-31 | Integration slice live: EDIFACT COPRAR/COPARN inbound parse + idempotency + envelope validation (FR-011), IoT sim + threshold alerts (FR-012), customs PENDING→UNDER_REVIEW→CLEARED/HELD→RELEASED sim + gate-out validation (FR-013); typed `integration.*` events (Log+Rabbit transports); retry/DLQ + audit log; API-CONTRACTS §8 canonical superset surface (D-449, 201 on parse); hand-rolled EDIFACT parser — offline StAEDI substitution (D-450/F-427); RBAC write=ADMIN|TERMINAL_OP|integration:write, reads=authenticated; RFC 9457 error envelope; 7 unit + 22 integration + 2 provider-contract = 31/31 (+17/17 common), reactor BUILD SUCCESS; D-449/D-450 recorded, F-425/F-426/F-427 recorded | E-030 | — |
| M9 notification | 2026-09-13 | Notification slice live: STOMP WebSocket push + JWT channel auth (FR-014), topic authorization (role × topic patterns, fail-closed), RabbitMQ→STOMP bridge, alert engine (dedupeKey+code, code→severity/category mapping), preferences REST (merge-patch PUT / GET), quiet hours, digest frequency; `NotificationPayload` record wire format; 11+13+7+17+9 = 57/57 tests + 17/17 common, reactor BUILD SUCCESS | E-031 | — |
| M10 discovery+gateway | 2026-09-14 | Eureka discovery live: dashboard basic-auth, `/eureka/**` + `/actuator/health|info` permitAll, env creds (`SMARTPORTX_EUREKA_SECURITY_*`, dev admin/admin), module Dockerfile; gateway edge live: 10 routes (application.yml source of truth), edge JWT auth + whitelist, trusted-header injection (X-User-Id/X-User-Role spoof-stripped), correlation echo, per-route rate-limit 429 + Retry-After, per-route CircuitBreaker 503/504 RFC 9457, `/ws/**` exempt; docs §7.1-7.3 + DEPLOYMENT env reconciled; 5/5 eureka + 14/14 gateway (+17/17 common), reactor 11/11 BUILD SUCCESS | E-032 | — |
| M11 dashboard | — | — | — | — |
| M12 hardening+reports | — | — | — | — |
| M13 final gate | — | — | — | — |

## 7. OPEN BLOCKERS (inflas from RISKS.md)
- R-007 Docker/WSL2 — needed for containerized run; native Postgres is the live dev DB; RabbitMQ fallback: `choco install rabbitmq` or WSL2 enable. Applies to event-integration milestones (M1.3 → M4.7+). EDIFACT StAEDI + Resilience4j offline substitutes (D-450/D-448) revisit with R-007.
- F-407 Notification schema §11 unresolved — confirm DATA-ARCHITECTURE §11 before M9.1.

## 8. HISTORICAL STATE (pre-Phase 4, from Phase 5 existence audit)
2026-08-27: 0/8 services, 0/7 schemas, 0 infrastructure implemented; score 3/100; NOT READY.
This tracker begins Phase 4 from that baseline — every service now slated for real implementation.