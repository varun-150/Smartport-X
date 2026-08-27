# ADR-012: Monitoring — Micrometer + Prometheus + Grafana

**Status:** PROPOSED
**Date:** 2026-08-26
**Deciders:** Architect, Lead

---

## Context

The SmartPort platform has 7+ services running in Docker Compose (ADR-011). When issues arise during development or demonstration, the team needs to:

1. **Monitor service health** — which services are up, which are failing?
2. **Track request metrics** — latency, throughput, error rates per service.
3. **Observe database performance** — connection pool usage, query execution times.
4. **Monitor message broker** — queue depths, message rates, consumer lag.
5. **Visualize trends** — dashboards showing system behavior over time.

Without monitoring, debugging distributed services is guesswork. The team needs a monitoring stack that:
- Integrates natively with Spring Boot.
- Is free and open-source.
- Provides rich dashboards.
- Can be deployed alongside the existing Docker Compose stack.

## Decision

We adopt **Micrometer + Prometheus + Grafana** as the monitoring stack.

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Monitoring Stack                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐     scrape      ┌──────────────────┐     │
│  │ Spring Boot  │  /actuator/     │    Prometheus     │     │
│  │ Actuator +   │───────────────→│    (port 9090)    │     │
│  │ Micrometer   │  prometheus     └────────┬─────────┘     │
│  │ (port 8080)  │  endpoint                 │               │
│  └──────────────┘                           │ query         │
│                                             ▼               │
│                                    ┌──────────────────┐     │
│                                    │     Grafana       │     │
│                                    │    (port 3000)    │     │
│                                    └──────────────────┘     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Spring Boot Actuator Configuration

```yaml
# application.yml (each service)
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,env,loggers
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      service: ${SERVICE_SCHEMA:default}
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5,0.75,0.95,0.99
      slo:
        http.server.requests: 100ms,500ms
```

### Custom Business Metrics

```java
@Component
public class VesselMetrics {

    private final Counter vesselArrivalCounter;
    private final Counter vesselDepartureCounter;
    private final Timer vesselProcessingTimer;
    private final Gauge activeVesselsGauge;

    public VesselMetrics(MeterRegistry registry) {
        this.vesselArrivalCounter = Counter.builder("smartport.vessel.arrivals")
            .description("Total vessel arrivals")
            .tag("type", "business")
            .register(registry);

        this.vesselDepartureCounter = Counter.builder("smartport.vessel.departures")
            .description("Total vessel departures")
            .tag("type", "business")
            .register(registry);

        this.vesselProcessingTimer = Timer.builder("smartport.vessel.processing.time")
            .description("Vessel processing time")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.activeVesselsGauge = Gauge.builder("smartport.vessel.active.count",
                this, VesselMetrics::getActiveVesselCount)
            .description("Number of active vessels")
            .register(registry);
    }

    public void recordArrival() { vesselArrivalCounter.increment(); }
    public void recordDeparture() { vesselDepartureCounter.increment(); }

    public <T> T timeProcessing(Supplier<T> operation) {
        return vesselProcessingTimer.record(operation);
    }
}
```

### Prometheus Configuration

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'smartport-services'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'auth-service:8080'
          - 'vessel-service:8080'
          - 'berth-service:8080'
          - 'cargo-service:8080'
          - 'compliance-service:8080'
          - 'notification-service:8080'
          - 'analytics-service:8080'
        labels:
          environment: 'development'

  - job_name: 'smartport-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['gateway:8080']
        labels:
          component: 'gateway'

  - job_name: 'smartport-eureka'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['eureka-server:8761']
        labels:
          component: 'eureka'

  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']
```

### Grafana Dashboards

Pre-configured dashboards for:

1. **Service Health Overview** — all services' health status, uptime, memory usage.
2. **Request Metrics** — HTTP request rate, latency percentiles, error rate per service.
3. **JVM Metrics** — heap usage, GC pauses, thread count, CPU usage.
4. **Database Metrics** — connection pool active/idle, query execution time.
5. **Business Metrics** — vessel arrivals/departures, berth utilization, cargo throughput.

### Docker Compose Addition

```yaml
  prometheus:
    image: prom/prometheus:latest
    container_name: smartport-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=7d'

  grafana:
    image: grafana/grafana:latest
    container_name: smartport-grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: smartport
      GF_INSTALL_PLUGINS: grafana-piechart-panel
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/var/lib/grafana/dashboards
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning
    depends_on:
      - prometheus

volumes:
  prometheus_data:
  grafana_data:
