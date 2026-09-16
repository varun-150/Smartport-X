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

## D-441 — Yard block type enum is a superset: API-CONTRACTS §4 wins, DATA-ARCHITECTURE §6.1 amended
- **Context:** API-CONTRACTS §4.1 defines 6 block types (`EXPORT, IMPORT, TRANSIT, REFRIGERATED, DANGEROUS, EMPTY`); DATA-ARCHITECTURE §6.1 defines 4 storage-oriented types (`DRY, REEFER, SOC, HAZMAT`). Traceability maps FR-001/FR-008 to Yard.
- **Decision:** `yard_blocks.block_type` accepts the API-CONTRACTS §4.1 superset (6 values). The §6.1 comment string is a storage-oriented subset — amended at M5.7 doc-fix sweep to list all 6 values. Layout/block responses expose `type` from this enum.
- **Consequences:** FR-001.5/FR-008 block filtering (e.g. `refrigeratedSlots`, `dangerousGoodsAllowed`) derives from block type; IMport/EXPORT semantics flow through the naming only.
- **Status:** ACCEPTED (2026-08-30, M5 planning).

## D-442 — Yard position format is `B{block}-B{bay}-R{row}-T{tier}` (zero-padded, 2-digit) per API-CONTRACTS §4.1
- **Context:** API-CONTRACTS §4.1 positions look like `B01-B01-R01-T01`; DATA-ARCHITECTURE §7.1 `containers.current_location` example is a longer free-text (`'BLOCK-A-BAY-03-ROW-02-TIER-01'`).
- **Decision:** canonical position string = `B{blockCode}-B{bay}-R{row}-T{tier}` with 2-digit zero-padded numbers, e.g. `B01-B01-R01-T01`. `current_location` in container service stores the same token (container already copies it; no cross-format drift). 3+ digit numbers (e.g. bay 120) print unpadded after `B`.
- **Consequences:** layout tiers, `assignSlot` return, and `ContainerService.currentLocation` all share one format; API-CONTRACTS examples are exact. DATA-ARCHITECTURE §7.1 example amended at sweep.
- **Status:** ACCEPTED (2026-08-30, M5 planning).

## D-443 — Yard optimization strategy enum: API-CONTRACTS §4.2 (`FIFO, WEIGHT_CLUSTER, DEPARTURE_CLUSTER, BALANCED`) is canonical
- **Context:** API-CONTRACTS §4.2 defines 4 named strategies; DATA-ARCHITECTURE §6.6 `optimization_history.strategy` comment only lists `'GREEDY','DYNAMIC','ML_PREDICTIVE'`.
- **Decision:** optimization_history.strategy = the API-CONTRACTS §4.2 enum. DATA-ARCHITECTURE §6.6 comment amended at M5.7 sweep to reference the canonical set. Analyzer/optimizer emits these four; `BALANCED` is the D-006 baseline composite.
- **Consequences:** strategy values persisted match the wire contract; no translation layer. `DEPARTURE_CLUSTER` is the primary explainable strategy in M5.5.
- **Status:** ACCEPTED (2026-08-30, M5 planning).

## D-444 — Equipment Management (FR-008.1–4) lands as a yard-owned `yard_equipment` registry + endpoints in M5
- **Context:** REQUIREMENTS.md FR-008.1–4 (vendor-neutral: equipment status, crane scheduling recommendation, downtime handling, moves-per-crane metric) is mapped to Yard by REQUIREMENT-ARCHITECTURE-TRACEABILITY, but DATA-ARCHITECTURE §6 has **no equipment table** and no M5 plan increment names one. User decision (2026-08-30): include equipment management in M5 rather than defer.
- **Decision:** add `yard_equipment` (RR-typed crane registry: `code`, `equipment_type 'RTG'|'ASC'`, `assigned_block_id`, `status 'AVAILABLE'|'ASSIGNED'|'OUT_OF_SERVICE'`, `moves_total` counter, timestamps) + DDL in V1__init.sql. Endpoints: `GET /api/v1/yard/equipment`, `GET/PUT /api/v1/yard/equipment/{equipmentId}` for status/assignment (manual updates, RBAC `yard:write`), `GET /api/v1/yard/equipment/{equipmentId}/moves` (FR-008.4 metric). `OUT_OF_SERVICE` → 409 on assignment attempts + `yard.EquipmentStatusChanged` event (FR-008.3). FR-008.2 rides the M5.5 optimizer: crane→block suggestion returned when `dryRun=false` persist executed moves to block's equipment counter. API-CONTRACTS §4 gains a §4.4 Equipment section; DATA-ARCHITECTURE §6 gains §6.7 yard_equipment — both applied at M5.7 doc-fix sweep.
- **Consequences:** FR-008.1–4 verifiable in M5 with real registry + tests; events align to existing `smartportx.domain-events` transport. Equip/metrics tables extend DATA-ARCHITECTURE §6 — superset DDL pattern same as D-431/D-436.
- **Status:** ACCEPTED (2026-08-30, M5 planning).

