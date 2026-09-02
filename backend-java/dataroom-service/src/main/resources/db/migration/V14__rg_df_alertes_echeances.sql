-- =====================================================================
-- JURIKA V14 -- RG-DF20 : alertes echeances DGI Maroc
-- 10 types d'echeances couvrant TVA / IS / IR / TP-TSC / RAS / Etat 9421
-- Reference : CGI Art. 110-111, 169, 20, 22, 156, 82 ; Loi 47-06 art. 13
-- =====================================================================

CREATE TABLE dataroom_alertes_echeances (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id        UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id          UUID         NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    exercice_fiscal_id  UUID         NOT NULL REFERENCES dataroom_exercices_fiscaux(id) ON DELETE CASCADE,
    type_echeance       VARCHAR(40)  NOT NULL
                        CHECK (type_echeance IN
                            ('TVA_MENSUELLE','TVA_TRIMESTRIELLE',
                             'IS_ACOMPTE_T1','IS_ACOMPTE_T2','IS_ACOMPTE_T3','IS_ACOMPTE_T4',
                             'IS_DECLARATION_ANNUELLE','TP_TSC_DECLARATION',
                             'ETAT_9421','IR_DECLARATION_ANNUELLE')),
    date_echeance       DATE         NOT NULL,
    date_alerte         DATE         NOT NULL, -- J-15 par defaut
    statut              VARCHAR(20)  NOT NULL DEFAULT 'PLANIFIEE'
                        CHECK (statut IN ('PLANIFIEE','ENVOYEE','TRAITEE','EXPIREE')),
    sent_at             TIMESTAMPTZ,
    traite_par          UUID         REFERENCES users(id) ON DELETE SET NULL,
    traite_at           TIMESTAMPTZ,
    document_id         UUID         REFERENCES dataroom_fiscal_documents(id) ON DELETE SET NULL,
    note                TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_id, dossier_id, exercice_fiscal_id, type_echeance, date_echeance)
);

CREATE INDEX idx_alertes_due_planifiees
    ON dataroom_alertes_echeances (date_alerte, statut)
    WHERE statut = 'PLANIFIEE';

CREATE INDEX idx_alertes_dossier_exercice
    ON dataroom_alertes_echeances (workspace_id, dossier_id, exercice_fiscal_id);

ALTER TABLE dataroom_alertes_echeances ENABLE ROW LEVEL SECURITY;
CREATE POLICY alertes_isolation ON dataroom_alertes_echeances FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

COMMENT ON TABLE dataroom_alertes_echeances
    IS 'RG-DF20 : alertes auto J-15 sur 10 types d echeances DGI Maroc.';
