-- =====================================================================
-- JURIKA V20 — Referentiel des demarches (workflow CREATION)
--
-- FICHIER GENERE. Ne pas editer a la main : regenerer avec
--   node scripts/lot1/derive-referentiel-demarches.mjs
-- Source : specs/creation-2026-09/GUIDE_CREATION_ENTREPRISE_MAROC_SARL_v2.xlsx
-- Onglets « 1. Parcours creation » et « 2. Workflow ticket » — 36 etapes.
--
-- Le referentiel vit en DONNEES et non en code : les 36 etapes vont evoluer
-- (l'onglet 5 du guide liste 6 points « a arbitrer ») et les autres workflows
-- auront leurs propres parcours. Une correction = une migration, pas un
-- redeploiement. Les cellules vides du guide restent NULL — rien n est
-- complete au juge (cf. output/lot1/referentiel-anomalies.md).
-- =====================================================================

CREATE TABLE IF NOT EXISTS demarches_referentiel (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_type         VARCHAR(30)  NOT NULL,
    ordre                 SMALLINT     NOT NULL,
    phase_code            VARCHAR(8)   NOT NULL,
    phase_libelle         VARCHAR(60)  NOT NULL,
    libelle               VARCHAR(400) NOT NULL,
    statut_ticket         VARCHAR(30)  NOT NULL,
    acteur                VARCHAR(80),
    organisme             VARCHAR(300),
    obligatoire           CHAR(1)      NOT NULL CHECK (obligatoire IN ('O','C')),
    condition_application TEXT,
    pieces_entrantes      TEXT,
    document_produit      TEXT,
    justificatifs_texte   TEXT,
    modele_jurika         VARCHAR(200),
    delai                 VARCHAR(300),
    cout_indicatif        TEXT,
    variables_alimentees  TEXT,
    -- Delai MECANISABLE : valeur + UNITE + etape dont la date de cochage sert
    -- de point de depart. NULL = le guide donne un delai en toutes lettres
    -- mais aucun point de depart calculable : aucune alerte ne sera levee.
    --
    -- L unite est portee jusqu ici et JAMAIS normalisee en jours : un delai
    -- legal exprime en mois se calcule en mois calendaires (31/01 + 1 mois
    -- = 28/02, et non 02/03). Normaliser produirait des echeances trop
    -- tardives sur les mois de 31 jours.
    delai_valeur          SMALLINT,
    delai_unite           VARCHAR(5) CHECK (delai_unite IN ('JOURS','MOIS')),
    delai_reference_ordre SMALLINT,
    CONSTRAINT chk_delai_complet CHECK (
        (delai_valeur IS NULL AND delai_unite IS NULL AND delai_reference_ordre IS NULL)
     OR (delai_valeur IS NOT NULL AND delai_unite IS NOT NULL
         AND delai_reference_ordre IS NOT NULL)),
    UNIQUE (workflow_type, ordre)
);

-- Justificatifs attendus au cochage, TYPES (le type ne se saisit pas :
-- il vient du referentiel). Deux lignes de meme `alternative_groupe`
-- sont des ALTERNATIVES — l'une d'elles suffit (ex. bail OU domiciliation
-- OU titre de propriete) ; deux groupes distincts sont CUMULATIFS.
CREATE TABLE IF NOT EXISTS demarches_justificatifs (
    id                 UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    demarche_id        UUID         NOT NULL REFERENCES demarches_referentiel(id) ON DELETE CASCADE,
    alternative_groupe SMALLINT     NOT NULL,
    document_type      VARCHAR(60)  NOT NULL,
    libelle            TEXT         NOT NULL,
    UNIQUE (demarche_id, alternative_groupe, document_type)
);

CREATE INDEX IF NOT EXISTS idx_demarches_ref_workflow
    ON demarches_referentiel (workflow_type, ordre);
CREATE INDEX IF NOT EXISTS idx_demarches_ref_statut
    ON demarches_referentiel (workflow_type, statut_ticket, ordre);
CREATE INDEX IF NOT EXISTS idx_demarches_justif_demarche
    ON demarches_justificatifs (demarche_id);

-- Rechargement idempotent : le referentiel est une donnee de reference, pas
-- une donnee client. On le remplace integralement a chaque version du guide.
DELETE FROM demarches_justificatifs WHERE demarche_id IN
    (SELECT id FROM demarches_referentiel WHERE workflow_type = 'CREATION');
DELETE FROM demarches_referentiel WHERE workflow_type = 'CREATION';

-- -- Etape 1 — Ouverture du ticket et qualification du dossier
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 1, 'P0', 'P0 Ouverture',
        'Ouverture du ticket et qualification du dossier', 'CREATION_TICKET', 'Cabinet',
        'JURIKA (interne)', 'O', 'Tous dossiers',
        'Fiche de renseignements complétée ; CIN / passeport des associés et du gérant ; 3 propositions de dénomination ; objet social envisagé ; montant et répartition du capital ; adresse du siège envisagée', 'Fiche dossier / accusé d''ouverture',
        'Fiche de renseignements signée par le client', 'FICHE_RENSEIGNEMENTS_CREATION (à créer)',
        'J0', NULL, '$DENOMINATION, $OBJET_SOCIAL, $CAPITAL_CHIFFRES, $SIEGE_ADRESSE', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'FICHE_RENSEIGNEMENTS', 'Fiche de renseignements signée par le client'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 1;

-- -- Etape 2 — Contrôle d'identité, de capacité et de qualité des associés (KYC)
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 2, 'P0', 'P0 Ouverture',
        'Contrôle d''identité, de capacité et de qualité des associés (KYC)', 'CREATION_TICKET', 'Cabinet',
        NULL, 'O', 'Tous dossiers',
        'CIN en cours de validité (résidents) ; passeport + justificatif de résidence (non-résidents) ; pour un associé personne morale : statuts + modèle J + PV désignant le représentant', 'Note de contrôle KYC',
        'Copies des pièces d''identité et, le cas échéant, des documents sociaux de l''associé PM', NULL,
        'J0', NULL, '$ASSOCIE_*, $GERANT_*', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'PIECE_IDENTITE', 'Copies des pièces d''identité et, le cas échéant, des documents sociaux de l''associé PM'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 2;

-- -- Etape 3 — Choix de la forme sociale et des paramètres structurants
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 3, 'P0', 'P0 Ouverture',
        'Choix de la forme sociale et des paramètres structurants', 'CREATION_TICKET', 'Cabinet + client',
        NULL, 'O', 'Tous dossiers',
        'Décision du client : SARL (2 associés et plus) ou SARL AU (associé unique) ; gérance statutaire ou non ; durée ; date de clôture de l''exercice ; nature des apports', 'Note de cadrage / synthèse des options retenues',
        'Validation écrite du client', NULL,
        'J0', NULL, '$FORME_JURIDIQUE, $DUREE_SOCIETE, $EXERCICE_CLOTURE', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'VALIDATION_CLIENT', 'Validation écrite du client'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 3;

-- -- Etape 4 — Demande et obtention du certificat négatif
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 4, 'P1', 'P1 Préalables',
        'Demande et obtention du certificat négatif', 'GENERATION_DOCUMENTS', 'Cabinet',
        'OMPIC — directompic.ma / plateforme DirectEntreprise.ma, ou CRI', 'O', 'Tous dossiers',
        'Copie CIN / passeport du demandeur ; 3 propositions de noms commerciaux par ordre de préférence', 'Demande de certificat négatif',
        'Certificat négatif original (n° et date à saisir au dossier)', '— (formulaire OMPIC en ligne)',
        '24 à 48 h ; validité 1 an (à confirmer)', '≈ 230', '$CERTIFICAT_NEGATIF_NUMERO, $CERTIFICAT_NEGATIF_DATE, $DENOMINATION, $SIGLE, $ENSEIGNE', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'CN', 'Certificat négatif original (n° et date à saisir au dossier)'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 4;

-- -- Etape 5 — Sécurisation du siège social : bail commercial, contrat de domiciliation ou local en propriété
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 5, 'P1', 'P1 Préalables',
        'Sécurisation du siège social : bail commercial, contrat de domiciliation ou local en propriété', 'GENERATION_DOCUMENTS', 'Client (assisté)',
        'Bailleur / société de domiciliation', 'O', 'Tous dossiers — la voie retenue dépend du cas',
        'Titre de propriété ou bail du bailleur ; CIN du bailleur ; RC du domiciliataire si domiciliation (loi 89-17)', 'Contrat de bail ou contrat de domiciliation',
        'Contrat signé', 'CONTRAT_DOMICILIATION (à créer) / CONTRAT_BAIL (à créer)',
        'J+1 à J+5', 'Selon prestataire', '$SIEGE_ADRESSE, $SIEGE_VILLE, $DOMICILIATAIRE_*', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'CONTRAT_BAIL', 'Contrat signé'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 5;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'CONTRAT_DOMICILIATION', 'Contrat signé'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 5;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'TITRE_PROPRIETE', 'Contrat signé'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 5;

-- -- Etape 6 — Légalisation des signatures du contrat de siège
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 6, 'P1', 'P1 Préalables',
        'Légalisation des signatures du contrat de siège', 'GENERATION_DOCUMENTS', 'Client',
        'Commune / arrondissement (ou notaire)', 'O', 'Tous dossiers',
        'Contrat en autant d''exemplaires que de parties + exemplaires administratifs ; CIN des signataires', NULL,
        'Contrat légalisé', NULL,
        'J+1', 'Timbre par signature', NULL, NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'CONTRAT_BAIL', 'Contrat légalisé'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 6;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'CONTRAT_DOMICILIATION', 'Contrat légalisé'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 6;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'TITRE_PROPRIETE', 'Contrat légalisé'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 6;

-- -- Etape 7 — Enregistrement du contrat de bail / de domiciliation
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 7, 'P1', 'P1 Préalables',
        'Enregistrement du contrat de bail / de domiciliation', 'GENERATION_DOCUMENTS', 'Cabinet',
        'DGI — service de l''enregistrement', 'O', 'Tous dossiers',
        'Contrat signé et légalisé (exemplaires) ; CIN des parties', NULL,
        'Contrat enregistré + attestation d''enregistrement (quittance)', NULL,
        'Dans les 30 jours de la signature', 'Droits proportionnels — à confirmer', NULL, 30, 'JOURS', 5);
