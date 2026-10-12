-- =====================================================================
-- JURIKA V36 (dataroom-service) -- Lot L5, etape E7 : types de document de la
-- fiche client et de la fiche dossier (RG-FIC-05 a 07, P5).
--
-- Les deux fiches sont generees au format Word a la demande ; chaque generation
-- par l'employe responsable est conservee dans la Data Room du dossier, en
-- versions successives du meme emplacement (RG-DR-04). Il leur faut un type.
--
-- Le CHECK est repose en entier (sur-ensemble de V31) : une migration appliquee
-- ne se modifie jamais. ATTENTION a toute migration ulterieure qui repose ce
-- CHECK : elle doit reprendre FICHE_CLIENT et FICHE_DOSSIER.
-- =====================================================================

ALTER TABLE dataroom_documents
    DROP CONSTRAINT IF EXISTS dataroom_documents_document_type_check;

ALTER TABLE dataroom_documents
    ADD CONSTRAINT dataroom_documents_document_type_check
    CHECK (document_type IN (
        -- ===== Valeurs historiques (V5) =====
        'STATUTS', 'PV_AGE', 'PV_AGO', 'PV_MODIFICATION', 'PV_DISSOLUTION',
        'PV_LIQUIDATION', 'ACTE_NOMINATION', 'CONTRAT_BAIL', 'CNIE_GERANT',
        'ANNONCE_JAL', 'RC', 'ICE', 'TP', 'CNSS', 'APOSTILLE', 'AUTRE',
        -- ===== Archivage d'identite (V16) =====
        'CIN_NOUVELLE', 'CIN_ANCIENNE', 'CN',
        -- ===== Documents de seance (V23) =====
        'CONVOCATION', 'FEUILLE_PRESENCE', 'RAPPORT_GESTION', 'RAPPORT_LIQUIDATION',
        -- ===== Parcours de creation (V24) =====
        'FICHE_RENSEIGNEMENTS', 'PIECE_IDENTITE', 'VALIDATION_CLIENT',
        'CONTRAT_DOMICILIATION', 'TITRE_PROPRIETE', 'ATTESTATION_ENREGISTREMENT',
        'RAPPORT_COMMISSAIRE_APPORTS', 'ETAT_ACTES_FORMATION', 'POUVOIR',
        'ATTESTATION_BLOCAGE_CAPITAL', 'BULLETIN_IF', 'JOURNAL_ANNONCE',
        'PUBLICATION_BO', 'ACCUSE_RBE', 'RIB', 'LIVRES_LEGAUX',
        'AUTORISATION_SECTORIELLE', 'IDENTIFIANTS_SIMPL', 'RECEPISSE_CNDP',
        'NOTE_CONFORMITE', 'BORDEREAU_REMISE',
        -- ===== Formulaires administratifs generes (V30) =====
        'DEMANDE_TAXE_PROFESSIONNELLE', 'DECLARATION_EXISTENCE',
        'DECLARATION_IMMATRICULATION_RC',
        -- ===== Justificatifs du parcours du 9 septembre (V31) =====
        'RECEPISSE_DEPOT',            -- lignes 15, 19, 21, 23, 25, 27, 32, 36, 39, 41, 43
        'AVIS_VERSEMENT_BANQUE',      -- ligne 17
        'INVESTISSEMENT_ETRANGER',    -- ligne 38
        'NOTE_ANNULATION',            -- ligne 49
        'ACCUSE_RETRAIT_DEPOT',       -- ligne 50
        -- ===== Documents produits par JURIKA au corpus du 9 septembre (V31) =====
        'ATTESTATION_SOUSCRIPTION_LIBERATION',  -- lignes 6 et 17
        'DEMANDE_AFFILIATION_CNSS',             -- ligne 32
        'DECLARATION_BENEFICIAIRES_EFFECTIFS',  -- ligne 34
        'DEMANDE_DEBLOCAGE_CAPITAL',            -- ligne 35
        'DECLARATION_CNDP',                     -- ligne 39
        'DEMANDE_ADHESION_SIMPL',               -- ligne 43
        'LETTRE_RETRAIT_DEPOT',                 -- ligne 50
        -- ===== Fiches produites par la Data Room (V36, lot L5) =====
        'FICHE_CLIENT', 'FICHE_DOSSIER'
    ));

COMMENT ON CONSTRAINT dataroom_documents_document_type_check ON dataroom_documents IS
    'Catalogue des types de documents. V36 (lot L5) ajoute la fiche client et la '
    'fiche dossier, produites par la Data Room et conservees en versions.';
