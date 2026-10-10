-- =====================================================================
-- JURIKA V35 (dataroom-service) -- Lot L1, etape E17 : historique de l'acces client
--
-- RG-CLI-01 : chaque modification des permissions du client est tracee. L'ecran
-- "Acces du client" affiche cet historique au responsable du dossier et au
-- superviseur : permissions (avant / apres) et suspension ou reactivation.
-- Ecrit dans la meme transaction que le changement (la trace d'audit, elle, passe
-- par RabbitMQ et peut se perdre : backlog L0, AuditEventConsumer).
-- =====================================================================
CREATE TABLE IF NOT EXISTS dataroom_acces_client_historique (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE RESTRICT,
    dossier_id   UUID NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE RESTRICT,
    acteur_id    UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    nature       VARCHAR(20) NOT NULL CHECK (nature IN ('PERMISSIONS', 'SUSPENSION', 'REACTIVATION')),
    avant        JSONB,
    apres        JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_acces_client_historique_dossier
    ON dataroom_acces_client_historique (workspace_id, dossier_id, created_at DESC);

ALTER TABLE dataroom_acces_client_historique ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS acces_client_historique_isolation ON dataroom_acces_client_historique;
CREATE POLICY acces_client_historique_isolation ON dataroom_acces_client_historique FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);
