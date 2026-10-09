-- Lot L0 (E10a) : schema minimal pour TenantTransactionIT.
-- Une table sous RLS de forme standard, une table calquee sur audit_log (trois
-- politiques permissives, FORCE), un role d'execution non superutilisateur.

CREATE TABLE rls_probe (
    id           UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    libelle      TEXT
);
ALTER TABLE rls_probe ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_probe_isolation ON rls_probe
    FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

INSERT INTO rls_probe VALUES
    ('a0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'A1'),
    ('a0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'A2'),
    ('b0000000-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'B1');

CREATE TABLE audit_probe (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id UUID,
    action       TEXT
);
ALTER TABLE audit_probe ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_probe FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_probe_isolation ON audit_probe
    FOR ALL
    USING (workspace_id IS NULL OR workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id IS NULL OR workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);
CREATE POLICY audit_probe_tenant_read ON audit_probe
    FOR SELECT
    USING (current_setting('app.audit_bypass', true) = 'true'
           OR workspace_id::text = current_setting('app.current_workspace_id', true));
CREATE POLICY audit_probe_insert ON audit_probe
    FOR INSERT
    WITH CHECK (true);

CREATE ROLE rls_app LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'rls_app';
GRANT SELECT, INSERT, UPDATE, DELETE ON rls_probe, audit_probe TO rls_app;
GRANT USAGE, SELECT ON SEQUENCE audit_probe_id_seq TO rls_app;