--   delai « Dans les 30 jours de la signature » -> depart = contrat de siege SIGNE (etape 5)
--   lecture alternative : contrat legalise (etape 6), soit un depart posterieur au fait generateur
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'CONTRAT_BAIL', 'Contrat enregistré + attestation d''enregistrement (quittance)'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 7;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'CONTRAT_DOMICILIATION', 'Contrat enregistré + attestation d''enregistrement (quittance)'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 7;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'TITRE_PROPRIETE', 'Contrat enregistré + attestation d''enregistrement (quittance)'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 7;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 2, 'ATTESTATION_ENREGISTREMENT', 'Contrat enregistré + attestation d''enregistrement (quittance)'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 7;

-- -- Etape 8 — Établissement des statuts
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 8, 'P2', 'P2 Rédaction',
        'Établissement des statuts', 'GENERATION_DOCUMENTS', 'Cabinet',
        'JURIKA', 'O', 'Tous dossiers',
        'Certificat négatif ; contrat de siège ; pièces d''identité des associés et du gérant ; répartition des parts ; description des apports', 'Statuts (SARL ou SARL AU)',
        'Projet de statuts validé par le client', 'STATUTS_SARL / STATUTS_SARL_AU (disponibles)',
        'J+1 à J+3', NULL, 'Toutes les variables du dictionnaire statuts', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'STATUTS', 'Projet de statuts validé par le client'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 8;

