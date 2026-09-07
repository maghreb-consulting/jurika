-- =====================================================================
-- JURIKA V25 -- Suppression des dossiers COMPTABLE et FISCAL
--
-- La Data Room se reduit a quatre sections : dossier juridique (organise par
-- ticket), depot client, requetes (employe -> client) et demandes
-- (client -> employe). Les dossiers comptable et fiscal sortent du perimetre
-- produit ; les fichiers de cette nature seront desormais simplement deposes,
-- sans traitement, dans l'espace « Depot ».
--
-- INVENTAIRE ETABLI ET VALIDE AVANT SUPPRESSION (base de developpement,
-- 2026-09-04) :
--   dataroom_comptable_documents  523 lignes / 253 workspaces / 1,26 Mo
--     dont 498 gabarits de seed identiques (« Factures clients Q1 »,
--     « Factures fournisseurs Q1 ») repliques sur ~249 workspaces « Cabinet Demo »
--     -> 25 documents reels seulement, sur 19 workspaces, tous de test.
--   dataroom_fiscal_documents     506 lignes / 252 workspaces / 1,17 Mo
--     dont 498 gabarits de seed (« IS annuel 2026 », « TVA mensuelle 2026-03 »)
--     -> 8 documents reels, sur 3 workspaces (« test », « test test »,
--        « Maghreb Consulting Demo »).
--   dataroom_exercices_fiscaux    290 lignes / 253 workspaces
--   dataroom_alertes_echeances    417 lignes / 6 workspaces
--   dataroom_fiscal_documents.comptable_doc_source : 0 ligne renseignee.
--   Aucune donnee client reelle. Aucune FK entrante hors de ce groupe de tables.
--
-- OBJETS MINIO -- la suppression des lignes rend orphelins les objets stockes.
-- Leurs cles sont conservees dans `dataroom_objets_supprimes_v25` pour qu'un
-- script de purge puisse les nettoyer plus tard : on ne supprime pas en silence
-- ce qu'on ne peut pas retrouver.
-- =====================================================================

CREATE TABLE IF NOT EXISTS dataroom_objets_supprimes_v25 (
    id           UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    origine      VARCHAR(30)  NOT NULL,   -- COMPTABLE | FISCAL
    workspace_id UUID         NOT NULL,
    dossier_id   UUID         NOT NULL,
    object_key   VARCHAR(500) NOT NULL,
    filename     VARCHAR(255) NOT NULL,
    size_bytes   BIGINT       NOT NULL DEFAULT 0,
    releve_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE dataroom_objets_supprimes_v25 IS
    'Cles MinIO devenues orphelines par la suppression des dossiers comptable et '
    'fiscal (V25). A purger par script, puis cette table peut etre supprimee.';

INSERT INTO dataroom_objets_supprimes_v25 (origine, workspace_id, dossier_id, object_key, filename, size_bytes)
SELECT 'COMPTABLE', workspace_id, dossier_id, object_key, filename, size_bytes
FROM dataroom_comptable_documents;

INSERT INTO dataroom_objets_supprimes_v25 (origine, workspace_id, dossier_id, object_key, filename, size_bytes)
SELECT 'FISCAL', workspace_id, dossier_id, object_key, filename, size_bytes
FROM dataroom_fiscal_documents;

-- ---------------------------------------------------------------------
-- Suppression, dans l'ordre des dependances :
--   alertes_echeances -> fiscal_documents -> comptable_documents -> exercices
-- DROP TABLE suffit (les FK partent avec les tables), mais l'ordre reste
-- explicite pour que la migration echoue bruyamment si une FK entrante avait
-- ete ajoutee entre-temps.
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS dataroom_alertes_echeances;
DROP TABLE IF EXISTS dataroom_fiscal_documents;
DROP TABLE IF EXISTS dataroom_comptable_documents;
DROP TABLE IF EXISTS dataroom_exercices_fiscaux;

-- ---------------------------------------------------------------------
-- Notification du comptable : le declencheur disparait avec les uploads
-- comptables / fiscaux et les echeances DGI. Les deux colonnes de reglage
-- deviennent mortes -- on les retire plutot que de laisser une case a cocher
-- sans effet dans l'ecran de parametres.
-- ---------------------------------------------------------------------
ALTER TABLE dataroom_settings DROP COLUMN IF EXISTS accountant_email;
ALTER TABLE dataroom_settings DROP COLUMN IF EXISTS notify_accountant_on_upload;
