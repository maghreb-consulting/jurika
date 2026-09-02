-- =====================================================================
-- JURIKA V20 -- Espace « Depots » client (Lot V)
--
-- Table distincte des dossiers Juridique / Comptable / Fiscal : le CLIENT
-- depose librement des fichiers (PDF, image, etc.) SANS categorie, uniquement
-- si perm_depot est accorde (gate applicatif via ClientDataroomPermissionGuard).
-- L'EMPLOYE responsable du dossier (et SUPERVISEUR / SUPER_ADMIN) consulte ces
-- depots : Voir (apercu inline) + Telecharger.
--
-- 1 depot appartient a un dossier ; 1 dataroom <-> 1 client (deja garanti).
--
-- Structure et RLS calquees a l'identique sur dataroom_comptable_documents (V5).
-- =====================================================================

CREATE TABLE dataroom_depots (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id      UUID         NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    title           VARCHAR(200),                 -- optionnel (defaut = filename cote service)
    filename        VARCHAR(255) NOT NULL,
    object_key      VARCHAR(500) NOT NULL,        -- cle MinIO
    content_type    VARCHAR(120),
    size_bytes      BIGINT       NOT NULL DEFAULT 0,
    uploaded_by     UUID         NOT NULL REFERENCES users(id),  -- l'utilisateur qui a depose (CLIENT en pratique)
    deleted_at      TIMESTAMPTZ,                  -- soft-delete
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_depots_dossier
    ON dataroom_depots (workspace_id, dossier_id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_depots_workspace
    ON dataroom_depots (workspace_id);

-- ---------------------------------------------------------------------
-- RLS : isolation multi-tenant par workspace_id (copie de comptable_isolation V5)
-- ---------------------------------------------------------------------
ALTER TABLE dataroom_depots ENABLE ROW LEVEL SECURITY;

CREATE POLICY depots_isolation ON dataroom_depots FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);
