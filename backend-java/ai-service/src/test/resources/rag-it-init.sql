-- Extensions requises AVANT que Flyway (V10) ne cree rag_corpus_chunks :
--   - uuid-ossp : fournit uuid_generate_v4() utilise comme DEFAULT de la PK.
--   - vector    : pgvector, requis par V10 (colonne embedding VECTOR(1536)).
-- L'image pgvector/pgvector:pg16 embarque l'extension vector.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;
