# SMARTPORTX — PHASE 4 DECISIONS

> Living log. Decision IDs continue the D-0xx sequence (D-001…D-028 from earlier phases → D-4xx here).
> Each decision either ratifies an approved-architecture point into executable form or resolves a
> phasel-4 conflict (see PHASE-4-FINDINGS.md). Status + rationale recorded.

--------------------------------------------------------------------------------
## D-409 — Monorepo layout & Maven module boundary (ratification)
- **Context:** DEPLOYMENT-ARCHITECTURE §4 defines images per service; SERVICE-CATALOG defines 8 services + eureka.
- **Decision:** single repo at D:\SOA FINAL PROJECT with parent POM + 11 modules (common, eureka, gateway,
  identity, container, yard, gate, vessel, integration, notification) + separate dashboard (Node/Vite).
  Docker build context per service (§2 of plan). Eureka and gateway ship as their own modules.
- **Consequences:** one CI pipeline; contract stubs share repo (TEST-STRATEGY §6); clear version pinning.
- **Status:** ACCEPTED.

## D-410 — RabbitMQ topology canonical (resolves F-401)
- **Decision:** 3 exchanges per COMMUNICATION-ARCHITECTURE — `smartportx.domain-events` (topic), `smartportx.notifications`
  (topic), `smartportx.audit-events` (topic); queue `{service}.{event-type}`; retry `{queue}.retry`; DLQ `{queue}.dlq`.
  Routing key convention `{service}.{event-type}`; DLX `smartportx.dlx` on retry queues. SERVICE-CATALOG §4.2 single-
  exchange sketch superseded (documentation fix flagged).
- **Status:** ACCEPTED (2026-08-27, architect + integration reviewer).

## D-411 — Container status enum canonical (resolves F-402)
- **Decision:** code + schema use §7.1 enum exactly: REGISTERED, AT_GATE, IN_YARD, LOADED, DISCHARGED, AT_GATE_OUT,
  DEPARTED, HELD. Allowed transitions defined in M4.4 table (matches WORKFLOWS.md). ARCHITECTURE-OVERVIEW enum
  superseded; overview doc updated during doc-fix sprint.
- **Status:** ACCEPTED.

## D-412 — Canonical role model (resolves F-403)
- **Decision:** JWT claims and security annotations use exclusively: ADMIN, TERMINAL_OPERATOR, YARD_MANAGER,
  GATE_OPERATOR, READ_ONLY, plus ACTUATOR_READ (health/monitoring). Legacy variants retired. CUSTOMS = gate
  condition (integration service), NOT a user role. Role→endpoint matrix per COMMUNICATION-ARCHITECTURE role-routes.
- **Status:** ACCEPTED.

## D-413 — EDIFACT parser: StAEDI (resolves F-404 / F-004)
- **Decision:** inbound COPRAR/COPARN parsing uses `io.xlate:staedi` (1.26.x, Apache-2.0, Maven Central). In-service
  transformer maps StAEDI events → domain announcements (plan M8.2–M8.4). No hand-rolled EDIFACT lexer. No networking
  in parser; inbound message transport remains RabbitMQ/S3-style ingress as defined by INTEGRATION-ARCHITECTURE §3.
- **Rationale:** active maintenance, streaming (large files), explicit UN/EDIFACT interchange model, permissive license.
- **Status:** ACCEPTED (pending final dependency approval at M8 build; recorded as design decision).

## D-414 — Requirement reference counts (resolves F-405)
- **Decision:** all Phase 4 reports use 46 FR / 16 NFR groups / 36 SR. Any doc still claiming 38/24/22 is corrected
  during M12.7 traceability sweep. Traceability table in REQUIREMENT-ARCHITECTURE-TRACEABILITY.md is authoritative for FR rows.
- **Status:** ACCEPTED.

## D-415 — Phase plan realignment (resolves F-406)
- **Decision:** "Phase 4" per user brief = implementation (this plan, M0–M13). ROADMAP: Phase 4 → this plan; next
  gates become Phase 5.A (hardening/deployment) because user explicitly does not auto-start Phase 5.
- **Status:** ACCEPTED.

## D-416 — Test framework stack (ratification of Phase 3 §12 recommendation)
- **Decision:** Spring Cloud Contract (provider Verifier + consumer Stub Runner) from day one; JUnit 5 + AssertJ;
  WebTestClient for API tests; WireMock for circuit/timeout stubs; Playwright (TS) for E2E; Gatling (or k6 if JS-native)
  for perf. All in BOM / already in stack (D-014, D-009, D-026) — no new external frameworks.
- **Status:** ACCEPTED.

## D-417 — Concurrency strategy (ratification of Phase 3 analysis, concrete)
- **Decision:** `@Version` optimistic locking on aggregate roots (container position, appointment, yard slot quota);
  unique DB constraints as backstop; idempotency table (eventId) in consumers for Rabbit replay; Redis not required
  for Phase 4 (quotas are per-appointment, not distributed counters). Pessimistic lock only where shown necessary by
  M12.3 concurrency tests.
- **Status:** ACCEPTED (revisit at M12.3 evidence).

## D-418 — Env separation & secrets
- **Decision:** dev runs natively (Postgres 17.11 localhost, RabbitMQ via choco or WSL2-enabled Docker) with
  `.env.example` committed; production compose uses env-injected secrets only. No secrets in git (gitleaks gate in CI).
