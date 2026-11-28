# SMARTPORTX — MODULE CRITICAL VALIDATION REPORT

- **Scope:** Independent forensic validation of M1 (Infrastructure), M2 (shared `smartportx-common` +
  microservice discovery), M3 (identity/JWT auth), M4 (container slice) — as delivered, with runtime
  end-to-end proof through the gateway.
- **Method:** Build + test from source, start the full stack, drive real HTTP through the edge, and
  read raw responses/logs. Claims below are marked **FACT** (observed), **SOURCE-DERIVED** (cited
  from a repo artifact), or **INFERENCE**. No claim is made without an observed command result.
- **Classification key:** FACT / SOURCE-DERIVED / INFERENCE.
- **Date:** 2026-09-17. **Author:** validation pass (lead/orchestrator role).

---

## 1. Executive Summary

| Module | Verdict | Basis |
|---|---|---|
| **M1 Infrastructure** | **PASS (broker-dependent leg PENDING)** | 7 `smartportx_*` schemas live; Flyway V1 applied + successful in all 7; `docker compose config` parses; 8/8 Eureka registrations. RabbitMQ unavailable → broker leg not verifiable locally. |
| **M2 common + discovery** | **PASS** | Cross-service reads resolve through the gateway (yard/blocks, appointments, vessels, containers). |
| **M3 identity / JWT** | **PASS** | RS256 token issued (`kid: identity-1`); unauthenticated → 401; role boundaries enforced (403); JWKS public. |
| **M4 container + gateway edge** | **PASS (after 1 fix)** | Rate limit 429, breaker 503 + 504 both runtime-proven; **one real defect found and fixed (F-428)**. |

**One defect was found in the gateway and fixed during this pass:** the CircuitBreaker fallback is a
method-preserving `forward:`, but `FallbackController` was mapped **GET-only**, so fallback for
POST/PUT/DELETE returned a spurious `405 Method Not Allowed` instead of the contractual RFC-9457
`503`/`504`. See **F-428**.

---

## 2. Validation Environment (FACT)

| Item | Value |
|---|---|
| Repo | `D:\SOA FINAL PROJECT` (Maven multi-module, Java 25) |
| Runtime | Java 25.0.3 Temurin (STS bundled JRE); Maven 3.9.16 |
| DB | PostgreSQL 17.11 @ `localhost:5432`, database `smartportx`, service `postgresql-x64-17` |
| Ports | eureka 8761, gateway 8080, identity 8081, yard 8082, gate 8083, container 8084, vessel 8085, integration 8086, notification 8087 |
| Eureka registrations | **8/8** — SMARTPORTX-{CONTAINER,GATE,GATEWAY,IDENTITY,INTEGRATION,NOTIFICATION,VESSEL,YARD} |
| Host note | Machine rebooted 2026-09-17 13:44:55; all service JVMs were restarted afterwards via Scheduled Tasks (`SPX-*`). |

---

## 3. M1 — Infrastructure

**FACT (verified this pass):**
- 7 schemas present, correctly prefixed: `smartportx_container, smartportx_gate, smartportx_identity,
  smartportx_integration, smartportx_notification, smartportx_vessel, smartportx_yard`.
- Flyway: exactly **1** entry (`V1`) per schema, and `success = true` for all 7 → 7/7 clean.
- Base tables per schema: container 5, gate 5, identity 6, integration 5, notification 3, vessel 5,
  yard 8 (**37 total**).
- `docker compose config --quiet` → exit 0 (**parses clean**, requires no daemon).
- 7/7 services carry `src/main/resources/db/migration/V1__init.sql`; scripts `scripts/init-databases.sh`
  and `scripts/rabbit-topology.sh` exist.
- 8/8 services registered in Eureka with the gateway among them.

**PENDING / NOT VERIFIABLE LOCALLY:**
- Broker-dependent leg (event topology, retry/DLQ, WS fan-out) — **RabbitMQ is not running**, and
  Docker daemon is off, so `rabbit-topology.sh` cannot be exercised. This is the pre-existing open
  item already recorded in E-022 ("broker-dependent verification PENDING at M4.7").

**M1 verdict: PASS for all locally verifiable criteria; broker leg remains PENDING (known R-007/R-009).**

---

## 4. M2 — shared library + microservice discovery

**FACT (runtime, through the gateway, authenticated):**
- `GET /api/v1/yard/blocks` → **200**
- `GET /api/v1/appointments` → **200**
- `GET /api/v1/vessels` → **200**
- `GET /api/v1/containers` → **200**

