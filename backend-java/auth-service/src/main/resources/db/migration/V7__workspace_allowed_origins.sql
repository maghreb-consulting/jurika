-- Sprint 1 / TASK 10 : CORS dynamique par workspace
-- Permet a chaque cabinet d'autoriser des origines custom (sous-domaines, embed).
-- Lue par DynamicCorsConfigurationSource (gateway) via endpoint /api/v1/admin/cors-origins.

CREATE TABLE IF NOT EXISTS workspace_allowed_origins (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    origin          VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    CONSTRAINT uq_workspace_origin UNIQUE (workspace_id, origin),
    CONSTRAINT chk_origin_format CHECK (origin ~ '^https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?$')
);

CREATE INDEX IF NOT EXISTS idx_workspace_allowed_origins_origin
    ON workspace_allowed_origins (origin);
CREATE INDEX IF NOT EXISTS idx_workspace_allowed_origins_workspace
    ON workspace_allowed_origins (workspace_id);

-- Origines par defaut (dev local + URL SaaS principale)
INSERT INTO workspace_allowed_origins (workspace_id, origin)
SELECT id, 'http://localhost:5173' FROM workspaces
ON CONFLICT (workspace_id, origin) DO NOTHING;

INSERT INTO workspace_allowed_origins (workspace_id, origin)
SELECT id, 'https://app.jurika.ma' FROM workspaces
ON CONFLICT (workspace_id, origin) DO NOTHING;