-- -- Etape 9 — Établissement de l'acte / PV de nomination du ou des gérants
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 9, 'P2', 'P2 Rédaction',
        'Établissement de l''acte / PV de nomination du ou des gérants', 'GENERATION_DOCUMENTS', 'Cabinet',
        'JURIKA', 'C', 'Si la gérance n''est pas désignée dans les statuts',
        'Identité et coordonnées du ou des gérants ; durée du mandat ; étendue des pouvoirs ; rémunération', 'Acte de nomination du gérant',
        'Acte validé par le client', 'ACTE_NOMINATION_GERANT (disponible)',
        'J+1', NULL, '$GERANT_NOM, $GERANT_CNI, $GERANT_QUALITE, $GERANT_DUREE_MANDAT', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ACTE_NOMINATION', 'Acte validé par le client'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 9;

-- -- Etape 10 — Évaluation des apports en nature — rapport du commissaire aux apports
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 10, 'P2', 'P2 Rédaction',
        'Évaluation des apports en nature — rapport du commissaire aux apports', 'GENERATION_DOCUMENTS', 'Commissaire aux apports',
        'Désigné par les associés (ou par le président du tribunal)', 'C', 'Apports en nature. Rapport obligatoire si un apport dépasse 100 000 DH ou si l''ensemble des apports en nature excède la moitié du capital',
        'Description et titres de propriété des biens apportés ; évaluation proposée', 'Lettre de mission / demande de désignation ; procès-verbal de décision des associés',
        'Rapport du commissaire aux apports, annexé aux statuts', 'RAPPORT_COMMISSAIRE_APPORTS (à créer)',
        'Avant signature des statuts', 'Honoraires du commissaire', '$APPORT_NATURE_*, $COMMISSAIRE_APPORTS_NOM', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RAPPORT_COMMISSAIRE_APPORTS', 'Rapport du commissaire aux apports, annexé aux statuts'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 10;

