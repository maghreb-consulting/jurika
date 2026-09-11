-- =====================================================================
-- JURIKA V24 — Referentiel du parcours de CREATION, version du 9 septembre
--
-- FICHIER GENERE. Ne pas editer a la main : regenerer avec
--   node scripts/lot1/derive-referentiel-demarches.mjs
-- Source : specs/creation-2026-09-09/1. Parcours creation.xlsx
--          une feuille, 9 colonnes, 51 lignes, 5 statuts.
--
-- CE QUE CETTE MIGRATION REMPLACE
-- V20 avait charge 36 etapes derivees du guide du 4 septembre. Le parcours
-- definitif du cabinet en compte 51. V20 N EST PAS EDITEE — une migration deja
-- appliquee ne doit jamais l etre (`validate-on-migrate: false` rendrait la
-- modification MUETTE sur toute base existante ; cf. V18 et V22). Le referentiel
-- est une donnee de reference : on le remplace integralement.
--
-- TROIS CHANGEMENTS DE STRUCTURE
--  1. `formalite_code` / `formalite_volet` : toute formalite comportant un depot
--     puis un retrait figure sur DEUX lignes, et les deux doivent se retrouver.
--     C est la date du DEPOT qui fait courir l attente du RETRAIT.
--  2. `actif` : une ligne du referentiel peut etre RETIREE du parcours sans etre
--     supprimee. C est ce qui permet a une demarche cochee sur une ligne
--     disparue de conserver son etat, son horodatage et ses justificatifs.
--  3. `phase_code` / `phase_libelle` portent desormais le STATUT (S1..S5) : le
--     parcours du 9 septembre n a plus de colonne « Phase », il regroupe par
--     statut. L API regroupe par code de phase — on fait coincider les deux
--     plutot que d inventer des phases que le cabinet n emploie pas.
--
-- Les colonnes que le nouveau classeur ne porte plus (acteur, organisme, pieces
-- entrantes, cout indicatif) restent NULL. Rien n est complete au juge.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Colonnes nouvelles
-- ---------------------------------------------------------------------
ALTER TABLE demarches_referentiel
    ADD COLUMN IF NOT EXISTS formalite_code  VARCHAR(40),
    ADD COLUMN IF NOT EXISTS formalite_volet VARCHAR(10),
    ADD COLUMN IF NOT EXISTS actif           BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE demarches_referentiel DROP CONSTRAINT IF EXISTS chk_formalite_volet;
ALTER TABLE demarches_referentiel
    ADD CONSTRAINT chk_formalite_volet CHECK (
        (formalite_code IS NULL     AND formalite_volet IS NULL)
     OR (formalite_code IS NOT NULL AND formalite_volet IN ('DEPOT','RETRAIT')));

-- Un seul depot et un seul retrait par formalite et par workflow : sans cette
-- unicite, « la ligne de depot de ma formalite » ne serait pas une notion.
-- Les lignes retirees (actif = FALSE) en sont exclues : une archive ne doit pas
-- empecher le rechargement du referentiel.
DROP INDEX IF EXISTS uq_demarches_formalite_volet;
CREATE UNIQUE INDEX uq_demarches_formalite_volet
    ON demarches_referentiel (workflow_type, formalite_code, formalite_volet)
    WHERE formalite_code IS NOT NULL AND actif;

COMMENT ON COLUMN demarches_referentiel.formalite_code IS
    'Formalite commune aux deux lignes depot / retrait. NULL pour une ligne unique.';
COMMENT ON COLUMN demarches_referentiel.formalite_volet IS
    'DEPOT ou RETRAIT. La date de cochage du DEPOT fait courir l''attente du RETRAIT.';
COMMENT ON COLUMN demarches_referentiel.actif IS
    'FALSE = ligne retiree du parcours, conservee pour les cochages qui la referencent.';

-- ---------------------------------------------------------------------
-- 2. Ce qui ne se rattache pas survit et se rapporte
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS demarches_migration_orphelines (
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    migre_le           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ticket_demarche_id UUID        NOT NULL,
    ticket_id          UUID        NOT NULL,
    ancien_ordre       SMALLINT    NOT NULL,
    ancien_libelle     TEXT        NOT NULL,
    etat               VARCHAR(20) NOT NULL,
    justificatifs      SMALLINT    NOT NULL,
    motif              TEXT        NOT NULL
);
COMMENT ON TABLE demarches_migration_orphelines IS
    'Demarches cochees qu''aucune ligne du parcours du 9 septembre n''accueille. Leur ligne ticket_demarches est CONSERVEE, rattachee a une ligne de referentiel retiree (actif = FALSE) : etat, horodatage, acteur et justificatifs intacts.';

-- ---------------------------------------------------------------------
-- 3. Archivage — une ligne retiree par ancienne ligne REFERENCEE
--
-- Pourquoi UNE PAR ANCIENNE LIGNE, et non une seule ligne-tampon : un ticket
-- porte plusieurs demarches, et `ticket_demarches` est UNIQUE (ticket_id,
-- demarche_id). Renvoyer trois demarches d un meme ticket sur le meme tampon
-- violerait cette unicite. La base en compte deja un cas : un ticket coche sur
-- les anciennes lignes 4, 5 et 6, dont deux sont sans equivalent.
--
-- Ordre de l archive = 9000 + ancien ordre. Les lignes du parcours vont de 1 a
-- 51 : aucun chevauchement possible.
-- ---------------------------------------------------------------------
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, obligatoire, condition_application,
        justificatifs_texte, actif)
SELECT dr.workflow_type, (9000 + dr.ordre)::SMALLINT, 'S0',
       'Referentiel retire (V20)', dr.libelle, dr.statut_ticket, dr.obligatoire,
       dr.condition_application, dr.justificatifs_texte, FALSE
  FROM demarches_referentiel dr
 WHERE dr.workflow_type = 'CREATION' AND dr.ordre < 9000
   AND EXISTS (SELECT 1 FROM ticket_demarches td WHERE td.demarche_id = dr.id)
ON CONFLICT (workflow_type, ordre) DO NOTHING;

UPDATE ticket_demarches td
   SET demarche_id = a.id
  FROM demarches_referentiel dr
  JOIN demarches_referentiel a
    ON a.workflow_type = 'CREATION' AND a.ordre = 9000 + dr.ordre
 WHERE td.demarche_id = dr.id
   AND dr.workflow_type = 'CREATION' AND dr.ordre < 9000;

-- ---------------------------------------------------------------------
-- 4. Rechargement du referentiel
-- ---------------------------------------------------------------------
DELETE FROM demarches_justificatifs WHERE demarche_id IN
    (SELECT id FROM demarches_referentiel
      WHERE workflow_type = 'CREATION' AND ordre < 9000);
DELETE FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre < 9000;

