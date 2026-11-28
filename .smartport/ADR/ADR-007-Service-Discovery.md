# ADR-007: Service Discovery — Netflix Eureka

**Status:** PROPOSED
**Date:** 2026-08-26
**Deciders:** Architect, Lead

---

## Context

In the SBA architecture (ADR-001), services communicate with each other via REST APIs. Without service discovery, each service would need to know the exact host and port of every other service it calls. This creates several problems:

1. **Hardcoded addresses** — if a service moves to a different port, all callers must be updated.
2. **No health checking** — callers don't know if a service is alive until they get a connection error.
3. **No load balancing** — if multiple instances of a service run, there's no automatic distribution.
4. **Configuration explosion** — each service needs a config file listing all other services' addresses.

Service discovery solves these by providing a registry where services announce themselves and a lookup mechanism for callers.

Key requirements:
- Integration with Spring Cloud Gateway (ADR-006).
- Integration with Spring Boot services.
- Simple setup for Docker Compose deployment.
- Health checking to remove unhealthy instances.

## Decision

We adopt **Netflix Eureka** as the service discovery mechanism.

### Eureka Server Setup

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

### Eureka Server Configuration

```yaml
# eureka-server application.yml
server:
  port: 8761

eureka:
  instance:
    hostname: localhost
  client:
    register-with-eureka: false
    fetch-registry: false
    service-url:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/
  server:
    enable-self-preservation: false  # Development mode
    eviction-interval-timer-in-ms: 5000
```

### Client Configuration (All Services)

```yaml
# Every service's application.yml
spring:
  application:
    name: vessel-service  # Service name registered with Eureka

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
    registry-fetch-interval-seconds: 5
  instance:
    prefer-ip-address: true  # Docker environment
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
```

### Service Registration Flow

```
┌─────────────────┐     register      ┌─────────────────┐
│  vessel-service  │ ─────────────────→│  Eureka Server   │
│  (port: 8081)    │     heartbeat     │  (port: 8761)   │
└─────────────────┘ ←─────────────────└─────────────────┘
                                              ↑
┌─────────────────┐     register              │
│  berth-service   │ ──────────────────────────┘
│  (port: 8082)    │     heartbeat
└─────────────────┘ ←─────────────────────┘
                                              ↑
┌─────────────────┐     register              │
│  cargo-service   │ ──────────────────────────┘
│  (port: 8083)    │     heartbeat
└─────────────────┘ ←─────────────────────┘
```

### Load Balancing with Eureka

```java
// RestTemplate with @LoadBalanced resolves service names via Eureka
@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// Usage — "vessel-service" resolves to actual host:port via Eureka
@Service
public class BerthService {

    private final RestTemplate restTemplate;

    public VesselInfo getVesselInfo(String imoNumber) {
        return restTemplate.getForObject(
            "http://vessel-service/api/vessels/{imo}",
            VesselInfo.class,
            imoNumber
        );
    }
}
```

## Alternatives Considered

### 1. HashiCorp Consul
A service mesh with service discovery, health checking, and KV store.
- **Pros:** Rich feature set (health checks, KV store, service mesh), multi-datacenter support, Web UI.
- **Cons:** Separate installation required, more complex setup, additional port (8500), heavier than Eureka for our needs, separate health check configuration.
- **Verdict:** Rejected — overkill for single-machine deployment.

### 2. Apache Zookeeper
A distributed coordination service used for service discovery.
- **Pros:** Battle-tested, strong consistency (CP system), used by Kafka.
- **Cons:** Complex setup, requires Curator library for Java clients, not Spring-native, session management complexity, overkill for service discovery alone.
- **Verdict:** Rejected — too complex for our use case.

### 3. Kubernetes DNS (for containerized deployments)
Using Kubernetes' built-in DNS for service discovery.
- **Pros:** Zero additional infrastructure, automatic, battle-tested at scale.
- **Cons:** Requires Kubernetes cluster (we're using Docker Compose), not available in our deployment model, would need to switch deployment infrastructure entirely.
- **Verdict:** Rejected — incompatible with Docker Compose deployment (ADR-011).

### 4. Static Configuration
Hardcoded service addresses in each service's configuration file.
- **Pros:** Zero infrastructure, simple to understand, no runtime dependency.
- **Cons:** No health checking, no load balancing, manual updates required when services move, configuration drift across services.
- **Verdict:** Rejected — doesn't scale to 7+ services, no health checking.

### 5. Netflix Eureka 2.x (Prana)
The sidecar-based approach for non-JVM services.
- **Pros:** Enables service discovery for non-Java services.
- **Cons:** Deprecated, not maintained, additional sidecar container per service.
- **Verdict:** Rejected — deprecated.

## Rationale

Eureka is the optimal choice based on:

1. **Spring Cloud integration:** Spring Cloud Netflix Eureka provides `@EnableEurekaServer` and `@EnableDiscoveryClient` annotations — one line to enable discovery.
2. **Gateway integration:** Spring Cloud Gateway (ADR-006) uses `lb://service-name` URIs that resolve via Eureka automatically.
3. **Simple setup:** Eureka Server is a single Spring Boot application with one annotation.
4. **Peer-to-peer replication:** Eureka supports multiple server instances for HA (useful if we scale beyond single-machine deployment).
5. **Health checking:** Eureka uses heartbeat-based health checking — services that stop sending heartbeats are automatically deregistered.
6. **Self-preservation mode:** Eureka protects against network partitions by not deregistering instances during network instability (disabled in development for faster eviction).
7. **Spring Boot auto-configuration:** Adding `spring-cloud-starter-netflix-eureka-client` dependency auto-configures registration and discovery.

### Development vs. Production Considerations

| Aspect | Development | Production |
|--------|-------------|------------|
| Eureka instances | 1 | 2+ (peer replication) |
| Self-preservation | Disabled | Enabled |
| Heartbeat interval | 10s | 30s |
| Fetch registry interval | 5s | 30s |
| IP address preference | Yes | Hostname preferred |

## Consequences

### Positive
- **Simple setup** — one `@EnableEurekaServer` annotation and dependency.
- **Spring integration** — native Spring Cloud integration with zero custom code.
- **Dynamic routing** — services can move ports and instances without caller configuration changes.
- **Health checking** — unhealthy instances are automatically deregistered.
- **Load balancing** — `@LoadBalanced` RestTemplate distributes requests across instances.
- **Battle-tested** — Netflix ran Eureka in production for years at massive scale.

### Negative
- **Eventually consistent** — Eureka favors availability over consistency (AP system); service registry may be slightly stale.
- **No health check beyond heartbeats** — Eureka checks if a service is alive (heartbeat) but not if it's healthy (no custom health endpoint integration without additional configuration).
- **Registry caching** — clients cache the registry; updates propagate with a delay (mitigated by `registry-fetch-interval-seconds: 5`).
- **Java-specific** — primarily designed for JVM services; non-JVM services need additional configuration.
- **Maintenance mode** — Netflix has placed Eureka in maintenance mode (though Spring Cloud continues to support it).

## Related

- ADR-001: Architecture Style — SBA (services register with Eureka)
- ADR-003: Backend Framework — Spring Boot 3.x (Spring Cloud Netflix dependency)
- ADR-006: API Gateway — Spring Cloud Gateway (uses Eureka for route resolution)
- Spring Cloud Netflix Eureka: https://docs.spring.io/spring-cloud-netflix/reference/
- Eureka GitHub: https://github.com/Netflix/eureka
