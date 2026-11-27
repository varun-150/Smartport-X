#!/bin/sh
# Create the service-owned schema in Render Postgres before the Spring Boot app starts.
set -eu
: "${PGHOST:?PGHOST is required}"
: "${PGPORT:?PGPORT is required}"
: "${PGDATABASE:?PGDATABASE is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
: "${SERVICE_SCHEMA:?SERVICE_SCHEMA is required}"
export PGPASSWORD
psql "host=${PGHOST} port=${PGPORT} dbname=${PGDATABASE} user=${PGUSER} sslmode=require" \
  -v ON_ERROR_STOP=1 \
  -v schema="${SERVICE_SCHEMA}" \
  -v owner="${PGUSER}" \
  -c 'CREATE SCHEMA IF NOT EXISTS :"schema" AUTHORIZATION :"owner";'
