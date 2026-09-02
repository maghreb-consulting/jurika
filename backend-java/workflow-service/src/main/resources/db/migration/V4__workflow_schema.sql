-- =====================================================================
-- JURIKA V4 — Workflow progress, fiche juridique, representants, associes
-- =====================================================================

CREATE TABLE workflow_progress (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    ticket_id       UUID         NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    workflow_type   VARCHAR(40)  NOT NULL,
    current_step    SMALLINT     NOT NULL DEFAULT 1,
    total_steps     SMALLINT     NOT NULL,
    data            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    statut          VARCHAR(20)  NOT NULL DEFAULT 'EN_COURS'
                    CHECK (statut IN ('EN_COURS','TERMINE','ABANDONNE')),
    started_by_id   UUID         NOT NULL REFERENCES users(id),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_workflow_ticket UNIQUE (ticket_id)
);
CREATE INDEX idx_workflow_progress_workspace ON workflow_progress (workspace_id);
CREATE INDEX idx_workflow_progress_ticket ON workflow_progress (workspace_id, ticket_id);

-- ---------------------------------------------------------------------
-- fiche_juridique : MAJ apres chaque operation reussie
-- ---------------------------------------------------------------------
CREATE TABLE fiche_juridique (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id      UUID         NOT NULL UNIQUE REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    objet_social    TEXT,
    activite_principale TEXT,
    activite_reglementee BOOLEAN  NOT NULL DEFAULT FALSE,
    duree_annees    INTEGER,
    date_debut_exercice DATE,
    capital_social_mad NUMERIC(15,2),
    capital_libere_mad NUMERIC(15,2),
    nombre_parts    INTEGER,
    valeur_nominale_mad NUMERIC(15,2),
    tribunal_competent VARCHAR(100),
    siege_adresse   TEXT,
    siege_province  VARCHAR(100),
    siege_commune   VARCHAR(100),
    siege_code_postal VARCHAR(10),
    cn_numero       VARCHAR(50),
    cn_date         DATE,
    cn_beneficiaire VARCHAR(200),
    cn_activite     TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- representants : gerants + actes nomination (PP ou PM)
-- ---------------------------------------------------------------------
CREATE TABLE representants (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id      UUID         NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    type_personne   VARCHAR(10)  NOT NULL CHECK (type_personne IN ('PP','PM')),
    nom             VARCHAR(100) NOT NULL,
    prenom          VARCHAR(100),
    genre           VARCHAR(10)  CHECK (genre IN ('M','F')),
    date_naissance  DATE,
    cin_numero      VARCHAR(30),
    nationalite     VARCHAR(80),
    adresse         TEXT,
    raison_sociale_pm VARCHAR(200),
    rc_pm           VARCHAR(50),
    ice_pm          VARCHAR(20),
    is_statutaire   BOOLEAN      NOT NULL DEFAULT TRUE,
    is_associe      BOOLEAN      NOT NULL DEFAULT FALSE,
    fonction        VARCHAR(80)  NOT NULL DEFAULT 'GERANT',
    date_nomination DATE,
    duree_mandat_annees INTEGER,
    pouvoirs        TEXT,
    actif           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_representants_dossier ON representants (workspace_id, dossier_id);

-- ---------------------------------------------------------------------
-- associes : actionnaires / parts sociales
-- ---------------------------------------------------------------------
CREATE TABLE associes (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id      UUID         NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    representant_id UUID         REFERENCES representants(id) ON DELETE SET NULL,
    nombre_parts    INTEGER      NOT NULL CHECK (nombre_parts > 0),
    apport_numeraire_mad NUMERIC(15,2) NOT NULL DEFAULT 0,
    apport_nature_mad NUMERIC(15,2) NOT NULL DEFAULT 0,
    apport_industrie_mad NUMERIC(15,2) NOT NULL DEFAULT 0,
    pourcentage_parts NUMERIC(7,4),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_associes_dossier ON associes (workspace_id, dossier_id);

-- ---------------------------------------------------------------------
-- evenements_juridiques : historique de toutes les operations
-- ---------------------------------------------------------------------
CREATE TABLE evenements_juridiques (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id      UUID         NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    ticket_id       UUID         REFERENCES tickets(id) ON DELETE SET NULL,
    type_evenement  VARCHAR(40)  NOT NULL,
    description     TEXT,
    date_effet      DATE         NOT NULL DEFAULT CURRENT_DATE,
    payload         JSONB,
    created_by_id   UUID         NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_evenements_dossier ON evenements_juridiques (workspace_id, dossier_id, date_effet DESC);

-- ---------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------
ALTER TABLE workflow_progress    ENABLE ROW LEVEL SECURITY;
ALTER TABLE fiche_juridique      ENABLE ROW LEVEL SECURITY;
ALTER TABLE representants        ENABLE ROW LEVEL SECURITY;
ALTER TABLE associes             ENABLE ROW LEVEL SECURITY;
ALTER TABLE evenements_juridiques ENABLE ROW LEVEL SECURITY;

CREATE POLICY wf_progress_isolation ON workflow_progress FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY fiche_isolation ON fiche_juridique FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY repres_isolation ON representants FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY associes_isolation ON associes FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY evt_isolation ON evenements_juridiques FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE TRIGGER trg_wf_progress_updated_at BEFORE UPDATE ON workflow_progress FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
CREATE TRIGGER trg_fiche_updated_at        BEFORE UPDATE ON fiche_juridique   FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
