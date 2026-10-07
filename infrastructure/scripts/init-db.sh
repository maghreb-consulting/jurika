#!/bin/bash
set -e

# Role applicatif jurika_app (lot L0) : role d'execution des services,
# NON proprietaire des tables, NOSUPERUSER et NOBYPASSRLS, pour que la Row
# Level Security s'applique. Flyway migre avec le proprietaire (POSTGRES_USER).
# Mot de passe DEDIE et OBLIGATOIRE : aucun repli sur POSTGRES_PASSWORD.
# Il est lu par psql (\getenv) et ne passe ni dans la ligne de commande ni
# dans le texte SQL interpole par le shell.
: "${JURIKA_APP_PASSWORD:?JURIKA_APP_PASSWORD est obligatoire (mot de passe du role jurika_app)}"
export JURIKA_APP_PASSWORD

# Base de donnees principale + extensions + role jurika_app
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS vector;
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
    CREATE EXTENSION IF NOT EXISTS pgcrypto;
    CREATE EXTENSION IF NOT EXISTS pg_trgm;

    \getenv app_pwd JURIKA_APP_PASSWORD
    CREATE ROLE jurika_app WITH LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION
        PASSWORD :'app_pwd';
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
