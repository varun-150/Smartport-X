#!/bin/sh
# Convert Render's PostgreSQL URL into the JDBC URL expected by Spring Boot,
# then select the service-owned schema without hardcoding the database host.
set -eu
case "${DATABASE_URL:-}" in
  jdbc:postgresql://*) ;;
  postgresql://*) DATABASE_URL="jdbc:${DATABASE_URL#postgresql://}" ;;
  postgres://*) DATABASE_URL="jdbc:${DATABASE_URL#postgres://}" ;;
  *) echo "DATABASE_URL must be a PostgreSQL connection URL" >&2; exit 1 ;;
esac
if [ -n "${SERVICE_SCHEMA:-}" ]; then
  case "$DATABASE_URL" in
    *\?*) DATABASE_URL="${DATABASE_URL}&currentSchema=${SERVICE_SCHEMA}" ;;
    *) DATABASE_URL="${DATABASE_URL}?currentSchema=${SERVICE_SCHEMA}" ;;
  esac
fi
export DATABASE_URL
exec java -jar /app/app.jar
