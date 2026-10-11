-- =====================================================================
-- JURIKA V34 -- Lot L4b (E2, RG-DEM-01) : referentiel des demarches
-- importe du corpus du cabinet, versionne.
--
-- Les lignes du corpus (onglets "Demarches" des 11 matrices) entrent dans
-- demarches_referentiel avec leur version de corpus, leur matrice, leur
-- forme et leur decision ; elles ne remplacent pas les 51 lignes de la
-- creation en service (corpus_version NULL), auxquelles sont rattaches les
-- cochages, les formalites et l'annulation.
--
-- Un import est une operation applicative journalisee (referentiel_imports) :
-- une version refusee ne touche a rien ; une version appliquee desactive
-- les lignes des versions precedentes sans les supprimer (les cochages des
-- tickets ouverts restent rattaches).
-- =====================================================================

-- Les cellules du corpus depassent les longueurs du guide de 2026-09.
ALTER TABLE demarches_referentiel
    ALTER COLUMN libelle TYPE TEXT,
    ALTER COLUMN organisme TYPE TEXT,
    ALTER COLUMN delai TYPE TEXT,
    ALTER COLUMN modele_jurika TYPE TEXT;

ALTER TABLE demarches_referentiel
    ADD COLUMN IF NOT EXISTS corpus_version          VARCHAR(60),
    ADD COLUMN IF NOT EXISTS matrice                 VARCHAR(80),
    ADD COLUMN IF NOT EXISTS forme                   VARCHAR(12),
    ADD COLUMN IF NOT EXISTS decision_code           VARCHAR(80),
    ADD COLUMN IF NOT EXISTS sous_condition_variable VARCHAR(80),
    ADD COLUMN IF NOT EXISTS sous_condition_valeur   VARCHAR(200),
    ADD COLUMN IF NOT EXISTS sous_condition_texte    TEXT,
    ADD COLUMN IF NOT EXISTS condition_variable      VARCHAR(80),
    ADD COLUMN IF NOT EXISTS condition_operateur     VARCHAR(8),
    ADD COLUMN IF NOT EXISTS condition_valeur        VARCHAR(200),
    ADD COLUMN IF NOT EXISTS numero_decision         SMALLINT,
    ADD COLUMN IF NOT EXISTS delai_echeance_donnee   VARCHAR(80),
    ADD COLUMN IF NOT EXISTS a_completer             TEXT,
    ADD COLUMN IF NOT EXISTS complete_plateforme     TEXT;

ALTER TABLE demarches_referentiel DROP CONSTRAINT IF EXISTS chk_condition_operateur;
ALTER TABLE demarches_referentiel ADD CONSTRAINT chk_condition_operateur
    CHECK (condition_operateur IS NULL OR condition_operateur IN ('EGAL', 'EXISTE', 'PHRASE'));

-- Unicite : (type, ordre) pour les lignes en service ; (version, matrice, ordre)
-- pour celles du corpus.
ALTER TABLE demarches_referentiel DROP CONSTRAINT IF EXISTS demarches_referentiel_workflow_type_ordre_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_demarches_ref_en_service
    ON demarches_referentiel (workflow_type, ordre) WHERE corpus_version IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_demarches_ref_corpus
    ON demarches_referentiel (corpus_version, matrice, ordre) WHERE corpus_version IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_demarches_ref_corpus_selection
    ON demarches_referentiel (matrice, decision_code) WHERE corpus_version IS NOT NULL AND actif;

-- Decisions de l'onglet d'origine (une ligne par decision, libelle du cabinet).
CREATE TABLE IF NOT EXISTS referentiel_decisions (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    corpus_version VARCHAR(60) NOT NULL,
    matrice        VARCHAR(80) NOT NULL,
    numero         SMALLINT    NOT NULL,
    section        TEXT,
    code           VARCHAR(80) NOT NULL,
    libelle        TEXT,
    type_decision  TEXT,
    actif          BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (corpus_version, matrice, numero)
);
CREATE INDEX IF NOT EXISTS idx_referentiel_decisions_actives
    ON referentiel_decisions (matrice) WHERE actif;

-- Onglets "Renvois" : pour ces decisions, la matrice de la famille fait foi.
CREATE TABLE IF NOT EXISTS referentiel_renvois (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    corpus_version VARCHAR(60) NOT NULL,
    matrice        VARCHAR(80) NOT NULL,
    numero         SMALLINT    NOT NULL,
    section        TEXT,
    decision       TEXT,
    code           VARCHAR(80) NOT NULL,
    famille_renvoi TEXT,
    matrice_renvoi VARCHAR(80) NOT NULL,
    numero_renvoi  SMALLINT,
    regle          TEXT,
    actif          BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (corpus_version, matrice, numero)
);

-- Journal des imports (appliques et refuses), rapport d'analyse joint.
CREATE TABLE IF NOT EXISTS referentiel_imports (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    corpus_version VARCHAR(60) NOT NULL,
    statut         VARCHAR(10) NOT NULL CHECK (statut IN ('APPLIQUE', 'REFUSE')),
    lignes         INTEGER     NOT NULL DEFAULT 0,
    decisions      INTEGER     NOT NULL DEFAULT 0,
    renvois        INTEGER     NOT NULL DEFAULT 0,
    motif          TEXT,
    rapport        JSONB,
    importe_par    UUID,
    importe_le     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_referentiel_imports_date ON referentiel_imports (importe_le DESC);
