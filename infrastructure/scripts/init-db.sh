#!/bin/bash
set -e

# Base de donnees principale + extensions + role jurika_app
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS vector;
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
    CREATE EXTENSION IF NOT EXISTS pgcrypto;
    CREATE EXTENSION IF NOT EXISTS pg_trgm;

    CREATE ROLE jurika_app WITH LOGIN PASSWORD '${POSTGRES_PASSWORD}';
    GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO jurika_app;
    GRANT USAGE ON SCHEMA public TO jurika_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO jurika_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT USAGE, SELECT ON SEQUENCES TO jurika_app;
EOSQL

# Base de donnees billing-service (separe du domaine metier)
BILLING_DB="${BILLING_DB_NAME:-jurika_billing}"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT 'CREATE DATABASE ${BILLING_DB} OWNER ${POSTGRES_USER}'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${BILLING_DB}')\gexec
EOSQL
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${BILLING_DB}" <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS vector;
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
    CREATE EXTENSION IF NOT EXISTS pgcrypto;
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
    GRANT CONNECT ON DATABASE ${BILLING_DB} TO jurika_app;
    GRANT USAGE ON SCHEMA public TO jurika_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO jurika_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT USAGE, SELECT ON SEQUENCES TO jurika_app;
EOSQL

echo "PostgreSQL initialized: extensions (vector, uuid-ossp, pgcrypto, pg_trgm) + role jurika_app + databases ${POSTGRES_DB} & ${BILLING_DB}"
