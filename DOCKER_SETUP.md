# SmartPortX — Local Docker Integration & Runtime Setup

Scope: the existing legacy **SmartPortX** codebase in this workspace. This document does **not**
describe the separate HarborFlow project, and no application service was renamed, restructured or
rewritten. All changes are infrastructure configuration plus documentation.

Commands below are PowerShell, run from the repository root `D:\D Storage\Smartport-X`.

---

## 1. Architecture actually deployed

```
Browser ── :3100 ──> dashboard (nginx:3000)
                         │ /api/** and /ws/**  (same origin)
                         ▼
                    gateway:8080  ── lb://<service> via Eureka ──> 7 business services
                         │                                      identity 8081
                         │                                      yard     8082
                         ├── JWKS ──────────────────────────────> gate     8083
                         │                                      container 8084
                         └── WS /ws/** ─────────────────────────> vessel   8085
                                                                 integration 8086
                                                                 notification 8087
   eureka:8761  (discovery, basic-auth dashboard)
   postgres:5432 (single DB `smartportx`, 7 schemas, SCRAM-SHA-256, named volume)
   rabbitmq:5672 / 15672 (domain events: 5 publishers, 1 consumer)
   prometheus:9090 · grafana:3000→host 3001 · jaeger:16686
```

All containers share the dedicated bridge network `smartportx-network` and talk to each other
**only by Compose service name** (`postgres`, `eureka`, `gateway`, `identity`, …).
No container uses `localhost` to reach another container.

### Host ports

| Service | Host | Container |
|---|---|---|
| PostgreSQL | `127.0.0.1:5433` (`POSTGRES_PORT`) | 5432 |
| Eureka | `127.0.0.1:8761` | 8761 |
| Gateway | `127.0.0.1:8080` | 8080 |
| Identity / Yard / Gate / Container / Vessel / Integration / Notification | `127.0.0.1:8081`–`8087` | same |
| Dashboard | `${DASHBOARD_PORT}` (default 3100) | 3000 |
| RabbitMQ AMQP / management | `127.0.0.1:5672` / `15672` | same |
| Prometheus / Grafana / Jaeger | `127.0.0.1:9090` / `3001` / `16686` | 9090 / 3000 / 16686 |

---

## 2. Prerequisites

- Docker Desktop with a running Linux engine and Compose v2+ (`docker compose version`).
- `.env` in the repository root. `.env.example` is the template; it contains placeholders only.
  `.env` is gitignored and excluded from both image build contexts.
- `.env` must define every `${VAR:?...}` used by `docker-compose.yml`; Compose fails fast if one is
  missing. Never commit `.env` or replace its credentials.
- Required images are pulled automatically on first start (Java 25 builder/runtime, PostgreSQL
  17.11, RabbitMQ 3-management, Prometheus, Grafana, Jaeger, Node 24, nginx 1.27-alpine).

---

## 3. Start / stop / inspect

```powershell
# First start only — compiles the 11-module Maven reactor inside the image and builds the dashboard
docker compose up -d --build

# Normal start (reuses existing images and volumes)
docker compose up -d

# Ordered-ish inspection: infrastructure first, then services
docker compose ps
docker compose logs -f --tail=200 postgres
docker compose logs -f --tail=200 eureka
docker compose logs -f --tail=200 gateway

# Stop without losing data (NEVER add -v)
docker compose stop
docker compose start
```

Startup order is enforced by `healthcheck` + `depends_on: condition: service_healthy`:
PostgreSQL and RabbitMQ → Eureka → identity/container → yard/gate/vessel/integration/notification
→ gateway → dashboard. Prometheus, Grafana and Jaeger have no application dependency.

### Data-preservation rules (mandatory)

- **Never** run `docker compose down -v`, `docker volume prune`, or `docker system prune --volumes`.
- **Never** delete `smartportx-postgres-data` or reinitialise an existing database.
- `scripts/init-databases.sh` runs only on a fresh, empty PostgreSQL data directory. It creates the
  application role and 7 schemas; it contains no `DROP`, `DELETE` or `TRUNCATE`.
- Flyway migrations on service boot create tables; they are non-destructive and must not be edited
  after they have been applied.
- Demo seeders insert rows only when the target rows do not already exist.

---

## 4. Configuration wiring (what was corrected and why)

