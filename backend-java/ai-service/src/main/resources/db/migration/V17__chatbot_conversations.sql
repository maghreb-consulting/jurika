-- =====================================================================
-- JURIKA V17 (ai-service) -- Lot L5b, etape E2 : conversations du chatbot
--
-- RG-COM-12 : chaque utilisateur retrouve ses conversations avec le chatbot
-- (nouvelle, reprise, titre tire de la premiere question et modifiable,
-- archivage ; une conversation archivee reste consultable). Les conversations
-- sont propres a chaque utilisateur et jamais visibles par un autre, meme du
-- meme cabinet. Jusqu'ici l'echange etait garde dans le navigateur
-- (localStorage, une seule conversation).
--
-- Ce que cette migration pose :
--   - chatbot_conversations : une conversation d'un utilisateur d'un cabinet ;
--     updated_at = derniere activite (tri de la liste) ; archivee + archived_at.
--   - chatbot_echanges : une question et sa reponse (texte, sources citees,
--     mode de reponse). Une question refusee par le filtre des donnees
--     personnelles (RG-COM-04) n'est jamais enregistree (controle applicatif
--     AVANT toute ecriture).
--   - workspace_id ET user_id sur les deux tables (le filtrage applicatif
--     porte sur les deux).
--   - RLS sur le workspace (app.current_workspace_id, pose localement par
--     ai-service dans chaque transaction). ai-service se connecte encore en
--     proprietaire (backlog L0) : la RLS ne s'applique qu'au role d'execution
--     jurika_app ; le filtrage applicatif (workspace_id ET user_id) tient par
--     lui-meme.
--   - Pas de cle etrangere vers workspaces / users (tables d'auth-service sur
--     la base partagee, dont l'ordre de migration n'est pas garanti ici).
-- =====================================================================

CREATE TABLE IF NOT EXISTS chatbot_conversations (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID         NOT NULL,
    user_id       UUID         NOT NULL,
    titre         VARCHAR(120) NOT NULL,
    archivee      BOOLEAN      NOT NULL DEFAULT FALSE,
    archived_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_chatbot_conv_titre_non_vide CHECK (length(btrim(titre)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_chatbot_conv_proprietaire
    ON chatbot_conversations (workspace_id, user_id, archivee, updated_at DESC);

CREATE TABLE IF NOT EXISTS chatbot_echanges (
    id               UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID             NOT NULL REFERENCES chatbot_conversations (id) ON DELETE CASCADE,
    workspace_id     UUID             NOT NULL,
    user_id          UUID             NOT NULL,
    question         TEXT             NOT NULL,
    reponse          TEXT             NOT NULL,
    sources          JSONB            NOT NULL DEFAULT '[]'::jsonb,
    rag_mode         VARCHAR(32),
    confidence       DOUBLE PRECISION,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_chatbot_echanges_conversation
    ON chatbot_echanges (conversation_id, created_at);

-- Row Level Security --------------------------------------------------------
ALTER TABLE chatbot_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE chatbot_echanges ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS chatbot_conv_workspace ON chatbot_conversations;
CREATE POLICY chatbot_conv_workspace ON chatbot_conversations
    USING (workspace_id::text = current_setting('app.current_workspace_id', TRUE))
    WITH CHECK (workspace_id::text = current_setting('app.current_workspace_id', TRUE));

DROP POLICY IF EXISTS chatbot_echanges_workspace ON chatbot_echanges;
CREATE POLICY chatbot_echanges_workspace ON chatbot_echanges
    USING (workspace_id::text = current_setting('app.current_workspace_id', TRUE))
    WITH CHECK (workspace_id::text = current_setting('app.current_workspace_id', TRUE));

-- Droits du role d'execution, s'il existe (ai-service n'y est pas encore bascule).
-- Aucune suppression : l'archivage remplace la suppression (RG-COM-12). Les
-- privileges par defaut du schema (init-db.sh, migrations "droits_role_applicatif")
-- accordent DELETE et TRUNCATE sur toute nouvelle table : ils sont retires ici,
-- explicitement, faute de quoi la regle ne serait qu'un commentaire.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        GRANT SELECT, INSERT, UPDATE ON chatbot_conversations TO jurika_app;
        GRANT SELECT, INSERT ON chatbot_echanges TO jurika_app;
        REVOKE DELETE, TRUNCATE ON chatbot_conversations FROM jurika_app;
        REVOKE UPDATE, DELETE, TRUNCATE ON chatbot_echanges FROM jurika_app;
    END IF;
END $$;
