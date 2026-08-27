# SMARTPORTX — ARCHITECTURE

## STATUS: DESIGNED

Architecture design is complete (Phase 3). Score: 92/100.
Full details in `.smartport/ARCHITECTURE-OVERVIEW.md` and `.smartport/ADR/ADR-001.md`–`ADR-013.md`.

## Summary

**Architecture Style:** Service-Based Architecture (SBA) — 8 independently deployable services (D-015).

**Services:**
| # | Service | Port | Responsibility |
|---|---|---|---|
| 1 | Gateway | 8080 | Routing, JWT validation, rate limiting, CORS, WebSocket proxy |
| 2 | Identity | 8081 | Login, refresh tokens, RBAC, session management |
| 3 | Yard | 8082 | Stacking optimization, block/cell management, analytics |
| 4 | Gate | 8083 | Truck processing, check-in/out, appointments |
| 5 | Container | 8084 | Container registry, tracking, customs status |
| 6 | Vessel | 8085 | Vessel calls, discharge/loading plans |
| 7 | Integration | 8086 | EDIFACT (COPRAR/COARRI/CODECO/COPARN), IoT simulation, customs adapters |
| 8 | Notification | 8087 | Real-time alerts (WebSocket/STOMP), email, SMS |

**Technology Stack (D-016):** Java 25 Temurin LTS · Spring Boot 3.x · PostgreSQL 17.11 (schema-per-service, 7 schemas / 33 tables) · RabbitMQ 3.13 (async events) · Spring Cloud Gateway · Netflix Eureka · Flyway 10.x · React 18+ TypeScript · Docker Compose v2.

**Communication (D-017):** REST (sync, 5s timeout) for critical transactional paths · RabbitMQ with CloudEvents envelope (async domain events) · WebSocket/STOMP (real-time dashboards). Hybrid pattern chosen to address eventual-consistency (F-003).

**Security (D-021):** JWT RS256, 15-min access / 7-day rotating refresh tokens · RBAC 5 roles (ADMIN, TERMINAL_OP, YARD_MANAGER, GATE_OPERATOR, READONLY) · TLS 1.3 · rate limit 100 req/min · RFC 9457 error format · append-only audit.

**Resilience:** Resilience4j circuit breaker / retry / timeout / bulkhead · idempotency keys on POST/PUT · Dead Letter Queues · graceful degradation (read-only mode).

**Observability (D-024/D-025):** Micrometer + Prometheus + Grafana (6 business metrics, 8 alert rules, 9 dashboards) · OpenTelemetry + Jaeger (distributed traces, W3C propagation) · structured logging with correlation IDs.

**Deployment (D-022):** Docker Compose (15 containers) + GitHub Actions CI/CD · Flyway migrations on boot · Docker daemon requires WSL2 (F-007).

## Key Decisions
D-015 Architecture Style (SBA) · D-016 Tech Stack · D-017 Broker (RabbitMQ) · D-018 8 Services · D-019 Schema-per-Service · D-020 Gateway · D-021 JWT/RBAC · D-022 Docker Compose · D-023 Flyway · D-024 Monitoring · D-025 Tracing · D-026 React/TS · D-027 Testing — full records in `.smartport/DECISIONS.md` and `.smartport/PHASE-3-DECISIONS.md`.

## Requirement Coverage
100% of 38 FR, 24 NFR, and 22 security requirements trace to architecture components (`.smartport/REQUIREMENT-ARCHITECTURE-TRACEABILITY.md`). One deferred gap: FR-003.9 export cut-off notification (F-011).

## Architecture Artifacts (`.smartport/`)
ARCHITECTURE-OVERVIEW.md · SERVICE-CATALOG.md · SERVICE-BOUNDARIES.md · API-CONTRACTS.md · DATA-ARCHITECTURE.md · COMMUNICATION-ARCHITECTURE.md · SECURITY-ARCHITECTURE.md · RESILIENCE-ARCHITECTURE.md · OBSERVABILITY-ARCHITECTURE.md · INTEGRATION-ARCHITECTURE.md · DEPLOYMENT-ARCHITECTURE.md · ARCHITECTURE-TRADEOFFS.md · REQUIREMENT-ARCHITECTURE-TRACEABILITY.md · ADR/ · PHASE-3-FINDINGS.md · PHASE-3-DECISIONS.md · PHASE-3-REPORT.md

## Next Phase
Phase 4 — Implementation Planning (scaffold plan for all 8 services, DB DDL, API stubs, CI/CD pipeline).