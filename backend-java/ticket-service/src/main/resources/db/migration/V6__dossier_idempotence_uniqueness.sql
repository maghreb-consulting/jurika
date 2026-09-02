-- =====================================================================
-- JURIKA V6 (ticket-service) — Idempotence dossier (anti-double-submit)
-- 2026-06-07 — Fix : CreateTicketUseCase auto-creait 2 dossiers en cas
-- de double-clic / StrictMode React. On ajoute un filet de securite
-- au niveau DB en plus du search-then-insert applicatif.
-- =====================================================================

-- ---------------------------------------------------------------------
-- ETAPE 0 : Resoudre les doublons LEGACY existants AVANT de creer les
-- index uniques partiels. On garde le plus ancien (MIN(created_at)) et
-- on marque les autres comme RADIE (sortie du WHERE de l'index). Aucune
-- donnee n'est perdue : statut RADIE = "deja close, plus exploite".
-- ---------------------------------------------------------------------
WITH ranked AS (
    SELECT id, workspace_id, lower(raison_sociale) AS rs, statut, created_at,
           row_number() OVER (
               PARTITION BY workspace_id, lower(raison_sociale)
               ORDER BY created_at ASC, id ASC
           ) AS rnk
      FROM entreprise_dossiers
     WHERE statut IN ('EN_CONSTITUTION','ACTIVE','EN_LIQUIDATION')
),
duplicates AS (
    SELECT id FROM ranked WHERE rnk > 1
)
UPDATE entreprise_dossiers d
   SET statut = 'RADIE', updated_at = NOW()
  FROM duplicates dup
 WHERE d.id = dup.id;

-- Idem pour ICE : un meme ICE en double = on garde la 1ere occurrence,
-- on met les autres a ICE = NULL (perte controlee : l'ICE est de toute
-- facon associe a la societe la plus ancienne du couple).
WITH ranked AS (
    SELECT id, workspace_id, ice,
           row_number() OVER (
               PARTITION BY workspace_id, ice
               ORDER BY created_at ASC, id ASC
           ) AS rnk
      FROM entreprise_dossiers
     WHERE ice IS NOT NULL
)
UPDATE entreprise_dossiers d
   SET ice = NULL, updated_at = NOW()
  FROM ranked r
 WHERE d.id = r.id AND r.rnk > 1;

-- ---------------------------------------------------------------------
-- Filet 1 : ICE unique par workspace, quand ice non nul
-- (un ICE 15 chiffres = 1 entreprise = 1 dossier)
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uq_dossier_workspace_ice_when_present
    ON entreprise_dossiers (workspace_id, ice)
    WHERE ice IS NOT NULL;

-- ---------------------------------------------------------------------
-- Filet 2 : Raison sociale + workspace unique pour les dossiers VIVANTS
-- (EN_CONSTITUTION / ACTIVE / EN_LIQUIDATION). DISSOUTE/LIQUIDEE/RADIE
-- sont exclus pour permettre la reincorporation de la meme denomination
-- plus tard. Case-insensitive (lower(raison_sociale)) pour bloquer les
-- doublons avec casse differente.
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uq_dossier_workspace_raison_alive
    ON entreprise_dossiers (workspace_id, lower(raison_sociale))
    WHERE statut IN ('EN_CONSTITUTION','ACTIVE','EN_LIQUIDATION');
