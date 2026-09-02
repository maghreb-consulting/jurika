-- =====================================================================
-- JURIKA V10 -- RG-DR-FTS : recherche full-text PostgreSQL sur les
-- documents juridiques (titre + filename)
--
-- Reference : docs/v2/PLAN_SPRINT_7_DATAROOM_V2_FINITION.md TASK 1
--
-- Strategie :
--  * colonne search_vector tsvector GENERATED ALWAYS AS ... STORED
--    -> Postgres recalcule a chaque insert/update, zero overhead applicatif
--  * pondération A sur title (poids fort), B sur filename (poids moyen)
--  * dictionnaire 'french' : couvre les Statuts, PV, contrats. Pour les
--    abreviations metier rares (PV AGE, ICE, RC), websearch_to_tsquery
--    les conserve telles quelles (pas de lemmatisation -> matching exact).
--  * Index GIN dedie pour < 100ms sur N=50 docs (cible plan TASK 1)
-- =====================================================================

ALTER TABLE dataroom_documents
    ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('french', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('french', coalesce(filename, '')), 'B')
    ) STORED;

CREATE INDEX idx_dataroom_docs_fts
    ON dataroom_documents USING GIN (search_vector);

COMMENT ON COLUMN dataroom_documents.search_vector
    IS 'RG-DR-FTS : tsvector GENERATED pour recherche full-text. Read-only cote applicatif.';