| Area | Problem in the checked-in Compose file | Correction |
|---|---|---|
| Gate/vessel HTTP clients | `CONTAINER_BASE_URL`, `YARD_BASE_URL`, `INTEGRATION_BASE_URL` were unset, so Spring defaults `localhost:8084/8082/8086` pointed at the container itself | Set to `http://container:8084`, `http://yard:8082`, `http://integration:8086`; added health-gated dependencies |
| Demo users | `SEED_DEMO_USERS` does not bind to `smartportx.security.seed-demo-users` | Compose now sets `SMARTPORTX_SECURITY_SEED_DEMO_USERS` from the same `.env` switch |
| RabbitMQ | `RABBITMQ_*` is not a Spring Boot property; event flags defaulted to `false` | Use `SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD`; publishers set `EVENTS_PUBLISH_TO_RABBITMQ=true`, notification sets `EVENTS_CONSUME_FROM_RABBITMQ=true` |
| IoT simulation | `IOT_MODE=simulation` is not read by the application | Use `IOT_SIM_ENABLED` (maps to `smartportx.integration.iot.enabled`) |
| Testimonials | `SEED_TESTIMONIALS` does not bind to `smartportx.notification.seed-testimonials` | Use `SMARTPORTX_NOTIFICATION_SEED_TESTIMONIALS` |
| Network trust | `.env` trust/monitoring CIDRs were never injected | Injected into every service that evaluates them |
| PostgreSQL | Healthcheck and 7 JDBC URLs hardcoded `smartportx` | Both derive from `${POSTGRES_DB}` / `$POSTGRES_USER` inside the container |
| Dashboard build context | No `.dockerignore`; host `node_modules/` and `dist/` could enter the image | Added `smartportx-dashboard/.dockerignore` |
| Dashboard runtime | nginx runs as `nginx` but `/var/run` is a symlink to `/run`, so `chown -R /var/run` did not make `/run/nginx.pid` writable → restart loop | Changed the dashboard Dockerfile to `mkdir`/`chown` `/run` directly (one-line fix); dashboard is now healthy |

Nothing in `src/`, `pom.xml`, the root `Dockerfile`, migrations, or `.env` was changed. The only
application-adjacent change is the dashboard Dockerfile line described above.

---

## 5. Verification (evidence recorded after the run in section 6)

Commands that must pass; all of them check behaviour, not just `running` state:

```powershell
# 1. Container state and restart loops
docker compose ps
docker inspect --format '{{.Name}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}} restarts={{.RestartCount}}' `
  (docker compose ps -q)

# 2. PostgreSQL auth + schemas/tables (no credentials printed)
docker exec smartportx-postgres psql -U ${env:POSTGRES_USER} -d ${env:POSTGRES_DB} -tAc `
  "select count(*) from information_schema.tables where table_schema like 'smartportx_%'"

# 3. Eureka registrations (no auth needed for /eureka/**)
$e = Invoke-RestMethod http://localhost:8761/eureka/apps
$e.applications.application | Select-Object name, @{n='instances';e={@($_.instance).Count}}

# 4. API health through the gateway (use -UseBasicParsing in Windows PowerShell 5.1)
Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health | Select-Object StatusCode

# 5. Login through the gateway (token value is never printed)
#    Note: repeated logins trigger rate limiting (429); do not loop this probe.
$password = Read-Host 'Demo admin password' -AsSecureString
$plain = [System.Net.NetworkCredential]::new('', $password).Password
$body = @{username='admin'; password=$plain} | ConvertTo-Json -Compress
$r = Invoke-WebRequest -UseBasicParsing -Method Post -Uri http://localhost:8080/api/v1/auth/login `
  -ContentType application/json -Body $body
"login_status=$($r.StatusCode) token_present=$([bool]($r.Content -match 'accessToken'))"

# 6. Frontend and its same-origin proxy
Invoke-WebRequest -UseBasicParsing http://localhost:3100/login | Select-Object StatusCode
Invoke-WebRequest -UseBasicParsing http://localhost:3100/api/v1/auth/jwks | Select-Object StatusCode

# 7. RabbitMQ exchange/queue created by the apps
docker exec smartportx-rabbitmq rabbitmqctl list_queues name | Select-String 'domain-events'
```

---

## 6. Verification results (live run, 2026-11-27)

All checks below were executed against the running stack, not inferred from `running` state.
Log timestamps are UTC; host timezone is IST (+05:30).

**Build.** `docker compose up -d --build` compiled the full Maven reactor — 11/11 modules
`SUCCESS` in 56.4 s — and built the Vite dashboard in 2.21 s. All 11 local images were created.

**Container state (final).** 15/15 containers `running`; all 12 containers with healthchecks
report `healthy`; `RestartCount=0` for every container. Prometheus, Grafana and Jaeger have no
healthcheck configured. Named volumes `smartportx-postgres-data`, `smartportx-rabbitmq-data`,
`smartportx-prometheus-data`, `smartportx-grafana-data` and network `smartportx-network` exist.

**Database.** Authenticated as `POSTGRES_USER` against `POSTGRES_DB`; 7 schemas with 38 tables:

```
smartportx_container|5   smartportx_gate|5       smartportx_identity|6
smartportx_integration|5 smartportx_notification|4 smartportx_vessel|5
smartportx_yard|8
```

Flyway history exists in all 7 schemas; seed data is present (`users=5`, `testimonials=12`).
Live application connections: 70 JDBC connections as role `smartportx_app`. A single
`password authentication failed` line for that role appeared at 01:02:24 during verification,
but it did not recur after 01:03:00 and did not interrupt any of the 70 live connections —
treated as a transient event, not an ongoing credential mismatch.

**Discovery.** `GET /eureka/apps` returned 8 registered applications, one instance each:
`SMARTPORTX-IDENTITY`, `-YARD`, `-GATE`, `-CONTAINER`, `-VESSEL`, `-INTEGRATION`,
`-NOTIFICATION`, `-GATEWAY`.

**HTTP.** `/actuator/health` returned `200` through ports 8080–8087 and 8761.
`http://localhost:3100/login` returned `200`; the same-origin proxy
`http://localhost:3100/api/v1/auth/jwks` returned `200` (403-byte JWKS), proving nginx → gateway
→ identity works. Login via `POST /api/v1/auth/login` returned `200` with a 900-char RS256 token.
An authenticated `GET /api/v1/containers` through the dashboard proxy returned `200`, and
`POST /api/v1/appointments/preview` returned `200` with real computed data (confirmation number,
slots, cost). Repeated login probes correctly triggered `429` rate limiting.

