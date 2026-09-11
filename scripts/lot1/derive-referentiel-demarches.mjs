/**
 * Derive la migration Flyway du referentiel des demarches depuis le parcours du
 * cabinet — PAS de recopie manuelle : le cabinet fournira des versions
 * ulterieures du classeur, et une recopie introduirait des ecarts.
 *
 *   node scripts/lot1/derive-referentiel-demarches.mjs
 *
 * ---------------------------------------------------------------------------
 * LOT B (2026-09-11) — LE SCRIPT CHANGE DE SOURCE, PAS DE METIER.
 *
 * Il lisait `GUIDE_CREATION_ENTREPRISE_MAROC_SARL_v2.xlsx` (4 septembre) : deux
 * onglets, 14 colonnes, 36 etapes reparties en 8 phases, et un onglet
 * « 2. Workflow ticket » qui donnait les plages d'etapes par statut.
 *
 * Il lit desormais `1. Parcours creation.xlsx` (9 septembre) : UNE seule
 * feuille, 9 colonnes, **51 lignes**, et plus aucune phase — le parcours est
 * regroupe par STATUT DE TICKET, materialise par des lignes-bandeaux
 * (« CRÉATION DU TICKET ET COLLECTE D'INFORMATION », « GÉNÉRATION DES
 * DOCUMENTS », …) qui n'ont pas de numero.
 *
 * Cinq colonnes ont disparu : acteur, organisme, pieces entrantes, cout
 * indicatif, et la phase. Elles restent NULL en base — on ne complete rien au
 * juge. Deux colonnes NOT NULL du schema sont donc DERIVEES, et la derivation
 * est tracee au rapport technique :
 *
 *   . `phase_code` / `phase_libelle` <- le STATUT de la ligne (S1..S5). Le
 *     parcours du cabinet regroupe par statut ; l'API regroupe par code de
 *     phase. On fait coincider les deux plutot que d'inventer des phases.
 *   . `obligatoire` <- la CONDITION D'APPLICATION. « Tous dossiers »,
 *     « Toutes les societes », « Tous dossiers annules » -> 'O' ; tout le
 *     reste -> 'C'. La condition reste stockee verbatim a cote.
 *
 * Deux colonnes sont AJOUTEES au schema (migration V24) :
 *
 *   . `formalite_code` / `formalite_volet` — toute formalite comportant un
 *     depot puis un retrait figure sur DEUX lignes. Ce n'est pas une commodite
 *     d'affichage : c'est la date du DEPOT qui fait courir l'attente du
 *     RETRAIT. Sans ce lien, la ligne « retrait » ne sait pas depuis quand
 *     elle attend.
 *
 * Sorties  : backend-java/ticket-service/src/main/resources/db/migration/
 *              V24__referentiel_parcours_creation_51.sql
 *            output/lotB/rapport-cabinet.md       (ce que le cabinet doit trancher)
 *            output/lotB/referentiel-technique.md (trace exhaustive, pour la relecture)
 *            output/lotB/correspondance-36-51.md  (ce que deviennent les anciennes lignes)
 *
 * Regle inchangee : ne RIEN inventer. Une cellule vide reste NULL en base et est
 * reportee dans le rapport d'anomalies ; aucun delai ni condition n'est complete
 * au juge. Seule exception assumee et tracee : le TYPAGE des justificatifs (le
 * parcours les decrit en texte libre, la base a besoin d'un document_type) —
 * chaque typage figure dans TYPAGE_JUSTIFICATIFS ci-dessous et dans le rapport.
 */
import fs from 'node:fs';
import path from 'node:path';
import { readWorkbook } from './lib-xlsx.mjs';

const RACINE = path.resolve(import.meta.dirname, '../..');
const CLASSEUR = 'C:/dev/JURIKA/specs/creation-2026-09-09/1. Parcours creation.xlsx';
const SQL_SORTIE = path.join(RACINE,
  'backend-java/ticket-service/src/main/resources/db/migration/V24__referentiel_parcours_creation_51.sql');
const RAPPORT_CABINET = path.join(RACINE, 'output/lotB/rapport-cabinet.md');
const RAPPORT_TECHNIQUE = path.join(RACINE, 'output/lotB/referentiel-technique.md');
const RAPPORT_CORRESPONDANCE = path.join(RACINE, 'output/lotB/correspondance-36-51.md');

/** Colonnes du parcours du 9 septembre (index 0 = colonne A). */
const COL = {
  numero: 0, statut: 1, libelle: 2, condition: 3, delai: 4,
  documentProduit: 5, justificatifs: 6, modele: 7, variables: 8,
};

/** Libelle de statut (colonne B) -> code persiste. Les cinq statuts du cabinet. */
const CODE_STATUT = {
  "Création du ticket et collecte d'information": 'CREATION_TICKET',
  'Génération des documents': 'GENERATION_DOCUMENTS',
  'Déroulement de la démarche': 'DEROULEMENT_DEMARCHE',
  'Clôture de dossier': 'CLOTURE_DOSSIER',
  'Ticket annulé': 'ANNULE',
};

/**
 * Phase derivee du statut. Le parcours du 9 septembre n'a plus de colonne
 * « Phase » : il regroupe par statut. L'API, elle, regroupe par code de phase.
 * On fait coincider les deux — un code, un libelle, et le libelle est celui du
 * cabinet.
 *
 * `phase_libelle` est contraint a VARCHAR(60) : le plus long ci-dessous fait
 * 43 caracteres.
 */
const PHASE_DU_STATUT = {
  CREATION_TICKET:      { code: 'S1', libelle: "Création du ticket et collecte d'information" },
  GENERATION_DOCUMENTS: { code: 'S2', libelle: 'Génération des documents' },
  DEROULEMENT_DEMARCHE: { code: 'S3', libelle: 'Déroulement de la démarche' },
  CLOTURE_DOSSIER:      { code: 'S4', libelle: 'Clôture de dossier' },
  ANNULE:               { code: 'S5', libelle: 'Ticket annulé' },
};

/**
 * Conditions d'application qui valent « obligatoire ». Comparees en minuscules,
 * sur le DEBUT de la cellule : « Tous dossiers — SARL ou SARL AU selon … »
 * reste obligatoire, la precision ne porte que sur la variante de modele.
 */
const CONDITIONS_OBLIGATOIRES = [
  'tous dossiers',
  'toutes les sociétés',
];

/**
 * DEPOT ET RETRAIT — les douze formalites qui figurent sur deux lignes.
 *
 * Le parcours du cabinet les scinde parce que l'etat reel d'un dossier ne se lit
 * pas autrement : un contrat depose a l'enregistrement n'est pas un contrat
 * enregistre. Le MODELE doit porter ce lien, sans quoi la ligne « retrait » ne
 * sait pas depuis quand elle attend — et « quand avons-nous depose ? » est la
 * question qui se pose des mois plus tard.
 *
 * `code` nomme la formalite ; les deux lignes le partagent.
 */
const FORMALITES = {
  15: ['ENREGISTREMENT_SIEGE', 'DEPOT'],
  16: ['ENREGISTREMENT_SIEGE', 'RETRAIT'],
  17: ['DEPOT_CAPITAL', 'DEPOT'],
  18: ['DEPOT_CAPITAL', 'RETRAIT'],
  19: ['ENREGISTREMENT_STATUTS', 'DEPOT'],
  20: ['ENREGISTREMENT_STATUTS', 'RETRAIT'],
  21: ['ENREGISTREMENT_ACTE_NOMINATION', 'DEPOT'],
  22: ['ENREGISTREMENT_ACTE_NOMINATION', 'RETRAIT'],
  23: ['TAXE_PROFESSIONNELLE', 'DEPOT'],
  24: ['TAXE_PROFESSIONNELLE', 'RETRAIT'],
  25: ['DECLARATION_EXISTENCE', 'DEPOT'],
  26: ['DECLARATION_EXISTENCE', 'RETRAIT'],
  27: ['IMMATRICULATION_RC', 'DEPOT'],
  28: ['IMMATRICULATION_RC', 'RETRAIT'],
  32: ['AFFILIATION_CNSS', 'DEPOT'],
  33: ['AFFILIATION_CNSS', 'RETRAIT'],
  36: ['LIVRES_LEGAUX', 'DEPOT'],
  37: ['LIVRES_LEGAUX', 'RETRAIT'],
  39: ['DECLARATION_CNDP', 'DEPOT'],
  40: ['DECLARATION_CNDP', 'RETRAIT'],
  41: ['AGREMENTS_SECTORIELS', 'DEPOT'],
  42: ['AGREMENTS_SECTORIELS', 'RETRAIT'],
  43: ['ADHESION_SIMPL', 'DEPOT'],
  44: ['ADHESION_SIMPL', 'RETRAIT'],
};