-- -- Etape 11 — État des actes accomplis pour le compte de la société en formation
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 11, 'P2', 'P2 Rédaction',
        'État des actes accomplis pour le compte de la société en formation', 'GENERATION_DOCUMENTS', 'Cabinet',
        'JURIKA', 'C', 'Si des engagements ont été pris avant l''immatriculation (bail, achats, embauches)',
        'Liste des actes et engagements, avec dates et montants', 'État des actes accomplis (annexe aux statuts) + reprise dans les statuts ou par décision des associés',
        'État annexé aux statuts signés', 'ETAT_ACTES_SOCIETE_EN_FORMATION (à créer)',
        'Avant signature des statuts', NULL, '$ACTES_EN_FORMATION_*', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ETAT_ACTES_FORMATION', 'État annexé aux statuts signés'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 11;

-- -- Etape 12 — Pouvoir / procuration donné au cabinet pour l'accomplissement des formalités
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 12, 'P2', 'P2 Rédaction',
        'Pouvoir / procuration donné au cabinet pour l''accomplissement des formalités', 'GENERATION_DOCUMENTS', 'Cabinet',
        'JURIKA', 'O', 'Tous dossiers où le cabinet dépose au nom du client',
        'CIN du mandant ; identification du mandataire', 'Pouvoir pour formalités de constitution',
        'Pouvoir signé et légalisé', 'POUVOIR_FORMALITES_CREATION (à créer)',
        'Avec la signature des actes', 'Timbre', '$MANDATAIRE_NOM, $MANDATAIRE_CNI', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'POUVOIR', 'Pouvoir signé et légalisé'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 12;

-- -- Etape 13 — Signature des statuts par tous les associés
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 13, 'P3', 'P3 Signature',
        'Signature des statuts par tous les associés', 'DEROULEMENT_DEMARCHE', 'Client',
        NULL, 'O', 'Tous dossiers',
        'Statuts en nombre suffisant d''originaux (prévoir 4 à 6) ; paraphe de chaque page', NULL,
        'Statuts signés', NULL,
        'J+1 après validation', NULL, NULL, NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'STATUTS', 'Statuts signés'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 13;

-- -- Etape 14 — Légalisation des signatures des statuts
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 14, 'P3', 'P3 Signature',
        'Légalisation des signatures des statuts', 'DEROULEMENT_DEMARCHE', 'Client',
        'Commune / arrondissement (ou notaire)', 'O', 'Tous dossiers',
        'Statuts signés ; CIN des signataires', NULL,
        'Statuts signés et légalisés', NULL,
        'J+1', 'Timbre par signature et par exemplaire', NULL, NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'STATUTS', 'Statuts signés et légalisés'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 14;

-- -- Etape 15 — Signature et légalisation de l'acte de nomination du gérant
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 15, 'P3', 'P3 Signature',
        'Signature et légalisation de l''acte de nomination du gérant', 'DEROULEMENT_DEMARCHE', 'Client',
        'Commune / arrondissement', 'C', 'Si acte de nomination non statutaire',
        'Acte en nombre suffisant d''originaux ; CIN des signataires', NULL,
        'Acte signé et légalisé', NULL,
        'J+1', 'Timbre par signature', NULL, NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ACTE_NOMINATION', 'Acte signé et légalisé'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 15;

-- -- Etape 16 — Dépôt du capital social sur un compte bloqué et attestation de blocage
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 16, 'P4', 'P4 Capital',
        'Dépôt du capital social sur un compte bloqué et attestation de blocage', 'DEROULEMENT_DEMARCHE', 'Client',
        'Banque', 'C', 'Obligatoire lorsque le capital atteint ou dépasse 100 000 DH ; facultatif en dessous (à confirmer avec la banque)',
        'Statuts (projet ou signés) ; certificat négatif ; CIN des associés et du gérant ; fonds à déposer', 'Lettre de demande d''ouverture de compte / liste des souscripteurs',
        'Attestation de blocage du capital', 'ATTESTATION_SOUSCRIPTION_LIBERATION (à créer)',
        'Dans les 8 jours de la réception des fonds (à confirmer)', 'Frais bancaires', '$CAPITAL_CHIFFRES, $BANQUE_NOM, $CAPITAL_LIBERE', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ATTESTATION_BLOCAGE_CAPITAL', 'Attestation de blocage du capital'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 16;

