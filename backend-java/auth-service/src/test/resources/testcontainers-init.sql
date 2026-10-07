-- Extensions PostgreSQL requises par les migrations Flyway de auth-service.
-- En prod : infrastructure/scripts/init-db.sh fait la meme chose au demarrage du conteneur PG.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Lot L0 (E9) : role d'execution des services, comme init-db.sh en production :
-- non proprietaire, NOSUPERUSER, NOBYPASSRLS. Les migrations de droits (E10)
-- echouent s'il est absent ; les tests connectes en jurika_app (E11 et
-- suivantes) prouvent la RLS, que l'utilisateur superuser du conteneur ignore.
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jurika_app') THEN
        CREATE ROLE jurika_app LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'jurika_app_it';
    END IF;
END $$;
