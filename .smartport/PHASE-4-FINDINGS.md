# SMARTPORTX — PHASE 4 FINDINGS

> Living log of findings/disagreements discovered during Phase 4, recorded as they occur.
> Each finding: observed fact → impact → resolution (decision ID) → status. Source-of-truth rule
> (§3 of brief): any drift from approved docs triggers a finding + decision, never silent change.

--------------------------------------------------------------------------------
## F-401 — RabbitMQ topology collision (COMMUNICATION-ARCHITECTURE vs SERVICE-CATALOG vs DEPLOYMENT-ARCHITECTURE)
- **Observed:** COMMUNICATION-ARCHITECTURE.md defines 3 topic exchanges (`smartportx.domain-events`,
  `smartportx.notifications`, `smartportx.audit-events`), queues `{service}.{event-type}`, DLQ `{queue}.dlq`,
  retry `{queue}.retry`. SERVICE-CATALOG §4.2 sketches a single exchange `smartportx.events` with DLX
  `smartportx.events.dlx` and routing key `{service}.{event-type}`. DEPLOYMENT-ARCHITECTURE mentions
  `smartportx.dlx`.
- **Impact:** dual topologies would silently fragment events (consumers bind wrong exchange → lost messages).
- **Resolution:** D-410 (adopt COMMUNICATION-ARCHITECTURE as canonical; SERVICE-CATALOG §4.2 note marked superseded).
- **Status:** RESOLVED.

## F-402 — Container status enum collision (DATA-ARCHITECTURE vs ARCHITECTURE-OVERVIEW)
- **Observed:** DATA-ARCHITECTURE §7.1 lifecycle enum: REGISTERED, AT_GATE, IN_YARD, LOADED, DISCHARGED,
  AT_GATE_OUT, DEPARTED, HELD. ARCHITECTURE-OVERVIEW (CH-1 summary) list: EMPTY, FULL, IN_TRANSIT, AT_YARD,
  AT_GATE, ON_VESSEL, IN_CUSTOMS — incompatible, missing gate-in/out and HELD.
- **Impact:** risk of two enums in code/schema; workflow (HELD blocks gate-out) only expressible in §7.1.
- **Determination:** §7.1 is the schema source-of-truth and matches WORKFLOWS.md sequences; overview is stale summary.
- **Action:** canonical = §7.1; update ARCHITECTURE-OVERVIEW enum + CH-1 to match (flagged as doc fix).
- **Status:** RESOLVED (D-411).

## F-403 — User-role naming collision (D-013/SECURITY vs DATA-ARCHITECTURE vs COMMUNICATION-ARCHITECTURE vs RESILIENCE)
- **Observed:** 5-role decision D-013 (Admin, Terminal Operator, Yard Manager, Gate Operator, Read-Only) is
  rendered differently: DATA-ARCHITECTURE §5.2 enum = ADMIN, OPERATOR, GATE_AGENT (+ CUSTOMS in §10);
  COMMUNICATION-ARCHITECTURE role-route table = ADMIN, TERMINAL_OP, YARD_MANAGER, GATE_OPERATOR, READONLY;
  ARCHITECTURE-OVERVIEW = TERMINAL_OPERATOR; RESILIENCE-ARCHITECTURE adds ACTUATOR_READ.
- **Impact:** JWT role claims, @PreAuthorize strings and WS role checks must agree or authz gaps appear.
- **Resolution:** canonical claims = ADMIN, TERMINAL_OPERATOR, YARD_MANAGER, GATE_OPERATOR, READ_ONLY (+ ACTUATOR_READ
  for health); CUSTOMS is a gate *condition*, not a user role. Legacy spellings mapped/retired (D-412).
- **Status:** RESOLVED.

## F-404 — EDIFACT parser library (carryover F-004 from Phase 3)
- **Observed:** architecture text allows hand-rolled fallback; JEDII researcher result (Phase 3) cited a stale
  library. 2026-08-27 web research: **StAEDI** (`io.xlate:staedi`, Apache-2.0, Maven Central, active, streaming
  EDIFACT/X12/TRADACOMS) is a maintained parser suited to COPRAR/COPARN.
- **Impact:** parser choice is implementation-level (INTEGRATION-ARCHITECTURE §3.2 is library-agnostic), but must
  be explicit to avoid a reimplementation rabbit hole.
