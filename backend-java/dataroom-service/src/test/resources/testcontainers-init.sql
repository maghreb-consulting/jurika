-- Lot L0 (E15) : initialisation des conteneurs de test de dataroom-service.
-- Comme infrastructure/scripts/init-db.sh : extensions et role d'execution
-- jurika_app. Les tables amont (auth, ticket, workflow) sont creees par leurs
-- VRAIES migrations (SchemaJurikaDb), et non plus simulees ici.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jurika_app') THEN
        CREATE ROLE jurika_app LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'jurika_app_it';
    END IF;
END $$;

-- Role non-superuser propre a DataroomMultitenancyIT (lecture sous SET LOCAL
-- ROLE app_no_super, avec les droits qu'il accorde lui-meme), conserve.
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_no_super') THEN
        CREATE ROLE app_no_super NOLOGIN NOSUPERUSER NOBYPASSRLS;
    END IF;
END $$;
