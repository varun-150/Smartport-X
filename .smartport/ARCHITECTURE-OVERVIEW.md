# SmartPortX — Master Architecture Overview

> **Intelligent Distributed Port Logistics & Container Yard Orchestration Platform**
> Phase 3 of 5 | Architecture Documentation (No Code)
> Last Updated: 2026-08-26

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture Style Decision](#2-architecture-style-decision)
3. [Technology Stack](#3-technology-stack)
4. [System Context Diagram](#4-system-context-diagram)
5. [Service Decomposition Overview](#5-service-decomposition-overview)
6. [Communication Architecture Summary](#6-communication-architecture-summary)
7. [Data Architecture Summary](#7-data-architecture-architecture-summary)
8. [Security Architecture Summary](#8-security-architecture-summary)
9. [Deployment Architecture Summary](#9-deployment-architecture-summary)
10. [Key Design Principles](#10-key-design-principles)
11. [Constraints and Trade-offs](#11-constraints-and-trade-offs)
12. [Architecture Decision Records Index](#12-architecture-decision-records-index)

---

## 1. Executive Summary

SmartPortX is an **Intelligent Distributed Port Logistics & Container Yard Orchestration Platform** designed to complement — not replace — existing Terminal Operating Systems (TOS) at container port terminals. The platform addresses the critical operational gap between vessel discharge planning, yard positioning, gate flow management, and real-time visibility by providing a unified, event-driven orchestration layer that coordinates across these traditionally siloed domains.

### 1.1 Problem Statement

Container terminals operate with fragmented systems: the TOS handles vessel operations, gate systems manage truck flow, yard planning is often manual or spreadsheet-driven, and real-time visibility requires walking the yard or calling operators. This fragmentation creates information lag, suboptimal yard utilization, increased reshuffles, and gate congestion. SmartPortX addresses these gaps with a service-oriented platform that provides:

- **Real-time yard visibility** — knowing exactly where every container sits without physical inspection
- **Intelligent stacking optimization** — reducing reshuffles through prediction-aware placement
- **Gate flow coordination** — dynamic appointment scheduling tied to yard capacity
- **Vessel discharge orchestration** — discharge plans that account for current yard state
- **Unified dashboard** — a single pane of glass for terminal operations

### 1.2 Project Scope and Constraints

| Dimension | Constraint |
|---|---|
| Team Size | 3–4 developers |
| Timeline | 4–6 weeks |
| Terminal Scope | Single terminal, single TOS integration point |
| IoT | Simulated sensor data (no physical hardware) |
| Interface | Web dashboard only (no mobile, no native apps) |
| Deployment | Docker Compose for development/demo; container-ready for production |

### 1.3 Architecture at a Glance

SmartPortX employs a **Service-Based Architecture (SBA)** — a pragmatic middle ground between a monolithic application and full microservices decomposition. Eight domain services, each with its own Spring Boot application, PostgreSQL schema, and API contract, communicate through synchronous REST calls and asynchronous RabbitMQ events. A React-based SPA dashboard consumes the unified API through a Spring Cloud Gateway.

The platform is designed to be **operational by a small team in a short timeline** while remaining **architecturally extensible** for future production deployment at a real terminal.

### 1.4 Phase Context

This document represents **Phase 3** of a five-phase engineering process:

| Phase | Name | Status |
|---|---|---|
| Phase 1 | Requirements Analysis & Domain Modeling | Complete |
| Phase 2 | Service Identification & API Design | Complete |
| **Phase 3** | **Architecture Documentation** | **Current** |
| Phase 4 | Implementation & Testing | Pending |
| Phase 5 | Integration & Deployment | Pending |

---

## 2. Architecture Style Decision

### 2.1 Alternatives Considered

Four architectural styles were evaluated against SmartPortX constraints:

#### 2.1.1 Monolithic Architecture

```
┌─────────────────────────────────────────┐
│           SmartPortX Monolith           │
│  ┌──────────┐ ┌──────────┐ ┌─────────┐  │
│  │   Yard   │ │   Gate   │ │ Vessel  │  │
│  │ Module   │ │ Module   │ │ Module  │  │
│  └────┬─────┘ └────┬─────┘ └────┬────┘  │
│       │            │            │        │
│  ┌────┴────────────┴────────────┴────┐   │
│  │        Shared PostgreSQL DB       │   │
│  └───────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

**Pros:** Simplest to build, deploy, and debug. No inter-service communication complexity. Fastest initial development velocity.

**Cons:** Tight coupling between domain modules. Cannot independently scale hot paths (e.g., gate processing). Shared database creates schema coupling — schema changes in one module risk breaking others. No independent deployment. Does not satisfy SOA requirements (service contracts, loose coupling, autonomy).

**Verdict:** Rejected. While tempting for a small team, the monolith fails SOA assessment criteria and creates long-term maintainability debt.

#### 2.1.2 Full Microservices Architecture

```
┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐
│ Yard │ │ Gate │ │ Vessel│ │ Cont.│ │Notif.│ │Ident.│ │ Integ.│ │Gate │
└──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘
   │        │        │        │        │        │        │        │
   └────────┴────────┴────────┴────────┴────────┴────────┴────────┘
                            │
                    ┌───────┴───────┐
                    │   Kubernetes  │
                    │    + Istio    │
                    └───────────────┘
```

**Pros:** Maximum flexibility, independent scaling, technology diversity, fault isolation.

**Cons:** Requires Kubernetes (team has no K8s expertise), service mesh complexity (Istio/Linkerd), distributed tracing overhead, significant DevOps burden. Over-engineered for 3–4 person team with 4–6 week timeline. Each service would need its own CI/CD pipeline, health checks, circuit breakers, retry policies — infrastructure work that dominates development time.

**Verdict:** Rejected. The operational complexity of full microservices with Kubernetes is disproportionate to team size and timeline constraints.

#### 2.1.3 Modular Monolith

```
┌─────────────────────────────────────────┐
│         SmartPortX Modular Monolith     │
│  ┌──────────┐ ┌──────────┐ ┌─────────┐  │
│  │   Yard   │ │   Gate   │ │ Vessel  │  │
│  │ Module   │ │ Module   │ │ Module  │  │
│  │(internal │ │(internal │ │(internal│  │
│  │ API)     │ │ API)     │ │ API)    │  │
│  └────┬─────┘ └────┬─────┘ └────┬────┘  │
│       │            │            │        │
│  ┌────┴────────────┴────────────┴────┐   │
│  │      Single PostgreSQL DB         │   │
│  │    (shared schema or schema-per-  │   │
│  │           module)                 │   │
│  └───────────────────────────────────┘   │
│  Single deployable artifact              │
└─────────────────────────────────────────┘
```

**Pros:** Clean module boundaries, fast development, single deployment, no network overhead between modules. Module interfaces enforce coupling discipline.

**Cons:** Single deployable artifact limits scaling flexibility. Shared JVM means a memory leak in one module affects all. Does not fully satisfy SOA autonomy requirement. Module boundaries are compile-time only — no runtime fault isolation.

**Verdict:** Considered but ultimately set aside. While excellent for many projects, it does not meet the SOA service autonomy requirement. However, SBA retains this option: services CAN run together in one JVM during development.

#### 2.1.4 Service-Based Architecture (SBA) — Selected

```
┌──────────────────────────────────────────────────────────────┐
│                        API Gateway                           │
│                    (Spring Cloud Gateway)                    │
└──────────┬──────────┬──────────┬──────────┬─────────────────┘
           │          │          │          │
    ┌──────┴───┐ ┌────┴────┐ ┌──┴───┐ ┌────┴────┐
    │ Identity │ │  Yard   │ │ Gate │ │Container│
    │ Service  │ │ Service │ │ Svc  │ │ Service │
    └────┬─────┘ └────┬────┘ └──┬───┘ └────┬────┘
         │            │         │           │
    ┌────┴───┐  ┌─────┴──┐ ┌───┴───┐ ┌─────┴──┐
    │ Schema │  │ Schema │ │Schema │ │ Schema │
    │  id    │  │  yard  │ │ gate  │ │  cont  │
    └────────┘  └────────┘ └───────┘ └────────┘

    + 4 more services (Vessel, Integration, Notification, Gateway)
    + RabbitMQ event bus
    + Shared PostgreSQL instance (separate schemas)
```

**Pros:**
- Each service is a deployable Spring Boot application (autonomy)
- Services can run in a single JVM for development (fast iteration)
- Services can run as separate processes for production (isolation, scaling)
- Schema-per-service satisfies data ownership without requiring separate database instances
- Satisfies all SOA tenets: service contracts, loose coupling, autonomy, statelessness, discoverability
- No Kubernetes required — Docker Compose is sufficient
- Team can learn incrementally: start monolithic, extract services as needed

**Cons:**
- Network overhead when services run as separate processes
- Distributed debugging requires tracing (addressed with OpenTelemetry)
- Eventual consistency for cross-service data (acceptable for port operations)
- Slightly more complex than a monolith

**Verdict:** **Selected.** SBA is the pragmatic architecture for a small team building a SOA-compliant system under tight constraints. It delivers service autonomy and loose coupling while remaining operationally simple enough for Docker Compose deployment.

### 2.2 SBA Selection Justification

The selection criteria weighted against SmartPortX constraints:

| Criterion | Weight | Monolith | Microservices | Modular Mono | **SBA** |
|---|---|---|---|---|---|
| SOA Compliance | 25% | 2 | 5 | 3 | **5** |
| Development Speed | 25% | 5 | 2 | 4 | **4** |
| Team Feasibility (3-4 ppl) | 20% | 5 | 1 | 5 | **4** |
| Operational Simplicity | 15% | 5 | 1 | 5 | **4** |
| Extensibility | 15% | 2 | 5 | 3 | **4** |
| **Weighted Score** | **100%** | **3.80** | **2.80** | **3.95** | **4.30** |

SBA scores highest because it satisfies the SOA requirement without sacrificing practical delivery. The key insight: **the same Spring Boot application can run embedded for development and as a standalone JAR for production**, giving the team both speed and architecture integrity.

### 2.3 SOA Tenet Mapping

| SOA Tenet | How SBA Satisfies It |
|---|---|
| **Service Contracts** | Each service exposes an OpenAPI 3.0 specification. Contracts are versioned and published. |
| **Loose Coupling** | Services communicate via REST (synchronous) and RabbitMQ events (async). No shared code or databases between services. |
| **Autonomy** | Each service has its own Spring Boot application, its own PostgreSQL schema, its own deployment unit. |
| **Statelessness** | Services do not hold client session state. Authentication state lives in JWT tokens. |
| **Discoverability** | Eureka service registry enables dynamic service discovery. REST APIs are self-documenting via OpenAPI. |
| **Composability** | The Gateway composes responses from multiple services. The dashboard aggregates data from multiple service APIs. |
| **Service Abstraction** | Internal implementation details are hidden behind API contracts. Clients only see the API, not the implementation. |

---

## 3. Technology Stack

### 3.1 Backend

| Technology | Version | Purpose | Justification |
|---|---|---|---|
| **Java** | 25 (Temurin LTS) | Primary language | LTS release with virtual threads, pattern matching, sealed classes. Temurin provides free, production-grade JDK. |
| **Spring Boot** | 3.x (latest stable) | Application framework | Industry standard for Java microservices. Massive ecosystem, excellent documentation, built-in observability. |
| **Spring Security** | 6.x (bundled) | Authentication & authorization | JWT support, RBAC, method-level security. Tightly integrated with Spring Boot. |
| **Spring Data JPA** | 3.x (bundled) | Data access layer | Repository pattern, query derivation, schema management. Reduces boilerplate. |
| **Spring Cloud Gateway** | 4.x | API Gateway | Reactive, non-blocking, filter-based. Native Spring ecosystem integration. |
| **Spring Cloud Netflix** | 4.x | Service discovery | Eureka client for service registration. Lightweight and battle-tested. |
| **Hibernate** | 6.x (via Spring Data) | ORM | JPA implementation. Schema generation, lazy loading, second-level cache. |
| **Lombok** | Latest | Boilerplate reduction | Reduces getter/setter/constructor noise. Used judiciously (no `@Data` on JPA entities). |
| **MapStruct** | 1.6.x | Object mapping | Compile-time DTO↔Entity mapping. Type-safe, zero reflection overhead. |

### 3.2 Database

| Technology | Version | Purpose | Justification |
|---|---|---|---|
| **PostgreSQL** | 17.11 | Primary database | ACID compliance, JSONB support for flexible fields, full-text search, mature replication. |
| **Schema-per-service** | N/A | Logical isolation | Each service writes to its own PostgreSQL schema within a single database instance. Satisfies data ownership without requiring separate database servers. |

### 3.3 Frontend

| Technology | Version | Purpose | Justification |
|---|---|---|---|
| **React** | 18+ | UI library | Component-based, massive ecosystem, team familiarity. |
| **TypeScript** | 5.x | Type safety | Catch errors at compile time, self-documenting props, better IDE support. |
| **Vite** | 6.x | Build tool | Fast HMR, ESM-native, minimal config. |
| **React Query (TanStack)** | 5.x | Server state management | Caching, background refetching, optimistic updates for API data. |
| **Recharts** | 2.x | Charts & dashboards | Declarative, composable chart components built on D3. |
| **React Router** | 7.x | Client-side routing | Standard routing solution for React SPAs. |

### 3.4 Messaging & Real-Time

| Technology | Version | Purpose | Justification |
|---|---|---|---|
| **RabbitMQ** | 3.13+ | Async message broker | Reliable message delivery, exchanges/topic routing, management UI. Simpler than Kafka for this scale. |
| **WebSocket** | JSR 356 / Spring WS | Real-time push | Dashboard needs live updates for yard status, gate queue, vessel progress. |
| **STOMP** | N/A | WebSocket protocol | Simple messaging protocol over WebSocket. Spring provides `@MessageMapping` support. |

### 3.5 Observability

| Technology | Version | Purpose | Justification |
|---|---|---|---|
| **Micrometer** | 1.14+ | Metrics facade | Vendor-neutral metrics API. Integrates with Prometheus, Grafana, and Spring Boot Actuator. |
| **Prometheus** | 2.54+ | Metrics storage | Time-series metrics database. Pull-based model. Excellent Grafana integration. |
| **Grafana** | 11.x | Metrics visualization | Dashboards for operational metrics, custom alerts, team-friendly. |
| **OpenTelemetry** | 1.x | Distributed tracing | Vendor-neutral tracing. Export to Jaeger or any OTLP-compatible backend. |
| **Jaeger** | 1.62+ | Trace visualization | Distributed trace viewer. Critical for debugging cross-service request flows. |
| **Spring Boot Actuator** | 3.x (bundled) | Health & info | Health checks, info endpoints, environment inspection. Foundation for all monitoring. |

### 3.6 Infrastructure

| Technology | Version | Purpose | Justification |
|---|---|---|---|
| **Docker** | 27.x | Containerization | Standard container runtime. Consistent environments across dev and production. |
| **Docker Compose** | 2.x | Local orchestration | Single-command startup for all services, databases, and brokers. Perfect for student project scale. |
| **Maven** | 3.9.x | Build tool | Multi-module project support. Dependency management, plugin ecosystem. |
| **GitHub Actions** | N/A | CI/CD | Free for educational repos. Build, test, Docker image push. |

### 3.7 Technology Decision Rationale

The stack was selected against four criteria:

1. **Team Familiarity** — Java and React are taught in most curricula. Spring Boot has extensive learning resources.
2. **Ecosystem Maturity** — Every technology above has 5+ years of production use, extensive documentation, and active communities.
3. **Operational Simplicity** — Docker Compose (not Kubernetes), single PostgreSQL instance (not per-service databases), RabbitMQ (not Kafka). The stack minimizes operational burden.
4. **SOA Alignment** — Each technology serves a specific SOA concern: Spring Cloud Gateway (routing), Eureka (discovery), RabbitMQ (async communication), OpenAPI (contracts).

---

## 4. System Context Diagram

### 4.1 High-Level System Context

```
                                    ┌─────────────────────┐
                                    │   Terminal Staff    │
                                    │  (Operational Users)│
                                    └─────────┬───────────┘
                                              │
                                    ┌─────────▼───────────┐
                                    │    Web Browser      │
                                    │  (React Dashboard)  │
                                    └─────────┬───────────┘
                                              │ HTTPS (REST + WebSocket)
                                              │
┌──────────────┐                   ┌─────────▼───────────┐
│  External    │◄──EDIFACT/──────►│     SmartPortX      │
│  Systems     │   IoT Events     │    Platform          │
│  (TOS, ERP,  │                   │  (8 Services +      │
│   Customs)   │                   │   Gateway)          │
└──────────────┘                   └─────────┬───────────┘
                                              │
                                    ┌─────────▼───────────┐
                                    │   Infrastructure    │
                                    │ PostgreSQL, RabbitMQ│
                                    │ Prometheus, Grafana │
                                    │ Jaeger              │
                                    └─────────────────────┘
```

### 4.2 Actor Definitions

| Actor | Description | Interaction |
|---|---|---|
| **Admin** | System administrator. Manages users, roles, system configuration. | Full dashboard access, user management, system settings. |
| **Terminal Operator** | Daily operator managing vessel and container operations. | Vessel scheduling, container tracking, yard views. |
| **Yard Manager** | Manages yard layout, container positioning, stacking strategy. | Yard layout editor, utilization reports, reshuffle prediction. |
| **Gate Operator** | Manages truck entry/exit, appointments, gate processing. | Gate queue management, truck check-in/out, appointment verification. |
| **Read-Only** | Auditor, visitor, or stakeholder with view-only access. | Dashboard views, reports, no modifications. |
| **External TOS** | The terminal's existing Terminal Operating System. | EDIFACT message exchange (COPRAR, COARRI, CODECO, COPARN). |
| **Simulated IoT** | Sensor data generators (temperature, GPS, container status). | Publishes to RabbitMQ via Integration service. |

### 4.3 External System Integrations

```
┌──────────────────────────────────────────────────────────────────┐
│                      SmartPortX Boundary                        │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              smartportx-integration                        │  │
│  │                                                            │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐   │  │
│  │  │ EDIFACT     │  │ IoT Ingest  │  │ External API    │   │  │
│  │  │ Parser      │  │ (Simulated) │  │ Adapter         │   │  │
│  │  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘   │  │
│  │         │                │                   │            │  │
│  └─────────┼────────────────┼───────────────────┼────────────┘  │
└────────────┼────────────────┼───────────────────┼────────────────┘
             │                │                   │
    ┌────────▼──────┐  ┌─────▼──────┐   ┌───────▼────────┐
    │  External TOS │  │ IoT Sensors│   │ Customs / ERP  │
    │  (EDIFACT)    │  │(Simulated) │   │ (Future)       │
    └───────────────┘  └────────────┘   └────────────────┘
```

### 4.4 EDIFACT Message Types

| Message | Direction | Purpose |
|---|---|---|
| **COPRAR** | Inbound | Container discharge/load order from TOS |
| **COARRI** | Outbound | Container discharge/load confirmation to TOS |
| **CODECO** | Outbound | Container gate-in/gate-out report to TOS |
| **COPARN** | Inbound | Container announcement ( arrival notice ) from TOS |

---

## 5. Service Decomposition Overview

### 5.1 Service Registry

| # | Service Name | Bounded Context | Primary Responsibility |
|---|---|---|---|
| 1 | `smartportx-gateway` | API Routing | Request routing, authentication enforcement, rate limiting, CORS, WebSocket proxy |
| 2 | `smartportx-identity` | Access Control | User management, RBAC, JWT authentication, authorization policies |
| 3 | `smartportx-yard` | Yard Operations | Yard layout, container positioning, utilization analytics, stacking optimization, reshuffle prediction |
| 4 | `smartportx-gate` | Gate Operations | Truck appointments, gate check-in/out, queue management, dynamic quotas |
| 5 | `smartportx-container` | Container Lifecycle | Container CRUD, lifecycle state machine, movement history, dangerous goods flagging |
| 6 | `smartportx-vessel` | Vessel Operations | Vessel scheduling, discharge plan creation, discharge progress tracking |
| 7 | `smartportx-integration` | Integration Hub | EDIFACT parsing, IoT data ingestion, external system adapters |
| 8 | `smartportx-notification` | Event Distribution | WebSocket push, appointment updates, real-time yard/gate status broadcasts |

### 5.2 Service Dependency Matrix

```
                 Gateway  Identity  Yard  Gate  Container  Vessel  Integration  Notification
Gateway            —       ◄────     ──►   ──►    ──►       ──►       ──►          ──►
Identity           ──►       —        ·     ·      ·         ·         ·            ·
Yard               ──►       ·       —    ──►     ──►        ·         ·           ──►
Gate               ──►       ·      ──►     —     ──►        ·         ·           ──►
Container          ──►       ·      ──►    ──►      —       ──►        ·           ──►
Vessel             ──►       ·       ·     ──►    ──►         —        ·           ──►
Integration        ──►       ·       ·      ·     ──►        ──►        —          ──►
Notification       ──►       ·       ·      ·      ·         ·         ·            —

Legend: ──► = calls (synchronous REST)
        ◄──── = used by (dependency)
        ·   = no direct dependency
```

### 5.3 Service Detail: smartportx-gateway

**Port:** 8080 (edge)
**Database:** None (stateless routing layer)
**Primary Events Published:** None
**Primary Events Consumed:** None

**Responsibilities:**
- Route all incoming HTTP requests to appropriate backend services
- Enforce JWT authentication by validating tokens with Identity service
- Apply rate limiting (100 req/min per API key)
- Handle CORS policy for the React dashboard
- Proxy WebSocket connections to Notification service
- Provide a unified API surface (clients never call backend services directly)

**Key Filters:**
- `AuthenticationFilter` — Validates JWT on every request (except `/auth/**`)
- `RateLimitFilter` — Token bucket rate limiting per API key
- `LoggingFilter` — Request/response logging for observability
- `CORSFilter` — Cross-origin policy for dashboard

### 5.4 Service Detail: smartportx-identity

**Port:** 8081
**Database:** `smartportx_identity` schema
**Primary Events Published:** `UserCreated`, `UserDeactivated`, `RoleChanged`
**Primary Events Consumed:** None

**Responsibilities:**
- Manage user accounts (CRUD, activation/deactivation)
- Authenticate users and issue JWT access + refresh tokens
- Define and manage RBAC roles and permissions
- Enforce password policies and account lockout
- Provide token introspection endpoint for Gateway validation

**Domain Model:**
- `User` — username, email, password hash, status, timestamps
- `Role` — name, description, permission set
- `UserRole` — user-to-role assignment (many-to-many)
- `RefreshToken` — token value, user reference, expiry, revoked flag

**RBAC Roles:**

| Role | Permissions |
|---|---|
| Admin | Full system access, user management, system configuration |
| Terminal Operator | Vessel operations, container management, yard read/write, gate read |
| Yard Manager | Yard layout management, container positioning, stacking config, yard reports |
| Gate Operator | Gate operations, truck appointments, gate queue management |
| Read-Only | Dashboard viewing, report generation, no modifications |

### 5.5 Service Detail: smartportx-yard

**Port:** 8082
**Database:** `smartportx_yard` schema
**Primary Events Published:** `ContainerPlaced`, `ContainerRetrieved`, `ReshuffleNeeded`, `YardUtilizationUpdated`
**Primary Events Consumed:** `ContainerArrived`, `ContainerDeparted`, `GateEvent`

**Responsibilities:**
- Model the physical yard layout (blocks → bays → rows → tiers)
- Track container positions in the yard (which block/bay/row/tier)
- Calculate yard utilization metrics (by block, by zone, overall)
- Execute stacking optimization algorithms (weight-based, hazard-aware, dwell-time-aware)
- Predict reshuffle needs based on upcoming vessel discharge plans
- Provide yard map visualization data for the dashboard

**Domain Model:**
- `YardBlock` — block identifier, dimensions, zone type (import/export/transit/refrigerated)
- `Bay` — bay number within block, row count, tier count
- `ContainerPosition` — block, bay, row, tier, container reference, placed timestamp
- `StackingRule` — criteria for placement decisions (weight, hazard class, dwell time, destination)
- `UtilizationSnapshot` — block, timestamp, occupancy percentage, capacity metrics

**Key Algorithms:**
- **Stacking Optimizer** — Given a container's properties (weight, hazard class, dwell estimate, next vessel), find the optimal position that minimizes future reshuffles
- **Reshuffle Predictor** — Analyze upcoming discharge plans against current yard state to predict containers that will need to be moved
- **Utilization Calculator** — Real-time occupancy metrics per block, per zone, with trend analysis

### 5.6 Service Detail: smartportx-gate

**Port:** 8083
**Database:** `smartportx_gate` schema
**Primary Events Published:** `GateEvent`, `TruckArrived`, `TruckDeparted`, `AppointmentCreated`, `AppointmentUpdated`
**Primary Events Consumed:** `ContainerPlaced`, `ContainerRetrieved`

**Responsibilities:**
- Manage truck appointment scheduling (create, modify, cancel)
- Process truck gate-in and gate-out events
- Maintain real-time gate queue status
- Enforce dynamic quotas (max trucks per hour, based on yard capacity)
- Track gate processing time and throughput metrics
- Coordinate with Yard service for container availability at gate

**Domain Model:**
- `TruckAppointment` — truck ID, driver, container reference, appointment time, type (pickup/dropoff), status
- `GateTransaction` — truck entry time, exit time, gate lane, processed-by operator, associated appointment
- `GateLane` — lane identifier, status (open/closed/maintenance), current queue length
- `DynamicQuota` — time window, max trucks, current count, quota source (manual/automatic)

**Gate Workflow:**
```
Truck Arrives → Queue → Lane Assignment → Document Check →
Yard Check (container ready?) → Load/Unload → Document Update → Exit
```

### 5.7 Service Detail: smartportx-container

**Port:** 8084
**Database:** `smartportx_container` schema
**Primary Events Published:** `ContainerArrived`, `ContainerDeparted`, `ContainerStatusChanged`, `DangerousGoodsFlagged`
**Primary Events Consumed:** `ContainerPlaced`, `ContainerRetrieved`, `GateEvent`

**Responsibilities:**
- Maintain the master container registry (CRUD operations)
- Track container lifecycle state machine (Empty → Full → In Transit → At Terminal → etc.)
- Record movement history (every location change with timestamp)
- Flag dangerous goods containers with hazard class information
- Provide container search and lookup APIs
- Expose container status for cross-service queries

**Domain Model:**
- `Container` — container number, ISO type, owner, weight, status, dangerous goods flag, hazard class, timestamps
- `ContainerMovement` — container reference, from location, to location, timestamp, movement type
- `ContainerStatus` — enum: EMPTY, FULL, IN_TRANSIT, AT_YARD, AT_GATE, ON_VESSEL, IN_CUSTOMS
- `DangerousGoods` — container reference, UN number, hazard class, packing group, emergency contact

**State Machine:**
```
                    ┌──────────┐
         ┌─────────│  EMPTY   │─────────┐
         │         └──────────┘         │
         │              │               │
         │         Loaded at Gate        │
         │              │               │
         │         ┌────▼────┐          │
         │         │  FULL   │          │
         │         └────┬────┘          │
         │              │               │
    Gate Out       Placed at Yard    Gate Out
    (pickup)            │          (return empty)
         │              │               │
         │         ┌────▼─────┐         │
         └────────►│ AT_YARD  │◄────────┘
                   └────┬─────┘
                        │
                   Loaded on Vessel
                        │
                   ┌────▼──────┐
                   │ ON_VESSEL │
                   └───────────┘
```

### 5.8 Service Detail: smartportx-vessel

**Port:** 8085
**Database:** `smartportx_vessel` schema
**Primary Events Published:** `VesselScheduled`, `DischargePlanCreated`, `DischargeProgressUpdated`, `VesselDeparted`
**Primary Events Consumed:** `ContainerPlaced`, `ContainerRetrieved`, `YardUtilizationUpdated`

**Responsibilities:**
- Maintain vessel schedule (arrival, berthing, departure times)
- Create discharge plans (which containers to unload, in what order, to which yard positions)
- Track discharge progress in real-time (containers discharged vs. remaining)
- Coordinate with Yard service for optimal container placement during discharge
- Provide berth utilization metrics

**Domain Model:**
- `Vessel` — vessel name, IMO number, call sign, LOA, beam, draft, arrival/departure times
- `Berth` — berth identifier, max LOA, max draft, current vessel, status
- `DischargePlan` — vessel reference, list of container discharge assignments, created timestamp
- `DischargeAssignment` — container reference, source bay/row/tier on vessel, target yard position, priority
- `DischargeProgress` — total containers, discharged count, remaining count, start/end timestamps

### 5.9 Service Detail: smartportx-integration

**Port:** 8086
**Database:** `smartportx_integration` schema
**Primary Events Published:** `EdifactMessageReceived`, `EdifactMessageProcessed`, `IoTSensorData`
**Primary Events Consumed:** `ContainerArrived`, `ContainerDeparted`, `VesselScheduled`, `GateEvent`

**Responsibilities:**
- Parse inbound EDIFACT messages (COPRAR, COPARN) and translate to domain events
- Generate outbound EDIFACT messages (COARRI, CODECO) from domain events
- Ingest simulated IoT sensor data (container temperature, GPS, shock)
- Provide adapter pattern for future external system integrations
- Maintain message audit log (every EDIFACT message received/sent, with status)

**Domain Model:**
- `EdifactMessage` — message ID, type (COPRAR/COARRI/CODECO/COPARN), raw content, parsed status, timestamp
- `MessageProcessingLog` — message reference, processing step, status, error details, timestamp
- `IoTSensorReading` — sensor ID, container reference, reading type (temperature/GPS/shock), value, timestamp
- `ExternalAdapter` — adapter name, system type, connection config, status

**EDIFACT Processing Pipeline:**
```
Raw EDIFACT → Receive → Validate → Parse → Translate to Domain Event → Publish Event → Log
```

### 5.10 Service Detail: smartportx-notification

**Port:** 8087
**Database:** `smartportx_notification` schema (for notification history)
**Primary Events Published:** `NotificationSent`
**Primary Events Consumed:** `ContainerPlaced`, `ContainerRetrieved`, `GateEvent`, `AppointmentCreated`, `AppointmentUpdated`, `YardUtilizationUpdated`, `VesselScheduled`, `DischargeProgressUpdated`

**Responsibilities:**
- Maintain WebSocket connections from dashboard clients
- Broadcast real-time events to connected clients (filtered by subscription/role)
- Persist notification history for audit and replay
- Support notification channels: WebSocket (primary), future: email, SMS
- Manage client subscription topics (which events each client receives)

**Domain Model:**
- `WebSocketSession` — session ID, user reference, connected timestamp, subscriptions
- `Notification` — type, payload, target users/roles, timestamp, delivery status
- `NotificationSubscription` — user reference, event types, active flag
- `NotificationHistory` — notification reference, sent timestamp, acknowledged flag

---

## 6. Communication Architecture Summary

### 6.1 Communication Patterns

SmartPortX employs three communication patterns, each selected for a specific use case:

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Communication Architecture                     │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Pattern 1: Synchronous REST (Client → Gateway → Service)   │   │
│  │  Use: Queries, CRUD, synchronous business operations        │   │
│  │  Protocol: HTTP/HTTPS + JSON                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Pattern 2: Asynchronous Events (Service → RabbitMQ → Svc)  │   │
│  │  Use: Domain events, cross-service notifications, decoupling│   │
│  │  Protocol: AMQP 0.9.1 (RabbitMQ)                            │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Pattern 3: WebSocket (Gateway → Client)                    │   │
│  │  Use: Real-time dashboard updates, live yard map, gate queue│   │
│  │  Protocol: WebSocket + STOMP                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.2 Synchronous REST Architecture

All synchronous communication flows through the Gateway:

```
Client (Dashboard)
       │
       │  HTTPS + JWT
       ▼
┌──────────────┐
│   Gateway    │──► Rate Limit Check
│   (8080)     │──► JWT Validation
└──────┬───────┘──► Route to Service
       │
       ├──► smartportx-identity    (8081)  /api/auth/**
       ├──► smartportx-yard        (8082)  /api/yard/**
       ├──► smartportx-gate        (8083)  /api/gate/**
       ├──► smartportx-container   (8084)  /api/containers/**
       ├──► smartportx-vessel      (8085)  /api/vessels/**
       ├──► smartportx-integration (8086)  /api/integration/**
       └──► smartportx-notification(8087)  /api/notifications/**
```

**Route Table:**

| Route Prefix | Target Service | Authentication Required |
|---|---|---|
| `/api/auth/**` | Identity | No (public endpoints) |
| `/api/yard/**` | Yard | Yes |
| `/api/gate/**` | Gate | Yes |
| `/api/containers/**` | Container | Yes |
| `/api/vessels/**` | Vessel | Yes |
| `/api/integration/**` | Integration | Yes (Admin only) |
| `/api/notifications/**` | Notification | Yes |
| `/ws/**` | Notification (WebSocket) | Yes (via handshake) |

### 6.3 Asynchronous Event Architecture

```
                    ┌─────────────────┐
                    │    RabbitMQ     │
                    │   (Message      │
                    │    Broker)      │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
    ┌─────────▼───┐  ┌──────▼──────┐  ┌───▼──────────┐
    │   Exchange   │  │  Exchange   │  │  Exchange     │
    │   port.      │  │  gate.      │  │  vessel.      │
    │   container  │  │  events     │  │  events       │
    └──────┬──────┘  └──────┬──────┘  └──────┬───────┘
           │                │                │
    ┌──────▼──────────────────▼──────────────────▼──────┐
    │              Queue Consumers                       │
    │  Yard ← ContainerMoved                            │
    │  Gate ← ContainerPlaced                           │
    │  Notification ← *.events                          │
    │  Integration ← COARRI needed, CODECO needed       │
    └───────────────────────────────────────────────────┘
```

**Event Naming Convention:**
- Pattern: `{DomainEntity}{Verb}` (past tense)
- Examples: `ContainerPlaced`, `GateEvent`, `VesselScheduled`, `DischargeProgressUpdated`
- All events include: `eventId` (UUID), `eventType`, `timestamp`, `version`, `source`, `payload`

**Exchange Topology:**

| Exchange | Type | Bindings | Purpose |
|---|---|---|---|
| `port.container.events` | topic | `container.placed`, `container.retrieved`, `container.status_changed` | Container domain events |
| `port.gate.events` | topic | `gate.truck_arrived`, `gate.truck_departed`, `gate.appointment_*` | Gate domain events |
| `port.vessel.events` | topic | `vessel.scheduled`, `vessel.discharge_*`, `vessel.departed` | Vessel domain events |
| `port.yard.events` | topic | `yard.utilization_updated`, `yard.reshuffle_needed` | Yard domain events |

### 6.4 WebSocket Architecture

```
┌──────────────┐    WebSocket     ┌──────────────┐    STOMP    ┌──────────────┐
│   Dashboard  │◄────────────────►│   Gateway    │◄───────────►│ Notification │
│   Client     │                  │  (WS Proxy)  │             │   Service    │
└──────────────┘                  └──────────────┘             └──────┬───────┘
                                                                     │
                                                              ┌──────▼───────┐
                                                              │  RabbitMQ    │
                                                              │  Consumer    │
                                                              └──────────────┘
```

**Subscription Topics:**

| Topic Pattern | Data | Subscribers |
|---|---|---|
| `/topic/yard/{blockId}` | Real-time block utilization | Yard Manager, Terminal Operator |
| `/topic/gate/queue` | Gate queue status | Gate Operator |
| `/topic/vessel/{vesselId}/discharge` | Discharge progress | Terminal Operator |
| `/topic/containers/{containerId}` | Container status changes | Terminal Operator, Yard Manager |
| `/topic/alerts` | System alerts and warnings | All authenticated users |

### 6.5 API Versioning Strategy

All APIs use URL-based versioning:
- Base path: `/api/v1/{service}/{resource}`
- Example: `/api/v1/yard/blocks`, `/api/v1/containers/{id}`
- Version bumps for breaking changes only
- Deprecated versions receive 12-month support window (with sunset headers)

---

## 7. Data Architecture Summary

### 7.1 Schema-per-Service Strategy

SmartPortX uses a **single PostgreSQL instance** with **separate schemas** for each service. This provides logical data isolation without the operational overhead of managing multiple database servers.

```
┌──────────────────────────────────────────────────────────────┐
│                    PostgreSQL 17.11                          │
│                    (Single Instance)                         │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │smartportx_   │  │smartportx_   │  │smartportx_       │  │
│  │  identity    │  │  yard        │  │  gate            │  │
│  │              │  │              │  │                  │  │
│  │ users        │  │ yard_blocks  │  │ appointments     │  │
│  │ roles        │  │ bays         │  │ gate_transactions│  │
│  │ user_roles   │  │ container_   │  │ gate_lanes       │  │
│  │ refresh_     │  │   positions  │  │ dynamic_quotas   │  │
│  │   tokens     │  │ stacking_    │  │                  │  │
│  │              │  │   rules      │  │                  │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │smartportx_   │  │smartportx_   │  │smartportx_       │  │
│  │  container   │  │  vessel      │  │  integration     │  │
│  │              │  │              │  │                  │  │
│  │ containers   │  │ vessels      │  │ edifact_messages │  │
│  │ container_   │  │ berths       │  │ message_logs     │  │
│  │   movements  │  │ discharge_   │  │ iot_readings     │  │
│  │ dangerous_   │  │   plans      │  │ external_adapters│  │
│  │   goods      │  │ discharge_   │  │                  │  │
│  │              │  │   progress   │  │                  │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
│                                                              │
│  ┌──────────────┐                                           │
│  │smartportx_   │                                           │
│  │  notification│                                           │
│  │              │                                           │
│  │ notifications│                                           │
│  │ subscriptions│                                           │
│  │ ws_sessions  │                                           │
│  └──────────────┘                                           │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 Data Ownership Rules

1. **No cross-schema joins.** Each service queries only its own schema.
2. **No shared tables.** If two services need the same data, one service owns it and the other receives it via events or API calls.
3. **Eventual consistency is acceptable.** Cross-service data (e.g., container location visible in both Yard and Container services) is synchronized via events. Small delays (< 1 second) are acceptable for port operations.
4. **Each service manages its own migrations.** Flyway runs migrations per schema, per service.

### 7.3 Cross-Service Data Access Patterns

| Scenario | Pattern | Details |
|---|---|---|
| Dashboard shows container with yard position | API Composition | Dashboard calls Container API, then Yard API. No direct DB access. |
| Yard needs container weight for stacking | Event | Container service publishes `ContainerCreated` event with weight. Yard service consumes and caches. |
| Gate needs to verify container exists | Synchronous API | Gate service calls Container API to verify container before processing gate-in. |
| Notification needs container location | Event | Yard service publishes `ContainerPlaced`. Notification service broadcasts to subscribers. |
| Integration needs gate event for EDIFACT | Event | Gate service publishes `GateEvent`. Integration service generates CODECO message. |

### 7.4 Database Configuration

```yaml
# Per-service DataSource configuration (example for Yard service)
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/smartportx
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    properties:
      hibernate:
        default_schema: smartportx_yard
  flyway:
    locations: classpath:db/migration/yard
    schemas: smartportx_yard
```

### 7.5 Data Retention Policy

| Data Type | Retention | Rationale |
|---|---|---|
| Container records | Permanent | Audit and historical tracking |
| Container movements | 2 years | Operational history |
| Gate transactions | 1 year | Regulatory compliance |
| IoT sensor readings | 6 months | Diagnostic purposes |
| EDIFACT messages | 1 year | Regulatory compliance, debugging |
| Notification history | 3 months | Audit trail |
| Audit logs | 2 years | Security compliance |
| Utilization snapshots | 1 year | Trend analysis |

---

## 8. Security Architecture Summary

### 8.1 Security Layers

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Security Architecture                        │
│                                                                     │
│  Layer 1: Transport Security                                        │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ TLS 1.3 for all external connections                          │  │
│  │ Internal service communication over Docker network            │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  Layer 2: Authentication (Gateway)                                  │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ JWT validation on every request (except /auth/**)             │  │
│  │ Token expiration: 15 min access, 7 days refresh              │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  Layer 3: Authorization (Service Level)                             │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ RBAC role checks on every endpoint                            │  │
│  │ Method-level security (@PreAuthorize annotations)             │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  Layer 4: Rate Limiting (Gateway)                                   │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ 100 requests per minute per API key                           │  │
│  │ Token bucket algorithm with burst allowance                   │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  Layer 5: Input Validation (Service Level)                          │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ Bean Validation (@Valid, @NotNull, @Size, etc.)              │  │
│  │ SQL injection prevention via parameterized queries (JPA)      │  │
│  │ XSS prevention via output encoding                            │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.2 Authentication Flow

```
┌──────────┐         ┌──────────┐         ┌──────────┐
│ Dashboard│         │ Gateway  │         │ Identity │
│ Client   │         │          │         │ Service  │
└────┬─────┘         └────┬─────┘         └────┬─────┘
     │                    │                    │
     │  POST /auth/login  │                    │
     │  (username/pass)   │                    │
     │───────────────────►│                    │
     │                    │  Forward to        │
     │                    │  Identity          │
     │                    │───────────────────►│
     │                    │                    │
     │                    │  { accessToken,    │
     │                    │    refreshToken }  │
     │                    │◄───────────────────│
     │  { accessToken,    │                    │
     │    refreshToken }  │                    │
     │◄───────────────────│                    │
     │                    │                    │
     │  GET /api/yard/    │                    │
     │  Authorization:    │                    │
     │  Bearer <token>    │                    │
     │───────────────────►│                    │
     │                    │                    │
     │                    │  Validate JWT      │
     │                    │  (decode locally   │
     │                    │   with public key) │
     │                    │                    │
     │                    │  Forward to Yard   │
     │                    │  + X-User-Id header│
     │                    │───────────────────►│
     │                    │                    │
     │                    │  { yard data }     │
     │                    │◄───────────────────│
     │  { yard data }     │                    │
     │◄───────────────────│                    │
```

### 8.3 JWT Token Structure

**Access Token (15-minute expiry):**
```json
{
  "sub": "user-id-uuid",
  "iss": "smartportx-identity",
  "iat": 1693000000,
  "exp": 1693000900,
  "roles": ["TERMINAL_OPERATOR"],
  "permissions": ["yard:read", "yard:write", "container:read", "container:write"]
}
```

**Refresh Token (7-day expiry):**
```json
{
  "sub": "user-id-uuid",
  "iss": "smartportx-identity",
  "iat": 1693000000,
  "exp": 1693604800,
  "type": "refresh",
  "jti": "token-uuid"
}
```

### 8.4 Role-Permission Matrix

| Resource | Admin | Terminal Operator | Yard Manager | Gate Operator | Read-Only |
|---|---|---|---|---|---|
| Users (CRUD) | Full | — | — | — | — |
| Roles | Full | — | — | — | — |
| Yard Layout | Full | Read | Full | Read | Read |
| Container Positions | Full | Read/Write | Read/Write | Read | Read |
| Container CRUD | Full | Read/Write | Read | Read | Read |
| Gate Operations | Full | Read | Read | Full | Read |
| Appointments | Full | Read | Read | Full | Read |
| Vessel Scheduling | Full | Full | Read | Read | Read |
| Discharge Plans | Full | Full | Read | Read | Read |
| Integration Config | Full | — | — | — | — |
| System Settings | Full | — | — | — | — |

### 8.5 Security Constraints

| Constraint | Implementation |
|---|---|
| Password hashing | BCrypt with strength factor 12 |
| Account lockout | 5 failed attempts → 15-minute lockout |
| Token revocation | Refresh tokens stored in DB; revocation via DB flag |
| CORS policy | Dashboard origin only; configurable via environment variable |
| SQL injection | Parameterized queries via JPA; no raw SQL |
| XSS | React auto-escaping; Helmet.js headers on Gateway |
| Secrets management | Environment variables; no secrets in code or config files |

---

## 9. Deployment Architecture Summary

### 9.1 Development Deployment (Docker Compose)

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Docker Compose (Development)                     │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Container: smartportx-gateway                              │   │
│  │  Port: 8080                                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Container: smartportx-identity                             │   │
│  │  Port: 8081                                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Container: smartportx-yard                                 │   │
│  │  Port: 8082                                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Container: smartportx-gate                                 │   │
│  │  Port: 8083                                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Container: smartportx-container                            │   │
│  │  Port: 8084                                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Container: smartportx-vessel                               │   │
│  │  Port: 8085                                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Container: smartportx-integration                          │   │
│  │  Port: 8086                                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Container: smartportx-notification                         │   │
│  │  Port: 8087                                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Container: smartportx-dashboard (Nginx + React)            │   │
│  │  Port: 3000                                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Container: postgres                                        │   │
│  │  Port: 5432 | Volumes: pgdata                               │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Container: rabbitmq                                        │   │
│  │  Ports: 5672 (AMQP), 15672 (Management UI)                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Container: eureka-server                                   │   │
│  │  Port: 8761                                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Container: prometheus                                      │   │
│  │  Port: 9090                                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Container: grafana                                         │   │
│  │  Port: 3001                                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Container: jaeger                                          │   │
│  │  Port: 16686 (UI)                                           │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 9.2 Production Deployment Considerations

For production deployment (beyond student project scope but architecturally planned):

| Aspect | Development | Production |
|---|---|---|
| Orchestration | Docker Compose | Docker Swarm or Kubernetes |
| Database | Single PostgreSQL | Primary + read replica |
| RabbitMQ | Single node | Clustered (3 nodes) |
| TLS | Self-signed certs | Let's Encrypt / CA-issued |
| Scaling | Single instance per service | Horizontal scaling for Gate, Container, Notification |
| Monitoring | Basic Prometheus + Grafana | Full APM with alerts, PagerDuty integration |
| Backup | Manual pg_dump | Automated daily backups with point-in-time recovery |
| CI/CD | Push to main → build | PR → review → build → test → stage → approval → production |

### 9.3 Network Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                     Docker Network: smartportx-net                   │
│                     Subnet: 172.20.0.0/16                            │
│                                                                      │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐               │
│  │   Gateway   │   │  Identity   │   │    Yard     │               │
│  │ 172.20.0.10 │   │ 172.20.0.11 │   │ 172.20.0.12│               │
│  └──────┬──────┘   └─────────────┘   └─────────────┘               │
│         │                                                            │
│  ┌──────┴──────────────────────────────────────────────────────┐    │
│  │              Internal Service Bus (Docker Network)          │    │
│  └──────┬──────────────────────────────────────────────────────┘    │
│         │                                                            │
│  ┌──────┴──────┐   ┌─────────────┐   ┌─────────────┐               │
│  │    Gate     │   │  Container  │   │   Vessel    │               │
│  │ 172.20.0.13 │   │ 172.20.0.14│   │ 172.20.0.15│               │
│  └─────────────┘   └─────────────┘   └─────────────┘               │
│                                                                      │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐               │
│  │ Integration │   │ Notification│   │ Eureka      │               │
│  │ 172.20.0.16 │   │ 172.20.0.17│   │ 172.20.0.20│               │
│  └─────────────┘   └─────────────┘   └─────────────┘               │
│                                                                      │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐               │
│  │ PostgreSQL  │   │  RabbitMQ   │   │ Prometheus  │               │
│  │ 172.20.0.30 │   │ 172.20.0.31│   │ 172.20.0.40│               │
│  └─────────────┘   └─────────────┘   └─────────────┘               │
└──────────────────────────────────────────────────────────────────────┘
```

### 9.4 Environment Variables

| Variable | Description | Default (Dev) |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `development` |
| `DB_HOST` | PostgreSQL host | `postgres` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `smartportx` |
| `DB_USERNAME` | Database user | `smartportx` |
| `DB_PASSWORD` | Database password | `smartportx_dev` |
| `RABBITMQ_HOST` | RabbitMQ host | `rabbitmq` |
| `RABBITMQ_PORT` | RabbitMQ port | `5672` |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | Eureka URL | `http://eureka-server:8761/eureka/` |
| `JWT_SECRET` | JWT signing secret | (generated at startup) |
| `JWT_EXPIRATION_MINUTES` | Access token lifetime | `15` |
| `CORS_ALLOWED_ORIGINS` | Dashboard origin | `http://localhost:3000` |
| `RATE_LIMIT_PER_MINUTE` | API rate limit | `100` |

---

## 10. Key Design Principles

### 10.1 Principle List

| # | Principle | Description | Application |
|---|---|---|---|
| 1 | **Service Autonomy** | Each service owns its data, logic, and deployment. No service can be broken by changes in another service without explicit contract violation. | Schema-per-service, independent deployment units, event-driven decoupling. |
| 2 | **Contract-First Design** | API contracts are defined before implementation. OpenAPI specifications serve as the source of truth. | OpenAPI 3.0 specs authored in Phase 2. Code generation from specs where possible. |
| 3 | **Eventual Consistency Over Distributed Transactions** | Cross-service data consistency is achieved through domain events, not two-phase commit or distributed transactions. | Container position in Yard service is eventually consistent with Container service's location field. |
| 4 | **Design for Failure** | Every service call can fail. Circuit breakers, retries with backoff, and graceful degradation are mandatory. | Resilience4j for circuit breakers, fallback responses for non-critical data. |
| 5 | **Single Source of Truth Per Entity** | Each domain entity has exactly one owning service. Other services may cache data but never treat their cache as authoritative. | Container entity is owned by Container service. Yard service caches container weight but Container service is authoritative. |
| 6 | **Observability by Default** | Every service emits structured logs, metrics, and traces. Debugging production issues should not require SSH into containers. | Micrometer + Prometheus for metrics, OpenTelemetry for traces, structured JSON logging. |
| 7 | **Security in Depth** | No single security layer is sufficient. TLS, authentication, authorization, rate limiting, and input validation are all required. | Gateway handles TLS + auth. Services handle authorization + validation. |
| 8 | **Incremental Complexity** | Start simple, add complexity only when needed. A single PostgreSQL instance with schema separation is sufficient until proven otherwise. | No Kubernetes until team size or scale demands it. No Kafka until RabbitMQ proves insufficient. |
| 9 | **Domain-Driven Boundaries** | Service boundaries are drawn along domain lines, not technical layers. Yard, Gate, Container, and Vessel are domain services, not "data service" or "API service." | Bounded contexts from Phase 1 directly map to services. |
| 10 | **Reversibility** | Prefer reversible decisions. Use abstractions (interfaces, event buses) so that underlying implementations can be swapped without breaking consumers. | Repository pattern allows database technology changes. Event bus allows broker swaps. |

### 10.2 Anti-Patterns Avoided

| Anti-Pattern | Why It's Avoided | SmartPortX Alternative |
|---|---|---|
| **Shared Database** | Creates tight coupling; schema changes in one service break others. | Schema-per-service with event-driven synchronization. |
| **Distributed Monolith** | Services that must all deploy together defeat the purpose of distribution. | Each service is independently deployable. No circular dependencies. |
| **God Service** | One service that handles everything becomes an unscalable monolith. | 8 focused services with clear bounded contexts. |
| **Chatty Communication** | Many fine-grained API calls between services create latency and coupling. | Aggregated APIs in services that own the composite view. Event-driven updates reduce polling. |
| **Database-First Design** | Designing schemas before understanding the domain leads to anemic models. | Domain events and bounded contexts drive schema design. |

---

## 11. Constraints and Trade-offs

### 11.1 Project Constraints

| Constraint | Impact | Mitigation |
|---|---|---|
| **3–4 person team** | Limited parallel development capacity; cannot maintain complex infrastructure. | SBA with Docker Compose. Single PostgreSQL. No Kubernetes. |
| **4–6 week timeline** | Insufficient time for full microservices infrastructure, comprehensive testing, or production hardening. | SBA allows starting as a modular monolith and extracting services incrementally. Focus on core domain services first. |
| **Single terminal scope** | No multi-terminal or multi-tenant requirements. | Simpler data model. No tenant isolation. No cross-terminal orchestration. |
| **Simulated IoT** | No physical sensors; data is generated programmatically. | Integration service includes IoT simulator module. Architecture supports real sensors without changes. |
| **Web dashboard only** | No mobile apps, no native clients, no external consumer-facing APIs. | WebSocket + REST is sufficient. No need for GraphQL, gRPC, or mobile-optimized APIs. |
| **Student project** | Primary goal is learning and demonstration, not production deployment. | Architecture is production-inspired but deployment is Docker Compose. Code quality standards are aspirational but not enterprise-grade. |

### 11.2 Key Trade-off Decisions

| Trade-off | Decision | Rationale |
|---|---|---|
| **Single PostgreSQL vs. separate instances** | Single instance, schema-per-service | Operational simplicity. A single `docker-compose up` starts everything. Schema separation provides logical isolation sufficient for this scale. |
| **RabbitMQ vs. Kafka** | RabbitMQ | Simpler operational model. Task queue semantics (not log-based) are appropriate for domain events at this volume. Lower learning curve. |
| **REST vs. gRPC** | REST | Team familiarity. Browser compatibility. OpenAPI ecosystem. gRPC would add protobuf complexity without proportional benefit at this scale. |
| **Monorepo vs. polyrepo** | Monorepo | Single Git repository for all services. Simpler dependency management, atomic commits across services, shared build configuration. Polyrepo overhead is unjustified for 3–4 people. |
| **Spring Cloud Gateway vs. Kong/Nginx** | Spring Cloud Gateway | Stays within Spring ecosystem. No need to learn a separate technology. Filters are written in Java. |
| **JWT vs. session-based auth** | JWT (stateless) | Stateless authentication fits the distributed model. No session store required. Gateway validates tokens locally with public key. |
| **Flyway vs. Liquibase** | Flyway | Simpler migration model. SQL-based migrations are more transparent than XML/JSON changelogs. Spring Boot native integration. |

### 11.3 Technical Debt Acceptance

The following technical debts are **intentionally accepted** for the student project timeline:

1. **No Kubernetes deployment** — Docker Compose is sufficient. Kubernetes migration is a documented future step.
2. **No circuit breakers in Phase 4** — Resilience4j integration is planned but not mandatory for demo. Services call each other directly.
3. **Limited test coverage** — Unit tests for critical paths, integration tests for service boundaries. Full E2E testing is aspirational.
4. **No secrets vault** — Environment variables for secrets. A production deployment would use HashiCorp Vault or AWS Secrets Manager.
5. **Simulated data only** — No real port data. IoT simulator generates synthetic container and sensor data.
6. **Single PostgreSQL instance** — No read replicas, no connection pooling (PgBouncer), no automated backups beyond manual pg_dump.

---

## 12. Architecture Decision Records Index

Architecture Decision Records (ADRs) document significant decisions made during the project. Each ADR captures the context, decision, and consequences of a specific choice.

| ADR # | Title | Status | Date | Summary |
|---|---|---|---|---|
| ADR-001 | Service-Based Architecture over Microservices | Accepted | Phase 3 | Chose SBA for SOA compliance with small team constraints. Microservices rejected due to operational complexity. |
| ADR-002 | PostgreSQL Schema-per-Service | Accepted | Phase 3 | Logical data isolation within a single PostgreSQL instance. Balances ownership with operational simplicity. |
| ADR-003 | RabbitMQ over Kafka | Accepted | Phase 3 | RabbitMQ selected for simpler operational model, task queue semantics, and lower learning curve. |
| ADR-004 | JWT Stateless Authentication | Accepted | Phase 3 | 15-minute access tokens with 7-day refresh. Gateway validates locally. No session store. |
| ADR-005 | Monorepo for All Services | Accepted | Phase 3 | Single Git repository. Simplifies cross-service refactoring and shared build configuration. |
| ADR-006 | Spring Cloud Gateway | Accepted | Phase 3 | Selected over Kong/Nginx for ecosystem consistency. Filters in Java. Native Spring integration. |
| ADR-007 | REST over gRPC | Accepted | Phase 3 | REST selected for browser compatibility, team familiarity, and OpenAPI ecosystem. |
| ADR-008 | Docker Compose over Kubernetes | Accepted | Phase 3 | Docker Compose for development and demo. Kubernetes is a documented future migration path. |
| ADR-009 | EDIFACT for TOS Integration | Accepted | Phase 2 | Standard maritime message format. COPRAR, COARRI, CODECO, COPARN for discharge/load/gate operations. |
| ADR-010 | React + TypeScript for Dashboard | Accepted | Phase 2 | Component-based SPA. Type safety. Team familiarity. Vite for build tooling. |
| ADR-011 | Flyway for Database Migrations | Accepted | Phase 3 | SQL-based migrations. Simple versioned approach. Native Spring Boot integration. |
| ADR-012 | Event-Driven Cross-Service Communication | Accepted | Phase 3 | Domain events via RabbitMQ for decoupled inter-service communication. REST for synchronous queries. |
| ADR-013 | OpenTelemetry for Distributed Tracing | Accepted | Phase 3 | Vendor-neutral tracing standard. Export to Jaeger for visualization. |
| ADR-014 | RBAC with Five Defined Roles | Accepted | Phase 2 | Admin, Terminal Operator, Yard Manager, Gate Operator, Read-Only. Covers all operational personas. |
| ADR-015 | Simulated IoT Data Pipeline | Accepted | Phase 2 | Integration service includes simulator module. Architecture supports real sensors without changes. |

> **Note:** ADRs are immutable once accepted. If a decision is reversed, a new ADR is created that supersedes the original, and the original's status is updated to "Superseded."

---

## Appendix A: Service Port Allocation

| Service | Port | Management Port |
|---|---|---|
| smartportx-gateway | 8080 | — |
| smartportx-identity | 8081 | 9081 |
| smartportx-yard | 8082 | 9082 |
| smartportx-gate | 8083 | 9083 |
| smartportx-container | 8084 | 9084 |
| smartportx-vessel | 8085 | 9085 |
| smartportx-integration | 8086 | 9086 |
| smartportx-notification | 8087 | 9087 |

> Management ports expose Spring Boot Actuator endpoints (`/actuator/health`, `/actuator/metrics`, `/actuator/info`).

## Appendix B: Docker Compose Service Startup Order

```
1. postgres           (database must be ready first)
2. rabbitmq           (message broker)
3. eureka-server      (service registry)
4. smartportx-identity (auth is required by all other services)
5. smartportx-container (domain entity used by many services)
6. smartportx-yard     (depends on container events)
7. smartportx-gate     (depends on container events)
8. smartportx-vessel   (depends on container and yard)
9. smartportx-integration (depends on all domain services)
10. smartportx-notification (depends on all domain services)
11. smartportx-gateway (depends on all services being registered)
12. dashboard          (depends on gateway)
```

> Health checks with `depends_on` conditions ensure proper startup ordering.

## Appendix C: Glossary

| Term | Definition |
|---|---|
| **Bay** | A vertical slice within a yard block, numbered sequentially. |
| **Berth** | A physical location at the quay where a vessel moors for loading/unloading. |
| **Block** | A large rectangular area in the yard, subdivided into bays. |
| **COPRAR** | UN/EDIFACT message for Container Discharge/Loading Order. |
| **CODECO** | UN/EDIFACT message for Container Gate-In/Gate-Out Report. |
| **COARRI** | UN/EDIFACT message for Container Discharge/Loading Report. |
| **COPARN** | UN/EDIFACT message for Container Announcement (arrival notice). |
| **Dangerous Goods** | Containers carrying hazardous materials, classified by UN number and hazard class. |
| **Discharge Plan** | A plan specifying which containers to unload from a vessel and where to place them in the yard. |
| **Dwell Time** | The duration a container remains in the yard. |
| **ISO Type** | Standardized container dimensions and type (e.g., 20GP, 40HC, 40RF). |
| **Reshuffle** | Moving a container that is on top of another container to access the one beneath. |
| **Row** | A horizontal position within a bay, numbered sequentially. |
| **SBA** | Service-Based Architecture — the architectural style selected for SmartPortX. |
| **SOA** | Service-Oriented Architecture — an architectural paradigm based on services. |
| **Tier** | A vertical stacking position within a bay/row combination. |
| **TOS** | Terminal Operating System — the existing software system managing port operations. |

---

## Appendix D: Document Metadata

| Field | Value |
|---|---|
| Document Title | SmartPortX Master Architecture Overview |
| Version | 1.0 |
| Author | SmartPortX Engineering Team |
| Last Updated | 2026-08-26 |
| Classification | Internal — Phase 3 Architecture Documentation |
| Review Status | Pending team review |
| Supersedes | None (initial version) |
| Related Documents | ADR-001 through ADR-015, API Specification (Phase 2), Domain Model (Phase 1) |

---

*End of ARCHITECTURE-OVERVIEW.md*