-- -- Ligne 1 — Ouverture du ticket
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 1, 'S1', 'Création du ticket et collecte d''information',
        'Ouverture du ticket', 'CREATION_TICKET', NULL, NULL, 'O', 'Tous dossiers',
        NULL, 'Fiche de renseignements et accusé d''ouverture de dossier',
        'Fiche de renseignements signée par le client', 'FICHE_RENSEIGNEMENTS_CREATION (livré)',
        'J0', NULL, '$DOSSIER_NUMERO, $DOSSIER_DATE_OUVERTURE, $DOSSIER_CHARGE, $DENOMINATION, $OBJET_SOCIAL', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'FICHE_RENSEIGNEMENTS', 'Fiche de renseignements signée par le client'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 1;

-- -- Ligne 2 — 1. Contrat de bail ou contrat de domiciliation
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 2, 'S2', 'Génération des documents',
        '1. Contrat de bail ou contrat de domiciliation', 'GENERATION_DOCUMENTS', NULL, NULL, 'C', 'Selon la voie retenue pour le siège',
        NULL, 'Contrat de bail commercial ou contrat de domiciliation',
        'Contrat signé, légalisé et enregistré ; attestation d''enregistrement', 'CONTRAT_BAIL / CONTRAT_DOMICILIATION (livrés)',
        'Avant la rédaction des statuts', NULL, '$SIEGE_TITRE_OCCUPATION, $SIEGE_SOCIAL, $BAILLEUR_*, $DOMICILIATAIRE_*, $BAIL_LOYER_CHIFFRES', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'CONTRAT_BAIL', 'Contrat signé, légalisé et enregistré ; attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 2;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'CONTRAT_DOMICILIATION', 'Contrat signé, légalisé et enregistré ; attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 2;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'TITRE_PROPRIETE', 'Contrat signé, légalisé et enregistré ; attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 2;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 2, 'ATTESTATION_ENREGISTREMENT', 'Contrat signé, légalisé et enregistré ; attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 2;

-- -- Ligne 3 — 2. Statuts
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 3, 'S2', 'Génération des documents',
        '2. Statuts', 'GENERATION_DOCUMENTS', NULL, NULL, 'O', 'Tous dossiers — SARL ou SARL AU selon $ASSOCIE_UNIQUE. Contrôles bloquants au lancement de la génération : certificat négatif obtenu ; pièces d''identité et de capacité des associés et des gérants réunies ; rapport du commissaire aux apports obtenu lorsque les apports en nature y sont soumis.',
        NULL, 'Statuts de la société',
        'Statuts signés, légalisés et enregistrés ; attestation d''enregistrement', 'STATUTS_SARL / STATUTS_SARL_AU (livrés)',
        'J+1 à J+3 après validation des données', NULL, 'Toutes les variables du dictionnaire statuts ; contrôles : $CERTIFICAT_NEGATIF_NUMERO, $CERTIFICAT_NEGATIF_DATE, $ASSOCIE_PIECE_NUMERO, $GERANT_PIECE_NUMERO, $COMMISSAIRE_APPORTS_DESIGNATION', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'STATUTS', 'Statuts signés, légalisés et enregistrés ; attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 3;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 2, 'ATTESTATION_ENREGISTREMENT', 'Statuts signés, légalisés et enregistrés ; attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 3;

-- -- Ligne 4 — 3. État des actes accomplis pour le compte de la société en formation
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 4, 'S2', 'Génération des documents',
        '3. État des actes accomplis pour le compte de la société en formation', 'GENERATION_DOCUMENTS', NULL, NULL, 'C', 'Si des engagements ont été pris avant l''immatriculation — annexe des statuts',
        NULL, 'État des actes accomplis, annexé aux statuts',
        'État annexé aux statuts signés', 'ETAT_ACTES_SOCIETE_EN_FORMATION (livré)',
        'Avec les statuts, avant leur signature', NULL, '$ACTES_EN_FORMATION_EXISTE, $ACTE_FORMATION_*, $ACTES_EN_FORMATION_TOTAL', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ETAT_ACTES_FORMATION', 'État annexé aux statuts signés'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 4;

-- -- Ligne 5 — 4. Acte de nomination du ou des gérants
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 5, 'S2', 'Génération des documents',
        '4. Acte de nomination du ou des gérants', 'GENERATION_DOCUMENTS', NULL, NULL, 'C', 'Si la gérance n''est pas désignée dans les statuts',
        NULL, 'Acte de nomination du gérant',
        'Acte signé, légalisé et enregistré ; attestation d''enregistrement', 'ACTE_NOMINATION_GERANT (livré)',
        'Avec les statuts', NULL, '$GERANT_MODE_DESIGNATION, $GERANT_NOM, $GERANT_PRENOM, $DUREE_GERANCE', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ACTE_NOMINATION', 'Acte signé, légalisé et enregistré ; attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 5;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 2, 'ATTESTATION_ENREGISTREMENT', 'Acte signé, légalisé et enregistré ; attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 5;

-- -- Ligne 6 — 5. Déclaration de souscription et de versement
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 6, 'S2', 'Génération des documents',
        '5. Déclaration de souscription et de versement', 'GENERATION_DOCUMENTS', NULL, NULL, 'O', 'Tous dossiers — pièce remise à la banque à l''appui du dépôt du capital',
        NULL, 'Déclaration de souscription et de versement',
        'Attestation de blocage du capital délivrée par la banque', 'ATTESTATION_SOUSCRIPTION_LIBERATION (livré)',
        'Avant le dépôt des fonds', NULL, '$DEPOT_FONDS_BLOQUE, $BANQUE_DEPOSITAIRE, $COMPTE_BANCAIRE_NUMERO, $SOUSCRIPTIONS_TOTAL_VERSE, $DATE_SOUSCRIPTION', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ATTESTATION_BLOCAGE_CAPITAL', 'Attestation de blocage du capital délivrée par la banque'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 6;

-- -- Ligne 7 — 6. Pouvoir pour l'accomplissement des formalités
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 7, 'S2', 'Génération des documents',
        '6. Pouvoir pour l''accomplissement des formalités', 'GENERATION_DOCUMENTS', NULL, NULL, 'O', 'Tous dossiers où le cabinet dépose au nom du client',
        NULL, 'Pouvoir donné au mandataire chargé des formalités',
        'Pouvoir signé et légalisé', 'POUVOIR_FORMALITES_CREATION (livré)',
        'Avec la signature des actes', NULL, '$POUVOIR_MANDANT, $POUVOIR_MANDATAIRE_NOM, $POUVOIR_MANDATAIRE_PIECE_NUMERO', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'POUVOIR', 'Pouvoir signé et légalisé'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 7;

