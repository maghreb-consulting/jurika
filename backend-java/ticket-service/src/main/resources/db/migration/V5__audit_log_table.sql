-- =====================================================================
-- JURIKA V5 -- Table audit_log pour ticket-service
-- Reference : Killer Feature §4.7 (Activity Feed Linear-style)
-- =====================================================================

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGSERIAL    PRIMARY KEY,
    workspace_id    UUID,
    user_id         UUID,
    action          VARCHAR(80)  NOT NULL,
    entity_type     VARCHAR(80),
    entity_id       UUID,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(255),
    metadata        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_log_workspace ON audit_log (workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_user      ON audit_log (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_action    ON audit_log (action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity    ON audit_log (entity_type, entity_id, created_at DESC);

-- Append-only : pas de RLS pour permettre la consultation cross-workspace par SUPER_ADMIN.
-- Le filtrage par workspace est fait au niveau de la couche application.
