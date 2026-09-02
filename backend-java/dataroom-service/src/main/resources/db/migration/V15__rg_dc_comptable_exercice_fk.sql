-- =====================================================================
-- JURIKA V15 -- Refactor Comptable : ajout FK vers exercice fiscal
-- Sprint 8. La colonne annee SMALLINT reste pour retrocompat lecture
-- (anciens endpoints ?annee= deprecies). exercice_fiscal_id devient
-- la source de verite.
-- =====================================================================

ALTER TABLE dataroom_comptable_documents
    ADD COLUMN exercice_fiscal_id UUID
    REFERENCES dataroom_exercices_fiscaux(id) ON DELETE RESTRICT;

CREATE INDEX idx_comptable_exercice
    ON dataroom_comptable_documents (workspace_id, exercice_fiscal_id, categorie)
    WHERE deleted_at IS NULL;

-- Backfill : pour chaque doc comptable existant, retrouver l exercice fiscal
-- correspondant (workspace_id + dossier_id + annee).
-- RLS desactivee temporairement (idem V12).
ALTER TABLE dataroom_comptable_documents DISABLE ROW LEVEL SECURITY;
ALTER TABLE dataroom_exercices_fiscaux   DISABLE ROW LEVEL SECURITY;

-- Etape 1 : creer les exercices manquants pour les annees deja referencees
INSERT INTO dataroom_exercices_fiscaux (workspace_id, dossier_id, annee, date_debut, date_fin)
SELECT DISTINCT
    d.workspace_id, d.dossier_id, d.annee,
    (d.annee || '-01-01')::DATE,
    (d.annee || '-12-31')::DATE
FROM dataroom_comptable_documents d
LEFT JOIN dataroom_exercices_fiscaux e
       ON e.workspace_id = d.workspace_id
      AND e.dossier_id   = d.dossier_id
      AND e.annee        = d.annee
WHERE e.id IS NULL
ON CONFLICT (workspace_id, dossier_id, annee) DO NOTHING;

-- Etape 2 : associer chaque doc a son exercice
UPDATE dataroom_comptable_documents d
SET exercice_fiscal_id = e.id
FROM dataroom_exercices_fiscaux e
WHERE d.workspace_id = e.workspace_id
  AND d.dossier_id   = e.dossier_id
  AND d.annee        = e.annee
  AND d.exercice_fiscal_id IS NULL;

ALTER TABLE dataroom_exercices_fiscaux   ENABLE ROW LEVEL SECURITY;
ALTER TABLE dataroom_comptable_documents ENABLE ROW LEVEL SECURITY;

-- Apres backfill, NOT NULL constraint sur la nouvelle colonne
ALTER TABLE dataroom_comptable_documents
    ALTER COLUMN exercice_fiscal_id SET NOT NULL;

-- Alignement modele directeur : categorie 'PAIE' -> 'LA_PAIE'
UPDATE dataroom_comptable_documents SET categorie = 'LA_PAIE' WHERE categorie = 'PAIE';

ALTER TABLE dataroom_comptable_documents
    DROP CONSTRAINT IF EXISTS dataroom_comptable_documents_categorie_check;
ALTER TABLE dataroom_comptable_documents
    ADD CONSTRAINT dataroom_comptable_documents_categorie_check
    CHECK (categorie IN ('ACHATS','VENTES','BANQUE','CAISSE','NDF','LA_PAIE'));

COMMENT ON COLUMN dataroom_comptable_documents.exercice_fiscal_id
    IS 'Sprint 8 -- lien vers exercice fiscal. La colonne annee reste pour retrocompat lecture.';
