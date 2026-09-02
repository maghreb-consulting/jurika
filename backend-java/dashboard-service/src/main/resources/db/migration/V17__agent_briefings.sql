-- =====================================================================
-- JURIKA V17 -- Lot IA-1 : table agent_briefings
--
-- Persiste le "briefing du jour" produit par l'agent copilote de l'employe
-- (observe -> raisonne -> propose). L'agent est STRICTEMENT en lecture sur
-- les tickets / demandes / dossiers : la seule ecriture metier est ici
-- (+ l'envoi d'une notification). Zero effet de bord.
--
-- payload JSONB = signaux bruts (echeances, reste-a-faire) +
--                 plan du jour a base de regles + texte LLM (nullable).
-- summary       = ligne courte (poussee dans la notification).
-- seen_at NULL  = pas encore consulte par l'employe.
--
-- RLS : bloc copie de V16 (dashboard_snapshots) -- isolation par workspace_id
--       + bypass SUPER_ADMIN via app.audit_bypass.
-- =====================================================================

CREATE TABLE IF NOT EXISTS agent_briefings (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID         NOT NULL,
    employee_id  UUID         NOT NULL,
    summary      TEXT         NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    seen_at      TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_agent_briefings_lookup
    ON agent_briefings (workspace_id, employee_id, created_at DESC);

ALTER TABLE agent_briefings ENABLE ROW LEVEL SECURITY;
CREATE POLICY agent_briefings_isolation ON agent_briefings FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid
        OR current_setting('app.audit_bypass', TRUE) = 'true')
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

COMMENT ON TABLE agent_briefings
    IS 'Lot IA-1 : briefing du jour de l''agent copilote (suggestions read-only, l''humain valide).';
