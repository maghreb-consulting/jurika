-- ---------------------------------------------------------------------
-- V23 — (A) 4 types de documents de SÉANCE manquants
--       (B) UN SEUL document « en vigueur » par slot logique
--
-- Correction globale simulation 2026-08-15, GROUPE 2 (M3/L2/L3) + GROUPE 6 (DR1/DR2).
--
-- (A) TYPES MANQUANTS — défauts M3 / L2 / A1
-- ------------------------------------------
-- Il n'existe pas d'enum Java `DocumentType` : le type est une chaîne contrainte
-- par ce CHECK. Convocation, feuille de présence et rapports n'y figuraient pas,
-- si bien que `JuridiqueController` et `DocumentNamingConvention` retombaient sur
-- le fourre-tout "AUTRE" codé en dur. Conséquences observées en simulation :
--   * convocation + feuille de présence invisibles dans les filtres par type ;
--   * rapport de liquidation noyé dans "AUTRE" ;
--   * pire (L3) : deux séances du MÊME dossier produisant deux documents de même
--     type "AUTRE" et de même titre étaient considérées comme le même document,
--     donc DÉDUPLIQUÉES — le second dépôt disparaissait silencieusement.
--
-- (B) UNICITÉ DU COURANT — défaut DR1
-- ------------------------------------
-- Le socle de versioning existe (colonnes version / is_current / replaced_at),
-- mais RIEN en base n'interdisait deux lignes `is_current = TRUE` sur le même
-- slot : on pouvait donc voir « deux Statuts en vigueur » côte à côte au lieu
-- d'un courant + un historique.
--
-- SLOT RETENU = (workspace_id, dossier_id, document_type, title)
--   Décision explicite : c'est DÉJÀ la clé de lignage du versioning explicite
--   (`DataroomJuridiqueService.replaceAsNewVersion` / `findLineage`). Le slot
--   (dossier, type) seul aurait été FAUX pour les types à exemplaires multiples
--   légitimes — typiquement une CIN par personne (défaut A5) : deux CIN
--   distinctes doivent coexister en vigueur, ce que le titre distingue.
--
-- Stratégie ADDITIVE et IDEMPOTENTE (règle projet : ne JAMAIS éditer une
-- migration déjà appliquée ; `validate-on-migrate: false` la rendrait muette).
-- Le backfill précède la création de l'index, sinon celle-ci échouerait sur les
-- doublons déjà présents en base.
-- ---------------------------------------------------------------------

-- ===== (A) Extension du CHECK — super-ensemble strict de V16 ==========
ALTER TABLE dataroom_documents
    DROP CONSTRAINT IF EXISTS dataroom_documents_document_type_check;

ALTER TABLE dataroom_documents
    ADD CONSTRAINT dataroom_documents_document_type_check
    CHECK (document_type IN (
        -- ===== Valeurs historiques (V5) =====
        'STATUTS',
        'PV_AGE',
        'PV_AGO',
        'PV_MODIFICATION',
        'PV_DISSOLUTION',
        'PV_LIQUIDATION',
        'ACTE_NOMINATION',
        'CONTRAT_BAIL',
        'CNIE_GERANT',
        'ANNONCE_JAL',
        'RC',
        'ICE',
        'TP',
        'CNSS',
        'APOSTILLE',
        'AUTRE',
        -- ===== Archivage d'identité (V16) =====
        'CIN_NOUVELLE',
        'CIN_ANCIENNE',
        'CN',
        -- ===== Documents de séance (V23) =====
        'CONVOCATION',
        'FEUILLE_PRESENCE',
        'RAPPORT_GESTION',
        'RAPPORT_LIQUIDATION'
    ));

-- ===== (B) Backfill : ne garder qu'UN courant par slot =================
-- Les doublons antérieurs deviennent des versions historiques. On conserve le
-- plus RÉCENT comme courant (created_at, puis version, puis id pour départager
-- de façon déterministe), et on renumérote les versions dans l'ordre
-- chronologique pour que l'historique se lise v1 → vN.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY workspace_id, dossier_id, document_type, title
               ORDER BY created_at DESC, version DESC, id DESC
           ) AS rang,
           COUNT(*) OVER (
               PARTITION BY workspace_id, dossier_id, document_type, title
           ) AS total,
           ROW_NUMBER() OVER (
               PARTITION BY workspace_id, dossier_id, document_type, title
               ORDER BY created_at ASC, version ASC, id ASC
           ) AS chrono
    FROM dataroom_documents
    WHERE is_current
)
UPDATE dataroom_documents d
SET is_current  = (r.rang = 1),
    version     = r.chrono,
    replaced_at = CASE WHEN r.rang = 1 THEN d.replaced_at ELSE COALESCE(d.replaced_at, NOW()) END
FROM ranked r
WHERE d.id = r.id
  AND r.total > 1;

-- ===== (B) Garde-fou définitif : la BASE interdit deux courants ========
-- Index UNIQUE PARTIEL : ne contraint que les lignes en vigueur, l'historique
-- reste librement multiple. Toute régression du code de dépôt échoue désormais
-- à l'INSERT au lieu de produire silencieusement un doublon.
CREATE UNIQUE INDEX IF NOT EXISTS ux_dataroom_documents_courant_par_slot
    ON dataroom_documents (workspace_id, dossier_id, document_type, title)
    WHERE is_current;
