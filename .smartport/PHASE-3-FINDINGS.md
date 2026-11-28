# PHASE 3 FINDINGS — SmartPortX

## Findings Summary

| ID | Finding | Severity |
|---|---|---|
| F-001 | Service Decomposition Optimal for Scope | POSITIVE |
| F-002 | Single PostgreSQL Instance Acceptable for MVP | POSITIVE |
| F-003 | Eventual Consistency Requires Careful Design | MEDIUM |
| F-004 | EDIFACT Parser Complexity Underestimated | MEDIUM |
| F-005 | RabbitMQ Sufficient for Current Throughput | POSITIVE |
| F-006 | WebSocket Scaling Requires Attention | LOW |
| F-007 | Docker WSL2 Dependency Blocks Containerized Development | HIGH |
| F-008 | Frontend Framework Not Explicitly Decided | LOW |
| F-009 | Algorithm Complexity Managed | POSITIVE |
| F-010 | Security Architecture Comprehensive for MVP | POSITIVE |
| F-011 | Traceability Gap — Export Cut-Off Notification Uncovered | MEDIUM |

---

## F-001: Service Decomposition Optimal for Scope
- **Severity:** POSITIVE
- **Finding:** The 8-service decomposition (Gateway, Identity, Yard, Gate, Container, Vessel, Integration, Notification) maps cleanly to domain boundaries. Each service has a single clear responsibility with no cross-cutting boundary violations found in review.
- **Evidence:** All 38 FRs trace to ≥1 service with no ambiguous ownership; SERVICE-BOUNDARIES.md resolutions table handles all fuzzy areas (position assignment, dual transactions, IMDG rules).
- **Impact:** Clean boundaries enable independent development, testing, and future split/merge without major rework.
- **Recommendation:** Proceed with the 8-service decomposition. Re-validate boundaries in Phase 4 before scaffolding.

## F-002: Single PostgreSQL Instance Acceptable for MVP
- **Severity:** POSITIVE
- **Finding:** Schema-per-service within a single PostgreSQL 17.11 instance provides logical data isolation without the operational overhead of multiple database instances.
- **Evidence:** 7 schemas / 33 tables total; all within single-instance capacity. Each service connects with its own credentials and only accesses its own schema.
- **Impact:** Simpler operations, single backup, single Flyway toolchain per schema. Acceptable for a student team and MVP demo.
- **Recommendation:** Accept single-instance for MVP. Document the split path (per-service containers) as future work.

## F-003: Eventual Consistency Requires Careful Design
- **Severity:** MEDIUM
- **Finding:** Cross-service data access via RabbitMQ events introduces eventual consistency windows. Some workflows require real-time (strong) consistency — chiefly gate processing, which must verify container + customs status before authorizing gate-out.
- **Evidence:** Gate→Container and Gate→Integration use synchronous REST in API-CONTRACTS.md (`POST /gate/check-out`); event-driven updates reserved for non-critical paths.
- **Impact:** A hybrid communication pattern is required: synchronous REST for critical transactional paths, asynchronous events for notifications, analytics, and cross-service propagation.
- **Recommendation:** Maintain the hybrid pattern. Document every cross-service data dependency as either SYNC (must be current) or ASYNC (tolerates lag) in the implementation plan.

## F-004: EDIFACT Parser Complexity Underestimated
- **Severity:** MEDIUM
- **Finding:** EDIFACT messages contain deeply nested segment groups with conditional elements. COPRAR alone has 15+ segment groups with conditional nesting and repetition.
- **Evidence:** DOMAIN-RESEARCH.md (EDIFACT standards section); FEASIBILITY.md rates COPRAR processing "Medium (nested segments)".
- **Impact:** Integration Service will be the most complex and time-consuming service to implement. Parser quality directly affects discharge-plan quality (FR-004.1).
- **Recommendation:** Start with the COPRAR parser (most critical), add COARRI/CODECO/COPARN incrementally. Evaluate the JEDII library; fall back to a hand-rolled interpreter if library maturity is insufficient.

## F-005: RabbitMQ Sufficient for Current Throughput
- **Severity:** POSITIVE
- **Finding:** NFR-002.2 requires ~1,000 events/sec. RabbitMQ handles 20,000+ msgs/sec on modest hardware, giving a 20× headroom.
- **Evidence:** NON-FUNCTIONAL-REQUIREMENTS.md (PRF-002, real-time event processing 1,000+ events/second).
- **Impact:** No need for Kafka at this scale. RabbitMQ's smaller operational footprint suits a student team.
- **Recommendation:** Use RabbitMQ. Add a queue-depth metric/alert so migration to Kafka is triggered by evidence, not guesswork.

