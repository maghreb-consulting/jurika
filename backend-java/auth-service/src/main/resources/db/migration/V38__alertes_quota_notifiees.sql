-- =====================================================================
-- JURIKA V38 (auth-service) -- Lot L5 (E9, RG-COM-08 et RG-PLN-02) : quota approchant.
--
-- RG-PLN-02 : a 80 % d'un quota (dossiers, stockage, utilisateurs), le
-- superviseur est alerte ; RG-COM-08 : dans l'application. Une detection
-- planifiee lit l'usage de chaque cabinet actif ; cette table retient l'alerte
-- deja emise, UNE fois par quota et par mois d'abonnement civil (RG-PLN-01 : le
-- quota s'entend par mois), pour ne pas la repeter a chaque passage.
-- =====================================================================

CREATE TABLE IF NOT EXISTS alertes_quota_notifiees (
    workspace_id  UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    quota         VARCHAR(20)  NOT NULL CHECK (quota IN ('DOSSIERS', 'UTILISATEURS', 'STOCKAGE')),
    periode       CHAR(7)      NOT NULL,
    notifie_le    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, quota, periode)
);

ALTER TABLE alertes_quota_notifiees ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS alertes_quota_notifiees_isolation ON alertes_quota_notifiees;
CREATE POLICY alertes_quota_notifiees_isolation ON alertes_quota_notifiees FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

-- Detection planifiee : seul chemin hors workspace (pas d'utilisateur). Liste
-- minimale des cabinets actifs ; chacun est ensuite traite dans son workspace.
-- Regles de V34 : un usage, colonnes minimales, aucun SQL dynamique,
-- search_path fixe, EXECUTE retire a PUBLIC puis accorde au seul jurika_app.
CREATE OR REPLACE FUNCTION auth_workspaces_pour_alertes_quota()
RETURNS TABLE (id uuid)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT w.id FROM workspaces w WHERE w.status = 'ACTIVE' ORDER BY w.created_at
$$;

REVOKE EXECUTE ON FUNCTION auth_workspaces_pour_alertes_quota() FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON alertes_quota_notifiees TO jurika_app;
        GRANT EXECUTE ON FUNCTION auth_workspaces_pour_alertes_quota() TO jurika_app;
    END IF;
END
$$;
