-- =====================================================================
-- JURIKA V31 — Les types de documents du parcours du 9 septembre
--
-- POURQUOI UNE MIGRATION ET NON UNE EDITION DE V30
-- V30 est appliquee. Sur ce projet, une migration deja passee n'est JAMAIS
-- editee : `validate-on-migrate: false` rendrait la modification muette sur
-- toute base existante (cf. V18, V22, V24 cote ticket).
--
-- CE QUI MANQUE AU CATALOGUE
-- Le parcours du 9 septembre attend, en colonne « Justificatif a obtenir et
-- archiver », cinq pieces qu'aucun type existant ne nomme. Sans type propre,
-- elles tomberaient toutes dans « AUTRE » — et le controle serveur du cochage
-- deviendrait vide : n'importe quel fichier validerait n'importe quelle ligne.
-- C'est exactement le defaut que V24 avait corrige pour le guide precedent.
--
--   RECEPISSE_DEPOT       — la piece rendue au depot, avant le retrait. Elle
--                           revient sur ONZE lignes du parcours : c'est elle qui
--                           prouve que le depot a eu lieu, et sa date qui fait
--                           courir l'attente du retrait.
--   AVIS_VERSEMENT_BANQUE — l'avis de versement des fonds (ligne 17), a ne pas
--                           confondre avec l'attestation de BLOCAGE (ligne 18) :
--                           le premier prouve le versement, la seconde
--                           l'indisponibilite. Deux pieces, deux moments.
--   INVESTISSEMENT_ETRANGER — le formulaire de l'Office des changes (ligne 38).
--   NOTE_ANNULATION       — la note d'annulation motivee (ligne 49).
--   ACCUSE_RETRAIT_DEPOT  — l'accuse de reception d'une administration saisie
--                           d'un retrait ou d'une regularisation (ligne 50).
--
-- LES DOCUMENTS QUE JURIKA PRODUIT
-- Sept modeles du corpus n'avaient pas de type : leurs fichiers generes se
-- rangeaient sous « AUTRE ». Ils se rangent desormais avec les actes generes.
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
        'LETTRE_RETRAIT_DEPOT'                  -- ligne 50
    ));

-- Ces sept-la sont produits PAR JURIKA : ils se rangent avec les actes generes,
-- jamais avec les justificatifs recus de l'administration.
UPDATE dataroom_documents SET groupe = 'ACTES_GENERES'
WHERE groupe IS NULL AND document_type IN (
    'ATTESTATION_SOUSCRIPTION_LIBERATION', 'DEMANDE_AFFILIATION_CNSS',
    'DECLARATION_BENEFICIAIRES_EFFECTIFS', 'DEMANDE_DEBLOCAGE_CAPITAL',
    'DECLARATION_CNDP', 'DEMANDE_ADHESION_SIMPL', 'LETTRE_RETRAIT_DEPOT');

-- Les cinq justificatifs, eux, viennent d'un guichet : ils se rangent avec les
-- pieces administratives.
UPDATE dataroom_documents SET groupe = 'JUSTIFICATIFS_ADMINISTRATIFS'
WHERE groupe IS NULL AND document_type IN (
    'RECEPISSE_DEPOT', 'AVIS_VERSEMENT_BANQUE', 'INVESTISSEMENT_ETRANGER',
    'ACCUSE_RETRAIT_DEPOT');

-- La note d'annulation est redigee par le cabinet, pas recue d'un guichet.
UPDATE dataroom_documents SET groupe = 'ACTES_GENERES'
WHERE groupe IS NULL AND document_type = 'NOTE_ANNULATION';

COMMENT ON CONSTRAINT dataroom_documents_document_type_check ON dataroom_documents IS
    'Catalogue des types de documents. V31 ajoute les cinq justificatifs du parcours du '
    '9 septembre — dont RECEPISSE_DEPOT, qui revient sur onze lignes — et les sept '
    'documents que JURIKA produit et qui n''avaient pas de type.';
