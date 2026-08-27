# ADR-004: Database — PostgreSQL 17 with Schema-per-Service

**Status:** PROPOSED
**Date:** 2026-08-26
**Deciders:** Architect, Lead

---

## Context

The SmartPort platform requires persistent storage for multiple bounded contexts: vessel records, berth reservations, cargo manifests, compliance certificates, user accounts, and analytics data. Each service in our SBA architecture (ADR-001) needs database access, but the team must decide between:

1. A single shared database (simple but coupled)
2. Multiple database instances (fully isolated but operationally complex)
3. A single instance with logical schema separation (balanced approach)
4. A NoSQL alternative for flexibility

Key constraints:
- The system runs on a single terminal/machine (Docker Compose deployment).
- The team needs ACID compliance for financial and scheduling data.
- Some data is naturally relational (berth → reservations → vessels → voyages).
- Some data benefits from flexible schemas (cargo items with varying attributes).
- PostgreSQL 17 is already verified running on the development machine.

## Decision

We adopt **PostgreSQL 17** as the database engine with **schema-per-service** logical separation within a single instance.

### Schema Layout

```sql
-- Authentication Service
CREATE SCHEMA auth;
CREATE TABLE auth.users (...);
CREATE TABLE auth.roles (...);
CREATE TABLE auth.user_roles (...);

-- Vessel Service
CREATE SCHEMA vessel;
CREATE TABLE vessel.vessels (...);
CREATE TABLE vessel.voyages (...);
CREATE TABLE vessel.positions (...);

-- Berth Service
CREATE SCHEMA berth;
CREATE TABLE berth.berths (...);
CREATE TABLE berth.reservations (...);
CREATE TABLE berth.schedules (...);

-- Cargo Service
CREATE SCHEMA cargo;
CREATE TABLE cargo.shipments (...);
CREATE TABLE cargo.containers (...);
CREATE TABLE cargo.manifests (...);

-- Compliance Service
CREATE SCHEMA compliance;
CREATE TABLE compliance.inspections (...);
CREATE TABLE compliance.certificates (...);
CREATE TABLE compliance.violations (...);

-- Notification Service
CREATE SCHEMA notification;
CREATE TABLE notification.alerts (...);
CREATE TABLE notification.subscriptions (...);

-- Analytics Service (read-only replicas or materialized views)
CREATE SCHEMA analytics;
CREATE TABLE analytics.metrics (...);
CREATE TABLE analytics.aggregations (...);
```

### Connection Configuration

```yaml
# application.yml for each service
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/smartport
    username: ${DB_USERNAME:smartport}
    password: ${DB_PASSWORD:smartport}
  jpa:
    hibernate:
      default_schema: ${SERVICE_SCHEMA:vessel}
    properties:
      hibernate.default_schema: ${SERVICE_SCHEMA:vessel}
  flyway:
    locations: classpath:db/migration
    schemas: ${SERVICE_SCHEMA:vessel}
```

### JSONB Usage for Flexible Data