These are cross-service reads resolved through Eureka service discovery and the gateway load balancer,
so discovery + the shared contract layer are exercised end-to-end.

**Prior root-cause note (FACT, from this workstream):** an earlier boot failure was caused by
`@ConditionalOnMissingBean` placed on `@Service`/`@Component` classes, which is self-defeating
(the condition inspects the bean-factory before the bean itself is registered). All 12 occurrences
were removed; repo-wide `src/main` now has **0**. Verified by rebuild + successful boot of all three
affected services.

**M2 verdict: PASS.**

---

## 5. M3 — identity + JWT authentication

**FACT (runtime):**
- `POST /api/v1/auth/login` (admin) → **200**, `accessToken` length **900**, header decodes to
  `{"kid":"identity-1","alg":"RS256"}` → **RS256**.
- `POST /api/v1/auth/login` (viewer) → **200** (token issued).
- `GET /api/v1/auth/jwks` (direct, 8081) → **200** (public RSA JWKS).
- No token → `GET /api/v1/yard/blocks` = **401** (edge rejects before routing).
- Viewer → `GET /api/v1/yard/blocks` = **200**; viewer → `GET /api/v1/users` = **403**
  (role boundary enforced); admin → `GET /api/v1/users` = **200**.
- Direct-to-service bypass check: identity on 8081 answers auth endpoints directly, confirming the
  gateway is the auth enforcement point and services themselves are reached through it.

**M3 verdict: PASS.**

---

## 6. M4 — container slice + gateway edge (rate limit, resilience)

### 6.1 Rate limiting — PASS (FACT)
Burst of **130** requests to `/api/v1/yard/blocks` (post-restart, fresh bucket):
- **104 × 200**, **26 × 429** — consistent with capacity 100 + refill 60/min.
- Earlier in this workstream the 429 body was captured as RFC-9457
  `{"type":"urn:smartportx:problems:rate_limited", …}` with `Retry-After: 1` and a correlation id.

### 6.2 CircuitBreaker fallback — PASS, both branches (FACT)
- **Timeout branch (504):** notification upstream killed while still registered → POST and GET to
  `/api/v1/notifications/preferences` both returned **504 `gateway_timeout`** in ~1.02–1.07 s with
  `instance: /fallback/notification`.
- **No-instance branch (503):** `GET /fallback/yard` and `POST /fallback/yard` → **503
  `service_unavailable`** (RFC-9457, `instance: /fallback/yard`).
- Both bodies carry `correlationId` and `errorId`, and correct `type`/`code`.

### 6.3 DEFECT F-428 — fallback returned 405 for non-GET requests (FIXED)
- **Symptom (FACT):** during a window when identity was not yet available, `POST /api/v1/auth/login`
  through the gateway returned **405** with body
  `{"type":"urn:smartportx:problems:error","title":"Method Not Allowed","status":405,
  "detail":"Request method 'POST' is not supported.", …}` instead of the contractual 503/504.
- **Root cause (FACT):** the routes use `fallbackUri: forward:/fallback/{service}`, and a Spring
  `forward:` **preserves the original HTTP method**. `FallbackController` was annotated
  `@GetMapping("/{service}")`, so a POST/PUT/DELETE fallback matched the path but not the method and
  the framework produced `405`. GET fallbacks worked, which is why the defect was easy to miss.
- **Impact (INFERENCE):** every write request (create/update/delete) that hit a tripped breaker or an
  unavailable upstream received a misleading, non-retryable `405` instead of `503`/`504`, breaking the
  documented edge error contract and defeating client retry logic.
- **Fix (FACT):** `FallbackController.fallback` changed from `@GetMapping("/{service}")` to
  `@RequestMapping("/{service}")` (all methods); unused `GetMapping` import removed.
- **Post-fix proof (FACT):** rebuild + restart; then
  - `POST /api/v1/notifications/preferences` (upstream down) → **504 `gateway_timeout`** (`instance:
    /fallback/notification`) — was 405 before the fix;
  - `POST /fallback/yard` → **503 `service_unavailable`** — was 405 before the fix;
  - `GET /fallback/yard` → **503** (unchanged);
  - regression: login 200 (jwt 900), `GET /api/v1/yard/blocks` 200, rate limit 104×200/26×429.
