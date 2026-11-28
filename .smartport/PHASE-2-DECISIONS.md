# PHASE 2 DECISIONS — SmartPortX

## D-005: SmartPortX Scope — Complementary Intelligence Layer, Not Full TOS
**Date:** 2026-08-26
**Status:** DECIDED
**Decision:** SmartPortX will be an intelligent yard orchestration and gate coordination platform that complements existing TOS, not a full TOS replacement.
**Rationale:** Enterprise TOS costs $1M+ and requires 12-24 months implementation. SmartPortX targets mid-size terminal needs and demonstrates SOA architecture principles applied to real complexity.
**Impact:** Defines project scope boundaries; prevents scope creep into full TOS territory.

## D-006: Optimization Approach — Simplified Heuristics First
**Date:** 2026-08-26
**Status:** DECIDED
**Decision:** Stacking optimization will use simplified heuristic algorithms initially, with option to upgrade to more sophisticated approaches later.
**Rationale:** Full NP-hard optimization is out of student timeline. Published research shows heuristics provide good results (23-78% reshuffling reduction). Start simple, optimize iteratively.
**Impact:** First implementation will use rule-based stacking with simple scoring. Advanced optimization (DQN-based memetic algorithm) deferred to future iterations.

## D-007: IoT Integration — Simulated Layer with Physical-Ready Architecture
**Date:** 2026-08-26
**Status:** DECIDED
**Decision:** SmartPortX will implement a simulated IoT data layer for development/demo, but architect the integration layer to accept real sensor data in future.
**Rationale:** Physical IoT requires hardware procurement, installation, and RF environment testing — beyond student project scope. Simulated data with realistic patterns (noise, gaps, signal degradation) is sufficient for demonstrating the platform's value.
**Impact:** IoT integration service will have a "simulation mode" (default for dev) and a "live mode" (for future physical deployment).

## D-008: EDIFACT Support — Parsing Only, Not Full Compliance
**Date:** 2026-08-26
**Status:** DECIDED
**Decision:** SmartPortX will parse inbound EDIFACT messages (COPRAR, COARRI, CODECO, COPARN) but will not implement full EDIFACT outbound generation or full PCS compliance.
**Rationale:** Full EDIFACT compliance requires certification and country-specific Message Implementation Guides (MIGs). SmartPortX demonstrates EDIFACT parsing capability without claiming full PCS compliance.
**Impact:** EDIFACT parser modules will be implemented as adapters that convert EDIFACT to internal domain model. Outbound messages will use REST API format.

## D-009: Frontend Technology — Web Dashboard, No Mobile
**Date:** 2026-08-26
**Status:** DECIDED
**Decision:** SmartPortX will implement a web-based dashboard using a modern frontend framework (React or Thymeleaf). No mobile application in MVP scope.
**Rationale:** Mobile app requires separate development effort and app store deployment. Web dashboard provides all required functionality for demo. Mobile can be added as future enhancement.
**Impact:** All user interaction through web browser. Dashboard must be responsive for tablet use.

## D-010: Data Simulation Strategy — Realistic Test Data Generator
**Date:** 2026-08-26
**Status:** DECIDED
**Decision:** SmartPortX will include a comprehensive data simulation module that generates realistic operational data for development, testing, and demonstration.
**Rationale:** No physical port access for testing. Realistic data simulation is essential for demonstrating platform value and testing optimization algorithms.
**Impact:** Data generator will produce: container movements, truck arrivals, vessel schedules, equipment status, customs clearance events, IoT sensor readings — all following realistic distributions and patterns.

## D-011: Architecture Pattern — Event-Driven Microservices
**Date:** 2026-08-26
**Status:** DECIDED
**Decision:** SmartPortX will use event-driven microservice architecture with Spring Boot services and async event processing.
**Rationale:** Port operations are inherently event-driven (container move, gate event, vessel arrival). Microservices reflect real domain boundaries (yard, gate, vessel, tracking). Event-driven communication enables loose coupling.
**Impact:** Services communicate via events (not direct REST calls for operational data). REST API used for external clients and synchronous queries.

## D-012: Database Strategy — PostgreSQL with Schema-per-Service
**Date:** 2026-08-26
**Status:** DECIDED
**Decision:** SmartPortX will use PostgreSQL as the primary database with logical schema separation per service.
**Rationale:** PostgreSQL available and verified. Schema-per-service provides data isolation while avoiding operational complexity of multiple database instances. Supports EDIFACT data model requirements.
**Impact:** Each microservice owns its schema. Cross-service data access via events or API calls only. No shared tables.

## D-013: Security Model — RBAC with 5 Roles
**Date:** 2026-08-26
**Status:** DECIDED
**Decision:** SmartPortX will implement role-based access control with 5 predefined roles: Admin, Terminal Operator, Yard Manager, Gate Operator, Read-Only.
**Rationale:** Maps directly to stakeholder roles identified in Phase 2. Provides appropriate access boundaries without over-engineering permission system.
**Impact:** All API endpoints protected by role checks. UI renders differently per role. No custom permission configuration in MVP.

## D-014: Testing Strategy — Test-Driven Development with Integration Tests
**Date:** 2026-08-26
**Status:** DECIDED
**Decision:** SmartPortX will use TDD for business logic with comprehensive integration tests for API and workflow validation.
**Rationale:** Quality gates from Phase 1.5 require evidence-based completion. TDD provides both design feedback and verification. Integration tests ensure services work together.
**Impact:** Unit tests for algorithms and business logic. Integration tests for API endpoints and workflows. Performance tests for NFR validation.

---
*Decisions documented: 2026-08-26*
*Previous decisions: D-001 (Phase 0), D-002 (Phase 1), D-003 (Phase 1), D-004 (Phase 1.5)*