/**
 * DELAIS MECANISABLES — valeur, unite, et etape dont la date de cochage sert de
 * point de depart.
 *
 * Regles acquises au lot 1 et inchangees :
 *   . calcul en MOIS CALENDAIRES (`plusMonths`) quand l'unite est MOIS ;
 *   . fuseau fige a Africa/Casablanca ;
 *   . point de depart LE PLUS PRECOCE quand le texte est ambigu — une alerte
 *     prematuree est un inconfort, une alerte tardive est une faute ;
 *   . AUCUNE date fabriquee quand le point de depart n'est pas mecanisable :
 *     la ligne reste sans echeance et l'interface dit pourquoi.
 *
 * Une ligne ABSENTE d'ici et portant pourtant un delai chiffre est signalee au
 * rapport : c'est un delai que le produit n'alertera pas, et le cabinet doit le
 * savoir.
 */
const DELAIS = {
  19: {
    valeur: 30, unite: 'JOURS', reference: 13,
    texte: "Dans les 30 jours de l'acte",
    lecture: 'depart = signature des statuts (ligne 13)',
    alternative: "aucune : « l'acte » designe les statuts, dont la signature est la ligne 13",
    consequence: 'aucune : le jalon retenu est le seul possible.',
  },
  21: {
    valeur: 30, unite: 'JOURS', reference: 14,
    texte: "Dans les 30 jours de l'acte",
    lecture: "depart = legalisation des signatures (ligne 14), qui couvre l'acte de nomination",
    alternative: "la date portee sur l'acte lui-meme, si elle etait saisie au dossier",
    consequence: "la legalisation suit la signature de peu ; l'ecart joue en faveur du cabinet "
      + "(alerte legerement plus tardive). A confirmer si la date d'acte devient une donnee saisie.",
    aTrancherEnPriorite: true,
  },
  25: {
    valeur: 30, unite: 'JOURS', reference: 28,
    texte: 'Dans les 30 jours de la constitution',
    // ARBITRAGE DU CABINET (2026-09-05), repris tel quel : « la constitution »
    // s'entend de l'immatriculation au registre du commerce. La societe acquiert
    // la personnalite morale a ce moment-la ; le delai ne court pas avant.
    lecture: 'depart = retrait du modele J (ligne 28) — ARBITRE PAR LE CABINET le 05/09/2026',
    alternative: 'aucune : le point a ete tranche par le cabinet',
    consequence: 'aucune : le point est tranche.',
  },
  27: {
    valeur: 3, unite: 'MOIS', reference: 13,
    texte: 'Dans les 3 mois de la constitution',
    lecture: 'depart = signature des statuts (ligne 13)',
    alternative: "aucune : le delai pour S'IMMATRICULER ne peut pas partir de l'immatriculation",
    consequence: "aucune : le delai pour s'immatriculer ne peut pas partir de l'immatriculation.",
  },
  28: {
    valeur: 3, unite: 'JOURS', reference: 27,
    texte: '24 a 72 heures apres le depot',
    lecture: 'depart = depot au greffe (ligne 27), borne HAUTE retenue : 72 h = 3 jours',
    alternative: 'la borne basse (24 h = 1 jour)',
    consequence: "la borne basse alerterait des le lendemain d'un depot normal. On retient la "
      + "borne haute : c'est un delai de SERVICE du greffe, pas une obligation du cabinet.",
  },
  30: {
    valeur: 1, unite: 'MOIS', reference: 28,
    texte: "Dans le mois de l'immatriculation",
    lecture: 'depart = retrait du modele J (ligne 28)',
    alternative: 'aucune',
    consequence: 'aucune.',
  },
  31: {
    valeur: 1, unite: 'MOIS', reference: 28,
    texte: "Dans le mois de l'immatriculation",
    lecture: 'depart = retrait du modele J (ligne 28)',
    alternative: 'aucune',
    consequence: 'aucune.',
  },
  34: {
    valeur: 1, unite: 'MOIS', reference: 28,
    texte: "Dans le mois de l'immatriculation",
    lecture: 'depart = retrait du modele J (ligne 28)',
    alternative: 'aucune',
    consequence: 'aucune.',
  },

  // ---------------------------------------------------------------------
  // DEUX DELAIS DONT LE POINT DE DEPART EST UNE DONNEE, ET NON UN COCHAGE.
  //
  // Ces deux lignes portaient « dans les 30 jours du debut d'activite » et
  // n'etaient PAS mecanisables : aucune ligne cochable ne porte le debut
  // d'activite, et le lot 1 avait donc — a raison — refuse de fabriquer une
  // echeance. Le lot B a ouvert le champ `$DATE_DEBUT_ACTIVITE` (la vingtieme
  // saisie heritee, trouvee en lisant la fiche de renseignements) : la donnee
  // existe desormais, et ces deux delais legaux cessent d'etre aveugles.
  //
  // `donnee` au lieu de `reference` : le depart se lit dans la SAISIE du
  // dossier, pas dans la date de cochage d'une autre ligne. La regle du lot 1
  // reste entiere — tant que la date n'est pas saisie, AUCUNE alerte n'est
  // levee et aucune date n'est fabriquee.
  //
  // La colonne `delai_reference_donnee` est ajoutee par la migration V26 : V24
  // ne peut pas la referencer, elle est anterieure. C'est donc V26 qui pose la
  // valeur, ici comme sur les bases deja migrees.
  // ---------------------------------------------------------------------
  23: {
    valeur: 30, unite: 'JOURS', donnee: 'DATE_DEBUT_ACTIVITE',
    texte: "Dans les 30 jours du debut d'activite",
    lecture: "depart = $DATE_DEBUT_ACTIVITE, saisi au parcours (etape 7)",
    alternative: "la date d'immatriculation, si le cabinet estime que l'activite "
      + 'commence a l immatriculation et non a la date declaree',
    consequence: "l'immatriculation est POSTERIEURE au debut d'activite declare dans le "
      + "cas general : la retenir alerterait plus tard, donc moins tot que l'obligation. "
      + 'On retient la donnee declaree, qui est celle que porte l imprime.',
  },
  32: {
    valeur: 30, unite: 'JOURS', donnee: 'DATE_DEBUT_ACTIVITE',
    texte: "Dans les 30 jours du debut d'activite ou de la premiere embauche",
    lecture: "depart = $DATE_DEBUT_ACTIVITE, saisi au parcours (etape 7)",
    alternative: '$CNSS_DATE_PREMIER_SALARIE, deja saisi, pour la branche « premiere embauche »',
    consequence: "le texte dit « OU » : le delai court du PREMIER des deux evenements. Le "
      + "debut d'activite precede la premiere embauche dans le cas general, et c'est donc "
      + 'lui qui donne l echeance la plus precoce — une alerte prematuree est un inconfort, '
      + 'une alerte tardive est une faute. A revoir si le cabinet constate le cas inverse.',
    aTrancherEnPriorite: true,
  },
};

/**
 * Typage des justificatifs attendus, par numero de ligne du parcours.
 *
 * Le parcours decrit les justificatifs en texte libre ; le cochage d'une
 * demarche a besoin d'un `document_type` contraint pour que le controle serveur
 * soit mecanisable. Le libelle reste celui du parcours, VERBATIM — seul le type
 * est ajoute ici.
 *
 *  . `types` : plusieurs valeurs = ALTERNATIVES (l'une d'elles suffit), ce qui
 *    traduit fidelement « contrat de bail OU de domiciliation » sans forcer un
 *    choix que le parcours ne fait pas.
 *  . plusieurs entrees = justificatifs CUMULATIFS (tous exiges).
 *  . `procedural: true` : le « justificatif » n'est pas une piece a televerser
 *    (« Ticket cloture ») — aucune piece n'est exigee au cochage.
 *
 * Types REUTILISES du catalogue existant (dataroom V24 / V30) partout ou c'est
 * possible ; les cinq types NOUVEAUX sont ajoutes par la migration dataroom V31
 * et signales ci-dessous par un commentaire.
 */
