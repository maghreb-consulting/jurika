-- =====================================================================
-- JURIKA V39 -- Lot L4d (E7) : delais de depot au greffe ajustes par le cabinet.
--
-- Decision du directeur du 11 octobre 2026 : un referentiel des delais de depot
-- au greffe par nature d'acte, avec l'article cite (loi 15-95 formant code de
-- commerce, loi 5-96), "a confirmer" s'il n'est pas verifie, ajustable par le
-- cabinet. Le referentiel lui-meme se lit dans le corpus importe (lignes de
-- depot au greffe de demarches_referentiel) : rien n'y est saisi ni invente.
-- Cette table ne porte que les AJUSTEMENTS d'un cabinet, par matrice et par
-- decision ; un ajustement s'applique aux lignes de depot de ses tickets.
-- =====================================================================

CREATE TABLE IF NOT EXISTS delais_greffe_cabinet (
    workspace_id  UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    matrice       VARCHAR(80)  NOT NULL,
    decision_code VARCHAR(120) NOT NULL,
    delai_valeur  INTEGER      NOT NULL CHECK (delai_valeur > 0),
    delai_unite   VARCHAR(10)  NOT NULL CHECK (delai_unite IN ('JOURS', 'MOIS')),
    article       TEXT,
    note          TEXT,
    modifie_par   UUID,
    modifie_le    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, matrice, decision_code)
);

ALTER TABLE delais_greffe_cabinet ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS delais_greffe_cabinet_isolation ON delais_greffe_cabinet;
CREATE POLICY delais_greffe_cabinet_isolation ON delais_greffe_cabinet FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON delais_greffe_cabinet TO jurika_app;
    END IF;
END
$$;