- **Resolution:** adopt StAEDI 1.26.x for inbound parse; transform to domain events in-service (D-413).
- **Status:** RESOLVED.

## F-405 — Requirement count drift (carryover F5-005)
- **Observed:** multiple docs claim 38 FR / 24 NFR / 22 SR; actual canonical files hold 46 FR / 16 NFR groups / 36 SR.
- **Impact:** completion reporting would use wrong denominators; scoring (§53) and traceability (§54) distorted.
- **Resolution:** canonical counts = 46/16/36 (D-414); stale docs corrected during M12.7 sweep.
- **Status:** RESOLVED (document sweep deferred to M12.7 close-out).

## F-406 — Phase numbering realignment
- **Observed:** user brief (2026-08-27) defines "Phase 4 = Implementation + Integration + Continuous Verification";
  ROADMAP had labeled Phase 4 "Implementation Planning" and Phase 5 "Implementation".
- **Impact:** two-phase plans would double-report work.
- **Resolution:** single Phase 4 as per brief; ROADMAP Phase 4 points to this plan; old Phase 5-implementation
  label rolled into this Phase's later milestones and split into 5.A (hardening) / 5.B (deployment) at the next gate (D-415).
- **Status:** RESOLVED.

## F-407 — Notification schema coverage gap (data catalog)
- **Observed:** DATA-ARCHITECTURE documented sections 1–10; notification service persistence table not yet
  confirmed in the catalog (§11/§12 unread range).
- **Impact:** M9 cannot finalise its Flyway until we confirm whether notifications persist.
- **Action:** read DATA-ARCHITECTURE remaining sections at M9 kickoff; resolution recorded then (pending → NOT BLOCKED,
  kept visible).
- **Status:** OPEN (dependencies M9.1).

## F-408 — Vessel service data schema gap (data catalog)
- **Observed:** service table inventory (Phase 3 summary) lists vessels & discharge plans as planned tables but
  exact column set not footnoted in the plan; DATA-ARCHITECTURE §9 is authoritative at M7.
- **Action:** read DATA-ARCHITECTURE §9 fully at M7; no code before schema read. Status flips only then.
- **Status:** OPEN (dependencies M7.1).

## F-409 — Spring Cloud train/Boot mismatch (2025.1.x targets Boot 4)
- **Observed:** M0 pinned `spring-cloud-dependencies 2025.1.3`; its POM declares `spring-boot.version 4.0.8`. This
  train (2025.1.x / Cloud 5.0.x) is built for Spring Boot 4, but the approved stack mandates Boot 3.5.x. Build failed:
  `spring-cloud-starter-gateway` version missing under that BOM.
- **Impact:** unbounded "version not managed" errors across gateway/eureka starters; wrong framework generation.
- **Resolution:** pinned `2025.0.3` (Spring Cloud Northfields generation for Boot 3.5, verified: netflix 4.3.3 etc.);
  full reactor then built 11/11. Parent pom now correct (D-424 context).
- **Status:** RESOLVED (2026-08-27, M0).

## F-410 — init-databases conflict: per-DATABASE vs schema-per-service (DEPLOYMENT-ARCHITECTURE vs ADR-004)
- **Observed:** DEPLOYMENT-ARCHITECTURE §3.3 init script + §3.1 DATABASE_URLs create seven DATABASES; ADR-004,
  DATA-ARCHITECTURE §2, and ARCHITECTURE-OVERVIEW §5 build on ONE database with seven schemas.
- **Impact:** two provisioning schemes → split-brain installs and JPA/Flyway schema misconfiguration.
- **Resolution:** schema-per-service wins (D-424). `scripts/init-databases.sh` now creates the `smartportx` database +
  7 schemas idempotently; repo compose uses `DATABASE_URL=…/smartportx` + `SERVICE_SCHEMA`. Verified twice on local
  Postgres 17.11 (E-022).
- **Status:** RESOLVED.

## F-411 — Windows `bash` resolves to WSL (blocked update) during M1 verification
- **Observed:** running `bash` on PATH invoked WSL with "must be updated". Git Bash at
  `C:\Program Files\Git\bin\bash.exe` works (used for init-databases.sh runs).