-- -- Etape 17 — Enregistrement des statuts
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 17, 'P4', 'P4 Enregistrement',
        'Enregistrement des statuts', 'DEROULEMENT_DEMARCHE', 'Cabinet',
        'DGI — service de l''enregistrement', 'O', 'Tous dossiers',
        'Statuts signés et légalisés (exemplaires) ; certificat négatif ; contrat de siège enregistré ; CIN du gérant', NULL,
        'Statuts enregistrés + attestation d''enregistrement', NULL,
        'Dans les 30 jours de l''acte', 'Exonération pour les constitutions dont le capital ≤ 500 000 DH ; au-delà 1 % (min. 1 000) — à confirmer selon la LF en vigueur', NULL, 30, 'JOURS', 13);
--   delai « Dans les 30 jours de l'acte » -> depart = statuts SIGNES (etape 13)
--   lecture alternative : statuts legalises (etape 14), soit un depart posterieur a l'acte lui-meme
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'STATUTS', 'Statuts enregistrés + attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 17;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 2, 'ATTESTATION_ENREGISTREMENT', 'Statuts enregistrés + attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 17;

-- -- Etape 18 — Enregistrement de l'acte de nomination du gérant
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 18, 'P4', 'P4 Enregistrement',
        'Enregistrement de l''acte de nomination du gérant', 'DEROULEMENT_DEMARCHE', 'Cabinet',
        'DGI — service de l''enregistrement', 'C', 'Si acte de nomination non statutaire',
        'Acte signé et légalisé ; statuts enregistrés', NULL,
        'Acte enregistré + attestation d''enregistrement', NULL,
        'Dans les 30 jours de l''acte', 'Droit fixe — à confirmer', NULL, 30, 'JOURS', 15);
--   delai « Dans les 30 jours de l'acte » -> depart = acte de nomination (etape 15, qui couvre signature ET legalisation)
--   lecture alternative : aucune : la signature est comprise dans l'etape 15
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ACTE_NOMINATION', 'Acte enregistré + attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 18;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 2, 'ATTESTATION_ENREGISTREMENT', 'Acte enregistré + attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 18;

-- -- Etape 19 — Demande d'inscription à la taxe professionnelle
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 19, 'P5', 'P5 Fiscal / RC',
        'Demande d''inscription à la taxe professionnelle', 'DEROULEMENT_DEMARCHE', 'Cabinet',
        'DGI — direction régionale / subdivision compétente', 'O', 'Tous dossiers',
        'Statuts enregistrés ; certificat négatif ; contrat de siège enregistré ; CIN du gérant ; plan de localisation', 'Demande d''inscription à la taxe professionnelle (formulaire DGI AAC050B)',
        'Attestation d''inscription à la taxe professionnelle', 'DEMANDE_TAXE_PROFESSIONNELLE (disponible)',
        'Dans les 30 jours du début d''activité', NULL, '$IDENTIFIANT_TP, $DIRECTION_REGIONALE, $SUBDIVISION', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'TP', 'Attestation d''inscription à la taxe professionnelle'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 19;

-- -- Etape 20 — Déclaration d'existence
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 20, 'P5', 'P5 Fiscal / RC',
        'Déclaration d''existence', 'DEROULEMENT_DEMARCHE', 'Cabinet',
        'DGI', 'O', 'Tous dossiers',
        'Statuts enregistrés ; certificat négatif ; contrat de siège ; CIN du gérant ; attestation TP', 'Déclaration d''existence (formulaire DGI ADP050B, art. 148 CGI)',
        'Bulletin d''identification fiscale (IF)', 'DECLARATION_EXISTENCE (disponible)',
        'Dans les 30 jours de la constitution / du début d''activité', NULL, '$IDENTIFIANT_FISCAL', 30, 'JOURS', 21);
