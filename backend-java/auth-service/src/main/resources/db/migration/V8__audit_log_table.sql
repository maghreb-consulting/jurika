-- =====================================================================
-- JURIKA Sprint 2 / TASK 6 commit 1 -- audit_log (auth-service)
--
-- Toutes les apps partagent jurika_db, donc une seule table physique
-- audit_log existe deja (creee par ticket V5 / workflow V12). Cette
-- migration est idempotente : elle complete la structure si besoin et
-- ajoute la RLS multi-tenant (RG-SAAS-01 + RG-SAAS-02).
--
-- Bypass SUPER_ADMIN : SET LOCAL app.audit_bypass = 'true' au debut
-- d'une requete admin permet de lire cross-workspace.
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

-- Colonnes Sprint 2 (correlation_id du MDC, payload_diff structure)
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(64);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS payload_diff   JSONB;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS source_service VARCHAR(40);

-- Indexes (idempotents, alignes sur le plan TASK 6)
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

-- Row Level Security multi-tenant
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;

-- Policy SELECT : workspace courant OU bypass SUPER_ADMIN.
DROP POLICY IF EXISTS audit_log_tenant_read ON audit_log;
CREATE POLICY audit_log_tenant_read ON audit_log
    FOR SELECT
    USING (
        current_setting('app.audit_bypass', true) = 'true'
        OR workspace_id::text = current_setting('app.current_workspace_id', true)
    );

-- Policy INSERT : toujours autorisee (workers RabbitMQ ecrivent depuis
-- contextes varies). Le workspace_id est valide cote application.
DROP POLICY IF EXISTS audit_log_insert ON audit_log;
CREATE POLICY audit_log_insert ON audit_log
    FOR INSERT
    WITH CHECK (true);
