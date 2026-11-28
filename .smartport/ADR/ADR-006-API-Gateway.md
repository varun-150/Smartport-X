# ADR-006: API Gateway — Spring Cloud Gateway

**Status:** PROPOSED
**Date:** 2026-08-26
**Deciders:** Architect, Lead

---

## Context

The SmartPort platform has 7 services (ADR-001) each exposing REST APIs. Without a gateway, the React frontend (ADR-008) would need to know the address and port of every service, handle JWT validation independently, and manage CORS configuration per service.

An API gateway solves these problems by providing:
1. A single entry point for all client requests.
2. Centralized authentication and authorization.
3. Request routing to appropriate backend services.
4. Cross-cutting concerns (CORS, rate limiting, logging).

The gateway must integrate with Eureka (ADR-007) for service discovery, validate JWT tokens (ADR-009), and handle both REST and WebSocket connections for real-time vessel tracking.

## Decision

We adopt **Spring Cloud Gateway** as the API gateway for all synchronous client-facing requests.

### Route Configuration

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/auth/**
          filters:
            - StripPrefix=2

        - id: vessel-service
          uri: lb://vessel-service
          predicates:
            - Path=/api/vessels/**
          filters:
            - StripPrefix=2
            - name: CircuitBreaker
              args:
                name: vessel-circuit
                fallbackUri: forward:/fallback/vessels

        - id: berth-service
          uri: lb://berth-service
          predicates:
            - Path=/api/berths/**
          filters:
            - StripPrefix=2

        - id: cargo-service
          uri: lb://cargo-service
          predicates:
            - Path=/api/cargo/**
          filters:
            - StripPrefix=2

        - id: compliance-service
          uri: lb://compliance-service
          predicates:
            - Path=/api/compliance/**
          filters:
            - StripPrefix=2

        - id: notification-service
          uri: lb://notification-service
          predicates:
            - Path=/api/notifications/**
          filters:
            - StripPrefix=2

        - id: analytics-service
          uri: lb://analytics-service
          predicates:
            - Path=/api/analytics/**
          filters:
            - StripPrefix=2
```

### JWT Authentication Filter

```java
@Component
public class JwtAuthenticationFilter implements GatewayFilter, Ordered {

    private final JwtValidator jwtValidator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Skip auth for public endpoints
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        String token = extractToken(exchange.getRequest());
        if (token == null) {
            return unauthorizedResponse(exchange, "Missing authorization token");
        }

        return jwtValidator.validate(token)
            .flatMap(claims -> {
                // Add user info to headers for downstream services
                ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", claims.getSubject())
                    .header("X-User-Role", claims.get("role", String.class))
                    .build();
                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            })
            .onErrorResume(e -> unauthorizedResponse(exchange, "Invalid token"));
    }

    @Override
    public int getOrder() {
        return -10; // Execute early in the filter chain
    }
}
```

### WebSocket Support (Vessel Tracking)

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: vessel-tracking-ws
          uri: lb:ws://vessel-service
          predicates:
            - Path=/ws/vessels/**
          filters:
            - StripPrefix=2
```

### CORS Configuration

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "http://localhost:5173"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders: "*"
            allowCredentials: true
            maxAge: 3600
```

## Alternatives Considered

### 1. Netflix Zuul
The original Spring Cloud gateway (now in maintenance mode).
- **Pros:** Familiar to Spring developers, well-documented.
- **Cons:** Blocking I/O model, not recommended for new projects, lower performance, lacks reactive support, maintenance mode since 2020.
- **Verdict:** Rejected — Spring Cloud Gateway is the official successor.

### 2. Kong
An open-source API gateway built on Nginx.
- **Pros:** High performance, plugin ecosystem, admin API, rate limiting built-in.
- **Cons:** Requires separate database (PostgreSQL or Cassandra), more complex deployment, not Spring-native, requires learning Kong's configuration language.
- **Verdict:** Rejected — deployment complexity and ecosystem mismatch.

### 3. Traefik
A cloud-native reverse proxy and load balancer.
- **Pros:** Automatic service discovery, Let's Encrypt integration, Docker label-based configuration.
- **Cons:** Not Spring-native, limited Spring Security integration, requires custom middleware for JWT validation, less control over request transformation.
- **Verdict:** Rejected — poor Spring ecosystem integration.

### 4. Nginx (as Reverse Proxy)
A high-performance HTTP server and reverse proxy.
- **Pros:** Extremely fast, battle-tested, low resource usage.
- **Cons:** Static configuration (no dynamic routing without Lua/OpenResty), no Spring Security integration, no Eureka integration, requires separate JWT validation module.
- **Verdict:** Rejected — lacks the dynamic routing and security integration needed.

### 5. No Gateway (Direct Client Calls)
The React frontend calls each service directly.
- **Pros:** Zero additional infrastructure, simplest architecture.
- **Cons:** Client must know all service addresses, duplicated JWT validation, no centralized CORS, no request aggregation, tight coupling between client and service topology.
- **Verdict:** Rejected — violates gateway pattern, creates client complexity.

## Rationale

Spring Cloud Gateway is the optimal choice based on:

1. **Reactive/non-blocking:** Built on Spring WebFlux and Project Reactor, handles thousands of concurrent connections with minimal threads. Critical for WebSocket vessel tracking.
2. **Spring ecosystem integration:** Native integration with Eureka (service discovery), Spring Security (JWT validation), and Spring Boot Actuator (health checks).
3. **Eureka service discovery:** Routes use `lb://service-name` URIs that resolve dynamically via Eureka. No hardcoded service addresses.
4. **JWT filter support:** Spring Security's `ReactiveJwtDecoder` integrates seamlessly with the gateway filter chain.
5. **Circuit breaker integration:** Resilience4j circuit breakers protect against cascading failures.
6. **WebSocket support:** First-class WebSocket routing for real-time vessel position streaming.
7. **Route configuration:** YAML-based route configuration is readable and maintainable.
8. **Team familiarity:** Spring Cloud Gateway is covered in the Spring Cloud Gateway skill file.

## Consequences

### Positive
- **Spring ecosystem consistency** — same programming model, configuration style, and tooling as backend services.
- **Reactive performance** — non-blocking I/O handles high concurrency with low resource usage.
- **Centralized security** — JWT validation in one place, not duplicated per service.
- **Dynamic routing** — Eureka integration enables automatic route updates when services start/stop.
- **CORS management** — single CORS configuration for all services.
- **Circuit breakers** — Resilience4j integration protects against cascading failures.
- **WebSocket support** — real-time vessel tracking without additional infrastructure.

### Negative
- **Tied to Spring ecosystem** — switching to a non-Spring gateway would require rewriting all filters and routes.
- **Learning curve** — reactive programming model (WebFlux/Reactor) has a steeper learning curve than servlet-based Spring MVC.
- **No built-in rate limiting** — requires custom filter or Resilience4j RateLimiter (added manually).
- **Debugging complexity** — reactive chains are harder to debug than imperative code.
- **Single point of failure** — gateway failure blocks all client requests (mitigated by running multiple instances in production, single instance in development).

## Related

- ADR-001: Architecture Style — SBA (gateway routes to services)
- ADR-003: Backend Framework — Spring Boot 3.x (Spring Cloud Gateway runs on Spring Boot)
- ADR-007: Service Discovery — Eureka (gateway uses Eureka for route resolution)
- ADR-008: Frontend — React (client calls gateway)
- ADR-009: Authentication — JWT (gateway validates tokens)
- Spring Cloud Gateway Documentation: https://docs.spring.io/spring-cloud-gateway/reference/
