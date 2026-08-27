# ADR-011: Deployment — Docker Compose

**Status:** PROPOSED
**Date:** 2026-08-26
**Deciders:** Architect, Lead

---

## Context

The SmartPort platform consists of 9+ components:

1. React frontend (ADR-008)
2. Spring Cloud Gateway (ADR-006)
3. Eureka Server (ADR-007)
4. Auth Service
5. Vessel Service
6. Berth Service
7. Cargo Service
8. Compliance Service
9. Notification Service
10. Analytics Service
11. PostgreSQL 17 (ADR-004)
12. RabbitMQ (ADR-005)

Each must be started, configured, and connected for the system to function. Starting 12+ processes manually is error-prone and time-consuming. The team needs a deployment mechanism that:

1. Starts all components with a single command.
2. Manages networking between services.
3. Handles service dependencies and startup order.
4. Works on a single development machine.
5. Is reproducible across team members' machines.

The deployment target is a single terminal/machine — the system must be demonstrable on a laptop during the course presentation.

## Decision

We adopt **Docker Compose v2** for all deployment (development, testing, and demonstration).

### Docker Compose Structure

```yaml
# docker-compose.yml
version: '3.8'

services:
  # ═══════════════════════════════════════
  # Infrastructure
  # ═══════════════════════════════════════

  postgres:
    image: postgres:17-alpine
    container_name: smartport-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: smartport
      POSTGRES_USER: smartport
      POSTGRES_PASSWORD: smartport
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init-scripts:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U smartport"]
      interval: 10s
      timeout: 5s
      retries: 5

  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    container_name: smartport-rabbitmq
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 15s
      timeout: 10s
      retries: 5

  # ═══════════════════════════════════════
  # Discovery & Gateway
  # ═══════════════════════════════════════

  eureka-server:
    build:
      context: ./eureka-server
      dockerfile: Dockerfile
    container_name: smartport-eureka
    ports:
      - "8761:8761"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 10

  gateway:
    build:
      context: ./smartport-gateway
      dockerfile: Dockerfile
    container_name: smartport-gateway
    ports:
      - "8080:8080"
    depends_on:
      eureka-server:
        condition: service_healthy
    environment:
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 10

  # ═══════════════════════════════════════
  # Business Services
  # ═══════════════════════════════════════

  auth-service:
    build:
      context: ./auth-service
      dockerfile: Dockerfile
    container_name: smartport-auth
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/smartport
      SPRING_DATASOURCE_USERNAME: smartport
      SPRING_DATASOURCE_PASSWORD: smartport
      SERVICE_SCHEMA: auth
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_RABBITMQ_HOST: rabbitmq

  vessel-service:
    build:
      context: ./vessel-service
      dockerfile: Dockerfile
    container_name: smartport-vessel
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/smartport
      SERVICE_SCHEMA: vessel
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_RABBITMQ_HOST: rabbitmq

  berth-service:
    build:
      context: ./berth-service
      dockerfile: Dockerfile
    container_name: smartport-berth
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/smartport
      SERVICE_SCHEMA: berth
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_RABBITMQ_HOST: rabbitmq

  cargo-service:
    build:
      context: ./cargo-service
      dockerfile: Dockerfile
    container_name: smartport-cargo
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/smartport
      SERVICE_SCHEMA: cargo
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_RABBITMQ_HOST: rabbitmq

  compliance-service:
    build:
      context: ./compliance-service
      dockerfile: Dockerfile
    container_name: smartport-compliance
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/smartport
      SERVICE_SCHEMA: compliance
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_RABBITMQ_HOST: rabbitmq

  notification-service:
    build:
      context: ./notification-service
      dockerfile: Dockerfile
    container_name: smartport-notification
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/smartport
      SERVICE_SCHEMA: notification
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_RABBITMQ_HOST: rabbitmq

  analytics-service:
    build:
      context: ./analytics-service
      dockerfile: Dockerfile
    container_name: smartport-analytics
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/smartport
      SERVICE_SCHEMA: analytics
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_RABBITMQ_HOST: rabbitmq

  # ═══════════════════════════════════════
  # Frontend
  # ═══════════════════════════════════════

  smartport-ui:
    build:
      context: ./smartport-ui
      dockerfile: Dockerfile
    container_name: smartport-ui
    ports:
      - "5173:80"
    depends_on:
      gateway:
        condition: service_healthy

volumes:
  postgres_data:
  rabbitmq_data:
```

### Dockerfile Pattern (Spring Boot)

```dockerfile
# Multi-stage build
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Dockerfile Pattern (React)

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### Startup Commands

```bash
# Start all services
docker compose up -d

# View logs
docker compose logs -f

# Start specific service
docker compose up -d auth-service

# Stop all services
docker compose down

# Rebuild after code changes
docker compose up -d --build

