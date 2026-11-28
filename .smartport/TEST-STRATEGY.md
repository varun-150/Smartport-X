# SMARTPORTX — TEST STRATEGY (Phase 4)

> Version 1.0 — 2026-08-27. Companion to PHASE-4-IMPLEMENTATION-PLAN.md §6 + §9.
> Governing doc: NON-FUNCTIONAL-REQUIREMENTS.md (16 groups), REQUIREMENTS.md (46 FR),
> SECURITY-REQUIREMENTS.md (36 SR). QA/TESTER objective = disprove the implementation (brief §5).

--------------------------------------------------------------------------------
## 1. TEST PYRAMID & TOOLING

| Layer          | Tooling                                                              | Portrait                              | CI stage |
|----------------|----------------------------------------------------------------------|---------------------------------------|----------|
| Unit           | JUnit 5 + AssertJ (services/domains)                                  | Fast, pure domain: state machine, mapping, calculations, validation | build    |
| Repository     | @SpringBootTest + @DataJpaTest against real local PostgreSQL 17.11 (NFR real DB; Testcontainers fallback once WSL2 active) | Schema/DDL + queries + constraints | build    |
| Integration    | @SpringBootTest, random port, embedded real services via discovery stubs, RabbitMQ (local or Testcontainers) | Gateway routing, JWT edge, event flows, Flyway | build/verify |
| Contract       | Spring Cloud Contract (Verifier + Stub Runner)                        | Provider per service; consumers via stubs | verify   |
| API            | MockMvc / WebTestClient against real service context                  | Each endpoint: status, validation, authz, errors, idempotency | verify    |
| Security       | Dedicated security test class per sensitive slice + JWT attack matrix | Token abuse, IDOR, esca, injection, secrets | verify    |
| Concurrency    | Parallel test runner (JUnit @Execution, ExecutorService) + lock assertions | Same-container, same-appointment, same-slot, duplicate-events | verify    |
| Failure        | Resilience4j tests + RabbitMQ DLQ tests + actuator state asserts      | Downstream down, DB down, timeout, retry, circuit open, DLQ route | verify    |
| E2E            | Playwright (TS) + HTTP client against running stack (scripts/dev-up.ps1). Playwright CLI skill for browser automation | Full FR journeys incl. real WS + real UI | manual/E2E job |
| Performance    | Gatling or k6 scenario runner (light)                                 | FR/NFR latency threshold under load   | perf job   |

Rationale: JUnit+AssertJ and Spring Cloud Contract are already in the Spring Boot BOM
(= D-014/ADR-011 approved stack); Playwright = approved dashboard stack companion (D-009/D-026,
java-springboot-testing / testing-webapps skills). No new untracked frameworks.

--------------------------------------------------------------------------------
## 2. STATUS VOCABULARY (identical to implementation, brief §42)
NOT STARTED → SCAFFOLDED → IN PROGRESS → IMPLEMENTED → TESTED → VERIFIED → BLOCKED.
"MANUALLY VERIFIED ONCE" is NOT "TESTED". A test must be **re-runnable** against the code to count.

--------------------------------------------------------------------------------
## 3. UNIT TEST MANDATE (antipattern: meaningless tests)
Test real business rules per layer:
- Container state machine: every legal/illegal transition table-driven (M4.4) — 17[] edges from HEH enum
- EDIFACT COPRAR/COPARN mapping incl. malformed segment streams (M8.3/M8.4)
- Yard slot allocation: nearest-walk + IMDG segregation + reshuffle heuristic "reason" (M5.3/M5.5)
- Identity: BCrypt, refresh rotation/reuse detection, password history/lockout (M3.4–M3.5, M3.8)
- Gate: quota math, time-window/late-arrival rules, duplicate check-in (M6.2, M6.6)
- Common: correlation filter, RFC 9457 mapper (no leak), pagination binding (M2.1–M2.3)

Coverage target: **≥80% line for domain logic** (measured; drop allowed only for glue code, recorded).

--------------------------------------------------------------------------------
## 4. INTEGRATION TEST MANDATE
Every **real** integration boundary must be exercised (brief §31):
- DB schema/flyway: each Flyway migration applies to scratch schema; idempotent re-run.
- Service↔service REST: circuit timeout/retry path with WireMock stub of the far service (5s/1 retry/CB values asserted).
- Gateway: routing per route, edge JWT→X-User-Id/X-User-Role injection, role-route allow/deny, RFC 9457 normalization, correlation propagation.
- Events: publish→consume→idempotent replay (two identical messages → single state change).
- Discovery: register/deregister/health; route to unavailable service → gateway failure behavior (brief §16).
- External-boundary (integration svc): StAEDI parse is pure; message→event pipeline tested with real broker.

--------------------------------------------------------------------------------
## 5. API MANDATE (per endpoint — brief §32)
For EVERY endpoint of API-CONTRACTS.md verify: method, path, request, response, each documented
status code, validation errors (RFC 9457 fields), authz (role allow/deny matrix), idempotency
(repeat request → same result/409 as documented), boundary conditions (pagination, empty, bad body,
oversized, malformed UUID).

A per-service `ApiContractTest` (WebTestClient) is REQUIRED in the same milestone as the endpoint.

