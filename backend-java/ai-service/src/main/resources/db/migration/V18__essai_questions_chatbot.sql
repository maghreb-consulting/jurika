-- =====================================================================
-- JURIKA V18 (ai-service) -- Lot L7 (E4), RG-ESS-04 : 20 questions au
-- chatbot pendant l'essai gratuit.
--
-- Un compteur par cabinet, incremente atomiquement AVANT que la question ne
-- parte au modele (INSERT ... ON CONFLICT DO UPDATE ... WHERE nombre < limite) :
-- deux questions simultanees ne peuvent pas depasser la limite. Le compteur
-- n'est tenu que pendant l'essai ; il disparait avec le cabinet si l'essai est
-- supprime (essai_supprimer_definitivement, auth V41 : table a workspace_id).
-- RLS par workspace ; jurika_app : ni DELETE ni TRUNCATE.
-- =====================================================================

CREATE TABLE IF NOT EXISTS chatbot_questions_essai (
    workspace_id  UUID         PRIMARY KEY,
    nombre        INTEGER      NOT NULL CHECK (nombre >= 0),
    mis_a_jour_le TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE chatbot_questions_essai ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS chatbot_questions_essai_workspace ON chatbot_questions_essai;
CREATE POLICY chatbot_questions_essai_workspace ON chatbot_questions_essai
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        REVOKE ALL ON chatbot_questions_essai FROM jurika_app;
        GRANT SELECT, INSERT, UPDATE ON chatbot_questions_essai TO jurika_app;
    END IF;
END $$;
