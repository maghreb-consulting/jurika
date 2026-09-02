-- =====================================================================
-- JURIKA V12 -- RG-DF03 : table des exercices fiscaux
--
-- Reference : docs/v2/PLAN_SPRINT_7_DATAROOM_V2_FINITION.md TASK 6
--             docs/v2/Regles_de_Gestion_V2.md RG-DF01..28 + CGI Art. 211
--             (retention 10 ans)
--
-- Cette table pilote a la fois :
--   * le Dossier Comptable (Sprint 8 -- refactor multi-exercices)
--   * le Dossier Fiscal (Sprint 8 -- nouveau module)
--
-- Sprint 7 ne fait que la creer + backfiller un exercice OUVERT pour
-- l'annee en cours. Aucun service applicatif ne lit ces lignes encore
-- (FiscalController stub renvoie placeholder Sprint 8).
-- =====================================================================

CREATE TABLE dataroom_exercices_fiscaux (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id      UUID         NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    annee           SMALLINT     NOT NULL CHECK (annee BETWEEN 2000 AND 2100),
    date_debut      DATE         NOT NULL,
    date_fin        DATE         NOT NULL,
    statut          VARCHAR(20)  NOT NULL DEFAULT 'OUVERT'
                    CHECK (statut IN ('OUVERT','CLOTURE','VERROUILLE')),
    date_ouverture  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    date_cloture    TIMESTAMPTZ,
    cloture_par     UUID         REFERENCES users(id) ON DELETE SET NULL,
    note            TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (workspace_id, dossier_id, annee),
    CHECK (date_fin > date_debut)
);

CREATE INDEX idx_exercices_dossier_statut
    ON dataroom_exercices_fiscaux (workspace_id, dossier_id, statut);

ALTER TABLE dataroom_exercices_fiscaux ENABLE ROW LEVEL SECURITY;
CREATE POLICY exercices_isolation ON dataroom_exercices_fiscaux FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

-- ---------------------------------------------------------------------
-- Backfill : 1 exercice OUVERT par dossier pour l'annee en cours.
-- Idempotent via ON CONFLICT DO NOTHING (UNIQUE workspace+dossier+annee).
-- RLS desactivee temporairement (idem migration V6 settings) puis remise.
-- ---------------------------------------------------------------------
ALTER TABLE dataroom_exercices_fiscaux DISABLE ROW LEVEL SECURITY;
INSERT INTO dataroom_exercices_fiscaux (workspace_id, dossier_id, annee, date_debut, date_fin)
SELECT workspace_id,
       id,
       EXTRACT(YEAR FROM CURRENT_DATE)::SMALLINT,
       (EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER || '-01-01')::DATE,
       (EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER || '-12-31')::DATE
FROM entreprise_dossiers
ON CONFLICT (workspace_id, dossier_id, annee) DO NOTHING;
ALTER TABLE dataroom_exercices_fiscaux ENABLE ROW LEVEL SECURITY;

COMMENT ON TABLE dataroom_exercices_fiscaux
    IS 'RG-DF03 : exercices fiscaux par dossier (OUVERT / CLOTURE / VERROUILLE). Sprint 7 cree la table + backfill annee en cours ; Sprint 8 ajoute la gestion applicative + le Dossier Fiscal complet.';
