-- =====================================================================
-- JURIKA V15 -- Recherche vectorielle RAG : alignement de la dimension
--               d'embedding sur le provider Gemini text-embedding-004.
--
-- Contexte : V10 avait cree `embedding VECTOR(1536)` (heritage OpenAI
-- ada-002) mais la colonne n'a JAMAIS ete remplie (RAG en FTS seul).
-- Le provider d'embeddings retenu (Gemini text-embedding-004, gratuit)
-- produit des vecteurs de 768 dimensions. On aligne donc la colonne sur
-- 768 et on recree l'index ivfflat cosine.
--
-- Sans danger : toutes les lignes existantes ont embedding = NULL, donc
-- le changement de dimension ne casse aucune donnee (aucune valeur a
-- reinterpreter). Les lignes NULL restent valides et continueront a etre
-- servies par le Full-Text Search (repli).
-- =====================================================================

-- L'index depend de la colonne : on le supprime avant l'ALTER TYPE.
DROP INDEX IF EXISTS idx_rag_chunks_embedding;

-- pgvector autorise le changement de dimension quand la colonne ne
-- contient aucune valeur non-NULL (c'est notre cas).
ALTER TABLE rag_corpus_chunks
    ALTER COLUMN embedding TYPE vector(768);

-- Recreation de l'index ivfflat cosine (idem V10, dimension 768).
CREATE INDEX IF NOT EXISTS idx_rag_chunks_embedding
    ON rag_corpus_chunks USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
