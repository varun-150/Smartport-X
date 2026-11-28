# SMARTPORTX — PHASE 4 FIXES LOG

> Living log of defects/bugs found and fixed during Phase 4. A fix is only CLOSED when the
> regression test that reproduces it passes (TEST-STRATEGY §2: re-runnable, not "manually confirmed once").
> Entries appear only after real code exists (baseline: no code yet at 2026-08-27).

--------------------------------------------------------------------------------
| ID | Milestone | Component | Severity | Symptom | Root cause | Fix | Regression test | Status |
|----|-----------|-----------|----------|---------|------------|-----|-----------------|--------|
| SC-01 | M0 | 7 generated service poms | blocker | `[FATAL] XML parse` — unescaped `&` in `<description>` | generator wrote `&` bare | escaped to `&amp;` | `mvn clean verify` root reactor passes | CLOSED |
| SC-02 | M0 | 7 service Application.java/yml/xml | blocker | `illegal character: '\ufeff'` (javac) | UTF-8 BOM written by Windows `Set-Content -Encoding utf8` | stripped BOM from 22 generated files | `mvn clean verify` compiles all modules | CLOSED |
| SC-03 | M0 | root `pom.xml` | critical | gateway starter "version missing"; ecosystem mismatch | `spring-cloud-dependencies` 2025.1.x targets Boot 4, we pin Boot 3.5 | pin `2025.0.3` (Cloud for Boot 3.5) | reactor 11/11 BUILD SUCCESS | CLOSED |
| SC-04 | M1.2 | `docker-compose.yml` gateway.jwt | medium | `invalid interpolation format for …JWT_EXPIRATION` | `${VAR:default}` not compose syntax | `${VAR:-default}` | `docker compose config --quiet` exit 0 | CLOSED |
| SC-05 | M1.2 | `docker-compose.yml` | low | obsolete `version:` warning | compose-spec v2 removed it | removed attribute | `docker compose config --quiet` exit 0, no warnings | CLOSED |
| SC-06 | M1.1 | `scripts/init-databases.sh` | medium | `syntax error at or near ":"` in role DO-block | psql does not interpolate `:vars` inside dollar-quoted bodies | bash-expanded DO block + `APP_ROLE` identifier guard | script runs idempotently twice; 7 schemas listed | CLOSED |
| SC-07 | M3.7 | identity `UserAdminController` | medium | `GET /users` ignored contract query names (`role`/`status`/`search` used) | impl used bare names; API-CONTRACTS §3.2 = `filter[role]`/`filter[status]`/`filter[search]` | bind `@RequestParam("filter[role]")` etc. | `ContractConformanceIntegrationTest.filterParamsUseContractNames` | CLOSED |
| SC-08 | M3.7 | identity `UserQueryService` | medium | default user list order ascending; contract = `createdAt,desc` | PageRequest built ascending | `parseSort` default DESC + keyset predicates for ASC and DESC | `sortDefaultsToCreatedAtDesc`, `sortCreatedAtAscReturnsOldestFirst` | CLOSED |
| SC-09 | M3.7 | identity error/web | low | arbitrary `sort` value silently ignored | no sort validation | `InvalidQueryException` → 400 `invalid_query` (RFC 9457 ProblemDetail) | `unsupportedSortFieldIsBadRequest` | CLOSED |
| SC-10 | M3.7 | `.github/workflows/ci.yml` | high | identity integration tests cannot pass on GitHub Actions (runner has no Postgres) | no DB service provisioned in backend job | `services: postgres:17` + `Init databases (schemas per service)` step running `scripts/init-databases.sh` | first green GitHub Actions backend run (pending — Actions not executable locally) | OPEN |
| SC-11 | M4.8 | container `application.yml` | blocker | context load fails `... parsing a block mapping` (36/50 errored) | `jackson:` block written at top level instead of under `spring:` | re-indented to `spring.jackson.default-property-inclusion: non_null` | all 5 container integration suites load the context and pass | CLOSED |
| SC-12 | M4.8 | `ContainerExceptionAdvice` (new) | medium | reader role on write endpoints → 500 instead of 403 `access_denied` (3/50) | controller-layer `AuthorizationDeniedException` fell to common `Throwable` catch-all → 500 | `@RestControllerAdvice` mirroring `IdentityExceptionAdvice`: `AccessDeniedException` → 403 ProblemDetail `access_denied` | reader-role 403 tests in register/status/movement suites | CLOSED |
| SC-13 | M4.8 | `TestContainerNumbers` | high | 22 duplicate-registration 409 failures on re-run | fixed `TSTU100000+` serial reused across runs against shared schema (no `@Transactional`) | per-JVM random 6-digit base (`ThreadLocalRandom` 10_000–910_000) + wraparound `reset()` | full container suite green twice in a row (SC-10-free) | CLOSED |

--------------------------------------------------------------------------------
## APPENDIX — DOCUMENT FIXES (non-code corrections applied during Phase 4)
> These are documentation conflicts resolved into decisions (PHASE-4-DECISIONS.md) and fixed in the docs at
> the scheduled doc-fix sprint (M12.7) unless they block code now.

| Doc | Issue (F-ID) | Correction | Applies |
|-----|--------------|------------|---------|
| ARCHITECTURE-OVERVIEW.md (CH-1 enum) | F-402 | ContainerStatus → DATA-ARCHITECTURE §7.1 enum | doc-fix sprint |
| SERVICE-CATALOG §4.2 | F-401 | Mark exchange sketch superseded by COMMUNICATION-ARCHITECTURE topology | M1.3 (topology script must match decision) |
| ROADMAP.md phase labels | F-406 | Phase 4 = implementation (this plan); 5.A/5.B next | now |
| REQUIREMENT counts in doc headers | F-405 | 46 FR / 16 NFR / 36 SR | doc-fix sprint |
| Role spellings (OP/TERMINAL_OP/GATE_AGENT/READONLY) | F-403 | canonical D-412 spellings | now where code touches, else doc sweep |
| ARCHITECTURE-OVERVIEW/other CH references | F-407/F-408 | notification/vessel schema confirmations | after §9/§11 reads at M7/M9 |
| DEPLOYMENT-ARCHITECTURE §3.1/§3.3 | F-410 | per-service DATABASE_URLs + per-DB init script → single DB `smartportx` + `SERVICE_SCHEMA` (D-424) | repo implements now (compose + init-databases.sh); doc edit at sweep |
| DEPLOYMENT-ARCHITECTURE §3.1 (gateway) | F-409 context | `JWT_EXPIRATION:${…:86400}` → `${…:-86400}`; drop `version: "3.9"` | repo compose fixed now; doc at sweep |