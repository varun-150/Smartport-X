# ADR-010: Database Migration — Flyway

**Status:** PROPOSED
**Date:** 2026-08-26
**Deciders:** Architect, Lead

---

## Context

The SmartPort platform has 7 services (ADR-001) each with its own database schema (ADR-004). As development progresses, database schemas will evolve:

1. New tables added as features are implemented.
2. Columns added/modified as requirements change.
3. Indexes added as query patterns emerge.
4. Seed data needed for development and testing.

Without a migration tool, the team would rely on one of these problematic approaches:
- **Hibernate auto-DDL** — generates schema from entity annotations, but doesn't track changes, can't handle data migrations, and can corrupt production data.
- **Manual SQL scripts** — error-prone, no version tracking, hard to coordinate across team members.
- **No schema management** — leads to schema drift between environments.

The team needs a tool that:
- Version-controls database schemas.
- Supports SQL-based migrations (not XML or YAML).
- Integrates natively with Spring Boot.
- Works with PostgreSQL 17 (ADR-004).
- Supports schema-per-service with Flyway's `schemas` configuration.

## Decision

We adopt **Flyway 10.x** for all database schema migrations.

### Migration File Convention

```
src/main/resources/db/migration/
├── V1__create_users_table.sql
├── V2__create_roles_table.sql
├── V3__create_user_roles_table.sql
├── V4__create_vessels_table.sql
├── V5__create_voyages_table.sql
├── V6__create_positions_table.sql
├── V7__create_berths_table.sql
├── V8__create_reservations_table.sql
├── V9__add_vessel_attributes_jsonb.sql
└── V10__seed_development_data.sql
```

### Naming Convention

- `V{number}__{description}.sql` — Versioned migrations (run once, in order).
- `R__{description}.sql` — Repeatable migrations (re-run when checksum changes).
- `U{number}__{description}.sql` — Undo migrations (requires Flyway Teams/Enterprise).

### Spring Boot Configuration

```yaml
# application.yml for each service
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: ${SERVICE_SCHEMA:vessel}
    table: flyway_schema_history
    baseline-on-migrate: true
    baseline-version: 0
    validate-on-migrate: true
    out-of-order: false
    placeholder-replacement: true
    placeholders:
      schema_name: ${SERVICE_SCHEMA:vessel}
```

### Example Migration Scripts

```sql
-- V1__create_users_table.sql (auth-service)
CREATE TABLE IF NOT EXISTS auth.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_users_email ON auth.users(email);
CREATE INDEX idx_users_active ON auth.users(is_active);

-- V2__create_roles_table.sql (auth-service)
CREATE TABLE IF NOT EXISTS auth.roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- V3__create_user_roles_table.sql (auth-service)
CREATE TABLE IF NOT EXISTS auth.user_roles (
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES auth.roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);

-- V4__create_vessels_table.sql (vessel-service)
CREATE TABLE IF NOT EXISTS vessel.vessels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imo_number VARCHAR(10) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    length_m DECIMAL(8,2),
    beam_m DECIMAL(8,2),
    draft_m DECIMAL(8,2),
    gross_tonnage DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    attributes JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_vessels_imo ON vessel.vessels(imo_number);
CREATE INDEX idx_vessels_status ON vessel.vessels(status);
CREATE INDEX idx_vessels_type ON vessel.vessels(type);

-- V5__create_positions_table.sql (vessel-service)
CREATE TABLE IF NOT EXISTS vessel.positions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vessel_id UUID NOT NULL REFERENCES vessel.vessels(id),
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    speed_knots DECIMAL(5,2),
    heading_degrees DECIMAL(5,2),
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_positions_vessel ON vessel.positions(vessel_id);
CREATE INDEX idx_positions_time ON vessel.positions(recorded_at DESC);
```

### Migration Execution Flow

```
Application Startup
        │
        ▼
┌─────────────────────┐
│ Flyway Migration    │
│ Check Schema Table  │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐     ┌──────────────────┐
│ Find unapplied      │────→│ Validate existing │
│ migrations          │     │ migrations        │
└──────────┬──────────┘     └──────────────────┘
           │
           ▼
┌─────────────────────┐
│ Apply migrations    │
│ in version order    │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Update schema       │
│ history table       │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Application starts  │
│ normally            │
└─────────────────────┘
```

### CI/CD Integration

```yaml
# GitHub Actions workflow snippet
- name: Run Flyway Migrations
  run: |
    flyway -url=jdbc:postgresql://localhost:5432/smartport \
           -user=smartport \
           -password=smartport \
           -schemas=${SERVICE_SCHEMA} \
           migrate
```

## Alternatives Considered

