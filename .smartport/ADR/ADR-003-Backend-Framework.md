# ADR-003: Backend Framework — Spring Boot 3.x

**Status:** PROPOSED
**Date:** 2026-08-26
**Deciders:** Architect, Lead

---

## Context

With Java 25 selected as the backend language (ADR-002), the team needs a framework that:

1. **Integrates with Spring ecosystem components** — Spring Cloud Gateway (ADR-006), Spring Security for JWT (ADR-009), Spring Data JPA for database access.
2. **Supports the SBA architecture** — provides tooling for building REST services, messaging, and configuration management.
3. **Has extensive documentation and community support** — the team needs to solve problems quickly.
4. **Matches team skill level** — at least 2 team members have production or academic Spring experience.
5. **Runs on Java 25** — official support for the chosen language version.

The framework decision is tightly coupled with the language choice. Since Java is selected, Node.js/Express and Go/Gin are eliminated. The remaining candidates are Java/JVM-based frameworks.

## Decision

We adopt **Spring Boot 3.5.x** as the backend framework for all services.

### Why Spring Boot 3.x (not 2.x)

Spring Boot 2.x reached end of open-source support in November 2023. Spring Boot 3.x:
- Requires Java 17+ (we're using Java 25)
- Uses Jakarta EE 9+ namespace (`jakarta.*` instead of `javax.*`)
- Has native GraalVM compilation support
- Includes improved observability (Micrometer integration)
- Supports Spring Security 6.x with lambda DSL configuration

### Framework Components Per Service

| Component | Library | Purpose |
|-----------|---------|---------|
| Web | Spring MVC | REST API endpoints |
| Data Access | Spring Data JPA + Hibernate | Database operations |
| Security | Spring Security 6.x | JWT validation, RBAC |
| Messaging | Spring AMQP | RabbitMQ integration |
| Configuration | Spring Cloud Config | Centralized configuration |
| Service Discovery | Spring Cloud Netflix Eureka | Service registration |
| API Documentation | SpringDoc OpenAPI 3.0 | Swagger UI |
| Validation | Jakarta Bean Validation | Request validation |
| Testing | Spring Boot Test | Unit & integration tests |
| Observability | Spring Boot Actuator + Micrometer | Health checks, metrics |

### Project Structure

```
smartport/
├── smartport-common/          # Shared DTOs, utilities, exceptions
├── smartport-gateway/         # Spring Cloud Gateway
├── auth-service/              # Authentication & authorization
├── vessel-service/            # Vessel tracking
├── berth-service/             # Berth scheduling
├── cargo-service/             # Cargo management
├── compliance-service/        # Regulatory compliance
├── notification-service/      # Notifications
├── analytics-service/         # Reporting & analytics
├── docker-compose.yml         # Deployment orchestration
└── pom.xml                    # Parent POM (multi-module)
```

### Key Dependencies (Parent POM)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.5</version>
</parent>

<modules>
    <module>smartport-common</module>
    <module>smartport-gateway</module>
    <module>auth-service</module>
    <module>vessel-service</module>
    <module>berth-service</module>
    <module>cargo-service</module>
    <module>compliance-service</module>
    <module>notification-service</module>
    <module>analytics-service</module>
</modules>
```

## Alternatives Considered

### 1. Jakarta EE (with TomEE/GlassFish)
The enterprise Java standard under Eclipse Foundation.
- **Pros:** Standardized API, vendor-neutral, full Java EE feature set.
- **Cons:** Heavyweight, verbose XML configuration (improved in recent versions), smaller community than Spring, fewer educational resources, no Spring Cloud integration.
- **Verdict:** Rejected — Spring Boot provides a much better developer experience and ecosystem.

### 2. Micronaut
A modern JVM framework with compile-time DI and GraalVM native support.
- **Pros:** Faster startup than Spring, lower memory footprint, compile-time DI (no reflection), excellent GraalVM support.
- **Cons:** Smaller community, fewer learning resources, limited Spring Cloud compatibility, team has no Micronaut experience, some Spring libraries don't work directly.
- **Verdict:** Rejected — ecosystem maturity and team familiarity favor Spring Boot.

### 3. Quarkus
A Kubernetes-native Java framework by Red Hat.
- **Pros:** Excellent cloud-native support, fast startup, low memory, dev mode with live reload.
- **Cons:** Red Hat ecosystem lock-in tendency, smaller community than Spring, different programming model, team has no experience.
- **Verdict:** Rejected — Spring Boot is more widely adopted and better documented.

### 4. Node.js / Express
A JavaScript runtime with Express web framework.
- **Pros:** JavaScript ecosystem, fast prototyping, huge npm registry.
- **Cons:** Not JVM-based (violates language decision), single-threaded event loop limits CPU-bound work, weaker typing (even with TypeScript), doesn't meet SOA course Java requirements.
- **Verdict:** Rejected — language incompatibility with ADR-002.

### 5. Go / Gin
A compiled language with Gin web framework.
- **Pros:** Fast runtime, excellent concurrency, small binaries.
- **Cons:** Not JVM-based, incompatible with Spring ecosystem, team limited Go experience, no Spring Cloud integration.
- **Verdict:** Rejected — ecosystem incompatibility.

## Rationale

Spring Boot 3.x is the clear winner based on:

1. **Ecosystem dominance:** Spring has the largest Java framework ecosystem with 15+ skill files available in our skill library (spring-boot-microservices, spring-data-jpa, spring-security-jwt, spring-cloud-gateway, etc.).
2. **Component integration:** Every infrastructure decision (gateway, security, database, messaging, monitoring) has a first-class Spring integration.
3. **Team proficiency:** 3 of 4 team members have academic or project experience with Spring Boot.
4. **Documentation quality:** Spring's official documentation, Baeldung tutorials, and Spring Guides are industry-leading.
5. **SOA alignment:** Spring Cloud provides all the patterns needed for service-based architecture (gateway, discovery, circuit breaker, config server).
6. **Spring Boot Test:** Comprehensive testing support with `@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`, Testcontainers integration.
7. **Actuator integration:** Production-ready health checks, metrics, and observability endpoints with zero additional configuration.

## Consequences

### Positive
- **Mature ecosystem** — every requirement has a well-tested Spring solution.
- **Rich feature set** — web, data, security, messaging, configuration, observability all in one framework.
- **Skill library coverage** — 15+ specialized Spring skills available for guidance.
- **Community support** — largest Java community means fast problem resolution.
- **Testing support** — Spring Boot Test provides comprehensive unit and integration testing tools.
- **Configuration flexibility** — `application.yml` profiles, environment variables, and Spring Cloud Config.
- **Production readiness** — Actuator endpoints, health checks, and Micrometer metrics out of the box.

### Negative
- **Heavier than alternatives** — larger memory footprint and slower startup compared to Micronaut/Quarkus (acceptable for our deployment model).
- **Steeper learning curve** — Spring's extensive feature set can be overwhelming for beginners (mitigated by team experience).
- **Boot time overhead** — Spring Boot applications take 2-5 seconds to start (acceptable for Docker Compose deployment).
- **Opinionated defaults** — Spring Boot's auto-configuration can be surprising when it doesn't match expectations (mitigated by explicit configuration).
- **Framework coupling** — changing frameworks later would require significant refactoring (unlikely given Spring's dominance).

## Related

- ADR-001: Architecture Style — SBA (Spring Boot implements each service)
- ADR-002: Backend Language — Java 25 (framework requires Java 17+)
- ADR-006: API Gateway — Spring Cloud Gateway (Spring Cloud component)
- ADR-007: Service Discovery — Eureka (Spring Cloud component)
- ADR-009: Authentication — JWT with Spring Security
- ADR-010: Database Migration — Flyway (Spring Boot native support)
- ADR-012: Monitoring — Micrometer (Spring Boot Actuator integration)