## D-445 — Yard authorization is role-based per API-CONTRACTS §4 role columns; the JWT `permissions` claim stays advisory for M5 (vocabulary reconciliation deferred to M10)
- **Context:** M5 realized yard authZ with `@PreAuthorize hasAnyRole` guards (GETs authenticated; PUT
  block / tier-container / equipment `ADMIN|YARD_MANAGER|TERMINAL_OP`; DELETE tier-container + POST optimize
  `ADMIN|YARD_MANAGER`) — matching the API-CONTRACTS §4/§4.4 role columns. Identity still issues a
  dot-vocabulary `permissions` claim (`yards.read`/`yards.write`) that excludes TERMINAL_OP from yard scopes,
  while documentation and D-444 name a colon vocabulary (`yard:write`). See F-420.
- **Decision:** keep yard authZ role-based for the whole slice; do **not** refactor identity claim
  vocabulary now (would ripple through M3/M4 contract-conformance tests for no runtime gain). The
  `permissions` claim is advisory until the gateway consumes it (SR-005 role-filtered routing, FR-006.1
  role-shaped dashboards, M10/M11). PHASE-4-IMPLEMENTATION-PLAN M10 increment gains a reconciliation item
  (permission vocabulary unification or an explicit mapping) — recorded as part of this decision so the
  deferral is not lost.
- **Consequences:** TERMINAL_OP can update yard state at the service (by role) even though its token carries
  no `yards.*` permission — intended for M5; any future gateway/dashboard permission check must wait for the
  M10 reconciliation. Cross-service event payload shape consciously diverges: container events carry
  `JsonNode` payloads (D-438) while yard events carry typed record payloads — both serialize to JSON on the
  shared `smartportx.domain-events` transport; no service consumes another's events in code today, so impact
  is nil; unify at M12 hardening if a cross-service consumer appears.
- **Status:** ACCEPTED (2026-08-31, M5 close-out).

## D-446 — Gate wire identifiers, superset gate schema, and BOTH-quota semantics (M6)
- **Context:** API-CONTRACTS §6 returns prefixed ids (`apt-<uuid>`, `txn-<uuid>`, `quota-<uuid>`) yet accepts
  plain UUIDs for some path parameters; the M6 gate slice must reconcile both on one codec. The gate DDL
  carries columns beyond DATA-ARCHITECTURE §8.1-8.4 (lane assignment and rework data) that API-CONTRACTS
  §6.1/§6.2/§6.3 requires. `BOTH` appointments must reserve exactly one quota bucket without double-booking.
- **Decision:** (1) Identify with a single codec `GateIds` — responses always render prefixed ids
  (`apt-`/`txn-`/`quota-`), paths accept the prefixed form or the bare UUID, malformed → 400
  `malformed_request` with a prefix-aware message. (2) Gate schema is a documented, forward-compatible
  **superset** of DATA-ARCHITECTURE §8.1-8.4: appointments.gate_lane (assigned at booking), appointments.
  special_requirements (JSONB: hazmatCertified/reeferConnection/doubleStack), gate_transactions.gate_lane,
  appointment_quotas.gate_lane (DEFAULT 'ALL') and appointment_quotas.adjustment_reason (audit); the
  DATA-ARCHITECTURE tables remain the canonical base. (3) `BOTH` occupies the outbound (return-leg) quota:
  `AppointmentType.quotaType()` maps BOTH → DROP_OFF, so a double-leg trip reserves one DROP_OFF slot and
  never a separate PICKUP slot. (4) Direction semantics: GateType INBOUND = drop-off, OUTBOUND = pickup.
