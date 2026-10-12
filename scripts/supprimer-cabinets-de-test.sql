-- Lot L5 (E3) : suppression des cabinets crees par le test de fumee.
--
-- Usage (base jurika_db, proprietaire, par l'entree standard) :
--   docker exec -i jurika-postgres psql -U <proprietaire> -d jurika_db -v ON_ERROR_STOP=1 \
--     -v code=JUR-XXXXX < scripts/supprimer-cabinets-de-test.sql
-- code = '*' vise TOUS les cabinets du test de fumee.
--
-- Garde-fou : seuls les cabinets nommes "SmokeTest Cabinet <horodatage>" (nom pose par
-- scripts/smoke-test.mjs) sont touches ; un autre code est ignore (0 cabinet). Tout se fait
-- dans une transaction : un echec n'efface rien. Chaque ligne effacee est comptee par table.

BEGIN;

CREATE TEMP TABLE cibles ON COMMIT DROP AS
    SELECT id, code FROM workspaces
     WHERE name ~ '^SmokeTest Cabinet [0-9]+$'
       AND (:'code' = '*' OR code = :'code');

SELECT 'cabinet vise : ' || code FROM cibles ORDER BY code;

DO $$
DECLARE
    r       record;
    n       bigint;
    reste   bigint;
    passe   int := 0;
    progres boolean;
BEGIN
    -- Les tables qui portent workspace_id se vident dans un ordre quelconque : une table
    -- encore referencee (cle etrangere) est retentee a la passe suivante.
    LOOP
        passe := passe + 1;
        progres := false;
        reste := 0;
        FOR r IN SELECT c.table_name
                   FROM information_schema.columns c
                   JOIN information_schema.tables t
                     ON t.table_schema = c.table_schema AND t.table_name = c.table_name
                  WHERE c.table_schema = 'public' AND c.column_name = 'workspace_id'
                    AND t.table_type = 'BASE TABLE'
                    AND c.table_name <> 'workspaces'
        LOOP
            BEGIN
                EXECUTE format('DELETE FROM %I WHERE workspace_id IN (SELECT id FROM cibles)', r.table_name);
                GET DIAGNOSTICS n = ROW_COUNT;
                IF n > 0 THEN
                    progres := true;
                    RAISE NOTICE 'passe % : % ligne(s) effacee(s) dans %', passe, n, r.table_name;
                END IF;
            EXCEPTION WHEN foreign_key_violation THEN
                reste := reste + 1;
            END;
        END LOOP;
        EXIT WHEN reste = 0;
        IF NOT progres OR passe > 10 THEN
            RAISE EXCEPTION 'suppression bloquee par une cle etrangere (% table(s))', reste;
        END IF;
    END LOOP;
    DELETE FROM workspaces WHERE id IN (SELECT id FROM cibles);
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE '% cabinet(s) de test supprime(s)', n;
END $$;

COMMIT;
