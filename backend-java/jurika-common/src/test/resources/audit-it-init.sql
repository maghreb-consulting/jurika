-- AuditAspectIT (Sprint 14 bis / C3) -- bootstrap minimal du schema audit_log
-- aligne sur V8__audit_log_table.sql d'auth-service. Permet de tester l'aspect
-- en isolation sans charger l'ensemble des migrations Flyway d'un service.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- L'utilisateur audit_it est superuser (bootstrap, ne peut pas etre retrograde
-- depuis Postgres 16). On cree un role non-superuser dedie pour valider que la
-- RLS policy fonctionne reellement -- les tests 4/5 utilisent SET LOCAL ROLE.
CREATE ROLE app_no_super NOLOGIN NOSUPERUSER NOBYPASSRLS;

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
    correlation_id  VARCHAR(64),
    payload_diff    JSONB,
    source_service  VARCHAR(40),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

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

-- Grant le minimum requis pour que SET LOCAL ROLE app_no_super dans les tests
-- puisse interroger la table (sinon : permission denied).
GRANT SELECT, INSERT, DELETE ON TABLE audit_log TO app_no_super;
GRANT USAGE, SELECT ON SEQUENCE audit_log_id_seq TO app_no_super;
