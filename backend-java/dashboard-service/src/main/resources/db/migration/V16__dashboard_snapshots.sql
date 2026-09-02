-- =====================================================================
-- JURIKA V16 -- Sprint 10 Dashboards : table dashboard_snapshots
--
-- Stocke en best-effort persistent les agregats calcules par
-- dashboard-service (un cache "warm" en plus du cache Redis "hot").
-- Permet la tracabilite (qui a vu quoi quand) et un fallback si
-- Redis tombe.
-- =====================================================================

CREATE TABLE IF NOT EXISTS dashboard_snapshots (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID         NOT NULL,
    scope        VARCHAR(32)  NOT NULL,
    actor_id     UUID,
    payload      JSONB        NOT NULL,
    generated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    valid_until  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_dashboard_scope CHECK (scope IN ('SUPER_ADMIN','SUPERVISEUR','EMPLOYE','CLIENT'))
);

CREATE INDEX IF NOT EXISTS idx_dashboard_snapshots_lookup
    ON dashboard_snapshots (workspace_id, scope, actor_id, valid_until DESC);

ALTER TABLE dashboard_snapshots ENABLE ROW LEVEL SECURITY;
CREATE POLICY dashboard_snapshots_isolation ON dashboard_snapshots FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid
        OR current_setting('app.audit_bypass', TRUE) = 'true')
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

COMMENT ON TABLE dashboard_snapshots
    IS 'Sprint 10 RG-DASH-03 : cache warm/persistent des agregats KPIs par scope. Hot cache = Redis.';
