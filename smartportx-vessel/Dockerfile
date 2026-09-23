# SmartPortX Vessel Service
#
# NOTE: single-stage, jar-copy image (mirrors smartportx-eureka/Dockerfile).
# The compose build context is ./smartportx-vessel (module dir only), which cannot
# resolve the reactor parent POM at the repo root, so an in-image `mvn package` is
# not possible here. The application JAR must be built first:
#
#   mvn -B -DskipTests -pl smartportx-vessel -am package
#   docker compose up --build vessel
FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/smartportx-vessel-*.jar app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]