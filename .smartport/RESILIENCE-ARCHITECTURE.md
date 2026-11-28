# SmartPortX — Resilience Architecture

> **Version:** 1.0  
> **Date:** 2026-08-26  
> **Status:** Active  
> **Stack:** Java 25 · Spring Boot 3.x · Resilience4j · RabbitMQ · PostgreSQL · Redis

---

## Table of Contents

1. [Overview](#1-overview)
2. [Service Mesh Topology](#2-service-mesh-topology)
3. [Circuit Breaker Pattern](#3-circuit-breaker-pattern)
4. [Retry Pattern](#4-retry-pattern)
5. [Timeouts](#5-timeouts)
6. [Bulkhead Pattern](#6-bulkhead-pattern)
7. [Graceful Degradation](#7-graceful-degradation)
8. [Health Checks](#8-health-checks)
9. [Idempotency](#9-idempotency)
10. [Rate Limiting](#10-rate-limiting)
11. [Disaster Recovery](#11-disaster-recovery)
12. [Resilience Decision Matrix](#12-resilience-decision-matrix)
13. [Configuration Reference](#13-configuration-reference)
14. [Runbook — Failure Scenarios](#14-runbook--failure-scenarios)

---

## 1. Overview

SmartPortX is a Service-Oriented Architecture (SOA) container port logistics platform
composed of **8 microservices**. Each service handles a bounded context within port
operations — from vessel scheduling and container tracking to yard optimization and
gate access control.

Resilience is not optional in this domain. A container port operates 24/7; a failed
optimization must not block gate operations, and a down notification service must not
lose events. Every resilience decision in this document follows one principle:

> **The platform degrades gracefully under failure, never catastrophically.**

All resilience patterns are implemented via **Resilience4j**, chosen over Hystrix
(maintenance mode) and Sentrix (Spring Cloud coupling) for its lightweight, framework-
agnostic design and first-class Spring Boot 3.x integration.

### Design Principles

| # | Principle | Rationale |
|---|-----------|-----------|
| 1 | Fail fast, recover fast | Circuit breakers prevent cascading failures within seconds |
| 2 | Never lose data | Messages queued, idempotency keys persisted, WAL archived |
| 3 | Bounded blast radius | Bulkheads isolate failures to one dependency |
| 4 | Observable failures | Every degradation emits metrics, logs, and traces |
| 5 | Human override | Manual fallback available when automation fails |

---

## 2. Service Mesh Topology

```
                          ┌──────────────────┐
                          │   API Gateway     │
                          │  (Spring Cloud)   │
                          └────────┬─────────┘
                                   │
          ┌────────────┬───────────┼───────────┬────────────┐
          │            │           │           │            │
    ┌─────▼────┐ ┌─────▼────┐ ┌───▼─────┐ ┌───▼─────┐ ┌───▼──────┐
    │  Yard    │ │  Gate    │ │Vessel   │ │Container│ │Notification│
    │ Service  │ │ Service  │ │Service  │ │Service  │ │  Service   │
    └─────┬────┘ └─────┬────┘ └───┬─────┘ └───┬─────┘ └───┬──────┘
          │            │          │            │            │
          │     ┌──────┘     ┌────┘            │     ┌──────┘
          ▼     ▼            ▼                 ▼     ▼
    ┌──────────────────────────────────────────────────────┐
    │              Integration Service                     │
    │         (EDI / EDIFACT / External APIs)              │
    └──────────────────────────────────────────────────────┘
                                   │
                          ┌────────▼─────────┐
                          │ Optimization Svc  │
                          │  (CPLEX / OR)     │
                          └──────────────────┘

    ─── Identity Service (cross-cutting, called by all) ───
    ─── RabbitMQ (async messaging between all services) ───
    ─── PostgreSQL / Redis / Caffeine (data + cache) ───
```

### Inter-Service Dependency Map

| Source | Target | Protocol | Resilience Concern |
|--------|--------|----------|--------------------|
| Gateway | All services | REST | Timeout, Rate Limit |
| Yard | Container | REST | Circuit Breaker, Cache Fallback |
| Gate | Yard | REST | Circuit Breaker, Placeholder |
| Vessel | Container | REST | Circuit Breaker, Stale Data |
| All services | Identity | REST | Circuit Breaker, Reject on Failure |
| All services | RabbitMQ | AMQP | Timeout, Prefetch, Persistent Queues |
| All services | PostgreSQL | JDBC | Timeout, Connection Pool |
| All services | Redis/Caffeine | Cache | Fallback on DB Failure |

---

## 3. Circuit Breaker Pattern

### 3.1 Library and Integration

**Resilience4j CircuitBreaker** wraps every outbound REST call between microservices.
The Spring Boot starter `resilience4j-spring-boot3` auto-configures circuit breakers
from `application.yml` properties.

```java
@CircuitBreaker(name = "containerService", fallbackMethod = "getCachedContainers")
public List<ContainerDTO> getContainersByYard(String yardId) {
    return restTemplate.exchange(
        "/api/v1/containers?yardId={yardId}",
        HttpMethod.GET, null,
        new ParameterizedTypeReference<List<ContainerDTO>>() {},
        yardId
    ).getBody();
}
```

### 3.2 Configuration

All services share a **base configuration** with per-service overrides:

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10
        failureRateThreshold: 50
        slowCallRateThreshold: 60
        slowCallDurationThreshold: 5s
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        registerHealthIndicator: true
        slidingWindowType: COUNT_BASED
        minimumNumberOfCalls: 5
    instances:
      containerService:
        baseConfig: default
      yardService:
        baseConfig: default
      gateService:
        baseConfig: default
      vesselService:
        baseConfig: default
      identityService:
        failureRateThreshold: 40
        waitDurationInOpenState: 15s
      optimizationService:
        slowCallDurationThreshold: 10s
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 1
```

### 3.3 State Machine

```
    CLOSED (normal)
         │
         │ failureRate ≥ 50% OR slowCallRate ≥ 60%
         ▼
      OPEN (10s wait)
         │
         │ waitDurationInOpenState elapsed
         ▼
   HALF-OPEN (3 probe calls)
         │
         ├── probe failures ≥ threshold → OPEN
         └── probe success → CLOSED
```

### 3.4 Per-Service Fallback Behavior

| Service Calling | Service Called | Fallback Behavior |
|----------------|----------------|-------------------|
| **Yard** | Container | Return cached container data from Redis (last known state). Cache TTL: 5 minutes. Log warning. |
| **Gate** | Yard | Return `"position TBD"` placeholder with `status: PENDING`. Gate proceeds with manual assignment. |
| **Vessel** | Container | Return last known container status from local cache. Mark response as `stale: true`. |
| **All services** | Identity | **Reject request** with HTTP 401 + RFC 9457 Problem Details. No auth = no access. No fallback for security. |
| **All services** | Optimization | Return HTTP 503 with `Retry-After: 30` header. Core operations continue. |
| **Notification** | Any | Queue event in RabbitMQ dead-letter exchange for retry when target recovers. |

### 3.5 Circuit Breaker Metrics

Each circuit breaker state transition emits a Micrometer event:

```java
CircuitBreaker.ofDefaults("containerService")
    .getEventPublisher()
    .onStateTransition(event ->
        meterRegistry.counter("smartportx_circuitbreaker_transitions",
            "name", event.getName(),
            "from", event.getStateTransition().getFromState().name(),
            "to", event.getStateTransition().getToState().name()
        ).increment()
    );
```

---

## 4. Retry Pattern

### 4.1 When to Retry

Retry is applied **only to idempotent operations**: `GET`, `PUT`, `DELETE`. These
operations can safely be repeated without side effects.

| HTTP Method | Idempotent | Retry Applied | Notes |
|-------------|------------|---------------|-------|
| GET | Yes | Yes | Read-only, safe to retry |
| PUT | Yes | Yes | Full resource replacement |
| DELETE | Yes | Yes | Idempotent by HTTP spec |
| POST | No | No | Unless explicit `Idempotency-Key` header present |
| PATCH | Depends | No | Partial updates may compound; not retried |

### 4.2 Configuration

```yaml
resilience4j:
  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.client.ResourceAccessException
        ignoreExceptions:
          - com.smartportx.shared.exception.BusinessException
          - com.smartportx.shared.exception.EntityNotFoundException
      aggressive:
        maxAttempts: 5
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
    instances:
      containerService:
        baseConfig: default
      yardService:
        baseConfig: default
      gateService:
        baseConfig: default
      vesselService:
        baseConfig: default
      identityService:
        baseConfig: default
      optimizationService:
        baseConfig: aggressive
```

### 4.3 Retry Timing

With exponential backoff (multiplier = 2):

| Attempt | Delay | Cumulative |
|---------|-------|------------|
| 1st retry | 1s | 1s |
| 2nd retry | 2s | 3s |
| 3rd retry | 4s | 7s |

Total worst-case latency for a retried call: **7 seconds** (within the 3s service
timeout × 3 attempts, but the retry config's wait is on top of timeout expiry).

### 4.4 Non-Retryable Exceptions

`BusinessException` is **never retried** because it represents a deterministic business
rule violation (e.g., "container already checked out", "yard is full"). Retrying a
business rule violation wastes resources and delays the client.

### 4.5 Retry + Circuit Breaker Interaction

The circuit breaker wraps the retry. If all retry attempts fail, the circuit breaker
counts it as a single call failure. This prevents the retry mechanism from artificially
inflating failure rates.

```
Client Request
    │
    ▼
Circuit Breaker (allows/blocks call)
    │
    ▼
Retry (up to 3 attempts)
    │
    ▼
Actual HTTP Call
```

---

## 5. Timeouts

Timeouts prevent threads from being held indefinitely by slow or unresponsive
dependencies. Every outbound call has an explicit timeout.

### 5.1 Timeout Configuration

| Caller | Target | Timeout | Justification |
|--------|--------|---------|---------------|
| API Gateway | Any service (default) | **5s** | User-facing; must respond quickly |
| API Gateway | Optimization endpoint | **30s** | Algorithm execution is compute-heavy |
| Service → Service (REST) | Any dependency | **3s** | Inter-service calls must be fast |
| Service → RabbitMQ | Publish message | **2s** | Publish should not block; queue if needed |
| Service → PostgreSQL | Query execution | **5s** | HikariCP `connectionTimeout` + query timeout |
| Service → Redis | Cache operation | **500ms** | Cache miss is acceptable; slow cache is not |
| WebSocket | Client connection | **None** | Persistent connection; no timeout |

### 5.2 Implementation

**RestTemplate timeout (service-to-service):**

```java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .setConnectTimeout(Duration.ofSeconds(1))
        .setReadTimeout(Duration.ofSeconds(3))
        .build();
}
```

**WebClient timeout (reactive paths):**

```java
@Bean
public WebClient webClient() {
    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(
            HttpClient.create()
                .responseTimeout(Duration.ofSeconds(3))
        ))
        .build();
}
```

**HikariCP connection timeout:**

```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 5000
      maximum-pool-size: 50
      minimum-idle: 10
      idle-timeout: 300000
      max-lifetime: 1200000
```

**RabbitMQ publish timeout:**

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
    publisher-returns: true
    connection-timeout: 2000
```

### 5.3 Timeout Propagation

Timeouts are **not propagated** between services. Each service enforces its own
timeout independently. If Service A calls Service B (3s timeout) which calls Service C
(3s timeout), the total worst-case is **6s**, not 3s. This prevents timeout cascading
while allowing each layer to enforce its own latency budget.

---

## 6. Bulkhead Pattern

### 6.1 Purpose

Bulkheads isolate failures by limiting the number of concurrent calls to each
downstream dependency. If the Container Service is slow, it consumes its bulkhead
slots but does not prevent calls to the Vessel Service.

### 6.2 Configuration

```yaml
resilience4j:
  bulkhead:
    configs:
      default:
        maxConcurrentCalls: 10
        maxWaitDuration: 0s
        writableStackTraces: true
    instances:
      containerService:
        maxConcurrentCalls: 10
        maxWaitDuration: 0s
      yardService:
        maxConcurrentCalls: 10
        maxWaitDuration: 0s
      gateService:
        maxConcurrentCalls: 10
        maxWaitDuration: 0s
      vesselService:
        maxConcurrentCalls: 10
        maxWaitDuration: 0s
      identityService:
        maxConcurrentCalls: 20
        maxWaitDuration: 0s
      optimizationService:
        maxConcurrentCalls: 3
        maxWaitDuration: 5s
```

### 6.3 Gateway Bulkhead Overrides

The API Gateway uses **thread pool isolation** with higher limits since it handles
aggregated traffic:

| Downstream Service | Max Concurrent | Queue Size | Justification |
|-------------------|----------------|------------|---------------|
| Yard | 20 | 0 | High traffic from mobile clients |
| Gate | 20 | 0 | Real-time gate operations |
| Container | 20 | 0 | Frequent lookups |
| Vessel | 10 | 0 | Lower call frequency |
| Optimization | 5 | 0 | Compute-heavy; limit concurrency |
| Identity | 30 | 0 | Every request needs auth; high concurrency |

### 6.4 RabbitMQ Bulkhead

RabbitMQ consumer concurrency is bounded by the **prefetch count**:

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        prefetch: 10
        concurrency: 5
        max-concurrency: 10
```

This ensures a single consumer does not monopolize all messages, providing natural
bulkhead isolation at the messaging layer.

### 6.5 Bulkhead Metrics

```java
Bulkhead.ofDefaults("containerService")
    .getEventPublisher()
    .onCallRejected(event ->
        meterRegistry.counter("smartportx_bulkhead_rejected",
            "name", event.getName()
        ).increment()
    );
```

---

## 7. Graceful Degradation

### 7.1 Degradation Matrix

| Failure | Impact | Degradation Strategy | User-Facing Effect |
|---------|--------|---------------------|-------------------|
| Optimization Service down | No auto-optimization | Core yard ops continue; manual override available | Optimization tab shows "Manual Mode" |
| Notification Service down | No push notifications | Events queued in RabbitMQ; delivered on recovery | Notifications delayed, not lost |
| Integration Service down | No EDIFACT parsing | Messages queued; parsed on recovery | EDI sync delayed |
| Database read failure | Cannot read current state | Read from Redis/Caffeine cache | Data may be stale; warning banner shown |
| Identity Service down | Cannot authenticate | All requests rejected with 401 | Login unavailable |
| Redis down | Cache unavailable | Fall through to database directly | Slightly higher DB load |
| Container Service down | No container data | Yard returns cached container data | "Last known" data shown |

### 7.2 Error Response Format (RFC 9457)

All degraded responses follow the RFC 9457 Problem Details standard:

```json
{
  "type": "https://smartportx.io/errors/service-unavailable",
  "title": "Service Temporarily Unavailable",
  "status": 503,
  "detail": "Container Service is currently unavailable. Showing last known data.",
  "instance": "/api/v1/yards/Y123/containers",
  "service": "yard-service",
  "traceId": "abc-123-def-456",
  "timestamp": "2026-08-26T14:30:00Z"
}
```

### 7.3 Degradation Flags

Each service exposes a `@RefreshScope` configuration bean that can be toggled at
runtime (via Spring Cloud Config or environment variable) to force degradation:

```java
@Component
@ConfigurationProperties(prefix = "smartportx.degradation")
public class DegradationProperties {
    private boolean forceCacheOnly = false;
    private boolean skipNotifications = false;
    private boolean skipOptimization = false;
}
```

### 7.4 Manual Override

For yard operations, supervisors can toggle a **Manual Override** flag in the UI that
bypasses optimization scheduling entirely. This flag is persisted in the database and
does not depend on any service being healthy.

---

## 8. Health Checks

### 8.1 Actuator Endpoints

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  endpoint:
    health:
      show-details: when-authorized
      roles: ACTUATOR_READ
  health:
    db:
      enabled: true
    rabbit:
      enabled: true
    diskspace:
      enabled: true
    ping:
      enabled: true
```

### 8.2 Probe Configuration (Kubernetes)

```yaml
# k8s deployment spec
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 3
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 15
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 2
```

### 8.3 Custom Health Indicators

```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Health health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Health.up()
                .withDetail("database", "postgresql")
                .withDetail("connectionPool", getPoolStats())
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("database", "postgresql")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}

@Component
public class RabbitMQHealthIndicator implements HealthIndicator {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public Health health() {
        try {
            ConnectionFactory factory = rabbitTemplate.getConnectionFactory();
            Connection connection = factory.createConnection();
            connection.close();
            return Health.up()
                .withDetail("rabbitmq", "connected")
                .withDetail("virtualHost", factory.getVirtualHost())
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("rabbitmq", e.getMessage())
                .build();
        }
    }
}

@Component
public class CacheHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;
    private final CacheManager cacheManager;

    @Override
    public Health health() {
        try {
            Connection connection = redisConnectionFactory.getConnection();
            String pong = connection.ping();
            connection.close();
            return Health.up()
                .withDetail("redis", pong)
                .withDetail("caches", getCacheNames())
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("redis", e.getMessage())
                .withDetail("fallback", "caffeine-only")
                .build();
        }
    }
}
```

### 8.4 Readiness vs Liveness

| Probe | Purpose | Fails When |
|-------|---------|-----------|
| **Liveness** | Is the JVM running? | Deadlock, OOM, infinite loop, thread starvation |
| **Readiness** | Can it serve traffic? | DB unreachable, critical dependency down, not yet initialized |

A readiness failure causes the pod to be removed from service endpoints (traffic
stops). A liveness failure causes the pod to be restarted.

---

## 9. Idempotency

### 9.1 Problem

If a client sends the same `POST` request twice (e.g., due to network retry), the
server might create duplicate resources. Idempotency keys prevent this.

### 9.2 Implementation

```
Client sends POST with header:
    Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

Server:
    1. Check Redis for key
    2. If found → return cached response (200 OK with original body)
    3. If not found → process request, store response in Redis with 24h TTL
```

### 9.3 Redis Storage

```
Key:   idempotency:{service}:{key}
Value: { status: 201, headers: {...}, body: {...} }
TTL:   86400 (24 hours)
```

### 9.4 Configuration

```yaml
smartportx:
  idempotency:
    enabled: true
    header-name: Idempotency-Key
    ttl-hours: 24
    max-key-length: 128
    redis-key-prefix: "idempotency:"
```

### 9.5 Service-Level Idempotency

| Service | Endpoint | Idempotent By Default | Idempotency Key Required |
|---------|----------|----------------------|--------------------------|
| Container | POST /containers | No | Optional |
| Yard | POST /moves | No | Optional |
| Gate | POST /transactions | No | Optional (for gate-in/gate-out) |
| Vessel | POST /vessels | No | Optional |
| Appointment | POST /appointments | No | Optional |
| Notification | POST /notifications | No | No (fire-and-forget) |

### 9.6 Concurrency Control

Two requests with the same idempotency key arriving simultaneously:

```java
// Atomic check-and-set using Redis SET NX
String result = redisTemplate.opsForValue()
    .setIfAbsent(key, responseJson, 24, TimeUnit.HOURS);

if (result == null) {
    // Another request won the race — return cached response
    return redisTemplate.opsForValue().get(key);
}
```

---

## 10. Rate Limiting

### 10.1 Gateway-Level Rate Limiting

Resilience4j RateLimiter enforces per-API-key limits at the gateway:

```yaml
resilience4j:
  ratelimiter:
    configs:
      default:
        limitForPeriod: 100
        limitRefreshPeriod: 1m
        timeoutDuration: 0s
        registerHealthIndicator: true
    instances:
      publicApi:
        limitForPeriod: 100
        limitRefreshPeriod: 1m
        timeoutDuration: 0s
      internalApi:
        limitForPeriod: 1000
        limitRefreshPeriod: 1m
        timeoutDuration: 0s
      mobileApi:
        limitForPeriod: 200
        limitRefreshPeriod: 1m
        timeoutDuration: 0s
```

### 10.2 Per-Service Rate Limiting

Internal service endpoints use configurable rate limits:

```yaml
smartportx:
  rate-limit:
    enabled: true
    default-limit: 500
    default-period: 1m
    endpoints:
      /api/v1/containers/search:
        limit: 50
        period: 1m
      /api/v1/optimization/run:
        limit: 5
        period: 1m
      /api/v1/reports/generate:
        limit: 10
        period: 1m
```

### 10.3 Rate Limit Response

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 30
Content-Type: application/problem+json

{
  "type": "https://smartportx.io/errors/rate-limit-exceeded",
  "title": "Rate Limit Exceeded",
  "status": 429,
  "detail": "API key rate limit of 100 requests/minute exceeded. Retry after 30 seconds.",
  "instance": "/api/v1/containers",
  "retryAfter": 30
}
```

### 10.4 RabbitMQ Prefetch as Rate Limiting

RabbitMQ prefetch count acts as a natural rate limiter on message processing:

| Consumer | Prefetch | Concurrency | Effective Rate |
|----------|----------|-------------|----------------|
| Yard event consumer | 10 | 5 | ~50 msgs/processing-cycle |
| Notification consumer | 10 | 3 | ~30 msgs/processing-cycle |
| EDIFACT consumer | 5 | 2 | ~10 msgs/processing-cycle |

---

## 11. Disaster Recovery

### 11.1 Objectives

| Metric | Target | Strategy |
|--------|--------|----------|
| **RTO** (Recovery Time Objective) | < 30 minutes | Automated failover, environment variables in Git |
| **RPO** (Recovery Point Objective) | < 5 minutes | WAL archiving, persistent RabbitMQ queues |

### 11.2 Database Recovery

**PostgreSQL WAL Archiving:**

```ini
# postgresql.conf
wal_level = replica
archive_mode = on
archive_command = 'cp %p /archive/%f'
max_wal_senders = 3
wal_keep_size = 1GB
```

**Daily pg_dump:**

```bash
#!/bin/bash
# backup.sh — cron: 0 2 * * *
DATE=$(date +%Y%m%d_%H%M%S)
pg_dump -h $DB_HOST -U $DB_USER -d smartportx \
  | gzip > /backups/smartportx_${DATE}.sql.gz

# Retain 30 days
find /backups -name "*.sql.gz" -mtime +30 -delete
```

**Point-in-time recovery:**

```bash
# Restore to specific timestamp
pg_basebackup -D /restore -XLOG_STREAM -t "2026-08-26 14:00:00"
```

### 11.3 RabbitMQ Recovery

- **Queues:** declared as `durable: true` — survive broker restart
- **Messages:** published with `deliveryMode: 2` (persistent)
- **Exchange:** durable, with dead-letter exchange for failed messages
- **Cluster:** mirrored queues across 3 nodes (quorum queues in RabbitMQ 3.8+)

```java
@Bean
public Queue orderQueue() {
    return QueueBuilder.durable("yard-events")
        .withArgument("x-dead-letter-exchange", "dlx")
        .withArgument("x-dead-letter-routing-key", "yard-events.dlq")
        .withArgument("x-message-ttl", 86400000) // 24h
        .build();
}
```

### 11.4 Configuration Recovery

All configuration is stored in:
1. **Git repository** — version controlled, auditable
2. **Spring Cloud Config** — runtime refreshable
3. **Environment variables** — override for secrets and deployment-specific values

Recovery procedure:
```bash
# 1. Clone config repository
git clone https://github.com/smartportx/config.git /config

# 2. Set environment variables
export SPRING_CONFIG_IMPORT=optional:configserver:http://config-server:8888

# 3. Services auto-discover configuration on startup
```

### 11.5 Full Recovery Runbook

```
1. DETECT    — Grafana alert fires, on-call receives notification
2. ASSESS    — Check service health dashboards, identify affected services
3. ISOLATE   — If cascading failure, disable affected circuit breakers
4. RECOVER   — Restart failed services, verify health checks pass
5. VERIFY    — Confirm metrics return to normal baselines
6. POST-MORTEM — Document incident, update runbook if needed
```

---

## 12. Resilience Decision Matrix

| Failure Scenario | Circuit Breaker | Retry | Bulkhead | Timeout | Fallback |
|-----------------|----------------|-------|----------|---------|----------|
| Container Service slow | Yes | Yes | Yes (10) | 3s | Cached data |
| Yard Service down | Yes | Yes | Yes (10) | 3s | Placeholder |
| Identity Service down | Yes | No | Yes (20) | 3s | Reject (401) |
| Optimization slow | Yes | Yes | Yes (3) | 30s | 503 + retry |
| RabbitMQ unavailable | No | Yes | Prefetch | 2s | Dead-letter queue |
| Database timeout | No | No | HikariCP | 5s | Cache fallback |
| Redis down | No | No | N/A | 500ms | Caffeine fallback |

---

## 13. Configuration Reference

### 13.1 Common Resilience4j Properties

```yaml
# All services share this base configuration
resilience4j:
  timelimiter:
    configs:
      default:
        timeoutDuration: 3s
        cancelRunningFuture: true
  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10
        failureRateThreshold: 50
        slowCallRateThreshold: 60
        slowCallDurationThreshold: 5s
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
  bulkhead:
    configs:
      default:
        maxConcurrentCalls: 10
        maxWaitDuration: 0s
  ratelimiter:
    configs:
      default:
        limitForPeriod: 100
        limitRefreshPeriod: 1m
        timeoutDuration: 0s
```

### 13.2 Per-Service Overrides

| Service | Circuit Breaker | Retry | Bulkhead | Timeout |
|---------|----------------|-------|----------|---------|
| Identity | failThreshold: 40%, wait: 15s | 3 attempts | 20 concurrent | 3s |
| Optimization | slowThreshold: 10s, wait: 30s | 5 attempts | 3 concurrent | 30s |
| Container | default | default | 10 concurrent | 3s |
| Yard | default | default | 10 concurrent | 3s |
| Gate | default | default | 10 concurrent | 3s |
| Vessel | default | default | 10 concurrent | 3s |

### 13.3 Actuator Metrics Exposed

```
resilience4j_circuitbreaker_state
resilience4j_circuitbreaker_failure_rate
resilience4j_circuitbreaker_slow_call_rate
resilience4j_circuitbreaker_open_calls
resilience4j_circuitbreaker_not_permitted_calls
resilience4j_bulkhead_available_concurrent_calls
resilience4j_bulkhead_max_allowed_concurrent_calls
resilience4j_bulkhead_rejected_calls
resilience4j_retry_successful_calls
resilience4j_retry_failed_calls
resilience4j_ratelimiter_available_permissions
resilience4j_ratelimiter_rejected_permissions
resilience4j_timelimiter_successful_calls
resilience4j_timelimiter_failed_calls
```

---

## 14. Runbook — Failure Scenarios

### Scenario 1: Container Service Unavailable

```
SYMPTOMS:
  - Yard Service circuit breaker opens for "containerService"
  - smartportx_circuitbreaker_transitions{to=OPEN} increasing
  - Container lookups return cached data

ACTIONS:
  1. Check Container Service health: curl http://container:8080/actuator/health
  2. Check Container Service logs: docker logs container-service
  3. Check database connectivity from Container Service
  4. If DB is down → restore PostgreSQL from WAL backup
  5. If service is OOM → increase memory limit, restart
  6. Wait for circuit breaker half-open state (10s)
  7. Verify probes succeed → circuit breaker closes automatically

ESCALATION:
  - If not recovered in 15 minutes → page on-call engineer
  - If database corruption suspected → engage DBA
```

### Scenario 2: RabbitMQ Broker Down

```
SYMPTOMS:
  - All consumers disconnected
  - Messages piling up in publishers' outbound buffers
  - RabbitMQ health indicator: DOWN

ACTIONS:
  1. Check RabbitMQ cluster status: rabbitmqctl cluster_status
  2. If node is down → restart RabbitMQ node
  3. If persistent queue data lost → rebuild queues (they auto-declare on startup)
  4. Check dead-letter queue for any lost messages
  5. Verify consumers reconnect: check consumer count metric

PREVENTION:
  - Quorum queues for all critical queues
  - Persistent messages for all business events
  - RabbitMQ cluster across 3 availability zones
```

### Scenario 3: Database Connection Pool Exhausted

```
SYMPTOMS:
  - smartportx_db_connections_active approaching 50 (max pool size)
  - Services reporting "Cannot acquire connection"
  - HikariCP metrics: pending threads increasing

ACTIONS:
  1. Identify slow queries: check PostgreSQL pg_stat_activity
  2. Kill long-running queries: SELECT pg_terminate_backend(pid)
  3. Check for connection leaks: compare acquired vs released counts
  4. If leak suspected → restart affected service
  5. Monitor pool metrics for recovery

PREVENTION:
  - Statement timeout: 5s
  - Connection timeout: 5s
  - Max lifetime: 20 minutes
  - Leak detection threshold: 30 seconds
```

---

*End of Resilience Architecture Document*