```

## Alternatives Considered

### 1. ELK Stack (Elasticsearch + Logstash + Kibana)
A centralized logging and search stack.
- **Pros:** Powerful full-text search, log aggregation, visualization.
- **Cons:** Heavy resource usage (~1GB RAM per component), focuses on logs not metrics, complex setup, not Spring-native, overkill for metrics monitoring.
- **Verdict:** Rejected — too heavy for metrics; useful for log aggregation but not needed for v1.

### 2. Datadog
A commercial monitoring SaaS platform.
- **Pros:** Zero infrastructure, excellent dashboards, alerting, APM.
- **Cons:** Commercial (paid), requires internet connection, data leaves the machine, not suitable for a student project with no budget.
- **Verdict:** Rejected — commercial and cloud-dependent.

### 3. New Relic
Another commercial APM platform.
- **Pros:** Comprehensive APM, distributed tracing, error tracking.
- **Cons:** Commercial, cloud-dependent, requires agent installation, overkill for development monitoring.
- **Verdict:** Rejected — commercial and cloud-dependent.

### 4. Spring Boot Actuator Only
Using only Spring Boot Actuator endpoints without visualization.
- **Pros:** Zero additional infrastructure, built into Spring Boot.
- **Cons:** Raw JSON metrics, no visualization, no dashboards, no historical data, no alerting.
- **Verdict:** Rejected — provides data but no way to visualize or alert on it.

### 5. Zipkin
A distributed tracing system.
- **Pros:** Excellent for tracing requests across services, latency analysis.
- **Cons:** Focuses on tracing only (not metrics or health), requires Brave/Spring Cloud Sleuth integration, doesn't provide business metrics.
- **Verdict:** Rejected — useful as a complement but doesn't cover health and business metrics.

## Rationale

Micrometer + Prometheus + Grafana is the optimal choice based on:

1. **Spring Boot native:** Micrometer is the standard metrics facade for Spring Boot — `spring-boot-starter-actuator` includes it automatically.
2. **Free and open-source:** All three components are free with no usage limits.
3. **Prometheus integration:** Micrometer exports metrics in Prometheus format natively — no custom configuration needed.
4. **Grafana dashboards:** Grafana provides rich, customizable dashboards with alerting capabilities.
5. **Docker deployment:** All three components run as Docker containers alongside the existing stack.
6. **Industry standard:** Prometheus + Grafana is the most widely adopted open-source monitoring stack.
7. **Business metrics:** Custom metrics (vessel arrivals, berth utilization) provide domain-specific insights.
8. **JVM metrics:** Micrometer automatically exposes JVM metrics (heap, GC, threads) without configuration.

### Key Metrics to Monitor

| Category | Metric | Source |
|----------|--------|--------|
| HTTP | Request rate, latency, error rate | Micrometer (automatic) |
| JVM | Heap usage, GC count/time, threads | Micrometer (automatic) |
| Database | Connection pool, query time | Spring Data JPA metrics |
| RabbitMQ | Queue depth, message rate | RabbitMQ Prometheus plugin |
| Business | Vessel arrivals, berth utilization | Custom Micrometer metrics |
| Health | Service up/down, dependency status | Spring Boot Actuator |

## Consequences

### Positive
- **Free and open-source** — no licensing costs, no usage limits.
- **Rich dashboards** — Grafana provides beautiful, customizable visualizations.
- **Spring Boot integration** — Micrometer is built into Spring Boot with zero configuration.
- **Historical data** — Prometheus stores metrics with configurable retention (7 days default).
- **Alerting** — Grafana supports alert rules for threshold-based notifications.
- **Community dashboards** — pre-built Grafana dashboards for Spring Boot, PostgreSQL, RabbitMQ.
- **Lightweight** — Prometheus (~100MB RAM), Grafana (~100MB RAM), minimal overhead.

### Negative
- **Self-hosted** — the team manages Prometheus and Grafana infrastructure (vs. SaaS).
- **Setup overhead** — initial configuration of dashboards, data sources, and alert rules takes time.
- **Storage management** — Prometheus data grows over time (mitigated by retention policies).
- **No distributed tracing** — Micrometer + Prometheus doesn't trace requests across services (would need Zipkin or Jaeger for that).
- **Dashboard maintenance** — Grafana dashboards need updating when services change.
- **Alert fatigue** — poorly configured alerts can generate noise (mitigated by careful threshold selection).

## Related

- ADR-001: Architecture Style — SBA (monitoring across all services)
- ADR-003: Backend Framework — Spring Boot 3.x (Actuator and Micrometer integration)
- ADR-011: Deployment — Docker Compose (Prometheus and Grafana deployed as containers)
- Micrometer Documentation: https://micrometer.io/docs
- Prometheus Documentation: https://prometheus.io/docs/
- Grafana Documentation: https://grafana.com/docs/