const TYPAGE_JUSTIFICATIFS = {
  1:  [{ types: ['FICHE_RENSEIGNEMENTS'] }],
  2:  [{ types: ['CONTRAT_BAIL', 'CONTRAT_DOMICILIATION', 'TITRE_PROPRIETE'] },
       { types: ['ATTESTATION_ENREGISTREMENT'] }],
  3:  [{ types: ['STATUTS'] }, { types: ['ATTESTATION_ENREGISTREMENT'] }],
  4:  [{ types: ['ETAT_ACTES_FORMATION'] }],
  5:  [{ types: ['ACTE_NOMINATION'] }, { types: ['ATTESTATION_ENREGISTREMENT'] }],
  6:  [{ types: ['ATTESTATION_BLOCAGE_CAPITAL'] }],
  7:  [{ types: ['POUVOIR'] }],
  8:  [{ types: ['TP'] }],
  9:  [{ types: ['RC'] }],
  10: [{ types: ['BULLETIN_IF'] }],
  11: [{ types: ['JOURNAL_ANNONCE'] }, { types: ['PUBLICATION_BO'] }],
  12: [{ types: ['VALIDATION_CLIENT'] }],
  13: [{ types: ['STATUTS'] }],
  // Ligne 14 : « Actes legalises » couvre les statuts, l'acte de nomination et le
  // pouvoir — dont deux sont conditionnels. Un seul groupe d'ALTERNATIVES, donc :
  // exiger les trois bloquerait les dossiers a gerance statutaire.
  14: [{ types: ['STATUTS', 'ACTE_NOMINATION', 'POUVOIR'] }],
  15: [{ types: ['RECEPISSE_DEPOT'] }],                     // nouveau type (V31)
  16: [{ types: ['CONTRAT_BAIL', 'CONTRAT_DOMICILIATION'] },
       { types: ['ATTESTATION_ENREGISTREMENT'] }],
  17: [{ types: ['AVIS_VERSEMENT_BANQUE'] }],               // nouveau type (V31)
  18: [{ types: ['ATTESTATION_BLOCAGE_CAPITAL'] }],
  19: [{ types: ['RECEPISSE_DEPOT'] }],
  20: [{ types: ['STATUTS'] }, { types: ['ATTESTATION_ENREGISTREMENT'] }],
  21: [{ types: ['RECEPISSE_DEPOT'] }],
  22: [{ types: ['ACTE_NOMINATION'] }, { types: ['ATTESTATION_ENREGISTREMENT'] }],
  23: [{ types: ['RECEPISSE_DEPOT'] }],
  24: [{ types: ['TP'] }],
  25: [{ types: ['RECEPISSE_DEPOT'] }],
  26: [{ types: ['BULLETIN_IF'] }],
  27: [{ types: ['RECEPISSE_DEPOT'] }],
  28: [{ types: ['RC'] }],
  29: [{ types: ['ICE'] }],
  30: [{ types: ['JOURNAL_ANNONCE'] }],
  31: [{ types: ['PUBLICATION_BO'] }],
  32: [{ types: ['RECEPISSE_DEPOT'] }],
  33: [{ types: ['CNSS'] }],
  34: [{ types: ['ACCUSE_RBE'] }],
  35: [{ types: ['RIB'] }],
  36: [{ types: ['RECEPISSE_DEPOT'] }],
  37: [{ types: ['LIVRES_LEGAUX'] }],
  38: [{ types: ['INVESTISSEMENT_ETRANGER'] }],             // nouveau type (V31)
  39: [{ types: ['RECEPISSE_DEPOT'] }],
  40: [{ types: ['RECEPISSE_CNDP'] }],
  41: [{ types: ['RECEPISSE_DEPOT'] }],
  42: [{ types: ['AUTORISATION_SECTORIELLE'] }],
  43: [{ types: ['RECEPISSE_DEPOT'] }],
  44: [{ types: ['IDENTIFIANTS_SIMPL'] }],
  45: [{ types: ['NOTE_CONFORMITE'] }],
  46: [{ types: ['BORDEREAU_REMISE'] }],
  // Ligne 47 : la colonne « Justificatif » enumere les VINGT-DEUX pieces a
  // archiver. Ce n'est pas une piece de plus a televerser : c'est le
  // recapitulatif de cloture qui les verifie, une par une (phase 5).
  47: [{ procedural: true }],
  48: [{ procedural: true }],
  49: [{ types: ['NOTE_ANNULATION'] }],                     // nouveau type (V31)
  50: [{ types: ['ACCUSE_RETRAIT_DEPOT'] }],                // nouveau type (V31)
  51: [{ types: ['BORDEREAU_REMISE'] }],
};

/**
 * CORRESPONDANCE 36 -> 51, pour la migration des tickets existants.
 *
 * Clef = `ordre` de l'ancien referentiel (V20, guide du 4 septembre).
 * Valeur = `ordre` de la ligne d'accueil au nouveau parcours, ou `null` quand il
 * n'y en a pas — et alors `motif` dit pourquoi, en toutes lettres, parce que
 * c'est ce qui sera rapporte au cabinet.
 *
 * Quand une ancienne ligne s'est SCINDEE en depot + retrait, l'accueil est la
 * ligne de DEPOT : c'est elle qui porte le geste deja accompli.
 */
const CORRESPONDANCE = {
  1:  { vers: 1,  note: 'Ouverture du ticket — inchangee' },
  2:  { vers: null, motif: "Le controle d'identite et de capacite des associes n'est plus une "
        + 'etape : il est devenu un CONTROLE BLOQUANT au lancement de la generation des statuts '
        + '(§ 18 du dictionnaire).' },
  3:  { vers: 1,  note: 'Choix de la forme sociale — absorbe par la collecte (ligne 1)' },
  4:  { vers: null, motif: "L'obtention du certificat negatif n'est plus une etape : elle est "
        + 'devenue un CONTROLE BLOQUANT au lancement de la generation des statuts (§ 18).' },
  5:  { vers: 2,  note: 'Securisation du siege -> contrat de bail ou de domiciliation' },
  6:  { vers: null, motif: 'La legalisation des signatures du contrat de siege ne figure plus au '
        + 'parcours : la ligne 14 ne couvre que les statuts, l\'acte de nomination et le pouvoir.' },
  7:  { vers: 15, aussi: [16], note: "Enregistrement du contrat de siege -> depot (ligne 15) puis retrait (16)" },
  8:  { vers: 3,  note: 'Etablissement des statuts' },
  9:  { vers: 5,  note: 'Acte de nomination du gerant' },
  10: { vers: null, motif: "L'evaluation des apports en nature n'est plus une etape : le rapport "
        + 'du commissaire aux apports est devenu un CONTROLE BLOQUANT (§ 18).' },
  11: { vers: 4,  note: 'Etat des actes accomplis pour le compte de la societe en formation' },
  12: { vers: 7,  note: 'Pouvoir pour les formalites' },
  13: { vers: 13, note: 'Signature des statuts' },
  14: { vers: 14, note: 'Legalisation des signatures — elargie a l\'acte de nomination et au pouvoir' },
  15: { vers: 14, note: 'Signature et legalisation de l\'acte de nomination — FUSIONNEE dans la ligne 14' },
  16: { vers: 17, aussi: [18], note: 'Depot du capital -> versement (ligne 17) puis retrait de l\'attestation (18)' },
  17: { vers: 19, aussi: [20], note: 'Enregistrement des statuts -> depot (19) puis retrait (20)' },
  18: { vers: 21, aussi: [22], note: 'Enregistrement de l\'acte de nomination -> depot (21) puis retrait (22)' },
  19: { vers: 23, aussi: [8, 24], note: 'Taxe professionnelle -> depot (23) puis retrait (24) ; la generation est ligne 8' },
  20: { vers: 25, aussi: [10, 26], note: "Declaration d'existence -> depot (25) puis retrait (26) ; generation ligne 10" },
  21: { vers: 27, aussi: [9, 28], note: 'Immatriculation -> depot legal (27) puis retrait du modele J (28) ; generation ligne 9' },
  22: { vers: 29, note: "Obtention de l'ICE" },
  23: { vers: 11, note: "Redaction de l'avis de constitution -> generation (ligne 11)" },
  24: { vers: 30, note: 'Publication au journal d\'annonces legales' },
  25: { vers: 31, note: 'Publication au Bulletin officiel' },
  26: { vers: 32, aussi: [33], note: 'CNSS / DAMANCOM -> depot (32) puis retrait (33)' },
  27: { vers: 34, note: 'Declaration des beneficiaires effectifs' },
  28: { vers: 35, note: 'Compte definitif et deblocage du capital' },
  29: { vers: 36, aussi: [37], note: 'Livres legaux -> depot (36) puis retrait (37)' },
  30: { vers: 41, aussi: [42], note: 'Agrements sectoriels -> depot (41) puis retrait (42)' },
  31: { vers: 43, aussi: [44], note: 'Adhesion SIMPL -> depot (43) puis retrait (44)' },
  32: { vers: 39, aussi: [40], note: 'Declaration CNDP -> depot (39) puis retrait (40)' },
  33: { vers: 45, note: 'Controle des mentions legales' },
  34: { vers: 12, aussi: [47], note: 'Controle de completude -> ligne 12 ; l archivage des pieces definitives devient la ligne 47' },
  35: { vers: 46, note: 'Remise des originaux contre bordereau — passe au DEROULEMENT, ligne 46' },
  36: { vers: 48, note: 'Cloture du ticket et alimentation de la fiche societe' },
};

