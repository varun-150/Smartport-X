# ADR-005: Message Broker — RabbitMQ

**Status:** PROPOSED
**Date:** 2026-08-26
**Deciders:** Architect, Lead

---

## Context

The SmartPort SBA architecture requires asynchronous communication between services. When a vessel arrives at a port, multiple services need to react:

1. **Vessel service** updates the vessel status.
2. **Berth service** checks for pending reservations.
3. **Compliance service** triggers inspection scheduling.
4. **Notification service** sends alerts to port operators.
5. **Analytics service** records the arrival event.

Making synchronous REST calls from vessel-service to all four other services would create tight coupling and fragile failure chains. If notification-service is down, the vessel arrival might fail or block.

The team needs a message broker that:
- Decouples producers from consumers.
- Supports multiple consumers per message (fan-out).
- Handles message persistence and delivery guarantees.
- Is lightweight enough for Docker Compose deployment.
- Has good Spring Boot integration.

## Decision

We adopt **RabbitMQ 3.13+** as the message broker for all asynchronous inter-service communication.

### Exchange and Queue Topology

```
┌─────────────────────────────────────────────────────────────┐
│                      RabbitMQ Broker                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Exchange: smartport.events (topic)                         │
│  ├── Binding: vessel.*          → vessel-events-queue       │
│  ├── Binding: berth.*           → berth-events-queue        │
│  ├── Binding: cargo.*           → cargo-events-queue        │
│  ├── Binding: compliance.*      → compliance-events-queue   │
│  ├── Binding: notification.*    → notification-events-queue │
│  └── Binding: analytics.*       → analytics-events-queue    │
│                                                             │
│  Exchange: smartport.commands (direct)                      │
│  ├── Binding: vessel.status     → vessel-commands-queue     │
│  ├── Binding: berth.reserve     → berth-commands-queue      │
│  └── Binding: cargo.update      → cargo-commands-queue      │
│                                                             │
│  Exchange: smartport.notifications (fanout)                  │
│  ├── Binding: email-channel     → email-notification-queue  │
│  ├── Binding: sms-channel       → sms-notification-queue    │
│  └── Binding: push-channel      → push-notification-queue   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Message Format

```json
{
  "eventId": "evt-uuid-1234",
  "eventType": "VesselArrival",
  "timestamp": "2026-08-26T14:30:00Z",
  "source": "vessel-service",
  "correlationId": "corr-uuid-5678",
  "payload": {
    "imoNumber": "IMO1234567",
    "vesselName": "MV SmartPort",
    "berthId": "BERTH-A1",
    "arrivalTime": "2026-08-26T14:25:00Z"
  }
}
```

### Spring AMQP Configuration

```yaml
# application.yml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}
    publisher-confirm-type: correlated
    publisher-returns: true
    listener:
      simple:
        retry:
          enabled: true
          initial-interval: 1000
          multiplier: 2
          max-attempts: 3
        prefetch: 10
        acknowledge-mode: manual
