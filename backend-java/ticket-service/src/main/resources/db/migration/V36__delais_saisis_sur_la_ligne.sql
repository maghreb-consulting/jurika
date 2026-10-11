-- =====================================================================
-- JURIKA V36 -- Lot L4b (E5, RG-DEM-06) : delais des demarches du corpus.
--
-- Un delai du corpus se calcule depuis un point de depart ; quand la
-- matrice ne le nomme pas, ou que la donnee qui le porte n'est pas saisie,
-- la plateforme le DEMANDE SUR LA LIGNE (RG-DEM-06). De meme pour une date
-- limite "au plus tard le $VAR" non saisie. Rien n'est deduit.
-- =====================================================================

CREATE TABLE IF NOT EXISTS ticket_demarche_delais (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    ticket_id    UUID        NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    demarche_id  UUID        NOT NULL REFERENCES demarches_referentiel(id),
    depart       DATE,
    echeance     DATE,
    saisi_par    UUID,
    saisi_le     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_delai_saisi CHECK (depart IS NOT NULL OR echeance IS NOT NULL),
    UNIQUE (ticket_id, demarche_id)
);

ALTER TABLE ticket_demarche_delais ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS ticket_demarche_delais_isolation ON ticket_demarche_delais;
CREATE POLICY ticket_demarche_delais_isolation ON ticket_demarche_delais FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);
