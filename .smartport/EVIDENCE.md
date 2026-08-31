# SMARTPORTX — EVIDENCE LOG

## Purpose
Track verification evidence for all completed work.

## Format
Each entry:
- REQUIREMENT: What was required
- IMPLEMENTATION: What was done
- TEST: How it was verified
- RESULT: Pass/Fail
- REVIEW: Who/what reviewed it
- EVIDENCE: Concrete proof

---

## E-001: Phase 0.1 — Environment Discovery
- **Requirement:** Understand the current environment before any changes
- **Implementation:** Inspected directory, Git, files, configs, tools, skills
- **Test:** Verified empty directory, no git, tool versions confirmed
- **Result:** PASS
- **Review:** Lead/Orchestrator
- **Evidence:** Directory listing empty, git status "not a git repository", Java 8/Node 24/Python 3.13 confirmed

## E-002: Phase 0.6 — Git Initialization
- **Requirement:** Version control foundation
- **Implementation:** git init in project root
- **Test:** git status shows clean working tree
- **Result:** PASS
- **Review:** Lead/Orchestrator
- **Evidence:** git log output, .git directory exists

## E-003: Phase 0.7 — Foundation Documents
- **Requirement:** Project memory structure
- **Implementation:** Created .smartport/ with all required documents
- **Test:** All files exist and contain valid content
- **Result:** PASS
- **Review:** Lead/Orchestrator
- **Evidence:** File listing confirms all documents present

## E-004: Phase 1.5 — Skill Registry Audit
- **Requirement:** Verify all skills exist, are valid, and are registered
- **Implementation:** Cross-referenced 77 folders, 65 registry entries, 66 system prompt entries
- **Test:** Verified all 77 SKILL.md files exist and are valid (>100 bytes each)
- **Result:** PASS with findings (F-001: 12 unregistered, F-002: 2 misclassified, F-003: META category missing)
- **Review:** Lead/Orchestrator
- **Evidence:** PHASE-1.5-FINDINGS.md Section 4, complete audit table

## E-005: Phase 1.5 — Dynamic Skill Routing Tests
- **Requirement:** Validate minimum-sufficient skill selection for 6 hypothetical scenarios
- **Implementation:** Tested REST endpoint, microservice review, JWT security, React dashboard, testing strategy, performance investigation
- **Test:** Each scenario produced clear agent + skill selection with irrelevant skills excluded
- **Result:** PASS (6/6 scenarios)
- **Review:** Lead/Orchestrator
- **Evidence:** PHASE-1.5-FINDINGS.md Section 7

## E-006: Phase 1.5 — Agent Delegation Test
- **Requirement:** Validate Lead can correctly decompose and delegate tasks
- **Implementation:** Tested with hypothetical container tracking API requirement
- **Test:** Lead correctly decomposed into 3 parallel tasks, selected 3 agents, synthesized results
- **Result:** PASS
- **Review:** Lead/Orchestrator
- **Evidence:** PHASE-1.5-FINDINGS.md Section 8

## E-007: Phase 1.5 — Quality Gate / Critic / QA / Security Tests
- **Requirement:** Validate quality pipeline, critic independence, QA attack behavior, security review
- **Implementation:** Tested with deliberately flawed designs
- **Test:** All agents have correct mandates and skills; control gaps identified (W-001, W-002)
- **Result:** PASS with findings
- **Review:** Lead/Orchestrator
- **Evidence:** PHASE-1.5-FINDINGS.md Sections 10-13

## E-008: Phase 1.5 — Project Memory Test
- **Requirement:** Verify decisions persist across reasoning cycles
- **Implementation:** Recorded D-004 test decision in DECISIONS.md
- **Test:** Decision readable from file, not reliant on conversational memory
- **Result:** PASS
- **Review:** Lead/Orchestrator
- **Evidence:** DECISIONS.md contains D-004

## E-009: Phase 2 — Domain Research
- **Requirement:** Comprehensive domain understanding before requirements
- **Implementation:** 11 web research queries, 30+ sources consulted covering terminal operations, yard management, gate congestion, IoT tracking, PCS standards, TOS market, automation
- **Test:** All 7 problem areas documented with evidence chains; stakeholder map validated against DCSA Blueprint
- **Result:** PASS
- **Review:** Lead/Orchestrator + Researcher
- **Evidence:** DOMAIN-RESEARCH.md (300+ lines), STAKEHOLDERS.md, WORKFLOWS.md, PROBLEMS.md