**Inter-service wiring.** From inside `smartportx-gate`, `http://container:8084/actuator/health`,
`http://yard:8082/actuator/health` and `http://integration:8086/actuator/health` all returned
`200` (the first probe returned `000` only during the startup race, then succeeded 4/4 on retry).
Verified environment keys (values not reproduced here) are
`CONTAINER_BASE_URL=http://container:8084`, `YARD_BASE_URL=http://yard:8082`,
`INTEGRATION_BASE_URL=http://integration:8086`, `IDENTITY_JWKS_URI=http://identity:8081/...`,
`SPRING_RABBITMQ_HOST=rabbitmq` in gate and vessel.

**Messaging.** Broker declares exchange `smartportx.domain-events` (topic) and queue
`notification.domain-events` with 1 consumer. Notification logs show an inbound event processed
(`alert published integration.IotAlertTriggered ...`), so publisher → broker → consumer wiring is
live.

**Known limitations / notes.**
- Identity uses ephemeral RS256 keys in the `docker` profile: restarting the identity container
  invalidates previously issued tokens until users log in again. Use the `prod` profile with a
  persistent keystore if token continuity across restarts is required.
- A 400 response during container creation was due to an incomplete test payload, not a stack
  fault; the gateway had briefly had no identity instance during the earlier startup race.
- The transient DB auth failure and the initial dashboard restart loop are both resolved; no
  application source, credential value, schema or migration was modified to fix them.

---

## 7. Troubleshooting

| Symptom | Check / fix |
|---|---|
| `docker info` fails | Start Docker Desktop; confirm WSL2 is healthy. |
| `required variable is not set` | Copy `.env.example` → `.env` and fill every placeholder. Do not invent new credential values for an existing volume. |
| Service `unhealthy` | `docker compose logs --tail=200 <service>`; check DB URL, Eureka DNS name, JWKS URL. |
| Postgres role auth failures after password change | `init-databases.sh` never overwrites an existing role password. Restore the original `APP_ROLE_PASSWORD` rather than rotating it against an existing volume. |
| Gateway 401 | Log in first; `/login`, `/refresh`, `/jwks` and actuator health/info are the only public routes. |
| WebSocket rejected | `SMARTPORTX_WEBSOCKET_ALLOWED_ORIGINS` must contain the exact browser origin, e.g. `http://localhost:3100`. |
| Prometheus 403 on `/actuator/prometheus` | Ensure the Docker bridge subnet is covered by `SMARTPORTX_MONITORING_ALLOWED_NETWORKS`. |
| Blank dashboard | Confirm nginx proxies `/api` and `/ws` to `gateway:8080` and the gateway is healthy. |
| Dashboard restart loop with `open() "/run/nginx.pid" failed (13: Permission denied)` | The runtime stage must `mkdir`/`chown` `/run`, not `/var/run` (symlink). Already fixed in `smartportx-dashboard/Dockerfile`. |
| `Invoke-WebRequest` fails with "NonInteractive mode. Read and Prompt" | Windows PowerShell 5.1 needs `-UseBasicParsing`. |

---

## 8. Related files

- `docker-compose.yml` — corrected Compose environment wiring (primary change).
- `smartportx-dashboard/Dockerfile` — one-line fix: `mkdir`/`chown` `/run` instead of the
  `/var/run` symlink so nginx can write its PID file.
- `smartportx-dashboard/.dockerignore` — new; keeps host artifacts out of the frontend image.
- `DOCKER_SETUP.md` — this runbook (new).
- `SMART_PORTICS_DOCKER_SETUP.md` — earlier historical runbook; kept unchanged for reference.
- `scripts/init-databases.sh`, `scripts/rabbit-topology.sh` — non-destructive init/topology helpers.
  The topology script is not required for application startup and is not run by Compose.
