-- =====================================================================
-- JURIKA V35 -- Lot L4b (E4, RG-DEM-01) : decisions d'un ticket.
--
-- Hors creation, les demarches d'un ticket sont les lignes du referentiel
-- du corpus des decisions qu'il porte (une ou plusieurs par ticket : une
-- assemblee peut adopter plusieurs resolutions). L'employe en charge les
-- choisit dans la liste des decisions de la matrice (libelle du cabinet).
-- =====================================================================

CREATE TABLE IF NOT EXISTS ticket_decisions (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id  UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    ticket_id     UUID        NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    matrice       VARCHAR(80) NOT NULL,
    decision_code VARCHAR(80) NOT NULL,
    ajoute_par    UUID,
    ajoute_le     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (ticket_id, matrice, decision_code)
);

CREATE INDEX IF NOT EXISTS idx_ticket_decisions_ticket ON ticket_decisions (ticket_id);

ALTER TABLE ticket_decisions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS ticket_decisions_isolation ON ticket_decisions;
CREATE POLICY ticket_decisions_isolation ON ticket_decisions FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);