-- -- Ligne 8 — 7. Demande d'inscription à la taxe professionnelle
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 8, 'S2', 'Génération des documents',
        '7. Demande d''inscription à la taxe professionnelle', 'GENERATION_DOCUMENTS', NULL, NULL, 'O', 'Tous dossiers',
        NULL, 'Demande d''inscription à la taxe professionnelle (formulaire DGI AAC050B)',
        'Attestation d''inscription à la taxe professionnelle', 'DEMANDE_TAXE_PROFESSIONNELLE (livré)',
        'Après enregistrement des statuts', NULL, '$IDENTIFIANT_TP, $DIRECTION_REGIONALE, $SUBDIVISION, $TP_OBJET, $TP_COMMUNE', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'TP', 'Attestation d''inscription à la taxe professionnelle'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 8;

-- -- Ligne 9 — 8. Demande d'immatriculation au registre du commerce
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 9, 'S2', 'Génération des documents',
        '8. Demande d''immatriculation au registre du commerce', 'GENERATION_DOCUMENTS', NULL, NULL, 'O', 'Tous dossiers',
        NULL, 'Déclaration d''immatriculation au RC (modèle 2), en-tête de tribunal conditionnel',
        'Certificat d''immatriculation — modèle J, portant le numéro RC', 'DECLARATION_IMMATRICULATION_RC (livré)',
        'Après obtention de l''attestation de taxe professionnelle', NULL, '$RC_VILLE, $TRIBUNAL_TYPE, $TRIBUNAL_VILLE, $CERTIFICAT_NEGATIF_NUMERO', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RC', 'Certificat d''immatriculation — modèle J, portant le numéro RC'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 9;

-- -- Ligne 10 — 9. Déclaration d'existence
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 10, 'S2', 'Génération des documents',
        '9. Déclaration d''existence', 'GENERATION_DOCUMENTS', NULL, NULL, 'O', 'Tous dossiers',
        NULL, 'Déclaration d''existence (formulaire DGI ADP050B, art. 148 CGI)',
        'Bulletin d''identification fiscale (IF)', 'DECLARATION_EXISTENCE (livré)',
        'Après enregistrement des statuts', NULL, '$IDENTIFIANT_FISCAL, $DE_REGIME_RESULTAT, $DE_TVA_ASSUJETTISSEMENT, $ASSOCIE_PRINCIPAL_NOM', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'BULLETIN_IF', 'Bulletin d''identification fiscale (IF)'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 10;

-- -- Ligne 11 — 10. Avis de publicité au journal d'annonces légales
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 11, 'S2', 'Génération des documents',
        '10. Avis de publicité au journal d''annonces légales', 'GENERATION_DOCUMENTS', NULL, NULL, 'O', 'Tous dossiers — généré ici, puis complété du numéro RC et modifiable pendant le déroulement de la démarche',
        NULL, 'Avis de constitution destiné au JAL et au Bulletin officiel',
        'Exemplaire du journal contenant l''avis et justificatif de publication au BO', 'ANNONCE_LEGALE (constitution) (livré)',
        'Généré avec les actes ; complété après immatriculation', NULL, '$DENOMINATION, $CAPITAL_CHIFFRES, $SIEGE_SOCIAL, $GERANT_NOM, $DATE_ACTE, $RC_NUMERO, $DATE_DEPOT_LEGAL', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'JOURNAL_ANNONCE', 'Exemplaire du journal contenant l''avis et justificatif de publication au BO'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 11;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 2, 'PUBLICATION_BO', 'Exemplaire du journal contenant l''avis et justificatif de publication au BO'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 11;

-- -- Ligne 12 — Contrôle de complétude du jeu de documents avant validation du client
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 12, 'S2', 'Génération des documents',
        'Contrôle de complétude du jeu de documents avant validation du client', 'GENERATION_DOCUMENTS', NULL, NULL, 'O', 'Tous dossiers',
        NULL, 'Bordereau récapitulatif des documents générés',
        'Validation écrite du client sur l''ensemble des actes', 'BORDEREAU_REMISE_DOSSIER (livré — emploi « récapitulatif »)',
        'Avant passage au statut suivant', NULL, '$BORDEREAU_OBJET, $PIECES_NOMBRE_TOTAL', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'VALIDATION_CLIENT', 'Validation écrite du client sur l''ensemble des actes'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 12;

-- -- Ligne 13 — Signature des statuts par tous les associés et paraphe de chaque page
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 13, 'S3', 'Déroulement de la démarche',
        'Signature des statuts par tous les associés et paraphe de chaque page', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, NULL,
        'Statuts signés', NULL,
        'Dès la validation écrite du client', NULL, NULL, NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'STATUTS', 'Statuts signés'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 13;

-- -- Ligne 14 — Légalisation des signatures des statuts, de l'acte de nomination et du pouvoir
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 14, 'S3', 'Déroulement de la démarche',
        'Légalisation des signatures des statuts, de l''acte de nomination et du pouvoir', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, NULL,
        'Actes légalisés', NULL,
        'J+1 après signature', NULL, NULL, NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'STATUTS', 'Actes légalisés'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 14;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ACTE_NOMINATION', 'Actes légalisés'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 14;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'POUVOIR', 'Actes légalisés'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 14;

