/**
 * Derive la migration Flyway du referentiel des demarches depuis le guide du
 * cabinet — PAS de recopie manuelle : le cabinet fournira des versions
 * ulterieures du classeur, et une recopie introduirait des ecarts.
 *
 *   node scripts/lot1/derive-referentiel-demarches.mjs
 *
 * Entrees  : specs/creation-2026-09/GUIDE_CREATION_ENTREPRISE_MAROC_SARL_v2.xlsx
 *              . onglet « 1. Parcours creation » — une ligne = une etape
 *              . onglet « 2. Workflow ticket »   — les 5 statuts et leurs plages d'etapes
 * Sorties  : backend-java/ticket-service/src/main/resources/db/migration/
 *              V20__referentiel_demarches_creation.sql
 *            output/lot1/rapport-cabinet.md       (ce que le cabinet doit trancher)
 *            output/lot1/referentiel-technique.md (trace exhaustive, pour la relecture)
 *
 * Regle : ne RIEN inventer. Une cellule vide reste NULL en base et est reportee
 * dans le rapport d'anomalies ; aucun delai, cout ni condition n'est complete au
 * juge. Seule exception assumee et tracee : le TYPAGE des justificatifs (le
 * guide les decrit en texte libre, la base a besoin d'un document_type) — chaque
 * typage figure dans TYPAGE_JUSTIFICATIFS ci-dessous et dans le rapport.
 */
import fs from 'node:fs';
import path from 'node:path';
import { readWorkbook } from './lib-xlsx.mjs';

const RACINE = path.resolve(import.meta.dirname, '../..');
const CLASSEUR = 'C:/dev/JURIKA/specs/creation-2026-09/GUIDE_CREATION_ENTREPRISE_MAROC_SARL_v2.xlsx';
const SQL_SORTIE = path.join(RACINE,
  'backend-java/ticket-service/src/main/resources/db/migration/V20__referentiel_demarches_creation.sql');
const RAPPORT_CABINET = path.join(RACINE, 'output/lot1/rapport-cabinet.md');
const RAPPORT_TECHNIQUE = path.join(RACINE, 'output/lot1/referentiel-technique.md');

/** Colonnes de l'onglet « 1. Parcours creation » (index 0 = colonne A). */
const COL = {
  numero: 0, phase: 1, libelle: 2, acteur: 3, organisme: 4, obligatoire: 5,
  condition: 6, piecesEntrantes: 7, documentProduit: 8, justificatifs: 9,
  modele: 10, delai: 11, cout: 12, variables: 13,
};

/** Libelle de statut (onglet 2) -> code persiste. */
const CODE_STATUT = {
  'Création du ticket': 'CREATION_TICKET',
  'Génération des documents': 'GENERATION_DOCUMENTS',
  'Déroulement de la démarche': 'DEROULEMENT_DEMARCHE',
  'Clôture de dossier': 'CLOTURE_DOSSIER',
  'Ticket annulé': 'ANNULE',
};

/**
 * Typage des justificatifs attendus, par numero d'etape.
 *
 * Le guide decrit les justificatifs en texte libre ; le cochage d'une demarche a
 * besoin d'un `document_type` contraint pour que le controle serveur soit
 * mecanisable. Le libelle reste celui du guide, VERBATIM — seul le type est
 * ajoute ici.
 *
 *  . `types` : plusieurs valeurs = ALTERNATIVES (l'une d'elles suffit), ce qui
 *    traduit fidelement « bail commercial, contrat de domiciliation ou local en
 *    propriete » sans forcer un choix que le guide ne fait pas.
 *  . plusieurs entrees = justificatifs CUMULATIFS (tous exiges).
 *  . `procedural: true` : le « justificatif » du guide n'est pas un document a
 *    televerser (ex. « Ticket cloture ») — aucune piece n'est exigee au cochage.
 *
 * Types REUTILISES de l'existant (aucun doublon cree) : CN = certificat negatif,
 * RC = registre du commerce (modele J), TP, ICE, CNSS, STATUTS, ACTE_NOMINATION,
 * CONTRAT_BAIL, ANNONCE_JAL.
 */
