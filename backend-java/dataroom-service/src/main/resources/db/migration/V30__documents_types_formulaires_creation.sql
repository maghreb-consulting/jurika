-- =====================================================================
-- JURIKA V30 -- Types des TROIS FORMULAIRES administratifs du parcours de
--               creation (lot 5, 2026-09-07)
--
-- CE QUI MANQUAIT. V24 a type les 43 justificatifs du parcours, c'est-a-dire
-- les documents que le cabinet OBTIENT : l'attestation de taxe professionnelle
-- (TP), le bulletin d'identification fiscale (BULLETIN_IF), le certificat
-- d'immatriculation modele J (RC). Les IMPRIMES qui permettent de les obtenir,
-- eux, n'avaient aucun type : le lot 5 les fait generer par la plateforme, et
-- ils seraient tous tombes dans « AUTRE ».
--
-- La consequence n'est pas cosmetique. Deux documents de type AUTRE portant le
-- meme titre sont DEDUPLIQUES par l'unicite du courant posee en V23 : deposer
-- la declaration d'existence apres la demande de TP aurait silencieusement
-- ecrase la premiere. C'est exactement le defaut corrige en V23 pour les
-- documents de seance.
--
-- Ne pas confondre, donc :
--     DEMANDE_TAXE_PROFESSIONNELLE   (on la depose)  -> TP           (on la recoit)
--     DECLARATION_EXISTENCE          (on la depose)  -> BULLETIN_IF  (on le recoit)
--     DECLARATION_IMMATRICULATION_RC (on la depose)  -> RC           (on le recoit)
--
-- Strategie ADDITIVE et IDEMPOTENTE, super-ensemble strict de V24 (regle projet :
-- ne JAMAIS editer une migration deja appliquee, `validate-on-migrate: false` la
-- rendrait muette -- cf. V18, V22, V24).
-- =====================================================================

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
        'FICHE_RENSEIGNEMENTS',
        'PIECE_IDENTITE',
        'VALIDATION_CLIENT',
        'CONTRAT_DOMICILIATION',
        'TITRE_PROPRIETE',
        'ATTESTATION_ENREGISTREMENT',
        'RAPPORT_COMMISSAIRE_APPORTS',
        'ETAT_ACTES_FORMATION',
        'POUVOIR',
        'ATTESTATION_BLOCAGE_CAPITAL',
        'BULLETIN_IF',
        'JOURNAL_ANNONCE',
        'PUBLICATION_BO',
        'ACCUSE_RBE',
        'RIB',
        'LIVRES_LEGAUX',
        'AUTORISATION_SECTORIELLE',
        'IDENTIFIANTS_SIMPL',
        'RECEPISSE_CNDP',
        'NOTE_CONFORMITE',
        'BORDEREAU_REMISE',
        -- ===== Formulaires administratifs generes (V30) =====
        'DEMANDE_TAXE_PROFESSIONNELLE',   -- etape 19 -- formulaire DGI AAC050B
        'DECLARATION_EXISTENCE',          -- etape 20 -- formulaire DGI ADP050B, art. 148 CGI
        'DECLARATION_IMMATRICULATION_RC'  -- etape 21 -- modele 2, art. 45-46 Code de commerce
    ));

-- Ces trois documents sont produits PAR JURIKA : ils se rangent avec les actes
-- generes, jamais avec les justificatifs recus de l'administration.
UPDATE dataroom_documents SET groupe = 'ACTES_GENERES'
WHERE groupe IS NULL AND document_type IN (
    'DEMANDE_TAXE_PROFESSIONNELLE', 'DECLARATION_EXISTENCE', 'DECLARATION_IMMATRICULATION_RC');

COMMENT ON CONSTRAINT dataroom_documents_document_type_check ON dataroom_documents IS
    'Catalogue des types de documents. V30 ajoute les trois formulaires DEPOSES '
    '(TP / existence / modele 2), a ne pas confondre avec les documents RECUS '
    'qu''ils font obtenir (TP / BULLETIN_IF / RC).';
