-- =====================================================================
-- JURIKA V29 (ticket-service) -- Lot L1, etape E6 : note de ticket (RG-TKT-07)
--
-- Un bloc-notes par ticket, ouvert des la creation (absence de ligne = note vide),
-- validable meme vide, modifiable par l'employe en charge, STRICTEMENT INTERNE :
-- aucun acces client (garde des endpoints, et rien ne la copie en Data Room).
-- =====================================================================
CREATE TABLE ticket_notes (
    ticket_id     UUID PRIMARY KEY REFERENCES tickets(id) ON DELETE CASCADE,
    workspace_id  UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    contenu       TEXT NOT NULL DEFAULT '',
    modifie_par   UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    modifie_le    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ticket_notes_workspace ON ticket_notes (workspace_id);

ALTER TABLE ticket_notes ENABLE ROW LEVEL SECURITY;
CREATE POLICY ticket_notes_isolation ON ticket_notes FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);
