#!/usr/bin/env bash
# =============================================================================
# SmartPortX — Database initialization (M1.1)
#
# Single PostgreSQL instance, schema-per-service (ADR-004, DATA-ARCHITECTURE §2).
# One database `smartportx` + 7 logical schemas (`smartportx_{service}`).
#
# Re-runnable / idempotent. Works in two contexts:
#   1. docker-compose: mounted at /docker-entrypoint-initdb.d/01-init-databases.sh
#      (runs once on first volume start, as POSTGRES_USER)
#   2. Local dev (no Docker / WSL2): `PGHOST=localhost psql ... ./scripts/init-databases.sh`
#
# Usage:
#   POSTGRES_USER=smartportx POSTGRES_DB=smartportx ./scripts/init-databases.sh
# =============================================================================
set -euo pipefail

POSTGRES_DB="${POSTGRES_DB:-smartportx}"
POSTGRES_USER="${POSTGRES_USER:-smartportx}"
# Bootstrap database used only to run `CREATE DATABASE` if the target is missing.
BOOTSTRAP_DB="${BOOTSTRAP_DB:-postgres}"
APP_ROLE="${APP_ROLE:-smartportx_app}"
APP_ROLE_PASSWORD="${APP_ROLE_PASSWORD:-smartportx_local}"
SERVICE_SCHEMAS=(
  smartportx_identity
  smartportx_container
  smartportx_yard
  smartportx_gate
  smartportx_vessel
  smartportx_integration
  smartportx_notification
)

echo "[init-databases] target db=${POSTGRES_DB} user=${POSTGRES_USER} (schemas: ${#SERVICE_SCHEMAS[@]})"

# -- 1. Ensure the target database exists -------------------------------------
if psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$BOOTSTRAP_DB" \
  -tAc "SELECT 1 FROM pg_database WHERE datname = '${POSTGRES_DB}'" | grep -q 1; then
  echo "[init-databases] database '${POSTGRES_DB}' already exists (ok)"
else
  createdb --username "$POSTGRES_USER" "$POSTGRES_DB"
  echo "[init-databases] created database '${POSTGRES_DB}'"
fi

# -- 2. Dev application role (own data user for local runs; safe to rerun) ----
case "$APP_ROLE" in
  ''|[a-z_][a-z0-9_]*) : ;; # ok (unquoted identifier-safe)
  *) echo "[init-databases] ERROR: APP_ROLE must match ^[a-z_][a-z0-9_]*\$" >&2; exit 1 ;;
esac

# (psql does not interpolate :vars inside dollar-quoted bodies, so bash expands here.)
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${APP_ROLE}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${APP_ROLE}', '${APP_ROLE_PASSWORD}');
        RAISE NOTICE 'role % created', '${APP_ROLE}';
    ELSE
        RAISE NOTICE 'role % already exists', '${APP_ROLE}';
    END IF;
END
\$\$;
EOSQL

# -- 3. Schema-per-service (idempotent) ----------------------------------------
for SCHEMA in "${SERVICE_SCHEMAS[@]}"; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v schema="$SCHEMA" -v owner="$POSTGRES_USER" -v app_role="$APP_ROLE" <<EOSQL
CREATE SCHEMA IF NOT EXISTS :"schema" AUTHORIZATION :"owner";
COMMENT ON SCHEMA :"schema" IS 'SmartPortX service schema';
GRANT USAGE, CREATE ON SCHEMA :"schema" TO :"app_role";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA :"schema"
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_role";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA :"schema"
    GRANT USAGE, SELECT ON SEQUENCES TO :"app_role";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA :"schema"
    GRANT EXECUTE ON FUNCTIONS TO :"app_role";
EOSQL
  echo "[init-databases] schema '${SCHEMA}' ready"
done

echo "[init-databases] complete"