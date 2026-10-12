-- Lot L5 E8 : initialisation du conteneur de RagCorpusCommunIT.
-- Extensions requises par les migrations d'auth (schema racine de jurika_db) et
-- d'ai (pgvector), et role d'execution jurika_app (NOSUPERUSER, NOBYPASSRLS),
-- comme infrastructure/scripts/init-db.sh.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jurika_app') THEN
        CREATE ROLE jurika_app LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'jurika_app_it';
    END IF;
END $$;
