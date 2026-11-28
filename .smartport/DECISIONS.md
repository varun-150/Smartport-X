# SMARTPORTX — ARCHITECTURE DECISIONS

## Format
Each decision follows ADR format when significant.

---

## D-001: Project Initialization Approach
- **Date:** 2026-08-26
- **Status:** ACCEPTED
- **Context:** Starting from empty directory
- **Decision:** Initialize with .smartport/ documentation structure before any code
- **Rationale:** Project memory must exist before engineering work begins
- **Consequences:** Extra upfront work, but enables evidence-based tracking

## D-002: Engineering Protocol
- **Date:** 2026-08-26
- **Status:** ACCEPTED
- **Context:** Need to establish quality standards
- **Decision:** Follow Stage-3 Engineering Protocol (Research → Evidence)
- **Rationale:** Prevents premature implementation and ensures verification
- **Consequences:** Slower initial progress, higher quality output

## D-003: Git Repository
- **Date:** 2026-08-26
- **Status:** ACCEPTED
- **Context:** No version control exists
- **Decision:** Initialize Git at project root
- **Rationale:** Version control is prerequisite for any engineering work
- **Consequences:** Enables tracking, branching, collaboration

---

## D-005: SmartPortX Scope — Complementary Intelligence Layer
- **Date:** 2026-08-26
- **Status:** DECIDED
- **Decision:** SmartPortX is an intelligent yard orchestration and gate coordination platform — not a full TOS replacement.
- **Rationale:** Enterprise TOS costs $1M+ and requires 12-24 months. SmartPortX targets mid-size terminal needs.
- **Consequences:** Clear scope boundaries; prevents scope creep.

## D-006: Optimization Approach — Simplified Heuristics First
- **Date:** 2026-08-26
- **Status:** DECIDED
- **Decision:** Stacking optimization uses simplified heuristics initially; upgrade to advanced algorithms later.
- **Rationale:** Full NP-hard optimization is out of student timeline. Heuristics provide 23-78% improvement.
- **Consequences:** First version uses rule-based stacking; advanced optimization deferred.

## D-007: IoT Integration — Simulated Layer
- **Date:** 2026-08-26
- **Status:** DECIDED
- **Decision:** Simulated IoT data layer for dev/demo; architecture ready for physical sensors.
- **Rationale:** Physical IoT requires hardware beyond student project scope.
- **Consequences:** Simulation mode default; live mode for future deployment.

## D-008: EDIFACT Support — Parsing Only
- **Date:** 2026-08-26
- **Status:** DECIDED
- **Decision:** Parse inbound EDIFACT messages (COPRAR, COARRI, CODECO, COPARN); no full compliance.
- **Rationale:** Full EDIFACT compliance requires certification and country-specific MIGs.
- **Consequences:** EDIFACT adapters convert to internal model; outbound uses REST.

## D-009: Frontend — Web Dashboard Only
- **Date:** 2026-08-26
- **Status:** DECIDED
- **Decision:** Web-based dashboard; no mobile application in MVP.
- **Rationale:** Mobile requires separate effort and app store deployment.
- **Consequences:** All interaction via browser; responsive for tablet use.

## D-010: Data Strategy — Realistic Test Data Generator
- **Date:** 2026-08-26
- **Status:** DECIDED
- **Decision:** Comprehensive data simulation module for development, testing, and demo.
- **Rationale:** No physical port access; realistic data essential for value demonstration.
- **Consequences:** Generator produces containers, trucks, vessels, equipment, customs, IoT events.

## D-011: Architecture Pattern — Event-Driven Microservices
- **Date:** 2026-08-26
- **Status:** DECIDED
- **Decision:** Event-driven microservice architecture with Spring Boot and async events.
- **Rationale:** Port operations are inherently event-driven; microservices reflect domain boundaries.
- **Consequences:** Services communicate via events; REST for external clients.