# View service status
docker compose ps
```

## Alternatives Considered

### 1. Kubernetes (K3s/Minikube)
Container orchestration with Kubernetes.
- **Pros:** Auto-scaling, self-healing, rolling updates, industry standard for production.
- **Cons:** Significant complexity for a student project, requires Kubernetes knowledge, heavier resource usage (~2GB RAM for K3s), YAML configuration is verbose, overkill for single-machine deployment.
- **Verdict:** Rejected — operational complexity outweighs benefits for a semester project.

### 2. Bare Metal (Direct Installation)
Install PostgreSQL, RabbitMQ, and Java directly on the machine.
- **Pros:** No container overhead, native performance.
- **Cons:** Not reproducible across machines, difficult to set up on new developer machines, version conflicts possible, no isolation between services, manual dependency management.
- **Verdict:** Rejected — not reproducible and setup is error-prone.

### 3. Vagrant
Virtual machine-based development environments.
- **Pros:** Complete isolation, reproducible VMs, works on any host OS.
- **Cons:** Heavy resource usage (each VM needs dedicated RAM), slow startup, Docker is now preferred for development, Vagrant is losing community momentum.
- **Verdict:** Rejected — Docker Compose provides the same benefits with less overhead.

### 4. Docker Swarm
Docker's built-in container orchestration.
- **Pros:** Simpler than Kubernetes, built into Docker, service discovery, overlay networking.
- **Cons:** Limited features compared to Kubernetes, less community support, Docker Inc. deprecated Swarm features, doesn't add much over Docker Compose for single-machine.
- **Verdict:** Rejected — Docker Compose is sufficient for single-machine deployment.

### 5. Testcontainers (for integration testing)
Using Testcontainers to spin up infrastructure for tests.
- **Pros:** Perfect integration testing with real databases/brokers.
- **Cons:** Not a deployment solution, slower test execution, requires Docker on CI.
- **Verdict:** Not rejected — used alongside Docker Compose for integration testing.

## Rationale

Docker Compose is the optimal choice based on:

1. **Single command deployment:** `docker compose up -d` starts all 12+ components.
2. **Dependency management:** `depends_on` with health checks ensures services start in the correct order.
3. **Networking:** Docker Compose creates a private network where services communicate by container name.
4. **Volume management:** Named volumes persist PostgreSQL data and RabbitMQ state across restarts.
5. **Development parity:** The same Docker Compose file runs on every team member's machine.
6. **Resource efficiency:** Alpine-based images minimize container size (Java service ~200MB, Node ~150MB).
7. **Demonstration ready:** `docker compose up -d` starts the entire system for the course presentation.
8. **Easy teardown:** `docker compose down -v` cleanly removes everything.

### Resource Requirements

| Component | RAM | CPU | Disk |
|-----------|-----|-----|------|
| PostgreSQL 17 | 256MB | 0.5 | 1GB |
| RabbitMQ 3.13 | 128MB | 0.25 | 500MB |
| Eureka Server | 256MB | 0.25 | 500MB |
| Gateway | 256MB | 0.25 | 500MB |
| 7x Business Services | 7 × 256MB | 7 × 0.25 | 7 × 500MB |
| React UI (Nginx) | 64MB | 0.1 | 100MB |
| **Total** | **~2.7GB** | **~3** | **~6GB** |

## Consequences

### Positive
- **Simple deployment** — `docker compose up -d` starts everything.
- **Development parity** — same environment on every machine.
- **Dependency management** — health checks ensure correct startup order.
- **Isolation** — each service runs in its own container with its own filesystem.
- **Reproducibility** — Dockerfiles and docker-compose.yml are version-controlled.
- **Easy cleanup** — `docker compose down -v` removes everything cleanly.
- **Port mapping** — services accessible via localhost on mapped ports.

### Negative
- **Single machine limitation** — all services run on one machine, no distribution.
- **No auto-scaling** — cannot automatically add instances under load.
- **Build time** — first `docker compose up --build` takes several minutes (mitigated by Docker layer caching).
- **Image size** — Java service images are ~200MB each (mitigated by multi-stage builds with Alpine).
- **Docker dependency** — requires Docker Desktop or Docker Engine installed on development machines.
- **Debugging complexity** — debugging inside containers is harder than local processes (mitigated by `docker compose logs` and remote debugging).

## Related

- ADR-001: Architecture Style — SBA (all services deployed together)
- ADR-004: Database — PostgreSQL (containerized)
- ADR-005: Message Broker — RabbitMQ (containerized)
- ADR-006: API Gateway — Spring Cloud Gateway (containerized)
- ADR-007: Service Discovery — Eureka (containerized)
- ADR-008: Frontend — React (containerized with Nginx)
- Docker Compose Documentation: https://docs.docker.com/compose/
