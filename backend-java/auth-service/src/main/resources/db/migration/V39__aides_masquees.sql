-- =====================================================================
-- JURIKA V39 (auth-service) -- Lot L6, etape E6 : aides masquees.
--
-- CDC 15.4 : "Une aide deja vue peut etre masquee et reaffichee." Le choix
-- etait retenu dans le navigateur (localStorage) : il ne suivait pas
-- l'utilisateur d'un poste a l'autre. Cette table le retient cote serveur, par
-- utilisateur : une ligne = une aide masquee (encadre d'aide, visite guidee
-- "visite-<role>"). Reafficher une aide supprime sa ligne.
--
-- Isolation : RLS par workspace (app.current_workspace_id) ET filtrage
-- applicatif explicite sur user_id et workspace_id (regle du projet : toujours
-- les deux).
--
-- Droits de jurika_app : SELECT, INSERT, DELETE seulement. Masquer est un
-- INSERT ... ON CONFLICT DO NOTHING ; reafficher est un DELETE. Aucun UPDATE
-- (les droits par defaut de V32 l'accordent : il est retire).
-- =====================================================================

CREATE TABLE IF NOT EXISTS aides_masquees (
    user_id       UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id  UUID          NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    cle           VARCHAR(120)  NOT NULL CHECK (cle ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,119}$'),
    masquee_le    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, cle)
);

CREATE INDEX IF NOT EXISTS idx_aides_masquees_workspace ON aides_masquees (workspace_id);

ALTER TABLE aides_masquees ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS aides_masquees_isolation ON aides_masquees;
CREATE POLICY aides_masquees_isolation ON aides_masquees FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        REVOKE ALL ON aides_masquees FROM jurika_app;
        GRANT SELECT, INSERT, DELETE ON aides_masquees TO jurika_app;
    END IF;
END
$$;
