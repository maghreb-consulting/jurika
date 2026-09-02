-- =====================================================================
-- JURIKA V6 -- Data Room settings (permissions, access, suspension)
--
-- Aligne le backend sur les fonctionnalites validees de la maquette :
--   - Permissions client par Data Room (view obligatoire, download, print)
--   - Statut d'acces (ACTIVE | SUSPENDED)
--   - Compteur d'acces client (incrementé a chaque GET juridique/comptable)
--   - Token de lien client (URL partageable signee)
-- =====================================================================

CREATE TABLE dataroom_settings (
    dossier_id           UUID PRIMARY KEY REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    workspace_id         UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    access_status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                         CHECK (access_status IN ('ACTIVE','SUSPENDED')),
    perm_download        BOOLEAN      NOT NULL DEFAULT TRUE,
    perm_print           BOOLEAN      NOT NULL DEFAULT FALSE,
    client_link_token    UUID         NOT NULL DEFAULT uuid_generate_v4(),
    access_count         INTEGER      NOT NULL DEFAULT 0,
    last_accessed_at     TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_settings_workspace_status ON dataroom_settings (workspace_id, access_status);
CREATE UNIQUE INDEX idx_settings_token ON dataroom_settings (client_link_token);

ALTER TABLE dataroom_settings ENABLE ROW LEVEL SECURITY;
CREATE POLICY settings_isolation ON dataroom_settings FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

-- Backfill : creer une ligne settings par defaut pour chaque dossier existant.
-- IMPORTANT : on doit positionner app.current_workspace_id par dossier sinon RLS bloque.
-- Pour le seed, on desactive temporairement RLS sur cette table.
ALTER TABLE dataroom_settings DISABLE ROW LEVEL SECURITY;
INSERT INTO dataroom_settings (dossier_id, workspace_id)
SELECT id, workspace_id FROM entreprise_dossiers
ON CONFLICT (dossier_id) DO NOTHING;
ALTER TABLE dataroom_settings ENABLE ROW LEVEL SECURITY;
