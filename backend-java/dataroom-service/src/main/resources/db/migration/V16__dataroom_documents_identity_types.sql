-- ---------------------------------------------------------------------
-- V16 — Étendre la contrainte CHECK de dataroom_documents.document_type
--       pour autoriser les 3 types issus de l'archivage d'identité
--       (DataroomIdentityArchiver, branche feat/dataroom-identity-extract).
--
-- Contexte :
--   En prod, IdentityExtractionService.archiveDocumentType(...) retourne
--   "CIN_NOUVELLE", "CIN_ANCIENNE" ou "CN" selon le type d'extraction.
--   DataroomIdentityArchiver.archive(...) inserre l'entité DocumentEntity
--   avec ce document_type → l'INSERT échoue avec :
--     ERROR: new row violates check constraint
--     "dataroom_documents_document_type_check"
--   parce que la liste autorisée (V5) ne contenait QUE les types
--   juridiques historiques (STATUTS, PV_*, CNIE_GERANT, etc.).
--
-- Stratégie : migration ADDITIVE et IDEMPOTENTE.
--   - DROP CONSTRAINT IF EXISTS : ne casse pas si la migration a déjà tourné.
--   - La nouvelle liste = SUPER-ENSEMBLE strict de l'ancienne (16 valeurs V5)
--     + 3 nouveaux types {CIN_NOUVELLE, CIN_ANCIENNE, CN}.
--     → Aucune ligne existante ne devient invalide, aucun rollback risqué.
-- ---------------------------------------------------------------------

ALTER TABLE dataroom_documents
    DROP CONSTRAINT IF EXISTS dataroom_documents_document_type_check;

ALTER TABLE dataroom_documents
    ADD CONSTRAINT dataroom_documents_document_type_check
    CHECK (document_type IN (
        -- ===== Valeurs historiques (V5__dataroom_schema.sql) =====
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
        -- ===== Nouveaux types — archivage d'identité (V16) =====
        'CIN_NOUVELLE',
        'CIN_ANCIENNE',
        'CN'
    ));
