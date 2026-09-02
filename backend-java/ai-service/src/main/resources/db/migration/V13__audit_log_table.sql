-- =====================================================================
-- JURIKA Sprint 2 / TASK 6 commit 1 -- audit_log (ai-service)
-- Voir auth-service V8 pour le commentaire complet. Migration idempotente.
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

ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(64);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS payload_diff   JSONB;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS source_service VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_audit_log_workspace_created
    ON audit_log (workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_user_action
    ON audit_log (user_id, action);
CREATE INDEX IF NOT EXISTS idx_audit_log_resource
    ON audit_log (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_correlation
    ON audit_log (correlation_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action_created
    ON audit_log (action, created_at DESC);

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS audit_log_tenant_read ON audit_log;
CREATE POLICY audit_log_tenant_read ON audit_log
    FOR SELECT
    USING (
        current_setting('app.audit_bypass', true) = 'true'
        OR workspace_id::text = current_setting('app.current_workspace_id', true)
    );

DROP POLICY IF EXISTS audit_log_insert ON audit_log;
CREATE POLICY audit_log_insert ON audit_log
    FOR INSERT
    WITH CHECK (true);
