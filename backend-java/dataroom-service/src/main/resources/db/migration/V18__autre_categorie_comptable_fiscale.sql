-- ---------------------------------------------------------------------
-- V18 — Ajout d'une catégorie "AUTRE" fourre-tout pour les imports (Prompt G,
--       Sprint 2026-06-23).
--
-- Contexte :
--   Lors de l'import de dossiers existants, certains documents comptables /
--   fiscaux ne se rangent dans aucune des catégories réglementaires
--   (ACHATS/VENTES/… ou TVA/IS/…). On autorise "AUTRE" comme fourre-tout,
--   matérialisé dans le front via les sélecteurs.
--
-- Stratégie : DROP + recreate des CHECK constraints, en SUR-ENSEMBLE strict
-- des valeurs existantes (aucune ligne ne devient invalide). Idempotent grâce
-- à IF EXISTS.
-- ---------------------------------------------------------------------

ALTER TABLE dataroom_comptable_documents
    DROP CONSTRAINT IF EXISTS dataroom_comptable_documents_categorie_check;

ALTER TABLE dataroom_comptable_documents
    ADD CONSTRAINT dataroom_comptable_documents_categorie_check
    CHECK (categorie IN ('ACHATS','VENTES','BANQUE','CAISSE','NDF','LA_PAIE','AUTRE'));

ALTER TABLE dataroom_fiscal_documents
    DROP CONSTRAINT IF EXISTS dataroom_fiscal_documents_categorie_check;

ALTER TABLE dataroom_fiscal_documents
    ADD CONSTRAINT dataroom_fiscal_documents_categorie_check
    CHECK (categorie IN ('TVA','IS','IR','TP_TSC','RAS','ATTESTATIONS','CONTENTIEUX','AUTRE'));
