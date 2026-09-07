-- =====================================================================
-- JURIKA V24 -- (A) Types de documents du parcours de CREATION
--               (B) Groupes du dossier juridique, par ticket
--
-- Source : specs/creation-2026-09/GUIDE_CREATION_ENTREPRISE_MAROC_SARL_v2.xlsx,
--          colonne « Justificatif a obtenir et archiver » de l'onglet 1.
--
-- (A) TYPES MANQUANTS
-- -------------------
-- Le cochage d'une demarche exige un justificatif TYPE, et le type vient du
-- referentiel (ticket-service V20), jamais de la saisie. 21 des 43 justificatifs
-- du parcours n'avaient aucun type : ils seraient tous tombes dans le fourre-tout
-- « AUTRE », ce qui aurait rendu le controle serveur inoperant (n'importe quel
-- fichier aurait valide n'importe quelle etape).
--
-- Types REUTILISES, sans doublon cree :
--   CN              = certificat negatif (etape 4)  -- cf. FicheClientPdf
--   RC              = registre du commerce / modele J (etape 21)
--   TP              = attestation taxe professionnelle (etape 19)
--   ICE             = attestation ICE (etape 22)
--   CNSS            = attestation d'affiliation (etape 26)
--   STATUTS         = etapes 8, 13, 14, 17
--   ACTE_NOMINATION = etapes 9, 15, 18
--   CONTRAT_BAIL    = etapes 5, 6, 7
--   ANNONCE_JAL     = etape 23
--
-- (B) GROUPES
-- -----------
-- Le dossier juridique s'organise par ticket, et dans chaque ticket par nature :
-- actes generes par JURIKA, justificatifs delivres par les administrations,
-- pieces fournies par le client. La colonne est NULLABLE a dessein : un document
-- dont la nature n'est pas deductible (type AUTRE) n'est pas range de force dans
-- un groupe faux -- il s'affiche sous « Autres documents ».
--
-- Strategie ADDITIVE et IDEMPOTENTE (regle projet : ne JAMAIS editer une
-- migration deja appliquee, `validate-on-migrate: false` la rendrait muette).
-- =====================================================================

-- ===== (A) Extension du CHECK -- super-ensemble strict de V23 =========
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
        -- ===== Archivage d'identite (V16) =====
        'CIN_NOUVELLE',
        'CIN_ANCIENNE',
        'CN',
        -- ===== Documents de seance (V23) =====
        'CONVOCATION',
        'FEUILLE_PRESENCE',
        'RAPPORT_GESTION',
        'RAPPORT_LIQUIDATION',
        -- ===== Parcours de creation (V24) =====
        'FICHE_RENSEIGNEMENTS',          -- etape 1
        'PIECE_IDENTITE',                -- etape 2
        'VALIDATION_CLIENT',             -- etape 3
        'CONTRAT_DOMICILIATION',         -- etapes 5, 6, 7 (alternative au bail)
        'TITRE_PROPRIETE',               -- etapes 5, 6, 7 (alternative au bail)
        'ATTESTATION_ENREGISTREMENT',    -- etapes 7, 17, 18
        'RAPPORT_COMMISSAIRE_APPORTS',   -- etape 10
        'ETAT_ACTES_FORMATION',          -- etape 11
        'POUVOIR',                       -- etape 12
        'ATTESTATION_BLOCAGE_CAPITAL',   -- etape 16
        'BULLETIN_IF',                   -- etape 20
        'JOURNAL_ANNONCE',               -- etape 24
        'PUBLICATION_BO',                -- etape 25
        'ACCUSE_RBE',                    -- etape 27
        'RIB',                           -- etape 28
        'LIVRES_LEGAUX',                 -- etape 29
        'AUTORISATION_SECTORIELLE',      -- etape 30
        'IDENTIFIANTS_SIMPL',            -- etape 31
        'RECEPISSE_CNDP',                -- etape 32
        'NOTE_CONFORMITE',               -- etape 33
        'BORDEREAU_REMISE'               -- etape 35
    ));

-- ===== (B) Groupe de rangement dans le dossier du ticket ==============
ALTER TABLE dataroom_documents
    ADD COLUMN IF NOT EXISTS groupe VARCHAR(30);

ALTER TABLE dataroom_documents DROP CONSTRAINT IF EXISTS chk_dataroom_documents_groupe;
ALTER TABLE dataroom_documents
    ADD CONSTRAINT chk_dataroom_documents_groupe
    CHECK (groupe IS NULL OR groupe IN
        ('ACTES_GENERES','JUSTIFICATIFS_ADMINISTRATIFS','PIECES_CLIENT'));

-- Backfill des 446 lignes existantes, par NATURE du type. Les types dont la
-- nature est ambigue (AUTRE) restent NULL : on ne devine pas.
UPDATE dataroom_documents SET groupe = 'ACTES_GENERES'
WHERE groupe IS NULL AND document_type IN (
    'STATUTS','PV_AGE','PV_AGO','PV_MODIFICATION','PV_DISSOLUTION','PV_LIQUIDATION',
    'ACTE_NOMINATION','ANNONCE_JAL','CONVOCATION','FEUILLE_PRESENCE',
    'RAPPORT_GESTION','RAPPORT_LIQUIDATION','ETAT_ACTES_FORMATION',
    'NOTE_CONFORMITE','BORDEREAU_REMISE','FICHE_RENSEIGNEMENTS');

UPDATE dataroom_documents SET groupe = 'JUSTIFICATIFS_ADMINISTRATIFS'
WHERE groupe IS NULL AND document_type IN (
    'RC','ICE','TP','CNSS','CN','APOSTILLE','BULLETIN_IF','ACCUSE_RBE',
    'ATTESTATION_ENREGISTREMENT','ATTESTATION_BLOCAGE_CAPITAL','JOURNAL_ANNONCE',
    'PUBLICATION_BO','LIVRES_LEGAUX','AUTORISATION_SECTORIELLE',
    'IDENTIFIANTS_SIMPL','RECEPISSE_CNDP','RIB');

UPDATE dataroom_documents SET groupe = 'PIECES_CLIENT'
WHERE groupe IS NULL AND document_type IN (
    'CIN_NOUVELLE','CIN_ANCIENNE','CNIE_GERANT','PIECE_IDENTITE','CONTRAT_BAIL',
    'CONTRAT_DOMICILIATION','TITRE_PROPRIETE','POUVOIR','VALIDATION_CLIENT',
    'RAPPORT_COMMISSAIRE_APPORTS');

CREATE INDEX IF NOT EXISTS idx_dataroom_docs_ticket_groupe
    ON dataroom_documents (workspace_id, ticket_id, groupe)
    WHERE ticket_id IS NOT NULL;

COMMENT ON COLUMN dataroom_documents.groupe IS
    'Rangement dans le dossier du ticket : actes generes / justificatifs '
    'administratifs / pieces client. NULL = nature non deductible (« Autres »).';