## F-006: WebSocket Scaling Requires Attention
- **Severity:** LOW
- **Finding:** WebSocket connections are stateful; multiple gateway instances need sticky sessions or a shared pub/sub subscription store.
- **Evidence:** NFR-002.1 sets concurrent users at 50+. A single gateway instance comfortably handles this for the MVP.
- **Impact:** Not a blocker now, but the scaling path must be documented so it does not surprise a future multi-instance deployment.
- **Recommendation:** Single gateway instance for MVP. Record sticky-session / Redis-pub-sub escalation path in DEPLOYMENT-ARCHITECTURE.md.

## F-007: Docker WSL2 Dependency Blocks Containerized Development
- **Severity:** HIGH
- **Finding:** Docker 29.7.2 is installed but the daemon cannot start because WSL2 is not enabled. Every deployment, CI, and containerized dev workflow depends on a working Docker daemon.
- **Evidence:** FEASIBILITY.md (Docker "PARTIAL / MEDIUM"); RISKS.md R-007 OPEN.
- **Impact:** Docker Compose-based local development and the CI demonstration are blocked until WSL2 is installed.
- **Recommendation:** **Action required** — user must run `wsl --install` as Administrator and restart. Fallback: run the 8 services + PostgreSQL + RabbitMQ directly on the Windows host during Phase 4/5 until Docker is available.

## F-008: Frontend Framework Not Explicitly Decided
- **Severity:** LOW
- **Finding:** D-009 (Phase 2) chose "web dashboard" but left the framework open. ADR-008 proposes React 18+ with TypeScript.
- **Evidence:** PHASE-2-DECISIONS.md D-009; ADR-008 status PROPOSED.
- **Impact:** The frontend is the only stack area not locked by a ratified decision.
- **Recommendation:** Ratify ADR-008 (React + TypeScript) at the start of Phase 4 before scaffolding the dashboard.

## F-009: Algorithm Complexity Managed
- **Severity:** POSITIVE
- **Finding:** Stacking optimization uses simplified heuristics (FIFO, balanced, weight-cluster, departure-cluster) rather than full NP-hard optimization.
- **Evidence:** D-006 (Phase 2); DOMAIN-RESEARCH.md shows published heuristics achieve 23–78% reshuffling reduction.
- **Impact:** Algorithm work is feasible within the student timeline while still delivering the core value proposition (P-001 reshuffling reduction).
- **Recommendation:** Implement FIFO/balanced first, then weight-clustering, then departure-clustering. Each is an isolated algorithm module behind the same optimizer interface (testable independently).

## F-010: Security Architecture Comprehensive for MVP
- **Severity:** POSITIVE
- **Finding:** JWT (RS256) + RBAC (5 roles) + TLS 1.3 + rate limiting + append-only audit covers all of SR-001 through SR-007.
- **Evidence:** SECURITY-ARCHITECTURE.md maps every security requirement; REQUIREMENT-ARCHITECTURE-TRACEABILITY.md §2 confirms 100% coverage.
- **Impact:** Security posture is appropriate for the demo/proof-of-concept stage; no architectural rework is needed before implementation.
- **Recommendation:** Proceed with the designed security architecture. Schedule the penetration test and access-control verification (Phase 7) as planned.

## F-011: Traceability Gap — Export Cut-Off Notification Uncovered
- **Severity:** MEDIUM
- **Finding:** Export cut-off deadline notification (proposed FR-003.9) is not covered by any ratified FR nor by a dedicated architecture component.
- **Evidence:** REQUIREMENT-TRACEABILITY.md gap analysis (P-008 "PARTIAL").
- **Impact:** Exporters may miss cut-off deadlines without proactive alerting — a known medium-severity operations gap. Non-blocking for architecture.
- **Recommendation:** Add FR-003.9 in the next requirement iteration. Interim mitigation: the Gate appointment-notification channel (`/topic/appointments/{id}`) can carry cut-off alerts without new architecture.

---

*Findings documented: 2026-08-27*
*References: ARCHITECTURE-OVERVIEW.md, SERVICE-BOUNDARIES.md, COMMUNICATION-ARCHITECTURE.md, SECURITY-ARCHITECTURE.md, FEASIBILITY.md, RISKS.md, REQUIREMENT-TRACEABILITY.md*