- **Impact:** scripts referencing `bash` in docs/tests must note the dev-machine PATH caveat; CI runs on Linux so
  unaffected.
- **Action:** script headers/doc say "Git Bash (or WSL)"; no repo change required beyond documenting.
- **Status:** RESOLVED (workaround documented; R-007 still governs Docker/WSL2).

## F-412 — CI backend job had no database for integration tests
- **Observed:** GitHub Actions runner is empty; identity (and future service) integration tests need a live
  Postgres with the `smartportx` database + schemas. The backend job ran `mvn clean verify` with no DB service,
  so M3 integration suites could never pass in CI.
- **Impact:** no regression signal from PRs; CI green claim would be false.
- **Resolution:** add `services: postgres:17` (postgres/postgres, port 5432, pg_isready health) and an
  `Init databases (schemas per service)` step that installs `postgresql-client`, sets PGHOST/PGPASSWORD and runs
  `scripts/init-databases.sh` (idempotent, proven E-022). docker/scan stages remain `if: false` until M12.
- **Status:** RESOLVED (workflow + script verified statically; first green GitHub Actions run lands on push).

## F-413 — Identity `GET /users` first-cut drifted from API-CONTRACTS §3.2
- **Observed:** at M3.7 self-check the implementation bound bare query params `role`/`status`/`search` and
  defaulted list order to ASCENDING; API-CONTRACTS §3.2 (with D-432) mandates `filter[role]`, `filter[status]`,
  `filter[search]` and a `sort` fixed on `createdAt,desc` by default.
- **Impact:** contract-faithful clients would send `filter[...]`/rely on desc and get wrong results or 400s.
- **Resolution:** controller binds `filter[...]` names + `sort`; `UserQueryService` added ASC/DESC keyset
  predicates with DESC default; unsupported sort → 400 `invalid_query` (InvalidQueryException, RFC 9457).
  Locked by 8-test conformance suite (E-025).
- **Status:** RESOLVED (2026-08-30, M3.7).

-## F-414 — API-CONTRACTS §5 lifecycle vocabulary diverges from canonical DATA-ARCHITECTURE §7 (D-411)
- **Observed:** at M4.0 contract read, API-CONTRACTS §5 (Container Service) still carries the D-411-superseded
  vocabulary: state machine `EXPECTED → RECEIVED → IN_YARD → POSITIONED → READY_FOR_PICKUP → LOADED → RELEASED →
  GATE_OUT` (§5.2, line 1685), create response `"status": "EXPECTED"`, 8 movement types incl. `VESSEL_LOADING`,
  `YARD_PLACEMENT`, `RESHUFFLE`, `POSITION_CHANGE`, `TRANSSHIPMENT` (§5.3) — while DATA-ARCHITECTURE §7.1 (canonical
  per D-411) defines REGISTERED/AT_GATE/IN_YARD/LOADED/DISCHARGED/AT_GATE_OUT/DEPARTED/HELD and §7.2 defines 5
  movement types (GATE_IN, GATE_OUT, YARD_MOVE, VESSEL_LOAD, VESSEL_DISCHARGE). §5.3 event log exposes
  STATUS_CHANGE/MOVEMENT/CUSTOMS_UPDATE/IOT_READING/DOCUMENT_UPLOADED/ALERT/MANUAL_NOTE tokens; GET /{id} response
  embeds `customsStatus` + `currentBlockCode` (integration/yard-owned data). Container port declared 8083 in §intro
  (lines 68/115) vs canonical 8084. §7.1 container-type comment lists 6 codes; API validation table lists 8 (adds
  20TK/40TK — valid ISO tank types).
- **Impact:** implementing §5 literally would re-introduce the two-enum conflict D-411 resolved and force container
  to serve data it does not own (customs, block codes).
- **Resolution:** implementation uses DATA-ARCHITECTURE §7.1/§7.2 + D-411 vocabulary; container types = 8 (superset
  20TK/40TK per API validation table); event log exposes REGISTERED/STATUS_CHANGE/MOVEMENT/REMOVED tokens (contract
  §5.3 shape); aggregation-only fields not returned. API-CONTRACTS §5 amended at end-of-M4 doc-fix sweep (D-435).
- **Status:** RESOLVED (2026-08-30, D-435).