- **Regression guard (FACT, follow-up closed):** `GatewayResilienceTest` gained
  `postFallbackPreservesMethodYielding503ProblemJson` and
  `deleteFallbackPreservesMethodYielding503ProblemJson` (dead upstream `127.0.0.1:1`). A **mutation
  check** re-introducing `@GetMapping` failed exactly these two tests with
  `expected:<503> but was:<405>`; the restored suite is **16/16 green** (`BUILD SUCCESS`).

**M4 verdict: PASS after F-428 fix.**

---

## 7. Findings

| ID | Severity | Status | Finding |
|---|---|---|---|
| **F-428** | HIGH | **FIXED + VERIFIED (regression test added)** | CircuitBreaker fallback returned `405 Method Not Allowed` for POST/PUT/DELETE because `FallbackController` was GET-only while `forward:` preserves the method. Fixed to method-agnostic mapping; 504/503 proven after fix, and POST/DELETE covered by `GatewayResilienceTest` (mutation-checked). |
| **F-429** | LOW | OPEN (doc) | `E-032` describes `RateLimitGlobalFilter` as using **bucket4j**; the implementation is a **custom in-memory token bucket** (`static final class Bucket`) and there is no bucket4j dependency. |
| **F-430** | LOW | OPEN (doc) | `E-032` names the edge filter `EdgeJwtAuthFilter`; the actual class is `JwtEdgeFilter`. |
| **F-431** | MEDIUM | OPEN | `JWT_SECRET` is set for 6 services in `docker-compose.yml` but read nowhere — identity signs with an **in-memory generated RSA keypair** (`KeyRepository`), so tokens are invalidated by any restart and cannot be shared across replicas. Dead config misleads operators. |
| **F-432** | MEDIUM | OPEN (env) | With RabbitMQ down, `/actuator/health` on yard/gate/vessel/container/integration reports DOWN (503) even though event publishing is disabled — a broker-less dev run looks unhealthy. Candidate mitigation: `management.health.rabbit.enabled: false` for the native profile. |
| **F-433** | LOW | OPEN | `SEED_DEMO_USERS` defaults false and is omitted from `docker-compose.yml`, so a compose run has no demo users unless env is supplied. |
| **F-434** | MEDIUM | OPEN (env) | Docker daemon is off → the containerised `docker compose up` path (M1.2 / R-007) is not executable in this workspace; parity was validated natively instead. |

---

## 8. Documentation Drift Reconciliation

Discovered inconsistencies between written evidence and the code as delivered:

1. **Rate limiter** — `E-032` says bucket4j; code is a custom token bucket (**F-429**).
2. **Edge filter name** — `E-032` says `EdgeJwtAuthFilter`; file is `JwtEdgeFilter` (**F-430**).
3. **JWKS default** — `GatewayConfig` defaults to `http://localhost:8081/api/v1/auth/jwks` while
   compose sets `IDENTITY_JWKS_URI` to the container hostname; native runs rely on the default.
4. **`JWT_SECRET`** — present in compose for 6 services, consumed by none (**F-431**).
5. **`IMPLEMENTATION-STATUS.md` header** — stale (2026-08-31) and its SCAFFOLDED table (E-021)
   disagrees with the verified milestone table (E-024…E-033); needs a dated refresh.
6. **Route count** — `E-032` says "10 routes"; `application.yml` defines 9 route ids
   (identity-auth, identity-users, container, yard, gate, vessel, integration, notification,
   notification-ws). Reconcile the count.

---

## 9. Residual Risks / Not Verifiable Here

- **RabbitMQ / broker topology** — cannot be validated locally; blocks M4.7 broker-dependent evidence
  (pre-existing R-009).
- **Docker Compose E2E** — daemon off; R-007 open.
- **Token persistence across restarts** — identity generates an ephemeral RSA keypair (**F-431**); any
  restart invalidates issued tokens. Acceptable for a demo, noted for production readiness.
- **Localhost JWKS default** — native runs depend on 8081 being reachable at the default URI.

---

## 10. Verdict

- **M1:** PASS (broker leg pending)
- **M2:** PASS
- **M3:** PASS
- **M4:** PASS (after F-428 fix)

One real, contract-breaking defect (F-428) was discovered by adversarial probing rather than by the
existing test suite — the suite exercises GET fallbacks only — which is the clearest argument for
independent runtime validation of this edge.

*(Evidence entry to accompany this report: E-033 in `EVIDENCE.md`.)*
