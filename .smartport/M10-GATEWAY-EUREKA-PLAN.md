# M10 — Discovery (Eureka) + Gateway Edge — Implementation Plan

Phase 4 · increment M10 of `PHASE-4-IMPLEMENTATION-PLAN.md` (§ M10, lines 253–264).
Status: **IN PROGRESS** · Owner: M10 dev · Gate: M10 DoD + full `mvn -B clean verify` (zero infra).

## Scope

| Task | Service | Summary |
|---|---|---|
| M10.1 | smartportx-eureka | Basic-auth protected dashboard; permit registration/health paths |
| M10.2 | all 8 services | Eureka registration (DONE — `eureka.client.service-url.defaultZone` present everywhere) |
| M10.3 | smartportx-gateway | Routing config (reconciled, NO StripPrefix), load-balancer |
| M10.4 | smartportx-gateway | Edge JWT validation (RS256), public whitelist, role-gate, trusted header injection |
| M10.5 | smartportx-gateway | Correlation propagation + tracing (traceId → MDC) |
| M10.6 | smartportx-gateway | Timeouts, circuit breaker + fallback, rate limiting |
| M10.7 | smartportx-gateway | RFC 9457 error handling + contract test |

## Reconciliations (source-of-truth decisions)

- **No `StripPrefix` at the gateway.** All controllers mount at `/api/v1/...` (verified:
  identity `AuthController`/`UserAdminController`; yard/gate/container/vessel/notification `@RequestMapping("/api/v1/...")`).
  The `StripPrefix=2` in `COMMUNICATION-ARCHITECTURE.md` §7.1 is **wrong** (would turn
  `/api/v1/containers/x` into `containers/x` and break routing). Plan's `/api/{svc}/**`
  route format is also wrong. Docs §7.1 will be corrected as part of this increment.
- Identity real public endpoints (SOURCE-DERIVED, `SecurityConfig.java`): `POST /api/v1/auth/login`,
  `POST /api/v1/auth/refresh`, `GET /api/v1/auth/jwks`, `OPTIONS`. §7.2's `/api/v1/identity/auth/**`
  and `/api/v1/identity/health` are stale — corrected whitelist below.
- Gateway does **NOT** depend on smartportx-common (it is servlet-bound and the reactive
  gateway is intentionally excluded — `SmartPortxCommonAutoConfiguration` javadoc). Gateway
  re-implements its own reactive correlation filter + RFC 9457 error handler mirroring
  common's shapes (`urn:smartportx:problems:<code>`, `code`, `errorId`, `correlationId`,
  `instance`, `fieldErrors`).

## Reconcracted Route Table (gateway `spring.cloud.gateway.routes`)

Routes in `application.yml` under `spring.cloud.gateway.routes`; each `uri` uses an env
placeholder so tests can redirect a route to a stub (production default `lb://…`).

| Route id | Predicate path(s) | URI (env override) | RBAC roles |
|---|---|---|---|
| smartportx-identity-auth | `/api/v1/auth/**` | `lb://smartportx-identity` | public whitelist (login/refresh/jwks; auth passthrough otherwise) |
| smartportx-identity-users | `/api/v1/users/**` | `lb://smartportx-identity` | ADMIN (method-security downstream also enforces) |
| smartportx-container | `/api/v1/containers/**` | `lb://smartportx-container` | ADMIN, TERMINAL_OP, YARD_MANAGER, GATE_OPERATOR, READONLY |
| smartportx-yard | `/api/v1/yard/**` | `lb://smartportx-yard` | ADMIN, TERMINAL_OP, YARD_MANAGER, READONLY |
| smartportx-gate | `/api/v1/gate/**` `/api/v1/appointments/**` `/api/v1/quotas/**` | `lb://smartportx-gate` | ADMIN, TERMINAL_OP, GATE_OPERATOR, READONLY |
| smartportx-vessel | `/api/v1/vessels/**` `/api/v1/schedules/**` `/api/v1/discharge-plans/**` | `lb://smartportx-vessel` | ADMIN, TERMINAL_OP, READONLY |
| smartportx-integration | `/api/v1/integration/**` | `lb://smartportx-integration` | ADMIN |
| smartportx-notification | `/api/v1/notifications/**` | `lb://smartportx-notification` | ADMIN, TERMINAL_OP, YARD_MANAGER, GATE_OPERATOR, READONLY |
| smartportx-notification-ws | `/ws/**` | `lb:ws://smartportx-notification` | exempt from edge JWT (STOMP handshake authorizes downstream) |

Gate/vessel sub-routes inherit their parent service role list (INFERENCE: same service,
same owners).

## Gateway design

### M10.1 Eureka security
- Add `spring-boot-starter-security` to eureka pom.
- `EurekaSecurityConfig`: HTTP Basic; `permitAll`: `/eureka/**`, `/actuator/health`,
  `/actuator/info`, `/actuator/metrics`; everything else authenticated (dashboard `/`).
