-- Lot L7 (E10) : initialisation du conteneur du schema COMPLET de jurika_db
-- (migrations de tous les services qui y migrent). Extensions d'auth
-- (uuid-ossp, pgcrypto), d'ai (pgvector) et pg_trgm, role d'execution jurika_app
-- (NOSUPERUSER, NOBYPASSRLS), comme infrastructure/scripts/init-db.sh.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jurika_app') THEN
        CREATE ROLE jurika_app LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'jurika_app_it';
    END IF;
END $$;