## F-415 — M4 plan DoD says invalid transitions → 409; API-CONTRACTS §2.6/§5.2 says 422
- **Observed:** M4.4 DoD (PHASE-4-IMPLEMENTATION-PLAN) reads "rejected → 409 with prior/new state in error body".
  API-CONTRACTS §2.6 table maps 422 `/errors/business-rule-violation` to "valid syntax but violates business logic"
  and §5.2 errors show 422 for `LOADED`→`EXPECTED`. Identity precedent (D-432) had the contract win on HTTP semantics.
- **Impact:** two governing docs disagree on a client-visible status code.
- **Resolution:** invalid state transition → 422 `invalid_status_transition` (D-434); plan DoD wording amended to match.
- **Status:** RESOLVED (2026-08-30, D-434).

## F-416 — Container `application.yml` had a top-level `jackson:` block (malformed YAML nesting)
- **Observed:** at the first M4.8 integration test run, 36/50 tests errored before execution — context load
  failed with `... while parsing a block mapping in '.../application.yml'`. The `jackson:` key sat at column 0
  instead of being nested under `spring:`.
- **Impact:** `BaseContainerIntegrationTest` could not boot the `ApplicationContext` at all; the whole container
  integration suite was red. Identity's equivalent file was correctly nested, so this was a generator-scaffold slip.
- **Resolution:** re-indented the block to `spring.jackson.default-property-inclusion: non_null` (SC-11).
  Full suite then loaded the context.
- **Status:** RESOLVED (2026-08-30, M4.8, E-026).

## F-417 — Controller-layer `@PreAuthorize` denials degraded to 500 instead of 403 `access_denied`
- **Observed:** 3/50 tests (reader role calling register/status-change/record-movement) returned 500. Spring
  Security 6.3+ throws `AuthorizationDeniedException` from `@PreAuthorize` at the controller layer; common
  `GlobalExceptionHandler`'s `Throwable` catch-all mapped it to 500.
- **Impact:** a genuine authorization failure became an internal error; RFC 9457 `access_denied` (403) never
  reached the client; identity parity broken.
- **Resolution:** added `ContainerExceptionAdvice` (`basePackages=com.smartportx.container.web`) with
  `@ExceptionHandler(AccessDeniedException.class)` → 403 ProblemDetail `access_denied`, mirroring identity's
  `IdentityExceptionAdvice` (SC-12). Note `AuthorizationDeniedException` extends `AccessDeniedException`.
- **Status:** RESOLVED (2026-08-30, M4.8, E-026).

## F-418 — `TestContainerNumbers` fixed `TSTU100000+` sequence collided across surefire runs
- **Observed:** the second full test run produced 22 duplicate-registration 409 failures. The test-number helper
  used a fixed base (`TSTU` + `100000+n`), so rows persisted in the shared `smartportx_container` schema by earlier
  runs (no `@Transactional`, per TEST-STRATEGY) collided with the client-generated sequence.
- **Impact:** flaky/serial-only suite; re-running the same milestone tests would fail on any populated dev DB.
- **Resolution:** `TestContainerNumbers.next()` now seeds a per-JVM random 6-digit serial
  (`10_000 + ThreadLocalRandom.current().nextInt(0, 900_000)`) with wraparound via `reset()` when the 999_999
  ceiling is crossed (SC-13). Tests keep self-isolating via unique numbers/owners.
- **Status:** RESOLVED (2026-08-30, M4.8, E-026).

## F-419 — IMPLEMENTATION-STATUS FR roll-up rows for M5 do not match REQUIREMENTS.md FR-008/FR-009
- **Observed:** IMPLEMENTATION-STATUS §3 lists "FR-008 yard slots + movements" and "FR-009 optimization w/ explanation" for M5. REQUIREMENTS.md FR-008 is **Equipment Management** (FR-008.1–4, cranes), and yard slots/optimization are actually **FR-001** (Container Yard Management, FR-001.1–8) and **FR-002** (AI-Driven Stacking Optimization, FR-002.1–6). The roll-up table's FR numbering and the REQUIREMENTS.md numbering are different schemes; the M5 milestone slice was labeled against the wrong row IDs.
- **Impact:** milestone→requirement trace read literally would claim M5 verifies "yard slots" under FR-008 while FR-009 doesn't exist in REQUIREMENTS.md; equipment requirements (real FR-008) would appear unverified.
- **Resolution:** M5 close-out will reference **FR-001 + FR-002 + FR-008** as the requirement set verified for the yard milestone; IMPLEMENTATION-STATUS §3 rows updated at M5.7 to point at the canonical IDs (F-419 noted, applied in close-out row wording). No code impact — purely a traceability-label fix.
- **Status:** RESOLVED (2026-08-30, M5 planning; reflected in M5 close-out).

