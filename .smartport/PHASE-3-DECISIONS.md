# PHASE 3 DECISIONS — SmartPortX

## D-015: Architecture Style — Service-Based Architecture (SBA)
**Date:** 2026-08-27
**Status:** DECIDED
**Decision:** SmartPortX uses Service-Based Architecture with 8 independently deployable services.
**Rationale:** SBA satisfies SOA principles (service contracts, loose coupling, autonomy) while remaining manageable for a 3–4 person student team and a single-terminal scope. Full microservices would impose Kubernetes-era operational overhead with no compensating benefit at this scale; a monolith would fail the project's mandate to demonstrate service-oriented design.
**Impact:** Each service owns its schema and API; services can run as separate processes (deployment) or together (development). No distributed-transaction framework is required — hybrid sync/async communication covers all workflows.

## D-016: Technology Stack — Java 25 + Spring Boot 3.x + PostgreSQL 17
**Date:** 2026-08-27
**Status:** DECIDED
**Decision:** Backend uses Java 25 Temurin LTS with Spring Boot 3.x and PostgreSQL 17.11.
**Rationale:** All three are installed and verified on the development machine; Spring Boot provides the largest ecosystem (Spring Security, Spring Data JPA, Spring Cloud, Spring Integration, Actuator); 15+ skills in the team library target this stack.
**Impact:** Every backend service uses the same stack and version family, giving consistency, shared abstractions, and one dependency-upgrade story.

## D-017: Message Broker — RabbitMQ
**Date:** 2026-08-27
**Status:** DECIDED
**Decision:** RabbitMQ provides asynchronous event processing between services.
**Rationale:** Lightweight and simple to operate; AMQP 0-9-1 supports topic exchanges for domain-event routing and dead-letter queues for reliability; throughput (20,000+ msgs/sec) far exceeds the ~1,000 events/sec NFR target (F-005).
**Impact:** Services publish domain events to RabbitMQ topic exchanges; consumers subscribe per event type; DLQs handle failed processing; no Kafka complexity is required at this scale.

## D-018: Service Decomposition — 8 Services
**Date:** 2026-08-27
**Status:** DECIDED
**Decision:** SmartPortX decomposes into 8 services: Gateway, Identity, Yard, Gate, Container, Vessel, Integration, Notification.
**Rationale:** Each service maps to a clear domain capability. All 38 FRs, 24 NFRs, and 22 security requirements trace to ≥1 service (F-001). The dependency graph is acyclic with minimal coupling (SERVICE-BOUNDARIES.md).
**Impact:** Each service has its own database schema, API contract, and deployment unit. Development can proceed in parallel on independent services.

## D-019: Data Ownership — Schema-per-Service
**Date:** 2026-08-27
**Status:** DECIDED
**Decision:** Each service owns its PostgreSQL schema. No shared tables, no cross-schema foreign keys.
**Rationale:** Schema isolation gives each service independent migration and evolution capability and prevents accidental coupling. Cross-service data access happens only via events or explicit API calls (F-002).
**Impact:** 7 schemas, 33 tables. Integration Service and Yard Service densest. Enforcement via per-service DB credentials + ArchUnit dependency tests.

## D-020: API Gateway — Spring Cloud Gateway
**Date:** 2026-08-27
**Status:** DECIDED
**Decision:** Spring Cloud Gateway routes external requests, validates JWTs, enforces rate limits.
**Rationale:** Reactive/non-blocking performance; native Spring ecosystem integration; works with Eureka discovery for dynamic routing. Single entry point for all external traffic.
**Impact:** Gateway owns cross-cutting concerns (auth, rate limit, CORS, WebSocket proxy). Services remain focused on business logic and trust gateway-validated user context headers.

