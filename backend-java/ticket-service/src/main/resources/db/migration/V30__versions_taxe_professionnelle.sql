-- =====================================================================
-- JURIKA V30 (ticket-service) -- Lot L1, etape E7 : versions datees de la taxe
-- professionnelle (patente).
--
-- RG-VAR-08 : un identifiant qui change (notamment la taxe professionnelle) est
-- enregistre comme une NOUVELLE VERSION DATEE ; les versions precedentes sont
-- conservees. RG-FIC-02 : la fiche client affiche toutes les versions successives,
-- chacune avec sa date de prise d'effet ; la version en vigueur est signalee.
--
-- La colonne entreprise_dossiers.taxe_professionnelle reste la valeur EN VIGUEUR
-- (lue par les actes) ; cette table en garde l'historique. Aucune suppression.
--
-- Reprise : chaque dossier qui porte deja une taxe professionnelle recoit sa
-- premiere version, date d'effet VIDE (jamais inventee, P4 / RG-VAR-07 : elle se
-- complete par l'employe), saisie attribuee au responsable du dossier.
-- =====================================================================
CREATE TABLE dossier_tp_versions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id  UUID NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE RESTRICT,
    numero      VARCHAR(50) NOT NULL,
    date_effet  DATE,
    saisi_par   UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    saisi_le    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    origine     VARCHAR(20) NOT NULL DEFAULT 'SAISIE' CHECK (origine IN ('SAISIE', 'REPRISE_V30'))
);
CREATE INDEX idx_tp_versions_dossier ON dossier_tp_versions (workspace_id, dossier_id, saisi_le);

ALTER TABLE dossier_tp_versions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tp_versions_isolation ON dossier_tp_versions FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

INSERT INTO dossier_tp_versions (workspace_id, dossier_id, numero, date_effet, saisi_par, saisi_le, origine)
SELECT workspace_id, id, taxe_professionnelle, NULL, responsable_id, updated_at, 'REPRISE_V30'
  FROM entreprise_dossiers
 WHERE NULLIF(BTRIM(taxe_professionnelle), '') IS NOT NULL;