## F-420 — JWT `permissions` claim vocabulary does not match the documented RBAC scheme; TERMINAL_OP has no yard permission
- **Observed:** identity `RolePermissions` (smartportx-identity → JWT `permissions` claim) issues a dot
  vocabulary: `yards.read`, `yards.write` (ADMIN, YARD_MANAGER), plus `vessels.*`, `cargo.*`, `gates.*`,
  `users.*`, `reports.*`. The documentation + decisions reference a colon vocabulary `yard:read` /
  `yard:write` (API-CONTRACTS §2 sample, D-444 "RBAC `yard:write`", ARCHITECTURE-OVERVIEW §5,
  SECURITY-ARCHITECTURE role–permission matrix). Separately, TERMINAL_OP — which API-CONTRACTS §4 lets update
  blocks / tier-container / equipment — carries **no** yard-scoped permission at all
  (`vessels.read/write`, `cargo.read/write` only), while READONLY *does* hold `yards.read`.
- **Impact:** latent, not runtime. The yard service authorizes by the role claim
  (`hasAnyRole ADMIN|YARD_MANAGER|TERMINAL_OP` per API-CONTRACTS §4 role columns) and never consults the
  `permissions` claim. But any future consumer built against the documented `yard:*` vocabulary — gateway
  role-filtered routing + `X-User-Perms` per SR-005, dashboard role-shaped responses per FR-006.1 (M10/M11) —
  will not match tokens issued by identity, and TERMINAL_OP would fail yard-scoped checks outright.
- **Resolution:** recorded non-blocking for M5 (D-445). Identity claim vocabulary unchanged; yard authZ
  stays role-based. PHASE-4-IMPLEMENTATION-PLAN M10 increment gains a reconciliation item (unify permission
  vocabulary — dot → colon or an explicit mapping — before gateway/dashboard consume the claim). No gateway
  code must hard-code English permission names before that reconciliation.
- **Status:** OPEN (2026-08-31, M5 close-out; resolution planned at M10 per D-445).

## F-421 — Optimizer `analysis` fields `totalContainers`/`containersAffected` are recommendation counts, `estimatedExecutionTimeMs` is fixed 0
- **Observed:** YardOptimizationController builds the response `Analysis` as `totalContainers =
  recommendations.size()`, `containersAffected = recommendations.size()`, `estimatedExecutionTimeMs = 0L`.
  API-CONTRACTS §4.2 documents these as block container totals (450), containers needing moves (112), and an
  execution-time estimate (45000 ms). `currentReshuffles` / `projectedReshuffles` /
  `estimatedReshufflingReduction` are computed honestly in YardOptimizationService. (An earlier
  "totalContainers=0" observation during M5 test development was a pre-wiring artifact; after wiring the
  field carries the recommendation count.)
- **Impact:** low — metrics/frontend consumers reading §4.2 semantics get placeholder values; no M5 test
  asserts these fields (the suite asserts the honest reshuffle metrics + recommendation shape). FR-002
  verifiability is unaffected. The optimizer's "recommendation only when the column's topmost tier is
  occupied" behavior was verified as correct design (no buried container needs reshuffling when the top is
  free) — a documentation note, not a defect.
- **Resolution:** defer to M12.5 hardening — populate `totalContainers` from the occupied-container count,
  keep `containersAffected` = recommendation count, and either measure `estimatedExecutionTimeMs` or return
  null with a §4.2 note. Recorded now to keep the ledger honest at close-out.
- **Status:** OPEN (2026-08-31, M5 close-out; fixed at M12.5).

