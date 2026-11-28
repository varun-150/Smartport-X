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

## JWT signing keys

Local development may omit keystore settings; identity then generates an ephemeral RSA key for that
process. Tokens issued before a restart will no longer validate after that restart in this development
mode. **Do not use ephemeral keys in production.**

Production must start with the `prod` Spring profile and a persistent PKCS#12 keystore containing an
RSA private key and matching certificate. Provision the keystore out-of-band, mount it read-only, and
supply these environment variables through the deployment secret manager (never commit the keystore or
password):

- `SPRING_PROFILES_ACTIVE=prod`
- `JWT_KEYSTORE_LOCATION=/run/secrets/smartportx-jwt.p12`
- `JWT_KEYSTORE_PASSWORD=<secret-manager-provided password>`
- `JWT_KEY_ALIAS=<private-key entry alias>`

Identity fails startup in the `prod` profile if a keystore is not configured. Partial keystore
configuration or a missing/non-RSA private-key entry also fails startup. Every identity replica must
use the same active signing key and alias; plan key rotation and JWKS cache overlap before rotating keys.

## Run (native dev, no Docker)

See `scripts/dev-up.ps1` (created in M1). Local Postgres 17.11 on `localhost:5432` is the dev database
(`scripts/init-databases.sh`); RabbitMQ required for event flow.

## Status

Phase 4 — Implementation in progress. Tracked in `.smartport/IMPLEMENTATION-STATUS.md`.

**Honesty rule:** no status beyond `SCAFFOLDED` is claimed until a milestone's tests pass. Nothing is
"running" or "verified" without evidence (`.smartport/EVIDENCE.md`).
