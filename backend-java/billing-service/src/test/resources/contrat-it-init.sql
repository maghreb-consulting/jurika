-- Lot L7 (E6) : initialisation du conteneur de test du contrat d'abonnement.
-- Role d'execution jurika_app (comme infrastructure/scripts/init-db.sh), pour verifier
-- les droits au plus juste et la RLS des tables du contrat.
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jurika_app') THEN
        CREATE ROLE jurika_app LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'jurika_app_it';
    END IF;
END $$;