- Credentials via env `SMARTPORTX_EUREKA_SECURITY_USERNAME` / `SMARTPORTX_EUREKA_SECURITY_PASSWORD`
  (dev defaults; prefix matches `EurekaSecurityProperties` `smartportx.eureka.security.*`).
  Stateless, CSRF off.
- Test (`EurekaSecurityTest`): unauth `/` → 401; `/eureka/apps` → 200; `/actuator/health` → 200.

### M10.3 Routes + load-balancer
- `application.yml` `spring.cloud.gateway.routes` per table; URI env placeholders
  `GATEWAY_URI_SMARTPORTX_{SVC}` default `lb://…`, and `GATEWAY_URI_SMARTPORTX_NOTIFICATION_WS`
  default `lb:ws://…`.
- Add `spring-cloud-starter-loadbalancer` to gateway pom.

### M10.4 `JwtEdgeFilter` (reactive `GlobalFilter`)
- Decoder: `ReactiveJwtDecoder` bean from `${IDENTITY_JWKS_URI:http://localhost:8081/api/v1/auth/jwks}`
  (Nimbus, lazy); tests override with a public-key decoder (GatewayTestTokens pattern).
- Public whitelist: `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/jwks`,
  `/actuator/health`, `/actuator/info`, `OPTIONS`, `/ws/**` (WS exempt).
- Authenticated requests: decode + issuer check; no implicit ADMIN bypass; longest-prefix
  role-route matching using the **full roles set**; strip legacy single-`role` fallback → use `roles[0]`.
- **Strip client-supplied `X-User-Id`/`X-User-Role`** then inject trusted values from JWT
  (`sub`, `roles[0]`); forward original `Authorization` downstream (services re-validate).
- 401 → problem+json code `unauthorized`; 403 → `forbidden`.

### M10.5 Correlation + tracing
- `CorrelationWebFilter` (reactive): read/generate `X-Correlation-Id`, echo on response,
  keep value on exchange attribute; downstream services pick it up via their servlet filter.
- Gateway pom: `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` → Boot's
  `MdcObservationFilter` provides traceId/spanId MDC. OTLP endpoint env-optional (never fails boot).

### M10.6 Resilience
- `spring.cloud.gateway.httpclient.response-timeout: 5s` (+ `connect-timeout: 2s`);
  `ResponseTimeoutException` → 504 problem+json.
- Per-route `CircuitBreaker` filter factory (`spring-cloud-starter-circuitbreaker-reactor-resilience4j`)
  with `fallbackUri: forward:/fallback/{svc}` + gateway-local `FallbackController` → 503 problem+json.
- In-memory token-bucket rate limiter (`GlobalFilter`), refill-on-access, key = route id +
  `X-User-Id` (else client IP). Do NOT trust `X-Forwarded-For`. 429 problem+json. Single-instance
  caveat documented (no Redis). `/ws/**` exempt.

### M10.7 Error handler + contract test
- Custom `ErrorWebExceptionHandler` (replaces default) producing RFC 9457 `application/problem+json`
  mirroring common `ApiProblems`: `type=urn:smartportx:problems:<code>`, `title`, `status`,
  `detail`, `code`, `errorId` (500 only), `correlationId`, `instance`. Downstream problem+json passes through.
- Contract test: `GatewayEdgeContractTest` @SpringBootTest(RANDOM_PORT) + JDK `HttpServer` stub upstream
  on container route; `GatewayTestTokens` (copy of yard pattern, issuer `smartportx-identity`); asserts
  no-strip passthrough, injected `X-User-Id/Role/Correlation-Id`, stripped spoofed headers, 401/403/429,
  problem+json shape, WS/routes.

## Quality gates
- TDD (failing test first) per task. Eureka/gateway tests run with `spring.main.allow-bean-definition-overriding`
  + `eureka.client.enabled: false` as needed — zero infra under `mvn -B clean verify`.

## Docs obligations (this increment)
- `COMMUNICATION-ARCHITECTURE.md` §7.1: remove StripPrefix, fix route table + path predicates;
  §7.2: replace stale identity public paths, add users/appointments/quotas/schedules/discharge-plans
  role rows; §7.3: reconcile `X-API-Key`/`X-Request-Timestamp` rows to implemented reality.
- `DEPLOYMENT-ARCHITECTURE.md` gateway env: drop `JWT_SECRET`/`JWT_EXPIRATION`, use `IDENTITY_JWKS_URI`.
- `docker-compose.yml` gateway: replace JWT leftovers.
- eureka Dockerfile (missing — compose references `./smartportx-eureka/Dockerfile`); create.
- `EVIDENCE.md` E-032 (gateway/eureka evidence); `IMPLEMENTATION-STATUS.md` line 98.