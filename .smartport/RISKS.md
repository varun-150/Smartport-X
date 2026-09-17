# SMARTPORTX — RISKS

## R-001: Empty Project
- **Status:** MITIGATED
- **Severity:** HIGH → LOW
- **Description:** Project was starting from absolute zero.
- **Mitigation:** Phase 0 foundation complete. Phase 2 will define requirements.

## R-002: Technology Stack Undefined
- **Status:** RESOLVED
- **Severity:** ~~HIGH~~ → NONE
- **Description:** ~~No technology decisions have been made.~~
- **Mitigation:** D-016 (Phase 3): Java 25 Temurin LTS + Spring Boot 3.x + PostgreSQL 17.11 + RabbitMQ + Spring Cloud Gateway + Eureka + React 18/TS + Flyway + Docker Compose. ADR-001..013 ratified as PROPOSED.

## R-003: Scope Unknown
- **Status:** RESOLVED
- **Severity:** ~~HIGH~~ → NONE
- **Description:** ~~"Port logistics and container yard orchestration" is broad.~~
- **Mitigation:** Phase 2 MVP scope (D-005, SCOPE.md); 46 FR + 16 NFR groups + 36 SR baseline.

## R-004: Skill Discrepancy
- **Status:** RESOLVED (documented)
- **Severity:** ~~LOW~~ → NONE
- **Description:** 77 skill folders on disk vs 65 in registry. 12 unregistered skills are valid and loadable via system prompt.
- **Mitigation:** All 12 unregistered skills identified, documented in Phase 1.5 findings. They are Spring Boot/Java ecosystem skills, loadable but not tracked in registry. Non-blocking.

## R-005: Limited Build Tools
- **Status:** RESOLVED
- **Severity:** ~~MEDIUM~~ → NONE
- **Description:** ~~No Maven or Gradle in PATH.~~
- **Mitigation:** Maven 3.9.16 installed at D:\apache-maven-3.9.16.

## R-006: Java Version Concern
- **Status:** RESOLVED
- **Severity:** ~~MEDIUM~~ → NONE
- **Description:** ~~Java 8 was installed. Modern Spring Boot (3.x) requires Java 17+.~~
- **Mitigation:** Java 25.0.3 (Temurin LTS) available via STS bundled JRE. JAVA_HOME configured.

## R-007: Docker Backend Missing
- **Status:** OPEN (Phase 4 active dependency)
- **Severity:** MEDIUM (HIGH for runnable demo)
- **Description:** Docker 29.7.2 installed but WSL2 not enabled. Docker daemon cannot start.
- **Mitigation:** (1) `wsl --install` as Administrator + restart (preferred); OR (2) `choco install rabbitmq`
  + `rabbitmq-plugins enable rabbitmq_management` for the broker. Local Postgres 17.11 is the active dev DB.
  Blocking events/broker-dependent milestones (M4.7+) and docker compose (M1.2); native fallback in scripts/dev-up.ps1.

## R-008: No Implementation Exists (Phase 5 audit discovery)
- **Status:** OPEN → IN PROGRESS (Phase 4 kickoff unblocked this)
- **Severity:** CRITICAL (baseline) → iteratively reduced
- **Description:** Repository was documentation-only. 0/8 services, 0/7 schemas implemented (F5-001..F5-004).
- **Mitigation:** Phase 4 implementation now ACTIVE per PHASE-4-IMPLEMENTATION-PLAN.md (M0–M13); progress
  tracked in IMPLEMENTATION-STATUS.md; evidence IDs appended to EVIDENCE.md as code lands; NOT resolved until
  M13 gate.

## R-009: RabbitMQ Broker Availability (new, Phase 4)
- **Status:** OPEN
- **Severity:** MEDIUM
- **Description:** No confirmed local broker; event topology, retry/DLQ, and WS fan-out depend on one.
- **Mitigation:** R-007 mitigation path (WSL2 docker compose OR choco install); topology script
  `scripts/rabbit-topology.sh` is idempotent so re-runs are safe.

## R-010: Scope Guard (new, Phase 4)
- **Status:** ACTIVE (process)
- **Severity:** LOW
- **Description:** 8 services + 33 tables + full E2E is large for one phase; risk of throughput drift or
  overbuild beyond approved MVP.
- **Mitigation:** vertical-slice milestone order (dependency-depth), out-of-scope list hard-sided
  (PHASE-4-IMPLEMENTATION-PLAN §1), architect/critic veto on any FR-adjacent gold-plating, honest
  BLOCKED/IN PROGRESS statuses, stop-at-M13.

## R-011: Ephemeral JWT signing key (new, M4 validation — F-431)
- **Status:** OPEN (accepted for demo)
- **Severity:** MEDIUM (production), LOW (demo)
- **Description:** Identity signs JWTs with an in-memory generated RSA keypair (`KeyRepository`); no key
  is persisted. Any restart invalidates all previously issued tokens and keys cannot be shared across
  replicas. Compose sets `JWT_SECRET` for 6 services but it is read nowhere (dead config, F-431).
- **Mitigation:** For the demo, accepted and documented. For production: persist/load a keypair from a
  keystore or secret manager (or move to a managed OIDC provider) and remove the dead `JWT_SECRET` env.

## R-012: M1/M4.7 broker-dependent verification still unproven (new, M4 validation)
- **Status:** OPEN
- **Severity:** MEDIUM
- **Description:** RabbitMQ is not running and Docker is off, so event topology, retry/DLQ and WS fan-out
  could not be exercised; M1's broker leg and M4.7 evidence remain PENDING. With the broker down,
  `/actuator/health` on the event-publishing services reports DOWN (503) even when events are disabled
  (F-432), so a broker-less run looks unhealthy.
- **Mitigation:** Install a broker (R-007/R-009 paths) and run `scripts/rabbit-topology.sh`; for
  broker-less dev, consider `management.health.rabbit.enabled: false`. Re-run broker-dependent evidence
  before the M13 gate.
