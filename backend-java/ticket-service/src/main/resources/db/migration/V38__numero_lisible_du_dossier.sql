-- =====================================================================
-- JURIKA V38 -- Lot L4d (E6) : reference lisible du dossier.
--
-- Decision du directeur du 11 octobre 2026 : un dossier se designe par son
-- annee, son numero et sa societe (ex. 2026-0007 -- PARACOSME). Le numero est
-- attribue par cabinet (workspace) et par annee de creation, dans l'ordre de
-- creation ; il ne change plus ensuite. La societe n'est pas stockee dans la
-- reference : elle suit la denomination courante du dossier.
--
-- Les dossiers existants sont numerotes dans l'ordre de leur creation.
-- =====================================================================

ALTER TABLE entreprise_dossiers ADD COLUMN IF NOT EXISTS numero_annee SMALLINT;
ALTER TABLE entreprise_dossiers ADD COLUMN IF NOT EXISTS numero_ordre INTEGER;

WITH n AS (
    SELECT id,
           EXTRACT(YEAR FROM created_at AT TIME ZONE 'Africa/Casablanca')::smallint AS annee,
           row_number() OVER (PARTITION BY workspace_id,
                                           EXTRACT(YEAR FROM created_at AT TIME ZONE 'Africa/Casablanca')
                              ORDER BY created_at, id) AS ordre
    FROM entreprise_dossiers
    WHERE numero_annee IS NULL
)
UPDATE entreprise_dossiers d SET numero_annee = n.annee, numero_ordre = n.ordre
FROM n WHERE d.id = n.id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_dossier_numero
    ON entreprise_dossiers (workspace_id, numero_annee, numero_ordre);

-- Attribution a la creation : verrou par cabinet et par annee (deux creations
-- simultanees ne prennent pas le meme numero ; l'index unique le garantit en
-- dernier ressort). SECURITY DEFINER : le numero suivant se lit sur tous les
-- dossiers du cabinet, y compris ceux que la RLS du role d'execution masquerait.
CREATE OR REPLACE FUNCTION entreprise_dossiers_numeroter()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.numero_annee IS NULL OR NEW.numero_ordre IS NULL THEN
        NEW.numero_annee := EXTRACT(YEAR FROM COALESCE(NEW.created_at, now()) AT TIME ZONE 'Africa/Casablanca')::smallint;
        PERFORM pg_advisory_xact_lock(hashtext('dossier_numero:' || NEW.workspace_id::text || ':' || NEW.numero_annee));
        SELECT COALESCE(max(numero_ordre), 0) + 1 INTO NEW.numero_ordre
        FROM entreprise_dossiers
        WHERE workspace_id = NEW.workspace_id AND numero_annee = NEW.numero_annee;
    END IF;
    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS trg_entreprise_dossiers_numeroter ON entreprise_dossiers;
CREATE TRIGGER trg_entreprise_dossiers_numeroter
    BEFORE INSERT ON entreprise_dossiers
    FOR EACH ROW EXECUTE FUNCTION entreprise_dossiers_numeroter();