- **Consequences:** clients may send either id form; responses are always prefixed and stable. The superset
  columns are nullable/defaulted so DATA-ARCHITECTURE readers are unaffected. BOTH-quota is conservative
  (no inbound+outbound double booking) and matches the API-CONTRACTS §6.2 model. GateRules centralizes the
  cross-service rule set (doc requirements, PICKUPABLE container states, gate direction mapping).
- **Status:** ACCEPTED (2026-08-31, M6 close-out).

## D-447 — Vessel superset schema + vocabulary reconciliation (M7)
- **Context:** DATA-ARCHITECTURE §9 and API-CONTRACTS §7 describe the vessel registry, schedule, and
  discharge-plan surfaces with divergent vocabulary (vessel_type, priority, schedule status, container
  status, and a numerical column-type mismatch). F-423 enumerates the deltas. Only one schema can be
  built and validated (`ddl-auto: validate`).
- **Decision:** (1) The vessel DDL is a documented, forward-compatible **superset** of DATA-ARCHITECTURE
  §9.1–9.4 merged with API-CONTRACTS §7 columns: vessels add gross_tonnage/flag/operator/call_sign;
  vessel_schedules add voyage_number/priority/port_of_call/next_port/estimated_teus/actual_teus;
  discharge_plans add voyage_number; discharge_containers add yard_target/priority/actual_yard_position/
  equipment_id/operator. (2) Vocabulary reconciliation — canonical values win where aliases collide:
  vessel_type AC enum `CONTAINER|BULK|TANKER|GENERAL|ROLL_ON_ROLL_OFF` (DA "RO-RO" → ROLL_ON_ROLL_OFF);
  priority `HIGH|MEDIUM|LOW`, default MEDIUM (`NORMAL` accepted as MEDIUM alias); plan status
  `PLANNED|IN_PROGRESS|COMPLETED` (DA DRAFT/APPROVED collapsed); discharge container status
  `PLANNED|DISCHARGED|IN_TRANSIT|STACKED|MISROUTED` (DA DISCHARGING → IN_TRANSIT alias);
  `OFF_VESSEL = {DISCHARGED, IN_TRANSIT, STACKED}`. (3) Vessel dimensions/gross_tonnage and container
  weight are Java double/Double, so DDL columns are DOUBLE PRECISION (float8) — NOT NUMERIC — to satisfy
  `ddl-auto: validate` (F-423). (4) Plan completes when every container is off-vessel (i.e. at the last
  container's DISCHARGED step), matching the DA/AC completion semantic.
- **Consequences:** superset columns are nullable/defaulted so DATA-ARCHITECTURE readers are unaffected.
  Aliases are accepted on input and normalized to canonical values on output. Resolves the M7 part of
  pending D-421 (vessel exact schema); the remaining D-421 scope is folded into this decision.
- **Status:** ACCEPTED (2026-08-31, M7 close-out).

## D-448 — Advisory best-effort cross-service propagation; fail-closed planning verification (M7)
- **Context:** the discharge flow must notify the container service (changeStatus on DISCHARGED) and the
  yard service (placeContainer on STACKED with a yard position). Resilience4j starter is not resolvable
  in the offline local `.m2` (same constraint as the M6 WireMock finding), so no circuit-breaking starter
  is available. Planning-time container verification must decide whether an unverifiable registry blocks
  the plan.
- **Decision:** (1) Cross-service propagation at DISCHARGED/IP (container status) and STACKED (yard
  placement) is **advisory / best-effort**: log + swallow transport failures, fail-open — a terminal
  update never rolls back because a downstream call failed. (2) Planning-time container verification is
  **fail-closed**: `ContainerApiClient.findByContainerNumber` — 404 → Optional.empty → 422
  `container_not_registered`; transport/5xx → `ContainerUnavailableException` → 422
  `container_registry_unavailable`; a plan is never accepted without knowing its containers exist.
  (3) Substitution: hand-rolled HTTP clients (`HttpContainerApiClient`, `HttpYardApiClient`) wired
  `@ConditionalOnMissingBean` so tests inject `@MockitoBean` interfaces; documented as the offline
  Resilience4j substitute (per F-419/`F-423` substitution pattern from M6).
- **Consequences:** downstream outages do not corrupt a completed discharge, but the container/yard
  services may be momentarily out of sync (reconciled by their own trusted state); planning is
  conservative and refuses to plan unknown containers. Rollback-to-Resilience4j is deferred until a
  resolvable starter exists (tracked alongside R-007).
- **Status:** ACCEPTED (2026-08-31, M7 close-out).

## D-449 - Integration endpoint surface: API-CONTRACTS \u00a78 canonical superset (M8)
- **Context:** two authoritative sources define the integration REST surface. API-CONTRACTS \u00a78 is our canonical
  contract and lists `POST /api/v1/integration/edifact/parse`, `iot/data`, and `customs/status`, plus
  `GET/PUT customs/status`. INTEGRATION-ARCHITECTURE \u00a79 offers a descriptive superset
  (`edifact/inbound`, `iot/ingest`, `customs/submit`, `customs/status/{containerNumber}`). They overlap but
  disagree on paths and on whether customs status is read/write at the same URL.
- **Decision:** implement the **API-CONTRACTS \u00a78 canonical surface as the shipped superset** and record the
  divergence as a finding (F-425). This means `POST /api/v1/integration/edifact/parse`, `POST /api/v1/integration/
  iot/data`, and a `customs` group (`POST /status` submit, `GET /status/{containerNumber}`, `POST
  /status/{containerNumber}/review`, `POST /status/{containerNumber}/release`, `GET
  /gate-out/{containerNumber}/validate`). The INTEGRATION-ARCHITECTURE \u00a79 spelling (`edifact/inbound` etc.) is
  NOT exposed; INTEGRATION-ARCHITECTURE \u00a79 is amended/superseded by the API-CONTRACTS surface (D-447
  superset-DDL pattern applied to the API surface). Authorization: write endpoints (`parse`, `iot/data`, customs
  submit/review/release) require `ADMIN|TERMINAL_OP` or `integration:write`; read endpoints (`GET messages/{id}`,
  customs status, gate-out validate) use `isAuthenticated()`.
- **Consequences:** one definitive endpoint set for clients and contract tests; readers of INTEGRATION-ARCHITECTURE
  \u00a79 must defer to API-CONTRACTS \u00a78. No behavioral gap: every documented operation has a mapped endpoint.
- **Status:** ACCEPTED (M8 close-out).

## D-450 - EDIFACT parser is a hand-rolled segment parser (offline StAEDI substitution) (M8)
- **Context:** FR-011 requires parsing COPRAR/COPARN EDIFACT inbound interchanges. The natural library
  `io.xlate:staedi` is NOT present in the local offline `.m2` (same constraint class as the M6 WireMock and
  M7 Resilience4j findings), so it cannot be added without breaking the reactor build.
- **Decision:** implement a **hand-rolled EDIFACT tokeniser + semantic mapper** (`EdifactTokeniser`,
  `EdifactSegment`, `EdifactParser`) covering exactly the subset the platform consumes: UNB/UNH/UNT/UNZ
  envelope validation, BGM (message number), NAD (carrier/country), TDT (vessel), LOC (port/terminal),
  DTM (dates, UTC), EQD (containers), RFF. This is a documented **substitution**, not silent omission,
  and is recorded under the established substitution pattern (D-008 jurisdiction, D-448 precedent). DTM
  time/date composites resolve against UTC (format 203/204). Unsupported message types and missing
  envelope segments produce RFC 9457 400s (`unsupported_message_type` / `validation_failed`).
- **Consequences:** no external StAEDI dependency; the parser is deliberately a pragmatic subset and does not
  claim full UN/EDIFACT compliance. Rollback-to-StAEDI is deferred and tracked alongside R-007.
- **Status:** ACCEPTED (M8 close-out).

-------------------------------------------------------------------------------
## PENDING / OPEN DECISIONS (surface before final gate)
- D-420: Notification service persistence (depends F-407 §11 read at M9).
- D-421: Vessel service exact schema columns — RESOLVED by D-447 (M7 superset DDL + vocabulary) and the
  VesselSchemas DDL; folded into D-447.
- D-422: Perf scenario thresholds finalization (depends NFR doc + M12.5 measurements).
- D-423: ADR-001…013 ratification sweep (PROPOSED → ACCEPTED) — batched at M13 with reviewer sign-off.