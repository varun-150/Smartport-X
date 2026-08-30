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

## D-429 — Canonical RBAC role set = API-CONTRACTS §3.2 (5 roles)
- **Context:** the doc set disagrees on role names. API-CONTRACTS §3.2/§3.3 + COMMUNICATION-ARCHITECTURE gateway
  role-routes + ARCHITECTURE-OVERVIEW §5.4 agree on `ADMIN, TERMINAL_OP, YARD_MANAGER, GATE_OPERATOR, READONLY`;
  DATA-ARCHITECTURE §5.1/5.2 lists `OPERATOR/GATE_AGENT/YARD_PLANNER/VESSEL_PLANNER/VIEWER`; ADR-009 sketches legacy
  PORT_* names. Contract must win.
- **Decision:** canonical set = `ADMIN, TERMINAL_OP, YARD_MANAGER, GATE_OPERATOR, READONLY`. `users.role` DEFAULT becomes
  `READONLY` (least privilege). VESSEL_PLANNER duties fold into TERMINAL_OP. DATA-ARCHITECTURE §5.1/5.2 amended.
- **Status:** ACCEPTED (2026-08-27, M3).

## D-430 — Single-role RBAC; `users.role` authoritative + `user_roles`/`roles` mirror
- **Context:** DATA-ARCHITECTURE defines both a `users.role` column (5.1) and a `roles`+`user_roles` M2M (5.2/5.3).
  API-CONTRACTS models exactly one role per user (create/read/update `role` scalar; no "set" endpoint).
- **Decision:** single active role per user. `users.role` is the authoritative value (matches contract); `roles` is a
  metadata table seeded with the 5 D-429 roles; `user_roles` keeps exactly one row per user (mirror, same transaction)
  so the M2M can grow to multi-role without schema change. Role change = update `users.role` + rewrite the row.
- **Status:** ACCEPTED (2026-08-27, M3).

## D-431 — Superset DDL for identity V1 (corrects gaps in DATA-ARCHITECTURE §5.1/5.4)
- **Context:** refresh-token rotation + reuse detection (ADR-009) need a session family and a used-marker, neither in
  §5.4; API-CONTRACTS emits `displayName` and `lastLoginAt`, neither in §5.1.
- **Decision:** V1 migration is a superset — `users` adds `display_name`, `last_login_at`; `refresh_tokens` adds
  `family_id UUID NOT NULL` + `used_at TIMESTAMPTZ`; `token_hash` UNIQUE. Everything else matches §5.1–5.5.
- **Status:** ACCEPTED (2026-08-27, M3).

## D-432 — Identity `GET /users` uses cursor pagination (per API-CONTRACTS)
- **Context:** API-CONTRACTS §3.2 specifies cursor/limit/nextCursor/hasMore/totalCount; M2 `PageRequest`/`PageResult`
  are offset/page-based and will serve contracts that specify `page`/`sort`.
- **Decision:** identity users list = cursor-based (`limit`, opaque base64 `cursor`, `filter[role|status|search]`,
  sort fixed `createdAt,desc` default). M2 DTOs are reused where the target contract names page/sort (yard, container).
- **Status:** ACCEPTED (2026-08-27, M3).

## D-433 — `PUT /api/v1/auth/password` (authenticated self-service) added
- **Context:** password-history reuse policy needs a change-password path; API-CONTRACTS §3 has none.
- **Decision:** add authenticated `PUT /api/v1/auth/password {currentPassword,newPassword}` → 204 on success; verifies
  current BCrypt, enforces policy, checks last-5 history, rotates hash, revokes other refresh-token families.
  Recorded as a contract extension (API-CONTRACTS changelog), not a deviation.
- **Status:** ACCEPTED (2026-08-27, M3).

-## D-434 — Invalid container state transitions → 422 `invalid_status_transition` (resolves F-415)
- **Context:** M4 plan DoD said "rejected → 409"; API-CONTRACTS §2.6/§5.2 defines business-rule violations as 422.
- **Decision:** the status endpoint returns 422 for any transition outside the allowed table, RFC 9457 body
  `urn:smartportx:problems:invalid_status_transition`, detail `Cannot transition container from X to Y`. Prior/new
  state carried in detail (+ `errors` where applicable). 409 stays reserved for duplicates/state conflicts per §2.6.
  Plan M4.4 DoD wording amended.
