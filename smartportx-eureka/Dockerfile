# SmartPortX Eureka Server
#
# NOTE: single-stage, jar-copy image. The compose build context is ./smartportx-eureka
# (module dir only), which cannot resolve the reactor parent POM at the repo root, so an
# in-image `mvn package` is not possible here. The application JAR must be built first:
#
#   mvn -B -pl smartportx-eureka package
#   docker compose up --build eureka
#
# Multi-stage (full reactor) images for all services are scheduled in M12 (docker milestone).
FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/smartportx-eureka-*.jar app.jar

EXPOSE 8761
ENTRYPOINT ["java", "-jar", "/app/app.jar"]