-- =====================================================================
-- JURIKA V43 -- Lot L5 (E9, RG-COM-08) : notification des delais a echeance.
--
-- Une detection planifiee parcourt les tickets en cours et notifie, dans
-- l'application, l'employe responsable du dossier (RG-DOS-01) -- et le
-- superviseur pour un delai depasse -- des echeances legales de leurs demarches
-- (memes points d'attention que l'ecran de deroulement : approche a J-15,
-- critique a J-3, depasse). Cette table retient ce qui a deja ete notifie, pour
-- ne notifier qu'UNE fois chaque palier d'une echeance donnee : si l'echeance
-- change (date saisie corrigee), le nouveau palier est notifie a nouveau.
-- =====================================================================

CREATE TABLE IF NOT EXISTS alertes_delais_notifiees (
    workspace_id  UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    ticket_id     UUID         NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    ordre         INTEGER      NOT NULL,
    echeance      DATE         NOT NULL,
    severite      VARCHAR(10)  NOT NULL CHECK (severite IN ('APPROCHE', 'CRITIQUE', 'DEPASSE')),
    notifie_le    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, ticket_id, ordre, echeance, severite)
);

ALTER TABLE alertes_delais_notifiees ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS alertes_delais_notifiees_isolation ON alertes_delais_notifiees;
CREATE POLICY alertes_delais_notifiees_isolation ON alertes_delais_notifiees FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

-- Detection planifiee : SEUL chemin qui lit au-dela du workspace courant. Elle
-- n'a pas de workspace (aucune requete d'utilisateur) : la fonction rend la
-- liste minimale (cabinet, ticket) des tickets en cours, puis chaque ticket est
-- traite DANS son cabinet (workspace pose, RLS appliquee). Meme regles que les
-- fonctions de auth V34 : un usage, colonnes minimales, aucun SQL dynamique,
-- search_path fixe, EXECUTE retire a PUBLIC puis accorde au seul jurika_app.
CREATE OR REPLACE FUNCTION ticket_tickets_pour_alertes_delais()
RETURNS TABLE (workspace_id uuid, ticket_id uuid)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT t.workspace_id, t.id
    FROM tickets t
    WHERE t.statut IN ('CREATION_TICKET', 'GENERATION_DOCUMENTS', 'DEROULEMENT_DEMARCHE')
    ORDER BY t.workspace_id, t.created_at
$$;

REVOKE EXECUTE ON FUNCTION ticket_tickets_pour_alertes_delais() FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON alertes_delais_notifiees TO jurika_app;
        GRANT EXECUTE ON FUNCTION ticket_tickets_pour_alertes_delais() TO jurika_app;
    END IF;
END
$$;
