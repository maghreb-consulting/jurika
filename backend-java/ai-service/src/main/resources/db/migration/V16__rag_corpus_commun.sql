-- =====================================================================
-- JURIKA V16 (ai-service) -- Lot L5, etape E8 : corpus commun du chatbot
--
-- CDC 3.1 et RG-COM-02 : le chatbot repond uniquement a partir des sources
-- fiables deposees par le super-admin, lues par TOUS les cabinets. Jusqu'ici
-- chaque passage etait range par workspace (CorpusRetriever : WHERE
-- workspace_id = ?) : les sources du super-admin restaient dans SON workspace
-- (celui du cabinet de demonstration) et n'atteignaient aucun autre cabinet.
--
-- Ce que cette migration pose :
--   - colonne `portee` : 'COMMUNE' (source du super-admin, lue par tous) ou
--     'ARCHIVEE' (passage depose par un cabinet avant le lot L0 E24, ou de
--     provenance inconnue : conserve, jamais lu par le chatbot). Le defaut est
--     'ARCHIVEE' : une insertion qui oublie la portee ne publie rien.
--   - une source commune n'appartient a aucun workspace (workspace_id NULL) ;
--     sa provenance reste dans uploaded_by.
--   - reprise : les passages deposes par un utilisateur SUPER_ADMIN deviennent
--     communs ; tous les autres sont archives (purge : A_DECIDER directeur).
--   - RLS : lecture des seules sources communes ; ecriture (ajout, retrait)
--     seulement sous le reglage de transaction app.rag_sources_admin = 'true',
--     pose par ai-service pour le super-admin. Aucune modification en place.
--     ai-service se connecte encore en proprietaire (backlog L0) : la RLS ne
--     s'applique qu'au role d'execution jurika_app ; le filtrage applicatif
--     (portee = 'COMMUNE') tient par lui-meme.
-- =====================================================================

ALTER TABLE rag_corpus_chunks
    ADD COLUMN IF NOT EXISTS portee VARCHAR(16) NOT NULL DEFAULT 'ARCHIVEE';

ALTER TABLE rag_corpus_chunks DROP CONSTRAINT IF EXISTS ck_rag_chunks_portee;
ALTER TABLE rag_corpus_chunks
    ADD CONSTRAINT ck_rag_chunks_portee CHECK (portee IN ('COMMUNE', 'ARCHIVEE'));

-- Reprise des sources existantes. La table users (auth-service) existe sur la
-- base partagee jurika_db ; elle peut manquer sur une base de test isolee.
DO $$
BEGIN
    IF to_regclass('public.users') IS NOT NULL THEN
        UPDATE rag_corpus_chunks c
           SET portee = 'COMMUNE',
               workspace_id = NULL
         WHERE c.doc_id IS NOT NULL
           AND c.uploaded_by IN (SELECT u.id FROM users u WHERE u.role = 'SUPER_ADMIN');
    END IF;
END $$;

-- Pose apres la reprise : une source commune n'a pas de workspace.
ALTER TABLE rag_corpus_chunks DROP CONSTRAINT IF EXISTS ck_rag_chunks_commune_sans_workspace;
ALTER TABLE rag_corpus_chunks
    ADD CONSTRAINT ck_rag_chunks_commune_sans_workspace
        CHECK (portee <> 'COMMUNE' OR workspace_id IS NULL);

CREATE INDEX IF NOT EXISTS idx_rag_chunks_portee_doc
    ON rag_corpus_chunks (portee, doc_id);

-- Row Level Security --------------------------------------------------------
ALTER TABLE rag_corpus_chunks ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS rag_chunks_lecture_commune ON rag_corpus_chunks;
CREATE POLICY rag_chunks_lecture_commune ON rag_corpus_chunks FOR SELECT
    USING (portee = 'COMMUNE');

DROP POLICY IF EXISTS rag_chunks_ajout_super_admin ON rag_corpus_chunks;
CREATE POLICY rag_chunks_ajout_super_admin ON rag_corpus_chunks FOR INSERT
    WITH CHECK (portee = 'COMMUNE'
                AND workspace_id IS NULL
                AND current_setting('app.rag_sources_admin', TRUE) = 'true');

DROP POLICY IF EXISTS rag_chunks_retrait_super_admin ON rag_corpus_chunks;
CREATE POLICY rag_chunks_retrait_super_admin ON rag_corpus_chunks FOR DELETE
    USING (portee = 'COMMUNE'
           AND current_setting('app.rag_sources_admin', TRUE) = 'true');

-- Droits du role d'execution, s'il existe (ai-service n'y est pas encore bascule).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        GRANT SELECT, INSERT, DELETE ON rag_corpus_chunks TO jurika_app;
    END IF;
END $$;
