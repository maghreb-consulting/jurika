-- =====================================================================
-- Lot W2 (2026-07-04) — Persistance de la date de dissolution
-- ---------------------------------------------------------------------
-- La date de dissolution n'etait stockee nulle part : l'employe la
-- re-saisissait a la main dans le workflow LIQUIDATION, empechant tout
-- calcul automatique du delai legal de 16 jours (RG-LI03) dans la LISTE
-- de selection. On ajoute une colonne nullable ; le workflow DISSOLUTION
-- la renseigne a la cloture (date d'effet de l'AGE de dissolution).
-- Les dossiers deja DISSOUTE sans date restent NULL (le front retombe
-- alors sur la saisie manuelle, comportement actuel).
-- =====================================================================
ALTER TABLE entreprise_dossiers
    ADD COLUMN IF NOT EXISTS date_dissolution DATE;