-- -- Ligne 15 — Enregistrement du contrat de bail ou de domiciliation — dépôt   [ENREGISTREMENT_SIEGE / DEPOT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 15, 'S3', 'Déroulement de la démarche',
        'Enregistrement du contrat de bail ou de domiciliation — dépôt', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, NULL,
        'Récépissé de dépôt à l''enregistrement', NULL,
        'Dans les 30 jours de la signature du contrat', NULL, NULL, NULL, NULL, NULL, 'ENREGISTREMENT_SIEGE', 'DEPOT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RECEPISSE_DEPOT', 'Récépissé de dépôt à l''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 15;

-- -- Ligne 16 — Enregistrement du contrat de bail ou de domiciliation — retrait   [ENREGISTREMENT_SIEGE / RETRAIT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 16, 'S3', 'Déroulement de la démarche',
        'Enregistrement du contrat de bail ou de domiciliation — retrait', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, NULL,
        'Contrat enregistré et attestation d''enregistrement', NULL,
        'Selon le délai du service de l''enregistrement', NULL, NULL, NULL, NULL, NULL, 'ENREGISTREMENT_SIEGE', 'RETRAIT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'CONTRAT_BAIL', 'Contrat enregistré et attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 16;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'CONTRAT_DOMICILIATION', 'Contrat enregistré et attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 16;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 2, 'ATTESTATION_ENREGISTREMENT', 'Contrat enregistré et attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 16;

-- -- Ligne 17 — Dépôt du capital en compte bloqué — versement des fonds   [DEPOT_CAPITAL / DEPOT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 17, 'S3', 'Déroulement de la démarche',
        'Dépôt du capital en compte bloqué — versement des fonds', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'C', 'Si le capital atteint ou dépasse 100 000 DH',
        NULL, 'Déclaration de souscription et de versement remise à la banque',
        'Avis de versement de la banque', 'ATTESTATION_SOUSCRIPTION_LIBERATION (livré)',
        'Avant le dépôt au greffe', NULL, '$DEPOT_FONDS_BLOQUE, $BANQUE_DEPOSITAIRE, $COMPTE_BANCAIRE_NUMERO', NULL, NULL, NULL, 'DEPOT_CAPITAL', 'DEPOT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'AVIS_VERSEMENT_BANQUE', 'Avis de versement de la banque'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 17;

-- -- Ligne 18 — Dépôt du capital en compte bloqué — retrait de l'attestation de blocage   [DEPOT_CAPITAL / RETRAIT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 18, 'S3', 'Déroulement de la démarche',
        'Dépôt du capital en compte bloqué — retrait de l''attestation de blocage', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'C', 'Si le capital atteint ou dépasse 100 000 DH',
        NULL, NULL,
        'Attestation de blocage du capital', NULL,
        'Selon le délai de la banque', NULL, '$DATE_ATTESTATION_BLOCAGE', NULL, NULL, NULL, 'DEPOT_CAPITAL', 'RETRAIT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ATTESTATION_BLOCAGE_CAPITAL', 'Attestation de blocage du capital'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 18;

-- -- Ligne 19 — Enregistrement des statuts au service de l'enregistrement (DGI) — dépôt   [ENREGISTREMENT_STATUTS / DEPOT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 19, 'S3', 'Déroulement de la démarche',
        'Enregistrement des statuts au service de l''enregistrement (DGI) — dépôt', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, NULL,
        'Récépissé de dépôt à l''enregistrement', NULL,
        'Dans les 30 jours de l''acte', NULL, NULL, 30, 'JOURS', 13, 'ENREGISTREMENT_STATUTS', 'DEPOT', TRUE);
--   delai « Dans les 30 jours de l'acte » -> depart = signature des statuts (ligne 13)
--   lecture alternative : aucune : « l'acte » designe les statuts, dont la signature est la ligne 13
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RECEPISSE_DEPOT', 'Récépissé de dépôt à l''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 19;

-- -- Ligne 20 — Enregistrement des statuts au service de l'enregistrement (DGI) — retrait   [ENREGISTREMENT_STATUTS / RETRAIT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 20, 'S3', 'Déroulement de la démarche',
        'Enregistrement des statuts au service de l''enregistrement (DGI) — retrait', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, NULL,
        'Statuts enregistrés et attestation d''enregistrement', NULL,
        'Selon le délai du service de l''enregistrement', NULL, NULL, NULL, NULL, NULL, 'ENREGISTREMENT_STATUTS', 'RETRAIT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'STATUTS', 'Statuts enregistrés et attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 20;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 2, 'ATTESTATION_ENREGISTREMENT', 'Statuts enregistrés et attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 20;

-- -- Ligne 21 — Enregistrement de l'acte de nomination du gérant — dépôt   [ENREGISTREMENT_ACTE_NOMINATION / DEPOT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 21, 'S3', 'Déroulement de la démarche',
        'Enregistrement de l''acte de nomination du gérant — dépôt', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'C', 'Si acte de nomination non statutaire',
        NULL, NULL,
        'Récépissé de dépôt à l''enregistrement', NULL,
        'Dans les 30 jours de l''acte', NULL, NULL, 30, 'JOURS', 14, 'ENREGISTREMENT_ACTE_NOMINATION', 'DEPOT', TRUE);
--   delai « Dans les 30 jours de l'acte » -> depart = legalisation des signatures (ligne 14), qui couvre l'acte de nomination
--   lecture alternative : la date portee sur l'acte lui-meme, si elle etait saisie au dossier
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RECEPISSE_DEPOT', 'Récépissé de dépôt à l''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 21;

-- -- Ligne 22 — Enregistrement de l'acte de nomination du gérant — retrait   [ENREGISTREMENT_ACTE_NOMINATION / RETRAIT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 22, 'S3', 'Déroulement de la démarche',
        'Enregistrement de l''acte de nomination du gérant — retrait', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'C', 'Si acte de nomination non statutaire',
        NULL, NULL,
        'Acte enregistré et attestation d''enregistrement', NULL,
        'Selon le délai du service de l''enregistrement', NULL, NULL, NULL, NULL, NULL, 'ENREGISTREMENT_ACTE_NOMINATION', 'RETRAIT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ACTE_NOMINATION', 'Acte enregistré et attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 22;
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 2, 'ATTESTATION_ENREGISTREMENT', 'Acte enregistré et attestation d''enregistrement'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 22;

-- -- Ligne 23 — Demande d'inscription à la taxe professionnelle — dépôt   [TAXE_PROFESSIONNELLE / DEPOT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 23, 'S3', 'Déroulement de la démarche',
        'Demande d''inscription à la taxe professionnelle — dépôt', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, 'Demande d''inscription à la taxe professionnelle',
        'Récépissé de dépôt de la demande', 'DEMANDE_TAXE_PROFESSIONNELLE (livré)',
        'Dans les 30 jours du début d''activité', NULL, NULL, 30, 'JOURS', NULL, 'TAXE_PROFESSIONNELLE', 'DEPOT', TRUE);
--   delai « Dans les 30 jours du debut d'activite » -> depart = $DATE_DEBUT_ACTIVITE, saisi au parcours (etape 7)
--   lecture alternative : la date d'immatriculation, si le cabinet estime que l'activite commence a l immatriculation et non a la date declaree
--   point de depart = la DONNEE $DATE_DEBUT_ACTIVITE, et non le cochage
--   d une autre ligne. La colonne `delai_reference_donnee` est posee par V26 :
--   elle n existe pas encore au moment ou cette migration s execute.
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RECEPISSE_DEPOT', 'Récépissé de dépôt de la demande'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 23;

-- -- Ligne 24 — Demande d'inscription à la taxe professionnelle — retrait   [TAXE_PROFESSIONNELLE / RETRAIT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 24, 'S3', 'Déroulement de la démarche',
        'Demande d''inscription à la taxe professionnelle — retrait', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, NULL,
        'Attestation d''inscription à la taxe professionnelle', NULL,
        'Selon le délai de la subdivision compétente', NULL, '$IDENTIFIANT_TP', NULL, NULL, NULL, 'TAXE_PROFESSIONNELLE', 'RETRAIT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'TP', 'Attestation d''inscription à la taxe professionnelle'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 24;

-- -- Ligne 25 — Déclaration d'existence — dépôt   [DECLARATION_EXISTENCE / DEPOT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 25, 'S3', 'Déroulement de la démarche',
        'Déclaration d''existence — dépôt', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, 'Déclaration d''existence',
        'Récépissé de dépôt de la déclaration', 'DECLARATION_EXISTENCE (livré)',
        'Dans les 30 jours de la constitution', NULL, NULL, 30, 'JOURS', 28, 'DECLARATION_EXISTENCE', 'DEPOT', TRUE);
