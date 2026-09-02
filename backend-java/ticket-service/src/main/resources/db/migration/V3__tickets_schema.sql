-- =====================================================================
-- JURIKA V3 — Tickets, dossiers d'entreprise, debours, documents
-- =====================================================================

-- ---------------------------------------------------------------------
-- entreprise_dossiers : une societe = un dossier (cree par workflow Creation ou Import)
-- ---------------------------------------------------------------------
CREATE TABLE entreprise_dossiers (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    raison_sociale  VARCHAR(200) NOT NULL,
    forme_juridique VARCHAR(20)  NOT NULL CHECK (forme_juridique IN ('SARL','SARL_AU','SA','SAS','SCS','GIE')),
    ice             VARCHAR(20),
    rc_numero       VARCHAR(50),
    rc_tribunal     VARCHAR(100),
    identifiant_fiscal VARCHAR(50),
    taxe_professionnelle VARCHAR(50),
    cnss            VARCHAR(50),
    adresse_siege   TEXT,
    ville           VARCHAR(100),
    capital_social_mad NUMERIC(15,2),
    date_constitution DATE,
    statut          VARCHAR(30)  NOT NULL DEFAULT 'EN_CONSTITUTION'
                    CHECK (statut IN ('EN_CONSTITUTION','ACTIVE','DISSOUTE','EN_LIQUIDATION','LIQUIDEE','RADIE')),
    client_id       UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dossiers_workspace ON entreprise_dossiers (workspace_id);
CREATE INDEX idx_dossiers_ice ON entreprise_dossiers (workspace_id, ice);
CREATE INDEX idx_dossiers_raison ON entreprise_dossiers (workspace_id, raison_sociale);

-- ---------------------------------------------------------------------
-- tickets : tout le travail est porte par un ticket
-- ---------------------------------------------------------------------
CREATE TABLE tickets (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    reference       VARCHAR(20)  NOT NULL,
    titre           VARCHAR(200) NOT NULL,
    type            VARCHAR(40)  NOT NULL
                    CHECK (type IN ('CREATION','IMPORT','MODIFICATION','DISSOLUTION','LIQUIDATION',
                                    'SUCCURSALE_MA','SUCCURSALE_ETR','FERMETURE_SUCCURSALE','PV_AGO')),
    statut          VARCHAR(20)  NOT NULL DEFAULT 'NOUVEAU'
                    CHECK (statut IN ('NOUVEAU','EN_COURS','CLOTURE','ANNULE')),
    priorite        VARCHAR(10)  NOT NULL DEFAULT 'NORMALE'
                    CHECK (priorite IN ('BASSE','NORMALE','HAUTE','URGENTE')),
    dossier_id      UUID         REFERENCES entreprise_dossiers(id) ON DELETE SET NULL,
    assigne_id      UUID         REFERENCES users(id) ON DELETE SET NULL,
    cree_par_id     UUID         NOT NULL REFERENCES users(id),
    description     TEXT,
    deadline        TIMESTAMPTZ,
    annulation_motif TEXT,
    cloture_at      TIMESTAMPTZ,
    annule_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ticket_ref UNIQUE (workspace_id, reference)
);
CREATE INDEX idx_tickets_workspace_statut ON tickets (workspace_id, statut);
CREATE INDEX idx_tickets_assigne ON tickets (workspace_id, assigne_id, statut);
CREATE INDEX idx_tickets_type ON tickets (workspace_id, type);
CREATE INDEX idx_tickets_dossier ON tickets (workspace_id, dossier_id);

-- ---------------------------------------------------------------------
-- ticket_debours : etat des debours (depenses du dossier)
-- ---------------------------------------------------------------------
CREATE TABLE ticket_debours (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    ticket_id       UUID         NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    libelle         VARCHAR(200) NOT NULL,
    categorie       VARCHAR(40)  NOT NULL
                    CHECK (categorie IN ('FRAIS_TRIBUNAL','FRAIS_NOTARIE','FRAIS_ENREGISTREMENT',
                                          'PUBLICATION_JAL','PUBLICATION_BO','HONORAIRES','TRANSPORT','AUTRE')),
    montant_mad     NUMERIC(12,2) NOT NULL CHECK (montant_mad >= 0),
    date_engagement DATE         NOT NULL DEFAULT CURRENT_DATE,
    piece_jointe_url TEXT,
    piece_jointe_filename VARCHAR(255),
    notes           TEXT,
    created_by_id   UUID         NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_debours_ticket ON ticket_debours (workspace_id, ticket_id);
CREATE INDEX idx_debours_date ON ticket_debours (workspace_id, date_engagement);

-- ---------------------------------------------------------------------
-- ticket_comments : commentaires + cloture + annulation (commentaire obligatoire annulation)
-- ---------------------------------------------------------------------
CREATE TABLE ticket_comments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    ticket_id       UUID         NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    auteur_id       UUID         NOT NULL REFERENCES users(id),
    type            VARCHAR(30)  NOT NULL DEFAULT 'COMMENTAIRE'
                    CHECK (type IN ('COMMENTAIRE','TRANSITION_STATUT','ANNULATION','CLOTURE')),
    contenu         TEXT         NOT NULL,
    metadata        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_comments_ticket ON ticket_comments (workspace_id, ticket_id, created_at DESC);

-- ---------------------------------------------------------------------
-- documents : tout document attache a un ticket (genere ou uploade)
-- ---------------------------------------------------------------------
CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    ticket_id       UUID         REFERENCES tickets(id) ON DELETE SET NULL,
    dossier_id      UUID         REFERENCES entreprise_dossiers(id) ON DELETE SET NULL,
    type            VARCHAR(60)  NOT NULL,
    titre           VARCHAR(255) NOT NULL,
    storage_key     TEXT         NOT NULL,
    mime_type       VARCHAR(100),
    taille_octets   BIGINT,
    version         INTEGER      NOT NULL DEFAULT 1,
    statut          VARCHAR(20)  NOT NULL DEFAULT 'BROUILLON'
                    CHECK (statut IN ('BROUILLON','VALIDE','ARCHIVE')),
    source          VARCHAR(20)  NOT NULL DEFAULT 'UPLOAD'
                    CHECK (source IN ('UPLOAD','IA_GENERE','OCR')),
    uploaded_by_id  UUID         REFERENCES users(id),
    metadata        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_documents_workspace ON documents (workspace_id);
CREATE INDEX idx_documents_ticket ON documents (workspace_id, ticket_id);
CREATE INDEX idx_documents_dossier ON documents (workspace_id, dossier_id);
CREATE INDEX idx_documents_type ON documents (workspace_id, type, statut);

-- ---------------------------------------------------------------------
-- RLS sur toutes les tables
-- ---------------------------------------------------------------------
ALTER TABLE entreprise_dossiers ENABLE ROW LEVEL SECURITY;
ALTER TABLE tickets             ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_debours      ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_comments     ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents           ENABLE ROW LEVEL SECURITY;

CREATE POLICY dossier_isolation ON entreprise_dossiers FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY ticket_isolation ON tickets FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY debours_isolation ON ticket_debours FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY ticket_comment_isolation ON ticket_comments FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY document_isolation ON documents FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

-- ---------------------------------------------------------------------
-- Triggers updated_at
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_dossiers_updated_at  BEFORE UPDATE ON entreprise_dossiers FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
CREATE TRIGGER trg_tickets_updated_at   BEFORE UPDATE ON tickets             FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
CREATE TRIGGER trg_debours_updated_at   BEFORE UPDATE ON ticket_debours      FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
CREATE TRIGGER trg_documents_updated_at BEFORE UPDATE ON documents           FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- ---------------------------------------------------------------------
-- Sequence pour reference ticket auto (T-2026-NNNNN)
-- ---------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS ticket_ref_seq START 1;
