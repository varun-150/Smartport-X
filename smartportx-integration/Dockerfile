# SmartPortX Integration Service
#
# NOTE: single-stage, jar-copy image (mirrors smartportx-eureka/Dockerfile).
# The compose build context is ./smartportx-integration (module dir only), which cannot
# resolve the reactor parent POM at the repo root, so an in-image `mvn package` is
# not possible here. The application JAR must be built first:
#
#   mvn -B -DskipTests -pl smartportx-integration -am package
#   docker compose up --build integration
FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 smartportx \
    && useradd --system --uid 10001 --gid smartportx --home-dir /app --no-create-home smartportx \
    && mkdir -p /app && chown -R smartportx:smartportx /app

WORKDIR /app
USER smartportx
COPY --chown=smartportx:smartportx target/smartportx-integration-*.jar app.jar

EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]