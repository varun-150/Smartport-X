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