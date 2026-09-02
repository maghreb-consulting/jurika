-- =====================================================================
-- JURIKA V14 -- Reparation rag_corpus_chunks pour l'ingestion de sources
--               fiables alimentant le RAG du chatbot (FTS sans cle LLM).
--
-- Probleme corrige : CorpusRetriever interrogeait `search_vector` que V10 ne
-- creait JAMAIS -> la requete echouait -> fallback ILIKE vide. De plus aucune
-- colonne de scoping tenant ni de provenance n'existait.
--
-- Ce que cette migration ajoute :
--   - search_vector tsvector GENERATED (french) + index GIN  -> FTS fonctionnel
--   - workspace_id (multi-tenant), uploaded_by (audit), doc_id (regroupement
--     des chunks d'un meme document uploade), chunk_index (ordre)            .
--   - embedding VECTOR(1536) reste NULLABLE (rempli seulement si rag.enabled).
-- =====================================================================

-- Provenance + scoping tenant -------------------------------------------------
ALTER TABLE rag_corpus_chunks
    ADD COLUMN IF NOT EXISTS workspace_id UUID,
    ADD COLUMN IF NOT EXISTS uploaded_by  UUID,
    ADD COLUMN IF NOT EXISTS doc_id       UUID,
    ADD COLUMN IF NOT EXISTS chunk_index  INTEGER;

-- Full-Text Search : colonne generee (toujours synchronisee avec le texte) ----
-- coalesce(article,'') car article est nullable ; chunk_text est NOT NULL.
ALTER TABLE rag_corpus_chunks
    ADD COLUMN IF NOT EXISTS search_vector tsvector
        GENERATED ALWAYS AS (
            to_tsvector('french', coalesce(article, '') || ' ' || coalesce(chunk_text, ''))
        ) STORED;

CREATE INDEX IF NOT EXISTS idx_rag_chunks_fts
    ON rag_corpus_chunks USING GIN (search_vector);

-- Scoping / gestion des sources ----------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rag_chunks_workspace
    ON rag_corpus_chunks (workspace_id);
CREATE INDEX IF NOT EXISTS idx_rag_chunks_doc
    ON rag_corpus_chunks (workspace_id, doc_id);
