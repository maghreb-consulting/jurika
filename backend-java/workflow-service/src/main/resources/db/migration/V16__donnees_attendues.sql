-- =====================================================================
-- JURIKA V16 (workflow-service) -- Lot L3 : donnees attendues d'un organisme
--
-- Regle des variables (CLAUDE.md) : seule une variable EXTERNE (RC, ICE, IF, TP,
-- CNSS, date d'immatriculation, depot au greffe...) peut manquer. L'acte sort avec le
-- marqueur « A OBTENIR », la plateforme RECLAME la donnee, et l'acte se REGENERE quand
-- elle arrive. Une ligne par (ticket, document, variable) reclamee ; `regeneree_le` est
-- pose quand le document est regenere sans plus attendre cette donnee.
-- =====================================================================
CREATE TABLE IF NOT EXISTS donnees_attendues (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id   UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    ticket_id      UUID NOT NULL REFERENCES tickets(id) ON DELETE RESTRICT,
    workflow_code  VARCHAR(40) NOT NULL,
    template_code  VARCHAR(120) NOT NULL,
    variable       VARCHAR(80) NOT NULL,
    libelle        TEXT NOT NULL,
    reclamee_le    TIMESTAMPTZ NOT NULL DEFAULT now(),
    regeneree_le   TIMESTAMPTZ,
    CONSTRAINT ux_donnees_attendues UNIQUE (workspace_id, ticket_id, template_code, variable)
);

CREATE INDEX IF NOT EXISTS idx_donnees_attendues_ticket
    ON donnees_attendues (workspace_id, ticket_id) WHERE regeneree_le IS NULL;

ALTER TABLE donnees_attendues ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS donnees_attendues_isolation ON donnees_attendues;
CREATE POLICY donnees_attendues_isolation ON donnees_attendues FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);