--------------------------------------------------------------------------------
## 6. CONTRACT TESTING (from day one)
- Provider repos: each service publishes `src/test/resources/contracts/<name>.yaml` (or Groovy DSL).
- Stub Runner used in consumers' integration tests (gate→container, gate→yard, yard→container,
  yard→gate, vessel→container, vessel→yard, integration→container/yard/gate, gateway→identity).
- Contract change = breaking change → architecture change control (§4 of plan) — never silent.
- Provider tests must hit the real service bean (not mocks) via Spring Cloud Contract Verifier.

--------------------------------------------------------------------------------
## 7. SECURITY TEST MANDATE (Security agent = BLOCKING authority, brief §35)
Mandatory security test class `XSecurityTest` per service (must exist, not optional):
- AuthN matrix: valid/invalid/missing/expired/malformed/forged (wrong alg/key) token.
- AuthZ: per-role matrix on every protected endpoint → denied = 403 (no data leak), ACL IDOR attempt.
- Input: SQLi/XSS/oversized payload on representative endpoints (values rejected, RFC 9457).
- Info leak: no stacktrace/password/token in any error body (assert on ProblemDetail).
- Secrets: verification (gitleaks/hooks) run in CI; repository scan zero secrets.
- Injection/escalation: role escalation attempt (user tries admin route with helper token) → 403.
- AuthN abuse: login rate-limit lockout after N fails; refresh reuse → family revoke.
- SR coverage: map each of 36 SR → test or explicit defer (SR-TRACE in SECURITY-TEST-REPORT.md).

--------------------------------------------------------------------------------
## 8. FAILURE TESTING (brief §34)
Documented expected behavior for each scenario, then assert:
| Scenario | Expected |
|---|---|
| Service unavailable (target down) | Caller retries ×1 → circuit opens → gateway 502/503 RFC 9457 → dashboard degraded state |
| DB unavailable | Health = DOWN (db indicator), service rejects with 503, no partial writes |
| Timeout | 5s timeout enforced; single retry; no hang |
| Duplicate request (idempotency) | Second write no-ops or 409 per contract; no double event |
| Duplicate event | Consumer dedupes by eventId; state unchanged on replay |
| Integration failure | EDIFACT error row + retry→DLQ; no silent drop |
| Invalid state transition | 409 with prior/new state |
| Unauthorized action | 403, audited |
| Session expiry | 401 → dashboard refresh flow → redirect to login (no blank screen) |

--------------------------------------------------------------------------------
## 9. CONCURRENCY TESTING (brief §36)
- Two users modify same container segment concurrently → last-write with @Version → OptimisticLock → 409, no torn state (test with parallel HTTP).
- Two trucks same appointment → one wins (check-in), second 409 (quota/lock).
- Two moves same yard slot → one wins, other 409; slot occupancy consistent.
- Duplicate events arrive together → single side effect (idempotency assert).
- Quota decrement under parallel booking → no oversell (unique/constraint + lock backstop).

--------------------------------------------------------------------------------
## 10. E2E JOURNEYS (brief §33 — must be REAL stack run)
1. `Login → Create Appointment → Truck Arrival → Gate Validation → Container Processing → Yard Operation → State Change → Dashboard Update → Notification → Audit` (primary, §51 demo)
2. Vessel discharge: schedule arrives → discharge plan → containers DISCHARGED → yard slots assigned → progress WS update
3. EDIFACT: upload COPRAR → parsed → yard pre-allocates → status events → dashboard alert
4. Customs: container HELD → gate-out blocked → cleared → gate-out succeeds
5. READ_ONLY role sees updated dashboard but cannot mutate (backend still blocks)

Each journey recorded with: steps, commands, screenshots (Playwright), assertion that UI values
come from real WS/REST (no static JSON).

--------------------------------------------------------------------------------
## 11. PERFORMANCE TESTING (PERFORMANCE-TEST-REPORT.md)
- Thresholds from NON-FUNCTIONAL-REQUIREMENTS + RESILIENCE-ARCHITECTURE.
- Scenarios: auth burst (login), container search (paged), yard slot query, appointment booking
  under contention, WS fanout to N subscribers, EDIFACT parse throughput.
- Metrics: p50/p95/p99 latency, throughput (rps), error%, CPU/mem (Actuator), PG slow-query log review.
- N+1 audit: Hibernate query log run on every read path (repository tests assert query count where hot).
- Ad-hoc results recorded as real numbers; comparisons vs thresholds; PERF findings → fix or accept-with-reason.

--------------------------------------------------------------------------------
## 12. TEST DATA & ISOLATION
- No shared test DB state: each service test uses a throwaway scratch schema (`smartportx_<service>_test`) created/dropped by Flyway per run; seeds via `scripts/seed-data.sql` idempotent inserts.
- Realistic data simulator (D-010) feeds containers/trucks/vessels/customs/IoT events — built as a dev/test utility under `scripts/` and integration `/sim` endpoints (documented as SIMULATED — brief §24 honesty rule).
- Evidence: each milestone closes with `mvn verify` + Playwright run output appended to EVIDENCE.md.

--------------------------------------------------------------------------------
## 13. DEFINITION OF "TESTED" (used in IMPLEMENTATION-STATUS.md + reports)
A feature is TESTED iff: (1) unit tests cover business rules; (2) API contract test covers endpoints
with roles&errors; (3) integration boundary exercised where real (DB/event/HTTP); (4) security test
class present for sensitive slices; (5) re-runnable on clean checkout. VERIFIED adds: E2E journey
and/or performance/concurrency evidence recorded with real output.