```

### Event Types

| Event | Exchange | Routing Key | Consumers |
|-------|----------|-------------|-----------|
| `VesselArrival` | smartport.events | vessel.arrival | berth, compliance, notification, analytics |
| `VesselDeparture` | smartport.events | vessel.departure | berth, notification, analytics |
| `VesselPositionUpdate` | smartport.events | vessel.position | analytics (throttled) |
| `BerthReserved` | smartport.events | berth.reserved | notification, cargo |
| `BerthReleased` | smartport.events | berth.released | vessel, analytics |
| `CargoManifestSubmitted` | smartport.events | cargo.manifest | compliance, notification |
| `InspectionScheduled` | smartport.events | compliance.inspection | notification |
| `InspectionCompleted` | smartport.events | compliance.completed | analytics, notification |

## Alternatives Considered

### 1. Apache Kafka
A distributed event streaming platform.
- **Pros:** Extremely high throughput, durable event log, event sourcing capability, exactly-once semantics, replay capability.
- **Cons:** Significant resource overhead (~1GB RAM minimum), complex setup (Zookeeper/KRaft), overkill for moderate throughput needs, requires topic partitioning strategy, harder to run in Docker Compose.
- **Verdict:** Rejected — throughput requirements don't justify the complexity. RabbitMQ handles thousands of messages per second, which is more than sufficient.

### 2. ActiveMQ Artemis
A message broker from the Apache project.
- **Pros:** JMS 2.0 compliant, message persistence, clustering support.
- **Cons:** Less intuitive management UI than RabbitMQ, smaller community, more complex configuration for topic exchanges, less Spring Boot auto-configuration support.
- **Verdict:** Rejected — RabbitMQ has better Spring integration and simpler management.

### 3. Amazon SQS
A managed message queue service from AWS.
- **Pros:** Zero operational overhead, auto-scaling, pay-per-use.
- **Cons:** Cloud vendor lock-in, requires AWS account and billing, higher latency than local broker, not available offline, violates the single-machine deployment principle.
- **Verdict:** Rejected — cloud dependency and vendor lock-in are unacceptable for a student project.

### 4. In-Memory Events (Spring Application Events)
Using Spring's `ApplicationEventPublisher` for in-process event dispatching.
- **Pros:** Zero infrastructure, zero latency, simple to implement.
- **Cons:** Events lost on restart, only works within a single JVM (cannot cross service boundaries), no persistence, no delivery guarantees, defeats the purpose of service-based architecture.
- **Verdict:** Rejected — in-memory events don't work across service boundaries.

### 5. NATS / JetStream
A lightweight messaging system.
- **Pros:** Extremely fast, minimal resource usage, simple protocol.
- **Cons:** Smaller community than RabbitMQ, less Spring AMQP integration (would need custom clients), JMS not natively supported, fewer managed hosting options.
- **Verdict:** Rejected — RabbitMQ's Spring integration is significantly better.

## Rationale

RabbitMQ is the optimal choice based on:

1. **Spring AMQP integration:** Spring Boot provides first-class auto-configuration for RabbitMQ with `spring-boot-starter-amqp`. No custom client libraries needed.
2. **Topic exchanges:** The topic exchange pattern perfectly matches our event routing needs — services subscribe to routing key patterns (e.g., `vessel.*`) to receive relevant events.
3. **Lightweight deployment:** RabbitMQ runs in a single Docker container with ~128MB RAM, well within our resource constraints.
4. **Management UI:** RabbitMQ's built-in management interface (port 15672) provides queue inspection, message rates, and connection monitoring without additional tooling.
5. **Delivery guarantees:** With `acknowledge-mode: manual` and `durable` queues, messages survive broker restarts and are not lost.
6. **AMQP 0-9-1 standard:** AMQP is a widely-adopted standard, reducing vendor lock-in.
7. **Fan-out support:** The fanout exchange pattern enables notification broadcasting (one message → multiple channels).
8. **Retry mechanism:** Spring AMQP's built-in retry support handles transient failures without custom retry logic.

## Consequences

### Positive
- **Simple setup** — Docker container starts in seconds, minimal configuration.
- **Flexible routing** — topic, direct, fanout, and headers exchanges cover all communication patterns.
- **Decoupled services** — producers don't know about consumers; adding new consumers requires only a new queue binding.
- **Message persistence** — durable queues survive broker restarts.
- **Management UI** — built-in web interface for monitoring and debugging.
- **Spring integration** — first-class auto-configuration with Spring Boot.
- **Delivery guarantees** — manual acknowledgment ensures at-least-once delivery.

### Negative
- **Lower throughput than Kafka** — RabbitMQ handles ~50K messages/second vs. Kafka's millions (sufficient for our needs).
- **Not ideal for event sourcing** — RabbitMQ doesn't maintain an immutable event log like Kafka.
- **No replay capability** — consumed messages cannot be replayed from the beginning (must be re-published).
- **Message ordering** — ordering is guaranteed per-queue but not across multiple queues.
- **Memory pressure** — under extreme load, RabbitMQ can consume significant memory (mitigated by flow control and monitoring).

## Related

- ADR-001: Architecture Style — SBA (async communication between services)
- ADR-003: Backend Framework — Spring Boot 3.x (Spring AMQP integration)
- ADR-006: API Gateway — Spring Cloud Gateway (handles sync requests; RabbitMQ handles async)
- RabbitMQ Documentation: https://www.rabbitmq.com/docs
- Spring AMQP Reference: https://docs.spring.io/spring-amqp/reference/