### 1. Liquibase
A database schema migration tool using XML, YAML, or JSON changelogs.
- **Pros:** Database-agnostic, rollback support (in some editions), XML/YAML/SQL formats, diff generation.
- **Cons:** Verbose XML/YAML syntax, steeper learning curve, more complex configuration, rollback requires Liquibase Pro (paid), larger community favors Flyway for SQL-based migrations.
- **Verdict:** Rejected — Flyway's SQL-first approach is simpler and more natural for our team.

### 2. Manual SQL Scripts
Hand-written SQL scripts applied manually.
- **Pros:** Zero tooling overhead, full control.
- **Cons:** No version tracking, no automatic execution, error-prone, no checksum validation, impossible to coordinate across team members, no history of what's been applied.
- **Verdict:** Rejected — unmanageable for 7+ schemas with multiple developers.

### 3. Hibernate Auto-DDL
Let Hibernate generate and update schemas from entity annotations.
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update  # or create, create-drop
```
- **Pros:** Zero manual SQL, schema matches entities automatically.
- **Cons:** Can't handle data migrations, may drop columns/tables, no version history, unpredictable in production, can't add indexes declaratively, dangerous in production (`ddl-auto: update` can destroy data).
- **Verdict:** Rejected — unsafe for production and doesn't provide migration history.

### 4. jOOQ Codegen
Generate Java code from database schema.
- **Pros:** Type-safe SQL, compile-time query verification.
- **Cons:** Not a migration tool (schema must exist first), requires separate migration solution, complex setup, commercial license for some features.
- **Verdict:** Rejected — not a migration tool; would still need Flyway or Liquibase.

### 5. Alembic (Python)
A migration tool for SQLAlchemy.
- **Pros:** Good for Python projects, supports autogeneration.
- **Cons:** Python-only, not compatible with Java/Spring Boot ecosystem, team has no experience.
- **Verdict:** Rejected — ecosystem incompatibility.

## Rationale

Flyway is the optimal choice based on:

1. **SQL-first approach:** Migrations are plain SQL files — no XML, YAML, or Java code required. The team writes SQL they understand.
2. **Spring Boot native support:** `spring-boot-starter-data-jpa` includes Flyway auto-configuration. Adding the dependency is sufficient.
3. **Simplicity:** Flyway's version-based migration model is straightforward — `V1__`, `V2__`, `V3__` applied in order.
4. **Checksum validation:** Flyway checksums migration files to detect modifications after execution.
5. **Schema-per-service:** Flyway's `schemas` configuration supports our schema-per-service approach (ADR-004).
6. **Integration testing:** `@FlywayTest` annotation enables schema reset for integration tests.
7. **Free and open-source:** The community edition covers all our needs (undo migrations require Teams edition).
8. **PostgreSQL support:** Flyway has excellent PostgreSQL support with native SQL syntax.

### Migration Rules

1. **Never modify an applied migration** — create a new migration instead.
2. **Use IF EXISTS / IF NOT EXISTS** — prevent errors on re-run.
3. **One logical change per migration** — each migration should be small and focused.
4. **Test migrations locally** — run `flyway migrate` before committing.
5. **Use placeholders** — for schema names and environment-specific values.
6. **Version numbering** — use sequential integers, no gaps (e.g., V1, V2, V3 — not V1, V5, V10).

## Consequences

### Positive
- **Version controlled schemas** — every schema change is tracked in version control.
- **Automatic execution** — migrations run on application startup, no manual steps.
- **Checksum validation** — prevents modification of already-applied migrations.
- **Team coordination** — multiple developers can create migrations without conflicts (if following naming conventions).
- **Environment parity** — same migrations run in development, testing, and production.
- **Simple to use** — SQL files with version prefixes, minimal configuration.

### Negative
- **No automatic rollback** — Flyway Community Edition doesn't support rollback (must write reverse migration manually).
- **Manual migration scripts** — developers must write SQL migrations (not auto-generated from entities).
- **Schema history table** — Flyway adds `flyway_schema_history` table to each schema (minimal overhead).
- **Conflict resolution** — if two developers create V5, one must rename (mitigated by coordination).
- **No data migration DSL** — data migrations must be written in plain SQL (more verbose but more flexible).

## Related

- ADR-001: Architecture Style — SBA (each service has its own migrations)
- ADR-004: Database — PostgreSQL 17 (Flyway manages PostgreSQL schemas)
- ADR-003: Backend Framework — Spring Boot 3.x (Flyway auto-configuration)
- Flyway Documentation: https://flywaydb.org/documentation/
- Spring Boot Flyway Integration: https://docs.spring.io/spring-boot/reference/dataaccess.html#data.sql.migration
