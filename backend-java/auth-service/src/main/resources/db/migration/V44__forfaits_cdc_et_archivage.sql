-- =====================================================================
-- JURIKA V44 (auth-service) -- Lot L7 (E5) : forfaits du CDC 4.3, mois
-- d'abonnement, archivage du workspace.
--
-- 1. Forfaits : Essentiel, Professionnel, Max, Sur Devis (codes essentiel,
--    professionnel, max, sur_devis). Reprise des anciens codes :
--      business   -> professionnel (meme forfait intermediaire, renomme en
--                    V23 ; symetrique de l'ancien renommage)
--      entreprise -> sur_devis     (offre sur devis, sans limite fixe)
--      essentiel  -> essentiel
--    NULL reste NULL (workspace anterieur aux forfaits : signale, jamais
--    rabattu en silence).
-- 2. abonnement_debut : date de debut de l'abonnement, ancre du mois
--    d'abonnement (RG-PLN-01 : quota de dossiers par mois d'abonnement).
--    Reprise : date (Casablanca) de la premiere activation connue
--    (active_since) ; sans activation connue, NULL (mois civil, decision du
--    lot). rythme_paiement : MENSUEL / ANNUEL (miroir de billing).
-- 3. Archivage (RG-TAR-07, RG-PLN-07) : statut ARCHIVED, jamais une
--    suppression. Les anciens DEACTIVATED (desactivation par decision)
--    deviennent ARCHIVED, motif DECISION : la desactivation archive.
-- 4. Canal d'alerte du super-admin (RG-PLN-02) : liste des super-admins
--    actifs, seul chemin hors workspace (regles de V34).
-- =====================================================================

-- 1. Forfaits -----------------------------------------------------------
ALTER TABLE workspaces DROP CONSTRAINT IF EXISTS workspaces_selected_plan_check;

UPDATE workspaces SET selected_plan = 'professionnel' WHERE selected_plan = 'business';
UPDATE workspaces SET selected_plan = 'sur_devis'     WHERE selected_plan = 'entreprise';

ALTER TABLE workspaces ADD CONSTRAINT workspaces_selected_plan_check
    CHECK (selected_plan IS NULL OR selected_plan IN ('essentiel', 'professionnel', 'max', 'sur_devis'));

COMMENT ON COLUMN workspaces.selected_plan IS
    'Lot L7 (V44) -- forfait du CDC 4.3 : essentiel | professionnel | max | sur_devis (NULL = workspace anterieur aux forfaits).';

-- 2. Mois d'abonnement --------------------------------------------------
ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS abonnement_debut DATE,
    ADD COLUMN IF NOT EXISTS rythme_paiement  VARCHAR(10);

ALTER TABLE workspaces DROP CONSTRAINT IF EXISTS workspaces_rythme_paiement_check;
ALTER TABLE workspaces ADD CONSTRAINT workspaces_rythme_paiement_check
    CHECK (rythme_paiement IS NULL OR rythme_paiement IN ('MENSUEL', 'ANNUEL'));

UPDATE workspaces
   SET abonnement_debut = (active_since AT TIME ZONE 'Africa/Casablanca')::date
 WHERE abonnement_debut IS NULL AND active_since IS NOT NULL;

COMMENT ON COLUMN workspaces.abonnement_debut IS
    'Lot L7 (V44) -- debut de l''abonnement (Casablanca), ancre du mois d''abonnement du quota de dossiers (RG-PLN-01). NULL = mois civil.';
COMMENT ON COLUMN workspaces.rythme_paiement IS
    'Lot L7 (V44) -- rythme de paiement de l''abonnement : MENSUEL | ANNUEL (RG-TAR-01, RG-TAR-02). Source : billing-service.';

-- 3. Archivage ----------------------------------------------------------
ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS archive_le    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS archive_motif VARCHAR(20);

ALTER TABLE workspaces DROP CONSTRAINT IF EXISTS workspaces_archive_motif_check;
ALTER TABLE workspaces ADD CONSTRAINT workspaces_archive_motif_check
    CHECK (archive_motif IS NULL OR archive_motif IN ('IMPAYE', 'FIN_ABONNEMENT', 'RESILIATION', 'DECISION'));

ALTER TABLE workspaces DROP CONSTRAINT IF EXISTS workspaces_status_check;
ALTER TABLE workspaces ADD CONSTRAINT workspaces_status_check
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED', 'PENDING_VERIFICATION', 'ARCHIVED'));

UPDATE workspaces
   SET status = 'ARCHIVED', archive_le = COALESCE(updated_at, now()), archive_motif = 'DECISION'
 WHERE status = 'DEACTIVATED';

COMMENT ON COLUMN workspaces.archive_le IS
    'Lot L7 (V44) -- archivage du workspace (RG-TAR-07) : acces fermes, rien n''est supprime ; NULL apres reactivation (RG-TAR-08).';

-- 4. Canal d'alerte du super-admin --------------------------------------
-- Les alertes de quota (RG-PLN-02) vont au superviseur du cabinet ET au
-- super-admin, notifie dans son propre workspace. Liste minimale des
-- super-admins actifs (identifiant et workspace) ; regles de V34 : un usage,
-- colonnes minimales, aucun SQL dynamique, search_path fixe, EXECUTE retire a
-- PUBLIC puis accorde au seul jurika_app.
CREATE OR REPLACE FUNCTION auth_super_admins_actifs()
RETURNS TABLE (id uuid, workspace_id uuid)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT u.id, u.workspace_id FROM users u
     WHERE u.role = 'SUPER_ADMIN' AND u.status = 'ACTIVE'
     ORDER BY u.created_at
$$;

REVOKE EXECUTE ON FUNCTION auth_super_admins_actifs() FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        GRANT EXECUTE ON FUNCTION auth_super_admins_actifs() TO jurika_app;
    END IF;
END
$$;