const TYPAGE_JUSTIFICATIFS = {
  1: [{ types: ['FICHE_RENSEIGNEMENTS'] }],
  2: [{ types: ['PIECE_IDENTITE'] }],
  3: [{ types: ['VALIDATION_CLIENT'] }],
  4: [{ types: ['CN'] }],
  5: [{ types: ['CONTRAT_BAIL', 'CONTRAT_DOMICILIATION', 'TITRE_PROPRIETE'] }],
  6: [{ types: ['CONTRAT_BAIL', 'CONTRAT_DOMICILIATION', 'TITRE_PROPRIETE'] }],
  7: [{ types: ['CONTRAT_BAIL', 'CONTRAT_DOMICILIATION', 'TITRE_PROPRIETE'] },
      { types: ['ATTESTATION_ENREGISTREMENT'] }],
  8: [{ types: ['STATUTS'] }],
  9: [{ types: ['ACTE_NOMINATION'] }],
  10: [{ types: ['RAPPORT_COMMISSAIRE_APPORTS'] }],
  11: [{ types: ['ETAT_ACTES_FORMATION'] }],
  12: [{ types: ['POUVOIR'] }],
  13: [{ types: ['STATUTS'] }],
  14: [{ types: ['STATUTS'] }],
  15: [{ types: ['ACTE_NOMINATION'] }],
  16: [{ types: ['ATTESTATION_BLOCAGE_CAPITAL'] }],
  17: [{ types: ['STATUTS'] }, { types: ['ATTESTATION_ENREGISTREMENT'] }],
  18: [{ types: ['ACTE_NOMINATION'] }, { types: ['ATTESTATION_ENREGISTREMENT'] }],
  19: [{ types: ['TP'] }],
  20: [{ types: ['BULLETIN_IF'] }],
  21: [{ types: ['RC'] }],
  22: [{ types: ['ICE'] }],
  23: [{ types: ['ANNONCE_JAL'] }],
  24: [{ types: ['JOURNAL_ANNONCE'] }],
  25: [{ types: ['PUBLICATION_BO'] }],
  26: [{ types: ['CNSS'] }],
  27: [{ types: ['ACCUSE_RBE'] }],
  28: [{ types: ['RIB'] }],
  29: [{ types: ['LIVRES_LEGAUX'] }],
  30: [{ types: ['AUTORISATION_SECTORIELLE'] }],
  31: [{ types: ['IDENTIFIANTS_SIMPL'] }],
  32: [{ types: ['RECEPISSE_CNDP'] }],
  33: [{ types: ['NOTE_CONFORMITE'] }],
  34: [{ procedural: true }],
  35: [{ types: ['BORDEREAU_REMISE'] }],
  36: [{ procedural: true }],
};

/**
 * Delais legaux MECANISABLES, par numero d'etape.
 *
 * Le guide donne le delai en toutes lettres (« Dans les 30 jours de l'acte »,
 * « Dans le mois de l'immatriculation ») mais jamais la DATE DE DEPART. Sans
 * point de depart, aucune alerte ne peut etre calculee — or un delai manque a
 * des consequences reelles.
 *
 * On declare donc ici, explicitement, la duree, son UNITE et l'etape de
 * reference (dont la date de cochage sert de point de depart). Chaque entree est
 * une INTERPRETATION du texte du guide : toutes sont reportees dans le rapport
 * destine au cabinet, et aucune n'est deduite automatiquement.
 *
 * REGLE : quand le point de depart est ambigu, on retient le jalon le PLUS
 * PRECOCE compatible avec le texte. Une alerte prematuree est un inconfort ;
 * une alerte tardive est une faute.
 *
 * UNITE : un delai exprime en mois se calcule en MOIS CALENDAIRES, jamais en
 * jours (31/01 + 1 mois = 28/02, pas 02/03). L'unite est donc portee jusqu'en
 * base et jamais normalisee ici.
 *
 * Les etapes dont le point de depart n'est pas mecanisable (« du debut
 * d'activite », « de l'embauche du 1er salarie ») restent volontairement
 * absentes : mieux vaut aucune alerte qu'une fausse.
 */
