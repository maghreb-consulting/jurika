-- =====================================================================
-- JURIKA V5 -- Data Room V2 + Dossier Comptable + Demandes Client
-- Reference : docs/v2/CDC_Professionnel_V2.md, docs/v2/Guide_Workflows_V2.md
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. dataroom_documents : documents juridiques (en vigueur + anciennes versions)
--    Une "version en vigueur" est la derniere par (dossier_id, document_type).
-- ---------------------------------------------------------------------
CREATE TABLE dataroom_documents (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id      UUID         NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    ticket_id       UUID         REFERENCES tickets(id) ON DELETE SET NULL,
    document_type   VARCHAR(40)  NOT NULL
                    CHECK (document_type IN ('STATUTS','PV_AGE','PV_AGO','PV_MODIFICATION','PV_DISSOLUTION',
                                              'PV_LIQUIDATION','ACTE_NOMINATION','CONTRAT_BAIL','CNIE_GERANT',
                                              'ANNONCE_JAL','RC','ICE','TP','CNSS','APOSTILLE','AUTRE')),
    title           VARCHAR(200) NOT NULL,
    version         SMALLINT     NOT NULL DEFAULT 1,
    is_current      BOOLEAN      NOT NULL DEFAULT TRUE,
    object_key      VARCHAR(500) NOT NULL,
    filename        VARCHAR(255) NOT NULL,
    content_type    VARCHAR(120),
    size_bytes      BIGINT       NOT NULL DEFAULT 0,
    uploaded_by     UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    replaced_at     TIMESTAMPTZ
);
CREATE INDEX idx_dataroom_docs_current
    ON dataroom_documents (workspace_id, dossier_id, is_current);
CREATE INDEX idx_dataroom_docs_ticket
    ON dataroom_documents (workspace_id, ticket_id) WHERE ticket_id IS NOT NULL;
CREATE INDEX idx_dataroom_docs_type
    ON dataroom_documents (workspace_id, dossier_id, document_type);

-- ---------------------------------------------------------------------
-- 2. ticket_document_snapshots : anciennes versions remplacees lors d'un ticket
--    Permet l'affichage "Documents remplaces lors de cette operation" (Guide V2).
-- ---------------------------------------------------------------------
CREATE TABLE ticket_document_snapshots (
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id       UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    ticket_id          UUID         NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    document_id        UUID         NOT NULL REFERENCES dataroom_documents(id) ON DELETE CASCADE,
    snapshot_kind      VARCHAR(20)  NOT NULL
                       CHECK (snapshot_kind IN ('REPLACED','GENERATED')),
    captured_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_snapshots_ticket ON ticket_document_snapshots (workspace_id, ticket_id);

-- ---------------------------------------------------------------------
-- 3. dataroom_comptable_documents : Dossier Comptable (annee x categorie)
-- ---------------------------------------------------------------------
CREATE TABLE dataroom_comptable_documents (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id      UUID         NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    annee           SMALLINT     NOT NULL CHECK (annee BETWEEN 2000 AND 2100),
    categorie       VARCHAR(20)  NOT NULL
                    CHECK (categorie IN ('ACHATS','VENTES','BANQUE','CAISSE','NDF','PAIE')),
    title           VARCHAR(200) NOT NULL,
    object_key      VARCHAR(500) NOT NULL,
    filename        VARCHAR(255) NOT NULL,
    content_type    VARCHAR(120),
    size_bytes      BIGINT       NOT NULL DEFAULT 0,
    uploaded_by     UUID         REFERENCES users(id) ON DELETE SET NULL,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_comptable_dossier_year_cat
    ON dataroom_comptable_documents (workspace_id, dossier_id, annee, categorie)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_comptable_years
    ON dataroom_comptable_documents (workspace_id, dossier_id, annee)
    WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- 4. dataroom_demandes_client : demandes soumises depuis le Data Room
-- ---------------------------------------------------------------------
CREATE TABLE dataroom_demandes_client (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id      UUID         NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    soumis_par      UUID         REFERENCES users(id) ON DELETE SET NULL,
    sujet           VARCHAR(200) NOT NULL,
    description     TEXT         NOT NULL,
    statut          VARCHAR(20)  NOT NULL DEFAULT 'NON_TRAITEE'
                    CHECK (statut IN ('NON_TRAITEE','EN_COURS','TRAITEE')),
    ticket_id       UUID         REFERENCES tickets(id) ON DELETE SET NULL,
    pris_en_charge_par UUID      REFERENCES users(id) ON DELETE SET NULL,
    note_interne    TEXT,
    traite_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_demandes_dossier_statut
    ON dataroom_demandes_client (workspace_id, dossier_id, statut);
CREATE INDEX idx_demandes_workspace_statut
    ON dataroom_demandes_client (workspace_id, statut);

-- ---------------------------------------------------------------------
-- 5. RLS sur les 4 tables
-- ---------------------------------------------------------------------
ALTER TABLE dataroom_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_document_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE dataroom_comptable_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE dataroom_demandes_client ENABLE ROW LEVEL SECURITY;

CREATE POLICY dataroom_docs_isolation ON dataroom_documents FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY snapshots_isolation ON ticket_document_snapshots FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY comptable_isolation ON dataroom_comptable_documents FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY demandes_isolation ON dataroom_demandes_client FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);
