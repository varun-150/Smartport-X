# SmartPortX

Intelligent Distributed Port Logistics &amp; Container Yard Orchestration — Phase 4 implementation.

Service-based architecture: 8 services + discovery + gateway + web dashboard, event-driven on RabbitMQ,
JWT (RS256) security, PostgreSQL schema-per-service, Docker Compose deployment.

## Repository layout

| Path | Purpose |
|------|---------|
| `pom.xml` | Maven parent (Spring Boot 3.5.x / Java 25 / Spring Cloud 2025.1.x) |
| `smartportx-common/` | Shared library: RFC 9457 errors, correlation id, events, observability |
| `smartportx-eureka/` | Eureka discovery server (8761) |
| `smartportx-gateway/` | Spring Cloud Gateway edge (8080) |
| `smartportx-identity/` | Identity &amp; auth (8081) |
| `smartportx-yard/` | Yard orchestration (8082) |
| `smartportx-gate/` | Gate &amp; appointments (8083) |
| `smartportx-container/` | Container lifecycle (8084) |
| `smartportx-vessel/` | Vessel schedules (8085) |
| `smartportx-integration/` | EDIFACT / IoT / customs adapters (8086) |
| `smartportx-notification/` | Notifications &amp; WebSocket (8087) |
| `smartportx-dashboard/` | React 18 + TypeScript dashboard (3000) |
| `.smartport/` | Architecture docs, plans, findings, decisions, evidence |
| `scripts/` | Database init, rabbit topology, dev stack scripts |
| `.github/workflows/` | CI pipeline |

## Build

Backend (all services):

```
mvn -q clean verify
```

Frontend:

```
cd smartportx-dashboard
npm install
npm run lint && npm run build && npm test
```

## Run (native dev, no Docker)

See `scripts/dev-up.ps1` (created in M1). Local Postgres 17.11 on `localhost:5432` is the dev database
(`scripts/init-databases.sh`); RabbitMQ required for event flow.

## Status

Phase 4 — Implementation in progress. Tracked in `.smartport/IMPLEMENTATION-STATUS.md`.

**Honesty rule:** no status beyond `SCAFFOLDED` is claimed until a milestone's tests pass. Nothing is
"running" or "verified" without evidence (`.smartport/EVIDENCE.md`).