# SMARTPORTX — PERFORMANCE TEST REPORT (Phase 4)

> Status: **NOT YET RUN** — stub created at Phase 4 kickoff (2026-08-27). Populated at M12.5 with real
> measurements. Thresholds referenced from NON-FUNCTIONAL-REQUIREMENTS.md (NFR-1 latency, NFR-2 concurrency)
> and RESILIENCE-ARCHITECTURE. No numbers claimed until measured (brief §42).

--------------------------------------------------------------------------------
## 1. SUT & ENVIRONMENT
- Deployment: native stack (scripts/dev-up.ps1) on Windows host; PG 17.11 localhost; real services + broker.
- Load driver: Gatling/k6 scenario suite `scripts/perf/` (new — recorded here when created).
- Baselined: CPU, memory, PG slow-query log captured alongside runs.

## 2. SCENARIOS
| ID | Scenario | Load model | Threshold (NFR ref) | Result | Pass/Fail |
|----|----------|-----------|---------------------|--------|-----------|
| PF-01 | auth burst (login+refresh) | ramp→100 concurrent | per NFR-1 | NOT RUN | — |
| PF-02 | container search (paged filter) | sustained | per NFR-1 | NOT RUN | — |
| PF-03 | yard slot query | sustained | per NFR-1 | NOT RUN | — |
| PF-04 | appointment booking under contention | ramp→quota pressure | per NFR-1/NFR-2 | NOT RUN | — |
| PF-05 | WS fanout to N subscribers | N=50 | per NFR-1 | NOT RUN | — |
| PF-06 | EDIFACT COPRAR parse throughput | file stream batch | per NFR-1 | NOT RUN | — |
| PF-07 | gateway routing overhead | mixed routes | per NFR-1 | NOT RUN | — |

## 3. METRICS CAPTURE
p50 / p95 / p99 latency, throughput (rps), error %, CPU/mem via Actuator, PG EXPLAIN/slow-log review,
Hibernate query-count audit on hot read paths (N+1 check “TEST-STRATEGY §11”).

## 4. FINDINGS & ACTIONS
_(real numbers + fix/accept-with-reason recorded here at M12.5)_

## 5. SIGN-OFF
QA + Performance reviewer. BLOCKERS: R-007 if broker-dependent scenarios cannot run.