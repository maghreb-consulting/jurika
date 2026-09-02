-- =====================================================================
-- JURIKA V10 -- Corpus juridique indexe pour RAG (Spring AI + pgvector)
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS rag_corpus_chunks (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source        VARCHAR(120) NOT NULL,
    article       VARCHAR(80),
    chunk_text    TEXT         NOT NULL,
    embedding     VECTOR(1536),
    metadata      JSONB,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rag_chunks_source ON rag_corpus_chunks (source);
CREATE INDEX IF NOT EXISTS idx_rag_chunks_embedding
    ON rag_corpus_chunks USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
