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

## M12 Docker E2E — Testimonials (REAL RUN, 2026-09-22)

> **Deliverable:** one-command Docker stack (`docker compose up --build`). All 9 app services + 5 infra
> containers up and healthy. Evidence classification per AGENTS.md (FACT = observed live; see logs/HTTP
> status in each row).

### 2. ENVIRONMENT (M12 run)
| Item | Value |
|------|-------|
| Host | Windows 11, Docker Desktop 4.92.0 (Server engine 29.8.0, overlayfs) |
| Compose project | `soafinalproject` (root `docker-compose.yml`, `context: .` + multistage Dockerfile) |
| App images | `soafinalproject-<service>:latest` ×9 + dashboard (node/nginx) |
| Infra | `postgres:17.11`, `rabbitmq:3-management`, `eureka` (in-repo), `prometheus:v2.53.0`, `grafana:11.1.0`, `jaeger all-in-one:1.59` |
| DB | `postgres:5432/smartportx`, creds `smartportx`/`smartportx_local`, 7 schemas |
| Gateway | `http://localhost:8080` (JWT authn, RBAC to routes) |

### 3. Bug found & fixed during run
- **FACT:** `smartportx-notification` startup fell back to `IDENTITY_JWKS_URI=localhost:8081/api/v1/auth/jwks`
  (default), so inside the container the JWKS fetch failed → every authenticated request returned HTTP 401
  (`CachingJWKSetSource... Connection refused`). **FIX:** added `IDENTITY_JWKS_URI: http://identity:8081/api/v1/auth/jwks`
  to the `notification` service env in `docker-compose.yml`; recreated container; health returned to `healthy`
  and authenticated calls succeeded. (Only gateway had the env before — notification was missing it.)

### 4. Testimonials CRUD evidence (via gateway, HTTP client)
| Step | Request | Expect | Actual | Verdict |
|------|---------|--------|--------|---------|
| 1 | `POST /api/v1/auth/login` `admin/Admin@12345` | 200 + JWT | 200, tokenType=Bearer | **PASS** |
| 2 | `GET /api/v1/testimonials` (no token) | 401 | 401 | **PASS** |
| 3 | `GET /api/v1/testimonials` (admin JWT) | 200, 12 seeded | 200, 12 rows | **PASS** |
| 4 | `POST /api/v1/testimonials` (admin) | 201 | 201, id issued | **PASS** |
| 5 | `GET /api/v1/testimonials/{id}` (admin) | 200 | 200, matching payload | **PASS** |
| 6 | `GET /api/v1/testimonials` (admin, after create) | 200, 13 rows | 200, 13 | **PASS** |
| 7 | `POST /api/v1/testimonials` (READONLY `viewer` JWT) | 403 | 403 | **PASS** |
| 8 | `DELETE /api/v1/testimonials/{id}` (READONLY JWT) | 403 | 403 | **PASS** |
| 9 | `DELETE /api/v1/testimonials/{id}` (admin) | 204 | 204 | **PASS** |
| 10 | `GET /api/v1/testimonials/{id}` (deleted) | 404 | 404 | **PASS** |
| 11 | `POST` empty name / invalid shape / rating=9 | 400 | 400, `code=validation_failed` | **PASS** |
| 12 | `GET /api/v1/testimonials` (final) | 200, 12 rows | 200, 12 | **PASS** |

### 5. Seed idempotency (restart test)
- **FACT:** `docker restart smartportx-notification` → container healthy; `GET /api/v1/testimonials` = 12 rows,
  0 duplicate names, 0 duplicate `createdAt`; direct `SELECT COUNT(*) FROM smartportx_notification.testimonials` = 12.
  Seeder (`SEED_TESTIMONIALS=true`) is idempotent across restarts. **PASS**

### 6. Remaining blocker for full-service journeys
- **FACT:** compose maps Postgres to host `5432`, which collides with the native host PostgreSQL 17 service.
  For the M12 run the host service was stopped (elevated `net stop postgresql-x64-17`). Recommended follow-up:
  map compose Postgres to a non-conflicting host port (`5433:5432`) and restore PORT; revisit before EJ-01 run or
  Playwright browser journeys.

--------------------------------------------------------------------------------
## § ATTACHED (from final gate) — checklists pass/fail with dates