## F-422 — Unquoted `HH:mm` in application.yml resolves as a YAML 1.1 sexagesimal, not a string → LocalTime binding failure
- **Observed:** M6 gate wrote `smartportx.gate.operating-hours-start: 06:00` (and `operating-hours-end:
  22:00`) unquoted. Under YAML 1.1 (SnakeYAML), a scalar shaped `[-+]?[0-9][0-9_]*(:[0-5]?[0-9])+` is
  resolved as a SEXAGESIMAL integer — `06:00` → `360`, `22:00` → `1320`, never a String. Spring Boot's
  `@ConfigurationProperties` binder then failed: `ConfigurationPropertiesBindException` →
  `BindException` under `smartportx.gate.operating-hours-end` → `ConverterNotFoundException: No converter
  found capable of converting from type [java.lang.Integer] to type [java.time.LocalTime]`, aborting the
  Spring context during test wiring (every gate integration suite failed identically, hiding the real
  cause). GateProperties binds `LocalTime operatingHoursStart/End` (defaults overridden from yml).
- **Impact:** startup/context failure for a quoting mismatch — the exception points at a missing LocalTime
  converter, not at the YAML scalar, costing a full debugging cycle. AFFECTS EVERY SERVICE with `HH:mm` (or
  `mm:ss`) time/duration literals in configuration: vessel berth windows, integration shifts, notification
  schedules at M7–M9 are the same trap.
- **Resolution:** quote the literals (`"06:00"`, `"22:00"`) → bound as String → converted to LocalTime,
  context loads. Recorded now; M7–M9 config sweeps must quote every time literal proactively and tests
  should assert the service context loads before business tests (the gate suites now do).
- **Status:** OPEN (2026-08-31, M6 close-out; remediated for gate by quoting; audit remaining services
  when their application.yml gains LocalTime/time-literal properties).

## F-423 — Vessel m7 DATA-ARCHITECTURE §9 vs API-CONTRACTS §7 deltas (data/vocabulary + numeric types)
- **Observed:** building the `smartportx_vessel` V1 DDL surfaced a full set of authoring divergences between
  the two canonical sources. DATA-ARCHITECTURE §9.1–9.4 omits columns API-CONTRACTS §7 requires and uses
  different vocabulary; API-CONTRACTS §7 omits some DA columns; and both document vessel dimensions/weights
  numerically while the JPA entities map Java double/Double.
- **Impact:** (a) one schema cannot satisfy both as-written; (b) `ddl-auto: validate` fails when the DDL
  declares NUMERIC but Hibernate maps to float8 (DOUBLE PRECISION) — the live failure was
  `Schema-validation: wrong column type encountered in column [weight_kg] … found [numeric (Types#NUMERIC)],
  but expecting [float(53) (Types#FLOAT)]`; the same NUMERIC-typed columns exist on `vessels` (length_m,
  beam_m, draft_m, gross_tonnage).
- **Deltas recorded:** vessel_type DA "RO-RO" → canonical `ROLL_ON_ROLL_OFF`; priority DA "NORMAL" →
  canonical `MEDIUM`; plan status DA DRAFT/APPROVED → collapsed `PLANNED|IN_PROGRESS|COMPLETED`; container
  status DA DISCHARGING → alias of `IN_TRANSIT`; vessel_schedules/discharge_plans/discharge_containers
  superset columns (voyage_number, port_of_call/next_port, estimated_teus/actual_teus, gross_tonnage/flag/
  operator/call_sign, yard_target/priority/actual_yard_position/equipment_id/operator).
- **Resolution:** resolved by D-447 — a documented forward-compatible superset DDL with canonical
  vocabulary, DOUBLE PRECISION for the double/Double-mapped numeric columns, and completion semantics when
  all containers are off-vessel. Resolution/status:
- **Status:** CLOSED (2026-08-31, M7 close-out) — resolved by D-447; superset DDL + vocabulary ships in
  `smartportx_vessel` V1.

## F-424 — Vessel/yard/container offline client substitution (Resilience4j starter not in local .m2)
- **Observed:** the M7 discharge flow must call the container and yard services, but
  `org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j` is not present in the local
  offline `.m2`, and `mvn dependency:get` cannot fetch it. This is the same class of constraint as the M6
  WireMock finding (F-419 substitution note).
- **Impact:** Resilience4j `@CircuitBreaker`/`@TimeLimiter` annotations would fail to resolve; adding a
  starter would break the reactor build.
