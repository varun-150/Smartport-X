# =============================================================================
# SmartPortX — Repo-Root Multi-Stage Dockerfile (M12)
#
# Builds the ENTIRE reactor in-image from the repo root, so there is no need to
# pre-build JARs on the host. One command reproduces the full stack:
#
#   docker compose up --build
#
# Build stages:
#   builder  — maven:3.9.16-eclipse-temurin-25, `mvn package` for all modules.
#   runtime  — eclipse-temurin:25-jre + curl (shared base for healthchecks).
#   smartportx-<service> — one final stage per app service, copies its boot JAR.
#
# Note: the dashboard is a node/nginx frontend and keeps its own multi-stage
# Dockerfile (context ./smartportx-dashboard).
# =============================================================================

# -----------------------------------------------------------------------------
# Builder — compile + repackage the full reactor
# -----------------------------------------------------------------------------
FROM maven:3.9.16-eclipse-temurin-25 AS builder

WORKDIR /workspace

# Cache-friendly: copy only POMs first, pre-fetch dependencies, then sources.
COPY pom.xml ./
COPY smartportx-common/pom.xml smartportx-common/pom.xml
COPY smartportx-eureka/pom.xml smartportx-eureka/pom.xml
COPY smartportx-gateway/pom.xml smartportx-gateway/pom.xml
COPY smartportx-identity/pom.xml smartportx-identity/pom.xml
COPY smartportx-container/pom.xml smartportx-container/pom.xml
COPY smartportx-yard/pom.xml smartportx-yard/pom.xml
COPY smartportx-gate/pom.xml smartportx-gate/pom.xml
COPY smartportx-vessel/pom.xml smartportx-vessel/pom.xml
COPY smartportx-integration/pom.xml smartportx-integration/pom.xml
COPY smartportx-notification/pom.xml smartportx-notification/pom.xml

# Warm the local m2 cache. Non-fatal if a plugin can't resolve yet (it will be
# fetched during package if needed).
RUN mvn -B -N dependency:go-offline || true

# Copy source for every module, then build the whole reactor.
COPY smartportx-common/src smartportx-common/src
COPY smartportx-eureka/src smartportx-eureka/src
COPY smartportx-gateway/src smartportx-gateway/src
COPY smartportx-identity/src smartportx-identity/src
COPY smartportx-container/src smartportx-container/src
COPY smartportx-yard/src smartportx-yard/src
COPY smartportx-gate/src smartportx-gate/src
COPY smartportx-vessel/src smartportx-vessel/src
COPY smartportx-integration/src smartportx-integration/src
COPY smartportx-notification/src smartportx-notification/src

RUN mvn -B -DskipTests package

# -----------------------------------------------------------------------------
# Runtime base — JRE + curl for actuator healthchecks
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# -----------------------------------------------------------------------------
# Application runtime stages (one per service)
# -----------------------------------------------------------------------------

FROM runtime AS smartportx-eureka
ARG SERVICE_MODULE=smartportx-eureka
ARG SERVICE_PORT=8761
COPY --from=builder /workspace/${SERVICE_MODULE}/target/${SERVICE_MODULE}-*.jar app.jar
EXPOSE ${SERVICE_PORT}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS smartportx-gateway
ARG SERVICE_MODULE=smartportx-gateway
ARG SERVICE_PORT=8080
COPY --from=builder /workspace/${SERVICE_MODULE}/target/${SERVICE_MODULE}-*.jar app.jar
EXPOSE ${SERVICE_PORT}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS smartportx-identity
ARG SERVICE_MODULE=smartportx-identity
ARG SERVICE_PORT=8081
COPY --from=builder /workspace/${SERVICE_MODULE}/target/${SERVICE_MODULE}-*.jar app.jar
EXPOSE ${SERVICE_PORT}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS smartportx-yard
ARG SERVICE_MODULE=smartportx-yard
ARG SERVICE_PORT=8082
COPY --from=builder /workspace/${SERVICE_MODULE}/target/${SERVICE_MODULE}-*.jar app.jar
EXPOSE ${SERVICE_PORT}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS smartportx-gate
ARG SERVICE_MODULE=smartportx-gate
ARG SERVICE_PORT=8083
COPY --from=builder /workspace/${SERVICE_MODULE}/target/${SERVICE_MODULE}-*.jar app.jar
EXPOSE ${SERVICE_PORT}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS smartportx-container
ARG SERVICE_MODULE=smartportx-container
ARG SERVICE_PORT=8084
COPY --from=builder /workspace/${SERVICE_MODULE}/target/${SERVICE_MODULE}-*.jar app.jar
EXPOSE ${SERVICE_PORT}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS smartportx-vessel
ARG SERVICE_MODULE=smartportx-vessel
ARG SERVICE_PORT=8085
COPY --from=builder /workspace/${SERVICE_MODULE}/target/${SERVICE_MODULE}-*.jar app.jar
EXPOSE ${SERVICE_PORT}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS smartportx-integration
ARG SERVICE_MODULE=smartportx-integration
ARG SERVICE_PORT=8086
COPY --from=builder /workspace/${SERVICE_MODULE}/target/${SERVICE_MODULE}-*.jar app.jar
EXPOSE ${SERVICE_PORT}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS smartportx-notification
ARG SERVICE_MODULE=smartportx-notification
ARG SERVICE_PORT=8087
COPY --from=builder /workspace/${SERVICE_MODULE}/target/${SERVICE_MODULE}-*.jar app.jar
EXPOSE ${SERVICE_PORT}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]