## E-010: Phase 2 — Requirements Engineering
- **Requirement:** Complete requirements set covering all identified problems
- **Implementation:** 38 functional requirements, 24 NFR, 22 security requirements, 8 use cases, full traceability matrix
- **Test:** Traceability matrix verified 100% problem coverage (1 gap identified: export cut-off notification)
- **Result:** PASS
- **Review:** Lead/Orchestrator + Architect
- **Evidence:** REQUIREMENTS.md, USE-CASES.md, NON-FUNCTIONAL-REQUIREMENTS.md, SECURITY-REQUIREMENTS.md, REQUIREMENT-TRACEABILITY.md

## E-011: Phase 2 — Competitor Analysis and Differentiation
- **Requirement:** Market positioning and uniqueness analysis
- **Implementation:** Analyzed 5 tiers of competitors (TOS, YMS, PCS, tracking, automation); identified 5 differentiation pillars
- **Test:** Each competitor gap documented with specific differentiation claim
- **Result:** PASS
- **Review:** Lead/Orchestrator + Product Thinker
- **Evidence:** COMPETITOR-ANALYSIS.md, DIFFERENTIATION.md

## E-012: Phase 2 — Scope, Feasibility, and Decisions
- **Requirement:** Clear boundaries and viability assessment
- **Implementation:** In/out scope defined, feasibility assessed across 4 dimensions, 10 decisions recorded (D-005 through D-014)
- **Test:** All decisions have rationale and impact analysis; feasibility score 87/100
- **Result:** PASS
- **Review:** Lead/Orchestrator
- **Evidence:** SCOPE.md, FEASIBILITY.md, PHASE-2-DECISIONS.md

## E-013: Phase 3 — Architecture Style & Service Decomposition
- **Requirement:** Justified architecture style and clean service boundaries
- **Implementation:** Service-Based Architecture chosen over monolith/microservices/EDA; 8 services decomposed from domain capabilities
- **Test:** 100% requirement traceability; acyclic dependency graph; boundary ambiguity resolutions documented
- **Result:** PASS
- **Review:** Lead/Orchestrator + Architect + Critic (8/10)
- **Evidence:** ARCHITECTURE-OVERVIEW.md, SERVICE-CATALOG.md, SERVICE-BOUNDARIES.md

## E-014: Phase 3 — API Contracts & Data Architecture
- **Requirement:** Complete API contracts and schema-per-service data design
- **Implementation:** API-CONTRACTS.md (2,903 lines, all 8 services, RFC 9457 errors, cursor pagination); DATA-ARCHITECTURE.md (7 schemas, 33 tables, data ownership, retention)
- **Test:** Every endpoint documented with request/response; every table with entities and relationships; indexes defined
- **Result:** PASS
- **Review:** Lead/Orchestrator + QA (9/10)
- **Evidence:** API-CONTRACTS.md, DATA-ARCHITECTURE.md

## E-015: Phase 3 — Security, Resilience & Observability
- **Requirement:** Comprehensive security, resilience, and observability architecture
- **Implementation:** SECURITY-ARCHITECTURE.md (JWT RS256 + RBAC + TLS + audit), RESILIENCE-ARCHITECTURE.md (circuit breaker/retry/timeout/bulkhead/idempotency), OBSERVABILITY-ARCHITECTURE.md (metrics/tracing/logging/alerting)
- **Test:** All 22 security requirements mapped; failure scenarios documented with graceful degradation; 8 alert rules, 9 dashboards, 6 business metrics
- **Result:** PASS
- **Review:** Lead/Orchestrator + Security Reviewer (9/10)
- **Evidence:** SECURITY-ARCHITECTURE.md, RESILIENCE-ARCHITECTURE.md, OBSERVABILITY-ARCHITECTURE.md

## E-016: Phase 3 — Integration, Deployment & ADRs
- **Requirement:** Integration architecture, deployment topology, and rationale records
- **Implementation:** INTEGRATION-ARCHITECTURE.md (EDIFACT, IoT, customs adapters), DEPLOYMENT-ARCHITECTURE.md (docker-compose, CI/CD, Flyway), 13 ADRs (ADR-001–013)
- **Test:** Integration flow documented (inbound/outbound); deployment topology verified; every ADR has context/decision/rationale/consequences
- **Result:** PASS
- **Review:** Lead/Orchestrator + Architect
- **Evidence:** INTEGRATION-ARCHITECTURE.md, DEPLOYMENT-ARCHITECTURE.md, ADR/ADR-001.md–ADR-013.md

