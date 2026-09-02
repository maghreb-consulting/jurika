-- =====================================================================
-- JURIKA V13 -- Dossier Fiscal (Sprint 8)
-- Reference legale : CGI Art. 211 (retention 10 ans), CGI Art. 95-125
-- (TVA), Art. 1-22 (IS), Art. 22-86 (IR), Art. 4-160 (RAS), Art. 137-173
-- (attestations), Art. 220-242 (contentieux).
-- =====================================================================

CREATE TABLE dataroom_fiscal_documents (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id          UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id            UUID         NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    exercice_fiscal_id    UUID         NOT NULL REFERENCES dataroom_exercices_fiscaux(id) ON DELETE RESTRICT,
    -- RG-DF01 : 7 categories CGI Maroc
    categorie             VARCHAR(20)  NOT NULL
                          CHECK (categorie IN
                              ('TVA','IS','IR','TP_TSC','RAS','ATTESTATIONS','CONTENTIEUX')),
    -- RG-DF16 : sous-classification obligatoire selon categorie
    sous_classification   VARCHAR(40)  NOT NULL,
    title                 VARCHAR(200) NOT NULL,
    -- RG-DF23 : commentaire obligatoire min 20 chars pour CONTENTIEUX
    commentaire           TEXT,
    object_key            VARCHAR(500) NOT NULL,
    filename              VARCHAR(255) NOT NULL,
    content_type          VARCHAR(120),
    size_bytes            BIGINT       NOT NULL DEFAULT 0,
    -- RG-DF28 : metadata SIMPL-DGI (preparation export futur)
    tif_metadata          VARCHAR(20),
    numero_declaration    VARCHAR(60),
    periode_declaree      VARCHAR(20), -- ex: "2026-03" ou "2026-T1"
    -- RG-DF27 : liaison optionnelle vers document Comptable source
    comptable_doc_source  UUID         REFERENCES dataroom_comptable_documents(id) ON DELETE SET NULL,
    uploaded_by           UUID         REFERENCES users(id) ON DELETE SET NULL,
    is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at            TIMESTAMPTZ,
    deleted_by            UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- Coherence is_deleted <-> deleted_at
    CHECK ((is_deleted = FALSE AND deleted_at IS NULL)
        OR (is_deleted = TRUE  AND deleted_at IS NOT NULL)),
    -- RG-DF23 : commentaire >= 20 chars si CONTENTIEUX
    CONSTRAINT chk_contentieux_commentaire
        CHECK (categorie <> 'CONTENTIEUX' OR (commentaire IS NOT NULL AND char_length(commentaire) >= 20))
);

CREATE INDEX idx_fiscal_dossier_exercice_cat
    ON dataroom_fiscal_documents (workspace_id, dossier_id, exercice_fiscal_id, categorie)
    WHERE is_deleted = FALSE;
CREATE INDEX idx_fiscal_periode
    ON dataroom_fiscal_documents (workspace_id, periode_declaree)
    WHERE periode_declaree IS NOT NULL AND is_deleted = FALSE;
CREATE INDEX idx_fiscal_comptable_source
    ON dataroom_fiscal_documents (workspace_id, comptable_doc_source)
    WHERE comptable_doc_source IS NOT NULL;

ALTER TABLE dataroom_fiscal_documents ENABLE ROW LEVEL SECURITY;
CREATE POLICY fiscal_isolation ON dataroom_fiscal_documents FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

COMMENT ON TABLE dataroom_fiscal_documents
    IS 'RG-DF01..28 : documents fiscaux par exercice. Retention 10 ans (CGI Art. 211).';
