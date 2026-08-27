# SMARTPORTX — IMPLEMENTATION STATUS

> Living tracker. Updated at every milestone close (plan §10). Status vocabulary (brief §42):
> NOT STARTED / SCAFFOLDED / IN PROGRESS / IMPLEMENTED / TESTED / VERIFIED / BLOCKED.
> Evidence IDs refer to EVIDENCE.md. Requirements reference the canonical counts:
> 46 FR (REQUIREMENTS.md) / 16 NFR groups (NON-FUNCTIONAL-REQUIREMENTS.md) / 36 SR (SECURITY-REQUIREMENTS.md).
> Last updated: 2026-08-27 (M0 verified; M1 in progress).

--------------------------------------------------------------------------------
## 1. SERVICE STATUS

| Service | Dir | Port | DB schema | Impl | Unit | Integ | API/Contract | Sec | E2E | Critic | Evidence |
|---------|-----|------|-----------|------|------|-------|--------------|-----|-----|--------|----------|
| smartportx-common | smartportx-common | — | — | TESTED | TESTED | — | — | — | — | — | E-023 |
| smartportx-identity | smartportx-identity | 8081 | smartportx_identity | SCAFFOLDED | — | — | — | — | — | — | E-021 |
| smartportx-container | smartportx-container | 8084 | smartportx_container | SCAFFOLDED | — | — | — | — | — | — | E-021 |
| smartportx-yard | smartportx-yard | 8082 | smartportx_yard | SCAFFOLDED | — | — | — | — | — | — | E-021 |
| smartportx-gate | smartportx-gate | 8083 | smartportx_gate | SCAFFOLDED | — | — | — | — | — | — | E-021 |
| smartportx-vessel | smartportx-vessel | 8085 | smartportx_vessel | SCAFFOLDED | — | — | — | — | — | — | E-021 |
| smartportx-integration | smartportx-integration | 8086 | smartportx_integration | SCAFFOLDED | — | — | — | — | — | — | E-021 |
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
| GitHub Actions CI | DEPLOYMENT-ARCHITECTURE §6 | SCAFFOLDED (ci.yml; docker/scan stages `if: false` until M12) | E-021 |
| Seed data generator (D-010) | INTEGRATION-ARCHITECTURE §7, D-010 | NOT STARTED | — |

## 3. FUNCTIONAL REQUIREMENTS TRACE (46 FR — slice mapping)

> Requirement text lives in REQUIREMENTS.md. Full standing table maintained E2E in
> REQUIREMENT-ARCHITECTURE-TRACEABILITY.md and finalized in PHASE-4-REPORT.md §24. Here we keep
> the roll-up by milestone group; FR rows get their final state (VERIFIED + evidence ID) at M12.7.

| FR group (REQUIREMENTS.md) | Milestone | Status | Evidence |
|----------------------------|-----------|--------|----------|
| FR-001 authentication/registration | M3 | NOT STARTED | — |
| FR-002 user profiles/roles | M3 | NOT STARTED | — |
| FR-003 container registry + tracking | M4 | NOT STARTED | — |
| FR-004 container state machine | M4 | NOT STARTED | — |
| FR-005 appointment booking + quotas | M6 | NOT STARTED | — |
| FR-006 real-time role-based dashboards | M9+M11 | NOT STARTED | — |
| FR-007 gate check-in/check-out | M6 | NOT STARTED | — |
| FR-008 yard slots + movements | M5 | NOT STARTED | — |
| FR-009 optimization w/ explanation | M5 | NOT STARTED | — |
| FR-010 vessel schedules + discharge | M7 | NOT STARTED | — |
| FR-011 EDIFACT inbound | M8 | NOT STARTED | — |
| FR-012 IoT sim | M8 | NOT STARTED | — |
| FR-013 customs sim + gate-out check | M8 | NOT STARTED | — |
| FR-014 notifications/alerts | M9 | NOT STARTED | — |
| FR-015 admin user management | M3+M11 | NOT STARTED | — |
| FR-016..FR-046 remaining (see REQUIREMENTS.md) | per milestone | NOT STARTED | — |

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
| M3 identity | — | — | — | — |
| M4 container | — | — | — | — |
| M5 yard | — | — | — | — |
| M6 gate | — | — | — | — |
| M7 vessel | — | — | — | — |
| M8 integration | — | — | — | — |
| M9 notification | — | — | — | — |
| M10 discovery+gateway | — | — | — | — |
| M11 dashboard | — | — | — | — |
| M12 hardening+reports | — | — | — | — |
| M13 final gate | — | — | — | — |

## 7. OPEN BLOCKERS (inflas from RISKS.md)
- R-007 Docker/WSL2 — needed for containerized run; native Postgres is the live dev DB; RabbitMQ fallback: `choco install rabbitmq` or WSL2 enable. Applies to event-integration milestones (M1.3 → M4.7+).
- F-407 Notification schema §11 unresolved — confirm DATA-ARCHITECTURE §11 before M9.1.

## 8. HISTORICAL STATE (pre-Phase 4, from Phase 5 existence audit)
2026-08-27: 0/8 services, 0/7 schemas, 0 infrastructure implemented; score 3/100; NOT READY.
This tracker begins Phase 4 from that baseline — every service now slated for real implementation.