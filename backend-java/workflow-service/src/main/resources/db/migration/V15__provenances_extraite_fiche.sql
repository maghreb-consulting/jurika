-- =====================================================================
-- JURIKA V15 (workflow-service) -- Lot L1, etape E12 : provenances du magasin
--
-- RG-VAR-02 : la provenance est tracee : saisie (par qui et quand), calculee,
-- EXTRAITE d'une piece, ou reprise de la FICHE societe. V13 ne connaissait que
-- SAISIE, BASE et DERIVEE.
-- RG-VAR-09 : une valeur extraite n'entre au magasin qu'apres confirmation par
-- l'employe : comme une saisie, elle porte donc son auteur.
-- =====================================================================
ALTER TABLE dossier_variables DROP CONSTRAINT IF EXISTS dossier_variables_origine_check;
ALTER TABLE dossier_variables ADD CONSTRAINT dossier_variables_origine_check
    CHECK (origine IN ('SAISIE', 'BASE', 'DERIVEE', 'EXTRAITE', 'FICHE'));

ALTER TABLE dossier_variables DROP CONSTRAINT IF EXISTS ck_dossier_variables_saisie_auteur;
ALTER TABLE dossier_variables ADD CONSTRAINT ck_dossier_variables_saisie_auteur
    CHECK (origine NOT IN ('SAISIE', 'EXTRAITE') OR saisie_par_id IS NOT NULL);
