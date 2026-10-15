-- =====================================================================
-- JURIKA V42 (auth-service) -- Lot L7 (E4) : statuts d'essai herites.
--
-- Le concept d'essai de 14 jours (Sprint 11, V16) a ete efface du produit le
-- 2026-06-02 (UX-3) : les colonnes trial_* sont restees, et des cabinets crees a
-- l'epoque peuvent encore porter TRIAL_ACTIVE ou TRIAL_EXPIRED. Depuis L7, ces
-- statuts declenchent les regles de l'essai gratuit (aucun telechargement,
-- filigrane, lecture seule, suppression). Ils ne doivent valoir QUE pour un
-- essai ouvert par l'inscription a l'essai (ligne dans essais_registre) :
--   * les statuts herites sont effaces (cabinet sans essai) ;
--   * le cycle planifie ne lit plus que les essais du registre.
-- =====================================================================

UPDATE workspaces w
   SET trial_status = NULL
 WHERE w.trial_status IN ('TRIAL_ACTIVE', 'TRIAL_EXPIRED')
   AND NOT EXISTS (SELECT 1 FROM essais_registre r WHERE r.workspace_id = w.id);

CREATE OR REPLACE FUNCTION essai_workspaces_a_traiter()
RETURNS TABLE (id uuid)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT w.id FROM workspaces w
    JOIN essais_registre r ON r.workspace_id = w.id AND r.supprime_le IS NULL
    WHERE w.trial_status IN ('TRIAL_ACTIVE', 'TRIAL_EXPIRED')
    ORDER BY w.created_at
$$;

REVOKE EXECUTE ON FUNCTION essai_workspaces_a_traiter() FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        GRANT EXECUTE ON FUNCTION essai_workspaces_a_traiter() TO jurika_app;
    END IF;
END
$$;
