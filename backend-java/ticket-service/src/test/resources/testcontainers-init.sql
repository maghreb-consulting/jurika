-- Lot L0 (E12) : initialisation des conteneurs de test.
-- Comme infrastructure/scripts/init-db.sh : extensions requises par les
-- migrations et role d'execution jurika_app. Les tables amont (auth, ticket,
-- dataroom) sont creees par leurs VRAIES migrations dans le test, dans l'ordre
-- de la base partagee jurika_db.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jurika_app') THEN
        CREATE ROLE jurika_app LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'jurika_app_it';
    END IF;
END $$;