- **Status:** ACCEPTED (2026-08-30, M4, user-confirmed).

## D-435 — Container implementation vocabulary = DATA-ARCHITECTURE §7.1/§7.2 + D-411 (resolves F-414)
- **Context:** API-CONTRACTS §5 still documents the D-411-superseded EXPECTED→…→GATE_OUT machine, 8 legacy movement
  types, and aggregation-only fields (customsStatus, currentBlockCode).
- **Decision:** M4 code/schema use DATA-ARCHITECTURE §7.1 status enum (8) and §7.2 movement types (5). API-CONTRACTS §5
  is amended to the canonical vocabulary during the end-of-M4 doc-fix sweep (status/movement tables, event log tokens,
  container-type comment, port 8084, aggregation note). No code imports the legacy enum values.
- **Status:** ACCEPTED (2026-08-30, M4.0, user-confirmed sweep).

## D-436 — Container first-cut contract surface & superset DDL (M4)
- **Context:** M4 must honor §5 REST shapes the contract body implies without inventing data the service does not own.
- **Decision:**
  - Superset DDL (D-431 convention) on `containers`: `max_payload`, `bill_of_lading`, `origin_port`,
    `destination_port`, `expected_arrival`, `flash_point`, `emergency_contact` (contract-bodied DG fields flattened
    to §7.1 columns), `removed_at TIMESTAMPTZ` (soft delete backing `ContainerRemoved`).
  - Container types: 8 (`20DC/40DC/40HC/45HC/20RF/40RF/20TK/40TK`) per API validation table; §7.1 comment updated at sweep.
  - Event log (`container_events.event_type`) stores contract-shaped tokens `REGISTERED/STATUS_CHANGE/MOVEMENT/REMOVED`;
    Rabbit envelopes use canonical names `container.ContainerRegistered|ContainerStatusChanged|ContainerMoved|ContainerRemoved`.
  - `customsStatus`/`currentBlockCode` are not returned by container (owned by integration/yard); listed as gateway
    aggregation, documented in API-CONTRACTS amendment.
  - Contract extensions (D-433 convention): `POST /api/v1/containers/{id}/movements` (movement recording), 
    `GET /api/v1/containers/number/{containerNumber}` (plan M4.6 get-by-number), 
    `GET /api/v1/containers/{id}/status-history` (plan M4.6 status history). Recorded in API-CONTRACTS changelog at sweep.
  - `POST /api/v1/containers/search` honors container-owned filters; unsupported cross-service filters
    (e.g. `customsStatus`) → 400 `invalid_query` (honest, never silently ignored).
  - Container port canonical 8084 (DEPLOYMENT-ARCHITECTURE/plan; API-CONTRACTS §intro 8083 amended at sweep).
  - ISO 6346 check digit enforced at register (plan M4.2) in addition to the §5.1 `[A-Z]{4}[0-9]{7}` pattern.
- **Status:** ACCEPTED (2026-08-30, M4.0).