--   delai « Dans les 30 jours de la constitution » -> depart = obtention du modele J (etape 21) — ARBITRE PAR LE CABINET
--   lecture alternative : aucune : le point a ete tranche par le cabinet le 05/09/2026
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'BULLETIN_IF', 'Bulletin d''identification fiscale (IF)'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 20;

-- -- Etape 21 — Dépôt légal et demande d'immatriculation au registre du commerce
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 21, 'P5', 'P5 Fiscal / RC',
        'Dépôt légal et demande d''immatriculation au registre du commerce', 'DEROULEMENT_DEMARCHE', 'Cabinet',
        'Greffe du tribunal de commerce, ou du tribunal de première instance si la ville n''en dispose pas', 'O', 'Tous dossiers',
        'Déclaration modèle 2 (3 originaux) ; statuts enregistrés (originaux + copies) ; certificat négatif ; attestation TP / IF ; contrat de siège enregistré ; CIN et copies légalisées des gérants ; attestation de blocage du capital si applicable ; pouvoir', 'Déclaration d''immatriculation au RC (modèle 2)',
        'Certificat d''immatriculation — modèle J, portant le n° RC', 'DECLARATION_IMMATRICULATION_RC (disponible)',
        'Dans les 3 mois de la constitution ; délivrance en 24 à 72 h', '≈ 350 (200 dépôt + 150 inscription) + 20 le modèle J', '$RC_NUMERO, $RC_VILLE, $TRIBUNAL_TYPE, $TRIBUNAL_VILLE', 3, 'MOIS', 13);
--   delai « Dans les 3 mois de la constitution » -> depart = signature des statuts (etape 13)
--   lecture alternative : aucune : le delai pour S'IMMATRICULER ne peut pas partir de l'immatriculation
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RC', 'Certificat d''immatriculation — modèle J, portant le n° RC'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 21;

-- -- Etape 22 — Obtention de l'identifiant commun de l'entreprise (ICE)
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 22, 'P5', 'P5 Fiscal / RC',
        'Obtention de l''identifiant commun de l''entreprise (ICE)', 'DEROULEMENT_DEMARCHE', 'Cabinet',
        'Plateforme ICE / RC — délivré avec l''immatriculation', 'O', 'Tous dossiers',
        'N° RC ; IF ; identifiant TP', NULL,
        'Attestation / numéro ICE', NULL,
        'Avec l''immatriculation', NULL, '$ICE', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ICE', 'Attestation / numéro ICE'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 22;

-- -- Etape 23 — Rédaction de l'avis de constitution
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 23, 'P6', 'P6 Publicité',
        'Rédaction de l''avis de constitution', 'DEROULEMENT_DEMARCHE', 'Cabinet',
        'JURIKA', 'O', 'Tous dossiers',
        'Statuts enregistrés ; modèle J', 'Avis de constitution',
        'Texte de l''avis validé', 'ANNONCE_LEGALE (constitution) (disponible)',
        'J+1 après immatriculation', NULL, NULL, NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ANNONCE_JAL', 'Texte de l''avis validé'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 23;

-- -- Etape 24 — Publication de l'avis dans un journal d'annonces légales
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 24, 'P6', 'P6 Publicité',
        'Publication de l''avis dans un journal d''annonces légales', 'DEROULEMENT_DEMARCHE', 'Cabinet',
        'Journal habilité', 'O', 'Tous dossiers',
        'Avis rédigé ; modèle J', NULL,
        'Exemplaire du journal contenant l''avis + facture', NULL,
        'Dans le mois de l''immatriculation', '≈ 150 à 1 500 selon le journal', NULL, 1, 'MOIS', 21);
--   delai « Dans le mois de l'immatriculation » -> depart = obtention du modele J (etape 21)
--   lecture alternative : aucune
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'JOURNAL_ANNONCE', 'Exemplaire du journal contenant l''avis + facture'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 24;

-- -- Etape 25 — Publication au Bulletin officiel
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 25, 'P6', 'P6 Publicité',
        'Publication au Bulletin officiel', 'DEROULEMENT_DEMARCHE', 'Cabinet',
        'Imprimerie officielle / Bulletin officiel', 'O', 'Tous dossiers',
        'Avis rédigé ; modèle J', NULL,
        'Justificatif de publication au BO', NULL,
        'Dans le mois de l''immatriculation', '≈ 460 (à confirmer)', NULL, 1, 'MOIS', 21);
