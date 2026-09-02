-- =====================================================================
-- JURIKA V11 -- RG-DR-ACCESS-LOG : tracage detaille des acces client
--                                  au Data Room
--
-- Reference : docs/v2/PLAN_SPRINT_7_DATAROOM_V2_FINITION.md TASK 5
--
-- Pourquoi separe de audit_log ?
--   audit_log (V9) trace les actions metier de TOUS les roles (RG-SAAS-02).
--   Ce log-ci est cible cote CLIENT : qui voit/telecharge/imprime quoi sur
--   un dossier donne, avec IP + User-Agent. Plus dense et plus utile pour
--   le rapport d'activite affiche au cabinet.
-- =====================================================================

CREATE TABLE dataroom_client_access_log (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id  UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id    UUID         NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    user_id       UUID         REFERENCES users(id) ON DELETE SET NULL,
    document_id   UUID         REFERENCES dataroom_documents(id) ON DELETE SET NULL,
    action        VARCHAR(20)  NOT NULL
                  CHECK (action IN ('VIEW_DOSSIER','PREVIEW_DOC','DOWNLOAD_DOC','PRINT_DOC')),
    ip_address    INET,
    user_agent    VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_access_log_dossier_date
    ON dataroom_client_access_log (workspace_id, dossier_id, created_at DESC);
CREATE INDEX idx_access_log_user
    ON dataroom_client_access_log (workspace_id, user_id, created_at DESC);

ALTER TABLE dataroom_client_access_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY access_log_isolation ON dataroom_client_access_log FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

COMMENT ON TABLE dataroom_client_access_log
    IS 'RG-DR-ACCESS-LOG : trace detaillee des acces client (Sprint 7 TASK 5). Inserts async via ClientAccessLogAspect.';