## E-017: Phase 3 — Requirement-Architecture Traceability
- **Requirement:** Every requirement maps to an architecture component
- **Implementation:** REQUIREMENT-ARCHITECTURE-TRACEABILITY.md — 100% FR (38/38), 100% NFR (6 groups), 100% Security (7 groups) coverage
- **Test:** Gap analysis: 1 gap (FR-003.9 export cut-off notification, deferred); density and complexity scoring per service
- **Result:** PASS
- **Review:** Lead/Orchestrator + QA
- **Evidence:** REQUIREMENT-ARCHITECTURE-TRACEABILITY.md

## E-018: Phase 3 — Tradeoffs, Findings, Decisions & Final Report
- **Requirement:** Tradeoff analysis, findings, decisions, and scored report
- **Implementation:** ARCHITECTURE-TRADEOFFS.md (1,216 lines, 9 decision areas + debt + complexity), PHASE-3-FINDINGS.md (11 findings), PHASE-3-DECISIONS.md (D-015–D-027), PHASE-3-REPORT.md (772 lines)
- **Test:** All decisions trace to ADRs; findings cite evidence; report scored 92/100
- **Result:** PASS
- **Review:** Lead/Orchestrator + Critic + QA + Security
- **Evidence:** ARCHITECTURE-TRADEOFFS.md, PHASE-3-FINDINGS.md, PHASE-3-DECISIONS.md, PHASE-3-REPORT.md

## E-019: Phase 5 — Existence Audit (no implementation)
- **Requirement:** Verify premise "an implemented SmartPortX exists to harden" before any Phase 5 testing
- **Implementation:** Repo-wide inventory (glob for build files, recursive dirs), `git log`/`git ls-files`, `.smartport/` artifact check, requirement count extraction
- **Test:** 0 build files; only `.smartport/` and docs present; 0 commits; PHASE-4-REPORT.md and all test reports MISSING; requirements 100% NOT IMPLEMENTED
- **Result:** PASS (audit correct) — verdict NOT READY FOR PHASE 6, score 3/100, no fabricated evidence
- **Review:** Lead/Orchestrator + Critic (no system to approve)
- **Evidence:** PHASE-5-PLAN.md, PHASE-5-FINDINGS.md (F5-001..F5-008), PHASE-5-REPORT.md; `git log` output; glob "No files found"; `.smartport/` listing

## E-020: Phase 4 — Kickoff Artifacts (plan, test strategy, tracker, findings/decisions/fixes, report stubs)
- **Requirement:** Begin Phase 4 (Implementation) per user brief — planning artifacts must precede code (§50 of brief)
- **Implementation:** Master plan PHASE-4-IMPLEMENTATION-PLAN.md (M0–M13 vertical slices, contract testing,
  resilience, security operating rules); TEST-STRATEGY.md; IMPLEMENTATION-STATUS.md tracker; PHASE-4-FINDINGS.md
  (F-401..F-408 incl. 4 doc- level conflicts resolved); PHASE-4-DECISIONS.md (D-409..D-423 incl. StAEDI for F-004,
  canonical roles, RabbitMQ topology, monorepo); PHASE-4-FIXES.md (empty-by-design until code exists);
  E2E/SECURITY/PERFORMANCE-TEST-REPORT.md stubs (explicit NOT-YET-RUN)
- **Test:** Files present with required section skeletons; conflict resolutions cross-checked against source docs
  (COMMUNICATION-ARCHITECTURE vs SERVICE-CATALOG; DATA-ARCHITECTURE vs ARCHITECTURE-OVERVIEW; role spellings); honest-baseline appendix records 0-code state
- **Result:** PASS (planning complete; no code claims made)
- **Review:** Lead/Orchestrator + Architect (conflict resolutions), recorded for Critic
- **Evidence:** PHASE-4-IMPLEMENTATION-PLAN.md, TEST-STRATEGY.md, IMPLEMENTATION-STATUS.md, PHASE-4-FINDINGS.md,
  PHASE-4-DECISIONS.md, PHASE-4-FIXES.md, E2E/SECURITY/PERFORMANCE-TEST-REPORT.md (stubs); STATUS/ROADMAP/RISKS updated

