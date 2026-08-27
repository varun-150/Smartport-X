# SMARTPORTX — E2E TEST REPORT (Phase 4)

> Status: **NOT YET RUN** — stub created at Phase 4 kickoff (2026-08-27). Will be populated at M12.1
> (real run) and M13.1 (final real-world demo). No pass/fail claims until then (brief §42).
> Approach: TEST-STRATEGY.md §10. Framework: Playwright (TS) + HTTP client against the running stack.

--------------------------------------------------------------------------------
## 1. JOURNEYS
| ID | Journey (TEST-STRATEGY §10) | Script | Result | Evidence (screenshot/log) | Notes |
|----|------------------------------|--------|--------|---------------------------|-------|
| EJ-01 | Login → appointment → gate → yard → notification → dashboard (primary §51) | — | NOT RUN | — | |
| EJ-02 | Vessel discharge flow | — | NOT RUN | — | |
| EJ-03 | EDIFACT COPRAR → pre-alloc → events | — | NOT RUN | — | |
| EJ-04 | Customs HELD blocks gate-out; cleared → succeeds | — | NOT RUN | — | |
| EJ-05 | READ_ONLY viewer: reads live, mutation → 403 UI state | — | NOT RUN | — | |

## 2. ENVIRONMENT
- Stack: scripts/dev-up.ps1 (native) — not yet run.
- Browser: Playwright Chromium headful/headless.
- Assertion guarantee: every UI value cross-checked to real WS/REST response (no static JSON).

## 3. BLOCKERS
- RabbitMQ broker required (WSL2 or choco install) before EJ-01–04 can complete (R-007).
- Frontend (M11) must exist before browser journeys (EJ run scheduled after M11).

--------------------------------------------------------------------------------
## § ATTACHED (from final gate) — checklists pass/fail with dates