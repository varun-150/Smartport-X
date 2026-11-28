# ADR-001: Architecture Style — Service-Based Architecture (SBA)

**Status:** PROPOSED
**Date:** 2026-08-26
**Deciders:** Architect, Lead

---

## Context

The SmartPort platform must support multiple bounded contexts — vessel tracking, berth scheduling, cargo management, and compliance — while remaining feasible for a small student development team working within a single-semester timeline. The architecture must satisfy the SOA course requirements, demonstrate service-oriented principles (loose coupling, contract-based interaction, composability), and still be deliverable as a working system on a single terminal/machine.

A monolithic architecture would simplify deployment but violate the SOA mandate for service decomposition. Full microservices would satisfy SOA principles but introduce significant operational overhead (separate deployment pipelines, distributed tracing, independent databases per service) that a small team cannot realistically maintain. An event-driven architecture alone does not provide synchronous request/response patterns needed for real-time dashboard queries.

The team needs an architecture that balances SOA principle compliance with practical delivery constraints.

## Decision

We adopt a **Service-Based Architecture (SBA)** — a middle-ground between monolith and full microservices. Each bounded context is implemented as a logically separated service with its own domain model, database schema, and API contract. Services communicate via REST (synchronous) and RabbitMQ (asynchronous). All services are deployed together in a single Docker Compose stack and can optionally be separated later.

Key characteristics of this architecture:

- **Logical separation:** Each service has its own package, domain model, and database schema.
- **Shared infrastructure:** Services share a single PostgreSQL instance and RabbitMQ broker.
- **Independent deployment capability:** Services are designed so they *could* be deployed separately, even though they currently are not.
- **API Gateway pattern:** Spring Cloud Gateway acts as the single entry point, routing to appropriate services.
- **Contract-first APIs:** REST APIs are defined with consistent request/response contracts.

### Service Boundaries

| Service | Bounded Context | Key Entities |
|---------|----------------|--------------|
| `auth-service` | Authentication & Authorization | User, Role, Permission, Token |
| `vessel-service` | Vessel Tracking | Vessel, Voyage, Position, ETA |
| `berth-service` | Berth Scheduling | Berth, Reservation, Schedule, Slot |
| `cargo-service` | Cargo Management | Shipment, Container, Manifest, CargoItem |
| `compliance-service` | Regulatory Compliance | Inspection, Certificate, Violation, Regulation |
| `notification-service` | Notifications | Notification, Alert, Subscription, Preference |
| `analytics-service` | Reporting & Analytics | Report, Dashboard, Metric, Aggregation |

### Communication Patterns

```
┌──────────────┐
│   React UI   │
└──────┬───────┘
       │ HTTP/REST
       ▼
┌──────────────────┐
│ Spring Cloud GW  │ ── JWT Validation
└──────┬───────────┘
       │ HTTP/REST
       ▼
┌─────────────────────────────────────────────────┐
│  auth │ vessel │ berth │ cargo │ compliance │ …  │
└──────────────────────┬──────────────────────────┘
                       │ AMQP 0-9-1
                       ▼
               ┌──────────────┐
               │   RabbitMQ   │
               └──────────────┘
```

## Alternatives Considered

### 1. Monolithic Architecture
A single deployable unit with all bounded contexts in one application.
- **Pros:** Simplest to build, test, and deploy; no inter-service communication overhead.
- **Cons:** Violates SOA course requirements; tight coupling between contexts; scaling is all-or-nothing; a bug in one context can crash the whole system.
- **Verdict:** Rejected — does not satisfy the SOA mandate.

### 2. Full Microservices
Each service with its own database, deployable independently, with service mesh and distributed tracing.
- **Pros:** Maximum flexibility, independent scaling, technology diversity per service.
- **Cons:** Massive operational complexity; requires Kubernetes or equivalent; distributed transactions are hard; student team lacks DevOps capacity.
- **Verdict:** Rejected for v1 — would consume the entire semester on infrastructure.

### 3. Event-Driven Architecture (EDA)
Services communicate exclusively through event streams (Kafka/NATS).
- **Pros:** Highly decoupled, excellent for real-time data flow, natural fit for vessel position streaming.
- **Cons:** Does not support synchronous request/response well; eventual consistency everywhere; complex debugging; overkill for dashboard queries.
- **Verdict:** Rejected as primary — used as a *complementary* pattern via RabbitMQ topic exchanges.

## Rationale

SBA satisfies the core SOA principles required by the course:

1. **Loose coupling:** Services interact through well-defined APIs, not shared code.
2. **Service contract:** Each service exposes a documented REST API.
3. **Service autonomy:** Each service owns its domain logic and database schema.
4. **Service reusability:** Services can be consumed by multiple consumers (UI, other services, future mobile app).
5. **Service composability:** Complex operations compose multiple service calls.

Evidence supporting this decision:
- Industry adoption: SBA is the most common architecture for SOA implementations in enterprise settings (cite: *Service-Based Architecture* by Thomas Erl).
- Team capacity: A 4-person team can realistically build and test 5-7 services in a semester.
- Deployment simplicity: Single Docker Compose file deploys the entire system.
- Course alignment: Demonstrates all required SOA patterns without infrastructure overhead.

## Consequences

### Positive
- **Loose coupling** between bounded contexts — changes in cargo-service don't require changes in vessel-service.
- **Independent deployability** — each service *can* be deployed separately when needed.
- **Team parallelism** — developers can work on different services simultaneously with minimal merge conflicts.
- **SOA principle compliance** — clearly demonstrates service orientation, contracts, and composition.
- **Incremental complexity** — can start simple and add infrastructure (distributed tracing, circuit breakers) incrementally.

### Negative
- **More complex than monolith** — requires API design, inter-service communication, and schema management.
- **Eventual consistency** — data across services may be temporarily inconsistent after writes.
- **Shared database risk** — schema coupling if not carefully managed (mitigated by schema-per-service).
- **Network overhead** — inter-service calls add latency compared to in-process method calls.
- **Testing complexity** — integration testing requires running multiple services.

## Related

- ADR-003: Backend Framework — Spring Boot 3.x (enables SBA implementation)
- ADR-004: Database — PostgreSQL 17 with Schema-per-Service (shared instance, logical separation)
- ADR-005: Message Broker — RabbitMQ (asynchronous inter-service communication)
- ADR-006: API Gateway — Spring Cloud Gateway (single entry point)
- ADR-007: Service Discovery — Netflix Eureka (service registration)
- SOA Course Requirements: Service decomposition, contract-based interaction, composability