## D-012: Database — PostgreSQL Schema-per-Service
- **Date:** 2026-08-26
- **Status:** DECIDED
- **Decision:** PostgreSQL with logical schema separation per service.
- **Rationale:** Available and verified; schema-per-service provides isolation without multiple instances.
- **Consequences:** Each service owns its schema; cross-service via events/API only.

## D-013: Security — RBAC with 5 Roles
- **Date:** 2026-08-26
- **Status:** DECIDED
- **Decision:** Role-based access control: Admin, Terminal Operator, Yard Manager, Gate Operator, Read-Only.
- **Rationale:** Maps to stakeholder roles; appropriate access boundaries.
- **Consequences:** All endpoints protected; UI renders per role.

## D-014: Testing — TDD with Integration Tests
- **Date:** 2026-08-26
- **Status:** DECIDED
- **Decision:** TDD for business logic; comprehensive integration tests for API and workflows.
- **Rationale:** Quality gates require evidence-based completion.
- **Consequences:** Unit tests for algorithms; integration tests for APIs; performance tests for NFRs.

## D-004: Phase 1.5 Test Decision
- **Date:** 2026-08-26
- **Status:** ACCEPTED (TEST ONLY)
- **Context:** Phase 1.5 validation testing — project memory persistence test
- **Decision:** The project will require evidence before any completion claim.
- **Rationale:** Testing that project memory persists across reasoning cycles
- **Consequences:** None (test decision, will be archived)

---

## D-015: Architecture Style — Service-Based Architecture (SBA)
- **Date:** 2026-08-27
- **Status:** DECIDED
- **Decision:** 8 independently deployable services; satisfies SOA principles manageable for student team.
- **Rationale:** SBA balances SOA demonstration with single-terminal scope; microservices too heavy, monolith fails mandate.
- **Consequences:** Services own schemas/APIs; can run separately or together.

## D-016: Technology Stack — Java 25 + Spring Boot 3.x + PostgreSQL 17
- **Date:** 2026-08-27
- **Status:** DECIDED
- **Decision:** Java 25 Temurin LTS, Spring Boot 3.x, PostgreSQL 17.11 for all backend services.
- **Rationale:** All verified installed; largest ecosystem; 15+ skills target this stack.
- **Consequences:** Consistent stack, shared abstractions, one upgrade story.

## D-017: Message Broker — RabbitMQ
- **Date:** 2026-08-27
- **Status:** DECIDED
- **Decision:** RabbitMQ for async event processing (topic exchanges + dead-letter queues).
- **Rationale:** Lightweight, simple, throughput far exceeds NFR (F-005).
- **Consequences:** Domain events via RabbitMQ; DLQs for reliability; no Kafka complexity.

## D-018: Service Decomposition — 8 Services
- **Date:** 2026-08-27
- **Status:** DECIDED
- **Decision:** Gateway, Identity, Yard, Gate, Container, Vessel, Integration, Notification.
- **Rationale:** Each maps to a domain capability; 100% requirement traceability; acyclic dependency graph.
- **Consequences:** Independent development; 7 schemas, 33 tables.

## D-019: Data Ownership — Schema-per-Service
- **Date:** 2026-08-27
- **Status:** DECIDED
- **Decision:** Each service owns its PostgreSQL schema; no shared tables.
- **Rationale:** Independent migration/evolution; prevents coupling.
- **Consequences:** Cross-service access via events/API only; per-service DB credentials.

## D-020: API Gateway — Spring Cloud Gateway
- **Date:** 2026-08-27
- **Status:** DECIDED
- **Decision:** Reactive gateway for routing, JWT validation, rate limiting, CORS, WebSocket proxy.
- **Rationale:** Spring ecosystem integration; Eureka discovery; single entry point.
- **Consequences:** Services trust gateway-validated user context headers.

## D-021: Authentication — JWT RS256 with Refresh Token Rotation
- **Date:** 2026-08-27
- **Status:** DECIDED
- **Decision:** 15-minute access tokens; 7-day rotating refresh tokens.
- **Rationale:** Stateless, scalable, gateway-level validation (SR-001.2).
- **Consequences:** X-User-Id/X-User-Role headers enable @PreAuthorize enforcement.