- **Status:** ACCEPTED.

## D-419 — Frontend auth model
- **Decision:** dashboard stores access token in memory + refresh token in HttpOnly cookie set by identity service
  (post-login); axios interceptor refreshes on 401; frontend role hiding is cosmetic — backend `@PreAuthorize` + gateway
  role-route is the security boundary (defense in depth). No tokens in localStorage.
- **Status:** ACCEPTED.

## D-424 — Database deployment: single DB + schema-per-service (resolves F-410; deviates DEPLOYMENT-ARCHITECTURE §3.1)
- **Context:** ADR-004 / DATA-ARCHITECTURE §2 / ARCHITECTURE-OVERVIEW §5 all mandate ONE PostgreSQL instance, one
  database `smartportx`, 7 logical schemas. DEPLOYMENT-ARCHITECTURE §3.1/§3.3 instead points each service at its own
  DATABASE (`jdbc:postgresql://postgres:5432/smartportx_identity`, …) — contradicts POSTGRES_DB=smartportx in the same
  file and the approved ADR.
- **Decision:** canonical = schema-per-service (ADR-004). App services connect to `DATABASE_URL=…/smartportx` and set
  `SERVICE_SCHEMA=smartportx_{service}` (compose sets this; service application.yml reads `${SERVICE_SCHEMA:smartportx_x}`
  for both JPA default_schema and Flyway schema). init-databases.sh / docker-compose.yml in the repo implement this.
  Schema names = `smartportx_identity, smartportx_container, smartportx_yard, smartportx_gate, smartportx_vessel,
  smartportx_integration, smartportx_notification` (matches DATA-ARCHITECTURE §2 and all ADRs).
- **Consequences:** no per-database provisioning; Flyway migrations run within each service's schema; the repo compose
  supersedes the doc's per-DB DATABASE_URL block (doc fix flagged).
- **Status:** ACCEPTED (2026-08-27, M1.2).

## D-425 — RabbitMQ DLX delivery bindings (implements COMMUNICATION-ARCHITECTURE §3.3–§3.4 literally)
- **Context:** §3.4 configures primary `x-dead-letter-routing-key={queue}.dlq` and retry
  `x-dead-letter-routing-key={queue}`. Topic bindings in §3.3 are wildcard patterns (`container.#`...) which do NOT
  match literal `{queue}` / `{queue}.dlq` routing keys, so dead-lettered/retried messages would drop.
- **Decision:** rabbit-topology.sh binds, in addition to the documented wildcard bindings: (a) each primary queue by its
  literal queue-name key on `smartportx.domain-events` (retry DLX return path), and (b) each `.dlq` queue by its literal
  `<queue>.dlq` key (dead-letter landing path). No new queues or exchanges; pure additive bindings.
- **Status:** ACCEPTED (2026-08-27, M1.3).

## D-426 — RabbitMQ exchange set = COMMUNICATION-ARCHITECTURE §3.2 (6 exchanges)
- **Context:** D-410 shorthand recorded "3 exchanges". Authoritative §3.2 lists 4 topic (`smartportx.domain-events`,
  `smartportx.notifications`, `smartportx.audit-events`, `smartportx.integration-events`) + 2 fanout
  (`smartportx.system-broadcasts`, `smartportx.cache-invalidation`).
- **Decision:** topology = all 6 exchanges; 24 primary queues from §3.3 (+ retry/dlq). rabbit-topology.sh (M1.3) is
  the executable specification; D-410 is superseded on exchange count only.
- **Status:** ACCEPTED (2026-08-27, M1.3).

## D-427 — Shared library ships as a module-dependency "starter" (resolves M2.6 build-friction)
- **Context:** M2.6 offered either a dedicated `smartportx-starter-common` Spring Boot starter artifact or plain module
  dependency. A separate starter artifact adds a second reactor module + version indirection for zero runtime benefit.
- **Decision:** keep a single `smartportx-common` module that BOTH hosts the shared code AND declares `@AutoConfiguration`
  via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Services simply depend on
  common (already done) and the beans auto-register — starter semantics, one artifact.
- **Consequence:** `spring-boot:repackage` and dependency management (root BOM) already cover common; no extra pom phase.
- **Status:** ACCEPTED (2026-08-27, M2.6).

## D-428 — Common web beans are servlet-scoped; reactive gateway is excluded
- **Context:** gateway is Spring Cloud Gateway (WebFlux). Its own WebFlux error handling / Netty filters own the
  reactive boundary; a servlet `@RestControllerAdvice` + `OncePerRequestFilter` must not leak into it.
- **Decision:** `SmartPortxCommonAutoConfiguration` is guarded by `@ConditionalOnWebApplication(type = SERVLET)`.
  Gateway builds/package with common on the classpath but registers none of its web beans. spring-webmvc is `provided`
  in common (services supply it via starter-web); spring-tx is compile (DataIntegrityViolationException → 409).
- **Status:** ACCEPTED (2026-08-27, M2).

--------------------------------------------------------------------------------
## PENDING / OPEN DECISIONS (surface before final gate)
- D-420: Notification service persistence (depends F-407 §11 read at M9).
- D-421: Vessel service exact schema columns (depends F-408 §9 read at M7).
- D-422: Perf scenario thresholds finalization (depends NFR doc + M12.5 measurements).
- D-423: ADR-001…013 ratification sweep (PROPOSED → ACCEPTED) — batched at M13 with reviewer sign-off.