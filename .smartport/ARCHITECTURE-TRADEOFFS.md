# SmartPortX — Architecture Trade-offs

**Document:** ARCHITECTURE-TRADEOFFS
**Version:** 1.0
**Date:** 2026-08-27
**Status:** FINAL
**Owner:** Architect

---

## Table of Contents

1. [Purpose & Scope](#1-purpose--scope)
2. [How to Read This Document](#2-how-to-read-this-document)
3. [Architecture Style Trade-offs](#3-architecture-style-trade-offs)
4. [Database Trade-offs](#4-database-trade-offs)
5. [Communication Trade-offs](#5-communication-trade-offs)
6. [Security Trade-offs](#6-security-trade-offs)
7. [Deployment Trade-offs](#7-deployment-trade-offs)
8. [Monitoring Trade-offs](#8-monitoring-trade-offs)
9. [Testing Trade-offs](#9-testing-trade-offs)
10. [Frontend Trade-offs](#10-frontend-trade-offs)
11. [Performance vs. Simplicity Trade-offs](#11-performance-vs-simplicity-trade-offs)
12. [Intentional Technical Debt](#12-intentional-technical-debt)
13. [Complexity Scoring](#13-complexity-scoring)
14. [Decision Reversibility](#14-decision-reversibility)
15. [Conclusion](#15-conclusion)
16. [Appendix A: Decision → ADR Mapping](#appendix-a-decision--adr-mapping)

---

## 1. Purpose & Scope

This document provides a comprehensive analysis of the architectural trade-offs that shaped
SmartPortX, a Service-Oriented Architecture (SOA) container port logistics platform.

Every architectural decision necessarily involves a trade-off: choosing one option means
giving up the benefits of another. This document makes those trade-offs explicit so that:

- **New team members** understand *why* the architecture looks the way it does, not just
  *what* it looks like.
- **Reviewers and evaluators** can assess whether each decision was well-justified against
  the stated constraints (small team, single semester, single machine, SOA mandate).
- **Future maintainers** know what was knowingly deferred and what would need to change to
  revisit a decision.

The choices analyzed here correspond to **ADR-001 through ADR-013**. The decision — and the
rejected alternative each decision chose against — is presented with its known consequences,
costs, and risks.

### Scope Boundary

This document does **not** re-derive the decisions (that is the job of the ADRs). It analyzes
the *trade-off space*: what was gained, what was lost, and under what conditions a different
choice would have been better. Where a choice carries ongoing operational cost, we call it out
explicitly so it is never "set and forgotten."

---

## 2. How to Read This Document

### Trade-off Format

Each trade-off is presented as a **chosen option vs. an alternative**, using a consistent
format:

- **Chosen:** The option actually adopted (e.g., Service-Based Architecture).
- **Alternative:** The option that was considered and rejected (e.g., Monolith).
- **Advantages of choice (+):** What we gained by choosing this option.
- **Disadvantages of choice (−):** What we gave up or now must manage.

### Scoring Notation

Complexity scores (Section 13) use a 1–5 scale:

| Score | Meaning |
|-------|---------|
| 1 | Trivial — minimal logic, few integration points, simple data |
| 2 | Low — some logic, one or two integration points, straightforward data |
| 3 | Moderate — meaningful business rules, several integrations, relational data |
| 4 | High — complex business rules, many integrations, nuanced data invariants |
| 5 | Very high — the hardest area in the whole platform |

### Cross-References

Relevant ADR numbers are cited inline, e.g. **(ADR-001)**. A full mapping is in
[Appendix A](#appendix-a-decision--adr-mapping).

---

## 3. Architecture Style Trade-offs

**Chosen: Service-Based Architecture (SBA)** — **(ADR-001)**

SBA implements each bounded context as a logically separate service with its own domain
model, database schema, and API contract, deployed together in a single Docker Compose
stack, but designed to be separable later.

### 3.1 SBA vs. Monolith

| | Chosen — SBA | Rejected — Monolith |
|---|---|---|
| Coupling | Loose — services interact via contracts | Tight — shared classpath, shared state |
| Deployment | Independent units | Single deployable unit |
| Failure isolation | One service can fail without taking down all | One bug can crash the whole system |
| Team parallelism | Developers work in parallel on boundaries | High merge friction on shared code |
| Operational cost | Higher (7+ processes, broker, gateway) | Lower |
| Consistency | Eventual across services | Strong (single transaction scope) |
| Debugging | Hardsingle — spans multiple processes | Easier — one stack trace |

**What we gained (+)**

- **Loose coupling** — a change in `container-service` does not require rebuilding or
  redeploying `vessel-service`. Services interact only through documented contracts.
- **Independent deployability** — each service *can* be deployed separately, satisfying the
  SOA course mandate and preserving an upgrade path.
- **Team autonomy** — a 4-person team can split work by service boundary with minimal
  merge conflicts. Each developer owns a service end-to-end.
- **Scaled failure isolation** — a runaway query in `yard-service` does not take down
  `gate-service` (they are separate processes, even if they share a database instance).

**What we gave up (−)**

- **Operational complexity** — 8 backend services, a gateway, a broker, and a database all
  need to run, connect, and be observed. A monolith has one process to health-check.
- **Eventual consistency** — because a single business transaction may span services
  (e.g., a gate-out touches container status and yard position), data across services can be
  momentarily inconsistent. This requires compensating logic and idempotency handling.
- **Debugging difficulty** — an error may originate in service A, be triggered by an event
  from service B, and surface in service C. Distributed tracing is required to make this
  tractable.

**Verdict:** SBA is the correct middle ground for this project. A monolith would violate the
SOA mandate and the entire point of the exercise; the runtime and debugging complexity of SBA
is the price of demonstrating service orientation.

---

### 3.2 SBA vs. Full Microservices

| | Chosen — SBA | Rejected — Full Microservices |
|---|---|---|
| Database | Shared instance, schema-per-service | One database per service |
| Deployment | Single Compose stack | Independent CI/CD per service |
| Infra | None beyond Compose (Eureka only for discovery) | Service mesh, orchestrator (K8s), tracing infra |
| Scaling | Coarse — scale whole stack or not at all | Granular — scale only hot service |
| Team capacity | Achievable in one semester | DevOps burden alone consumes the semester |

**What we gained (+)**

- **Simpler deployment** — one `docker-compose.yml` brings the entire platform up. No
  Kubernetes YAML, no Helm charts, no per-service registries.
- **Lower infrastructure cost** — a single laptop/VM with 8 GB RAM runs everything
  (single PostgreSQL, single RabbitMQ). Per-service databases would triple the RAM overhead.
- **One tracking/monitoring surface** — one broker, one database, one Compose topology to
  reason about.

**What we gave up (−)**

- **Less granular scaling** — we cannot scale only the busy `gate-service`; we scale the
  whole stack (or don't scale at all). Acceptable since load is bounded for a demo.
- **Potential service boundary violations** — because services share a database instance and
  a repo, there is a temptation to couple to another service's schema or to "just make a
  quick change" across a boundary. Schema-per-service and contract discipline mitigate this,
  but it is a maintained discipline, not automatic.

**Verdict:** Full microservices would be architecturally "purer" but operationally
impossible for a small student team in one semester. SBA preserves the *learning value* of
microservices (service decomposition, contracts, communication) without the operational tax.

---

### 3.3 SBA vs. Event-Driven Architecture (EDA)

| | Chosen — SBA (REST + RabbitMQ hybrid) | Rejected — Event-driven only |
|---|---|---|
| Request/response | First-class via REST | Awkward — everything is a stream |
| Real-time reactivity | Via WebSocket + RabbitMQ fanout where needed | Native — but everywhere, even where unnecessary |
| Mental model | Familiar — call a service, get a result | Requires reasoning in events, projections, CQRS |
| Debugging | Snapshot state at any time | Reconstruct state from event log |
| Polling need | More for some read models | Less — but you pay in complexity |

**What we gained (+)**

- **Simpler mental model** — most operations (login, create container, book gate slot) are
  request/response. Developers reason in terms of calls and returns, not event flows.
- **Easier debugging** — a synchronous call gives immediate feedback and a stack trace. No
  need to replay an event log to understand current state.
- **Synchronous consistency when needed** — berth assignment, gate validation, and
  authentication all benefit from strong, immediate consistency via REST.

**What we gave up (−)**

- **Less real-time reactivity by default** — pure EDA would push position updates and state
  changes reactively everywhere. We compensate with targeted WebSocket (vessel tracking)
  and RabbitMQ fanout for domain events, but some consumers still poll for fresh data.
- **More polling needed** — some read models (dashboard metrics, status aggregates) are
  refreshed by schedule or on-demand rather than pushed the instant they change.

**Verdict:** The chosen hybrid (REST + RabbitMQ + WebSocket) is strictly more practical than
pure EDA for this system. We get event-driven flow where the domain is genuinely
event-driven (yard moves, gate transactions, vessel arrivals) and request/response where it
is not (admin CRUD, auth). Pure EDA would force unnecessary CQRS and projection machinery
onto every single feature.

---

### 3.4 SBA vs. Modular Monolith

| | Chosen — SBA | Rejected — Modular Monolith |
|---|---|---|
| Deployment | Multiple deployable units | One deployable unit (internal modules) |
| Technology flexibility | Each service could use different tech | One technology for all |
| Infrastructure | Broker, gateway, discovery needed | None — in-process |
| Network latency | Real inter-process/HTTP calls | In-process, negligible |
| Independent deployment | Yes | No (by design) |

**What we gained (+)**

- **Independent deployment** — satisfies the SOA mandate and allows hot-fixing one service
  without redeploying others.
- **Technology flexibility** — in principle, any service could be rewritten in another stack
  without touching the rest (in practice, all use Spring Boot for consistency, but the option
  exists).

**What we gave up (−)**

- **More infrastructure** — a modular monolith would need none of the broker, gateway, or
  discovery machinery that SBA requires. Every piece of distributed infrastructure is
  additional surface to operate and debug.
- **Network latency** — inter-service calls cross process and network boundaries rather than
  method-call boundaries, adding milliseconds and failure modes (timeouts, partial failures,
  retries).

**Verdict:** A modular monolith is tempting for a small team, but it does **not** demonstrate
the SOA patterns (contract-based inter-service interaction, messaging, a gateway) that the
course and the project brief require. SBA, despite the extra infrastructure, is the right
call.

---

## 4. Database Trade-offs

**Chosen: Single PostgreSQL 17 with Schema-per-Service** — **(ADR-004)**

### 4.1 Schema-per-Service vs. Multiple PostgreSQL Instances

| | Chosen — Single instance, schema-per-service | Rejected — One instance per service |
|---|---|---|
| Operations | One container to manage, back up, monitor | 7+ containers (each ~128MB+ RAM) |
| Backup | Single backup covers all data | Per-service backup orchestration |
| Isolation | Logical (schema boundary) | Physical (process boundary) |
| Single point of failure | Yes — one instance | No (but more to fail) |
| Resource cost | ~256MB total | ~896MB+ for seven |

**What we gained (+)**

- **Simpler operations** — one PostgreSQL container, one health check, one backup job.
- **Single backup** — a single `pg_dump` captures the entire system consistently.
- **Schema isolation** — each service owns its schema (`smartportx_identity`,
  `smartportx_yard`, …), preventing accidental cross-service table access while sharing
  infrastructure.

**What we gave up (−)**

- **Schema coupling risk** — if two services use colocated tables in a shared instance,
  naming collisions or accidental cross-references are possible. Mitigated by strict
  schema ownership and Flyway management.
- **Single point of failure** — a crash of the shared instance takes down every service.
  Acceptable for a single-machine demo, mitigated by Docker restart policy; *not* acceptable
  for production (mitigated in production by moving to managed RDS/Cloud SQL).

**Verdict:** The resource and operational savings of a single instance massively outweigh
the isolation benefits of seven instances for a single-machine demo. Physical isolation is a
production concern, not a student-demo concern.

---

### 4.2 Schema-per-Service vs. Shared Schema

| | Chosen — Schema-per-service | Rejected — Shared tables |
|---|---|---|
| Data isolation | Logical boundary per service | All services touch shared tables |
| Migrations | Independent per service (Flyway) | Contended, coordinated lock-step |
| Cross-service queries | Not allowed (by design) — via API/event | Trivial — direct SQL joins |
| Storage | Slight duplication (references, denormalized copies) | Minimal duplication |
| Service autonomy | Supported | Violated |

**What we gained (+)**

- **Data isolation** — a schema change (even an accidental destructive one) affects only the
  owning service.
- **Independent migrations** — each service's Flyway migration can evolve on its own
  schedule (ADR-010), without coordinating with other services.

**What we gave up (−)**

- **Cross-service queries harder** — you cannot write `SELECT ... JOIN yyy.zztable`
  across schemas. Services must call another service's REST API or subscribe to its events
  to obtain data. This is a deliberate feature (enforces loose coupling) but it is more
  code than a join.
- **More storage** — denormalized copies of reference data and cached state consume
  slightly more space. Negligible at this scale.

**Verdict:** The isolation and autonomy benefits are worth the loss of ad-hoc cross-schema
joins. For an SOA project, cross-service joins are an anti-pattern anyway.

---

### 4.3 PostgreSQL vs. MongoDB

| | Chosen — PostgreSQL (relational) | Rejected — MongoDB (document) |
|---|---|---|
| ACID | Full, across rows and transactions | Limited multi-document transactions |
| SQL | Full SQL + JSONB | No SQL; JSON-like queries only |
| Maturity | Extremely mature | Mature, but fewer invariants |
| Schema flexibility | JSONB columns give partial flexibility | Fully flexible documents |
| Scaling | Vertical (single instance) | Horizontal (sharding) |

**What we gained (+)**

- **ACID** — critical for scheduling, gate transactions, and cargo manifests where partial
  writes would be catastrophic.
- **SQL flexibility** — powerful joins, aggregations, and window functions for reporting and
  analytics.
- **Mature** — well-documented, best-in-class tooling (pgAdmin, DBeaver, DataGrip), and the
  team already has PostgreSQL verified on the dev machine.

**What we gave up (−)**

- **Less flexible schema** — adding an arbitrary new field to a container requires either a
  migration or an explicit JSONB attribute. MongoDB would allow unconstrained documents.
- **Vertical scaling only** — one instance scales up, not horizontally across shards.
  Acceptable at demo scale; MongoDB's horizontal scaling is irrelevant to a single-machine
  project.

**Verdict:** For a system with financial-grade invariants (scheduling, transactions) and
relational data, ACID + SQL is decisive. MongoDB's schema flexibility would buy little
because JSONB already covers the variable-attribute cases (vessel type data, container
attributes).

---

### 4.4 PostgreSQL vs. MySQL

| | Chosen — PostgreSQL | Rejected — MySQL |
|---|---|---|
| JSONB | Native binary JSON + indexing | JSON type with weaker features |
| Concurrency | MVCC, better concurrent update behavior | Good, but historically more locking |
| Extensions | Rich (PostGIS, pgvector, …) | Fewer advanced extensions |
| Community | Large, but smaller than MySQL | Largest relational community |

**What we gained (+)**

- **JSONB support** — true binary JSON with GIN indexing for the variable-attribute data in
  vessels and containers (ADR-004).
- **Better concurrency** — PostgreSQL's MVCC handles concurrent reads/writes more gracefully,
  important for gate and yard services under simulated load.
- **Extensions** — flexibility to add PostGIS for geospatial vessel positions if needed.

**What we gave up (−)**

- **Smaller community** — MySQL has a larger install base and even more Stack Overflow
  answers. In practice, PostgreSQL's community is more than sufficient for this project.

**Verdict:** PostgreSQL's JSONB and concurrency advantages directly serve the project's
needs (flexible attributes + concurrent gate/yard operations). MySQL's community-size
advantage does not offset this.

---

## 5. Communication Trade-offs

**Chosen: REST (synchronous) + RabbitMQ (asynchronous) + WebSocket (real-time)** — **(ADR-005, ADR-006, ADR-007)**

### 5.1 REST — Synchronous

**Advantages (+)**

- **Simple** — every service is a predictable HTTP endpoint; no broker to reason about for
  CRUD operations.
- **Debuggable** — curl, Postman, Swagger; a request maps to a response with a clear
  status code.
- **Standard** — universally understood, tool-backed (OpenAPI), and what any external
  consumer expects.

**Disadvantages (−)**

- **Blocking** — callers wait synchronously; a slow downstream blocks the upstream.
- **Timeout risk** — inter-service calls need explicit timeouts and retry policies; without
  them a hung service cascades (mitigated by Resilience4j circuit breakers, ADR-006).

**Decision trade-off:** We accept blocking REST for operations that need immediate
consistency (CRUD, auth, validation) and route long-running or fire-and-forget work through
RabbitMQ instead.

---

### 5.2 RabbitMQ — Asynchronous

**Advantages (+)**

- **Decoupled** — producers publish events without knowing who consumes them.
- **Resilient** — messages persist; temporary consumer outages do not lose work.
- **Buffered** — what is published is not immediately required; downstream can catch up.

**Disadvantages (−)**

- **Eventual consistency** — consumers may lag; a read immediately after a write can be
  stale.
- **Debugging harder** — a message may be published, routed, consumed, and processed
  across several hops; tracing state is harder than a synchronous call.

**Decision trade-off:** We use RabbitMQ for domain events that benefit from decoupling and
buffering (yard moves, gate transactions, vessel arrivals, notifications) and accept the
eventual-consistency lag in the read models those events feed.

---

### 5.3 WebSocket — Real-time

**Advantages (+)**

- **Real-time** — vessel positions and status changes push to the UI instantly.
- **Efficient** — one persistent connection carries many updates, avoiding HTTP polling
  overhead.

**Disadvantages (−)**

- **Connection management** — the server must track, heart-beat, and clean up connections.
- **Scaling complexity** — sticky sessions/stateful connections complicate horizontal
  scaling (acceptable now; noted for production).

**Decision trade-off:** WebSocket is scoped to the genuinely real-time feature (vessel
tracking via `notification-service` proxy through the gateway) and is not used as a
general-purpose transport.

---

### 5.4 RabbitMQ vs. Kafka

| | Chosen — RabbitMQ | Rejected — Kafka |
|---|---|---|
| Setup | Simple — one broker, AMQP 0-9-1 | Requires ZooKeeper/KRaft cluster |
| Replay | None by default | Native (offset replay) |
| Event sourcing | Not supported | Natural fit |
| Throughput | Sufficient for demo load | Much higher |

**What we gained (+)**

- **Simpler setup** — RabbitMQ runs as a single container with a management console; no
  cluster coordination.
- **Lower throughput acceptable** — the project's event volume (thousands of events, not
  millions per second) is well within RabbitMQ's capacity.

**What we gave up (−)**

- **No event sourcing** — messages are consumed and acknowledged, not retained as an
  immutable log. We cannot replay history to rebuild state or audit retroactively.
- **No replay** — a new consumer cannot catch up on past events; it only sees new ones.

**Verdict:** RabbitMQ's operational simplicity wins decisively for this scale. Kafka's
replay and event-sourcing power is deferred (see Section 12, Intentional Technical Debt),
since we do not need log replay for the MVP.

---

### 5.5 REST vs. gRPC

| | Chosen — REST | Rejected — gRPC |
|---|---|---|
| Simplicity | Try it in a browser, OpenAPI docs | Requires codegen, protobuf tooling |
| Tools | Swagger, Postman, curl everywhere | Specialized tooling |
| Efficiency | JSON overhead, text protocol | Binary, compact, faster |
| Streaming | Not native (HTTP/1.1) | Native bidirectional streaming |

**What we gained (+)**

- **Simpler** — JSON over HTTP is debuggable and universally supported, including by the web
  frontend (browsers cannot call gRPC directly without a proxy).
- **More tools** — mature OpenAPI/contract tooling and the gateway ecosystem.

**What we gave up (−)**

- **Less efficient** — JSON serialization and text framing are heavier than protobuf.
- **No streaming** — bidirectional streaming requires gRPC; we use WebSocket for the one
  real-time need instead.

**Verdict:** REST is the right choice because the primary consumers are browsers and other
services that speak HTTP, and debuggability matters more than peak wire efficiency at this
scale.

---

## 6. Security Trade-offs

**Chosen: JWT with Refresh Token Rotation** — **(ADR-009)**

### 6.1 JWT vs. Session-based

| | Chosen — JWT (stateless) | Rejected — Server sessions |
|---|---|---|
| State | Stateless — token carries claims | Stateful — server stores sessions |
| Scalability | Any instance validates any token | Session store must be shared/distributed |
| Revocation | Hard — token valid until expiry | Easy — delete session |
| Payload | Larger (sent with every request) | Small (just a session id) |

**What we gained (+)**

- **Stateless** — the gateway validates the JWT signature and claims without consulting a
  central session store, making each service instance interchangeable and horizontally
  scalable.
- **Scalable** — no sticky-session requirement; any gateway instance can validate any token.

**What we gave up (−)**

- **Token revocation harder** — a stolen or logged-out token remains valid until it expires.
  Mitigated by short-lived access tokens + refresh token rotation (revoke on rotation).
- **Larger payload** — the JWT travels with every request, adding a few hundred bytes to
  each call.

**Verdict:** For a stateless, gateway-routed SOA, JWT's scalability and statelessness are a
better fit than server sessions, whose shared-store requirement would complicate the
gateway and services.

---

### 6.2 JWT vs. External OAuth2

| | Chosen — Self-contained JWT | Rejected — External OAuth2 (Keycloak/Auth0) |
|---|---|---|
| Dependency | None — self-contained | External identity provider |
| SSO | No | Native |
| Implementation | In-house (filters, refresh logic) | Provided by the provider |

**What we gained (+)**

- **Self-contained** — no external service to run, license, or configure; ideal for a
  self-hosted single-machine demo.
- **No dependency** — the platform runs entirely offline; no third-party uptime or account
  needed.

**What we gave up (−)**

- **More implementation work** — JWT issuing, refresh rotation, and role enforcement are
  built in-house (ADR-009).
- **No SSO** — users cannot log in with an existing enterprise identity provider, and the
  platform cannot integrate with a corporate IdP out of the box.

**Verdict:** For a self-contained semester project, an external IdP would add a heavyweight
dependency with minimal benefit. In-house JWT is appropriate now; the API is structured so
an external IdP can be swapped in for production SSO later.

---

### 6.3 JWT vs. API Keys Only

| | Chosen — JWT (user-level auth + RBAC) | Rejected — API keys |
|---|---|---|
| User-level auth | Yes — per-user identity | No — key identifies caller, not user |
| Role enforcement | Tight — claims carry roles | Manual, error-prone |
| Revocation | Rotation + short TTL | Key rotation orphan management |

**What we gained (+)**

- **User-level auth** — each request carries the authenticated user's identity and roles,
  enabling per-user authorization and audit (RBAC with 5 roles, ADR-009/D-013).
- **Role enforcement** — gateway and services enforce role-based access from JWT claims.

**What we gave up (−)**

- **More complex** — full token lifecycle (issuance, refresh, rotation, blacklist) is more
  machinery than a static API key.
- **Token management** — handling expiry and refresh on the client is added work that API
  keys avoid.

**Verdict:** The platform's RBAC requirements (5 distinct roles) demand real user identity
and role claims, which API keys cannot provide granularly. JWT is the correct choice.

---

## 7. Deployment Trade-offs

**Chosen: Docker Compose** — **(ADR-011)**

### 7.1 Docker Compose vs. Kubernetes

| | Chosen — Docker Compose | Rejected — Kubernetes |
|---|---|---|
| Simplicity | One file, one command | Cluster, nodes, pods, controllers |
| Machine footprint | Single machine | Cluster of machines |
| Auto-scaling | No | Yes (HPA) |
| Self-healing | Basic restart policy | Native (reschedules failed pods) |

**What we gained (+)**

- **Simpler** — a single `docker-compose.yml` defines the whole platform; `docker compose
  up` runs it (ADR-011).
- **Single machine** — runs entirely on one laptop/VM with 8 GB RAM; no cluster setup.

**What we gave up (−)**

- **No auto-scaling** — the stack cannot scale a hot service on demand.
- **No self-healing** — Compose restarts crashed containers but does not re-schedule,
  drain, or balance like K8s does.

**Verdict:** Kubernetes is production-grade orchestration that would be pure overhead for a
single-machine demo. The architecture is K8s-compatible (ADR-011's "production" profile
describes the migration), so the future path is clear.

---

### 7.2 Docker Compose vs. Bare Metal

| | Chosen — Docker Compose | Rejected — Bare metal (no containers) |
|---|---|---|
| Consistency | Reproducible images | Environment drift between dev machines |
| Setup | Declarative, one command | Manual install per dependency/version |
| Overhead | Docker daemon + image layers | Native process, ~zero overhead |
| WSL2 dependency | Yes (on Windows dev machines) | No |

**What we gained (+)**

- **Consistent environments** — every developer and the demo machine run identical images;
  "it works on my machine" is eliminated.
- **Easy setup** — PostgreSQL, RabbitMQ, and all 8 services version-matched in one compose
  file; bare metal would require installing and pinning each dependency by hand.

**What we gave up (−)**

- **Docker overhead** — an extra abstraction layer, image build time, and daemon resource
  cost.
- **WSL2 dependency** — Windows development machines rely on WSL2 for Docker, adding a
  platform dependency and occasional friction.

**Verdict:** The reproducibility and setup simplicity of containers decisively outweigh the
minor Docker overhead. Bare metal would make onboarding and demo reproducibility
significantly worse.

---

### 7.3 Docker Compose vs. Vagrant

| | Chosen — Docker Compose | Rejected — Vagrant (VMs) |
|---|---|---|
| Weight | Light — container images | Heavy — full virtual machines |
| Speed | Fast startup | Slow VM boot |
| Isolation | Process-level (shares host kernel) | Strong — full VM isolation |

**What we gained (+)**

- **Lighter** — containers share the host kernel, using a fraction of the RAM/CPU of full
  VMs.
- **Faster** — container startup is seconds; VM boot is minutes. Iteration is much faster.

**What we gave up (−)**

- **Less isolation** — a container shares the host kernel and is less strongly isolated than
  a full VM. For a demo with no adversarial workloads, this is acceptable.

**Verdict:** Vagrant's stronger isolation is unnecessary here, and its weight/speed costs
would slow every build and demo cycle. Docker Compose is the pragmatic choice.

---

## 8. Monitoring Trade-offs

**Chosen: Micrometer + Prometheus + Grafana** — **(ADR-012)**

### 8.1 Self-hosted OSS vs. Datadog / New Relic

| | Chosen — Micrometer + Prometheus + Grafana | Rejected — SaaS (Datadog/New Relic) |
|---|---|---|
| Cost | Free / open source | Per-host / per-user subscription |
| Hosting | Self-hosted in Compose | Vendor-hosted SaaS |
| Reliability | Depends on own infra (single box) | Vendor SLA |
| Setup | More manual configuration | Quick SDK install |

**What we gained (+)**

- **Free** — no subscription cost for a student project; the entire observability stack runs
  in Compose.
- **Self-hosted** — all metrics stay on the demo machine; no data leaves the environment,
  and the stack works fully offline.

**What we gave up (−)**

- **More setup** — exporters, scrape config, dashboard provisioning, and alert rules are
  manually maintained.
- **No SaaS reliability** — one local Grafana/Prometheus instance has no vendor SLA and can
  be down if the machine is down (acceptable for a demo).

**Verdict:** Commercial APM is cost-prohibitive and out of scope. The OSS stack gives
professional-grade metrics (Micrometer → Prometheus → Grafana) at zero cost, with the
standard production path available later (ADR-012 describes the production profile).

---

### 8.2 Self-hosted stack vs. ELK Stack

| | Chosen — Micrometer + Prometheus + Grafana | Rejected — ELK (Elasticsearch/Logstash/Kibana) |
|---|---|---|
| Focus | Metrics-first | Logs-first (full-text search) |
| Simplicity | Simpler — scrape + query | Complex — ingest pipeline + index |
| Log aggregation | Limited (MVP) | Native (centralized logs) |
| Search | Metric queries only | Full-text log search |

**What we gained (+)**

- **Simpler** — Micrometer instruments metrics natively in Spring Boot; Prometheus scrapes;
  Grafana visualizes. No ingestion pipeline to maintain.
- **Metrics focus** — the platform's observability need is primarily operational metrics
  (latency, throughput, error rates, resource usage).

**What we gave up (−)**

- **No log aggregation (MVP)** — logs stay per-container unless centralized. ADR-012 notes
  this as a known MVP limitation; ELK would centralize them.
- **Less search** — cannot full-text search logs from a single dashboard.

**Verdict:** Log aggregation is a recognized gap, but building an ELK ingestion pipeline for
an MVP would be over-investment. Metrics-first monitoring covers the demo's operational
story; structured logging is already in place so centralized logs can be layered on later.

---

### 8.3 Full stack vs. Spring Boot Actuator Only

| | Chosen — Micrometer + Prometheus + Grafana | Rejected — Actuator-only |
|---|---|---|
| Dashboards | Rich, persistent Grafana dashboards | Ad-hoc /bespoke HTTP endpoints |
| Alerting | Alert rules in Prometheus/Grafana | None built-in |
| Infrastructure | More components to run | Zero — Actuator only |
| History | Prometheus stores time series | No time-series retention |

**What we gained (+)**

- **Richer dashboards** — Grafana provides persistent, shareable, historical views that
  Actuator's raw `/actuator/metrics` endpoints do not.
- **Alerting** — Prometheus alert rules can page on high latency or error rates; Actuator
  alone has no alerting.

**What we gave up (−)**

- **More infrastructure** — three extra components (Prometheus, Grafana, and exporter
  config) to run in Compose.

**Verdict:** Actuator is the *instrumentation*, not the *observability platform*. Without
Prometheus + Grafana, there is nowhere to store and visualize metrics over time. The extra
components are justified by real dashboard and alerting value. We use Actuator as the data
source feeding the stack.

---

## 9. Testing Trade-offs

**Chosen: TDD + Integration Tests** — **(ADR-013, D-014)**

### 9.1 TDD + Integration vs. BDD / Cucumber

| | Chosen — TDD + integration tests | Rejected — BDD/Cucumber |
|---|---|---|
| Feedback | Fast unit-level feedback | Slower — feature-level scenarios |
| Design focus | Forces testable design | Focuses on business-readable specs |
| Business readability | Medium — technical tests | High — Gherkin plain-English |
| Gherkin syntax | None | Required |

**What we gained (+)**

- **Faster feedback** — unit tests run in milliseconds during development; bugs are caught
  at the point of writing.
- **Design focus** — writing tests first compels decoupled, testable service design.

**What we gave up (−)**

- **Less business readable** — tests express intent in code, not in plain-English scenarios
  that non-developers can read.
- **No Gherkin** — no executable specification layer that doubles as living documentation
  for stakeholders.

**Verdict:** For a small team where developers are the primary readers of tests, TDD gives
more value per unit of effort than BDD tooling. The work is already split into the right
test tiers (unit → integration → performance, ADR-013).

---

### 9.2 Automated Testing vs. Manual Only

| | Chosen — TDD + integration + performance tests | Rejected — Manual testing only |
|---|---|---|
| Regression safety | High — tests catch regressions | Low — depends on human diligence |
| Design feedback | Immediate, automated | Delayed, manual |
| Time investment | Upfront and ongoing | Cheap initially |
| Test maintenance | Required | None |

**What we gained (+)**

- **Regression safety** — changes to `vessel-service` cannot silently break `yard-service`
  workflows because integration tests exercise the workflow.
- **Design feedback** — the test suite becomes a living specification that documents
  intended behavior.

**What we gave up (−)**

- **Time investment** — authoring and maintaining tests costs real development time.
- **Test maintenance** — contract changes require test updates, adding ongoing effort.

**Verdict:** For a project whose quality gates require *evidence-based completion* (the
engineering protocol and D-002/D-014), automated tests are not optional. They are the
evidence. Manual-only testing would fail the project's own quality requirements.

---

### 9.3 Unit/Integration vs. Property-based

| | Chosen — Example-based (TDD + integration) | Rejected — Property-based (Hypothesis/jqwik) |
|---|---|---|
| Edge case coverage | Limited to chosen examples | Broad — generated inputs |
| Complexity | Simple to write and read | More complex, harder to reason about |
| Determinism | Deterministic | Probabilistic (until shrunk) |

**What we gained (+)**

- **Edge case coverage approachable** — developers enumerate the cases that matter and test
  exactly those.
- **Simplicity** — tests are easy to write, read, and debug; failures are concrete.

**What we gave up (−)**

- **Less exhaustive edge-case coverage** — property-based testing would find corner cases
  humans do not think to enumerate.
- **More complex to write** — property-based tests require modeling invariants and are
  harder for a small team to maintain.

**Verdict:** Property-based testing is a valuable advanced technique but is a poor fit for
the team's timeline and current skill level. Targeted example-based tests cover the known
critical paths (optimization heuristics, gate validation). Property-based tests can be added
later where correctness is most sensitive (e.g., stacking heuristics).

---

## 10. Frontend Trade-offs

**Chosen: React 18+ with TypeScript (Vite)** — **(ADR-008)**

### 10.1 React vs. Vue.js

| | Chosen — React | Rejected — Vue |
|---|---|---|
| Ecosystem | Largest (200K+ packages) | Smaller |
| Job market | Most in-demand | Smaller |
| Learning curve | Steeper (hooks, effects) | Gentler |
| Boilerplate | More (state, refs, memo) | Less |

**What we gained (+)**

- **Larger ecosystem** — every UI need (maps, charts, tables, WebSocket) has a
  well-maintained React solution (Leaflet, Recharts, TanStack, Socket.io-client).
- **More jobs** — React is the most in-demand frontend skill, maximizing resume value.

**What we gave up (−)**

- **Steeper learning curve** — hooks and state management are harder to master than Vue's
  Composition API.
- **More boilerplate** — more incidental code (memoization, custom hooks) than Vue.

**Verdict:** The ecosystem and hiring value of React outweigh its steeper curve, and the
team already has React experience (3 of 4 members, ADR-008).

---

### 10.2 React vs. Angular

| | Chosen — React (library) | Rejected — Angular (framework) |
|---|---|---|
| Weight | Light — bring-your-own libraries | Heavyweight, batteries-included |
| Flexibility | High — choose tools | Low — framework decides |
| Opinionated | No — many decisions | Yes — canonical way |
| Decisions to make | Many (state, routing, forms) | Fewer |

**What we gained (+)**

- **Lighter** — React is a library; the project composes only what it needs (Zustand,
  TanStack Query, React Hook Form).
- **More flexible** — tools are chosen per need rather than dictated.

**What we gave up (−)**

- **Less opinionated** — the team must decide state, data-fetching, forms, and routing
  strategies (they did, in ADR-008's table).
- **More decisions** — more architectural choices, and more room to make poor ones.

**Verdict:** Angular's structure would reduce decision-making but add weight and a steeper
learning curve with less team familiarity. React's flexibility suits a small, capable team.

---

### 10.3 React SPA vs. Thymeleaf (Server-Rendered)

| | Chosen — React SPA | Rejected — Thymeleaf |
|---|---|---|
| Richness | Rich, interactive SPA | Limited server-rendered pages |
| Real-time | Native via WebSocket | Poor (needs HTMX workarounds) |
| Complexity | Separate build (Node) | Single deployment unit |
| SOA demonstration | Clean frontend/backend separation | Tied to server templates |

**What we gained (+)**

- **Richer UI** — interactive dashboards, live maps, and data grids are first-class in an
  SPA.
- **Real-time** — Socket.io-client gives native WebSocket vessel tracking.
- **Clean separation** — the frontend is an independent consumer of the service APIs,
  demonstrating SOA composition.

**What we gave up (−)**

- **More complexity** — a separate Node/Vite build step and deployment unit.
- **Separate build** — two build pipelines (frontend + backend) instead of one.

**Verdict:** Thymeleaf would not deliver the interactive, real-time dashboard the product
requires and would hide the SOA frontend/backend separation this project must demonstrate.

---

### 10.4 React vs. Svelte

| | Chosen — React | Rejected — Svelte/SvelteKit |
|---|---|---|
| Bundle size | Larger | Smallest |
| Runtime speed | Fast | Fastest |
| Ecosystem | Largest | Smaller |
| Maturity | Most mature | Newer, less stable |

**What we gained (+)**

- **Lighter** (relative to Angular) and broadly familiar; the ecosystem and stability of
  React outweigh Svelte's marginal size/performance benefits.

**What we gave up (−)**

- **Smaller ecosystem / less mature** — Svelte has fewer libraries, less community, and a
  less stable framework; no team experience.

**Verdict:** Svelte's size and speed advantages are incremental, while React's ecosystem,
maturity, and team familiarity are decisive.

---

## 11. Performance vs. Simplicity Trade-offs

This section analyzes the recurring tension between performance and simplicity across the
architecture. Each trade-off is explicit about what was traded and which way we leaned.

### 11.1 Schema-per-Service vs. Shared DB

| Trade | Isolation | Query complexity |
|---|---|---|
| Leaning | Strong isolation (schema-per-service) | Accept higher query complexity |
| Detail | Each service owns data; no accidental coupling | Cross-service data fetched via API/events, never joins |

**Cost:** More code to fetch related data across boundaries. **Payoff:** Loose coupling
preserved, a core SOA requirement. **If revisited:** Only if a read path becomes a
performance bottleneck and the coupling is knowingly accepted.

---

### 11.2 Eventual Consistency vs. Strong Consistency

| Trade | Availability / decoupling | Consistency strength |
|---|---|---|
| Leaning | Favor availability and decoupling | Accept eventual consistency across service boundaries |
| Detail | RabbitMQ decouples producers/consumers | Read-after-write across services can be momentarily stale |

**Cost:** Compensating actions and idempotency handling when consumers fail; brief staleness
in aggregated views. **Payoff:** Services never block on each other and remain available
when a peer is down. **If revisited:** Replace event-fed projections with synchronous reads
only where the business truly requires immediate consistency (e.g., gate validation stays
synchronous via REST).

---

### 11.3 Caching vs. Freshness

| Trade | Performance | Data freshness |
|---|---|---|
| Leaning | Cache where reads dominate | Accept configurable staleness (TTL) |
| Detail | Dashboard metrics, vessel lists, reference data cached | TTL bounds staleness; invalidation on write where feasible |

**Cost:** Stale-data risk — a cached aggregate may briefly lag the source of truth. **Payoff:**
Order-of-magnitude lower latency on hot read paths and reduced database load. **If revisited:**
Shorten TTLs or add write-through invalidation for critical metrics.

---

### 11.4 Connection Pooling vs. Simplicity

| Trade | Performance | Configuration complexity |
|---|---|---|
| Leaning | Pool connections (HikariCP) | Accept modest configuration |
| Detail | Bounded pools per service (ADR-004/ADR-011) | Pool size, idle timeout, max lifetime must be tuned |

**Cost:** One more knobs file to configure; misconfiguration can cause connection exhaustion
or leaks. **Payoff:** Avoids per-request connection setup cost and keeps the single shared
PostgreSQL instance from being overwhelmed. **If revisited:** Adjust pool sizes based on
observed load from Prometheus rather than defaults.

### 11.5 Summary of the Performance/Simplicity Balance

The architecture consistently **favors operational simplicity and loose coupling over peak
performance**, because the demo's load is bounded and correctness/decoupling carry more
weight for an SOA course project than latency micro-optimization. Where performance matters
(hot read paths, dashboard queries), caching and pooling are applied *locally*, not
architecture-wide. This keeps the system simple where performance is irrelevant and
optimized where it matters.

---

## 12. Intentional Technical Debt

The following items are **knowingly deferred**. Each is a conscious scope cut, not an
oversight. They are recorded so that (a) they are never mistaken for "done," and (b) the
team and evaluators know the boundary of what was built.

### Deferred Items

| # | Item | What was done instead | Why deferred | Revisit trigger |
|---|------|-----------------------|--------------|-----------------|
| 1 | No event sourcing | RabbitMQ messages consumed + acknowledged, no immutable log (ADR-005) | Kafka/event-sourcing machinery exceeds semester scope | If audit/replay of historical state becomes a requirement |
| 2 | No CQRS | Read and write share the same domain model/query path | CQRS adds projection complexity without a demonstrated read bottleneck at demo scale | If a read model shows repeated hot-query pain |
| 3 | No API versioning beyond v1 | Single `/api/v1` namespace | All consumers in-repo; no external contract churn | When the first external/integration consumer needs a breaking change |
| 4 | No multi-terminal support | Single-terminal model | Only one terminal in scope for the demo | When the product targets chains or multiple yards |
| 5 | No mobile app | Responsive web dashboard only (D-009) | App store + separate client effort | When external stakeholders need on-the-ground access |
| 6 | Simplified optimization algorithms (heuristics only) | Rule-based stacking heuristics (D-006) | Full NP-hard optimization out of timeline; heuristics give 23–78% improvement | When stacking performance becomes a differentiator |
| 7 | No real IoT integration | Simulated IoT data layer, physical-ready architecture (D-007) | Physical sensors/hardware out of student scope | When deployed to a real terminal |
| 8 | No real customs system integration | Simulated customs workflow (D-008) | Real customs requires certification and country-specific MIGs | When targeting a live port authority |
| 9 | No centralized log aggregation | Metrics-first monitoring; structured logs per container (ADR-012) | ELK pipeline over-investment for MVP | When cross-service log correlation is required |

### Debt Management

- Each item maps to a concrete revisit trigger so the debt is *tracked*, not forgotten.
- None of the deferred items block the MVP's stated goals.
- The architecture was kept reversible: the event feed, schema layout, and API layer are
  structured so these items could be added without a rewrite.

---

## 13. Complexity Scoring

Each service is scored 1–5 across five dimensions. This gives the team and reviewers an
at-a-glance view of where effort and risk concentrate.

### Scoring Scale

| Score | Meaning |
|-------|---------|
| 1 | Trivial |
| 2 | Low |
| 3 | Moderate |
| 4 | High |
| 5 | Very high (hardest area in platform) |

### 13.1 Service Complexity Matrix

| Service | Business Logic | Integration | Data | Testing | Deployment | Overall |
|---------|----------------|-------------|------|---------|------------|---------|
| **Identity Service** | 2 | 1 | 2 | 2 | 1 | **1.6** |
| **Vessel Service** | 3 | 3 | 3 | 3 | 1 | **2.6** |
| **Container Service** | 3 | 2 | 3 | 3 | 1 | **2.4** |
| **Yard Service** | 4 | 4 | 4 | 4 | 1 | **3.4** |
| **Gate Service** | 4 | 4 | 3 | 4 | 1 | **3.2** |
| **Integration Service** | 3 | 5 | 3 | 4 | 1 | **3.2** |
| **Notification Service** | 2 | 3 | 2 | 2 | 1 | **2.0** |

### 13.2 Per-Dimension Rationale

**Business Logic Complexity**

| Service | Score | Rationale |
|---------|-------|-----------|
| Identity | 2 | RBAC, password hashing, token issuance — standard, well-trodden |
| Vessel | 3 | Voyages, ETAs, stowage planning — moderate domain rules |
| Container | 3 | Status lifecycle, manifest handling, history tracking |
| Yard | 4 | Stacking heuristics, slot/position logic, rebalancing (D-006) — the algorithmic core |
| Gate | 4 | Appointments, validation rules, entry/exit state machines — high rule density |
| Integration | 3 | EDIFACT parsing, IoT simulation, customs workflow simulation |
| Notification | 2 | Templates, delivery, subscriptions — straightforward |

**Integration Complexity**

| Service | Score | Rationale |
|---------|-------|-----------|
| Identity | 1 | Leaf — no outbound calls (ADR-001 dependency matrix) |
| Vessel | 3 | Calls Container + Yard; publishes voyage events |
| Container | 2 | Leaf for inbound; source of events for others |
| Yard | 4 | High fan-out/fan-in: REST + events flow to/from Gate, Container, Vessel, Integration |
| Gate | 4 | Coordinates Container, Yard, publishes gate events |
| Integration | 5 | Touches everything — consumes events from all services, simulates EDIFACT/IoT/customs |
| Notification | 3 | Subscribes to all domain events; WebSocket delivery through gateway |

**Data Complexity**

| Service | Score | Rationale |
|---------|-------|-----------|
| Identity | 2 | Users, roles, tokens — simple relational |
| Vessel | 3 | Vessels, voyages, positions, stowage — relational + JSONB attributes |
| Container | 3 | Container registry, status history, manifests — relational + JSONB |
| Yard | 4 | Layout, slots, positions, moves — highest data invariants |
| Gate | 3 | Transactions, appointments, validation records |
| Integration | 3 | Message payloads, mappings, transformations (JSONB-heavy) |
| Notification | 2 | Templates, subscriptions, delivery log |

**Testing Complexity**

| Service | Score | Rationale |
|---------|-------|-----------|
| Identity | 2 | Unit-heavy; simple integration |
| Vessel | 3 | Needs scenario/integration tests across voyage workflows |
| Container | 3 | Status/state transitions require integration coverage |
| Yard | 4 | Optimization heuristics need careful property/edge-case testing |
| Gate | 4 | Gate-in/out workflows span multiple services; high integration-test density |
| Integration | 4 | Message parsing + transformed payloads + simulation — many edge cases |
| Notification | 2 | Delivery + WebSocket basics; moderate |

**Deployment Complexity**

| Service | Score | Rationale |
|---------|-------|-----------|
| All services | 1 | Identical pattern — one Spring Boot image, one Compose entry (ADR-011). Uniform |

Deployment is uniformly trivial because the Docker Compose pattern (ADR-011) makes every
service a repeat of the same template. This is the *payoff* of the deployment decision
(Section 7).

### 13.3 Interpretation

- **Highest complexity concentrates in Yard, Gate, and Integration services.** These are the
  three to budget the most tightest for, and the places most likely to harbor bugs.
- **Identity, Container, and Notification are low-complexity** and can be built/owned by
  less-experienced team members, or built first to establish the template.
- **Deployment is a solved, uniform problem** — the same Compose pattern applies everywhere,
  which is exactly why the Docker Compose decision pays off.

---

## 14. Decision Reversibility

Not all decisions are equally hard to undo. This section scores reversibility so future
maintainers know which choices are "cheap to revisit" and which are "expensive to reverse."

| Decision | Reversibility | Notes |
|----------|---------------|-------|
| Architecture style (SBA) | Low | Re-architecting to monolith or full microservices is a rewrite; SBA boundaries are structural |
| Single PostgreSQL + schema-per-service | Medium | Splitting into per-service instances is possible but touches every service's config |
| RabbitMQ | High | Swap to Kafka later requires changing producers/consumers, but the event schema layer was designed to be portable |
| REST + WebSocket | High | Well-standardized; easy to retain regardless of other changes |
| JWT with refresh rotation | Medium | Swapping to external OAuth2 requires replacing the auth filter but the claims model is preserved |
| Docker Compose | High | K8s migration is an established, document path (ADR-011 production profile) |
| Monitoring (Prometheus/Grafana) | High | Standard, portable to K8s |
| Testing strategy (TDD) | High | Nothing structural binds it |
| React + TypeScript | High | Entirely replaceable; an isolated consumer of the APIs |

**Principle:** The *hard-to-reverse* decisions (architecture style, database model, broker
choice) were made early and deliberately. The *easy-to-reverse* decisions (testing, frontend,
monitoring) were chosen for day-to-day fit with the team. This is a sound allocation — the
irreversible choices were given the most deliberation (ADR-001, ADR-004), while reversible
ones can evolve without cost.

---

## 15. Conclusion

The SmartPortX architecture is a set of deliberate, defensible trade-offs optimized for a
single dominant constraint: **a small team delivering a demonstrative SOA platform in one
semester on a single machine.**

The recurring theme across all nine trade-off areas is a bias toward **operational
simplicity and loose coupling over peak performance or raw scalability**. Every rejected
alternative (full microservices, Kafka, Kubernetes, MongoDB, external OAuth2) was rejected
not because it is "bad," but because it would cost the team time and infrastructure that the
semester cannot afford without a commensurate MVP benefit.

The costs of these choices are acknowledged and recorded — eventual consistency, schema
coupling risk, no log aggregation, no event sourcing, deferred capabilities — in Section 12
as tracked technical debt with concrete revisit triggers.

The architecture is **reversible where it matters and decisive where it does not**: the
irreversible structural decisions (SBA boundaries, schema-per-service, broker) are sound and
well-justified; the reversible ones (testing, frontend, monitoring, deployment) can evolve
freely.

---

## Appendix A: Decision → ADR Mapping

| Section | Decision | ADR |
|---------|----------|-----|
| 3. Architecture Style \| Service-Based Architecture | ADR-001 |
| 4. Database \| PostgreSQL 17, Schema-per-Service | ADR-004 |
| 5. Communication \| REST + RabbitMQ + WebSocket | ADR-005 |
| 5. Communication \| API Gateway | ADR-006 |
| 5. Communication \| Service Discovery (Eureka) | ADR-007 |
| 6. Security \| JWT + Refresh Token Rotation | ADR-009 |
| 7. Deployment \| Docker Compose | ADR-011 |
| 8. Monitoring \| Micrometer + Prometheus + Grafana | ADR-012 |
| 9. Testing \| TDD + Integration Tests | ADR-013 |
| 10. Frontend \| React 18+ / TypeScript | ADR-008 |
| 11. Performance \| Schema-per-Service, caching, pooling | ADR-004, ADR-011 |
| 12. Technical Debt \| Deferred capabilities | D-006, D-007, D-008, D-009 |

*Backend language (ADR-002) and framework (ADR-003) — Java/Spring Boot — underpin all
services but are not themselves trade-off subjects in this document; they are recorded
separately in their own ADRs.*

---

*End of ARCHITECTURE-TRADEOFFS.md*
