-- Lot L0 (securite serveur), etape E10 -- ticket-service.
--
-- Droits du role d'execution des services, jurika_app : non proprietaire,
-- NOSUPERUSER, NOBYPASSRLS, pour que la Row Level Security s'applique (les
-- services se connectaient en superutilisateur, qui l'ignore). Flyway, lui,
-- migre avec le proprietaire.
--
-- Cette migration ne CREE PAS le role et ne contient AUCUN mot de passe : le role
-- est cree par infrastructure/scripts/init-db.sh (volume neuf) ou par
-- infrastructure/scripts/rattrapage-role-applicatif.sh (base existante, RDS ;
-- cf. infrastructure/production/ROLE_APPLICATIF_RDS.md). S'il est absent, elle
-- ECHOUE explicitement au lieu de passer en silence.
--
-- Droits accordes : DML seulement (SELECT, INSERT, UPDATE, DELETE) sur les
-- tables, USAGE et SELECT sur les sequences, existantes et futures (droits par
-- defaut du role qui migre). Aucun droit DDL, TRUNCATE ni REFERENCES. Aucun
-- droit sur l'historique Flyway. Idempotente : la base jurika_db est partagee,
-- plusieurs services l'appliquent chacun a leur tour.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        RAISE EXCEPTION 'Role jurika_app absent : le creer avant le demarrage des services '
            '(init-db.sh ou rattrapage-role-applicatif.sh, cf. ROLE_APPLICATIF_RDS.md)';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO jurika_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO jurika_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO jurika_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO jurika_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO jurika_app;

-- L'historique Flyway reste hors de portee du role d'execution.
DO $$
DECLARE
    historique record;
BEGIN
    FOR historique IN
        SELECT c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind = 'r'
          AND c.relname LIKE 'flyway%history%'
    LOOP
        EXECUTE format('REVOKE ALL ON TABLE public.%I FROM jurika_app', historique.relname);
    END LOOP;
END
$$;