const DELAIS = {
  7: {
    valeur: 30, unite: 'JOURS', reference: 5,
    texte: "Dans les 30 jours de la signature",
    lecture: "depart = contrat de siege SIGNE (etape 5)",
    alternative: "contrat legalise (etape 6), soit un depart posterieur au fait generateur",
    consequence: "l'alerte partira de la signature du contrat. Retenir la legalisation la decalerait d'autant de jours que separent les deux gestes — au-dela, l'enregistrement serait deja hors delai quand l'alerte se leverait.",
  },
  17: {
    valeur: 30, unite: 'JOURS', reference: 13,
    texte: "Dans les 30 jours de l'acte",
    lecture: "depart = statuts SIGNES (etape 13)",
    alternative: "statuts legalises (etape 14), soit un depart posterieur a l'acte lui-meme",
    consequence: "l'alerte partira de la signature des statuts. Retenir la legalisation la decalerait d'autant de jours que separent les deux gestes, avec le meme risque de reveil tardif.",
  },
  18: {
    valeur: 30, unite: 'JOURS', reference: 15,
    texte: "Dans les 30 jours de l'acte",
    lecture: "depart = acte de nomination (etape 15, qui couvre signature ET legalisation)",
    alternative: "aucune : la signature est comprise dans l'etape 15",
    consequence: "aucune : le jalon retenu est le seul possible.",
  },
  20: {
    valeur: 30, unite: 'JOURS', reference: 21,
    texte: "Dans les 30 jours de la constitution",
    // ARBITRAGE DU CABINET (2026-09-05) : « la constitution » s'entend de
    // l'immatriculation au registre du commerce. La societe acquiert la
    // personnalite morale a ce moment-la ; le delai ne court pas avant.
    lecture: "depart = obtention du modele J (etape 21) — ARBITRE PAR LE CABINET",
    alternative: "aucune : le point a ete tranche par le cabinet le 05/09/2026",
    consequence: "aucune : le point est tranche.",
  },
  21: {
    valeur: 3, unite: 'MOIS', reference: 13,
    texte: "Dans les 3 mois de la constitution",
    lecture: "depart = signature des statuts (etape 13)",
    alternative: "aucune : le delai pour S'IMMATRICULER ne peut pas partir de l'immatriculation",
    consequence: "aucune : le delai pour s'immatriculer ne peut pas partir de l'immatriculation.",
  },
  24: {
    valeur: 1, unite: 'MOIS', reference: 21,
    texte: "Dans le mois de l'immatriculation",
    lecture: "depart = obtention du modele J (etape 21)",
    alternative: "aucune",
    consequence: "aucune.",
  },
  25: {
    valeur: 1, unite: 'MOIS', reference: 21,
    texte: "Dans le mois de l'immatriculation",
    lecture: "depart = obtention du modele J (etape 21)",
    alternative: "aucune",
    consequence: "aucune.",
  },
  27: {
    valeur: 1, unite: 'MOIS', reference: 21,
    texte: "Dans le mois de l'immatriculation",
    lecture: "depart = obtention du modele J (etape 21)",
    alternative: "aucune",
    consequence: "aucune.",
  },
};

/** Les 6 points « a arbitrer » de l'onglet 5 du guide, a joindre au rapport cabinet. */
const ARBITRAGES_ONGLET5 = [17, 18, 19, 20, 21, 22];

/** Une cellule ne portant qu'un tiret ou une mention « procedure interne » ne dit rien. */
const VIDE = /^(|—|-|–|n\/a|na|—\s*\(procédure interne\))$/i;
const estVide = (v) => VIDE.test((v ?? '').trim());

