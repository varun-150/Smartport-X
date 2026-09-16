# SmartPortX — Communication Architecture

> SOA Container Port Logistics Platform  
> Version: 1.0.0  
> Last Updated: 2026-08-26

---

## Table of Contents

1. [Overview](#1-overview)
2. [Synchronous Communication (REST)](#2-synchronous-communication-rest)
3. [Asynchronous Communication (RabbitMQ)](#3-asynchronous-communication-rabbitmq)
4. [WebSocket Communication](#4-websocket-communication)
5. [Service Discovery](#5-service-discovery)
6. [Circuit Breaker](#6-circuit-breaker)
7. [API Gateway Routing](#7-api-gateway-routing)
8. [Inter-Service Dependency Matrix](#8-inter-service-dependency-matrix)
9. [Message Schema Standards](#9-message-schema-standards)
10. [Observability & Tracing](#10-observability--tracing)
11. [Configuration Reference](#11-configuration-reference)

---

## 1. Overview

SmartPortX uses a hybrid communication model combining synchronous REST for real-time request/response interactions and asynchronous messaging via RabbitMQ for event-driven decoupled workflows. WebSocket connections provide live status updates to frontend clients. All external traffic enters through Spring Cloud Gateway, which handles routing, authentication, rate limiting, and protocol translation.

```
┌─────────────────────────────────────────────────────────────────────┐
│                         External Clients                           │
│            (Terminal UI, Mobile, EDI Partners, Customs)            │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ HTTPS / WSS
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     Spring Cloud Gateway                           │
│         (JWT Validation, Rate Limiting, Routing, Load Balancing)    │
└───────┬──────────┬──────────┬──────────┬──────────┬────────────────┘
        │          │          │          │          │
        ▼          ▼          ▼          ▼          ▼
   ┌─────────┐┌────────┐┌────────┐┌────────┐┌──────────┐
   │Identity ││  Yard  ││  Gate  ││Container││  Vessel  │
   │ Service ││Service ││Service ││ Service ││ Service  │
   └─────────┘└────────┘└────────┘└────────┘└──────────┘
        ▲          ▲          ▲          ▲          ▲
        │          └──────┬───┘          │          │
        │                 │              │          │
        │          ┌──────▼──────────────▼──────────▼──┐
        │          │          RabbitMQ Broker           │
        │          │    (Topic / Fanout Exchanges)      │
        │          └──────┬──────────────┬─────────────┘
        │                 │              │
        └─────────────────┼──────────────┘
                          ▼
                ┌───────────────────┐
                │Notification Service│◄──── WebSocket (STOMP)
                └───────────────────┘
```

**Technology Stack:**

| Component              | Technology                          |
|------------------------|-------------------------------------|
| Service Framework      | Java 25, Spring Boot 3.x           |
| API Gateway            | Spring Cloud Gateway                |
| Service Discovery      | Netflix Eureka                      |
| Message Broker         | RabbitMQ (AMQP 0-9-1)             |
| WebSocket              | Spring WebSocket (STOMP)           |
| Load Balancer          | Spring Cloud LoadBalancer           |
| Circuit Breaker        | Resilience4j                        |
| Serialization          | Jackson (JSON)                      |
| Tracing                | Micrometer + OpenTelemetry          |

---

## 2. Synchronous Communication (REST)

### 2.1 Client → Gateway → Service Flow

All external client requests follow a single ingress path:

```
Client ──HTTP/S──▶ Gateway ──JWT Validate──▶ Route Selection ──Load Balance──▶ Service Instance
```

**Step-by-step flow:**

1. Client sends HTTP request to Gateway (`https://api.smartportx.local/api/v1/yard/zones`)
2. Gateway extracts JWT from `Authorization: Bearer <token>` header
3. Gateway validates JWT signature (RS256), expiry, and issuer claims
4. Gateway checks rate limit for the requesting API key
5. Gateway matches route pattern `/api/v1/yard/**` to service `smartportx-yard`
6. Gateway resolves service instance via Eureka + LoadBalancer
7. Gateway forwards request with enriched headers:
   - `X-User-Id`: authenticated user ID from JWT subject claim
   - `X-User-Role`: role from JWT custom claim
   - `X-Correlation-Id`: unique request trace ID (generated if absent)
   - `X-Forwarded-For`: original client IP
8. Service processes request and returns response
9. Gateway returns response to client

### 2.2 Service-to-Service REST Calls

Services communicate synchronously when a real-time response is required. These calls bypass the Gateway and communicate directly over the Docker internal network using service discovery.

```
┌──────────┐  REST (direct)  ┌──────────┐
│   Gate   │─────────────────▶│Container │
│ Service  │◀─────────────────│ Service  │
└──────────┘   Response       └──────────┘
```

**When to use synchronous REST between services:**

- Container lookup during gate-in processing (need immediate container data)
- Yard position query during vessel discharge (need real-time slot availability)
- Identity token introspection (need immediate validation)

**When NOT to use synchronous:**

- Notifications → use event publishing
- Audit trail writes → use async events
- Report generation → use async with callback
- Non-critical data enrichment → use async

### 2.3 Load Balancing

SmartPortX uses Spring Cloud LoadBalancer (replacing Netflix Ribbon) for client-side load balancing.

**Configuration:**

```yaml
spring:
  cloud:
    loadbalancer:
      ribbon:
        enabled: false
      cache:
        enabled: true
        ttl: 35s
      retry:
        enabled: true
        max-retries-on-same-service-instance: 0
        max-retries-on-next-service-instance: 2
```

**Load Balancing Strategy:**

- **Default**: Round-Robin across all healthy instances
- **Zone-aware**: Prefer instances in the same availability zone
- **Weighted**: Optional weighted distribution based on instance metadata

**Instance Health:**

Instances are only included in the load balancer pool if they pass Eureka health checks. Unhealthy instances are automatically removed within 30 seconds of failed health check.

### 2.4 Timeout Configuration

Default timeout is **5 seconds** with per-endpoint overrides for known slow operations.

```yaml
resilience4j:
  timelimiter:
    configs:
      default:
        timeoutDuration: 5s
        cancelRunningFuture: true
    instances:
      # Container search can be slow with large datasets
      container-search:
        timeoutDuration: 15s
      # Integration with external customs systems
      customs-declaration:
        timeoutDuration: 30s
      # Vessel schedule updates (external API)
      vessel-schedule:
        timeoutDuration: 20s
      # Yard optimization computation
      yard-optimization:
        timeoutDuration: 45s
```

**Timeout behavior:**

- On timeout, the calling service receives a `java.util.concurrent.TimeoutException`
- Circuit breaker records the timeout as a failure
- Request is cancelled on the callee side if `cancelRunningFuture: true`

### 2.5 Retry Policy

Retries apply only to **idempotent operations** (GET, PUT, DELETE with deterministic semantics). Non-idempotent operations (POST creating new resources) are never retried automatically.

```yaml
resilience4j:
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.client.HttpServerErrorException
        ignore-exceptions:
          - com.smartportx.common.exception.BusinessRuleException
          - com.smartportx.common.exception.EntityNotFoundException
    instances:
      container-service:
        base-config: default
        max-attempts: 5
        wait-duration: 1s
      identity-service:
        base-config: default
        # Never retry authentication failures
        retry-exceptions:
          - java.util.concurrent.TimeoutException
```

**Retry timeline (default config):**

```
Attempt 1:  immediately
Attempt 2:  +500ms   (1 × 500ms)
Attempt 3:  +1500ms  (3 × 500ms)
            Total wait before failure: ~2000ms
```

**Retry headers:**

Retried requests include `X-Retry-Attempt: <n>` header so callees can detect and deduplicate.

---

## 3. Asynchronous Communication (RabbitMQ)

### 3.1 Message Broker

- **Broker**: RabbitMQ 3.13+
- **Protocol**: AMQP 0-9-1
- **Connection**: Each service maintains a connection pool (min: 2, max: 10 connections)
- **Virtual Host**: `/smartportx` (isolated from other workloads)

### 3.2 Exchange Types

#### Topic Exchanges — Domain Events

Primary mechanism for inter-service event communication. Services publish domain events to topic exchanges and route to specific queues using binding keys.

| Exchange Name                 | Purpose                              | Publisher(s)           |
|-------------------------------|--------------------------------------|------------------------|
| `smartportx.domain-events`    | All domain state change events       | All services           |
| `smartportx.notifications`    | Notification trigger events          | All services           |
| `smartportx.audit-events`     | Audit trail events                   | All services           |
| `smartportx.integration-events`| External system integration events  | Integration Service    |

#### Fanout Exchanges — Broadcasts

Used for system-wide broadcasts where every subscriber receives every message.

| Exchange Name                    | Purpose                         |
|----------------------------------|---------------------------------|
| `smartportx.system-broadcasts`  | System health, config changes   |
| `smartportx.cache-invalidation` | Cache invalidation signals      |

### 3.3 Exchange and Queue Topology

#### Naming Conventions

- **Queues**: `{service-name}.{event-type}` (lowercase, dot-separated)
- **Dead Letter Queues**: `{queue-name}.dlq`
- **Retry Queues**: `{queue-name}.retry`
- **Binding Keys**: `{domain}.{event-type}` (e.g., `container.moved`, `gate.entry-processed`)

#### Complete Queue Topology

```
smartportx.domain-events (Topic Exchange)
│
├── binding: container.#
│   ├── yard.container-moved
│   ├── gate.container-moved
│   ├── vessel.container-loaded
│   ├── integration.container-moved
│   └── notification.container-moved
│
├── binding: yard.#
│   ├── container.yard-updated
│   ├── gate.yard-updated
│   ├── vessel.yard-updated
│   └── notification.yard-status-changed
│
├── binding: gate.#
│   ├── container.gate-entry
│   ├── yard.gate-entry
│   ├── vessel.gate-entry
│   └── notification.gate-entry-processed
│
├── binding: vessel.#
│   ├── container.vessel-arrival
│   ├── yard.vessel-arrival
│   └── notification.vessel-status-changed
│
├── binding: appointment.#
│   ├── gate.appointment-created
│   ├── yard.appointment-created
│   └── notification.appointment-updated
│
└── binding: customs.#
    ├── container.customs-cleared
    └── notification.customs-status-changed

smartportx.notifications (Topic Exchange)
│
├── binding: notification.#
│   ├── notification.push-events
│   └── notification.digest-events

smartportx.audit-events (Topic Exchange)
│
└── binding: audit.#
    └── integration.audit-trail
```

### 3.4 Dead Letter Handling

Every queue has a corresponding dead letter queue (DLQ) for messages that fail processing after maximum retries.

```
Primary Queue ──(max retries: 3)──▶ Retry Queue ──(final failure)──▶ DLQ
   yard.container-moved              yard.container-moved.retry      yard.container-moved.dlq
```

**Configuration per queue:**

```
x-dead-letter-exchange: smartportx.domain-events
x-dead-letter-routing-key: {queue-name}.dlq
x-message-ttl: 60000 (retry delay in ms)
x-max-delivery-count: 3
```

**DLQ Monitoring:**

- Alert when any DLQ message count > 0
- DLQ messages retained for 72 hours for manual inspection
- Admin API to replay messages from DLQ to primary queue
- Dashboard shows DLQ depth per service in real time

### 3.5 Message Serialization

All messages use JSON serialization wrapped in a CloudEvents v1.0 envelope for standardized metadata.

**CloudEvents Envelope:**

```json
{
  "specversion": "1.0",
  "id": "evt-8a3f2b1c-9d4e-4f5a-b6c7-d8e9f0a1b2c3",
  "source": "/smartportx/yard-service",
  "type": "com.smartportx.yard.container-moved",
  "datacontenttype": "application/json",
  "time": "2026-08-26T14:30:00.000Z",
  "subject": "container-CMAU1234567",
  "correlationid": "req-abc123-def456",
  "data": {
    "containerNumber": "CMAU1234567",
    "fromPosition": { "block": "A", "row": 3, "tier": 2 },
    "toPosition": { "block": "B", "row": 7, "tier": 1 },
    "movedBy": "user-0042",
    "movedAt": "2026-08-26T14:29:58.000Z",
    "equipmentId": "RTG-003"
  }
}
```

**Required CloudEvents Attributes:**

| Attribute        | Description                                      | Example                              |
|------------------|--------------------------------------------------|--------------------------------------|
| `specversion`    | CloudEvents spec version                         | `1.0`                                |
| `id`             | Unique event ID (UUID)                           | `evt-8a3f2b1c-...`                  |
| `source`         | Originating service path                         | `/smartportx/yard-service`           |
| `type`           | Event type (reverse-domain notation)             | `com.smartportx.yard.container-moved`|
| `datacontenttype`| Content type of data payload                     | `application/json`                   |
| `time`           | Event timestamp (ISO 8601)                       | `2026-08-26T14:30:00.000Z`          |
| `subject`        | Primary entity reference                         | `container-CMAU1234567`              |
| `correlationid`  | Request trace ID for cross-service correlation   | `req-abc123-def456`                  |
| `data`           | Event-specific payload (JSON object)             | varies                               |

### 3.6 Publisher Configuration

Each service publishes its own domain events. Publishing is done within the same transactional context as the state change.

**Guaranteed Publishing Pattern:**

```java
// Within a @Transactional method
@Transactional
public ContainerMovement moveContainer(String containerId, Position from, Position to) {
    ContainerMovement movement = repository.save(new ContainerMovement(...));

    // Outbox pattern: store event in outbox table within same transaction
    outboxRepository.save(new OutboxEvent(
        "com.smartportx.yard.container-moved",
        objectMapper.writeValueAsString(buildCloudEvent(movement)),
        "container." + containerId
    ));

    return movement;
}

// Separate poller publishes outbox events to RabbitMQ
@Scheduled(fixedDelay = 1000)
public void publishOutboxEvents() {
    List<OutboxEvent> events = outboxRepository.findUnpublished();
    for (OutboxEvent event : events) {
        rabbitTemplate.convertAndSend(
            "smartportx.domain-events",
            event.getRoutingKey(),
            event.getPayload(),
            message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }
        );
        event.markPublished();
        outboxRepository.save(event);
    }
}
```

**Publisher guarantees:**

- At-least-once delivery (outbox pattern ensures no event loss)
- Persistent messages (survive broker restart)
- Publisher confirms enabled (`spring.rabbitmq.publisher-confirm-type: correlated`)
- Publisher returns enabled for unroutable messages

### 3.7 Subscriber Configuration

Services declare their queue bindings and consumer configuration declaratively.

**Consumer Configuration:**

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        default-concurrency: 3
        max-concurrency: 10
        prefetch: 10
        acknowledge-mode: manual
        retry:
          enabled: true
          initial-interval: 1000
          multiplier: 2.0
          max-attempts: 3
          max-interval: 10000
        default-requeue-rejected: false
```

**Manual Acknowledgment Pattern:**

```java
@RabbitListener(
    bindings = @QueueBinding(
        value = @Queue(
            value = "yard.container-moved",
            durable = "true",
            arguments = {
                @Argument(name = "x-dead-letter-exchange", value = "smartportx.domain-events"),
                @Argument(name = "x-dead-letter-routing-key", value = "yard.container-moved.dlq"),
                @Argument(name = "x-message-ttl", value = "60000")
            }
        ),
        exchange = @Exchange(value = "smartportx.domain-events", type = "topic"),
        key = "container.moved"
    )
)
public void handleContainerMoved(Message message, Channel channel) {
    try {
        CloudEvent event = objectMapper.readValue(message.getBody(), CloudEvent.class);
        ContainerMovedData data = objectMapper.convertValue(event.getData(), ContainerMovedData.class);

        processContainerMovement(data);

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    } catch (Exception e) {
        channel.basicNack(
            message.getMessageProperties().getDeliveryTag(),
            false,
            shouldRequeue(e)
        );
    }
}
```

### 3.8 Message Ordering

Message ordering is guaranteed **per aggregate ID**, not globally.

- **Aggregate ID**: Container number, Vessel ID, Appointment ID
- **Mechanism**: Publisher sends all events for the same aggregate to the same queue (using aggregate ID as routing key hash)
- **Limitation**: Ordering across different aggregates is not guaranteed and should not be relied upon

```
Routing key: container.moved.CMAU1234567
    → All events for CMAU1234567 are routed to the same partition
    → Events for CMAU1234567 are processed in order
    → Events for CMAU7654321 may be processed in parallel
```

---

## 4. WebSocket Communication

### 4.1 Protocol

SmartPortX uses STOMP (Simple Text Oriented Messaging Protocol) over WebSocket for real-time client updates.

- **Transport**: WebSocket (WSS in production)
- **Protocol**: STOMP 1.2
- **Library**: Spring WebSocket + SockJS fallback
- **Endpoint**: `/ws` (proxied through Gateway)

### 4.2 Gateway WebSocket Proxy

The Gateway routes WebSocket connections to the Notification Service:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: notification-websocket
          uri: lb:ws://smartportx-notification
          predicates:
            - Path=/ws/**
          filters:
            - StripPrefix=0
```

**Connection flow:**

```
Client ──WSS──▶ Gateway ──WS──▶ Notification Service
   │                              │
   ├── CONNECT (STOMP)            ├── Authenticate JWT from connect headers
   ├── SUBSCRIBE /topic/...       ├── Register subscriptions
   ├── SEND /app/...              ├── Process client messages
   └── DISCONNECT                  └── Cleanup
```

### 4.3 WebSocket Topics

| Topic Pattern                          | Event Type              | Subscribers        |
|----------------------------------------|-------------------------|--------------------|
| `/topic/yard-status`                   | Yard zone occupancy     | Terminal Operators |
| `/topic/yard-zone/{zoneId}`            | Specific zone updates   | Yard Managers      |
| `/topic/gate-status`                   | Gate lane status        | Gate Operators     |
| `/topic/gate-lane/{laneId}`            | Specific lane updates   | Gate Operators     |
| `/topic/vessel-status`                 | Vessel operations       | Vessel Coordinators|
| `/topic/vessel/{vesselId}`             | Specific vessel updates | Vessel Coordinators|
| `/topic/containers/{containerNumber}`  | Container tracking      | All roles          |
| `/topic/appointments/{appointmentId}`  | Appointment status      | Gate Operators     |
| `/topic/alerts`                        | System alerts           | All roles          |
| `/topic/alerts/{severity}`             | Severity-filtered alerts| All roles          |
| `/topic/customs/{containerNumber}`     | Customs clearance       | Terminal Operators |
| `/topic/dashboard/summary`             | Dashboard KPI updates   | All roles          |

### 4.4 Message Format

All WebSocket messages use JSON:

```json
{
  "type": "CONTAINER_MOVED",
  "timestamp": "2026-08-26T14:30:00.000Z",
  "data": {
    "containerNumber": "CMAU1234567",
    "fromPosition": "A-03-02",
    "toPosition": "B-07-01",
    "equipmentId": "RTG-003"
  },
  "metadata": {
    "correlationId": "req-abc123",
    "userId": "user-0042"
  }
}
```

### 4.5 Heartbeat Configuration

```yaml
spring:
  websocket:
    heartbeat:
      interval: 10000  # 10 seconds
      timeout: 30000   # 3x interval before considering dead
```

**Behavior:**

- Server sends STOMP HEARTBEAT frames every 10 seconds
- Client must respond within 30 seconds (3 missed heartbeats = disconnect)
- Connection is closed gracefully on heartbeat timeout
- Client receives DISCONNECT frame with reason `HEARTBEAT_TIMEOUT`

### 4.6 Reconnection Strategy

Client-side automatic reconnection with exponential backoff:

```
Attempt 1:  immediately
Attempt 2:  +1 second
Attempt 3:  +2 seconds
Attempt 4:  +4 seconds
Attempt 5:  +8 seconds
Attempt 6:  +16 seconds
Attempt 7+: +30 seconds (capped)
```

**Reconnection behavior:**

- Client preserves subscription state and re-subscribes after reconnection
- Last received event timestamp is tracked; client can request missed events via REST
- After 10 failed reconnection attempts, client shows "Connection lost" UI
- Client-side jitter added to backoff (±20%) to prevent thundering herd

### 4.7 Authentication

WebSocket connections authenticate via STOMP connect headers:

```javascript
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);

stompClient.connect(
  { Authorization: 'Bearer ' + jwtToken },
  (frame) => {
    // Connected
    stompClient.subscribe('/topic/yard-status', (message) => { ... });
  },
  (error) => {
    // Reconnect logic
  }
);
```

**Server-side validation:**

1. Gateway extracts JWT from `Authorization` header during WebSocket upgrade
2. Gateway validates JWT and forwards to Notification Service with validated headers
3. Notification Service verifies user has permission for requested subscriptions
4. Subscription-level authorization checked on every SUBSCRIBE frame

---

## 5. Service Discovery

### 5.1 Eureka Server

SmartPortX uses Netflix Eureka for service registration and discovery.

```yaml
# Eureka Server Configuration
eureka:
  server:
    enable-self-preservation: true
    renewal-percent-threshold: 0.85
    eviction-interval-timer-in-ms: 60000
    peer-node-read-timeout-ms: 3000
  client:
    register-with-eureka: false
    fetch-registry: false
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

### 5.2 Service Registration

Every service registers with Eureka on startup:

```yaml
# Service Registration Configuration (applied to all services)
eureka:
  instance:
    instance-id: ${spring.application.name}:${random.value}
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 30
    lease-expiration-duration-in-seconds: 90
    metadata-map:
      version: ${app.version}
      environment: ${spring.profiles.active}
      service-type: microservice
  client:
    registry-fetch-interval: 30
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
```

**Registration process:**

1. Service starts and registers with Eureka using instance ID `{service-name}:{uuid}`
2. Service sends heartbeat every 30 seconds
3. Eureka marks service as UP
4. Gateway queries Eureka registry to resolve service locations
5. On shutdown, service deregisters gracefully (if possible)

### 5.3 Health Check Interval

| Parameter                       | Value     |
|---------------------------------|-----------|
| Heartbeat interval              | 30 seconds|
| Lease renewal interval          | 30 seconds|
| Lease expiration duration       | 90 seconds|
| Registry fetch interval         | 30 seconds|
| Eviction interval               | 60 seconds|
| Self-preservation threshold     | 85%       |

**Self-preservation mode:**

When Eureka detects that more than 15% of instances have failed to renew (below 85% threshold), it enters self-preservation mode. In this mode, Eureka stops evicting instances to protect against network partitions. This means potentially unavailable instances remain in the registry.

**Service health endpoint:**

All services expose a `/actuator/health` endpoint used by Eureka for health checks. The health check includes:

- Database connectivity
- RabbitMQ connectivity
- Disk space
- Custom health indicators per service

---

## 6. Circuit Breaker

### 6.1 Resilience4j Configuration

All service-to-service REST calls are protected by Resilience4j circuit breakers.

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        # Sliding window configuration
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5

        # Failure thresholds
        failure-rate-threshold: 50
        slow-call-rate-threshold: 60
        slow-call-duration-threshold: 5s

        # State transitions
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3

        # Recording
        record-exceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.client.HttpServerErrorException
        ignore-exceptions:
          - com.smartportx.common.exception.BusinessRuleException

        # Event listeners
        event-consumer-buffer-size: 100

    instances:
      container-service:
        base-config: default
        failure-rate-threshold: 40
        wait-duration-in-open-state: 15s

      yard-service:
        base-config: default

      identity-service:
        base-config: default
        # More lenient for auth — don't open circuit on transient failures
        failure-rate-threshold: 70
        wait-duration-in-open-state: 5s

      gateway-outbound:
        base-config: default
        # Strict threshold for external-facing calls
        failure-rate-threshold: 30
        wait-duration-in-open-state: 30s
```

### 6.2 Circuit Breaker States

```
         ┌───────────────────────────────┐
         │         CLOSED                │
         │  (Normal operation)           │
         │  Requests pass through        │
         │  Failure rate monitored       │
         └──────────┬────────────────────┘
                    │
                    │ Failure rate > 50%
                    │ OR Slow call rate > 60%
                    ▼
         ┌───────────────────────────────┐
         │          OPEN                 │
         │  (All requests rejected)      │
         │  Fast-fail with fallback      │
         │  Wait 10 seconds              │
         └──────────┬────────────────────┘
                    │
                    │ Wait duration elapsed
                    ▼
         ┌───────────────────────────────┐
         │       HALF-OPEN               │
         │  (Probe requests)             │
         │  3 test requests allowed      │
         └───────┬──────────┬────────────┘
                 │          │
                 │ Probe    │ Probe
                 │ fails    │ succeeds
                 ▼          ▼
              OPEN        CLOSED
```

### 6.3 Fallback Strategies

When the circuit is open, services implement graceful degradation:

| Service       | Call Target     | Fallback Behavior                                    |
|---------------|-----------------|------------------------------------------------------|
| Gate          | Container Svc   | Cache last-known container status (5-min TTL)        |
| Gate          | Yard Svc        | Default to yard zone "UNKNOWN", flag for manual check|
| Yard          | Container Svc   | Return cached container metadata                     |
| Vessel        | Container Svc   | Return vessel manifest from local cache              |
| Vessel        | Yard Svc        | Return estimated positions from last schedule        |
| All           | Identity Svc    | Reject request (auth is mandatory) — no fallback     |

### 6.4 Monitoring Circuit Breaker State

Resilience4j exposes metrics via Micrometer:

```
resilience4j_circuitbreaker_state{name="container-service", state="CLOSED"} 1
resilience4j_circuitbreaker_calls_seconds_count{name="container-service", kind="successful"} 1247
resilience4j_circuitbreaker_calls_seconds_count{name="container-service", kind="failed"} 23
resilience4j_circuitbreaker_calls_seconds_count{name="container-service", kind="rejected"} 0
```

**Alerting:**

- Alert on any circuit transitioning to OPEN state
- Alert when half-open state persists for > 2 minutes (flapping)
- Dashboard showing circuit states across all services

---

## 7. API Gateway Routing

### 7.1 Route Configuration

```yaml
spring:
  cloud:
    gateway:
      # No StripPrefix, no Retry default-filter: all controllers mount at
      # /api/v1/... (identity AuthController/UserAdminController; yard/gate/
      # container/vessel/notification @RequestMapping("/api/v1/...")), so the
      # gateway forwards the full path unchanged. Per-route CircuitBreaker
      # with a gateway-local FallbackController (forward:/fallback/{svc} →
      # RFC 9457 problem+json, 503; 504 when the downstream timeout fired).
      # Each route URI is an env placeholder (default lb://…) so tests can
      # redirect a route to a stub upstream. URI env vars:
      #   GATEWAY_URI_SMARTPORTX_IDENTITY            (2 routes)
      #   GATEWAY_URI_SMARTPORTX_{CONTAINER,YARD,GATE,VESSEL,INTEGRATION,NOTIFICATION}
      #   GATEWAY_URI_SMARTPORTX_NOTIFICATION_WS     (lb:ws://… for /ws/**)
      server:
        webflux:
          httpclient:
            connect-timeout: 2000
            response-timeout: 5s
      routes:
        # ── Identity Service (split into public auth + admin user routes) ──
        - id: smartportx-identity-auth
          uri: ${GATEWAY_URI_SMARTPORTX_IDENTITY:lb://smartportx-identity}
          predicates:
            - Path=/api/v1/auth/**
          filters:
            - name: CircuitBreaker
              args:
                name: gateway-identity
                fallbackUri: forward:/fallback/identity

        - id: smartportx-identity-users
          uri: ${GATEWAY_URI_SMARTPORTX_IDENTITY:lb://smartportx-identity}
          predicates:
            - Path=/api/v1/users/**
          filters:
            - name: CircuitBreaker
              args:
                name: gateway-identity
                fallbackUri: forward:/fallback/identity

        # ── Container Service ──
        - id: smartportx-container
          uri: ${GATEWAY_URI_SMARTPORTX_CONTAINER:lb://smartportx-container}
          predicates:
            - Path=/api/v1/containers/**
          filters:
            - name: CircuitBreaker
              args:
                name: gateway-container
                fallbackUri: forward:/fallback/container

        # ── Yard Service ──
        - id: smartportx-yard
          uri: ${GATEWAY_URI_SMARTPORTX_YARD:lb://smartportx-yard}
          predicates:
            - Path=/api/v1/yard/**
          filters:
            - name: CircuitBreaker
              args:
                name: gateway-yard
                fallbackUri: forward:/fallback/yard

        # ── Gate Service (gate + appointment + quota sub-routes) ──
        - id: smartportx-gate
          uri: ${GATEWAY_URI_SMARTPORTX_GATE:lb://smartportx-gate}
          predicates:
            - Path=/api/v1/gate/**,/api/v1/appointments/**,/api/v1/quotas/**
          filters:
            - name: CircuitBreaker
              args:
                name: gateway-gate
                fallbackUri: forward:/fallback/gate

        # ── Vessel Service (vessel + schedule + discharge-plan sub-routes) ──
        - id: smartportx-vessel
          uri: ${GATEWAY_URI_SMARTPORTX_VESSEL:lb://smartportx-vessel}
          predicates:
            - Path=/api/v1/vessels/**,/api/v1/schedules/**,/api/v1/discharge-plans/**
          filters:
            - name: CircuitBreaker
              args:
                name: gateway-vessel
                fallbackUri: forward:/fallback/vessel

        # ── Integration Service ──
        - id: smartportx-integration
          uri: ${GATEWAY_URI_SMARTPORTX_INTEGRATION:lb://smartportx-integration}
          predicates:
            - Path=/api/v1/integration/**
          filters:
            - name: CircuitBreaker
              args:
                name: gateway-integration
                fallbackUri: forward:/fallback/integration

        # ── Notification Service ──
        - id: smartportx-notification
          uri: ${GATEWAY_URI_SMARTPORTX_NOTIFICATION:lb://smartportx-notification}
          predicates:
            - Path=/api/v1/notifications/**
          filters:
            - name: CircuitBreaker
              args:
                name: gateway-notification
                fallbackUri: forward:/fallback/notification

        # ── WebSocket Route (exempt from edge JWT; STOMP handshake authorizes) ──
        - id: smartportx-notification-ws
          uri: ${GATEWAY_URI_SMARTPORTX_NOTIFICATION_WS:lb:ws://smartportx-notification}
          predicates:
            - Path=/ws/**
```

### 7.2 Route Authorization

Routes are protected at the Gateway level based on role. The edge `JwtEdgeFilter`
validates an RS256 JWT (via the identity service's `/api/v1/auth/jwks`, falling back to
the `jwk-set-uri` from config), strips any client-supplied `X-User-Id`/`X-User-Role`, and
injects trusted values from the token's `sub` and first `roles` claim. Matches against
`public-routes` (exact) skip JWT; everything else requires a valid token; then the request
path is matched against `role-routes` for RBAC (403 on role mismatch). WebSocket `/ws/**`
is exempt from this filter — the STOMP CONNECT frame authorizes via JWT.

```yaml
smartportx:
  gateway:
    authorization:
      public-routes:
        - /api/v1/auth/login
        - /api/v1/auth/refresh
        - /api/v1/auth/jwks
        - /actuator/health
        - /actuator/info
      role-routes:
        - path: /api/v1/yard
          roles: [ADMIN, TERMINAL_OP, YARD_MANAGER, READONLY]
        - path: /api/v1/gate
          roles: [ADMIN, TERMINAL_OP, GATE_OPERATOR, READONLY]
        - path: /api/v1/appointments
          roles: [ADMIN, TERMINAL_OP, GATE_OPERATOR, READONLY]
        - path: /api/v1/quotas
          roles: [ADMIN, TERMINAL_OP, GATE_OPERATOR, READONLY]
        - path: /api/v1/containers
          roles: [ADMIN, TERMINAL_OP, YARD_MANAGER, GATE_OPERATOR, READONLY]
        - path: /api/v1/vessels
          roles: [ADMIN, TERMINAL_OP, READONLY]
        - path: /api/v1/schedules
          roles: [ADMIN, TERMINAL_OP, READONLY]
        - path: /api/v1/discharge-plans
          roles: [ADMIN, TERMINAL_OP, READONLY]
        - path: /api/v1/integration
          roles: [ADMIN]
        - path: /api/v1/notifications
          roles: [ADMIN, TERMINAL_OP, YARD_MANAGER, GATE_OPERATOR, READONLY]
        - path: /api/v1/users
          roles: [ADMIN]
```

### 7.3 Gateway Headers

The Gateway adds the following headers to all forwarded requests:

| Header              | Source                    | Description                                    |
|---------------------|---------------------------|------------------------------------------------|
| `X-User-Id`         | JWT `sub` claim           | Authenticated user ID (injected; any client-supplied value is stripped) |
| `X-User-Role`       | JWT first `roles` claim   | User's assigned role (injected; client-supplied value is stripped) |
| `X-Correlation-Id`  | Generated or forwarded    | Request trace ID, echoed on the response header |

Notes:
- `X-Forwarded-For` is appended automatically by Spring Cloud Gateway (reactor
  Netty). It is **never trusted** for identity or rate-limiting decisions: the rate
  limiter keys on `routeId|user:<X-User-Id>` when an authenticated user is present,
  otherwise on `routeId|ip:<client-address>` from the inbound connection — never on
  a forwarded header value.
- `X-Request-Timestamp` and `X-API-Key` are **not implemented** in the current gateway;
  these rows were removed from the contract. There is no API-key authentication at the
  edge (authentication is JWT-based via `public-routes` + `role-routes`).

---

## 8. Inter-Service Dependency Matrix

### 8.1 Dependency Diagram

```
                    ┌──────────────────────────────────┐
                    │          API Gateway              │
                    │   (Routes to all services)        │
                    └──┬──┬──┬──┬──┬──┬───────────────┘
                       │  │  │  │  │  │
           ┌───────────┘  │  │  │  │  └──────────┐
           ▼              ▼  ▼  ▼  ▼              ▼
      ┌─────────┐  ┌──────┐ ┌────┐ ┌──────┐ ┌─────────┐
      │Identity │  │ Yard │ │Gate│ │Vessel│ │Integration│
      │         │  │      │ │    │ │      │ │          │
      └────▲────┘  └──┬───┘ └─┬──┘ └──┬───┘ └────┬────┘
           │          │        │        │          │
           │    ┌─────┘   ┌────┘   ┌────┘     ┌───┘
           │    ▼         ▼        ▼          ▼
           │  ┌──────────────────────────────┐
           │  │       Container Service       │
           │  └──────────────────────────────┘
           │
           │  ┌──────────────────────────────┐
           └──┤    Notification Service       │
              └──────────────────────────────┘
```

### 8.2 Detailed Dependencies

| Source Service     | Target Service       | Protocol   | Purpose                                    | Frequency       |
|--------------------|----------------------|------------|--------------------------------------------|-----------------|
| **Gateway**        | Identity             | REST       | JWT validation on startup                  | Once per boot   |
| **Gateway**        | All Services         | REST       | Request routing                            | Per request     |
| **Gateway**        | Notification         | WebSocket  | WS connection proxy                        | Persistent      |
| **Yard**           | Container            | REST       | Container details (type, weight, hazmat)   | Per move        |
| **Yard**           | Container            | Event      | Publish container position changes         | Per move        |
| **Yard**           | Identity             | REST       | JWT validation (gateway-injected claims)   | Per request     |
| **Gate**           | Container            | REST       | Container lookup, status update            | Per gate-in/out |
| **Gate**           | Yard                 | REST       | Position query for container placement     | Per gate-in     |
| **Gate**           | Container            | Event      | Gate entry/exit events                     | Per gate-in/out |
| **Gate**           | Identity             | REST       | JWT validation (gateway-injected claims)   | Per request     |
| **Vessel**         | Container            | REST       | Container manifest, load/discharge status  | Per operation   |
| **Vessel**         | Yard                 | REST       | Yard position availability for stowage     | Per planning    |
| **Vessel**         | Container            | Event      | Vessel arrival/departure events            | Per voyage      |
| **Vessel**         | Identity             | REST       | JWT validation (gateway-injected claims)   | Per request     |
| **Integration**    | Container            | Event      | Subscribe to container state changes       | Streaming       |
| **Integration**    | Yard                 | Event      | Subscribe to yard state changes            | Streaming       |
| **Integration**    | Gate                 | Event      | Subscribe to gate events                   | Streaming       |
| **Integration**    | Identity             | REST       | JWT validation (gateway-injected claims)   | Per request     |
| **Notification**   | All Services         | Event      | Subscribe to all domain events             | Streaming       |
| **Notification**   | Identity             | REST       | JWT validation (WS connections)            | Per connection  |
| **Identity**       | None (leaf)          | —          | No outbound service calls                  | —               |
| **Container**      | None (leaf)          | —          | No outbound service calls                  | —               |

### 8.3 Service Tier Classification

| Tier   | Services                           | Justification                          |
|--------|------------------------------------|----------------------------------------|
| Tier 1 | Gateway, Identity                  | Failure blocks all access              |
| Tier 2 | Container, Yard                    | Core data — most services depend on them|
| Tier 3 | Gate, Vessel, Notification         | Operational — degraded mode acceptable  |
| Tier 4 | Integration                        | External sync — can be delayed          |

---

## 9. Message Schema Standards

### 9.1 Event Types Catalog

| Event Type                          | Publisher     | Subscribers                          |
|-------------------------------------|---------------|--------------------------------------|
| `container.created`                 | Container     | Notification, Integration            |
| `container.moved`                   | Yard          | Container, Gate, Vessel, Integration, Notification |
| `container.status-changed`          | Container     | Yard, Gate, Vessel, Integration, Notification |
| `container.customs-cleared`         | Integration   | Container, Yard, Gate, Notification  |
| `yard.zone-updated`                 | Yard          | Container, Gate, Vessel, Notification|
| `yard.position-assigned`            | Yard          | Container, Notification              |
| `gate.entry-processed`              | Gate          | Container, Yard, Integration, Notification |
| `gate.exit-processed`               | Gate          | Container, Yard, Integration, Notification |
| `gate.appointment-created`          | Gate          | Yard, Notification                   |
| `gate.appointment-updated`          | Gate          | Yard, Notification                   |
| `vessel.arrival-registered`         | Vessel        | Yard, Container, Integration, Notification |
| `vessel.departure-registered`       | Vessel        | Yard, Container, Integration, Notification |
| `vessel.stowage-plan-received`      | Vessel        | Yard, Container, Notification        |
| `customs.declaration-received`      | Integration   | Container, Notification              |
| `customs.clearance-approved`        | Integration   | Container, Yard, Notification        |
| `notification.push-sent`            | Notification  | — (terminal event)                   |

### 9.2 Event Payload Examples

**container.moved:**

```json
{
  "containerNumber": "CMAU1234567",
  "isoCode": "42G1",
  "fromPosition": {
    "zone": "A",
    "block": "03",
    "row": 05,
    "tier": 2
  },
  "toPosition": {
    "zone": "B",
    "block": "07",
    "row": 12,
    "tier": 1
  },
  "equipmentId": "RTG-003",
  "operatorId": "user-0042",
  "movedAt": "2026-08-26T14:29:58.000Z",
  "durationSeconds": 145
}
```

**gate.entry-processed:**

```json
{
  "gateEntryId": "GE-2026-08-00421",
  "containerNumber": "CMAU1234567",
  "truckLicensePlate": "B-12345-YYZ",
  "driverName": "John Smith",
  "gateLane": "IN-LANE-02",
  "appointmentId": "APT-2026-08-00089",
  "entryTime": "2026-08-26T14:15:00.000Z",
  "inspectionResult": "PASSED",
  "sealNumber": "ML1234567",
  "destinationZone": "B"
}
```

---

## 10. Observability & Tracing

### 10.1 Correlation IDs

Every request receives a correlation ID that propagates through all synchronous and asynchronous calls:

- **Generated at**: Gateway (if not provided by client)
- **Propagated via**: `X-Correlation-Id` header (sync), CloudEvents `correlationid` field (async)
- **Format**: `req-{8-char-hex}-{8-char-hex}` (e.g., `req-abc123de-f4567890`)
- **Logged at**: Every service hop, included in all structured logs

### 10.2 Distributed Tracing

```
Gateway ──▶ Yard ──▶ Container    (REST chain)
  │          │
  │          ├──▶ RabbitMQ ──▶ Notification  (async)
  │          │                  │
  │          │                  └──▶ WebSocket ──▶ Client
  │
  └──▶ Trace spans via Micrometer + OpenTelemetry
```

Each service produces trace spans that are collected and correlated:

```
Trace ID: abcdef1234567890abcdef1234567890
├── Span: Gateway.route (12ms)
├── Span: Yard.processMove (45ms)
│   ├── Span: Container REST call (18ms)
│   └── Span: RabbitMQ publish (3ms)
├── Span: Container.processMovement (12ms)
└── Span: Notification.sendUpdate (8ms)
    └── Span: WebSocket broadcast (2ms)
```

### 10.3 Metrics

Key communication metrics collected per service:

| Metric                                        | Type    | Tags                     |
|-----------------------------------------------|---------|--------------------------|
| `http.server.requests`                        | Timer   | method, uri, status      |
| `http.client.requests`                        | Timer   | service, method, status  |
| `rabbitmq.messages.published`                 | Counter | exchange, routing-key    |
| `rabbitmq.messages.consumed`                  | Counter | queue                    |
| `rabbitmq.messages.failed`                    | Counter | queue                    |
| `resilience4j_circuitbreaker_state`           | Gauge   | name, state              |
| `resilience4j_circuitbreaker_calls`           | Counter | name, kind               |
| `resilience4j_retry_calls`                    | Counter | name, kind               |
| `websocket.connections.active`                | Gauge   | —                        |
| `websocket.messages.sent`                     | Counter | topic                    |
| `websocket.messages.received`                 | Counter | topic                    |

---

## 11. Configuration Reference

### 11.1 Environment Variables

| Variable                          | Default                           | Description                    |
|-----------------------------------|-----------------------------------|--------------------------------|
| `SPRING_RABBITMQ_HOST`           | `rabbitmq`                        | RabbitMQ host                  |
| `SPRING_RABBITMQ_PORT`           | `5672`                            | RabbitMQ AMQP port             |
| `SPRING_RABBITMQ_USERNAME`       | `smartportx`                      | RabbitMQ username              |
| `SPRING_RABBITMQ_PASSWORD`       | (from secrets)                    | RabbitMQ password              |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://eureka-server:8761/eureka/` | Eureka server URL         |
| `SPRING_CLOUD_GATEWAY_ROUTES`    | (YAML config)                     | Gateway route definitions      |
| `CIRCUIT_BREAKER_FAILURE_THRESHOLD` | `50`                            | Circuit breaker threshold      |
| `WS_HEARTBEAT_INTERVAL`          | `10000`                           | WebSocket heartbeat (ms)       |

### 11.2 Docker Network Configuration

All services communicate over a shared Docker bridge network:

```yaml
# docker-compose.yml (networking section)
networks:
  smartportx-internal:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16

services:
  smartportx-gateway:
    networks:
      - smartportx-internal
      - smartportx-external  # Only gateway is publicly accessible
  smartportx-identity:
    networks:
      - smartportx-internal
  smartportx-yard:
    networks:
      - smartportx-internal
  # ... all other services on smartportx-internal only
```

### 11.3 Connection Pool Settings

| Service      | Connection Pool Size | Idle Timeout | Max Lifetime  |
|--------------|----------------------|--------------|---------------|
| RabbitMQ     | Min: 2, Max: 10     | 60s          | 300s          |
| REST Client  | Min: 5, Max: 50     | 60s          | 600s          |
| WebSocket    | Max: 1000 concurrent | 30s heartbeat| Session-based |

---

*End of Communication Architecture Document*