--   delai « Dans le mois de l'immatriculation » -> depart = obtention du modele J (etape 21)
--   lecture alternative : aucune
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'PUBLICATION_BO', 'Justificatif de publication au BO'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 25;

-- -- Etape 26 — Affiliation à la CNSS et inscription aux téléservices DAMANCOM
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 26, 'P7', 'P7 Post-imm.',
        'Affiliation à la CNSS et inscription aux téléservices DAMANCOM', 'DEROULEMENT_DEMARCHE', 'Cabinet',
        'CNSS', 'O', 'Tous dossiers ; l''immatriculation des salariés suit l''embauche',
        'Modèle J ; statuts définitifs ; CIN du gérant ; RIB de la société ; demande d''affiliation', 'Demande d''affiliation employeur',
        'Attestation d''affiliation + numéro d''affiliation CNSS', 'DEMANDE_AFFILIATION_CNSS (à créer)',
        'Dans les 30 jours (embauche du 1er salarié / début d''activité)', 'Gratuit', '$CNSS_NUMERO', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'CNSS', 'Attestation d''affiliation + numéro d''affiliation CNSS'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 26;

-- -- Etape 27 — Déclaration des bénéficiaires effectifs
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 27, 'P7', 'P7 Post-imm.',
        'Déclaration des bénéficiaires effectifs', 'DEROULEMENT_DEMARCHE', 'Cabinet',
        'Registre des bénéficiaires effectifs — OMPIC (rbe.ompic.ma)', 'O', 'Toutes les sociétés immatriculées',
        'Identité, nationalité, date et lieu de naissance, résidence et pièce d''identité de chaque bénéficiaire effectif ; nature et étendue du contrôle exercé', 'Formulaire de déclaration des bénéficiaires effectifs',
        'Accusé de dépôt de la déclaration', 'DECLARATION_BENEFICIAIRES_EFFECTIFS (à créer)',
        'Dans le mois de l''immatriculation, puis dans le mois de toute modification', '— (sanction : 5 000 à 50 000 DH)', '$BENEFICIAIRE_EFFECTIF_*', 1, 'MOIS', 21);
--   delai « Dans le mois de l'immatriculation » -> depart = obtention du modele J (etape 21)
--   lecture alternative : aucune
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ACCUSE_RBE', 'Accusé de dépôt de la déclaration'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 27;

-- -- Etape 28 — Ouverture du compte bancaire définitif et déblocage du capital
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 28, 'P7', 'P7 Post-imm.',
        'Ouverture du compte bancaire définitif et déblocage du capital', 'DEROULEMENT_DEMARCHE', 'Client (assisté)',
        'Banque', 'O', 'Tous dossiers ; le déblocage ne concerne que les capitaux préalablement bloqués',
        'Modèle J ; statuts enregistrés ; ICE ; IF ; CIN du gérant ; spécimen de signature', 'Lettre de demande de déblocage du capital',
        'RIB et attestation d''ouverture de compte ; justificatif de déblocage', 'DEMANDE_DEBLOCAGE_CAPITAL (à créer)',
        'Après immatriculation', 'Frais bancaires', '$BANQUE_NOM, $RIB', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RIB', 'RIB et attestation d''ouverture de compte ; justificatif de déblocage'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 28;

-- -- Etape 29 — Cotation et paraphe des livres légaux
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 29, 'P7', 'P7 Post-imm.',
        'Cotation et paraphe des livres légaux', 'DEROULEMENT_DEMARCHE', 'Cabinet',
        'Greffe du tribunal', 'O', 'Tous dossiers',
        'Registres vierges (livre journal, grand livre, registre des procès-verbaux, registre des associés) ; modèle J', NULL,
        'Registres cotés et paraphés', NULL,
        'Avant la première clôture d''exercice', 'Droits de greffe', NULL, NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'LIVRES_LEGAUX', 'Registres cotés et paraphés'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 29;

-- -- Etape 30 — Autorisations, licences et agréments sectoriels
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 30, 'P7', 'P7 Post-imm.',
        'Autorisations, licences et agréments sectoriels', 'DEROULEMENT_DEMARCHE', 'Client (assisté)',
        'Autorité de tutelle selon l''activité', 'C', 'Activités réglementées (transport, santé, formation, agroalimentaire, BTP, import-export, débit de boissons…)',
        'Dossier propre à l''activité', 'Demandes et dossiers d''agrément',
        'Autorisation ou agrément', '— (au cas par cas)',
        'Variable', 'Variable', '$ACTIVITE_PRINCIPALE', NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'AUTORISATION_SECTORIELLE', 'Autorisation ou agrément'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 30;

