-- =====================================================================
-- JURIKA V4 -- Auto-computed Deadlines (Killer Feature)
-- Reference : docs/v2/AGENT_BRIEF_RECONCILIATION.md section 4.2
--             docs/v2/CDC_Professionnel_V2.md (Module 6 Tickets)
--             RG-CN-90j (validite CN), RG-RC-3M (depot RC), RG-CNSS-30j
-- =====================================================================

CREATE TABLE deadlines (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    ticket_id       UUID         REFERENCES tickets(id) ON DELETE CASCADE,
    dossier_id      UUID         REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    due_at          TIMESTAMPTZ  NOT NULL,
    severity        VARCHAR(10)  NOT NULL DEFAULT 'INFO'
                    CHECK (severity IN ('INFO','WARNING','CRITICAL')),
    source          VARCHAR(10)  NOT NULL CHECK (source IN ('AUTO','MANUAL')),
    rule_key        VARCHAR(60),
    statut          VARCHAR(20)  NOT NULL DEFAULT 'OUVERTE'
                    CHECK (statut IN ('OUVERTE','TERMINEE','IGNOREE')),
    assigne_id      UUID         REFERENCES users(id) ON DELETE SET NULL,
    cree_par_id     UUID         REFERENCES users(id) ON DELETE SET NULL,
    terminee_at     TIMESTAMPTZ,
    ignoree_at      TIMESTAMPTZ,
    metadata        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Unicite : pour les deadlines AUTO, eviter les doublons sur (ticket, regle)
CREATE UNIQUE INDEX uq_deadline_ticket_rule
    ON deadlines (workspace_id, ticket_id, rule_key)
    WHERE source = 'AUTO' AND ticket_id IS NOT NULL AND rule_key IS NOT NULL;

-- Index principal : liste des deadlines ouvertes triees par echeance
CREATE INDEX idx_deadlines_open_due
    ON deadlines (workspace_id, due_at)
    WHERE statut = 'OUVERTE';

CREATE INDEX idx_deadlines_ticket
    ON deadlines (workspace_id, ticket_id)
    WHERE ticket_id IS NOT NULL;

CREATE INDEX idx_deadlines_dossier
    ON deadlines (workspace_id, dossier_id)
    WHERE dossier_id IS NOT NULL;

CREATE INDEX idx_deadlines_assigne
    ON deadlines (workspace_id, assigne_id, statut)
    WHERE assigne_id IS NOT NULL;

-- RLS multi-tenant
ALTER TABLE deadlines ENABLE ROW LEVEL SECURITY;
CREATE POLICY deadline_isolation ON deadlines FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE TRIGGER trg_deadlines_updated_at
    BEFORE UPDATE ON deadlines
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