/** Une cellule ne portant qu'un tiret ou une mention « procedure interne » ne dit rien. */
const VIDE = /^(|—|-|–|n\/a|na|—\s*\(procédure interne\))$/i;
const estVide = (v) => VIDE.test((v ?? '').trim());

const sqlTexte = (v) => (estVide(v) ? 'NULL' : "'" + String(v).trim().replace(/'/g, "''") + "'");
const sqlBrut = (v) => (v == null ? 'NULL' : "'" + String(v).replace(/'/g, "''") + "'");

/** « ☐ Signature des statuts… » -> « Signature des statuts… ». La case est portee par l'UI. */
const nettoyerLibelle = (v) => String(v ?? '').replace(/^[\u2610\u2611\u2612]\s*/, '').trim();

async function main() {
  const wb = await readWorkbook(CLASSEUR);
  const parcours = Object.values(wb)[0];
  if (!parcours) throw new Error('Feuille du parcours introuvable dans le classeur.');

  // -- 1. Lignes -----------------------------------------------------------
  //
  // Le statut est LU dans la colonne B de chaque ligne numerotee, jamais deduit
  // d'une plage : le parcours du 9 septembre le porte ligne a ligne. Les
  // lignes-bandeaux (« GÉNÉRATION DES DOCUMENTS ») n'ont pas de numero et sont
  // ignorees — on verifie neanmoins qu'elles annoncent bien un statut connu,
  // sans quoi une section entiere pourrait passer inapercue.
  const anomalies = [];
  const lignes = [];
  const bandeauxVus = [];

  for (const r of parcours) {
    const num = (r?.[COL.numero] ?? '').trim();
    if (!/^\d+$/.test(num)) {
      const t = num.replace(/\s+/g, ' ').trim();
      if (t && t === t.toUpperCase() && t.length > 8 && !/^PARCOURS|^Ordre/i.test(t)) {
        bandeauxVus.push(t);
      }
      continue;
    }
    const n = Number(num);

    const libelleStatut = (r[COL.statut] ?? '').trim();
    const statut = CODE_STATUT[libelleStatut];
    if (!statut) {
      throw new Error('Ligne ' + n + ' : statut inconnu « ' + libelleStatut + ' ». '
        + 'Les cinq statuts attendus sont : ' + Object.keys(CODE_STATUT).join(', '));
    }
    const phase = PHASE_DU_STATUT[statut];

    // `obligatoire` DERIVE de la condition d'application. La condition reste
    // stockee verbatim : la derivation ne remplace pas le texte, elle l'indexe.
    const condition = (r[COL.condition] ?? '').trim();
    const conditionBasse = condition.toLowerCase();
    const oc = CONDITIONS_OBLIGATOIRES.some((c) => conditionBasse.startsWith(c)) ? 'O' : 'C';
    if (oc === 'C' && estVide(condition)) {
      anomalies.push({ cat: 'DECISION', etape: n, champ: "condition d'application",
        detail: 'ligne conditionnelle sans condition ecrite' });
    }
    anomalies.push({ cat: 'DERIVATION', etape: n, champ: 'obligatoire',
      detail: "« " + condition + " » -> '" + oc + "'" });

    // Cellules structurellement vides : comptees, jamais listees.
    for (const [cle, libelle] of [
      ['delai', 'Delai'], ['variables', 'Donnees alimentees'],
      ['documentProduit', 'Document produit'], ['modele', 'Modele JURIKA'],
      ['justificatifs', 'Justificatif a obtenir et archiver'],
    ]) {
      if (estVide(r[COL[cle]])) {
        anomalies.push({ cat: 'SANS_OBJET', etape: n, champ: libelle, detail: 'cellule vide' });
      }
    }

    if (/à confirmer|à valider|à arbitrer/i.test([r[COL.delai], r[COL.condition]].join(' '))) {
      anomalies.push({ cat: 'DECISION', etape: n, champ: 'delai / condition',
        detail: 'le parcours porte lui-meme une mention « a confirmer / a valider » : '
          + [r[COL.delai], r[COL.condition]].filter((x) => /à confirmer|à valider/i.test(x ?? ''))
              .map((x) => '« ' + String(x).trim() + ' »').join(' ; ') });
    }

    const typage = TYPAGE_JUSTIFICATIFS[n];
    if (!typage) {
      anomalies.push({ cat: 'TECHNIQUE', etape: n, champ: 'typage justificatif',
        detail: 'aucun typage defini pour cette ligne' });
    }

    const delaiRegle = DELAIS[n];
    if (delaiRegle) {
      anomalies.push({ cat: 'DELAI', etape: n, champ: 'point de depart du delai',
        delai: delaiRegle, libelle: nettoyerLibelle(r[COL.libelle]) });
    } else if (/\d+\s*(jour|mois)|dans le mois/i.test(r[COL.delai] ?? '')) {
      anomalies.push({ cat: 'DECISION', etape: n, champ: 'delai non calculable',
        detail: 'delai chiffre mais point de depart non mecanisable : « '
          + (r[COL.delai] ?? '').trim() + ' » — AUCUNE alerte ne sera levee sur cette ligne' });
    }

    const [formaliteCode, formaliteVolet] = FORMALITES[n] ?? [null, null];

    lignes.push({ n, phase, statut, oc, ligne: r, condition,
      typage: typage ?? [], delaiRegle, formaliteCode, formaliteVolet });
  }

  // -- 1 bis. Controles de coherence, avant d'ecrire quoi que ce soit -------

  if (lignes.length !== 51) {
    throw new Error('Le parcours du 9 septembre compte 51 lignes ; ' + lignes.length + ' lues.');
  }
  const cinqStatuts = new Set(lignes.map((e) => e.statut));
  if (cinqStatuts.size !== 5) {
    throw new Error('Cinq statuts attendus, ' + cinqStatuts.size + ' rencontres : '
      + [...cinqStatuts].join(', '));
  }
  if (bandeauxVus.length !== 5) {
    anomalies.push({ cat: 'TECHNIQUE', etape: null, champ: 'bandeaux de statut',
      detail: bandeauxVus.length + ' bandeaux lus au lieu de 5 : ' + bandeauxVus.join(' / ') });
  }

  // Depot et retrait : chaque code de formalite porte EXACTEMENT un depot et un
  // retrait, et le retrait suit le depot. Un couple incomplet rendrait le lien
  // muet — on echoue ici plutot que de livrer une ligne « retrait » orpheline.
  {
    const parCode = new Map();
    for (const e of lignes) {
      if (!e.formaliteCode) continue;
      if (!parCode.has(e.formaliteCode)) parCode.set(e.formaliteCode, {});
      parCode.get(e.formaliteCode)[e.formaliteVolet] = e.n;
    }
    for (const [code, volets] of parCode) {
      if (volets.DEPOT == null || volets.RETRAIT == null) {
        throw new Error('Formalite ' + code + ' : couple depot/retrait incomplet ('
          + JSON.stringify(volets) + ')');
      }
      if (volets.RETRAIT <= volets.DEPOT) {
        throw new Error('Formalite ' + code + ' : le retrait (ligne ' + volets.RETRAIT
          + ') precede le depot (ligne ' + volets.DEPOT + ')');
      }
    }
    if (parCode.size !== 12) {
      throw new Error('Douze formalites scindees attendues, ' + parCode.size + ' declarees.');
    }
  }

  // Le graphe des points de depart doit etre ACYCLIQUE : une echeance se calcule
  // depuis la date de cochage d'une autre ligne, qui peut elle-meme en
  // referencer une troisieme (25 -> 28 -> 27 -> 13). Un cycle rendrait le delai
  // incalculable et, pire, ne se verrait pas.
  for (const depart of Object.keys(DELAIS).map(Number)) {
    const vus = [depart];
    let courant = DELAIS[depart].reference;
    while (courant != null && DELAIS[courant]) {
      if (vus.includes(courant)) {
        throw new Error('Reference circulaire dans les points de depart : '
          + [...vus, courant].join(' -> '));
      }
      vus.push(courant);
      courant = DELAIS[courant].reference;
    }
    if (courant != null && !lignes.some((e) => e.n === courant)) {
      throw new Error('Ligne ' + depart + ' : point de depart ' + courant + ' absent du parcours');
    }
  }

  // -- 2. SQL --------------------------------------------------------------
  const l = [];
  l.push('-- =====================================================================');
  l.push('-- JURIKA V24 — Referentiel du parcours de CREATION, version du 9 septembre');
  l.push('--');
  l.push('-- FICHIER GENERE. Ne pas editer a la main : regenerer avec');
  l.push('--   node scripts/lot1/derive-referentiel-demarches.mjs');
  l.push('-- Source : specs/creation-2026-09-09/1. Parcours creation.xlsx');
  l.push('--          une feuille, 9 colonnes, ' + lignes.length + ' lignes, 5 statuts.');
  l.push('--');
  l.push('-- CE QUE CETTE MIGRATION REMPLACE');
  l.push('-- V20 avait charge 36 etapes derivees du guide du 4 septembre. Le parcours');
  l.push('-- definitif du cabinet en compte 51. V20 N EST PAS EDITEE — une migration deja');
  l.push('-- appliquee ne doit jamais l etre (`validate-on-migrate: false` rendrait la');
  l.push('-- modification MUETTE sur toute base existante ; cf. V18 et V22). Le referentiel');
  l.push('-- est une donnee de reference : on le remplace integralement.');
  l.push('--');
  l.push('-- TROIS CHANGEMENTS DE STRUCTURE');
  l.push('--  1. `formalite_code` / `formalite_volet` : toute formalite comportant un depot');
  l.push('--     puis un retrait figure sur DEUX lignes, et les deux doivent se retrouver.');
  l.push('--     C est la date du DEPOT qui fait courir l attente du RETRAIT.');
  l.push('--  2. `actif` : une ligne du referentiel peut etre RETIREE du parcours sans etre');
  l.push('--     supprimee. C est ce qui permet a une demarche cochee sur une ligne');
  l.push('--     disparue de conserver son etat, son horodatage et ses justificatifs.');
  l.push('--  3. `phase_code` / `phase_libelle` portent desormais le STATUT (S1..S5) : le');
  l.push('--     parcours du 9 septembre n a plus de colonne « Phase », il regroupe par');
  l.push('--     statut. L API regroupe par code de phase — on fait coincider les deux');
  l.push('--     plutot que d inventer des phases que le cabinet n emploie pas.');
  l.push('--');
  l.push('-- Les colonnes que le nouveau classeur ne porte plus (acteur, organisme, pieces');
  l.push('-- entrantes, cout indicatif) restent NULL. Rien n est complete au juge.');
  l.push('-- =====================================================================');
  l.push('');
  l.push('-- ---------------------------------------------------------------------');
  l.push('-- 1. Colonnes nouvelles');
  l.push('-- ---------------------------------------------------------------------');
  l.push('ALTER TABLE demarches_referentiel');
  l.push('    ADD COLUMN IF NOT EXISTS formalite_code  VARCHAR(40),');
  l.push('    ADD COLUMN IF NOT EXISTS formalite_volet VARCHAR(10),');
  l.push('    ADD COLUMN IF NOT EXISTS actif           BOOLEAN NOT NULL DEFAULT TRUE;');
  l.push('');
  l.push('ALTER TABLE demarches_referentiel DROP CONSTRAINT IF EXISTS chk_formalite_volet;');
  l.push('ALTER TABLE demarches_referentiel');
  l.push('    ADD CONSTRAINT chk_formalite_volet CHECK (');
  l.push('        (formalite_code IS NULL     AND formalite_volet IS NULL)');
  l.push("     OR (formalite_code IS NOT NULL AND formalite_volet IN ('DEPOT','RETRAIT')));");
  l.push('');
  l.push('-- Un seul depot et un seul retrait par formalite et par workflow : sans cette');
  l.push('-- unicite, « la ligne de depot de ma formalite » ne serait pas une notion.');
  l.push('-- Les lignes retirees (actif = FALSE) en sont exclues : une archive ne doit pas');
  l.push('-- empecher le rechargement du referentiel.');
  l.push('DROP INDEX IF EXISTS uq_demarches_formalite_volet;');
  l.push('CREATE UNIQUE INDEX uq_demarches_formalite_volet');
  l.push('    ON demarches_referentiel (workflow_type, formalite_code, formalite_volet)');
  l.push('    WHERE formalite_code IS NOT NULL AND actif;');
  l.push('');
  l.push('COMMENT ON COLUMN demarches_referentiel.formalite_code IS');
  l.push("    'Formalite commune aux deux lignes depot / retrait. NULL pour une ligne unique.';");
  l.push('COMMENT ON COLUMN demarches_referentiel.formalite_volet IS');
  l.push("    'DEPOT ou RETRAIT. La date de cochage du DEPOT fait courir l''attente du RETRAIT.';");
  l.push('COMMENT ON COLUMN demarches_referentiel.actif IS');
  l.push("    'FALSE = ligne retiree du parcours, conservee pour les cochages qui la referencent.';");
  l.push('');
  l.push('-- ---------------------------------------------------------------------');
  l.push('-- 2. Ce qui ne se rattache pas survit et se rapporte');
  l.push('-- ---------------------------------------------------------------------');
  l.push('CREATE TABLE IF NOT EXISTS demarches_migration_orphelines (');
  l.push('    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),');
  l.push('    migre_le           TIMESTAMPTZ NOT NULL DEFAULT NOW(),');
  l.push('    ticket_demarche_id UUID        NOT NULL,');
  l.push('    ticket_id          UUID        NOT NULL,');
  l.push('    ancien_ordre       SMALLINT    NOT NULL,');
  l.push('    ancien_libelle     TEXT        NOT NULL,');
  l.push('    etat               VARCHAR(20) NOT NULL,');
  l.push('    justificatifs      SMALLINT    NOT NULL,');
  l.push('    motif              TEXT        NOT NULL');
  l.push(');');
  l.push('COMMENT ON TABLE demarches_migration_orphelines IS');
  l.push("    'Demarches cochees qu''aucune ligne du parcours du 9 septembre n''accueille. "
    + "Leur ligne ticket_demarches est CONSERVEE, rattachee a une ligne de referentiel "
    + "retiree (actif = FALSE) : etat, horodatage, acteur et justificatifs intacts.';");
  l.push('');
  l.push('-- ---------------------------------------------------------------------');
  l.push('-- 3. Archivage — une ligne retiree par ancienne ligne REFERENCEE');
  l.push('--');
  l.push('-- Pourquoi UNE PAR ANCIENNE LIGNE, et non une seule ligne-tampon : un ticket');
  l.push('-- porte plusieurs demarches, et `ticket_demarches` est UNIQUE (ticket_id,');
  l.push('-- demarche_id). Renvoyer trois demarches d un meme ticket sur le meme tampon');
  l.push('-- violerait cette unicite. La base en compte deja un cas : un ticket coche sur');
  l.push('-- les anciennes lignes 4, 5 et 6, dont deux sont sans equivalent.');
  l.push('--');
  l.push('-- Ordre de l archive = 9000 + ancien ordre. Les lignes du parcours vont de 1 a');
  l.push('-- 51 : aucun chevauchement possible.');
  l.push('-- ---------------------------------------------------------------------');
  l.push('INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,');
  l.push('        libelle, statut_ticket, obligatoire, condition_application,');
  l.push('        justificatifs_texte, actif)');
  l.push('SELECT dr.workflow_type, (9000 + dr.ordre)::SMALLINT, \'S0\',');
  l.push("       'Referentiel retire (V20)', dr.libelle, dr.statut_ticket, dr.obligatoire,");
  l.push('       dr.condition_application, dr.justificatifs_texte, FALSE');
  l.push('  FROM demarches_referentiel dr');
  l.push(" WHERE dr.workflow_type = 'CREATION' AND dr.ordre < 9000");
  l.push('   AND EXISTS (SELECT 1 FROM ticket_demarches td WHERE td.demarche_id = dr.id)');
  l.push('ON CONFLICT (workflow_type, ordre) DO NOTHING;');
  l.push('');
  l.push('UPDATE ticket_demarches td');
  l.push('   SET demarche_id = a.id');
  l.push('  FROM demarches_referentiel dr');
  l.push('  JOIN demarches_referentiel a');
  l.push("    ON a.workflow_type = 'CREATION' AND a.ordre = 9000 + dr.ordre");
  l.push(' WHERE td.demarche_id = dr.id');
  l.push("   AND dr.workflow_type = 'CREATION' AND dr.ordre < 9000;");
  l.push('');
  l.push('-- ---------------------------------------------------------------------');
  l.push('-- 4. Rechargement du referentiel');
  l.push('-- ---------------------------------------------------------------------');
  l.push('DELETE FROM demarches_justificatifs WHERE demarche_id IN');
  l.push("    (SELECT id FROM demarches_referentiel");
  l.push("      WHERE workflow_type = 'CREATION' AND ordre < 9000);");
  l.push("DELETE FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre < 9000;");
  l.push('');

  for (const e of lignes) {
    const r = e.ligne;
    const libelle = nettoyerLibelle(r[COL.libelle]);
    l.push('-- -- Ligne ' + e.n + ' — ' + libelle.replace(/\s+/g, ' ')
      + (e.formaliteCode ? '   [' + e.formaliteCode + ' / ' + e.formaliteVolet + ']' : ''));
    l.push('INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,');
    l.push('        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,');
    l.push('        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,');
    l.push('        delai, cout_indicatif, variables_alimentees,');
    l.push('        delai_valeur, delai_unite, delai_reference_ordre,');
    l.push('        formalite_code, formalite_volet, actif)');
    l.push("VALUES ('CREATION', " + e.n + ", '" + e.phase.code + "', " + sqlBrut(e.phase.libelle) + ',');
    l.push('        ' + sqlBrut(libelle) + ", '" + e.statut + "', NULL, NULL, '" + e.oc + "', "
      + sqlTexte(r[COL.condition]) + ',');
    l.push('        NULL, ' + sqlTexte(r[COL.documentProduit]) + ',');
    l.push('        ' + sqlTexte(r[COL.justificatifs]) + ', ' + sqlTexte(r[COL.modele]) + ',');
    l.push('        ' + sqlTexte(r[COL.delai]) + ', NULL, ' + sqlTexte(r[COL.variables]) + ', '
           + (e.delaiRegle ? e.delaiRegle.valeur : 'NULL') + ', '
           + (e.delaiRegle ? "'" + e.delaiRegle.unite + "'" : 'NULL') + ', '
           + (e.delaiRegle && e.delaiRegle.reference != null
              ? e.delaiRegle.reference : 'NULL') + ', '
           + sqlBrut(e.formaliteCode) + ', ' + sqlBrut(e.formaliteVolet) + ', TRUE);');
    if (e.delaiRegle) {
      l.push('--   delai « ' + e.delaiRegle.texte + ' » -> ' + e.delaiRegle.lecture);
      l.push('--   lecture alternative : ' + e.delaiRegle.alternative);
      if (e.delaiRegle.donnee) {
        l.push('--   point de depart = la DONNEE $' + e.delaiRegle.donnee + ', et non le cochage');
        l.push('--   d une autre ligne. La colonne `delai_reference_donnee` est posee par V26 :');
        l.push('--   elle n existe pas encore au moment ou cette migration s execute.');
      }
    }

    const texteJustif = (r[COL.justificatifs] ?? '').trim();
    e.typage.forEach((j, i) => {
      if (j.procedural) {
        l.push('--   justificatif procedural : aucune piece a televerser au cochage.');
        return;
      }
      for (const t of j.types) {
        l.push('INSERT INTO demarches_justificatifs (demarche_id, alternative_groupe, document_type, libelle)');
        l.push('SELECT id, ' + (i + 1) + ", '" + t + "', " + sqlTexte(texteJustif));
        l.push("  FROM demarches_referentiel WHERE workflow_type = 'CREATION' AND ordre = " + e.n + ';');
      }
    });
    l.push('');
  }

  l.push('-- ---------------------------------------------------------------------');
  l.push('-- 5. Rattachement des demarches deja cochees');
  l.push('--');
  l.push('-- La table ci-dessous est la correspondance 36 -> 51, etablie ligne par ligne');
  l.push('-- (voir output/lotB/correspondance-36-51.md). Quand une ancienne ligne s est');
  l.push('-- SCINDEE en depot puis retrait, la ligne d accueil est celle du DEPOT : c est');
  l.push('-- elle qui porte le geste deja accompli.');
  l.push('-- ---------------------------------------------------------------------');
  l.push('CREATE TEMPORARY TABLE tmp_corr (');
  l.push('    ancien  SMALLINT PRIMARY KEY,');
  l.push('    nouveau SMALLINT,');
  l.push('    motif   TEXT');
  l.push(') ON COMMIT DROP;');
  l.push('');
  l.push('INSERT INTO tmp_corr (ancien, nouveau, motif) VALUES');
  {
    const rows = Object.entries(CORRESPONDANCE).map(([ancien, c]) =>
      '    (' + ancien + ', ' + (c.vers == null ? 'NULL' : c.vers) + ', '
      + (c.vers == null ? sqlBrut(c.motif) : 'NULL') + ')');
    l.push(rows.join(',\n') + ';');
  }
  l.push('');
  l.push('-- Un ticket peut porter DEUX anciennes lignes qui pointent la meme nouvelle');
  l.push('-- (les anciennes 14 et 15 fusionnent dans la ligne 14). `ticket_demarches` etant');
  l.push('-- UNIQUE (ticket_id, demarche_id), une seule peut etre deplacee : on retient la');
  l.push('-- plus ancienne, et l autre reste archivee et rapportee. DISTINCT ON fige ce');
  l.push('-- choix AVANT l UPDATE — un NOT EXISTS dans l UPDATE serait evalue sur');
  l.push('-- l instantane d avant, et laisserait passer les deux.');
  l.push('CREATE TEMPORARY TABLE tmp_deplacement ON COMMIT DROP AS');
  l.push('SELECT DISTINCT ON (td.ticket_id, c.nouveau)');
  l.push('       td.id AS ticket_demarche_id, n.id AS nouvelle_demarche_id');
  l.push('  FROM ticket_demarches td');
  l.push('  JOIN demarches_referentiel a ON a.id = td.demarche_id');
  l.push("   AND a.workflow_type = 'CREATION' AND a.ordre > 9000");
  l.push('  JOIN tmp_corr c ON c.ancien = a.ordre - 9000 AND c.nouveau IS NOT NULL');
  l.push('  JOIN demarches_referentiel n');
  l.push("    ON n.workflow_type = 'CREATION' AND n.ordre = c.nouveau AND n.actif");
  l.push(' ORDER BY td.ticket_id, c.nouveau, a.ordre;');
  l.push('');
  l.push('UPDATE ticket_demarches td');
  l.push('   SET demarche_id = d.nouvelle_demarche_id');
  l.push('  FROM tmp_deplacement d');
  l.push(' WHERE td.id = d.ticket_demarche_id;');
  l.push('');
  l.push('-- Ce qui reste sur une ligne archivee n a pas trouve d accueil : soit l ancienne');
  l.push('-- ligne n a pas d equivalent, soit la nouvelle etait deja prise sur ce ticket.');
  l.push('-- On le RAPPORTE, on ne le supprime pas.');
  l.push('INSERT INTO demarches_migration_orphelines');
  l.push('       (ticket_demarche_id, ticket_id, ancien_ordre, ancien_libelle, etat,');
  l.push('        justificatifs, motif)');
  l.push('SELECT td.id, td.ticket_id, (a.ordre - 9000)::SMALLINT, a.libelle, td.etat,');
  l.push('       (SELECT count(*) FROM ticket_demarche_justificatifs j');
  l.push('         WHERE j.ticket_demarche_id = td.id),');
  l.push('       COALESCE(c.motif,');
  l.push("           'Fusionnee : la ligne ' || c.nouveau || ' du parcours du 9 septembre etait "
    + "deja occupee sur ce ticket par une autre ancienne ligne.')");
  l.push('  FROM ticket_demarches td');
  l.push('  JOIN demarches_referentiel a ON a.id = td.demarche_id');
  l.push("   AND a.workflow_type = 'CREATION' AND a.ordre > 9000");
  l.push('  LEFT JOIN tmp_corr c ON c.ancien = a.ordre - 9000;');
  l.push('');
  l.push('-- Les archives que plus personne ne reference disparaissent ; celles qui portent');
  l.push('-- encore un cochage restent, inactives.');
  l.push("DELETE FROM demarches_referentiel dr");
  l.push(" WHERE dr.workflow_type = 'CREATION' AND dr.ordre > 9000");
  l.push('   AND NOT EXISTS (SELECT 1 FROM ticket_demarches td WHERE td.demarche_id = dr.id);');
  l.push('');
  l.push('-- ---------------------------------------------------------------------');
  l.push('-- 6. Garde-fous');
  l.push('-- ---------------------------------------------------------------------');
  l.push('DO $$');
  l.push('DECLARE n INTEGER;');
  l.push('BEGIN');
  l.push("    SELECT count(*) INTO n FROM demarches_referentiel");
  l.push("     WHERE workflow_type = 'CREATION' AND actif;");
  l.push('    IF n <> ' + lignes.length + ' THEN');
  l.push("        RAISE EXCEPTION 'Migration V24 : % lignes actives au lieu de " + lignes.length + "', n;");
  l.push('    END IF;');
  l.push('');
  l.push('    SELECT count(*) INTO n FROM demarches_referentiel');
  l.push("     WHERE workflow_type = 'CREATION' AND actif AND formalite_code IS NOT NULL;");
  l.push('    IF n <> 24 THEN');
  l.push("        RAISE EXCEPTION 'Migration V24 : % lignes depot/retrait au lieu de 24', n;");
  l.push('    END IF;');
  l.push('');
  l.push('    -- Aucune demarche cochee ne doit avoir disparu.');
  l.push('    SELECT count(*) INTO n FROM ticket_demarches td');
  l.push('     WHERE NOT EXISTS (SELECT 1 FROM demarches_referentiel dr');
  l.push('                        WHERE dr.id = td.demarche_id);');
  l.push('    IF n > 0 THEN');
  l.push("        RAISE EXCEPTION 'Migration V24 : % cochage(s) orphelin(s) de referentiel', n;");
  l.push('    END IF;');
  l.push('END $$;');
  l.push('');

  fs.mkdirSync(path.dirname(SQL_SORTIE), { recursive: true });
  fs.writeFileSync(SQL_SORTIE, l.join('\n'), 'utf8');

  // -- 3. Rapports ---------------------------------------------------------
  const parLigne = new Map(lignes.map((e) => [e.n, e]));
  const decisions = anomalies.filter((x) => x.cat === 'DELAI' || x.cat === 'DECISION');
  const sansObjet = anomalies.filter((x) => x.cat === 'SANS_OBJET');
  const derivations = anomalies.filter((x) => x.cat === 'DERIVATION');
  const techniques = anomalies.filter((x) => x.cat === 'TECHNIQUE');

  const nbJustif = lignes.reduce((acc, e) =>
    acc + e.typage.filter((j) => !j.procedural).reduce((x, j) => x + j.types.length, 0), 0);

  const delaisTous = decisions.filter((x) => x.cat === 'DELAI');
  const delais = delaisTous.filter((x) => !x.delai.alternative.startsWith('aucune'));
  const prioritaires = delais.filter((x) => x.delai.aTrancherEnPriorite);
  const autresDelais = delais.filter((x) => !x.delai.aTrancherEnPriorite);
  const autresDecisions = decisions.filter((x) => x.cat === 'DECISION')
    .sort((x, y) => x.etape - y.etape);

  // -- 3a. Rapport destine au cabinet --
  const cab = [];
  cab.push('# Points a trancher — parcours de creation du 9 septembre 2026');
  cab.push('');
  cab.push('Genere depuis `specs/creation-2026-09-09/1. Parcours creation.xlsx`,');
  cab.push('par `scripts/lot1/derive-referentiel-demarches.mjs`.');
  cab.push('Rien n a ete complete au juge : ce que le parcours ne dit pas reste vide en base.');
  cab.push('');
  cab.push('| Section | Nature | Lignes |');
  cab.push('|---|---|---|');
  cab.push('| 1 | Points de depart des delais legaux | ' + delais.length + ' |');
  cab.push('| 2 | Delais que le produit n alertera pas | '
    + autresDecisions.filter((d) => d.champ === 'delai non calculable').length + ' |');
  cab.push('| 3 | Points que le parcours signale lui-meme | '
    + autresDecisions.filter((d) => d.champ !== 'delai non calculable').length + ' |');
  cab.push('| 4 | Sans objet (comptees, non listees) | ' + sansObjet.length + ' |');
  cab.push('');

  cab.push('## 1. Points de depart des delais legaux');
  cab.push('');
  cab.push('Le parcours donne les delais (« dans les 30 jours de l acte ») mais jamais la date');
  cab.push('a partir de laquelle ils courent. Chaque lecture ci-dessous applique une regle');
  cab.push('constante : **le jalon le plus precoce compatible avec le texte**. Une alerte');
  cab.push('prematuree est un inconfort ; une alerte tardive est une faute.');
  cab.push('');
  for (const d of [...prioritaires, ...autresDelais]) {
    const r = d.delai;
    cab.push('**Ligne ' + d.etape + ' — ' + d.libelle + '**'
      + (r.aTrancherEnPriorite ? '  ← **a trancher en priorite**' : ''));
    cab.push('');
    cab.push('| | |');
    cab.push('|---|---|');
    cab.push('| Texte du parcours | « ' + r.texte + ' » |');
    cab.push('| Duree retenue | ' + r.valeur + ' ' + (r.unite === 'MOIS' ? 'mois calendaires' : 'jours') + ' |');
    cab.push('| Lecture retenue | ' + r.lecture + ' |');
    cab.push('| Lecture alternative | ' + r.alternative + ' |');
    cab.push('| Consequence | ' + r.consequence + ' |');
    cab.push('');
  }
  const sansAlternative = delaisTous.filter((x) => x.delai.alternative.startsWith('aucune'));
  if (sansAlternative.length) {
    cab.push('Les lignes ' + sansAlternative.map((x) => x.etape).join(', ')
      + ' portent aussi un delai calcule, mais leur point de depart ne souffre');
    cab.push('aucune autre lecture : rien a trancher.');
    cab.push('');
  }

  const nonCalculables = autresDecisions.filter((d) => d.champ === 'delai non calculable');
  if (nonCalculables.length) {
    cab.push('## 2. Delais que le produit n alertera pas');
    cab.push('');
    cab.push('Ces lignes portent un delai chiffre, mais leur point de depart n existe pas au');
    cab.push('parcours : aucune ligne cochable ne le donne. **Aucune echeance n est fabriquee** —');
    cab.push('l interface affiche le texte du delai et dit qu elle ne sait pas le calculer.');
    cab.push('Pour qu une alerte existe, il faut que le cabinet dise quelle date la declenche.');
    cab.push('');
    cab.push('| Ligne | Delai du parcours |');
    cab.push('|---|---|');
    for (const d of nonCalculables) cab.push('| ' + d.etape + ' | ' + d.detail + ' |');
    cab.push('');
  }

  const signales = autresDecisions.filter((d) => d.champ !== 'delai non calculable');
  if (signales.length) {
    cab.push('## 3. Points que le parcours signale lui-meme comme incertains');
    cab.push('');
    cab.push('| Ligne | Point |');
    cab.push('|---|---|');
    for (const d of signales) cab.push('| ' + d.etape + ' | ' + d.detail + ' |');
    cab.push('');
  }

  const etapesSansObjet = new Set(sansObjet.map((x) => x.etape));
  cab.push('## 4. Sans objet');
  cab.push('');
  cab.push('**' + sansObjet.length + ' cellules vides** reparties sur ' + etapesSansObjet.size + ' lignes :');
  cab.push('des lignes de suivi sans document produit, sans modele ou sans variable alimentee.');
  cab.push('Rien a decider ; elles sont NULL en base et le restent. Le detail figure dans le');
  cab.push('rapport technique.');
  cab.push('');
  fs.mkdirSync(path.dirname(RAPPORT_CABINET), { recursive: true });
  fs.writeFileSync(RAPPORT_CABINET, cab.join('\n'), 'utf8');

  // -- 3b. Rapport technique --
  const parEtape = new Map();
  for (const x of anomalies) {
    if (!parEtape.has(x.etape)) parEtape.set(x.etape, []);
    parEtape.get(x.etape).push(x);
  }
  const tec = [];
  tec.push('# Referentiel du parcours de creation — trace technique complete');
  tec.push('');
  tec.push('Genere par `scripts/lot1/derive-referentiel-demarches.mjs`.');
  tec.push('Ce fichier n est PAS destine au cabinet : voir `rapport-cabinet.md`.');
  tec.push('');
  tec.push('- Lignes chargees : **' + lignes.length + '**');
  tec.push('- Statuts : **' + cinqStatuts.size + '** (' + [...cinqStatuts].join(', ') + ')');
  tec.push('- Formalites scindees depot / retrait : **12** (24 lignes)');
  tec.push('- Justificatifs types : **' + nbJustif + '**');
  tec.push('- Delais calculables : **' + delaisTous.length + '** dont **' + delais.length
    + '** avec une lecture alternative');
  tec.push('- Constats : ' + decisions.length + ' decisions, ' + derivations.length
    + ' derivations, ' + sansObjet.length + ' sans objet, ' + techniques.length + ' techniques');
  tec.push('');
  tec.push('## Repartition par statut');
  tec.push('');
  tec.push('| Statut | Phase derivee | Lignes |');
  tec.push('|---|---|---|');
  for (const [statut, phase] of Object.entries(PHASE_DU_STATUT)) {
    const n = lignes.filter((e) => e.statut === statut).length;
    tec.push('| ' + statut + ' | ' + phase.code + ' — ' + phase.libelle + ' | ' + n + ' |');
  }
  tec.push('');
  tec.push('## Formalites scindees');
  tec.push('');
  tec.push('| Formalite | Depot | Retrait |');
  tec.push('|---|---|---|');
  {
    const parCode = new Map();
    for (const e of lignes) {
      if (!e.formaliteCode) continue;
      if (!parCode.has(e.formaliteCode)) parCode.set(e.formaliteCode, {});
      parCode.get(e.formaliteCode)[e.formaliteVolet] = e.n;
    }
    for (const [code, v] of parCode) {
      tec.push('| ' + code + ' | ligne ' + v.DEPOT + ' | ligne ' + v.RETRAIT + ' |');
    }
  }
  tec.push('');
  tec.push('## Constats, ligne par ligne');
  tec.push('');
  tec.push('| Ligne | Categorie | Champ | Constat |');
  tec.push('|---|---|---|---|');
  for (const n of [...parEtape.keys()].sort((x, y) => (x ?? 0) - (y ?? 0))) {
    for (const x of parEtape.get(n)) {
      const detail = x.cat === 'DELAI'
        ? '« ' + x.delai.texte + ' » -> ' + x.delai.lecture
          + ' (' + x.delai.valeur + ' ' + x.delai.unite + ', ref. ligne ' + x.delai.reference + ')'
        : x.detail;
      tec.push('| ' + (n ?? '—') + ' | ' + x.cat + ' | ' + x.champ + ' | ' + detail + ' |');
    }
  }
  tec.push('');
  fs.writeFileSync(RAPPORT_TECHNIQUE, tec.join('\n'), 'utf8');

  // -- 3c. Correspondance 36 -> 51 --
  const cor = [];
  cor.push('# Correspondance 36 lignes -> 51 lignes');
  cor.push('');
  cor.push('Genere par `scripts/lot1/derive-referentiel-demarches.mjs`. C est la table');
  cor.push('qu applique la migration V24 pour rattacher les demarches deja cochees.');
  cor.push('');
  const reprises = Object.values(CORRESPONDANCE).filter((c) => c.vers != null).length;
  const perdues = Object.values(CORRESPONDANCE).filter((c) => c.vers == null).length;
  const accueils = new Set(Object.values(CORRESPONDANCE).map((c) => c.vers).filter((v) => v != null));
  // Une ancienne ligne peut engendrer PLUSIEURS lignes du 9 septembre : la
  // scission depot / retrait, et la separation entre la GENERATION du document
  // et son DEPOT aupres de l administration. Seule la ligne d ACCUEIL sert a la
  // migration, mais le rapport doit dire la verite : ces lignes derivees ne sont
  // pas « nouvelles ».
  const derivees = new Map();
  for (const [ancien, c] of Object.entries(CORRESPONDANCE)) {
    for (const n of c.aussi ?? []) derivees.set(n, Number(ancien));
  }
  const nouvelles = lignes.filter((e) => !accueils.has(e.n) && !derivees.has(e.n));
  cor.push('| | Nombre |');
  cor.push('|---|---:|');
  cor.push('| Anciennes lignes | **' + Object.keys(CORRESPONDANCE).length + '** |');
  cor.push('| dont rattachees | **' + reprises + '** |');
  cor.push('| dont sans equivalent | **' + perdues + '** |');
  cor.push('| Nouvelles lignes | **' + lignes.length + '** |');
  cor.push('| dont lignes d accueil | **' + accueils.size + '** |');
  cor.push('| dont derivees par scission | **' + derivees.size + '** |');
  cor.push('| dont sans antecedent | **' + nouvelles.length + '** |');
  cor.push('');
  cor.push('## Ancienne ligne -> nouvelle');
  cor.push('');
  cor.push('| Ancien | Nouveau | Ce qui se passe |');
  cor.push('|---:|---:|---|');
  for (const [ancien, c] of Object.entries(CORRESPONDANCE)) {
    const cible = c.vers == null ? '**—**'
      : String(c.vers) + (c.aussi?.length
          ? ' (+ ' + [...c.aussi].sort((a, b) => a - b).join(', ') + ')' : '');
    cor.push('| ' + ancien + ' | ' + cible + ' | ' + (c.vers == null ? c.motif : c.note) + ' |');
  }
  cor.push('');
  cor.push('La colonne « Nouveau » porte d abord la ligne d **accueil** — celle sur laquelle');
  cor.push('la migration repointe une demarche deja cochee — puis, entre parentheses, les');
  cor.push('autres lignes du 9 septembre issues de la meme ancienne ligne : scission depot /');
  cor.push('retrait, ou separation entre la generation du document et son depot.');
  cor.push('');
  cor.push('## Lignes derivees d une ancienne par scission');
  cor.push('');
  cor.push('| Ligne | Issue de | Libelle |');
  cor.push('|---:|---:|---|');
  for (const [n, ancien] of [...derivees.entries()].sort((a, b) => a[0] - b[0])) {
    cor.push('| ' + n + ' | ' + ancien + ' | '
      + nettoyerLibelle(parLigne.get(n).ligne[COL.libelle]) + ' |');
  }
  cor.push('');
  cor.push('## Lignes du 9 septembre sans antecedent');
  cor.push('');
  cor.push('| Ligne | Statut | Libelle |');
  cor.push('|---:|---|---|');
  for (const e of nouvelles) {
    cor.push('| ' + e.n + ' | ' + e.statut + ' | ' + nettoyerLibelle(e.ligne[COL.libelle]) + ' |');
  }
  cor.push('');
  fs.writeFileSync(RAPPORT_CORRESPONDANCE, cor.join('\n'), 'utf8');

  console.log(lignes.length + ' lignes -> ' + path.relative(RACINE, SQL_SORTIE));
  console.log(delais.length + ' delais a trancher, ' + nonCalculables.length
    + ' non calculables -> ' + path.relative(RACINE, RAPPORT_CABINET));
  console.log(reprises + ' rattachees / ' + perdues + ' sans equivalent / '
    + derivees.size + ' derivees / ' + nouvelles.length + ' nouvelles -> '
    + path.relative(RACINE, RAPPORT_CORRESPONDANCE));
  console.log(anomalies.length + ' constats -> ' + path.relative(RACINE, RAPPORT_TECHNIQUE));
  if (parLigne.size !== 51) throw new Error('incoherence de comptage');
}

main().catch((e) => { console.error(e); process.exit(1); });