- **Resolution:** per D-448, propagate best-effort/advisory (log + swallow) for status/yard placement and
  fail-closed for planning-time container verification, using hand-rolled HTTP clients
  (`HttpContainerApiClient`, `HttpYardApiClient`) behind `ContainerApiClient`/`YardApiClient` interfaces,
  wired `@ConditionalOnMissingBean` so tests inject `@MockitoBean` stubs. Provider-contract coverage uses an
  in-process JDK `com.sun.net.httpserver.HttpServer` (mirrors the M6 gate substitution). This is a
  documented substitution, not a silent omission; revisiting Resilience4j (tracked with R-007) remains open.
- **Status:** CLOSED-as-substituted (2026-08-31, M7 close-out).

## F-425 - Integration endpoint surface divergence: API-CONTRACTS \u00a78 vs INTEGRATION-ARCHITECTURE \u00a79 (M8)
- **Observed:** API-CONTRACTS \u00a78 and INTEGRATION-ARCHITECTURE \u00a79 describe overlapping but differently-spelled
  integration endpoints. API-CONTRACTS uses `edifact/parse`, `iot/data`, `customs/status`; INTEGRATION-ARCHITECTURE
  uses `edifact/inbound`, `iot/ingest`, `customs/submit`, `customs/status/{containerNumber}`. They also disagree
  on whether customs status is a single GET/PUT resource or split submit/review/release actions.
- **Impact:** ambiguous contract for clients and provider-contract tests; a reader could not know which path
  to call.
- **Resolution:** resolved by D-449 - API-CONTRACTS \u00a78 is canonical and implemented as the shipped superset;
  INTEGRATION-ARCHITECTURE \u00a79 is superseded for the REST surface. No operation is lost; every documented
  operation maps to a shipped endpoint.
- **Status:** CLOSED (M8 close-out) - resolved by D-449.

## F-426 - Integration schema/env delta + inbound-bound contract surface (M8)
- **Observed:** (1) The `smartportx_integration` schema exists but is **created externally** (it is empty and is
  NOT created by Flyway - only tables are, and Flyway runs `V1__init.sql` into it on first use); this matches
  the M1 init-databases convention but must be documented so a fresh checkout runs the init script first. (2)
  The integration service is structural **inbound-bound** in this slice: it receives EDIFACT/IoT/customs feeds
  and issues **no outbound partner HTTP calls**, so there are no outbound provider tunnels to contract-test.
- **Impact:** (a) a developer who creates the schema must re-run the init-databases script or `CREATE SCHEMA`
  manually; `ddl-auto: validate` fails if the schema is absent. (b) Provider-side contract coverage is therefore
  REST-schema-level (parse response envelope + RFC 9457 error envelope), not outbound-tunnel-level as in
  M6/M7; the inbound Rabbit/notification publish path is exercised via `@MockitoBean EventTransport` instead.
- **Resolution:** documented here and in EVIDENCE E-030; DDL column/entity mismatch caught by `ddl-auto: validate`
  (missing `edifact_messages.updated_at`) fixed in V1 and the test schema reset.
- **Status:** CLOSED-as-documented (M8 close-out).

## F-427 - EDIFACT parser library (StAEDI) not in offline .m2 (M8)
- **Observed:** FR-011 needs an EDIFACT parser; `io.xlate:staedi` (the natural library) is NOT resolvable in the
  local offline `.m2`, and `mvn dependency:get` cannot fetch it (same class of constraint as the M6 WireMock
  finding and M7 F-424 Resilience4j finding).
- **Impact:** a StAEDI dependency would fail the offline reactor build.
- **Resolution:** per D-450, a hand-rolled tokeniser + semantic parser implements the consumed COPRAR/COPARN
  subset; documented substitution, revisit tracked with R-007.
- **Status:** CLOSED-as-substituted (M8 close-out).

-------------------------------------------------------------------------------
## APPENDIX — HONEST-BASELINE
As of 2026-08-27 (Phase 4 kickoff) the repository is documentation-only: no pom.xml, no source directories,
zero git commits, no schemas, no containers. All FINDINGS above are about DOCUMENT-LEVEL conflicts, not
code-level defects. First code artifact lands in M0.