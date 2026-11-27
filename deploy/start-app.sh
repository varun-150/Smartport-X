#!/bin/sh
# Convert Render's PostgreSQL URI to a pgJDBC URL. Credentials are supplied
# separately through DATABASE_USERNAME and DATABASE_PASSWORD.
set -eu
case "${DATABASE_URL:-}" in
  jdbc:postgresql://*) ;;
  postgresql://*|postgres://*)
    URI="${DATABASE_URL#*://}"
    AUTHORITY="${URI%%/*}"
    DBPATH="${URI#*/}"
    HOSTPORT="${AUTHORITY#*@}"
    DATABASE_URL="jdbc:postgresql://${HOSTPORT}/${DBPATH}"
    ;;
  *) echo "DATABASE_URL must be a PostgreSQL connection URL" >&2; exit 1 ;;
esac
case "$DATABASE_URL" in
  *sslmode=*) ;;
  *\?*) DATABASE_URL="${DATABASE_URL}&sslmode=require" ;;
  *) DATABASE_URL="${DATABASE_URL}?sslmode=require" ;;
esac
if [ -n "${SERVICE_SCHEMA:-}" ]; then
  case "$DATABASE_URL" in
    *currentSchema=*) ;;
    *\?*) DATABASE_URL="${DATABASE_URL}&currentSchema=${SERVICE_SCHEMA}" ;;
    *) DATABASE_URL="${DATABASE_URL}?currentSchema=${SERVICE_SCHEMA}" ;;
  esac
fi
export DATABASE_URL
exec java -jar /app/app.jar
