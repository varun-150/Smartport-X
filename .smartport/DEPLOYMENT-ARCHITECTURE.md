# SmartPortX — Deployment Architecture

> **Version:** 1.0  
> **Status:** Active  
> **Last Updated:** 2026-08-26  
> **Scope:** Local development, demo/evaluation, and future production deployment configurations

---

## Table of Contents

1. [Overview](#1-overview)
2. [Deployment Model](#2-deployment-model)
   - 2.1 [Local Development](#21-local-development)
   - 2.2 [Demo / Evaluation](#22-demo--evaluation)
   - 2.3 [Production (Future)](#23-production-future)
3. [Docker Compose Architecture](#3-docker-compose-architecture)
   - 3.1 [Complete docker-compose.yml](#31-complete-docker-composeyml)
   - 3.2 [Service Dependency Graph](#32-service-dependency-graph)
   - 3.3 [Infrastructure Services](#33-infrastructure-services)
   - 3.4 [Application Services](#34-application-services)
   - 3.5 [Frontend Service](#35-frontend-service)
   - 3.6 [Monitoring Stack](#36-monitoring-stack)
4. [Dockerfile Pattern](#4-dockerfile-pattern)
   - 4.1 [Multi-Stage Build (Java Services)](#41-multi-stage-build-java-services)
   - 4.2 [Frontend Dockerfile (React)](#42-frontend-dockerfile-react)
   - 4.3 [Eureka Server Dockerfile](#43-eureka-server-dockerfile)
   - 4.4 [Docker Best Practices](#44-docker-best-practices)
5. [Environment Configuration](#5-environment-configuration)
   - 5.1 [Spring Profiles](#51-spring-profiles)
   - 5.2 [Environment Variables](#52-environment-variables)
   - 5.3 [12-Factor App Compliance](#53-12-factor-app-compliance)
   - 5.4 [Secrets Management](#54-secrets-management)
6. [CI/CD Pipeline](#6-cicd-pipeline)
   - 6.1 [GitHub Actions Workflow](#61-github-actions-workflow)
   - 6.2 [Build Stage](#62-build-stage)
   - 6.3 [Test Stage](#63-test-stage)
   - 6.4 [Security Scan Stage](#64-security-scan-stage)
   - 6.5 [Docker Build Stage](#65-docker-build-stage)
   - 6.6 [Deploy Stage](#66-deploy-stage)
   - 6.7 [Pipeline Diagram](#67-pipeline-diagram)
7. [Database Migration](#7-database-migration)
   - 7.1 [Flyway Configuration](#71-flyway-configuration)
   - 7.2 [Migration Script Standards](#72-migration-script-standards)
   - 7.3 [Per-Service Migrations](#73-per-service-migrations)
   - 7.4 [Migration Examples](#74-migration-examples)
8. [Configuration Management](#8-configuration-management)
   - 8.1 [Profile Strategy](#81-profile-strategy)
   - 8.2 [Configuration Hierarchy](#82-configuration-hierarchy)
   - 8.3 [Config Refresh Policy](#83-config-refresh-policy)
9. [Network Architecture](#9-network-architecture)
   - 9.1 [Docker Network Topology](#91-docker-network-topology)
   - 9.2 [Port Mapping](#92-port-mapping)
   - 9.3 [Inter-Service Communication](#93-inter-service-communication)
   - 9.4 [Network Security](#94-network-security)
10. [Service Startup Order](#10-service-startup-order)
11. [Health Checks](#11-health-checks)
12. [Logging Architecture](#12-logging-architecture)
13. [Troubleshooting Guide](#13-troubleshooting-guide)

---

## 1. Overview

SmartPortX uses a container-first deployment strategy. All 8 backend microservices,
the React frontend, and supporting infrastructure run as Docker containers orchestrated
by Docker Compose. This document covers the complete deployment architecture from local
development through evaluation and toward production readiness.

**Deployment Principles:**
- **Reproducibility:** Same containers run in development, demo, and production
- **Isolation:** Each service has its own database schema and process
- **Observability:** Centralized logging, metrics, and distributed tracing
- **Simplicity:** Single `docker-compose up` starts the entire platform
- **Security:** No secrets in source code, environment-based configuration

---

## 2. Deployment Model

### 2.1 Local Development

| Aspect | Configuration |
|---|---|
| **Orchestration** | Docker Compose (single `docker-compose.yml`) |
| **Services** | All 8 microservices + infrastructure + monitoring |
| **Database** | Single PostgreSQL 17 instance, separate schemas per service |
| **Message Broker** | RabbitMQ 3.x with management console |
| **Service Discovery** | Eureka Server (custom image) |
| **Frontend** | React dev server with hot reload (Vite) |
| **Resource Requirements** | 8 GB RAM, 4 CPU cores, 20 GB disk |

**Quick Start:**

```bash
# Clone repository
git clone https://github.com/smartportx/smartportx-platform.git
cd smartportx-platform

# Set environment variables
cp .env.example .env
# Edit .env with local passwords

# Start all services
docker-compose up -d

# Verify health
docker-compose ps

# Access services
# Dashboard:    http://localhost:3000
# API Gateway:  http://localhost:8080
# Eureka:       http://localhost:8761
# RabbitMQ:     http://localhost:15672
# Grafana:      http://localhost:3001
# Jaeger:       http://localhost:16686
```

### 2.2 Demo / Evaluation

| Aspect | Configuration |
|---|---|
| **Orchestration** | Docker Compose (same `docker-compose.yml`) |
| **Target** | Single machine (laptop or VM) |
| **Data** | Pre-loaded sample data (seed scripts) |
| **IoT** | Simulation mode (default) |
| **Customs** | Simulated customs workflow |
| **Resource Requirements** | 8 GB RAM, 4 CPU cores, 20 GB disk |

**Demo Seed Data:**

```bash
# Run seed scripts after services are healthy
docker-compose exec integration python /app/scripts/seed_data.py
docker-compose exec yard python /app/scripts/seed_yard_layout.py
```

### 2.3 Production (Future)

| Aspect | Configuration |
|---|---|
| **Orchestration** | Kubernetes (EKS / GKE / AKS) |
| **Container Registry** | GitHub Container Registry (GHCR) |
| **Database** | Managed PostgreSQL (RDS / Cloud SQL) |
| **Message Broker** | Managed RabbitMQ or Amazon SQS |
| **Service Discovery** | Kubernetes Service DNS (replaces Eureka) |
| **Ingress** | NGINX Ingress Controller / AWS ALB |
| **Monitoring** | Prometheus + Grafana + Jaeger (on K8s) |
| **Auto-scaling** | HPA (Horizontal Pod Autoscaler) |

---

## 3. Docker Compose Architecture

### 3.1 Complete docker-compose.yml

```yaml
version: "3.9"

# =============================================================================
# SmartPortX — Docker Compose (Local Development & Demo)
# =============================================================================

services:

  # ---------------------------------------------------------------------------
  # Infrastructure Services
  # ---------------------------------------------------------------------------

  postgres:
    image: postgres:17.11
    container_name: smartportx-postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./scripts/init-databases.sh:/docker-entrypoint-initdb.d/01-init-databases.sh:ro
    environment:
      POSTGRES_DB: smartportx
      POSTGRES_USER: smartportx
      POSTGRES_PASSWORD: ${DB_PASSWORD:-smartportx_local}
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U smartportx"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - smartportx-network

  rabbitmq:
    image: rabbitmq:3-management
    container_name: smartportx-rabbitmq
    ports:
      - "5672:5672"
      - "15672:15672"
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USERNAME:-guest}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-guest}
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "check_port_connectivity"]
      interval: 15s
      timeout: 10s
      retries: 5
    networks:
      - smartportx-network

  eureka:
    build:
      context: ./smartportx-eureka
      dockerfile: Dockerfile
    container_name: smartportx-eureka
    ports:
      - "8761:8761"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVICE_PORT: 8761
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 30s
    networks:
      - smartportx-network

  # ---------------------------------------------------------------------------
  # Application Services
  # ---------------------------------------------------------------------------

  gateway:
    build:
      context: ./smartportx-gateway
      dockerfile: Dockerfile
    container_name: smartportx-gateway
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVICE_PORT: 8080
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka:8761/eureka
      JWT_SECRET: ${JWT_SECRET:-local-dev-secret-change-in-production}
      JWT_EXPIRATION: ${JWT_EXPIRATION:86400}
    depends_on:
      eureka:
        condition: service_healthy
      identity:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 30s
    networks:
      - smartportx-network

  identity:
    build:
      context: ./smartportx-identity
      dockerfile: Dockerfile
    container_name: smartportx-identity
    ports:
      - "8081:8081"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVICE_PORT: 8081
      DATABASE_URL: jdbc:postgresql://postgres:5432/smartportx_identity
      DATABASE_USERNAME: ${DATABASE_USERNAME:-smartportx}
      DATABASE_PASSWORD: ${DB_PASSWORD:-smartportx_local}
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_USERNAME: ${RABBITMQ_USERNAME:-guest}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-guest}
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka:8761/eureka
      JWT_SECRET: ${JWT_SECRET:-local-dev-secret-change-in-production}
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 45s
    networks:
      - smartportx-network

  yard:
    build:
      context: ./smartportx-yard
      dockerfile: Dockerfile
    container_name: smartportx-yard
    ports:
      - "8082:8082"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVICE_PORT: 8082
      DATABASE_URL: jdbc:postgresql://postgres:5432/smartportx_yard
      DATABASE_USERNAME: ${DATABASE_USERNAME:-smartportx}
      DATABASE_PASSWORD: ${DB_PASSWORD:-smartportx_local}
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_USERNAME: ${RABBITMQ_USERNAME:-guest}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-guest}
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka:8761/eureka
      JWT_SECRET: ${JWT_SECRET:-local-dev-secret-change-in-production}
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka:
        condition: service_healthy
      container:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8082/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 45s
    networks:
      - smartportx-network

  gate:
    build:
      context: ./smartportx-gate
      dockerfile: Dockerfile
    container_name: smartportx-gate
    ports:
      - "8083:8083"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVICE_PORT: 8083
      DATABASE_URL: jdbc:postgresql://postgres:5432/smartportx_gate
      DATABASE_USERNAME: ${DATABASE_USERNAME:-smartportx}
      DATABASE_PASSWORD: ${DB_PASSWORD:-smartportx_local}
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_USERNAME: ${RABBITMQ_USERNAME:-guest}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-guest}
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka:8761/eureka
      JWT_SECRET: ${JWT_SECRET:-local-dev-secret-change-in-production}
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka:
        condition: service_healthy
      container:
        condition: service_healthy
      yard:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8083/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 45s
    networks:
      - smartportx-network

  container:
    build:
      context: ./smartportx-container
      dockerfile: Dockerfile
    container_name: smartportx-container
    ports:
      - "8084:8084"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVICE_PORT: 8084
      DATABASE_URL: jdbc:postgresql://postgres:5432/smartportx_container
      DATABASE_USERNAME: ${DATABASE_USERNAME:-smartportx}
      DATABASE_PASSWORD: ${DB_PASSWORD:-smartportx_local}
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_USERNAME: ${RABBITMQ_USERNAME:-guest}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-guest}
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka:8761/eureka
      JWT_SECRET: ${JWT_SECRET:-local-dev-secret-change-in-production}
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8084/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 45s
    networks:
      - smartportx-network

  vessel:
    build:
      context: ./smartportx-vessel
      dockerfile: Dockerfile
    container_name: smartportx-vessel
    ports:
      - "8085:8085"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVICE_PORT: 8085
      DATABASE_URL: jdbc:postgresql://postgres:5432/smartportx_vessel
      DATABASE_USERNAME: ${DATABASE_USERNAME:-smartportx}
      DATABASE_PASSWORD: ${DB_PASSWORD:-smartportx_local}
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_USERNAME: ${RABBITMQ_USERNAME:-guest}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-guest}
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka:8761/eureka
      JWT_SECRET: ${JWT_SECRET:-local-dev-secret-change-in-production}
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka:
        condition: service_healthy
      container:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8085/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 45s
    networks:
      - smartportx-network

  integration:
    build:
      context: ./smartportx-integration
      dockerfile: Dockerfile
    container_name: smartportx-integration
    ports:
      - "8086:8086"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVICE_PORT: 8086
      DATABASE_URL: jdbc:postgresql://postgres:5432/smartportx_integration
      DATABASE_USERNAME: ${DATABASE_USERNAME:-smartportx}
      DATABASE_PASSWORD: ${DB_PASSWORD:-smartportx_local}
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_USERNAME: ${RABBITMQ_USERNAME:-guest}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-guest}
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka:8761/eureka
      JWT_SECRET: ${JWT_SECRET:-local-dev-secret-change-in-production}
      IOT_MODE: simulation
      CUSTOMS_CLEARANCE_RATE: 0.7
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      eureka:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8086/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 45s
    networks:
      - smartportx-network

  notification:
    build:
      context: ./smartportx-notification
      dockerfile: Dockerfile
    container_name: smartportx-notification
    ports:
      - "8087:8087"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVICE_PORT: 8087
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_USERNAME: ${RABBITMQ_USERNAME:-guest}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-guest}
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka:8761/eureka
    depends_on:
      rabbitmq:
        condition: service_healthy
      eureka:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8087/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 45s
    networks:
      - smartportx-network

  # ---------------------------------------------------------------------------
  # Frontend
  # ---------------------------------------------------------------------------

  dashboard:
    build:
      context: ./smartportx-dashboard
      dockerfile: Dockerfile
    container_name: smartportx-dashboard
    ports:
      - "3000:3000"
    environment:
      VITE_API_BASE_URL: http://localhost:8080
    depends_on:
      gateway:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3000"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 30s
    networks:
      - smartportx-network

  # ---------------------------------------------------------------------------
  # Monitoring & Observability
  # ---------------------------------------------------------------------------

  prometheus:
    image: prom/prometheus:v2.53.0
    container_name: smartportx-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./config/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    networks:
      - smartportx-network

  grafana:
    image: grafana/grafana:11.1.0
    container_name: smartportx-grafana
    ports:
      - "3001:3000"
    volumes:
      - grafana-data:/var/lib/grafana
      - ./config/grafana/provisioning:/etc/grafana/provisioning:ro
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-admin}
      GF_USERS_ALLOW_SIGN_UP: "false"
    depends_on:
      - prometheus
    networks:
      - smartportx-network

  jaeger:
    image: jaegertracing/all-in-one:1.59
    container_name: smartportx-jaeger
    ports:
      - "16686:16686"
      - "6831:6831/udp"
      - "14268:14268"
    environment:
      COLLECTOR_OTLP_ENABLED: "true"
    networks:
      - smartportx-network

# =============================================================================
# Networks
# =============================================================================
networks:
  smartportx-network:
    driver: bridge
    name: smartportx-network

# =============================================================================
# Volumes
# =============================================================================
volumes:
  postgres-data:
    name: smartportx-postgres-data
  rabbitmq-data:
    name: smartportx-rabbitmq-data
  prometheus-data:
    name: smartportx-prometheus-data
  grafana-data:
    name: smartportx-grafana-data
```

### 3.2 Service Dependency Graph

```
Infrastructure Layer:
┌──────────┐    ┌──────────┐
│ postgres │    │ rabbitmq │
└────┬─────┘    └────┬─────┘
     │               │
     └───────┬───────┘
             │
Discovery Layer:
        ┌────▼─────┐
        │  eureka  │
        └────┬─────┘
             │
Application Layer:
             │
     ┌───────┼───────────────────────┐
     │       │                       │
┌────▼───┐ ┌─▼─────────┐ ┌─────────▼───┐
│identity│ │container   │ │notification │
└────┬───┘ └─┬─────────┘ └─────────────┘
     │       │
     │   ┌───┼──────────┐
     │   │              │
┌────▼───┐ ┌──▼───────┐ ┌────────────┐
│gateway │ │  vessel   │ │integration │
└────────┘ └──────────┘ └────────────┘
                  │
           ┌──────┼──────┐
           │             │
      ┌────▼───┐  ┌──────▼──┐
      │  yard  │  │   gate  │
      └────────┘  └─────────┘
             │
Frontend Layer:
        ┌────▼──────┐
        │ dashboard │
        └───────────┘

Monitoring Layer:
┌───────────┐  ┌─────────┐  ┌────────┐
│ prometheus│  │ grafana  │  │ jaeger │
└───────────┘  └─────────┘  └────────┘
```

### 3.3 Infrastructure Services

#### PostgreSQL 17

| Property | Value |
|---|---|
| Image | `postgres:17.11` |
| Port | 5432 |
| Data Volume | `smartportx-postgres-data` |
| Schemas | 8 (one per service) |

**Database Initialization Script:**

```bash
#!/bin/bash
# scripts/init-databases.sh
# Creates separate schemas for each microservice

set -e
set -u

POSTGRES_DB="${POSTGRES_DB:-smartportx}"
POSTGRES_USER="${POSTGRES_USER:-smartportx}"

echo "Creating service databases..."

# Each service gets its own database for isolation
for SERVICE in identity yard gate container vessel integration notification; do
    DB_NAME="smartportx_${SERVICE}"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        SELECT 'CREATE DATABASE $DB_NAME'
        WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$DB_NAME')\gexec
        GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $POSTGRES_USER;
EOSQL
    echo "  Database: $DB_NAME ready"
done

echo "All service databases created."
```

**Schema Per Service:**

| Database | Service | Description |
|---|---|---|
| `smartportx_identity` | Identity Service | Users, roles, permissions, JWT tokens |
| `smartportx_yard` | Yard Service | Yard layout, slots, container positions |
| `smartportx_gate` | Gate Service | Gate transactions, appointments, validation |
| `smartportx_container` | Container Service | Container registry, status, history |
| `smartportx_vessel` | Vessel Service | Vessels, voyages, stowage plans |
| `smartportx_integration` | Integration Service | EDIFACT messages, IoT readings, customs |
| `smartportx_notification` | Notification Service | Notification templates, delivery log |

#### RabbitMQ 3

| Property | Value |
|---|---|
| Image | `rabbitmq:3-management` |
| AMQP Port | 5672 |
| Management Port | 15672 |
| Data Volume | `smartportx-rabbitmq-data` |
| Management URL | http://localhost:15672 |

**RabbitMQ Exchanges:**

| Exchange | Type | Purpose |
|---|---|---|
| `smartportx.events` | topic | Main event bus for domain events |
| `smartportx.dlx` | fanout | Dead-letter exchange for failed messages |

**RabbitMQ Queues:**

| Queue | Consumer Service | Binding Key |
|---|---|---|
| `yard.container.discharge` | Yard Service | `event.container.discharge.*` |
| `yard.container.load` | Yard Service | `event.container.load.*` |
| `yard.iot.location` | Yard Service | `event.iot.gps` |
| `gate.container.arrival` | Gate Service | `event.container.arrival` |
| `gate.container.departure` | Gate Service | `event.container.departure` |
| `gate.customs.status` | Gate Service | `event.customs.status.*` |
| `container.status.*` | Container Service | `event.container.*` |
| `vessel.*` | Vessel Service | `event.vessel.*` |
| `notification.*` | Notification Service | `event.notification.*` |
| `smartportx.dlq.integration` | Manual review | Dead-letter queue |

#### Eureka Server

| Property | Value |
|---|---|
| Image | Custom build (`./smartportx-eureka`) |
| Port | 8761 |
| Purpose | Service discovery and registration |

**Eureka Configuration:**

```yaml
# smartportx-eureka/src/main/resources/application.yml
server:
  port: ${SERVICE_PORT:8761}

spring:
  application:
    name: eureka-server

eureka:
  instance:
    hostname: eureka
  client:
    register-with-eureka: false
    fetch-registry: false
  server:
    enable-self-preservation: false
    eviction-interval-timer-in-ms: 30000
```

### 3.4 Application Services

| Service | Port | Database | Depends On |
|---|---|---|---|
| `smartportx-gateway` | 8080 | None | eureka, identity |
| `smartportx-identity` | 8081 | smartportx_identity | postgres, rabbitmq, eureka |
| `smartportx-yard` | 8082 | smartportx_yard | postgres, rabbitmq, eureka, container |
| `smartportx-gate` | 8083 | smartportx_gate | postgres, rabbitmq, eureka, container, yard |
| `smartportx-container` | 8084 | smartportx_container | postgres, rabbitmq, eureka |
| `smartportx-vessel` | 8085 | smartportx_vessel | postgres, rabbitmq, eureka, container |
| `smartportx-integration` | 8086 | smartportx_integration | postgres, rabbitmq, eureka |
| `smartportx-notification` | 8087 | None (in-memory) | rabbitmq, eureka |

### 3.5 Frontend Service

| Property | Value |
|---|---|
| Image | Custom build (`./smartportx-dashboard`) |
| Port | 3000 |
| Framework | React 18+ with TypeScript |
| Build Tool | Vite |
| API Target | Gateway (port 8080) |

### 3.6 Monitoring Stack

| Tool | Port | Purpose |
|---|---|---|
| Prometheus | 9090 | Metrics collection and alerting rules |
| Grafana | 3001 | Dashboard visualization |
| Jaeger | 16686 | Distributed tracing UI |

---

## 4. Dockerfile Pattern

### 4.1 Multi-Stage Build (Java Services)

Every Java microservice uses the same multi-stage Dockerfile pattern:

```dockerfile
# =============================================================================
# SmartPortX — Java Microservice Dockerfile (Multi-Stage Build)
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Build
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build

# Install Maven wrapper dependencies
WORKDIR /app
COPY pom.xml .
COPY .mvn ./.mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN ./mvnw package -DskipTests -B

# ---------------------------------------------------------------------------
# Stage 2: Runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre

# Security: Run as non-root user
RUN groupadd -r smartportx && useradd -r -g smartportx smartportx

WORKDIR /app

# Copy built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Change ownership
RUN chown -R smartportx:smartportx /app

# Switch to non-root user
USER smartportx

# Expose service port (overridden per service)
EXPOSE 8081

# Health check
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD curl -f http://localhost:${SERVICE_PORT:-8081}/actuator/health || exit 1

# Entrypoint
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:InitialRAMPercentage=50.0", \
    "-jar", "app.jar"]
```

### 4.2 Frontend Dockerfile (React)

```dockerfile
# =============================================================================
# SmartPortX — React Dashboard Dockerfile (Multi-Stage Build)
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Build
# ---------------------------------------------------------------------------
FROM node:20-alpine AS build

WORKDIR /app

# Install dependencies
COPY package.json package-lock.json ./
RUN npm ci --no-audit

# Copy source and build
COPY . .
RUN npm run build

# ---------------------------------------------------------------------------
# Stage 2: Serve (Nginx)
# ---------------------------------------------------------------------------
FROM nginx:alpine

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Copy custom nginx config
COPY nginx.conf /etc/nginx/conf.d/smartportx.conf

# Copy built assets from build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Security: Run as non-root
RUN chown -R nginx:nginx /usr/share/nginx/html

EXPOSE 3000

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:3000 || exit 1

CMD ["nginx", "-g", "daemon off;"]
```

**Nginx Configuration:**

```nginx
# smartportx-dashboard/nginx.conf
server {
    listen 3000;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    # SPA routing
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API proxy (optional, for direct access without gateway)
    location /api/ {
        proxy_pass http://gateway:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 4.3 Eureka Server Dockerfile

```dockerfile
# =============================================================================
# SmartPortX — Eureka Server Dockerfile
# =============================================================================
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn ./.mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd -r smartportx && useradd -r -g smartportx smartportx
COPY --from=build /app/target/*.jar app.jar
RUN chown -R smartportx:smartportx /app
USER smartportx
EXPOSE 8761
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=10 \
    CMD curl -f http://localhost:8761/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]
```

### 4.4 Docker Best Practices

| Practice | Implementation |
|---|---|
| Multi-stage builds | Separate build and runtime stages, smaller final images |
| Non-root user | `useradd -r smartportx` and `USER smartportx` |
| Health checks | `HEALTHCHECK` directive in every Dockerfile |
| Layer caching | Dependencies copied before source code |
| Minimal base | `eclipse-temurin:25-jre` for runtime (not JDK) |
| No secrets in image | Environment variables only, never baked in |
| .dockerignore | Excludes `.git`, `target/`, `node_modules/`, `*.md` |
| Specific tags | `postgres:17.11` not `postgres:latest` |

**.dockerignore (Java Services):**

```
.git
.gitignore
target/
*.md
*.log
.idea/
*.iml
.env
```

---

## 5. Environment Configuration

### 5.1 Spring Profiles

| Profile | Environment | Database | IoT Mode | Purpose |
|---|---|---|---|---|
| `local` | Local Docker Compose | Docker PostgreSQL | Simulation | Development |
| `docker` | Docker Compose | Docker PostgreSQL | Simulation | Demo/evaluation |
| `production` | Kubernetes | Managed PostgreSQL | Live | Production (future) |

### 5.2 Environment Variables

**Infrastructure:**

| Variable | Example | Description |
|---|---|---|
| `DB_PASSWORD` | `smartportx_local` | PostgreSQL password |
| `RABBITMQ_USERNAME` | `guest` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ password |

**Application (per service):**

| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `docker` | Active Spring profile |
| `SERVICE_PORT` | `808x` | Service HTTP port |
| `DATABASE_URL` | `jdbc:postgresql://postgres:5432/smartportx_{svc}` | Database connection URL |
| `DATABASE_USERNAME` | `smartportx` | Database username |
| `DATABASE_PASSWORD` | `${DB_PASSWORD}` | Database password |
| `RABBITMQ_HOST` | `rabbitmq` | RabbitMQ hostname |
| `RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port |
| `RABBITMQ_USERNAME` | `${RABBITMQ_USERNAME}` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `${RABBITMQ_PASSWORD}` | RabbitMQ password |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://eureka:8761/eureka` | Eureka server URL |
| `JWT_SECRET` | (required) | JWT signing secret |
| `JWT_EXPIRATION` | `86400` | JWT token expiry (seconds) |

**Integration Service Specific:**

| Variable | Default | Description |
|---|---|---|
| `IOT_MODE` | `simulation` | IoT mode: `simulation` or `live` |
| `SIM_CONTAINER_COUNT` | `50` | Number of simulated containers |
| `SIM_TICK_INTERVAL` | `30` | Simulation tick interval (seconds) |
| `CUSTOMS_CLEARANCE_RATE` | `0.7` | Simulated customs clearance rate |
| `RETRY_MAX_ATTEMPTS` | `3` | Max retry attempts for integration |

### 5.3 12-Factor App Compliance

| Factor | Implementation |
|---|---|
| I. Codebase | One repo, many services (monorepo) |
| II. Dependencies | Maven for Java, npm for React — declared explicitly |
| III. Config | Environment variables (never hardcoded) |
| IV. Backing Services | PostgreSQL, RabbitMQ, Eureka as attached resources |
| V. Build/Release/Run | CI builds Docker image, Compose runs it |
| VI. Processes | Stateless services, state in databases/volumes |
| VII. Port Binding | Each service exports via port binding |
| VIII. Concurrency | Horizontal scaling via multiple container instances |
| IX. Disposability | Fast startup, graceful shutdown (Spring Boot) |
| X. Dev/Prod Parity | Same Docker images across environments |
| XI. Logs | stdout/stderr → Docker log driver → centralized |
| XII. Admin Processes | One-off tasks via `docker-compose run` |

### 5.4 Secrets Management

| Environment | Strategy |
|---|---|
| Local Development | `.env` file (gitignored), defaults in `docker-compose.yml` |
| Demo/Evaluation | `.env` file on deployment machine |
| Production (Future) | Kubernetes Secrets or HashiCorp Vault |

**`.env.example`:**

```bash
# =============================================================================
# SmartPortX — Environment Variables (copy to .env and customize)
# =============================================================================

# Database
DB_PASSWORD=change_me_in_production

# RabbitMQ
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=change_me_in_production

# JWT
JWT_SECRET=change_me_to_a_long_random_string_at_least_32_chars
JWT_EXPIRATION=86400

# Integration
IOT_MODE=simulation
CUSTOMS_CLEARANCE_RATE=0.7

# Monitoring
GRAFANA_PASSWORD=admin
```

**`.gitignore` entry:**

```
.env
.env.local
.env.production
```

---

## 6. CI/CD Pipeline

### 6.1 GitHub Actions Workflow

```yaml
# .github/workflows/ci.yml
name: SmartPortX CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_PREFIX: ${{ github.repository_owner }}/smartportx

jobs:
  # ===========================================================================
  # Stage 1: Build
  # ===========================================================================
  build:
    name: Build
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: "25"
          distribution: "temurin"
          cache: "maven"

      - name: Build all modules
        run: ./mvnw compile -B -T 1C

  # ===========================================================================
  # Stage 2: Test
  # ===========================================================================
  test:
    name: Test
    needs: build
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:17.11
        env:
          POSTGRES_DB: smartportx_test
          POSTGRES_USER: smartportx
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
      rabbitmq:
        image: rabbitmq:3
        ports:
          - 5672:5672
        options: >-
          --health-cmd "rabbitmq-diagnostics check_port_connectivity"
          --health-interval 15s
          --health-timeout 10s
          --health-retries 5
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: "25"
          distribution: "temurin"
          cache: "maven"

      - name: Run unit tests
        run: ./mvnw test -B -T 1C
        env:
          DATABASE_URL: jdbc:postgresql://localhost:5432/smartportx_test
          DATABASE_USERNAME: smartportx
          DATABASE_PASSWORD: test
          RABBITMQ_HOST: localhost
          RABBITMQ_PORT: 5672
          RABBITMQ_USERNAME: guest
          RABBITMQ_PASSWORD: guest

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: "**/target/surefire-reports/"

  # ===========================================================================
  # Stage 3: Security Scan
  # ===========================================================================
  security:
    name: Security Scan
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: "25"
          distribution: "temurin"
          cache: "maven"

      - name: Run OWASP dependency check
        run: ./mvnw dependency-check:check -B
        continue-on-error: true

      - name: Upload security report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: dependency-check-report
          path: "**/target/dependency-check-report.html"

  # ===========================================================================
  # Stage 4: Docker Build
  # ===========================================================================
  docker-build:
    name: Docker Build
    needs: [test, security]
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    strategy:
      matrix:
        service:
          - smartportx-eureka
          - smartportx-gateway
          - smartportx-identity
          - smartportx-yard
          - smartportx-gate
          - smartportx-container
          - smartportx-vessel
          - smartportx-integration
          - smartportx-notification
          - smartportx-dashboard
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/${{ matrix.service }}
          tags: |
            type=sha,prefix=
            type=ref,event=branch

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: ./${{ matrix.service }}
          push: ${{ github.event_name != 'pull_request' }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}

  # ===========================================================================
  # Stage 5: Deploy
  # ===========================================================================
  deploy:
    name: Deploy (Docker Compose)
    needs: docker-build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Pull latest images
        run: docker-compose pull

      - name: Deploy with Docker Compose
        run: |
          docker-compose up -d --remove-orphans
          docker-compose ps

      - name: Verify health
        run: |
          sleep 30
          ./scripts/health-check.sh
```

### 6.2 Build Stage

- Compiles all Java modules with `mvn compile`
- Uses Maven parallel execution (`-T 1C`)
- Maven cache persisted via `actions/setup-java` cache
- Fast feedback: build failures block downstream stages

### 6.3 Test Stage

- Runs after successful build
- Unit tests and integration tests via `mvn test`
- PostgreSQL and RabbitMQ available as service containers
- Test reports uploaded as artifacts for review
- Parallel execution across modules

### 6.4 Security Scan Stage

- OWASP Dependency Check scans for vulnerable dependencies
- Runs in parallel with test stage
- Report uploaded as artifact (non-blocking with `continue-on-error`)
- Future: add SAST (SpotBugs, SonarQube) and container scanning (Trivy)

### 6.5 Docker Build Stage

- Builds Docker images for each service independently
- Uses matrix strategy for parallel builds (10 services)
- Images tagged with git SHA and branch name
- Pushed to GitHub Container Registry (GHCR) on main branch
- PR builds: build only, no push

### 6.6 Deploy Stage

- Only runs on push to `main` (not on PRs)
- Pulls latest images from GHCR
- Runs `docker-compose up -d` to deploy
- Health check script verifies all services are up

### 6.7 Pipeline Diagram

```
Push to main / PR to main
         │
         ▼
    ┌─────────┐
    │  Build   │ (mvn compile)
    └────┬────┘
         │
    ┌────┴────────────────┐
    │                     │
    ▼                     ▼
┌────────┐         ┌──────────┐
│  Test  │         │ Security │  (parallel)
└───┬────┘         └────┬─────┘
    │                   │
    └────────┬──────────┘
             │
             ▼
    ┌────────────────┐
    │ Docker Build   │ (10 parallel builds)
    └───────┬────────┘
            │
       ┌────┴────┐
       │ Push?   │
       └────┬────┘
        Yes │ (main only)
            ▼
    ┌────────────────┐
    │ Deploy         │ (docker-compose up)
    └────────────────┘
```

---

## 7. Database Migration

### 7.1 Flyway Configuration

Each microservice uses Flyway for automated schema migration on startup:

```yaml
# application.yml (shared across all services)
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 0
    validate-on-migrate: true
    out-of-order: false
```

### 7.2 Migration Script Standards

**Naming Convention:**

```
V{version}__{description}.sql
```

- `V` prefix for versioned migrations
- `{version}` is a sequential number (no decimals in Flyway Community)
- Double underscore `__` separates version from description
- Description uses snake_case

**Directory Structure:**

```
smartportx-{service}/
└── src/
    └── main/
        └── resources/
            └── db/
                └── migration/
                    ├── V1__create_users_table.sql
                    ├── V2__add_roles_table.sql
                    ├── V3__create_permissions_table.sql
                    └── V4__add_user_roles_foreign_key.sql
```

### 7.3 Per-Service Migrations

| Service | Migration Directory | Schema Target |
|---|---|---|
| smartportx-identity | `src/main/resources/db/migration/` | `smartportx_identity` |
| smartportx-yard | `src/main/resources/db/migration/` | `smartportx_yard` |
| smartportx-gate | `src/main/resources/db/migration/` | `smartportx_gate` |
| smartportx-container | `src/main/resources/db/migration/` | `smartportx_container` |
| smartportx-vessel | `src/main/resources/db/migration/` | `smartportx_vessel` |
| smartportx-integration | `src/main/resources/db/migration/` | `smartportx_integration` |
| smartportx-notification | `src/main/resources/db/migration/` | `smartportx_notification` |

Each service runs its own migrations against its own database schema on startup.
No shared migration scripts between services.

### 7.4 Migration Examples

**V1 — Identity Service:**

```sql
-- V1__create_users_table.sql
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50) NOT NULL UNIQUE,
    email           VARCHAR(100) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(50) NOT NULL,
    last_name       VARCHAR(50) NOT NULL,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
```

**V1 — Container Service:**

```sql
-- V1__create_containers_table.sql
CREATE TABLE containers (
    id                  BIGSERIAL PRIMARY KEY,
    container_number    VARCHAR(11) NOT NULL UNIQUE,
    iso_code            VARCHAR(4) NOT NULL,
    carrier_code        VARCHAR(10),
    status              VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    current_location    VARCHAR(50),
    weight_kg           DECIMAL(10,2),
    tare_weight_kg      DECIMAL(10,2),
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_containers_number ON containers(container_number);
CREATE INDEX idx_containers_status ON containers(status);
```

**V1 — Integration Service:**

```sql
-- V1__create_edifact_messages_table.sql
CREATE TABLE edifact_messages (
    id              BIGSERIAL PRIMARY KEY,
    message_id      VARCHAR(50) NOT NULL,
    message_type    VARCHAR(10) NOT NULL,
    sender          VARCHAR(50) NOT NULL,
    receiver        VARCHAR(50) NOT NULL,
    direction       VARCHAR(10) NOT NULL,
    raw_payload     TEXT NOT NULL,
    parsed_data     JSONB,
    status          VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    error_message   TEXT,
    correlation_id  VARCHAR(36),
    received_at     TIMESTAMP NOT NULL,
    processed_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_edifact_messages_type ON edifact_messages(message_type);
CREATE INDEX idx_edifact_messages_status ON edifact_messages(status);
```

---

## 8. Configuration Management

### 8.1 Profile Strategy

| Profile | File | Purpose |
|---|---|---|
| `application.yml` | Base config | Shared defaults across all environments |
| `application-local.yml` | Local overrides | localhost ports, dev settings |
| `application-docker.yml` | Docker overrides | Docker DNS names, container ports |
| `application-production.yml` | Production overrides | Managed services, tuned settings |

### 8.2 Configuration Hierarchy

```
Environment Variables (highest priority)
        │
        ▼
application-{profile}.yml
        │
        ▼
application.yml (base defaults)
```

Spring Boot resolution order:
1. Environment variables (override everything)
2. Active profile YAML (`application-{profile}.yml`)
3. Base YAML (`application.yml`)

### 8.3 Config Refresh Policy

| Setting | Policy |
|---|---|
| Dynamic config refresh | **Not supported** in MVP |
| Config changes | Require service restart |
| Future: Spring Cloud Config | Centralized config server with `/actuator/refresh` |
| Future: Kubernetes ConfigMaps | Native K8s config management |

---

## 9. Network Architecture

### 9.1 Docker Network Topology

All services run on a single Docker bridge network:

```
Network: smartportx-network (bridge, 172.18.0.0/16)
```

```
┌─────────────────────────────────────────────────────────────┐
│                    smartportx-network                        │
│                                                             │
│  Infrastructure:        Application:        Monitoring:     │
│  ┌──────────────┐      ┌──────────────┐    ┌────────────┐  │
│  │ postgres     │      │ gateway      │    │ prometheus │  │
│  │ 172.18.0.2   │      │ 172.18.0.10  │    │ 172.18.0.20│  │
│  ├──────────────┤      ├──────────────┤    ├────────────┤  │
│  │ rabbitmq     │      │ identity     │    │ grafana    │  │
│  │ 172.18.0.3   │      │ 172.18.0.11  │    │ 172.18.0.21│  │
│  ├──────────────┤      ├──────────────┤    ├────────────┤  │
│  │ eureka       │      │ yard         │    │ jaeger     │  │
│  │ 172.18.0.4   │      │ 172.18.0.12  │    │ 172.18.0.22│  │
│  └──────────────┘      ├──────────────┤    └────────────┘  │
│                        │ gate         │                     │
│  Frontend:             │ 172.18.0.13  │                     │
│  ┌──────────────┐      ├──────────────┤                     │
│  │ dashboard    │      │ container    │                     │
│  │ 172.18.0.30  │      │ 172.18.0.14  │                     │
│  └──────────────┘      ├──────────────┤                     │
│                        │ vessel       │                     │
│                        │ 172.18.0.15  │                     │
│                        ├──────────────┤                     │
│                        │ integration  │                     │
│                        │ 172.18.0.16  │                     │
│                        ├──────────────┤                     │
│                        │ notification │                     │
│                        │ 172.18.0.17  │                     │
│                        └──────────────┘                     │
└─────────────────────────────────────────────────────────────┘
```

### 9.2 Port Mapping

| Port | Service | Access | Description |
|---|---|---|---|
| **3000** | Dashboard | Host | React frontend |
| **3001** | Grafana | Host | Monitoring dashboards |
| **5432** | PostgreSQL | Host (dev only) | Database access |
| **5672** | RabbitMQ | Host (dev only) | AMQP protocol |
| **8080** | API Gateway | Host | Main API entry point |
| **8761** | Eureka | Host (dev only) | Service discovery dashboard |
| **9090** | Prometheus | Host | Metrics endpoint |
| **15672** | RabbitMQ Mgmt | Host | RabbitMQ management UI |
| **16686** | Jaeger | Host | Distributed tracing UI |

**Exposed Ports (Host Accessible):** 3000, 3001, 8080, 8761, 9090, 15672, 16686

**Internal-Only Ports (Network Only):** 8081–8087 (application services), 5432, 5672

### 9.3 Inter-Service Communication

Services communicate via Docker DNS using service names:

| From | To | Protocol | DNS Name | Port |
|---|---|---|---|---|
| Gateway | Identity | HTTP | `identity` | 8081 |
| Gateway | Yard | HTTP | `yard` | 8082 |
| Gateway | Gate | HTTP | `gate` | 8083 |
| Gateway | Container | HTTP | `container` | 8084 |
| Gateway | Vessel | HTTP | `vessel` | 8085 |
| Gateway | Integration | HTTP | `integration` | 8086 |
| All Services | PostgreSQL | JDBC | `postgres` | 5432 |
| All Services | RabbitMQ | AMQP | `rabbitmq` | 5672 |
| All Services | Eureka | HTTP | `eureka` | 8761 |
| All Services | Jaeger | UDP/HTTP | `jaeger` | 6831/14268 |

### 9.4 Network Security

| Principle | Implementation |
|---|---|
| Minimal port exposure | Only gateway, dashboard, and monitoring ports on host |
| Network isolation | Single bridge network, no cross-network communication |
| No public ingress | All services internal to Docker network |
| Secrets via env | No secrets in Docker images or compose file |
| Non-root containers | All Java services run as `smartportx` user |
| TLS (future) | NGINX reverse proxy with TLS termination |

---

## 10. Service Startup Order

Services start in dependency order using `depends_on` with health checks:

```
Phase 1 (Infrastructure):
  postgres ──┐
             ├──▶ All healthy
  rabbitmq ──┘

Phase 2 (Discovery):
  eureka (waits for Phase 1 implicitly)

Phase 3 (Core Services — parallel):
  identity ─────────────────────┐
  container ────────────────────┤
  integration ──────────────────┤
  notification ─────────────────┘
  (all wait for eureka, postgres, rabbitmq)

Phase 4 (Dependent Services):
  yard ──────── (waits for container)
  vessel ────── (waits for container)

Phase 5 (Top-Level Services):
  gate ──────── (waits for container AND yard)
  gateway ───── (waits for eureka AND identity)
  dashboard ─── (waits for gateway)

Phase 6 (Monitoring — independent):
  prometheus ──┐
  grafana ─────┤ (all start in parallel)
  jaeger ──────┘
```

**Typical Startup Time:**

| Phase | Duration | Cumulative |
|---|---|---|
| Infrastructure | ~15s | 15s |
| Eureka | ~20s | 35s |
| Core Services | ~30s | 65s |
| Dependent Services | ~20s | 85s |
| Top-Level Services | ~15s | 100s |
| Monitoring | ~10s | 100s |
| **Total (all healthy)** | | **~100s** |

---

## 11. Health Checks

Every service exposes health information via Spring Boot Actuator:

| Service | Health Endpoint | Expected Response |
|---|---|---|
| PostgreSQL | `pg_isready` | Exit code 0 |
| RabbitMQ | `rabbitmq-diagnostics check_port_connectivity` | Exit code 0 |
| Eureka | `GET /actuator/health` | `{"status":"UP"}` |
| All Java Services | `GET /actuator/health` | `{"status":"UP"}` |
| Dashboard | `GET /` | HTTP 200 |

**Docker Compose Health Check Configuration:**

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:808x/actuator/health"]
  interval: 15s      # Check every 15 seconds
  timeout: 5s        # Fail if no response in 5 seconds
  retries: 5         # Mark unhealthy after 5 failures
  start_period: 45s  # Grace period for startup
```

---

## 12. Logging Architecture

| Layer | Tool | Format |
|---|---|---|
| Application Logs | Logback (Spring Boot) | Structured JSON |
| Container Logs | Docker log driver | stdout/stderr |
| Log Aggregation (future) | ELK Stack or Loki | Centralized |
| Metrics | Prometheus + Micrometer | Time-series |
| Tracing | Jaeger (OpenTelemetry) | Distributed traces |

**Structured Log Format:**

```json
{
  "timestamp": "2026-08-26T10:30:00.123Z",
  "level": "INFO",
  "service": "smartportx-yard",
  "traceId": "abc123def456",
  "spanId": "789ghi012",
  "message": "Container position updated",
  "containerNumber": "MSKU7654321",
  "yardSlot": "A-12-03",
  "userId": "operator-42"
}
```

---

## 13. Troubleshooting Guide

| Issue | Diagnosis | Resolution |
|---|---|---|
| Service won't start | `docker-compose logs {service}` | Check dependencies are healthy |
| Database connection refused | `docker-compose exec postgres pg_isready` | Verify postgres is running, check credentials |
| RabbitMQ not accessible | Open `http://localhost:15672` | Check rabbitmq container health |
| Eureka shows services DOWN | `docker-compose logs eureka` | Ensure eureka started first |
| Port already in use | `netstat -ano \| findstr :8080` | Change port in docker-compose.yml or stop conflicting process |
| Out of memory | `docker stats` | Increase Docker Desktop memory allocation |
| Migration fails | `docker-compose logs {service}` | Check Flyway migration scripts for errors |

**Useful Commands:**

```bash
# View all service status
docker-compose ps

# View logs for specific service
docker-compose logs -f integration

# Restart a single service
docker-compose restart yard

# Rebuild a specific service
docker-compose build --no-cache integration
docker-compose up -d integration

# Enter a running container
docker-compose exec integration bash

# Run a one-off command
docker-compose exec postgres psql -U smartportx -d smartportx_container

# Stop everything
docker-compose down

# Stop and remove volumes (fresh start)
docker-compose down -v
```

---

*End of Deployment Architecture Document*
