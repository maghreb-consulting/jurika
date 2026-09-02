-- =====================================================================
-- JURIKA V17 — Fermeture de succursale : date d'effet + motif (lot DIVERS §D)
-- 2026-08-13
-- =====================================================================
-- CAUSE RACINE. La table `succursales` (V15) ne portait que `statut` et
-- `closed_at` (horodatage de l'ACTION dans l'application). Or la spec §D exige
-- de « marquer la succursale fermée/radiée en base (date + motif) » :
--
--   * `closed_at` n'est PAS la date d'effet juridique de la fermeture : celle-ci
--     est décidée par l'assemblée ($SUCCURSALE_DATE_FERMETURE) et peut être
--     antérieure ou postérieure à la saisie ;
--   * le MOTIF est publié dans l'annonce légale (« Cette fermeture est motivée
--     par … ») : ne pas le conserver rendait impossible de rejouer ou justifier
--     l'avis après coup.
--
-- On ajoute donc les deux colonnes, sans toucher à `closed_at` (qui garde son
-- rôle de trace applicative).
-- ---------------------------------------------------------------------

ALTER TABLE succursales
    -- Date d'EFFET juridique de la fermeture ($SUCCURSALE_DATE_FERMETURE).
    ADD COLUMN IF NOT EXISTS date_fermeture DATE,
    -- Motif publié dans l'annonce légale de fermeture ($SUCCURSALE_MOTIF).
    ADD COLUMN IF NOT EXISTS motif_fermeture TEXT;

-- Lot DIVERS §B/§C : le RC de la succursale n'est plus saisi dans le workflow
-- d'ouverture (il est attribué par le greffe APRÈS le dépôt). Il est donc
-- renseigné plus tard — notamment à la fermeture, où il devient obligatoire
-- (l'avis publie « immatriculée … sous le n° $SUCCURSALE_RC_NUMERO »). La
-- colonne `rc_secondaire` existe déjà (V15) : rien à créer, on documente.
COMMENT ON COLUMN succursales.rc_secondaire IS
    'RC de la succursale, attribue par le greffe APRES le depot d''ouverture. NULL tant qu''il n''est pas connu ; capture une seule fois (workflow de fermeture ou fiche succursale), jamais re-saisi ensuite.';
COMMENT ON COLUMN succursales.date_fermeture IS
    'Date d''EFFET juridique de la fermeture (decision d''assemblee). Distincte de closed_at, qui horodate l''action applicative.';
COMMENT ON COLUMN succursales.motif_fermeture IS
    'Motif de la fermeture, PUBLIE dans l''annonce legale ($SUCCURSALE_MOTIF).';