--   delai « Dans les 30 jours de la constitution » -> depart = retrait du modele J (ligne 28) — ARBITRE PAR LE CABINET le 05/09/2026
--   lecture alternative : aucune : le point a ete tranche par le cabinet
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RECEPISSE_DEPOT', 'Récépissé de dépôt de la déclaration'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 25;

-- -- Ligne 26 — Déclaration d'existence — retrait   [DECLARATION_EXISTENCE / RETRAIT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 26, 'S3', 'Déroulement de la démarche',
        'Déclaration d''existence — retrait', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, NULL,
        'Bulletin d''identification fiscale', NULL,
        'Selon le délai de la direction régionale', NULL, '$IDENTIFIANT_FISCAL', NULL, NULL, NULL, 'DECLARATION_EXISTENCE', 'RETRAIT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'BULLETIN_IF', 'Bulletin d''identification fiscale'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 26;

-- -- Ligne 27 — Immatriculation au registre du commerce — dépôt légal   [IMMATRICULATION_RC / DEPOT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 27, 'S3', 'Déroulement de la démarche',
        'Immatriculation au registre du commerce — dépôt légal', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, 'Déclaration d''immatriculation au RC (modèle 2)',
        'Récépissé de dépôt au greffe', 'DECLARATION_IMMATRICULATION_RC (livré)',
        'Dans les 3 mois de la constitution', NULL, '$DATE_DEPOT_LEGAL', 3, 'MOIS', 13, 'IMMATRICULATION_RC', 'DEPOT', TRUE);
--   delai « Dans les 3 mois de la constitution » -> depart = signature des statuts (ligne 13)
--   lecture alternative : aucune : le delai pour S'IMMATRICULER ne peut pas partir de l'immatriculation
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RECEPISSE_DEPOT', 'Récépissé de dépôt au greffe'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 27;

-- -- Ligne 28 — Immatriculation au registre du commerce — retrait du modèle J   [IMMATRICULATION_RC / RETRAIT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 28, 'S3', 'Déroulement de la démarche',
        'Immatriculation au registre du commerce — retrait du modèle J', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, NULL,
        'Certificat d''immatriculation — modèle J', NULL,
        '24 à 72 heures après le dépôt', NULL, '$RC_NUMERO, $RC_VILLE, $DATE_IMMATRICULATION', 3, 'JOURS', 27, 'IMMATRICULATION_RC', 'RETRAIT', TRUE);
--   delai « 24 a 72 heures apres le depot » -> depart = depot au greffe (ligne 27), borne HAUTE retenue : 72 h = 3 jours
--   lecture alternative : la borne basse (24 h = 1 jour)
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RC', 'Certificat d''immatriculation — modèle J'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 28;

-- -- Ligne 29 — Obtention de l'identifiant commun de l'entreprise (ICE)
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 29, 'S3', 'Déroulement de la démarche',
        'Obtention de l''identifiant commun de l''entreprise (ICE)', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, NULL,
        'Attestation ou numéro ICE', NULL,
        'Avec l''immatriculation', NULL, '$ICE', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ICE', 'Attestation ou numéro ICE'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 29;

-- -- Ligne 30 — Mise à jour de l'avis de constitution et publication au journal d'annonces légales
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 30, 'S3', 'Déroulement de la démarche',
        'Mise à jour de l''avis de constitution et publication au journal d''annonces légales', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, 'Avis de constitution complété du numéro RC',
        'Exemplaire du journal et facture d''insertion', 'ANNONCE_LEGALE (constitution) (livré)',
        'Dans le mois de l''immatriculation', NULL, '$RC_NUMERO, $DATE_IMMATRICULATION', 1, 'MOIS', 28, NULL, NULL, TRUE);
--   delai « Dans le mois de l'immatriculation » -> depart = retrait du modele J (ligne 28)
--   lecture alternative : aucune
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'JOURNAL_ANNONCE', 'Exemplaire du journal et facture d''insertion'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 30;

-- -- Ligne 31 — Publication au Bulletin officiel
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 31, 'S3', 'Déroulement de la démarche',
        'Publication au Bulletin officiel', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, NULL,
        'Justificatif de publication au BO', NULL,
        'Dans le mois de l''immatriculation', NULL, NULL, 1, 'MOIS', 28, NULL, NULL, TRUE);
--   delai « Dans le mois de l'immatriculation » -> depart = retrait du modele J (ligne 28)
--   lecture alternative : aucune
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'PUBLICATION_BO', 'Justificatif de publication au BO'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 31;

-- -- Ligne 32 — Affiliation à la CNSS et inscription aux téléservices DAMANCOM — dépôt   [AFFILIATION_CNSS / DEPOT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 32, 'S3', 'Déroulement de la démarche',
        'Affiliation à la CNSS et inscription aux téléservices DAMANCOM — dépôt', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, 'Demande d''affiliation employeur',
        'Récépissé de dépôt de la demande', 'DEMANDE_AFFILIATION_CNSS (livré)',
        'Dans les 30 jours du début d''activité ou de la première embauche', NULL, '$CNSS_DATE_PREMIER_SALARIE, $CNSS_MODE_DECLARATION', 30, 'JOURS', NULL, 'AFFILIATION_CNSS', 'DEPOT', TRUE);
--   delai « Dans les 30 jours du debut d'activite ou de la premiere embauche » -> depart = $DATE_DEBUT_ACTIVITE, saisi au parcours (etape 7)
--   lecture alternative : $CNSS_DATE_PREMIER_SALARIE, deja saisi, pour la branche « premiere embauche »
--   point de depart = la DONNEE $DATE_DEBUT_ACTIVITE, et non le cochage
--   d une autre ligne. La colonne `delai_reference_donnee` est posee par V26 :
--   elle n existe pas encore au moment ou cette migration s execute.
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RECEPISSE_DEPOT', 'Récépissé de dépôt de la demande'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 32;

-- -- Ligne 33 — Affiliation à la CNSS et inscription aux téléservices DAMANCOM — retrait   [AFFILIATION_CNSS / RETRAIT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 33, 'S3', 'Déroulement de la démarche',
        'Affiliation à la CNSS et inscription aux téléservices DAMANCOM — retrait', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, NULL,
        'Attestation d''affiliation, numéro CNSS et identifiants DAMANCOM', NULL,
        'Selon le délai de l''agence CNSS', NULL, '$CNSS_NUMERO', NULL, NULL, NULL, 'AFFILIATION_CNSS', 'RETRAIT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'CNSS', 'Attestation d''affiliation, numéro CNSS et identifiants DAMANCOM'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 33;

-- -- Ligne 34 — Déclaration des bénéficiaires effectifs
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 34, 'S3', 'Déroulement de la démarche',
        'Déclaration des bénéficiaires effectifs', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Toutes les sociétés',
        NULL, 'Déclaration des bénéficiaires effectifs',
        'Accusé de dépôt de la déclaration', 'DECLARATION_BENEFICIAIRES_EFFECTIFS (livré)',
        'Dans le mois de l''immatriculation', NULL, '$RBE_NATURE_DECLARATION, $BE_*', 1, 'MOIS', 28, NULL, NULL, TRUE);