## D-022: Deployment — Docker Compose
- **Date:** 2026-08-27
- **Status:** DECIDED
- **Decision:** Docker Compose for local dev and demo (15 containers).
- **Rationale:** Simple, environment parity, no Kubernetes complexity.
- **Consequences:** Blocked until WSL2 installed (F-007). Fallback: run on host.

## D-023: Database Migration — Flyway
- **Date:** 2026-08-27
- **Status:** DECIDED
- **Decision:** Flyway SQL-based versioned migrations per service schema.
- **Rationale:** Simple, native Spring Boot integration, run on startup.
- **Consequences:** `V{n}__*.sql` per service; automatic migration on boot.

## D-024: Monitoring — Micrometer + Prometheus + Grafana
- **Date:** 2026-08-27
- **Status:** DECIDED
- **Decision:** Metrics via Micrometer/Actuator; Prometheus scrape; Grafana dashboards/alerting.
- **Rationale:** Free, open-source, native integration (OPS-001).
- **Consequences:** 6 custom business metrics; 8 alert rules; 9 dashboard views.

## D-025: Tracing — OpenTelemetry + Jaeger
- **Date:** 2026-08-27
- **Status:** DECIDED
- **Decision:** OpenTelemetry instrumentation; Jaeger collection/UI.
- **Rationale:** Vendor-neutral; W3C propagation across sync+async (SR-004.3).
- **Consequences:** Correlation IDs everywhere; 100% error sampling, 10% normal.

## D-026: Frontend — React 18+ with TypeScript
- **Date:** 2026-08-27
- **Status:** DECIDED
- **Decision:** React SPA (served via Nginx) for the web dashboard.
- **Rationale:** Largest ecosystem; component-based; WebSocket support (FR-006.2–4).
- **Consequences:** Ratify from PROPOSED in Phase 4 (F-008).

## D-027: Testing Strategy — TDD + Integration Tests
- **Date:** 2026-08-27
- **Status:** DECIDED
- **Decision:** TDD for business logic; integration tests for API/workflows; CI/CD runs all tests.
- **Rationale:** Evidence-based completion (MNT-001); quality gates enforced.
- **Consequences:** Unit + integration + ArchUnit boundary tests.

## D-028: Phase 5 Scope — Existence Audit (User-Directed)
- **Date:** 2026-08-27
- **Status:** ACCEPTED
- **Context:** Phase 5 brief presupposes an implemented system; repository is documentation-only (verified; 0 commits, no build files, Phase-4/test artifacts MISSING).
- **Decision:** Run Phase 5 as an existence audit only — record the absence as the primary finding; verdict NOT READY FOR PHASE 6. Do NOT fabricate attack/load/failure results.
- **Rationale:** Project protocol forbids fabricated evidence; documentation is not implementation evidence; hardening requires a real system.
- **Consequences:** Score 3/100; PHASE-5-PLAN/FINDINGS/REPORT created; full hardening brief deferred to 5.B after real implementation; Phase 6 will not auto-start.

---

## D-409 … D-423: Phase 4 Implementation Decisions
- **Date:** 2026-08-27
- **Status:** ACCEPTED (see full bodies in PHASE-4-DECISIONS.md)
- **Coverage:** Monorepo layout (D-409); RabbitMQ topology canonical from COMMUNICATION-ARCHITECTURE (D-410);
  container status enum from DATA-ARCHITECTURE §7.1 (D-411); canonical roles (D-412); StAEDI parser for EDIFACT
  inbound (D-413, resolves F-004); requirement counts 46/16/36 (D-414); phase realignment (D-415); test framework
  stack (D-416); concurrency strategy @Version+unique+idempotency (D-417); env separation (D-418); frontend auth
  model HttpOnly refresh cookie (D-419). Open: D-420..D-423 (notification schema, vessel schema, perf thresholds,
  ADR ratification sweep).
- **Evidence:** PHASE-4-DECISIONS.md, PHASE-4-FINDINGS.md, PHASE-4-IMPLEMENTATION-PLAN.md
