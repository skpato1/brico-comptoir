#!/bin/sh
set -eu

# Only runs on an empty local PostgreSQL volume. Passwords come from .env.
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=ON_ERROR_STOP=1 <<'SQL'
\getenv app_user DB_USERNAME
\getenv app_password DB_PASSWORD
\getenv migration_user DB_MIGRATION_USERNAME
\getenv migration_password DB_MIGRATION_PASSWORD
\getenv database POSTGRES_DB
CREATE ROLE :"migration_user" LOGIN PASSWORD :'migration_password';
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password';
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT CONNECT, CREATE ON DATABASE :"database" TO :"migration_user";
GRANT CONNECT ON DATABASE :"database" TO :"app_user";
SQL
