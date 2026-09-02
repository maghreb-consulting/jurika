-- ---------------------------------------------------------------------
-- V17 — Ajout de la colonne `motif` à dataroom_documents pour la
--       traçabilité des remplacements de version (Sprint 2026-06-23).
--
-- Contexte :
--   Le schéma existant (V5) supporte déjà le versioning implicite via
--   les colonnes `version`, `is_current`, `replaced_at`. Chaque upload
--   avec un titre identique crée une nouvelle ligne et marque l'ancienne
--   is_current=false + replaced_at=NOW.
--
--   Il manquait juste la raison du remplacement (ex. "Modification :
--   Transfert du siège") pour exposer un historique parlant côté UI.
--
-- Stratégie : migration ADDITIVE, idempotente, sans default.
--   - `motif` peut rester NULL pour les lignes antérieures et pour les
--     uploads où l'utilisateur ne renseigne pas de raison.
--   - TEXT (pas VARCHAR borné) : pas de contrainte de longueur arbitraire.
-- ---------------------------------------------------------------------

ALTER TABLE dataroom_documents
    ADD COLUMN IF NOT EXISTS motif TEXT NULL;