--   delai « Dans le mois de l'immatriculation » -> depart = retrait du modele J (ligne 28)
--   lecture alternative : aucune
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ACCUSE_RBE', 'Accusé de dépôt de la déclaration'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 34;

-- -- Ligne 35 — Ouverture du compte bancaire définitif et déblocage du capital
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 35, 'S3', 'Déroulement de la démarche',
        'Ouverture du compte bancaire définitif et déblocage du capital', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers ; le déblocage ne concerne que les capitaux bloqués',
        NULL, 'Demande de déblocage du capital',
        'RIB et justificatif de déblocage', 'DEMANDE_DEBLOCAGE_CAPITAL (livré)',
        'Après immatriculation', NULL, '$COMPTE_DEFINITIF_NUMERO, $RIB, $DEBLOCAGE_DESTINATION', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RIB', 'RIB et justificatif de déblocage'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 35;

-- -- Ligne 36 — Cotation et paraphe des livres légaux au greffe — dépôt   [LIVRES_LEGAUX / DEPOT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 36, 'S3', 'Déroulement de la démarche',
        'Cotation et paraphe des livres légaux au greffe — dépôt', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, NULL,
        'Récépissé de dépôt des registres', NULL,
        'Avant la première clôture d''exercice', NULL, NULL, NULL, NULL, NULL, 'LIVRES_LEGAUX', 'DEPOT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RECEPISSE_DEPOT', 'Récépissé de dépôt des registres'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 36;

-- -- Ligne 37 — Cotation et paraphe des livres légaux au greffe — retrait   [LIVRES_LEGAUX / RETRAIT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 37, 'S3', 'Déroulement de la démarche',
        'Cotation et paraphe des livres légaux au greffe — retrait', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, NULL,
        'Registres cotés et paraphés', NULL,
        'Selon le délai du greffe', NULL, NULL, NULL, NULL, NULL, 'LIVRES_LEGAUX', 'RETRAIT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'LIVRES_LEGAUX', 'Registres cotés et paraphés'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 37;

-- -- Ligne 38 — Déclaration d'investissement étranger à l'Office des changes
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 38, 'S3', 'Déroulement de la démarche',
        'Déclaration d''investissement étranger à l''Office des changes', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'C', 'Si associé non-résident ou apport en devises',
        NULL, NULL,
        'Formulaire d''investissement étranger déposé et visé par la banque', NULL,
        'Lors de la réalisation de l''investissement — délai à confirmer par le cabinet', NULL, '$ASSOCIE_RESIDENCE, $ASSOCIE_NATIONALITE', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'INVESTISSEMENT_ETRANGER', 'Formulaire d''investissement étranger déposé et visé par la banque'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 38;

-- -- Ligne 39 — Déclaration des traitements de données personnelles (CNDP) — dépôt   [DECLARATION_CNDP / DEPOT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 39, 'S3', 'Déroulement de la démarche',
        'Déclaration des traitements de données personnelles (CNDP) — dépôt', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'C', 'Si la société traite des données personnelles',
        NULL, 'Déclaration CNDP',
        'Accusé de dépôt de la déclaration', 'DECLARATION_CNDP (livré)',
        'Avant la mise en œuvre du traitement', NULL, '$TRAITEMENT_DONNEES_PERSONNELLES, $CNDP_NATURE_DECLARATION', NULL, NULL, NULL, 'DECLARATION_CNDP', 'DEPOT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RECEPISSE_DEPOT', 'Accusé de dépôt de la déclaration'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 39;

-- -- Ligne 40 — Déclaration des traitements de données personnelles (CNDP) — retrait du récépissé   [DECLARATION_CNDP / RETRAIT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 40, 'S3', 'Déroulement de la démarche',
        'Déclaration des traitements de données personnelles (CNDP) — retrait du récépissé', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'C', 'Si la société traite des données personnelles',
        NULL, NULL,
        'Récépissé de déclaration ou autorisation', NULL,
        'Selon le délai de la commission', NULL, '$CNDP_RECEPISSE_NUMERO', NULL, NULL, NULL, 'DECLARATION_CNDP', 'RETRAIT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RECEPISSE_CNDP', 'Récépissé de déclaration ou autorisation'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 40;

-- -- Ligne 41 — Autorisations, licences et agréments sectoriels — dépôt   [AGREMENTS_SECTORIELS / DEPOT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 41, 'S3', 'Déroulement de la démarche',
        'Autorisations, licences et agréments sectoriels — dépôt', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'C', 'Si l''activité est réglementée',
        NULL, 'Demandes et dossiers d''agrément',
        'Récépissé de dépôt du dossier', NULL,
        'Variable selon l''autorité de tutelle', NULL, '$ACTIVITE_REGLEMENTEE, $ACTIVITE_REGLEMENTEE_PRECISION', NULL, NULL, NULL, 'AGREMENTS_SECTORIELS', 'DEPOT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RECEPISSE_DEPOT', 'Récépissé de dépôt du dossier'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 41;

-- -- Ligne 42 — Autorisations, licences et agréments sectoriels — retrait   [AGREMENTS_SECTORIELS / RETRAIT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 42, 'S3', 'Déroulement de la démarche',
        'Autorisations, licences et agréments sectoriels — retrait', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'C', 'Si l''activité est réglementée',
        NULL, NULL,
        'Autorisation, licence ou agrément', NULL,
        'Variable selon l''autorité de tutelle', NULL, NULL, NULL, NULL, NULL, 'AGREMENTS_SECTORIELS', 'RETRAIT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'AUTORISATION_SECTORIELLE', 'Autorisation, licence ou agrément'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 42;

-- -- Ligne 43 — Adhésion aux téléservices fiscaux (SIMPL — DGI) — dépôt   [ADHESION_SIMPL / DEPOT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 43, 'S3', 'Déroulement de la démarche',
        'Adhésion aux téléservices fiscaux (SIMPL — DGI) — dépôt', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, 'Demande d''adhésion aux téléservices',
        'Récépissé de dépôt de la demande', 'DEMANDE_ADHESION_SIMPL (livré)',
        'Dès l''obtention de l''identifiant fiscal', NULL, '$SIMPL_ADHESION_OBJET, $SIMPL_CONTACT_NOM', NULL, NULL, NULL, 'ADHESION_SIMPL', 'DEPOT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'RECEPISSE_DEPOT', 'Récépissé de dépôt de la demande'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 43;

-- -- Ligne 44 — Adhésion aux téléservices fiscaux (SIMPL — DGI) — retrait des identifiants   [ADHESION_SIMPL / RETRAIT]
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 44, 'S3', 'Déroulement de la démarche',
        'Adhésion aux téléservices fiscaux (SIMPL — DGI) — retrait des identifiants', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, NULL,
        'Identifiants d''accès SIMPL', NULL,
        'Selon le délai de la direction régionale', NULL, NULL, NULL, NULL, NULL, 'ADHESION_SIMPL', 'RETRAIT', TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'IDENTIFIANTS_SIMPL', 'Identifiants d''accès SIMPL'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 44;

-- -- Ligne 45 — Contrôle des mentions légales sur les documents commerciaux
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 45, 'S3', 'Déroulement de la démarche',
        'Contrôle des mentions légales sur les documents commerciaux', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, 'Note de conformité des mentions obligatoires',
        'Note remise au client', 'NOTE_CONFORMITE_MENTIONS_LEGALES (livré)',
        'Avant la remise du dossier', NULL, '$DENOMINATION, $CAPITAL_CHIFFRES, $RC_NUMERO, $IDENTIFIANT_FISCAL, $ICE, $CNSS_NUMERO', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'NOTE_CONFORMITE', 'Note remise au client'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 45;

-- -- Ligne 46 — Remise des originaux au client contre bordereau signé
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 46, 'S3', 'Déroulement de la démarche',
        'Remise des originaux au client contre bordereau signé', 'DEROULEMENT_DEMARCHE', NULL, NULL, 'O', 'Tous dossiers',
        NULL, 'Bordereau de remise',
        'Bordereau signé par le client valant décharge', 'BORDEREAU_REMISE_DOSSIER (livré — emploi « remise »)',
        'À l''achèvement des démarches', NULL, '$BORDEREAU_OBJET, $CLIENT_SIGNATAIRE_NOM', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'BORDEREAU_REMISE', 'Bordereau signé par le client valant décharge'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 46;

-- -- Ligne 47 — Archivage des documents définitifs sur la plateforme
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 47, 'S4', 'Clôture de dossier',
        'Archivage des documents définitifs sur la plateforme', 'CLOTURE_DOSSIER', NULL, NULL, 'O', 'Tous dossiers',
        NULL, 'Jeu complet des actes et justificatifs, numérisé et indexé au dossier électronique',
        'Certificat négatif ; contrat de bail ou de domiciliation enregistré et son attestation d''enregistrement ; statuts signés, légalisés et enregistrés et leur attestation d''enregistrement ; état des actes accomplis pour le compte de la société en formation ; rapport du commissaire aux apports ; acte de nomination du gérant enregistré ; pouvoir légalisé ; déclaration de souscription et de versement ; attestation de blocage du capital ; attestation d''inscription à la taxe professionnelle ; bulletin d''identification fiscale ; modèle J et récépissé de dépôt au greffe ; attestation ICE ; exemplaire du journal d''annonces légales et facture d''insertion ; justificatif de publication au Bulletin officiel ; attestation d''affiliation CNSS et identifiants DAMANCOM ; accusé de dépôt de la déclaration des bénéficiaires effectifs ; RIB et justificatif de déblocage du capital ; registres légaux cotés et paraphés ; récépissé CNDP et autorisations sectorielles le cas échéant ; note de conformité des mentions légales ; bordereau de remise signé par le client', NULL,
        'À la clôture', NULL, '$DOSSIER_NUMERO, $PIECES_NOMBRE_TOTAL, $PIECES_MANQUANTES', NULL, NULL, NULL, NULL, NULL, TRUE);
--   justificatif procedural : aucune piece a televerser au cochage.

-- -- Ligne 48 — Clôture du ticket et alimentation de la fiche société
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 48, 'S4', 'Clôture de dossier',
        'Clôture du ticket et alimentation de la fiche société', 'CLOTURE_DOSSIER', NULL, NULL, 'O', 'Tous dossiers',
        NULL, 'Fiche société consolidée, réutilisable pour les actes ultérieurs',
        'Ticket clôturé', NULL,
        'À la clôture', NULL, '$RC_NUMERO, $IDENTIFIANT_FISCAL, $ICE, $IDENTIFIANT_TP, $CNSS_NUMERO', NULL, NULL, NULL, NULL, NULL, TRUE);
--   justificatif procedural : aucune piece a televerser au cochage.

-- -- Ligne 49 — Enregistrement du motif d'annulation et arrêt des démarches en cours
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 49, 'S5', 'Ticket annulé',
        'Enregistrement du motif d''annulation et arrêt des démarches en cours', 'ANNULE', NULL, NULL, 'C', 'Abandon du client, dossier sans suite, refus non surmontable ou non-paiement',
        NULL, 'Note d''annulation motivée',
        'Note versée au dossier', 'NOTE_ANNULATION_DOSSIER (livré)',
        'À la survenance du motif', NULL, '$DOSSIER_MOTIF_ANNULATION, $DOSSIER_DATE_ANNULATION, $ANNULATION_ORIGINE', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'NOTE_ANNULATION', 'Note versée au dossier'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 49;

-- -- Ligne 50 — Retrait ou régularisation des dépôts en cours auprès des administrations
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 50, 'S5', 'Ticket annulé',
        'Retrait ou régularisation des dépôts en cours auprès des administrations', 'ANNULE', NULL, NULL, 'C', 'Si un dépôt a déjà été effectué',
        NULL, 'Lettres de retrait ou de régularisation',
        'Accusés de réception des administrations', 'LETTRE_RETRAIT_DEPOT (livré)',
        'Sans délai après la décision d''annulation', NULL, '$RETRAIT_OBJET, $RETRAIT_DEPOT_REFERENCE, $CERTIFICAT_NEGATIF_NUMERO', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'ACCUSE_RETRAIT_DEPOT', 'Accusés de réception des administrations'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 50;

-- -- Ligne 51 — Restitution des pièces originales au client contre décharge
INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,
        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,
        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,
        delai, cout_indicatif, variables_alimentees,
        delai_valeur, delai_unite, delai_reference_ordre,
        formalite_code, formalite_volet, actif)
VALUES ('CREATION', 51, 'S5', 'Ticket annulé',
        'Restitution des pièces originales au client contre décharge', 'ANNULE', NULL, NULL, 'O', 'Tous dossiers annulés',
        NULL, 'Bordereau de restitution',
        'Bordereau signé par le client', 'BORDEREAU_REMISE_DOSSIER (livré — emploi « restitution »)',
        'À la clôture du ticket', NULL, '$BORDEREAU_OBJET, $CLIENT_SIGNATAIRE_NOM', NULL, NULL, NULL, NULL, NULL, TRUE);
INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)
SELECT id, 1, 'BORDEREAU_REMISE', 'Bordereau signé par le client'
  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = 51;

-- ---------------------------------------------------------------------
-- 5. Rattachement des demarches deja cochees
--
-- La table ci-dessous est la correspondance 36 -> 51, etablie ligne par ligne
-- (voir output/lotB/correspondance-36-51.md). Quand une ancienne ligne s est
-- SCINDEE en depot puis retrait, la ligne d accueil est celle du DEPOT : c est
-- elle qui porte le geste deja accompli.
-- ---------------------------------------------------------------------
CREATE TEMPORARY TABLE tmp_corr (
    ancien  SMALLINT PRIMARY KEY,
    nouveau SMALLINT,
    motif   TEXT
) ON COMMIT DROP;

INSERT INTO tmp_corr (ancien, nouveau, motif) VALUES
    (1, 1, NULL),
    (2, NULL, 'Le controle d''identite et de capacite des associes n''est plus une etape : il est devenu un CONTROLE BLOQUANT au lancement de la generation des statuts (§ 18 du dictionnaire).'),
    (3, 1, NULL),
    (4, NULL, 'L''obtention du certificat negatif n''est plus une etape : elle est devenue un CONTROLE BLOQUANT au lancement de la generation des statuts (§ 18).'),
    (5, 2, NULL),
    (6, NULL, 'La legalisation des signatures du contrat de siege ne figure plus au parcours : la ligne 14 ne couvre que les statuts, l''acte de nomination et le pouvoir.'),
    (7, 15, NULL),
    (8, 3, NULL),
    (9, 5, NULL),
    (10, NULL, 'L''evaluation des apports en nature n''est plus une etape : le rapport du commissaire aux apports est devenu un CONTROLE BLOQUANT (§ 18).'),
    (11, 4, NULL),
    (12, 7, NULL),
    (13, 13, NULL),
    (14, 14, NULL),
    (15, 14, NULL),
    (16, 17, NULL),
    (17, 19, NULL),
    (18, 21, NULL),
    (19, 23, NULL),
    (20, 25, NULL),
    (21, 27, NULL),
    (22, 29, NULL),
    (23, 11, NULL),
    (24, 30, NULL),
    (25, 31, NULL),
    (26, 32, NULL),
    (27, 34, NULL),
    (28, 35, NULL),
    (29, 36, NULL),
    (30, 41, NULL),
    (31, 43, NULL),
    (32, 39, NULL),
    (33, 45, NULL),
    (34, 12, NULL),
    (35, 46, NULL),
    (36, 48, NULL);

-- Un ticket peut porter DEUX anciennes lignes qui pointent la meme nouvelle
-- (les anciennes 14 et 15 fusionnent dans la ligne 14). `ticket_demarches` etant
-- UNIQUE (ticket_id, demarche_id), une seule peut etre deplacee : on retient la
-- plus ancienne, et l autre reste archivee et rapportee. DISTINCT ON fige ce
-- choix AVANT l UPDATE — un NOT EXISTS dans l UPDATE serait evalue sur
-- l instantane d avant, et laisserait passer les deux.
CREATE TEMPORARY TABLE tmp_deplacement ON COMMIT DROP AS
SELECT DISTINCT ON (td.ticket_id, c.nouveau)
       td.id AS ticket_demarche_id, n.id AS nouvelle_demarche_id
  FROM ticket_demarches td
  JOIN demarches_referentiel a ON a.id = td.demarche_id
   AND a.workflow_type = 'CREATION' AND a.ordre > 9000
  JOIN tmp_corr c ON c.ancien = a.ordre - 9000 AND c.nouveau IS NOT NULL
  JOIN demarches_referentiel n
    ON n.workflow_type = 'CREATION' AND n.ordre = c.nouveau AND n.actif
 ORDER BY td.ticket_id, c.nouveau, a.ordre;

UPDATE ticket_demarches td
   SET demarche_id = d.nouvelle_demarche_id
  FROM tmp_deplacement d
 WHERE td.id = d.ticket_demarche_id;

-- Ce qui reste sur une ligne archivee n a pas trouve d accueil : soit l ancienne
-- ligne n a pas d equivalent, soit la nouvelle etait deja prise sur ce ticket.
-- On le RAPPORTE, on ne le supprime pas.
INSERT INTO demarches_migration_orphelines
       (ticket_demarche_id, ticket_id, ancien_ordre, ancien_libelle, etat,
        justificatifs, motif)
SELECT td.id, td.ticket_id, (a.ordre - 9000)::SMALLINT, a.libelle, td.etat,
       (SELECT count(*) FROM ticket_demarche_justificatifs j
         WHERE j.ticket_demarche_id = td.id),
       COALESCE(c.motif,
           'Fusionnee : la ligne ' || c.nouveau || ' du parcours du 9 septembre etait deja occupee sur ce ticket par une autre ancienne ligne.')
  FROM ticket_demarches td
  JOIN demarches_referentiel a ON a.id = td.demarche_id
   AND a.workflow_type = 'CREATION' AND a.ordre > 9000
  LEFT JOIN tmp_corr c ON c.ancien = a.ordre - 9000;

-- Les archives que plus personne ne reference disparaissent ; celles qui portent
-- encore un cochage restent, inactives.
DELETE FROM demarches_referentiel dr
 WHERE dr.workflow_type = 'CREATION' AND dr.ordre > 9000
   AND NOT EXISTS (SELECT 1 FROM ticket_demarches td WHERE td.demarche_id = dr.id);

-- ---------------------------------------------------------------------
-- 6. Garde-fous
-- ---------------------------------------------------------------------
DO $$
DECLARE n INTEGER;
BEGIN
    SELECT count(*) INTO n FROM demarches_referentiel
     WHERE workflow_type = 'CREATION' AND actif;
    IF n <> 51 THEN
        RAISE EXCEPTION 'Migration V24 : % lignes actives au lieu de 51', n;
    END IF;

    SELECT count(*) INTO n FROM demarches_referentiel
     WHERE workflow_type = 'CREATION' AND actif AND formalite_code IS NOT NULL;
    IF n <> 24 THEN
        RAISE EXCEPTION 'Migration V24 : % lignes depot/retrait au lieu de 24', n;
    END IF;

    -- Aucune demarche cochee ne doit avoir disparu.
    SELECT count(*) INTO n FROM ticket_demarches td
     WHERE NOT EXISTS (SELECT 1 FROM demarches_referentiel dr
                        WHERE dr.id = td.demarche_id);
    IF n > 0 THEN
        RAISE EXCEPTION 'Migration V24 : % cochage(s) orphelin(s) de referentiel', n;
    END IF;
END $$;