const sqlTexte = (v) => (estVide(v) ? 'NULL' : "'" + String(v).trim().replace(/'/g, "''") + "'");

async function main() {
  const wb = await readWorkbook(CLASSEUR);
  const parcours = wb['1. Parcours création'];
  const workflow = wb['2. Workflow ticket'];
  if (!parcours || !workflow) throw new Error('Onglets attendus introuvables dans le classeur.');

  // -- 1. Plages d'etapes par statut, LUES dans l'onglet 2 (jamais codees en dur) --
  const plages = [];
  for (const r of workflow) {
    const statut = CODE_STATUT[(r?.[1] ?? '').trim()];
    if (!statut) continue;
    const m = /(\d+)\s*à\s*(\d+)/.exec(r[4] ?? '');
    if (m) plages.push({ statut, de: Number(m[1]), a: Number(m[2]) });
  }
  const statutDeLEtape = (n) => plages.find((p) => n >= p.de && n <= p.a)?.statut ?? null;

  // -- 2. Etapes --
  const anomalies = [];
  const etapes = [];
  for (const r of parcours) {
    const num = (r?.[COL.numero] ?? '').trim();
    if (!/^\d+$/.test(num)) continue;
    const n = Number(num);

    const phaseBrute = (r[COL.phase] ?? '').trim();          // ex. « P5 Fiscal / RC »
    const phaseCode = (/^P\d/.exec(phaseBrute) || [''])[0];
    const statut = statutDeLEtape(n);
    if (!statut) {
      anomalies.push({ etape: n, champ: 'statut_ticket',
        detail: "aucune plage de l'onglet 2 ne couvre cette etape" });
    }

    const oc = (r[COL.obligatoire] ?? '').trim().toUpperCase();
    if (oc !== 'O' && oc !== 'C') {
      anomalies.push({ cat: 'TECHNIQUE', etape: n, champ: 'O/C',
        detail: 'valeur inattendue « ' + oc + ' »' });
    }
    if (oc === 'C' && estVide(r[COL.condition])) {
      anomalies.push({ cat: 'DECISION', etape: n, champ: "condition d'application",
        detail: 'etape conditionnelle sans condition ecrite' });
    }

    // Cellules structurellement vides : comptees, jamais listees. Une etape
    // interne n'a ni cout ni organisme, ce n'est pas une decision a prendre.
    for (const [cle, libelle] of [
      ['delai', 'Delai'], ['cout', 'Cout indicatif'], ['variables', 'Donnees alimentees'],
      ['justificatifs', 'Justificatif a obtenir et archiver'], ['organisme', 'Organisme / guichet'],
    ]) {
      if (estVide(r[COL[cle]])) {
        anomalies.push({ cat: 'SANS_OBJET', etape: n, champ: libelle, detail: 'cellule vide' });
      }
    }

    const cout = (r[COL.cout] ?? '').trim();
    if (!estVide(cout)) {
      // Chiffre => valeur de source publique, a remplacer par le bareme du cabinet.
      // Non chiffre => libelle de frais, a chiffrer.
      const chiffre = /^[≈~]?\s*\d/.test(cout);
      anomalies.push({ cat: 'CHIFFRAGE', etape: n, champ: 'Cout indicatif',
        detail: (chiffre ? 'source publique 2026 a remplacer par le bareme du cabinet : « '
                         : 'libelle de frais, non chiffre : « ') + cout + ' »' });
    }

    if (/à confirmer|à valider|à arbitrer/i.test(
          [r[COL.delai], r[COL.condition]].join(' '))) {
      anomalies.push({ cat: 'DECISION', etape: n, champ: 'delai / condition',
        detail: 'le guide porte lui-meme une mention « a confirmer / a valider » : '
          + [r[COL.delai], r[COL.condition]].filter((x) => /à confirmer|à valider/i.test(x ?? ''))
              .map((x) => '« ' + String(x).trim() + ' »').join(' ; ') });
    }

    const typage = TYPAGE_JUSTIFICATIFS[n];
    if (!typage) {
      anomalies.push({ cat: 'TECHNIQUE', etape: n, champ: 'typage justificatif',
        detail: 'aucun typage defini pour cette etape' });
    }

    const delaiRegle = DELAIS[n];
    if (delaiRegle) {
      anomalies.push({ cat: 'DELAI', etape: n, champ: 'point de depart du delai',
        delai: delaiRegle, libelle: (r[COL.libelle] ?? '').trim() });
    } else if (/\d+\s*(jour|mois)|dans le mois/i.test(r[COL.delai] ?? '')) {
      anomalies.push({ cat: 'DECISION', etape: n, champ: 'delai non calculable',
        detail: 'delai chiffre mais point de depart non mecanisable : « '
          + (r[COL.delai] ?? '').trim() + ' » — AUCUNE alerte ne sera levee sur cette etape' });
    }

    etapes.push({ n, phaseCode, phaseLibelle: phaseBrute, statut, oc, ligne: r,
      typage: typage ?? [], delaiRegle });
  }

  // -- 2 ter. UN CODE DE PHASE = UN LIBELLE --
  //
  // Lot 2 (2026-09-07). La colonne « Phase » du guide portait deux libelles
  // sous le meme code P4 (« P4 Capital » pour l'etape 16, « P4 Enregistrement »
  // pour les etapes 17-18) alors que la banniere de l'onglet n'annonce qu'une
  // phase : « PHASE 4 — CAPITAL ET ENREGISTREMENT ». L'API regroupant par CODE,
  // un des deux libelles disparaissait purement et simplement de l'affichage —
  // sans erreur, sans trace. On le detecte desormais a la derivation plutot que
  // de laisser un libelle se perdre en silence.
  {
    const parCode = new Map();
    for (const e of etapes) {
      if (!parCode.has(e.phaseCode)) parCode.set(e.phaseCode, new Set());
      parCode.get(e.phaseCode).add(e.phaseLibelle);
    }
    for (const [code, libelles] of parCode) {
      if (libelles.size > 1) {
        anomalies.push({ cat: 'DECISION', etape: null, champ: 'libelle de phase',
          detail: 'le code ' + code + ' porte ' + libelles.size + ' libelles differents ('
            + [...libelles].map((x) => '« ' + x + ' »').join(', ')
            + '). L\'affichage regroupe par code : un seul survivra. '
            + 'Trancher : un libelle unique, ou des codes distincts.' });
      }
    }
  }

  // -- 2 bis. Le graphe des points de depart doit etre ACYCLIQUE --
  //
  // Une echeance se calcule depuis la date de cochage d'une autre etape, qui
  // peut elle-meme en referencer une troisieme (20 -> 21 -> 13). Un cycle
  // rendrait le delai incalculable et, pire, ne se verrait pas : l'etape
  // resterait simplement muette. On le detecte a la derivation.
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
    // Le point de depart final doit exister au parcours.
    if (courant != null && !etapes.some((e) => e.n === courant)) {
      throw new Error('Etape ' + depart + ' : point de depart ' + courant
        + ' absent du parcours');
    }
  }

  // -- 3. SQL --
  const l = [];
  l.push('-- =====================================================================');
  l.push('-- JURIKA V20 — Referentiel des demarches (workflow CREATION)');
  l.push('--');
  l.push('-- FICHIER GENERE. Ne pas editer a la main : regenerer avec');
  l.push('--   node scripts/lot1/derive-referentiel-demarches.mjs');
  l.push('-- Source : specs/creation-2026-09/GUIDE_CREATION_ENTREPRISE_MAROC_SARL_v2.xlsx');
  l.push('-- Onglets « 1. Parcours creation » et « 2. Workflow ticket » — ' + etapes.length + ' etapes.');
  l.push('--');
  l.push('-- Le referentiel vit en DONNEES et non en code : les 36 etapes vont evoluer');
  l.push("-- (l'onglet 5 du guide liste 6 points « a arbitrer ») et les autres workflows");
  l.push('-- auront leurs propres parcours. Une correction = une migration, pas un');
  l.push('-- redeploiement. Les cellules vides du guide restent NULL — rien n est');
  l.push('-- complete au juge (cf. output/lot1/referentiel-anomalies.md).');
  l.push('-- =====================================================================');
  l.push('');
  l.push('CREATE TABLE IF NOT EXISTS demarches_referentiel (');
  l.push('    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),');
  l.push('    workflow_type         VARCHAR(30)  NOT NULL,');
  l.push('    ordre                 SMALLINT     NOT NULL,');
  l.push('    phase_code            VARCHAR(8)   NOT NULL,');
  l.push('    phase_libelle         VARCHAR(60)  NOT NULL,');
  l.push('    libelle               VARCHAR(400) NOT NULL,');
  l.push('    statut_ticket         VARCHAR(30)  NOT NULL,');
  l.push('    acteur                VARCHAR(80),');
  l.push('    organisme             VARCHAR(300),');
  l.push("    obligatoire           CHAR(1)      NOT NULL CHECK (obligatoire IN ('O','C')),");
  l.push('    condition_application TEXT,');
  l.push('    pieces_entrantes      TEXT,');
  l.push('    document_produit      TEXT,');
  l.push('    justificatifs_texte   TEXT,');
  l.push('    modele_jurika         VARCHAR(200),');
  l.push('    delai                 VARCHAR(300),');
  l.push('    cout_indicatif        TEXT,');
  l.push('    variables_alimentees  TEXT,');
  l.push('    -- Delai MECANISABLE : valeur + UNITE + etape dont la date de cochage sert');
  l.push('    -- de point de depart. NULL = le guide donne un delai en toutes lettres');
  l.push('    -- mais aucun point de depart calculable : aucune alerte ne sera levee.');
  l.push('    --');
  l.push('    -- L unite est portee jusqu ici et JAMAIS normalisee en jours : un delai');
  l.push('    -- legal exprime en mois se calcule en mois calendaires (31/01 + 1 mois');
  l.push('    -- = 28/02, et non 02/03). Normaliser produirait des echeances trop');
  l.push('    -- tardives sur les mois de 31 jours.');
  l.push('    delai_valeur          SMALLINT,');
  l.push("    delai_unite           VARCHAR(5) CHECK (delai_unite IN ('JOURS','MOIS')),");
  l.push('    delai_reference_ordre SMALLINT,');
  l.push('    CONSTRAINT chk_delai_complet CHECK (');
  l.push('        (delai_valeur IS NULL AND delai_unite IS NULL AND delai_reference_ordre IS NULL)');
  l.push('     OR (delai_valeur IS NOT NULL AND delai_unite IS NOT NULL');
  l.push('         AND delai_reference_ordre IS NOT NULL)),');
  l.push('    UNIQUE (workflow_type, ordre)');
  l.push(');');
  l.push('');
  l.push('-- Justificatifs attendus au cochage, TYPES (le type ne se saisit pas :');
  l.push('-- il vient du referentiel). Deux lignes de meme `alternative_groupe`');
  l.push("-- sont des ALTERNATIVES — l'une d'elles suffit (ex. bail OU domiciliation");
  l.push('-- OU titre de propriete) ; deux groupes distincts sont CUMULATIFS.');
  l.push('CREATE TABLE IF NOT EXISTS demarches_justificatifs (');
  l.push('    id                 UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),');
  l.push('    demarche_id        UUID         NOT NULL REFERENCES demarches_referentiel(id) ON DELETE CASCADE,');
  l.push('    alternative_groupe SMALLINT     NOT NULL,');
  l.push('    document_type      VARCHAR(60)  NOT NULL,');
  l.push('    libelle            TEXT         NOT NULL,');
  l.push('    UNIQUE (demarche_id, alternative_groupe, document_type)');
  l.push(');');
  l.push('');
  l.push('CREATE INDEX IF NOT EXISTS idx_demarches_ref_workflow');
  l.push('    ON demarches_referentiel (workflow_type, ordre);');
  l.push('CREATE INDEX IF NOT EXISTS idx_demarches_ref_statut');
  l.push('    ON demarches_referentiel (workflow_type, statut_ticket, ordre);');
  l.push('CREATE INDEX IF NOT EXISTS idx_demarches_justif_demarche');
  l.push('    ON demarches_justificatifs (demarche_id);');
  l.push('');
  l.push('-- Rechargement idempotent : le referentiel est une donnee de reference, pas');
  l.push('-- une donnee client. On le remplace integralement a chaque version du guide.');
  l.push('DELETE FROM demarches_justificatifs WHERE demarche_id IN');
  l.push("    (SELECT id FROM demarches_referentiel WHERE workflow_type = 'CREATION');");
  l.push("DELETE FROM demarches_referentiel WHERE workflow_type = 'CREATION';");
  l.push('');

  for (const e of etapes) {
    const r = e.ligne;
    l.push('-- -- Etape ' + e.n + ' — ' + (r[COL.libelle] ?? '').replace(/\s+/g, ' '));
    l.push('INSERT INTO demarches_referentiel (workflow_type, ordre, phase_code, phase_libelle,');
    l.push('        libelle, statut_ticket, acteur, organisme, obligatoire, condition_application,');
    l.push('        pieces_entrantes, document_produit, justificatifs_texte, modele_jurika,');
    l.push('        delai, cout_indicatif, variables_alimentees,');
    l.push('        delai_valeur, delai_unite, delai_reference_ordre)');
    l.push("VALUES ('CREATION', " + e.n + ", '" + e.phaseCode + "', " + sqlTexte(e.phaseLibelle) + ',');
    l.push('        ' + sqlTexte(r[COL.libelle]) + ", '" + e.statut + "', " + sqlTexte(r[COL.acteur]) + ',');
    l.push('        ' + sqlTexte(r[COL.organisme]) + ", '" + e.oc + "', " + sqlTexte(r[COL.condition]) + ',');
    l.push('        ' + sqlTexte(r[COL.piecesEntrantes]) + ', ' + sqlTexte(r[COL.documentProduit]) + ',');
    l.push('        ' + sqlTexte(r[COL.justificatifs]) + ', ' + sqlTexte(r[COL.modele]) + ',');
    l.push('        ' + sqlTexte(r[COL.delai]) + ', ' + sqlTexte(r[COL.cout]) + ', '
           + sqlTexte(r[COL.variables]) + ', '
           + (e.delaiRegle ? e.delaiRegle.valeur : 'NULL') + ', '
           + (e.delaiRegle ? "'" + e.delaiRegle.unite + "'" : 'NULL') + ', '
           + (e.delaiRegle ? e.delaiRegle.reference : 'NULL') + ');');
    if (e.delaiRegle) {
      l.push('--   delai « ' + e.delaiRegle.texte + ' » -> ' + e.delaiRegle.lecture);
      l.push('--   lecture alternative : ' + e.delaiRegle.alternative);
    }

    const texteJustif = (r[COL.justificatifs] ?? '').trim();
    e.typage.forEach((j, i) => {
      if (j.procedural) {
        l.push('--   justificatif procedural (« ' + texteJustif + ' ») : aucune piece a televerser.');
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
  fs.mkdirSync(path.dirname(SQL_SORTIE), { recursive: true });
  fs.writeFileSync(SQL_SORTIE, l.join('\n'), 'utf8');

  // -- 4. Deux rapports distincts --
  //
  // Le rapport CABINET ne contient que ce qui appelle une decision. Le rapport
  // TECHNIQUE garde la trace exhaustive : utile a la relecture, illisible pour
  // un directeur.
  const decisions = anomalies.filter((x) => x.cat === 'DELAI' || x.cat === 'DECISION');
  const chiffrages = anomalies.filter((x) => x.cat === 'CHIFFRAGE');
  const sansObjet = anomalies.filter((x) => x.cat === 'SANS_OBJET');
  const techniques = anomalies.filter((x) => x.cat === 'TECHNIQUE');

  const nbJustif = etapes.reduce((acc, e) =>
    acc + e.typage.filter((j) => !j.procedural).reduce((x, j) => x + j.types.length, 0), 0);

  const delaisTous = decisions.filter((x) => x.cat === 'DELAI');
  const delais = delaisTous.filter((x) => !x.delai.alternative.startsWith('aucune'));
  const prioritaires = delais.filter((x) => x.delai.aTrancherEnPriorite);
  const autresDelais = delais.filter((x) => !x.delai.aTrancherEnPriorite);
  const autresDecisions = decisions.filter((x) => x.cat === 'DECISION')
    .sort((x, y) => x.etape - y.etape);

  const onglet5 = wb['5. Écarts & compléments'] || [];
  const arbitrages = onglet5.filter((r) => r && ARBITRAGES_ONGLET5.includes(Number(r[0])));

  // -- 4a. Rapport destine au cabinet --
  const cab = [];
  cab.push('# Points a trancher — parcours de creation SARL');
  cab.push('');
  cab.push('Genere depuis `GUIDE_CREATION_ENTREPRISE_MAROC_SARL_v2.xlsx`.');
  cab.push('Rien n a ete complete au juge : ce que le guide ne dit pas reste vide en base.');
  cab.push('');
  cab.push('| Section | Nature | Lignes |');
  cab.push('|---|---|---|');
  cab.push('| 1 | Decisions juridiques | ' + (delais.length + autresDecisions.length + arbitrages.length) + ' |');
  cab.push('| 2 | Coûts a chiffrer | ' + chiffrages.length + ' |');
  cab.push('| 3 | Sans objet (comptees, non listees) | ' + sansObjet.length + ' |');
  cab.push('');

  cab.push('## 1. A trancher — decisions juridiques');
  cab.push('');
  cab.push('### 1.1 Points de depart des delais legaux');
  cab.push('');
  cab.push('Le guide donne les delais (« dans les 30 jours de l acte ») mais jamais la date');
  cab.push('a partir de laquelle ils courent. Chaque lecture ci-dessous applique une regle');
  cab.push('constante : **le jalon le plus precoce compatible avec le texte**. Une alerte');
  cab.push('prematuree est un inconfort ; une alerte tardive est une faute.');
  cab.push('');
  for (const d of [...prioritaires, ...autresDelais]) {
    const r = d.delai;
    cab.push('**Etape ' + d.etape + ' — ' + d.libelle + '**'
      + (r.aTrancherEnPriorite ? '  ← **a trancher en priorite**' : ''));
    cab.push('');
    cab.push('| | |');
    cab.push('|---|---|');
    cab.push('| Texte du guide | « ' + r.texte + ' » |');
    cab.push('| Duree retenue | ' + r.valeur + ' ' + (r.unite === 'MOIS' ? 'mois calendaires' : 'jours') + ' |');
    cab.push('| Lecture retenue | ' + r.lecture + ' |');
    cab.push('| Lecture alternative | ' + r.alternative + ' |');
    cab.push('| Consequence | ' + r.consequence + ' |');
    cab.push('');
  }
  const sansAlternative = delaisTous.filter((x) => x.delai.alternative.startsWith('aucune'));
  if (sansAlternative.length) {
    cab.push('Les etapes ' + sansAlternative.map((x) => x.etape).join(', ')
      + ' portent aussi un delai calcule, mais leur point de depart ne souffre');
    cab.push('aucune autre lecture : rien a trancher.');
    cab.push('');
  }

  if (autresDecisions.length) {
    cab.push('### 1.2 Points que le guide signale lui-meme comme incertains');
    cab.push('');
    cab.push('| Etape | Point |');
    cab.push('|---|---|');
    for (const d of autresDecisions) cab.push('| ' + d.etape + ' | ' + d.detail + ' |');
    cab.push('');
  }

  if (arbitrages.length) {
    cab.push('### 1.3 Points « a arbitrer » de l onglet 5 du guide');
    cab.push('');
    cab.push('Repris verbatim. Ils ne sont PAS implementes : le code applique le parcours');
    cab.push('tel qu il est ecrit dans l onglet 1.');
    cab.push('');
    for (const r of arbitrages) {
      cab.push('**' + r[0] + '. ' + (r[5] || '').trim() + '** — criticite : ' + (r[4] || '').trim());
      cab.push('');
      cab.push('- *Constat* : ' + (r[2] || '').trim());
      cab.push('- *A decider* : ' + (r[3] || '').trim());
      cab.push('');
    }
  }

  cab.push('## 2. A chiffrer — bareme du cabinet');
  cab.push('');
  cab.push('Les montants du guide viennent de sources publiques 2026, ou sont donnes en');
  cab.push('toutes lettres (« timbre par signature »). Ils sont conserves TELS QUELS en base');
  cab.push('et doivent etre remplaces par le bareme reellement pratique.');
  cab.push('');
  cab.push('| Etape | Cout indique par le guide |');
  cab.push('|---|---|');
  for (const c of chiffrages.sort((x, y) => x.etape - y.etape)) {
    cab.push('| ' + c.etape + ' | ' + c.detail + ' |');
  }
  cab.push('');

  const etapesSansObjet = new Set(sansObjet.map((x) => x.etape));
  cab.push('## 3. Sans objet');
  cab.push('');
  cab.push('**' + sansObjet.length + ' cellules vides** reparties sur ' + etapesSansObjet.size + ' etapes :');
  cab.push('des etapes internes sans cout, sans organisme exterieur ou sans variable produite.');
  cab.push('Rien a decider ; elles sont NULL en base et le restent. Le detail figure dans le');
  cab.push('rapport technique.');
  cab.push('');
  fs.mkdirSync(path.dirname(RAPPORT_CABINET), { recursive: true });
  fs.writeFileSync(RAPPORT_CABINET, cab.join('\n'), 'utf8');

  // -- 4b. Rapport technique (trace exhaustive) --
  const parEtape = new Map();
  for (const x of anomalies) {
    if (!parEtape.has(x.etape)) parEtape.set(x.etape, []);
    parEtape.get(x.etape).push(x);
  }
  const tec = [];
  tec.push('# Referentiel des demarches — trace technique complete');
  tec.push('');
  tec.push('Genere par `scripts/lot1/derive-referentiel-demarches.mjs`.');
  tec.push('Ce fichier n est PAS destine au cabinet : voir `rapport-cabinet.md`.');
  tec.push('');
  tec.push('- Etapes chargees : **' + etapes.length + '**');
  tec.push('- Phases : **' + new Set(etapes.map((e) => e.phaseCode)).size + '**');
  tec.push('- Justificatifs types : **' + nbJustif + '**');
  tec.push('- Delais calculables : **' + delaisTous.length + '** dont **' + delais.length
    + '** avec une lecture alternative');
  tec.push('- Constats : ' + decisions.length + ' decisions, ' + chiffrages.length
    + ' chiffrages, ' + sansObjet.length + ' sans objet, ' + techniques.length + ' techniques');
  tec.push('');
  tec.push('| Etape | Categorie | Champ | Constat |');
  tec.push('|---|---|---|---|');
  for (const n of [...parEtape.keys()].sort((x, y) => x - y)) {
    for (const x of parEtape.get(n)) {
      const detail = x.cat === 'DELAI'
        ? '« ' + x.delai.texte + ' » -> ' + x.delai.lecture
          + ' (' + x.delai.valeur + ' ' + x.delai.unite + ', ref. etape ' + x.delai.reference + ')'
        : x.detail;
      tec.push('| ' + n + ' | ' + x.cat + ' | ' + x.champ + ' | ' + detail + ' |');
    }
  }
  tec.push('');
  fs.writeFileSync(RAPPORT_TECHNIQUE, tec.join('\n'), 'utf8');

  console.log(etapes.length + ' etapes -> ' + path.relative(RACINE, SQL_SORTIE));
  console.log((delais.length + autresDecisions.length + arbitrages.length)
    + ' points a trancher -> ' + path.relative(RACINE, RAPPORT_CABINET));
  console.log(anomalies.length + ' constats -> ' + path.relative(RACINE, RAPPORT_TECHNIQUE));
}

main().catch((e) => { console.error(e); process.exit(1); });