## E-021: M0 — Repository Foundation (versions pinned, monorepo scaffolds, CI, verified build)
- **Requirement:** M0 per PHASE-4-IMPLEMENTATION-PLAN — parent POM with pinned versions, module scaffolds, dashboard scaffold, CI workflow, everything green
- **Implementation:** Root parent POM (spring-boot-starter-parent 3.5.16, Java 25, modules list, **spring-cloud BOM 2025.0.3** — corrected from 2025.1.3 which targets Boot 4); .gitignore/.editorconfig/README; smartportx-common (+ `SmartPortxCommonTest`); smartportx-eureka; smartportx-gateway; 7 uniform service scaffolds (identity 8081, container 8084, yard 8082, gate 8083, vessel 8085, integration 8086, notification 8087); smartportx-dashboard (Vite + React 18 + TS); .github/workflows/ci.yml; BOM fix for generated poms (`&`;`→ `&amp;`); UTF-8 BOM stripped from 22 generated files (javac `\ufeff` errors)
- **Test:** `mvn -B -ntp clean verify` at root → **BUILD SUCCESS** (11/11 projects, Reactor Summary); dashboard `npm run lint` (clean), `npm test` (1/1 passed), `npm run build` (vite build OK)
- **Result:** PASS
- **Review:** Lead/Orchestrator + Implementer (BOM/train resolution); recorded for Critic
- **Evidence:** pom.xml; per-module pom/Application/application.yml; dashboard package.json + App.test.tsx (1 passing); ci.yml; command outputs above. M0.6 git initial-commit remains PENDING (needs explicit user authorization per git policy)

## E-022: M1 — Infrastructure (DB init, compose, Rabbit topology, env, Flyway skeletons)
- **Requirement:** M1 per PHASE-4-IMPLEMENTATION-PLAN — idempotent DB/schema init, authoritative docker-compose,
  RabbitMQ topology script, .env.example, per-service Flyway skeletons, monitoring configs
- **Implementation:** `scripts/init-databases.sh` (DB `smartportx` + 7 schemas + dev role, idempotent; D-424);
  `docker-compose.yml` (17 services: postgres/rabbitmq/eureka + 8 apps + dashboard + prometheus/grafana/jaeger, network +
  4 named volumes; fixed `${VAR:-def}` interpolation + removed obsolete version attr); `scripts/rabbit-topology.sh`
  (6 exchanges per COMMUNICATION-ARCHITECTURE §3.2, 24 primary queues + retry(60s TTL)+dlq + wildcard & literal DLX
  bindings, D-425/D-426); `.env.example` (no secrets); `config/prometheus/prometheus.yml`;
  `config/grafana/provisioning/{datasources,dashboards}.yml`; 7× `V1__init.sql` Flyway skeletons
- **Test:** init-databases.sh run TWICE against local PostgreSQL 17.11 (postgres) via Git Bash — second run fully
  idempotent, 7 schemas confirmed (`pg_namespace` listing); `docker compose config --quiet` exit 0 (no warnings);
  `bash -n` syntax check on rabbit-topology.sh (live broker verify deferred — R-007);
  `mvn -B -ntp clean verify` root reactor still 11/11 SUCCESS after resource additions
- **Result:** PASS (broker-dependent verification PENDING at M4.7 when RabbitMQ becomes available)
- **Review:** Lead/Orchestrator + Implementer; findings F-409/F-410/F-411 + decisions D-424/D-425/D-426 recorded
- **Evidence:** scripts/init-databases.sh, docker-compose.yml, scripts/rabbit-topology.sh, .env.example,
  config/prometheus/prometheus.yml, config/grafana/provisioning/*, 7× db/migration/V1__init.sql; command outputs above


## E-023: M2 - smartportx-common shared library
- **Requirement:** M2 per plan - RFC 9457 error model, correlation id, pagination/sort DTOs, domain event envelope,
  observability, shared starter (module or starter, decide at M2.5)
- **Implementation:** com.smartportx.common packages - error (ApiError, ApiProblems, ApiException,
  NotFoundException, ConflictException, GlobalExceptionHandler), correlation (CorrelationContext + CorrelationIdFilter
  X-Correlation-Id in/out + MDC), web (PageRequest caps size 100 / sort parse, PageResult), event (DomainEvent<T>
  Avro-free JSON), observability (SmartPortxMetrics counters, SmartPortxHealthIndicator, SmartPortxTracer),
  autoconfig.SmartPortxCommonAutoConfiguration activated via META-INF/spring/...AutoConfiguration.imports;
  pom: web, webmvc(prov), tx, validation, actuator, micrometer-registry-prometheus, micrometer-tracing-bridge-otel,
  opentelemetry-exporter-otlp, servlet-api(prov)
- **Test:** mvn -pl smartportx-common test - 17/17 PASS (ProblemDetail hides stack/secrets with errorId, validation
  fieldErrors to 400, 404/409 mapping, filter assigns/propagates/clears MDC+ThreadLocal incl. failure path, page
  defaults/bounds/sort parse, PageResult math, DomainEvent Jackson round-trip); ContextSmokeTest in identity -
  real Spring context with common starter wired: GlobalExceptionHandler + CorrelationIdFilter + metrics + health present
- **Result:** PASS
- **Review:** Lead/Orchestrator + Implementer; decisions D-427 (module-dependency starter, AutoConfiguration.imports)
  and D-428 (filter/advice only in servlet services; reactive gateway excluded) recorded
- **Evidence:** SmartPortxCommonAutoConfiguration + imports file; 5 main packages; 4 test classes; root reactor
  mvn clean verify still 11/11 + identity context test green


## E-024: M3 — smartportx-identity vertical slice (auth, profiles, admin management)
- **Requirement:** M3 per plan — authentication/registration (FR-001), user profiles/roles (FR-002),
  admin user management core (FR-015, M3 part), with contracts API-CONTRACTS §3.1/§3.2 and ADR-009.
- **Implementation:** com.smartportx.identity full slice —
  domain (User, Role, UserRole, RefreshTokenEntity, PasswordHistory, enums), repositories (users, roles,
  user_roles, refresh_tokens, password_history), services (AuthService, UserService, UserQueryService with
  cursor/keyset pagination, RefreshTokenService with rotation + reuse-detection via unique token_hash,
  PasswordService BCrypt + last-5 history, AccountSecurity), security (JwtTokenService + KeyRepository
  in-JVM signing keys, TokenHasher SHA-256, LoginRateLimiter, JsonProblemHandler, RolePermissions),
  config (SecurityConfig method security, SecurityProperties), web (AuthController, UserAdminController,
  IdentityExceptionAdvice), DemoDataSeeder; Flyway V1 identity superset DDL (D-431). Decisions D-429
  (canonical 5-role RBAC = API-CONTRACTS), D-430 (single-role authoritative + mirror), D-431 (superset DDL),
  D-432 (cursor pagination per contract), D-433 (PUT /auth/password extension).
- **Test:** mvn -pl smartportx-identity -am test — 15/15 PASS (ContextSmoke, AuthFlow, LoginSecurity,
  RateLimit, AdminUserManagement integration suites): JWT issue/refresh rotation, reuse detection, logout,
  password policy + history, RBAC @PreAuthorize, login rate-limit 429 `rate_limited`, admin CRUD + role/status
  change, cursor pagination envelope; reactor mvn clean verify green across all 10 modules.
- **Result:** PASS (M3.7 contract conformance seperate entry E-025)
- **Review:** Lead/Orchestrator + Implementer; decisions D-429…D-433 recorded (PHASE-4-DECISIONS.md);
  findings F-412/F-413 and fixes SC-07…SC-10 logged (PHASE-4-FINDINGS.md / PHASE-4-FIXES.md).
- **Evidence:** 4 main packages × 49 files under smartportx-identity; V1__init.sql; 5 integration test classes;
  mvn output above (23 tests total incl. E-025 conformance, green).

## E-025: M3.7 — contract conformance reconciliation + CI Postgres provisioning
- **Requirement:** M3.7 per plan — API-CONTRACTS conformance check on identity responses/params; CI must run the
  integration suites (GitHub Actions runner has no database by default).
- **Implementation:** (1) `GET /users` now binds the contract's query names `filter[role]`, `filter[status]`,
  `filter[search]` and `sort` (`createdAt,asc|desc`, default `createdAt,desc`); `UserQueryService` keyset
  predicates for both directions; unsupported sort → 400 `invalid_query` via new InvalidQueryException +
  IdentityExceptionAdvice handler (RFC 9457 ProblemDetail). (2) New 8-test conformance suite
  ContractConformanceIntegrationTest locks login/refresh/logout response shapes (§3.1), users envelope +
  pagination (§3.2/§2.4), filter param names, default sort direction, asc sort, and invalid-sort 400.
  (3) ci.yml backend job now provisions `postgres:17` service (postgres/postgres, port 5432, pg_isready
  health) and runs `scripts/init-databases.sh` (installs postgresql-client, PGHOST/PGPASSWORD/init script;
  script already proven idempotent in E-022).
- **Test:** mvn -B -ntp -pl smartportx-identity -am clean test — ContractConformanceIntegrationTest 8/8 PASS;
  full suite 23/23 PASS (identity) + 17/17 PASS (common reactored in), BUILD SUCCESS; log saved.
- **Result:** PASS (identity contract conformance verified); CI green run on GitHub Actions PENDING (local
  machine cannot execute Actions; workflow + script verified statically).
- **Review:** Lead/Orchestrator + Implementer; findings F-412 (CI DB gap) / F-413 (param+sort drift) and
  fixes SC-07/08/09 CLOSED, SC-10 OPEN recorded.
- **Evidence:** ContractConformanceIntegrationTest.java (8 tests); UserAdminController filter/sort binding;
  UserQueryService CursorDirection ASC/DESC; InvalidQueryException + advice; ci.yml services/init step;
  scripts/init-databases.sh; mvn output (Tests run: 23 … BUILD SUCCESS).

## E-026: M4.8 — container registry, state machine, movements, events, RBAC (FR-003/FR-004)
- **Requirement:** M4 per plan — container registry + lifecycle endpoints (FR-003) and state machine +
  movements + status history + event log (FR-004), API-CONTRACTS §5 + decisions D-434…D-437.
- **Implementation:** domain (Container, ContainerType, ContainerStatus, MovementType, ContainerNumbers,
  ContainerStateMachine with exactly the 15 recorded transitions); repositories with keyset cursor paging,
  soft-delete filtering (`removed_at`), case-insensitive search specs; services (ContainerService,
  ContainerQueryService, ContainerEventLogger); events (EventTransport interface, LogEventTransport default,
  RabbitEventTransport behind `smartportx.events.publish-to-rabbitmq`, ContainerEventsConfig mutually-exclusive
  beans, ContainerDomainEvents envelope vocabulary, JsonNode payloads per D-438); web (ContainerController +
  18 DTOs incl. flattened dangerous-goods declaration with `dangerousGoods` flag); security (JWKS JwtDecoder,
  stateless chain, JsonProblemHandler, ContainerExceptionAdvice mapping AuthorizationDeniedException → 403
  `access_denied`, role guard `ADMIN|TERMINAL_OP` on writes); Flyway V1 superset DDL in
  `smartportx_container` schema. Decisions D-438/D-439/D-440 recorded.
- **Test:** `mvn -B -ntp -pl smartportx-container -am clean test` — **50/50 PASS** (14 unit: ContainerNumbers
  10 + ContainerStateMachine 4; 36 integration: register 9, query 11, status 7, movement 5, events 4).
  Integration suites verify ISO 6346 check-digit rejection, duplicate 409, soft-delete replay, cursor pagination,
  `sort`/`customsStatus` 400 `invalid_query`, all legal transitions + same-status 422, JWT-authoritative
  performedBy, and Rabbit envelope capture (`container.ContainerRegistered|ContainerStatusChanged|
  ContainerMoved|ContainerRemoved`) via `@MockitoBean EventTransport`. Full reactor `mvn -B clean verify`
  **11/11 BUILD SUCCESS** (mirrors CI backend job).
- **Result:** PASS (FR-003, FR-004 delivered; SC-11/SC-12/SC-13 closed; findings F-414…F-418 + E-025 carried
  over). CI green run on GitHub Actions PENDING (SC-10 — local machine cannot execute Actions).
- **Review:** Lead/Orchestrator + Implementer; decisions D-438…D-440 recorded; doc-fix sweep applied to
  API-CONTRACTS §5 (ports, §5.1 sample + lengths, §5.2 state machine, §5.3 movement/event vocabulary) and
  DATA-ARCHITECTURE §7.1 (8 container types).
- **Evidence:** 10 packages × 45 main files + 5 integration suites (11 test files incl. infra) under
  smartportx-container; V1__init.sql;
  LogEventTransport emitted domain events with correlationId in MDC during the runs; mvn output
  (Tests run: 50 … BUILD SUCCESS; reactor BUILD SUCCESS).

## E-027: M5 — yard layout, positioning, optimization, equipment, events, RBAC (FR-001/FR-002/FR-008)
- **Requirement:** M5 per plan — yard layout visualization + block/bay/row/tier hierarchy + container
  positioning + utilization metrics (FR-001), AI-driven stacking optimization with explainable
  recommendations + dry-run + history (FR-002), equipment registry + status/assignment/moves (FR-008);
  decisions D-441…D-444.
- **Implementation:** Flyway V1 superset DDL in `smartportx_yard` (D-441 six-value block_type, D-443
  four-value strategy, D-444 yard_equipment) + seed (B01 EXPORT 2 bays × 2 rows × 3 tiers = 12 tiers,
  RTG-01 ASSIGNED to B01); domain building blocks (YardBlock/Bay/Row/Tier) with position model
  `B{BLOCK}-B{bay}-R{row}-T{tier}` per D-442; services (YardQueryService, YardMetricsService,
  YardAssignmentService with occupancy / dangerous-goods / reefer / weight / container-status / stacked-
  above constraint checks, YardEquipmentService, YardOptimizationService heuristics
  FIFO | WEIGHT_CLUSTER | DEPARTURE_CLUSTER | BALANCED per D-443 — per-column top-occupied reshuffle scan
  with lowest-empty-tier targets, dry-run preserved in optimization_history, moves counters incremented
  on executed runs); OptimizationHistoryRepository cursor-paged history; events (EventTransport pattern
  mirrored from M4/D-438, yard-domain **typed record** payloads, YardEventsConfig log/rabbit mutually-
  exclusive beans, vocabulary `yard.YardPositionAssigned | yard.YardPositionFreed | yard.YardOptimizationRun |
  yard.EquipmentStatusChanged`, source `smartportx-yard`); web (YardController / YardOptimizationController /
  YardMetricsController / YardEquipmentController + DTO records); security (stateless JWT chain shared via
  common starter, role guards: GETs authenticated, PUT block/tier-container/equipment
  `ADMIN|YARD_MANAGER|TERMINAL_OP`, DELETE tier-container + POST optimize `ADMIN|YARD_MANAGER`).
- **Test:** `mvn -B -ntp -pl smartportx-yard verify` — **37/37 PASS** (5 integration suites: layout 8,
  assignment 13, optimization 5, equipment 8, events 3). Suites verify position-constraint violations
  (occupancy 409, stacked-above 422, dangerous-goods / reefer / weight 422, unknown container 404), the RBAC
  matrix (401 unauthenticated, 403 reader), dry-run analysis (currentReshuffles=1, RESHUFFLE recommendation
  with non-empty recommendedPosition, status COMPLETED) + unknown-strategy 422 + missing-blockId 400 +
  history envelope, equipment list/filter/get/update + OUT_OF_SERVICE → 409 on re-assignment + moves, and
  event-envelope capture via `@MockitoBean EventTransport`. Postgres-backed (shared `smartportx_yard`
  schema, per-suite `@Transactional`, real repositories resolved per run). Jar repackaged — **BUILD SUCCESS**.
- **Result:** PASS (FR-001/FR-002/FR-008 delivered; F-419 roll-up label applied; F-420/F-421 recorded,
  non-blocking; SC-10 CI-green on GitHub Actions + R-007 live RabbitMQ verify still OPEN — local machine
  cannot execute Actions).
- **Review:** Lead/Orchestrator + Implementer; decisions D-441…D-444 recorded + D-445; doc-fix sweep
  applied: API-CONTRACTS §4.4 (Equipment), DATA-ARCHITECTURE §6.1/§6.6 comment amendments + §6.7
  (yard_equipment), IMPLEMENTATION-STATUS §1/§3/§6.
- **Evidence:** main sources under smartportx-yard/src/main/java (web/service/domain/repository/event);
  V1__init.sql (superset DDL + B01/RTG-01 seed); 5 integration suites under smartportx-yard/src/test;
  mvn output (Tests run: 37 … BUILD SUCCESS; jar repackaged).