-- -- Etape 31 — Adhésion aux téléservices fiscaux (SIMPL — DGI)
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 31, 'P7', 'P7 Post-imm.',
        'Adhésion aux téléservices fiscaux (SIMPL — DGI)', 'DEROULEMENT_DEMARCHE', 'Cabinet',
        'DGI', 'O', 'Tous dossiers',
        'IF ; ICE ; modèle J ; CIN du gérant', 'Demande d''adhésion aux téléservices',
        'Identifiants d''accès SIMPL', NULL,
        'Dès l''obtention de l''IF', 'Gratuit', NULL, NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'IDENTIFIANTS_SIMPL', 'Identifiants d''accès SIMPL'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 31;

-- -- Etape 32 — Déclaration des traitements de données personnelles
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 32, 'P7', 'P7 Post-imm.',
        'Déclaration des traitements de données personnelles', 'DEROULEMENT_DEMARCHE', 'Cabinet / client',
        'CNDP', 'C', 'Si la société traite des données personnelles (fichier clients, RH, vidéosurveillance)',
        'Description des traitements', 'Déclaration CNDP',
        'Récépissé de déclaration', NULL,
        'Avant la mise en œuvre du traitement', 'Gratuit', NULL, NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RECEPISSE_CNDP', 'Récépissé de déclaration'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 32;

-- -- Etape 33 — Vérification des mentions légales sur les documents commerciaux
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 33, 'P7', 'P7 Post-imm.',
        'Vérification des mentions légales sur les documents commerciaux', 'DEROULEMENT_DEMARCHE', 'Cabinet',
        NULL, 'O', 'Tous dossiers',
        'Modèles de factures, devis, papier à en-tête, site web', 'Note de conformité des mentions obligatoires (dénomination, forme, capital, siège, RC, IF, ICE, CNSS)',
        'Note remise au client', NULL,
        'À la clôture', NULL, NULL, NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'NOTE_CONFORMITE', 'Note remise au client'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 33;

-- -- Etape 34 — Constitution de la pochette dossier et contrôle de complétude
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 34, 'P8', 'P8 Clôture',
        'Constitution de la pochette dossier et contrôle de complétude', 'CLOTURE_DOSSIER', 'Cabinet',
        NULL, 'O', 'Tous dossiers',
        'Ensemble des justificatifs listés en colonne « Justificatif à obtenir et archiver »', 'Bordereau de pièces / sommaire du dossier',
        'Dossier complet, numérisé et indexé', 'BORDEREAU_REMISE_DOSSIER (à créer)',
        'À la clôture', NULL, NULL, NULL, NULL, NULL);
--   justificatif procedural (« Dossier complet, numérisé et indexé ») : aucune piece a televerser.

-- -- Etape 35 — Remise des originaux au client et signature du bordereau de remise
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 35, 'P8', 'P8 Clôture',
        'Remise des originaux au client et signature du bordereau de remise', 'CLOTURE_DOSSIER', 'Cabinet',
        NULL, 'O', 'Tous dossiers',
        'Originaux : statuts enregistrés, modèle J, attestations TP / IF / ICE / CNSS, certificat négatif, journaux de publication', 'Bordereau de remise',
        'Bordereau signé par le client', 'BORDEREAU_REMISE_DOSSIER (à créer)',
        'À la clôture', NULL, NULL, NULL, NULL, NULL);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'BORDEREAU_REMISE', 'Bordereau signé par le client'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 35;

-- -- Etape 36 — Clôture du ticket et alimentation de la fiche société
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre)
VALUES ('CREATION', 36, 'P8', 'P8 Clôture',
        'Clôture du ticket et alimentation de la fiche société', 'CLOTURE_DOSSIER', 'Cabinet',
        'JURIKA', 'O', 'Tous dossiers',
        'Données définitives : RC, IF, ICE, TP, CNSS, dates de publication', 'Fiche société consolidée (jeu de variables réutilisable pour les actes ultérieurs)',
        'Ticket clôturé', NULL,
        'À la clôture', NULL, '$RC_NUMERO, $IDENTIFIANT_FISCAL, $ICE, $IDENTIFIANT_TP, $CNSS_NUMERO', NULL, NULL, NULL);
--   justificatif procedural (« Ticket clôturé ») : aucune piece a televerser.