## D-021: Authentication — JWT RS256 with Refresh Token Rotation
**Date:** 2026-08-27
**Status:** DECIDED
**Decision:** JWT access tokens (15-minute expiry) with rotating refresh tokens (7-day lifetime).
**Rationale:** Stateless and horizontally scalable; RS256 public-key verification allows the gateway to validate tokens without calling Identity on every request; industry standard (SR-001.2).
**Impact:** Gateway validates all requests; services receive user ID + role via X-User-Id/X-User-Role headers for @PreAuthorize enforcement. Token compromise detection via rotation on refresh.

## D-022: Deployment — Docker Compose
**Date:** 2026-08-27
**Status:** DECIDED
**Decision:** Docker Compose orchestrates all services + infrastructure for local development and demo deployment.
**Rationale:** Simple single-command startup, environment parity across machines, no Kubernetes complexity required for a single-terminal demo (ADR-011).
**Impact:** One `docker-compose.yml` runs 15 containers (8 services + Postgres, RabbitMQ, Eureka, React/Nginx, Prometheus, Grafana, Jaeger). Blocker: Docker daemon requires WSL2 (F-007, user action required).

## D-023: Database Migration — Flyway
**Date:** 2026-08-27
**Status:** DECIDED
**Decision:** Flyway manages schema versioning and migration for every service database.
**Rationale:** Simple SQL-based versioned migrations; native Spring Boot integration (runs on startup); keeps each service's schema versioned from day one (D-012 consistency).
**Impact:** Each service ships `src/main/resources/db/migration/V{n}__*.sql`. Migrations run automatically on boot against the service's own schema. Rollback via Flyway undo (community) or restore.

## D-024: Monitoring — Micrometer + Prometheus + Grafana
**Date:** 2026-08-27
**Status:** DECIDED
**Decision:** Micrometer exposes metrics; Prometheus scrapes; Grafana visualizes and alerts.
**Rationale:** Free, open-source, native Spring Boot integration (Actuator `/actuator/prometheus`), rich dashboard ecosystem (OPS-001 alignment).
**Impact:** All services expose the Prometheus endpoint; custom business metrics (yard utilization, moves, gate transactions, optimization duration, appointments, EDIFACT processed) feed operational dashboards and alert rules.

## D-025: Tracing — OpenTelemetry + Jaeger
**Date:** 2026-08-27
**Status:** DECIDED
**Decision:** OpenTelemetry provides distributed tracing; Jaeger collects and visualizes traces.
**Rationale:** Industry-standard, vendor-neutral instrumentation; W3C trace-context propagation gives end-to-end visibility across the async RabbitMQ hops (SR-004.3 correlation).
**Impact:** Every request and event carries a trace/correlation ID; Jaeger UI shows the full cross-service span tree. Sampling: 100% errors, 10% normal.

## D-026: Frontend — React 18+ with TypeScript
**Date:** 2026-08-27
**Status:** DECIDED
**Decision:** Web dashboard is a single-page application built with React 18+ and TypeScript.
**Rationale:** Largest ecosystem, component-based architecture, type safety, strong WebSocket support for real-time yard/gate/vessel views (FR-006.2–4).
**Impact:** React SPA served via Nginx in Docker, proxying API and WebSocket traffic through the Gateway. Ratify from PROPOSED in Phase 4 (F-008).

## D-027: Testing Strategy — TDD + Integration Tests
**Date:** 2026-08-27
**Status:** DECIDED
**Decision:** TDD for business logic; integration tests for API endpoints and workflows; CI/CD runs all tests on every push.
**Rationale:** Quality gates require evidence-based completion (MNT-001); TDD gives design feedback for algorithms; integration tests prove services cooperate correctly (D-014).
**Impact:** Unit tests cover algorithms and validators; integration tests cover API contracts and workflows (gate-in/out, discharge, customs block); ArchUnit tests enforce service-boundary rules.

---

*Decisions documented: 2026-08-27*
*Previous decisions: D-001 (Phase 0), D-002–D-003 (Phase 1), D-004 (Phase 1.5), D-005–D-014 (Phase 2)*
*Decision records: ADR-001 through ADR-013 in `.smartport/ADR/`*