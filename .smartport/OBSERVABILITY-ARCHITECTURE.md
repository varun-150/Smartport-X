# SmartPortX — Observability Architecture

> **Version:** 1.0  
> **Date:** 2026-08-26  
> **Status:** Active  
> **Stack:** Micrometer · Prometheus · Grafana · OpenTelemetry · Jaeger · ELK (future)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Metrics — Micrometer + Prometheus](#2-metrics--micrometer--prometheus)
3. [Distributed Tracing — OpenTelemetry](#3-distributed-tracing--opentelemetry)
4. [Logging — Structured JSON](#4-logging--structured-json)
5. [Alerting — Grafana](#5-alerting--grafana)
6. [Dashboard Views — Grafana](#6-dashboard-views--grafana)
7. [Correlation](#7-correlation)
8. [Health Check Endpoints](#8-health-check-endpoints)
9. [Infrastructure Metrics](#9-infrastructure-metrics)
10. [Observability Pipeline](#10-observability-pipeline)
11. [Cost and Performance](#11-cost-and-performance)
12. [Configuration Reference](#12-configuration-reference)

---

## 1. Overview

SmartPortX is a distributed system of 8 microservices. When a container move fails at
2 AM, the on-call engineer needs to answer three questions in under 5 minutes:

1. **What is happening?** → Metrics show the anomaly
2. **Why is it happening?** → Traces reveal the failing call chain
3. **What happened before?** → Logs provide the context

This document defines the observability stack that answers all three. Every service
in the SmartPortX platform is instrumented with the three pillars of observability:
**metrics**, **traces**, and **logs**.

### Design Principles

| # | Principle | Rationale |
|---|-----------|-----------|
| 1 | Structured everything | JSON logs, labeled metrics, tagged traces — all machine-parseable |
| 2 | Correlation by default | Every request gets a correlation ID that flows through all services |
| 3 | Business + technical | JVM metrics are table stakes; container moves/hour is the real signal |
| 4 | Cost-aware sampling | 100% error traces, 10% success traces — control storage costs |
| 5 | Shift-left observability | Developers add metrics during coding, not after deployment |

---

## 2. Metrics — Micrometer + Prometheus

### 2.1 Architecture

```
  ┌─────────────────────────────────────────────────────────────┐
  │                    Microservice Instance                     │
  │                                                             │
  │  ┌─────────────┐    ┌──────────────┐    ┌──────────────┐  │
  │  │  Business    │    │  Spring Boot │    │ Resilience4j │  │
  │  │  Metrics     │    │  Actuator    │    │ Metrics      │  │
  │  └──────┬──────┘    └──────┬───────┘    └──────┬───────┘  │
  │         │                  │                    │           │
  │         └──────────┬───────┴────────────────────┘           │
  │                    ▼                                        │
  │           Micrometer Registry                               │
  │                    │                                        │
  └────────────────────┼────────────────────────────────────────┘
                       │ /actuator/prometheus (text format)
                       ▼
              ┌────────────────┐
              │   Prometheus   │  (scrape every 15s)
              │    Server      │
              └───────┬────────┘
                      │ PromQL queries
                      ▼
              ┌────────────────┐
              │    Grafana      │  (dashboards + alerts)
              │   Dashboards    │
              └────────────────┘
```

### 2.2 Spring Boot Actuator Prometheus Setup

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    prometheus:
      enabled: true
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      service: ${smartportx.service.name}
      environment: ${SPRING_PROFILES_ACTIVE:local}
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.75, 0.95, 0.99
      sla:
        http.server.requests: 50ms, 100ms, 200ms, 500ms, 1s, 2s, 5s
```

### 2.3 Prometheus Scrape Configuration

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'smartportx-services'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'gateway:8080'
          - 'yard-service:8081'
          - 'gate-service:8082'
          - 'container-service:8083'
          - 'vessel-service:8084'
          - 'notification-service:8085'
          - 'integration-service:8086'
          - 'optimization-service:8087'
          - 'identity-service:8088'
        labels:
          environment: 'production'

  - job_name: 'rabbitmq'
    metrics_path: '/api/metrics'
    static_configs:
      - targets: ['rabbitmq:15692']

  - job_name: 'postgresql'
    static_configs:
      - targets: ['postgres-exporter:9187']
```

### 2.4 JVM Metrics (Auto-Collected)

Micrometer auto-collects these JVM metrics from every Spring Boot service:

| Metric | Type | Description |
|--------|------|-------------|
| `jvm_memory_used_bytes` | Gauge | Heap and non-heap memory usage |
| `jvm_memory_max_bytes` | Gauge | Maximum memory available |
| `jvm_gc_pause_seconds` | Timer | GC pause duration and count |
| `jvm_threads_live_threads` | Gauge | Live thread count |
| `jvm_threads_daemon_threads` | Gauge | Daemon thread count |
| `jvm_classes_loaded_classes` | Gauge | Number of loaded classes |
| `jvm_buffer_memory_used_bytes` | Gauge | Buffer pool memory usage |
| `process_cpu_usage` | Gauge | CPU utilization percentage |
| `process_uptime_seconds` | Gauge | Time since service start |
| `system_cpu_count` | Gauge | Available CPU cores |

### 2.5 HTTP Metrics (Auto-Collected)

| Metric | Type | Description |
|--------|------|-------------|
| `http_server_requests_seconds_count` | Counter | Total HTTP requests |
| `http_server_requests_seconds_sum` | Timer | Total response time |
| `http_server_requests_seconds_max` | Gauge | Max response time |
| `http_server_requests_seconds{quantile}` | Histogram | p50, p75, p95, p99 latencies |

Tags applied automatically:
- `method`: GET, POST, PUT, DELETE
- `uri`: /api/v1/containers, /api/v1/yards, etc.
- `status`: 200, 201, 400, 404, 500, 503
- `outcome`: SUCCESS, CLIENT_ERROR, SERVER_ERROR

### 2.6 Database Metrics (Auto-Collected)

| Metric | Type | Description |
|--------|------|-------------|
| `hikaricp_connections_active` | Gauge | Active connections in pool |
| `hikaricp_connections_idle` | Gauge | Idle connections |
| `hikaricp_connections_pending` | Gauge | Threads waiting for connection |
| `hikaricp_connections_timeout_total` | Counter | Connection timeout count |
| `hikaricp_connections_creation_seconds` | Timer | Connection creation time |
| `hikaricp_connections_usage_seconds` | Timer | Connection usage duration |

### 2.7 RabbitMQ Metrics (Auto-Collected)

| Metric | Type | Description |
|--------|------|-------------|
| `rabbitmq_messages_published_total` | Counter | Messages published |
| `rabbitmq_messages_consumed_total` | Counter | Messages consumed |
| `rabbitmq_messages_acknowledged_total` | Counter | Messages acknowledged |
| `rabbitmq_messages_delivered_total` | Counter | Messages delivered |
| `rabbitmq_queue_depth` | Gauge | Messages in queue |
| `rabbitmq_queue_consumers` | Gauge | Consumer count per queue |
| `rabbitmq_queue_memory_bytes` | Gauge | Queue memory usage |

### 2.8 Custom Business Metrics

These metrics capture the domain-specific health of port operations:

```java
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    // Yard utilization gauge (0.0 to 1.0)
    private final AtomicReference<Double> yardUtilization = new AtomicReference<>(0.0);

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;

        Gauge.builder("smartportx_yard_utilization", yardUtilization, AtomicReference::get)
            .description("Current yard utilization rate")
            .tag("yard", "all")
            .register(registry);
    }

    public void recordContainerMove(String yardId, String containerType) {
        registry.counter("smartportx_container_moves_total",
            "yard", yardId,
            "containerType", containerType
        ).increment();
    }

    public void recordGateTransaction(String gateId, String transactionType) {
        registry.counter("smartportx_gate_transactions_total",
            "gate", gateId,
            "type", transactionType
        ).increment();
    }

    public void recordOptimizationRun(String yardId, boolean success, long durationMs) {
        Timer.builder("smartportx_optimization_duration_seconds")
            .tag("yard", yardId)
            .tag("success", String.valueOf(success))
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
            .record(Duration.ofMillis(durationMs));
    }

    public void recordAppointmentBooked(String serviceType) {
        registry.counter("smartportx_appointments_booked_total",
            "serviceType", serviceType
        ).increment();
    }

    public void recordEdifactMessage(String messageType, boolean success) {
        registry.counter("smartportx_edifact_messages_processed_total",
            "messageType", messageType,
            "success", String.valueOf(success)
        ).increment();
    }

    public void updateYardUtilization(String yardId, double utilization) {
        yardUtilization.set(utilization);
    }
}
```

### 2.9 Custom Business Metrics Reference

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `smartportx_yard_utilization` | Gauge | `yard` | Current yard utilization (0.0–1.0) |
| `smartportx_container_moves_total` | Counter | `yard`, `containerType` | Total container movements |
| `smartportx_gate_transactions_total` | Counter | `gate`, `type` | Total gate transactions (IN/OUT) |
| `smartportx_optimization_duration_seconds` | Timer | `yard`, `success` | Optimization algorithm execution time |
| `smartportx_appointments_booked_total` | Counter | `serviceType` | Total appointments booked |
| `smartportx_edifact_messages_processed_total` | Counter | `messageType`, `success` | Total EDIFACT messages parsed |
| `smartportx_vessel_discharge_progress` | Gauge | `vessel`, `voyage` | Discharge completion (0.0–1.0) |
| `smartportx_container_status_count` | Gauge | `status` | Containers by status ( Laden, MTY, Full, Empty ) |

### 2.10 Resilience4j Metrics

All Resilience4j components automatically register metrics with Micrometer:

```
resilience4j_circuitbreaker_state{name="containerService"}
resilience4j_circuitbreaker_failure_rate{name="containerService"}
resilience4j_circuitbreaker_slow_call_rate{name="containerService"}
resilience4j_circuitbreaker_open_calls{name="containerService"}
resilience4j_bulkhead_available_concurrent_calls{name="containerService"}
resilience4j_bulkhead_rejected_calls{name="containerService"}
resilience4j_retry_successful_calls{name="containerService"}
resilience4j_retry_failed_calls{name="containerService"}
resilience4j_ratelimiter_available_permissions{name="publicApi"}
resilience4j_ratelimiter_rejected_permissions{name="publicApi"}
resilience4j_timelimiter_successful_calls{name="containerService"}
resilience4j_timelimiter_failed_calls{name="containerService"}
```

---

## 3. Distributed Tracing — OpenTelemetry

### 3.1 Architecture

```
  ┌──────────────────────┐
  │  OpenTelemetry Agent  │  (Java agent attached to each service JVM)
  │  - Auto-instrument HTTP clients/servers
  │  - Instrument JDBC, RabbitMQ
  │  - W3C Trace Context propagation
  └───────────┬──────────┘
              │ OTLP (gRPC, port 4317)
              ▼
  ┌──────────────────────┐
  │  OpenTelemetry        │
  │  Collector            │  (receives, processes, exports spans)
  │  - Batch export       │
  │  - Sampling config    │
  │  - Attribute enrich   │
  └───────────┬──────────┘
              │
              ▼
  ┌──────────────────────┐
  │       Jaeger          │  (trace storage + query UI)
  │  - Trace search       │
  │  - Service topology   │
  │  - Span analysis      │
  └──────────────────────┘
```

### 3.2 Java Agent Configuration

```bash
# JVM args for each Spring Boot service
java -javaagent:opentelemetry-javaagent.jar \
  -Dotel.service.name=yard-service \
  -Dotel.service.namespace=smartportx \
  -Dotel.service.version=1.0.0 \
  -Dotel.exporter.otlp.endpoint=http://otel-collector:4317 \
  -Dotel.exporter.otlp.protocol=grpc \
  -Dotel.traces.sampler=parentbased_traceidratio \
  -Dotel.traces.sampler.arg=0.1 \
  -Dotel.instrumentation.spring-web.enabled=true \
  -Dotel.instrumentation.spring-webmvc.enabled=true \
  -Dotel.instrumentation.rabbitmq.enabled=true \
  -Dotel.instrumentation.jdbc.enabled=true \
  -Dotel.metrics.exporter=none \
  -Dotel.logs.exporter=none \
  -jar yard-service.jar
```

### 3.3 Sampling Strategy

| Scenario | Sampling Rate | Justification |
|----------|--------------|---------------|
| Error responses (4xx, 5xx) | **100%** | Every error must be traceable |
| Health check endpoints | **0%** | Too noisy, no value |
| Normal requests (2xx) | **10%** | Sufficient for latency analysis |
| Optimization runs | **100%** | Long-running, high value |
| RabbitMQ publish/consume | **10%** | High volume, but critical path |

### 3.4 OpenTelemetry Collector Configuration

```yaml
# otel-collector-config.yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 5s
    send_batch_size: 1024

  tail_sampling:
    decision_wait: 10s
    policies:
      - name: error-sampling
        type: status_code
        status_code: {status_codes: [ERROR]}
      - name: normal-sampling
        type: probabilistic
        probabilistic: {sampling_percentage: 10}
      - name: optimization-always
        type: string_attribute
        string_attribute: {key: http.target, values: ["/api/v1/optimization/run"]}

  resource:
    attributes:
      - key: deployment.environment
        value: production
        action: upsert

exporters:
  otlp/jaeger:
    endpoint: jaeger:4317
    tls:
      insecure: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [tail_sampling, batch, resource]
      exporters: [otlp/jaeger]
```

### 3.5 Span Naming Convention

All spans follow the pattern `{HTTP_METHOD} {PATH}`:

| Span Name | Service | Description |
|-----------|---------|-------------|
| `GET /api/v1/containers/{id}` | Container | Fetch single container |
| `POST /api/v1/containers` | Container | Create container |
| `GET /api/v1/yards/{id}/utilization` | Yard | Get yard utilization |
| `POST /api/v1/optimization/run` | Optimization | Run optimization algorithm |
| `SELECT containers` | Container | Database query span |
| `publish yard-events` | RabbitMQ | Message publish span |
| `consume yard-events` | Yard | Message consume span |
| `optimization-solver` | Optimization | Algorithm execution span |

### 3.6 Custom Spans

```java
@Service
public class OptimizationService {

    @Autowired
    private io.opentelemetry.api.trace.Tracer tracer;

    @Transactional
    public OptimizationResult runOptimization(String yardId) {
        Span span = tracer.spanBuilder("optimization-solver")
            .setAttribute("yard.id", yardId)
            .setAttribute("service.name", "optimization-service")
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Load yard data (creates child span automatically via JDBC instrumentation)
            YardData data = yardRepository.loadYardData(yardId);

            // Custom span for solver execution
            Span solverSpan = tracer.spanBuilder("constraint-solver")
                .setAttribute("constraints", data.getConstraintCount())
                .setAttribute("containers", data.getContainerCount())
                .startSpan();

            try {
                OptimizationResult result = solver.solve(data);
                solverSpan.setStatus(StatusCode.OK);
                return result;
            } catch (Exception e) {
                solverSpan.setStatus(StatusCode.ERROR, e.getMessage());
                solverSpan.recordException(e);
                throw e;
            } finally {
                solverSpan.end();
            }
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

### 3.7 Trace Context Propagation

W3C Trace Context headers are automatically injected/extracted by the OpenTelemetry
Java agent:

```
# Outbound HTTP call (auto-injected)
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
tracestate: smartportx=yard123

# RabbitMQ message properties (auto-injected)
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
```

### 3.8 Trace-to-Metric Correlation

Traces feed into metrics via span attributes:

```
span attributes → metric labels:
  http.method       → method
  http.target       → uri
  http.status_code  → status
  service.name      → service
  deployment.environment → environment
```

This allows Grafana to jump from a metric spike to the relevant traces.

---

## 4. Logging — Structured JSON

### 4.1 Log Format

All services emit structured JSON logs via Logback + logstash-logback-encoder:

```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>correlationId</includeMdcKeyName>
            <includeMdcKeyName>service</includeMdcKeyName>
            <fieldNames>
                <timestamp>timestamp</timestamp>
                <level>level</level>
                <logger>logger</logger>
                <thread>thread</thread>
                <message>message</message>
            </fieldNames>
            <timeZone>UTC</timeZone>
        </encoder>
    </appender>

    <springProfile name="local">
        <root level="INFO">
            <appender-ref ref="STDOUT" />
        </root>
    </springProfile>

    <springProfile name="production">
        <root level="INFO">
            <appender-ref ref="STDOUT" />
        </root>
    </springProfile>
</configuration>
```

### 4.2 Log Entry Example

```json
{
  "timestamp": "2026-08-26T14:30:15.123Z",
  "level": "INFO",
  "logger": "com.smartportx.gate.service.GateTransactionService",
  "thread": "http-nio-8082-exec-5",
  "message": "Gate transaction processed successfully",
  "service": "gate-service",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "correlationId": "req-abc-123-def-456",
  "userId": "operator-789",
  "gateId": "GATE-03",
  "transactionType": "GATE_OUT",
  "containerNumber": "MSKU7654321",
  "duration": 142,
  "outcome": "SUCCESS"
}
```

### 4.3 Log Levels

| Level | When to Use | Retention |
|-------|-------------|-----------|
| **ERROR** | Exceptions, failures, data corruption | Always logged, never filtered |
| **WARN** | Degraded behavior, retryable failures, near-capacity | Always logged |
| **INFO** | Business events, request/response, lifecycle | Default level |
| **DEBUG** | SQL queries, RabbitMQ payloads, internal state | Only in debug profile |

### 4.4 Logging Configuration Per Profile

| Profile | Level | Output | Use Case |
|---------|-------|--------|----------|
| `local` | DEBUG | Console (colorized) | Development |
| `development` | INFO | Console + File | Testing |
| `staging` | INFO | Console (JSON) | Pre-production |
| `production` | INFO | Console (JSON) | Production |

### 4.5 Key Log Events

**Service Lifecycle:**
```java
log.info("Service started on port {} in {}ms", port, startupTime);
log.info("Service shutting down, draining connections...");
log.info("Service shutdown complete, JVM exiting");
```

**HTTP Request/Response:**
```java
// Filter logs every request at INFO level
log.info("HTTP {} {} -> {} ({}ms)",
    request.getMethod(), request.getRequestURI(),
    response.getStatus(), duration);
```

**Database Queries (DEBUG only):**
```java
log.debug("SQL query executed: {} in {}ms, rows: {}",
    sql, duration, rowCount);
```

**RabbitMQ (DEBUG only):**
```java
log.debug("Message published to exchange={} routingKey={} messageId={}",
    exchange, routingKey, messageId);

log.debug("Message consumed from queue={} messageId={} retryCount={}",
    queue, messageId, retryCount);
```

**Business Events:**
```java
log.info("Container moved: {} from yard={} slot={} to yard={} slot={}",
    containerNumber, fromYard, fromSlot, toYard, toSlot);

log.info("Gate transaction: {} container={} type={}",
    transactionId, containerNumber, transactionType);

log.info("Optimization completed: yard={} duration={}ms containers={} score={}",
    yardId, duration, containerCount, score);
```

**Security Events:**
```java
log.warn("Login failed: username={} reason={} ip={}",
    username, reason, ipAddress);

log.warn("Unauthorized access attempt: userId={} resource={} ip={}",
    userId, resource, ipAddress);
```

**Errors:**
```java
log.error("Failed to process gate transaction: container={} error={}",
    containerNumber, e.getMessage(), e);

log.error("Circuit breaker OPEN for service={} state={} failureRate={}",
    serviceName, state, failureRate);
```

### 4.6 MDC (Mapped Diagnostic Context)

Every service populates MDC fields that appear in all log entries:

```java
@Component
public class CorrelationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain) {
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put("correlationId", correlationId);
        MDC.put("service", serviceName);
        MDC.put("userId", extractUserId(request));

        response.setHeader("X-Correlation-Id", correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

---

## 5. Alerting — Grafana

### 5.1 Alert Rules

```yaml
# Grafana alerting rules (provisioned via YAML)
apiVersion: 1
groups:
  - name: smartportx-alerts
    folder: SmartPortX
    interval: 1m
    rules:
      # HIGH ERROR RATE
      - uid: smartportx-high-error-rate
        title: "High HTTP 5xx Error Rate"
        condition: A
        data:
          - refId: A
            datasourceUid: prometheus
            model:
              expr: |
                100 * sum(rate(http_server_requests_seconds_count{
                  status=~"5.."
                }[5m])) /
                sum(rate(http_server_requests_seconds_count[5m])) > 5
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate on {{ $labels.service }}"
          description: >
            HTTP 5xx error rate is {{ $value | printf "%.1f" }}%
            for service {{ $labels.service }} over the last 5 minutes.

      # HIGH LATENCY
      - uid: smartportx-high-latency
        title: "High p95 Response Time"
        condition: A
        data:
          - refId: A
            datasourceUid: prometheus
            model:
              expr: |
                histogram_quantile(0.95,
                  rate(http_server_requests_seconds_bucket[5m])
                ) > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High latency on {{ $labels.service }}"
          description: >
            p95 response time is {{ $value | printf "%.2f" }}s
            for service {{ $labels.service }}.

      # HIGH MEMORY
      - uid: smartportx-high-memory
        title: "High JVM Heap Usage"
        condition: A
        data:
          - refId: A
            datasourceUid: prometheus
            model:
              expr: |
                100 * jvm_memory_used_bytes{area="heap"} /
                jvm_memory_max_bytes{area="heap"} > 80
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High heap usage on {{ $labels.service }}"
          description: >
            JVM heap usage is {{ $value | printf "%.1f" }}%
            for service {{ $labels.service }}.

      # DATABASE CONNECTION POOL EXHAUSTED
      - uid: smartportx-db-pool-exhausted
        title: "Database Connection Pool Near Exhaustion"
        condition: A
        data:
          - refId: A
            datasourceUid: prometheus
            model:
              expr: |
                hikaricp_connections_active > 45
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "DB connection pool near exhaustion on {{ $labels.service }}"
          description: >
            Active DB connections: {{ $value }} (max: 50)
            for service {{ $labels.service }}.

      # RABBITMQ QUEUE DEPTH
      - uid: smartportx-rabbitmq-depth
        title: "RabbitMQ Queue Depth High"
        condition: A
        data:
          - refId: A
            datasourceUid: prometheus
            model:
              expr: rabbitmq_queue_depth > 1000
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "Queue {{ $labels.queue }} depth high"
          description: >
            Queue {{ $labels.queue }} has {{ $value }} messages.
            Consumer may be slow or down.

      # SERVICE DOWN
      - uid: smartportx-service-down
        title: "Service Health Check Failing"
        condition: A
        data:
          - refId: A
            datasourceUid: prometheus
            model:
              expr: |
                up{job="smartportx-services"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service {{ $labels.instance }} is DOWN"
          description: >
            Service {{ $labels.instance }} health check has been
            failing for over 1 minute.

      # CIRCUIT BREAKER OPEN
      - uid: smartportx-circuit-breaker-open
        title: "Circuit Breaker OPEN"
        condition: A
        data:
          - refId: A
            datasourceUid: prometheus
            model:
              expr: resilience4j_circuitbreaker_state == 1
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "Circuit breaker OPEN: {{ $labels.name }}"
          description: >
            Circuit breaker {{ $labels.name }} is in OPEN state.
            Calls to {{ $labels.name }} are being rejected.

      # CONTAINER MOVES DROP
      - uid: smartportx-moves-drop
        title: "Container Moves Significantly Below Average"
        condition: A
        data:
          - refId: A
            datasourceUid: prometheus
            model:
              expr: |
                rate(smartportx_container_moves_total[15m]) <
                0.3 * avg_over_time(
                  rate(smartportx_container_moves_total[15m])[1h:15m]
                )
        for: 15m
        labels:
          severity: warning
        annotations:
          summary: "Container moves below normal"
          description: >
            Current container move rate is significantly below the
            hourly average. Operations may be degraded.
```

### 5.2 Notification Channels

| Channel | Status | Use Case |
|---------|--------|----------|
| **Grafana Dashboard Alerts** | MVP | On-call engineer sees alert in dashboard |
| **Email** | Future | Automated email to on-call team |
| **Slack** | Future | #smartportx-alerts channel |
| **PagerDuty** | Future | Critical alerts for 24/7 on-call |

### 5.3 Alert Severity Levels

| Severity | Response Time | Example |
|----------|--------------|---------|
| **Critical** | < 15 minutes | Service down, DB pool exhausted, high error rate |
| **Warning** | < 1 hour | High latency, high memory, circuit breaker open |
| **Info** | Next business day | Container moves below average, queue depth elevated |

---

## 6. Dashboard Views — Grafana

### 6.1 Dashboard Hierarchy

```
SmartPortX Overview (Landing Page)
├── Service Health Overview
├── API Gateway Dashboard
├── Yard Service Dashboard
├── Gate Service Dashboard
├── Container Service Dashboard
├── Vessel Service Dashboard
├── Notification Service Dashboard
├── Integration Service Dashboard
├── Optimization Service Dashboard
├── Identity Service Dashboard
├── Infrastructure Dashboard
│   ├── PostgreSQL
│   ├── RabbitMQ
│   ├── Redis
│   └── JVM Metrics
└── Business Intelligence Dashboard
```

### 6.2 Service Health Overview

**Purpose:** Single-glance status of all 8 services.

| Panel | Visualization | Query |
|-------|---------------|-------|
| Service Status | Stat (green/yellow/red) | `up{job="smartportx-services"}` |
| Request Rate | Time series | `sum(rate(http_server_requests_seconds_count[5m])) by (service)` |
| Error Rate | Time series | `100 * sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (service) / sum(rate(http_server_requests_seconds_count[5m])) by (service)` |
| p95 Latency | Time series | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, service))` |
| Circuit Breakers | Table | `resilience4j_circuitbreaker_state` |
| Active Alerts | Alert list | Grafana unified alerting |

### 6.3 API Gateway Dashboard

| Panel | Visualization | Query |
|-------|---------------|-------|
| Request Rate by Service | Stacked bar | `sum(rate(http_server_requests_seconds_count[5m])) by (service)` |
| Response Time Distribution | Heatmap | `sum(rate(http_server_requests_seconds_bucket[5m])) by (le, service)` |
| Error Rate by Status Code | Stacked area | `sum(rate(http_server_requests_seconds_count{status=~".."}[5m])) by (status)` |
| Rate Limiter Rejections | Time series | `rate(resilience4j_ratelimiter_rejected_permissions[5m])` |
| Active Connections | Gauge | Active WebSocket + HTTP connections |
| Top 10 Slow Endpoints | Table | `topk(10, histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])))` |

### 6.4 Yard Service Dashboard

| Panel | Visualization | Query |
|-------|---------------|-------|
| Container Moves/Hour | Gauge + time series | `rate(smartportx_container_moves_total[1h])` |
| Yard Utilization | Gauge (0–100%) | `smartportx_yard_utilization` |
| Optimization Runs | Time series | `rate(smartportx_optimization_duration_seconds_count[1h])` |
| Optimization Duration | Histogram | `smartportx_optimization_duration_seconds` |
| Container Service Circuit Breaker | State indicator | `resilience4j_circuitbreaker_state{name="containerService"}` |
| Active Moves in Progress | Gauge | Current number of in-progress moves |

### 6.5 Gate Service Dashboard

| Panel | Visualization | Query |
|-------|---------------|-------|
| Gate Transactions/Hour | Gauge + time series | `rate(smartportx_gate_transactions_total[1h])` |
| Queue Length | Time series | Average vehicles waiting at gate |
| Appointments Today | Stat | `smartportx_appointments_booked_total` (daily reset) |
| Gate Transaction Types | Pie chart | `smartportx_gate_transactions_total by (type)` |
| Yard Service Circuit Breaker | State indicator | `resilience4j_circuitbreaker_state{name="yardService"}` |
| Gate Processing Time | Histogram | Transaction processing duration |

### 6.6 Container Service Dashboard

| Panel | Visualization | Query |
|-------|---------------|-------|
| Container Count by Status | Stacked bar | `smartportx_container_status_count by (status)` |
| Container Search Queries | Time series | `rate(http_server_requests_seconds_count{uri="/api/v1/containers/search"}[5m])` |
| Database Query Time | Time series | `hikaricp_connections_usage_seconds` |
| Active Connections | Gauge | `hikaricp_connections_active` |
| Cache Hit Rate | Time series | Cache hits / (hits + misses) |

### 6.7 Vessel Service Dashboard

| Panel | Visualization | Query |
|-------|---------------|-------|
| Active Vessels | Stat | Count of vessels in port |
| Discharge Progress | Gauge per vessel | `smartportx_vessel_discharge_progress` |
| Container Service Latency | Time series | p95 latency to Container Service |
| Vessel ETA Accuracy | Scatter plot | Predicted vs actual arrival |

### 6.8 Infrastructure Dashboard

**PostgreSQL Panel:**
| Metric | Query |
|--------|-------|
| Active Connections | `pg_stat_activity_count` |
| Query Duration p95 | `pg_stat_activity_max_duration` |
| Cache Hit Ratio | `pg_stat_database_blks_hit / (blks_hit + blks_read)` |
| Transactions/sec | `rate(pg_stat_database_xact_commit[5m])` |
| Dead Tuples | `pg_stat_user_tables_n_dead_tup` |
| Replication Lag | `pg_stat_replication_replay_lag` |

**RabbitMQ Panel:**
| Metric | Query |
|--------|-------|
| Queue Depth | `rabbitmq_queue_depth` |
| Publish Rate | `rate(rabbitmq_messages_published_total[5m])` |
| Consume Rate | `rate(rabbitmq_messages_consumed_total[5m])` |
| Consumer Count | `rabbitmq_queue_consumers` |
| Memory Usage | `rabbitmq_queue_memory_bytes` |
| Dead Letter Messages | `rabbitmq_queue_depth{queue=~".*dlq.*"}` |

**Redis Panel:**
| Metric | Query |
|--------|-------|
| Connected Clients | `redis_connected_clients` |
| Memory Usage | `redis_memory_used_bytes` |
| Hit Rate | `redis_keyspace_hits / (hits + misses)` |
| Operations/sec | `rate(redis_commands_processed_total[5m])` |
| Evicted Keys | `rate(redis_evicted_keys_total[5m])` |

---

## 7. Correlation

### 7.1 Correlation ID Flow

```
Client Request
    │
    │ Header: X-Correlation-Id: req-abc-123
    ▼
API Gateway
    │ Propagates correlation ID to all downstream services
    │ Adds to response headers
    ▼
Service A ──REST──▶ Service B ──REST──▶ Service C
    │                   │                   │
    │  X-Correlation-Id propagated via HTTP headers
    ▼
Service A ──RabbitMQ──▶ Service D
    │                    │
    │  X-Correlation-Id in message properties
    ▼
Service D logs: correlationId: req-abc-123
```

### 7.2 Implementation

**Filter (applied to all services):**

```java
@Component
@Order(1)
public class CorrelationFilter extends OncePerRequestFilter {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = generateCorrelationId();
        }

        MDC.put(CORRELATION_MDC_KEY, correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_MDC_KEY);
        }
    }

    private String generateCorrelationId() {
        return "req-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
```

**RabbitMQ message property propagation:**

```java
@Component
public class RabbitCorrelationInterceptor implements RabbitTemplate.ConfirmCallback {

    private static final String CORRELATION_HEADER = "correlationId";

    public void publishWithCorrelation(RabbitTemplate template,
            String exchange, String routingKey, Object message) {

        MessageProperties props = new MessageProperties();
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            props.setHeader(CORRELATION_HEADER, correlationId);
        }

        Message amqpMessage = MessageBuilder
            .withBody(objectMapper.writeValueAsBytes(message))
            .andProperties(props)
            .build();

        template.send(exchange, routingKey, amqpMessage);
    }
}
```

**Consumer-side extraction:**

```java
@Component
public class CorrelationConsumerInterceptor implements ChannelAwareMessageListener {

    @Override
    public void onMessage(Message message, Channel channel) {
        MessageProperties props = message.getMessageProperties();
        String correlationId = props.getHeader("correlationId");

        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }

        try {
            processMessage(message);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
```

### 7.3 Correlation in Logs

Every log entry from every service includes the correlation ID:

```json
{
  "timestamp": "2026-08-26T14:30:15.123Z",
  "level": "INFO",
  "service": "gate-service",
  "correlationId": "req-abc-123-def-456",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "message": "Gate transaction processed",
  "containerNumber": "MSKU7654321"
}
```

To trace a request across all services:

```bash
# Kibana / Elasticsearch query
correlationId: "req-abc-123-def-456"
```

This returns every log entry from every service involved in that request, in
chronological order.

---

## 8. Health Check Endpoints

### 8.1 Endpoint Summary

| Endpoint | Purpose | Probe Type | Auth Required |
|----------|---------|------------|---------------|
| `/actuator/health` | Aggregate health of all indicators | — | Yes (Actuator role) |
| `/actuator/health/liveness` | Is the JVM running? | Liveness | No (k8s probes) |
| `/actuator/health/readiness` | Can the service accept traffic? | Readiness | No (k8s probes) |
| `/actuator/info` | Service version, build info | — | Yes |
| `/actuator/prometheus` | Metrics endpoint for Prometheus | — | No (internal only) |
| `/actuator/metrics` | Available metric names | — | Yes |

### 8.2 Readiness Dependencies

| Service | Must Be Healthy | Can Degrade |
|---------|----------------|-------------|
| Yard | PostgreSQL, Redis | Container Service (cached) |
| Gate | PostgreSQL, Redis | Yard Service (placeholder) |
| Container | PostgreSQL | — |
| Vessel | PostgreSQL | Container Service (stale data) |
| Notification | RabbitMQ | — (queue events) |
| Integration | RabbitMQ | — (queue EDIFACT) |
| Optimization | PostgreSQL | — (return 503) |
| Identity | PostgreSQL | — (reject all) |

### 8.3 Custom Health Indicator

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CompositeDependencyHealthIndicator implements HealthIndicator {

    private final Map<String, HealthIndicator> indicators;

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        boolean allUp = true;

        for (Map.Entry<String, HealthIndicator> entry : indicators.entrySet()) {
            try {
                Health health = entry.getValue().health();
                builder.withDetail(entry.getKey(), health.getStatus().getCode());
                if (health.getStatus() == Status.DOWN) {
                    allUp = false;
                }
            } catch (Exception e) {
                builder.withDetail(entry.getKey(), "ERROR: " + e.getMessage());
                allUp = false;
            }
        }

        return allUp ? builder.up().build() : builder.down().build();
    }
}
```

### 8.4 Readiness Response Example

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "rabbit": { "status": "UP" },
    "redis": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "containerService": { "status": "UP" },
    "identityService": { "status": "UP" }
  }
}
```

---

## 9. Infrastructure Metrics

### 9.1 PostgreSQL Metrics (postgres-exporter)

| Metric | Type | Description |
|--------|------|-------------|
| `pg_stat_database_xact_commit` | Counter | Committed transactions |
| `pg_stat_database_xact_rollback` | Counter | Rolled back transactions |
| `pg_stat_activity_count` | Gauge | Active connections by state |
| `pg_stat_database_blks_hit` | Counter | Buffer cache hits |
| `pg_stat_database_blks_read` | Counter | Disk reads |
| `pg_stat_user_tables_n_live_tup` | Gauge | Live rows per table |
| `pg_stat_user_tables_n_dead_tup` | Gauge | Dead rows (bloat) |
| `pg_stat_replication_replay_lag` | Gauge | Replication lag in seconds |
| `pg_database_size_bytes` | Gauge | Database size |

### 9.2 RabbitMQ Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `rabbitmq_connections` | Gauge | Total connections |
| `rabbitmq_channels` | Gauge | Total channels |
| `rabbitmq_queue_messages` | Gauge | Messages in queue |
| `rabbitmq_queue_messages_ready` | Gauge | Messages ready for delivery |
| `rabbitmq_queue_messages_unacked` | Gauge | Unacknowledged messages |
| `rabbitmq_queue_consumers` | Gauge | Active consumers |
| `rabbitmq_queue_memory` | Gauge | Queue memory in bytes |
| `rabbitmq_queue_disk_reads` | Gauge | Disk read operations |
| `rabbitmq_queue_disk_writes` | Gauge | Disk write operations |

### 9.3 Redis Metrics (redis-exporter)

| Metric | Type | Description |
|--------|------|-------------|
| `redis_connected_clients` | Gauge | Connected clients |
| `redis_used_memory_bytes` | Gauge | Memory usage |
| `redis_max_memory_bytes` | Gauge | Max memory configured |
| `redis_keyspace_hits_total` | Counter | Cache hits |
| `redis_keyspace_misses_total` | Counter | Cache misses |
| `redis_commands_processed_total` | Counter | Total commands processed |
| `redis_expired_keys_total` | Counter | Expired keys |
| `redis_evicted_keys_total` | Counter | Evicted keys (OOM) |

### 9.4 Docker Container Metrics (cAdvisor)

| Metric | Type | Description |
|--------|------|-------------|
| `container_cpu_usage_seconds_total` | Counter | CPU usage |
| `container_memory_working_set_bytes` | Gauge | Memory working set |
| `container_network_receive_bytes_total` | Counter | Network received bytes |
| `container_network_transmit_bytes_total` | Counter | Network transmitted bytes |
| `container_fs_usage_bytes` | Gauge | Filesystem usage |
| `container_fs_limit_bytes` | Gauge | Filesystem limit |

---

## 10. Observability Pipeline

### 10.1 End-to-End Data Flow

```
┌────────────────────────────────────────────────────────────────────────┐
│                         SmartPortX Services                            │
│                                                                       │
│  Metrics (Micrometer) ──▶ /actuator/prometheus ──▶ Prometheus ──▶ Grafana│
│  Traces (OTel Agent) ──▶ OTel Collector ──▶ Jaeger                     │
│  Logs (Logback JSON) ──▶ stdout ──▶ Docker ──▶ ELK (future)           │
│                                                                       │
└────────────────────────────────────────────────────────────────────────┘

Prometheus ──PromQL──▶ Grafana (dashboards + alerts)
Jaeger ──Trace Query──▶ Grafana (trace-to-dashboard correlation)
Docker Logs ──▶ File ──▶ ELK (future)
```

### 10.2 Deployment Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Compose / K8s                      │
│                                                             │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐          │
│  │ Prometheus  │ │   Grafana   │ │   Jaeger    │          │
│  │  :9090      │ │  :3000      │ │  :16686     │          │
│  └──────┬──────┘ └──────┬──────┘ └──────┬──────┘          │
│         │               │               │                   │
│  ┌──────┴───────────────┴───────────────┴──────┐           │
│  │            SmartPortX Network                │           │
│  │                                              │           │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐    │           │
│  │  │  Gateway  │ │   Yard   │ │   Gate   │    │           │
│  │  │  :8080   │ │  :8081   │ │  :8082   │    │           │
│  │  └──────────┘ └──────────┘ └──────────┘    │           │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐    │           │
│  │  │Container │ │  Vessel  │ │Notif.    │    │           │
│  │  │  :8083   │ │  :8084   │ │  :8085   │    │           │
│  │  └──────────┘ └──────────┘ └──────────┘    │           │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐    │           │
│  │  │Integration│ │Optimiz. │ │Identity  │    │           │
│  │  │  :8086   │ │  :8087   │ │  :8088   │    │           │
│  │  └──────────┘ └──────────┘ └──────────┘    │           │
│  └──────────────────────────────────────────────┘           │
│                                                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                    │
│  │PostgreSQL│ │ RabbitMQ │ │  Redis   │                    │
│  │  :5432   │ │  :5672   │ │  :6379   │                    │
│  └──────────┘ └──────────┘ └──────────┘                    │
│                                                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                    │
│  │Postgres  │ │  Rabbit  │ │  Redis   │                    │
│  │Exporter  │ │MQ Exporter│ │ Exporter │                    │
│  │  :9187   │ │  :15692  │ │  :9121   │                    │
│  └──────────┘ └──────────┘ └──────────┘                    │
└─────────────────────────────────────────────────────────────┘
```

---

## 11. Cost and Performance

### 11.1 Metric Storage Estimates

| Component | Metrics/Service | Services | Total | Retention | Storage |
|-----------|----------------|----------|-------|-----------|---------|
| JVM metrics | ~25 | 8 | ~200 | 30 days | ~50 MB |
| HTTP metrics | ~50 | 8 | ~400 | 30 days | ~100 MB |
| Business metrics | ~15 | 8 | ~120 | 90 days | ~50 MB |
| Resilience4j | ~20 | 8 | ~160 | 30 days | ~40 MB |
| Infrastructure | ~100 | — | ~100 | 30 days | ~30 MB |
| **Total** | | | **~980** | | **~270 MB** |

### 11.2 Trace Storage Estimates

| Parameter | Value |
|-----------|-------|
| Avg spans per request | 5 |
| Requests per minute (total) | 500 |
| Sampling rate | 10% |
| Spans per minute (sampled) | 250 |
| Avg span size | 1 KB |
| Storage per minute | 250 KB |
| Storage per hour | 15 MB |
| Storage per day | 360 MB |
| Retention (Jaeger) | 7 days |
| **Total trace storage** | **~2.5 GB** |

### 11.3 Log Storage Estimates

| Parameter | Value |
|-----------|-------|
| Avg log entry size | 500 bytes |
| Log entries per minute (all services) | 10,000 |
| Storage per minute | 5 MB |
| Storage per hour | 300 MB |
| Storage per day | 7.2 GB |
| Retention (local files) | 7 days |
| **Total log storage** | **~50 GB** |

### 11.4 Resource Allocation

| Component | CPU | Memory | Disk |
|-----------|-----|--------|------|
| Prometheus | 0.5 cores | 2 GB | 5 GB |
| Grafana | 0.25 cores | 512 MB | 1 GB |
| Jaeger | 0.5 cores | 1 GB | 5 GB |
| OTel Collector | 0.25 cores | 512 MB | — |
| **Total observability stack** | **1.5 cores** | **4 GB** | **11 GB** |

---

## 12. Configuration Reference

### 12.1 Docker Compose — Observability Stack

```yaml
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=30d'
      - '--storage.tsdb.retention.size=5GB'

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD}
    volumes:
      - grafana_data:/var/lib/grafana
      - ./grafana/dashboards:/var/lib/grafana/dashboards
      - ./grafana/provisioning:/etc/grafana/provisioning

  jaeger:
    image: jaegertracing/all-in-one:latest
    ports:
      - "16686:16686"
      - "4317:4317"
    environment:
      COLLECTOR_OTLP_ENABLED: true
      SPAN_STORAGE_TYPE: badger
      BADGER_DIRECTORY_KEY: /badger/key
      BADGER_DIRECTORY_VALUE: /badger/data
      BADGER_EPHEMERAL: false

  otel-collector:
    image: otel/opentelemetry-collector-contrib:latest
    ports:
      - "4317:4317"
      - "4318:4318"
    volumes:
      - ./otel-collector-config.yaml:/etc/otelcol/config.yaml

  postgres-exporter:
    image: prometheuscommunity/postgres-exporter
    ports:
      - "9187:9187"
    environment:
      DATA_SOURCE_NAME: "postgresql://${DB_USER}:${DB_PASSWORD}@${DB_HOST}:5432/${DB_NAME}?sslmode=disable"

  rabbitmq-exporter:
    image: kbudde/rabbitmq-exporter
    ports:
      - "15692:15692"
    environment:
      RABBITMQ_URL: http://rabbitmq:15672
      RABBITMQ_USER: ${RABBITMQ_USER}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}

  redis-exporter:
    image: oliver006/redis_exporter
    ports:
      - "9121:9121"
    environment:
      REDIS_ADDR: redis://redis:6379

volumes:
  prometheus_data:
  grafana_data:
```

### 12.2 Environment Variables

```bash
# Observability
OTEL_SERVICE_NAME=yard-service
OTEL_SERVICE_NAMESPACE=smartportx
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
OTEL_TRACES_SAMPLER=parentbased_traceidratio
OTEL_TRACES_SAMPLER_ARG=0.1

# Prometheus
PROMETHEUS_SCRAPE_INTERVAL=15s
PROMETHEUS_RETENTION_DAYS=30

# Grafana
GRAFANA_SECURITY_ADMIN_PASSWORD=changeme
GRAFANA_DASHBOARDS_PATH=/var/lib/grafana/dashboards

# Jaeger
COLLECTOR_OTLP_ENABLED=true
SPAN_STORAGE_TYPE=badger

# Logging
LOG_LEVEL=INFO
LOG_FORMAT=json
LOG_OUTPUT=stdout
```

### 12.3 Spring Boot Actuator Endpoints Summary

```
GET /actuator/health              → Aggregate health status
GET /actuator/health/liveness     → Liveness probe (k8s)
GET /actuator/health/readiness    → Readiness probe (k8s)
GET /actuator/info                → Service info (version, build)
GET /actuator/prometheus           → Prometheus metrics (text format)
GET /actuator/metrics             → Available metric names
GET /actuator/metrics/{name}      → Specific metric details
```

---

*End of Observability Architecture Document*
