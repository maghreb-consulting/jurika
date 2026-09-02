-- Enable Row Level Security on all tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;

ALTER TABLE entreprise_dossiers ENABLE ROW LEVEL SECURITY;
ALTER TABLE entreprise_dossiers FORCE ROW LEVEL SECURITY;

ALTER TABLE fiche_juridique ENABLE ROW LEVEL SECURITY;
ALTER TABLE fiche_juridique FORCE ROW LEVEL SECURITY;

ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents FORCE ROW LEVEL SECURITY;

ALTER TABLE tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE tickets FORCE ROW LEVEL SECURITY;

ALTER TABLE evenements_juridiques ENABLE ROW LEVEL SECURITY;
ALTER TABLE evenements_juridiques FORCE ROW LEVEL SECURITY;

ALTER TABLE historique ENABLE ROW LEVEL SECURITY;
ALTER TABLE historique FORCE ROW LEVEL SECURITY;

ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications FORCE ROW LEVEL SECURITY;

ALTER TABLE chatbot_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE chatbot_history FORCE ROW LEVEL SECURITY;

ALTER TABLE transferts_dossiers ENABLE ROW LEVEL SECURITY;
ALTER TABLE transferts_dossiers FORCE ROW LEVEL SECURITY;

-- RLS Policies
CREATE POLICY tenant_isolation_users ON users
    USING (workspace_id::text = current_setting('app.current_tenant_id', TRUE));

CREATE POLICY tenant_isolation_dossiers ON entreprise_dossiers
    USING (workspace_id::text = current_setting('app.current_tenant_id', TRUE));

CREATE POLICY tenant_isolation_fiche ON fiche_juridique
    USING (workspace_id::text = current_setting('app.current_tenant_id', TRUE));

CREATE POLICY tenant_isolation_documents ON documents
    USING (workspace_id::text = current_setting('app.current_tenant_id', TRUE));

CREATE POLICY tenant_isolation_tickets ON tickets
    USING (workspace_id::text = current_setting('app.current_tenant_id', TRUE));

CREATE POLICY tenant_isolation_evenements ON evenements_juridiques
    USING (workspace_id::text = current_setting('app.current_tenant_id', TRUE));

CREATE POLICY tenant_isolation_historique ON historique
    USING (workspace_id::text = current_setting('app.current_tenant_id', TRUE));

CREATE POLICY tenant_isolation_notifications ON notifications
    USING (workspace_id::text = current_setting('app.current_tenant_id', TRUE));

CREATE POLICY tenant_isolation_chatbot ON chatbot_history
    USING (workspace_id::text = current_setting('app.current_tenant_id', TRUE));

CREATE POLICY tenant_isolation_transferts ON transferts_dossiers
    USING (workspace_id::text = current_setting('app.current_tenant_id', TRUE));