```sql
-- Cargo items with varying attributes (container type, weight, hazmat class)
CREATE TABLE cargo.cargo_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id UUID NOT NULL REFERENCES cargo.shipments(id),
    description TEXT NOT NULL,
    weight_kg DECIMAL(10,2),
    attributes JSONB DEFAULT '{}',  -- Flexible per-item data
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Vessel attributes that vary by type
CREATE TABLE vessel.vessels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imo_number VARCHAR(10) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    attributes JSONB DEFAULT '{}',  -- Type-specific data
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

## Alternatives Considered

### 1. Multiple Database Instances (One Per Service)
Each service gets its own PostgreSQL container.
- **Pros:** Maximum isolation, independent backup/restore, no schema coupling.
- **Cons:** 7+ PostgreSQL containers consuming significant RAM (~128MB each), complex Docker Compose setup, no cross-service joins (by design), higher operational overhead.
- **Verdict:** Rejected — excessive resource consumption for a single-machine deployment.

### 2. Shared Schema (Single Database, Shared Tables)
All services read/write to the same tables.
- **Pros:** Simplest setup, easy cross-service queries, minimal configuration.
- **Cons:** Tight coupling (schema changes affect all services), no ownership boundaries, violates SBA autonomy principle, single point of schema evolution conflicts.
- **Verdict:** Rejected — violates the loose coupling principle of SBA.

### 3. MongoDB (Document Database)
A NoSQL document store with flexible schemas.
- **Pros:** Schema flexibility, horizontal scaling, JSON-native storage.
- **Cons:** No ACID transactions across documents (multi-document transactions are limited), no foreign keys, no SQL familiarity for team, JPA/Hibernate integration requires MongoDB-specific drivers, loses relational query power.
- **Verdict:** Rejected — ACID compliance and relational modeling are critical for scheduling and financial data.

### 4. MySQL 8
A popular relational database.
- **Pros:** Widely used, good performance, strong community.
- **Cons:** No native JSONB support (JSON type is less capable), no schema-level isolation without separate databases, less advanced feature set than PostgreSQL, no `GEN_RANDOM_UUID()` without extensions.
- **Verdict:** Rejected — PostgreSQL's JSONB and schema features are superior for this use case.

### 5. H2 (In-Memory Database)
An in-memory Java database.
- **Pros:** Zero setup, fast for tests, embedded.
- **Cons:** Not persistent, limited SQL features, not suitable for production, data loss on restart.
- **Verdict:** Rejected — suitable only for unit tests, not as the primary data store.

## Rationale

PostgreSQL 17 with schema-per-service is the optimal balance:

1. **Verified deployment:** PostgreSQL 17 is confirmed running on the development machine (`psql --version` verified).
2. **Schema isolation:** Each service owns its schema, preventing accidental cross-service data access while sharing a single instance.
3. **ACID compliance:** Full transaction support for berth scheduling, cargo manifests, and compliance records.
4. **JSONB support:** Flexible attribute storage without schema migrations when adding new fields to vessels or cargo items.
5. **Resource efficiency:** Single PostgreSQL instance consumes ~256MB RAM versus 7+ containers for multi-instance approach.
6. **Mature tooling:** pgAdmin, DBeaver, and IntelliJ DataGrip provide excellent schema management.
7. **Free and open-source:** No licensing costs for the student project.
8. **Flyway integration:** ADR-010 specifies Flyway for migrations, which works natively with PostgreSQL schemas.

### Cross-Service Data Access Pattern

When a service needs data from another service's schema, it uses the service API (REST), not direct database access:

```
berth-service needs vessel data → calls vessel-service API
cargo-service needs berth data → calls berth-service API
analytics-service needs all data → calls each service's API or uses event stream
```

This enforces loose coupling even at the data layer.

## Consequences

### Positive
- **Single instance simplicity** — one PostgreSQL container to manage, backup, and monitor.
- **ACID compliance** — full transaction support for critical operations (scheduling, cargo manifests).
- **Schema isolation** — logical separation prevents cross-service coupling at the database level.
- **JSONB flexibility** — handle varying attributes without schema migrations.
- **Resource efficiency** — ~256MB RAM for one instance vs. ~896MB for seven.
- **SQL power** — full SQL support for complex queries, aggregations, and reporting.
- **Free and open-source** — no licensing costs.
- **Flyway support** — native integration for version-controlled schema migrations.

### Negative
- **Schema coupling risk** — if two services create tables with the same name in different schemas, conflicts are possible (mitigated by naming conventions and Flyway naming).
- **Single point of failure** — one PostgreSQL instance failure affects all services (acceptable for single-machine deployment; mitigated by Docker restart policy).
- **Shared resource contention** — high load on one service's schema could affect others (mitigated by connection pooling and query optimization).
- **No independent scaling** — cannot scale the database independently per service (acceptable for the project scope).
- **Cross-schema queries not allowed** — services cannot join across schemas (this is a feature, not a bug — enforces loose coupling).

## Related

- ADR-001: Architecture Style — SBA (schema-per-service supports service autonomy)
- ADR-003: Backend Framework — Spring Boot 3.x (Spring Data JPA for database access)
- ADR-010: Database Migration — Flyway (schema version control)
- PostgreSQL 17 Documentation: https://www.postgresql.org/docs/17/
- JSONB Documentation: https://www.postgresql.org/docs/17/datatype-json.html