## D-437 — Container M4 runtime semantics (supplements D-436)
- **Context:** several behavioral corners have no explicit doc; decisions prevent code-time waffling.
- **Decision:**
  - `register` — ISO 6346 check-digit failure → 400 `validation_failed`
    (in addition to the `[A-Z]{4}[0-9]{7}` Bean-validation pattern).
  - Duplicate (incl. soft-removed) container number → 409 `conflict` (UNIQUE is global; re-registration impossible).
  - Delete is a **soft delete** (`removed_at` set): list/get 404 afterwards; movements/events/status-history remain
    replayable (audit). Event log token `REMOVED` + envelope `container.ContainerRemoved`.
  - Same-status → 422 (self-transitions are not in the allowed table, incl. `HELD→HELD`).
  - Movement recording does **not** auto-transition status (decoupled; gate/vessel/yard orchestrate via status endpoint
    in later milestones). Movement `to_location` non-blank updates `current_location` (tracking semantic).
  - Status-change `location` is optional and always updates `current_location` if provided (legacy "required for
    POSITIONED" has no canonical equivalent — F-414).
  - PUT update is partial: only non-null fields applied; `dangerousGoods` presence sets flag true and flattens
    `unNumber/properShippingName/hazardClass/packingGroup/flashPoint/emergencyContact`; containerNumber immutable.
  - `POST /containers/search`: limit-only page (nextCursor null, hasMore false); unsupported contract fields
    `customsStatus` / `location` → 400 `invalid_query` with an explicit ownership message (never silently ignored,
    honoring F-412/D-436); sort allowed on `createdAt|weight|containerNumber`.
  - GET list sort restricted to `createdAt,asc|desc` (default desc) mirroring identity/D-432; cursor is base64
    `createdAt:id`; `filter[dangerousGoods]`, `filter[location]` added as container-owned filters.
  - Movements/events lists default `limit=50` (per §5.3); history replay endpoints accessible post-soft-delete.
  - Write operations guard: `hasAnyRole('ADMIN','TERMINAL_OP')`; reads any authenticated; no ownership checks
    (containers are shared operational data — IDOR scope = role-based write guard).
- **Status:** ACCEPTED (2026-08-30, M4.0).

## D-438 — Container event payload is a Jackson `ObjectNode` (JsonNode), not the typed payload records
- **Context:** D-436 specified the event log + envelope shape. Implementation needed a concrete payload type
  for `container_events.event_data` and the published `DomainEvent` payload.
- **Decision:** `ContainerEventLogger` builds a Jackson `ObjectNode` per event and publishes `DomainEvent<JsonNode>`
  (and persists it via `ContainerEvent`). The typed payload records in `ContainerDomainEvents` are retained as
  reference/vocabulary only and are **not** serialized. Consumers read the payload by path
  (e.g. `data.previousStatus`, `data.movementType`), matching the §5.3 sample JSON.
- **Consequences:** single JSON representation in DB and over the wire; no payload-DTO drift; schema-agnostic consumption.
- **Status:** ACCEPTED (2026-08-30, M4.8).

## D-439 — Search `location` filter is container-owned and supported; supersedes the D-437 rejection clause
- **Context:** D-437 listed `customsStatus` and `location` as unsupported search filters → 400. Implementation
  found `location` CAN be owner-filtered: `block` prefix-matches container-owned `currentLocation`, and
  `fromDate`/`toDate` bound to `createdAt` range (the container's arrival into the system).
- **Decision:** `POST /containers/search` supports `status`, `type`, `owner`, `containerNumber` (wildcard),
  `weight` (min/max range), `dangerousGoods`, `location{block,fromDate,toDate}`, `sort` on
  `createdAt|weight|containerNumber`; `customsStatus` remains **rejected** with 400 `invalid_query`
  (cross-service data, never silently ignored). `location.fromDate/toDate` → `createdAt` range is an ASSUMPTION
  (location history is owned by yard later; flagged for VIVA defense).
- **Consequences:** D-437's "`location` → 400" clause is superseded by this decision for `location` only.
- **Status:** ACCEPTED (2026-08-30, M4.8).

## D-440 — `EventResponse.source` is `null` (source not persisted in the container event log)
- **Context:** the event log table has no origin column; `EventResponse` exposes `source` per §5.3 shape.
- **Decision:** container event log does not persist `source`; `GET /{id}/events` returns `"source": null`.
  Consumers needing origin attribution must read the RabbitMQ envelope's `source` field
  (`smartportx.container.source` = `smartportx-container`).
- **Consequences:** §5.3 sample updated to show `source: null`; no false attribution data persisted.
- **Status:** ACCEPTED (2026-08-30, M4.8).

-------------------------------------------------------------------------------
## PENDING / OPEN DECISIONS (surface before final gate)
- D-420: Notification service persistence (depends F-407 §11 read at M9).
- D-421: Vessel service exact schema columns (depends F-408 §9 read at M7).
- D-422: Perf scenario thresholds finalization (depends NFR doc + M12.5 measurements).
- D-423: ADR-001…013 ratification sweep (PROPOSED → ACCEPTED) — batched at M13 with reviewer sign-off.