/**
 * LOT B — LE CATALOGUE DES CHAMPS DU PARCOURS DE CREATION.
 *
 *   node scripts/lotB/derive-champs-creation.mjs
 *
 * ---------------------------------------------------------------------------
 * CE QUE CE SCRIPT ETABLIT, ET POURQUOI IL EST GENERE
 *
 * Le lot A a etabli qu'ouvrir le corpus du 9 septembre demande **160 champs de
 * saisie**. Les ecrire a la main, un par un, garantirait la derive : le cabinet
 * livrera d'autres versions du corpus, et une recopie ne se resynchronise pas.
 * Le catalogue est donc DERIVE de trois sources, toutes deja au depot :
 *
 *   1. `specs/creation-2026-09-09/1. Parcours creation.xlsx`
 *      — quelle ligne produit quel document, sous quelle condition, a quel statut.
 *        C'est la colonne « Modele JURIKA » qui fait foi, pas une liste ecrite ici.
 *   2. `ai-service/.../templates/v2/manifest.json`
 *      — quel document consomme quelle variable, et quelles boucles il porte.
 *        C'est ce qui permet la regle posee par l'utilisateur : **un champ
 *        n'apparait que si le document qui le consomme est retenu**.
 *   3. `output/2026-09-10_lotA_ecart_404_variables.md`
 *      — les quatre categories du lot A : deja resolue / derivable / a saisir /
 *        sans source. Seules les « a saisir » deviennent des champs.
 *
 * ---------------------------------------------------------------------------
 * CE QUI N'EST PAS DECIDE ICI : L'OBLIGATION
 *
 * Aucun champ n'est marque « obligatoire ». Ce n'est pas un oubli.
 *
 * Le classement affine au lot A — phrase contre case, **par segment** — se fait
 * sur le DOCUMENT RENDU, pas sur une liste tenue a la main
 * (`ControleCompletude` + `MissingVariableMarker.applyDetailed`). Une variable
 * qui tombe au milieu d'une phrase refuse la generation et nomme la ligne ;
 * une variable seule apres un libelle (« Ville : ») sort blanche et passe. Une
 * variable d'une branche conditionnelle non retenue, ou d'une boucle vide, a
 * disparu du document : elle ne peut pas etre reclamee.
 *
 * Dupliquer ce jugement ici, en dur, produirait deux verites qui divergeraient.
 * Le parcours demande donc les champs sans les exiger, et la generation dit,
 * document par document, ce qui manque vraiment.
 *
 * Les SEULS blocages a la saisie sont les trois controles du § 18 du
 * dictionnaire, qui ne portent pas sur des champs de formulaire mais sur des
 * pieces du dossier — ils sont implementes cote serveur, pas ici.
 *
 * ---------------------------------------------------------------------------
 * SORTIES
 *   backend-java/ai-service/src/main/resources/templates/v2/creation-champs.json
 *     — le catalogue, lu par le mapper et par le controle de saisie.
 *   frontend-react/src/pages/workflow/steps/documents-creation.generated.ts
 *     — le meme catalogue, type, pour l'affichage conditionnel.
 *   output/lotB/champs-ouverts.md        — combien des 160, et lesquels.
 *   output/lotB/variables-sans-source.md — les 55, pour le cabinet.
 */
import fs from 'node:fs';
import path from 'node:path';
import { readWorkbook } from '../lot1/lib-xlsx.mjs';

const RACINE = path.resolve(import.meta.dirname, '../..');
const CLASSEUR = 'C:/dev/JURIKA/specs/creation-2026-09-09/1. Parcours creation.xlsx';
const DICTIONNAIRE = 'C:/dev/JURIKA/specs/creation-2026-09-09/modeles_markdown/'
  + 'dictionnaire_variables_creation.md';
const MANIFESTE = path.join(RACINE,
  'backend-java/ai-service/src/main/resources/templates/v2/manifest.json');
const ECART = path.join(RACINE, 'output/2026-09-10_lotA_ecart_404_variables.md');

const SORTIE_JSON = path.join(RACINE,
  'backend-java/ai-service/src/main/resources/templates/v2/creation-champs.json');
const SORTIE_TS = path.join(RACINE,
  'frontend-react/src/pages/workflow/steps/documents-creation.generated.ts');
const RAPPORT_CHAMPS = path.join(RACINE, 'output/lotB/champs-ouverts.md');
const RAPPORT_SANS_SOURCE = path.join(RACINE, 'output/lotB/variables-sans-source.md');

const COL = { numero: 0, statut: 1, libelle: 2, condition: 3, delai: 4,
              documentProduit: 5, justificatifs: 6, modele: 7, variables: 8 };

const MODELES = 'C:/dev/JURIKA/specs/creation-2026-09-09/modeles_markdown';

/**
 * LES SAISIES QUE LE LOT A CLASSE « DEJA RESOLUES » ET QUI SONT POURTANT DES
 * SAISIES.
 *
 * La classification du lot A porte sur la RESOLUTION : le mapper sait lire ces
 * variables. Elle ne dit rien de leur SOURCE — et la leur, c'est l'employe. Elles
 * arrivaient des « complements » du lot 5, que ce lot remplace : sans champ,
 * plus personne ne les renseigne.
 *
 * Constate sur le document produit : la declaration d'existence sortait avec ses
 * vingt-deux cases vides, regime fiscal compris, et partait ainsi a la DGI.
 *
 * La cle est celle que `CreationFormulairesVarsBuilder` lit sous
 * `payload.formulaires` — contrat du lot 5, inchange.
 */
const SAISIES_HERITEES = {
  DE_REGIME_RESULTAT: 'regimeResultat',
  DE_ACTIVITE_NATURE: 'activiteNature',
  DE_TVA_ASSUJETTISSEMENT: 'tvaAssujettissement',
  DE_TVA_FAIT_GENERATEUR: 'tvaFaitGenerateur',
  DE_TVA_PERIODICITE: 'tvaPeriodicite',
  TP_OBJET: 'tpObjet',
  TP_OBJET_AUTRE_PRECISION: 'tpObjetAutrePrecision',
  DIRECTION_REGIONALE: 'directionRegionale',
  SUBDIVISION: 'subdivision',
  TELEPHONE: 'telephone',
  FAX: 'fax',
  EMAIL: 'email',
  ENSEIGNE: 'enseigne',
  PIECES_PRODUITES: 'piecesProduites',
  ASSOCIE_PRINCIPAL_IF: 'associePrincipalIf',
  ASSOCIE_PRINCIPAL_VILLE: 'associePrincipalVille',
  ASSOCIE_PRINCIPAL_TEL: 'associePrincipalTel',
  ASSOCIE_PRINCIPAL_FAX: 'associePrincipalFax',
  ASSOCIE_PRINCIPAL_EMAIL: 'associePrincipalEmail',
  // Trouvee par le parcours temoin : la fiche de renseignements refusait de
  // sortir sur « Date de debut d'activite envisagee : . ». Le mapper lit bien
  // `formulaires.dateDebutActivite` et en derive $DATE_COMMENCEMENT_EXPLOITATION
  // puis $DATE_DEBUT_ACTIVITE — mais aucun ecran ne la produisait. C'est aussi
  // le point de depart des deux delais de 30 jours que le produit n'alerte pas
  // (lignes 23 et 32 du parcours) : avec ce champ, ils deviennent calculables.
  DATE_DEBUT_ACTIVITE: 'dateDebutActivite',
};

/**
 * LES OPTIONS D'UNE CASE A COCHER SE LISENT DANS LE MODELE, PAS AU DICTIONNAIRE.
 *
 * Le moteur coche la case dont le libelle est EXACTEMENT egal a la valeur. Le
 * dictionnaire, lui, RESUME : « Resultat net reel » la ou le gabarit ecrit
 * « Impot sur les societes — regime du resultat net reel ». Une liste deroulante
 * batie sur le resume ne cochera jamais rien — et le formulaire partirait vide a
 * l'administration, sans erreur et sans trace.
 *
 * On lit donc les blocs `◈ CASE À COCHER pilotée par $VAR` et les lignes `- …`
 * qui suivent, dans les 23 modeles.
 */
function lireOptionsDesModeles() {
  const parVariable = new Map();
  for (const fichier of fs.readdirSync(MODELES)) {
    if (!fichier.endsWith('.md')) continue;
    const lignes = fs.readFileSync(path.join(MODELES, fichier), 'utf8').split('\n');
    for (let i = 0; i < lignes.length; i++) {
      const m = /◈\s*CASE À COCHER[^$]*\$([A-Z0-9_]+)/.exec(lignes[i]);
      if (!m) continue;
      const options = [];
      for (let j = i + 1; j < lignes.length; j++) {
        const o = /^-\s+(.+?)\s*$/.exec(lignes[j]);
        if (!o) break;
        options.push(o[1].trim());
      }
      if (options.length >= 2 && !parVariable.has(m[1])) parVariable.set(m[1], options);
    }
  }
  return parVariable;
}

/**
 * Le parcours nomme les modeles en clair ; le manifeste les nomme par code.
 * Quatre libelles ne se deduisent pas mecaniquement — ils sont alignes ici, et
 * nulle part ailleurs.
 */
const ALIAS_MODELE = {
  ANNONCE_LEGALE: 'ANNONCE_LEGALE_CONSTITUTION',
  ETAT_ACTES_SOCIETE_EN_FORMATION: 'ETAT_ACTES_SOCIETE_EN_FORMATION',
};

/**
 * LES DEUX DOCUMENTS COCHES PAR DEFAUT AU STATUT 2 — decision du cabinet, non
 * rouvrable : « Statuts et Annonce legale uniquement ; l'employe choisit le
 * reste ». Les variantes SARL / SARL AU comptent pour un seul document : c'est
 * `$ASSOCIE_UNIQUE` qui choisit, pas l'employe.
 */
const COCHES_PAR_DEFAUT = new Set([
  'STATUTS_SARL', 'STATUTS_SARL_AU', 'ANNONCE_LEGALE_CONSTITUTION',
]);

/**
 * Modeles du corpus qui ne sont rattaches a AUCUNE ligne du parcours par la
 * colonne « Modele JURIKA ». Il n'y en a qu'un, et ce n'est pas un oubli du
 * cabinet : le rapport du commissaire aux apports n'est pas un document que
 * JURIKA produit au fil du parcours, c'est une piece dont l'obtention est l'un
 * des trois CONTROLES BLOQUANTS du § 18. Le gabarit existe pour le cas ou le
 * cabinet le redige lui-meme.
 */
const HORS_PARCOURS = {
  RAPPORT_COMMISSAIRE_APPORTS: {
    statut: 'GENERATION_DOCUMENTS',
    condition: "Si des apports en nature sont soumis a l'evaluation d'un commissaire",
    note: "Piece du controle bloquant § 18, pas une ligne du parcours.",
  },
};

/**
 * Type de champ, deduit du nom de la variable.
 *
 * Le test du NOMBRE porte sur le DERNIER segment, jamais sur le nom entier :
 * `$BAIL_LOYER_CHIFFRES` est un montant, `$BAIL_LOYER_PERIODICITE` ne l'est pas.
 * Un champ de periodicite rendu en `<input type="number">` ne se remplit pas.
 */
const SEGMENTS_NOMBRE = new Set([
  'CHIFFRES', 'MONTANT', 'TOTAL', 'NOMBRE', 'POURCENTAGE', 'SUPERFICIE',
  'EFFECTIF', 'VALEUR', 'PARTS', 'QUOTITE',
]);
const SEGMENTS_TEXTE_LONG = new Set([
  'OBSERVATION', 'OBSERVATIONS', 'DESCRIPTION', 'PRECISION', 'MENTIONS',
  'FINALITE', 'MOTIF', 'LISTE', 'DETAIL', 'COMMENTAIRE',
]);

function typeDe(nom, options) {
  if (options && options.length) return 'select';
  const segments = nom.split('_');
  const dernier = segments[segments.length - 1];
  if (segments.includes('DATE')) return 'date';
  if (SEGMENTS_NOMBRE.has(dernier)) return 'number';
  if (SEGMENTS_TEXTE_LONG.has(dernier)) return 'textarea';
  if (nom === 'BE_CHAINE_DETENTION' || nom === 'OBJET_SOCIAL') return 'textarea';
  return 'text';
}

/** Segments -> mot francais. Ce qui n'est pas ici reste tel quel, en minuscules. */
const MOTS = {
  // Sigles et abreviations que le corpus emploie en prefixe : les laisser tels
  // quels donnerait « Tp commune » et « De tva date option ».
  TP: 'taxe professionnelle',
  DE: "déclaration d'existence",
  TVA: 'TVA',
  RC: 'registre du commerce',
  IF: 'identifiant fiscal',
  ICE: 'ICE',
  CIN: 'CIN',
  CN: 'certificat négatif',
  JAL: "journal d'annonces légales",
  BO: 'Bulletin officiel',
  DGI: 'DGI',
  RIB: 'RIB',
  ADRESSE: 'adresse', AGENCE: 'agence', ANNULATION: 'annulation', APPORT: 'apport',
  APPORTS: 'apports', BAIL: 'bail', BAILLEUR: 'bailleur', BANQUE: 'banque',
  BE: 'bénéficiaire effectif', BENEFICIAIRE: 'bénéficiaire', BORDEREAU: 'bordereau',
  CAPACITE: 'capacité', CAPITAL: 'capital', CHAINE: 'chaîne', CHARGES: 'charges',
  CIVILITE: 'civilité', CNDP: 'CNDP', CNSS: 'CNSS', COMMISSAIRE: 'commissaire',
  COMPLEMENT: 'complément', COMPTE: 'compte', CONTACT: 'contact', CRITERE: 'critère',
  DATE: 'date', DEBLOCAGE: 'déblocage', DEBUT: 'début', DECLARANT: 'déclarant',
  DECLARATION: 'déclaration',
  DELIVRANCE: 'délivrance', DEMARCHE: 'démarche', DENOMINATION: 'dénomination',
  DEPOT: 'dépôt', DESIGNATION: 'désignation', DESTINATAIRE: 'destinataire',
  DESTINATION: 'destination', DETENTION: 'détention', DOCUMENT: 'document',
  DOMICILIATAIRE: 'domiciliataire', DOMICILIATION: 'domiciliation', DUREE: 'durée',
  EFFET: 'effet', EMAIL: 'courriel', ETRANGER: 'étranger', EVENEMENT: 'événement',
  FIN: 'fin', FINALITE: 'finalité', FONCTION: 'fonction', FORME: 'forme',
  FORMATION: 'formation', GENRE: 'genre', GERANT: 'gérant', HABILITEE: 'habilitée',
  IDENTIFIANT: 'identifiant', INTERMEDIAIRE: 'intermédiaire', LEGAL: 'légal',
  LIBELLE: 'libellé', LIEU: 'lieu', LOYER: 'loyer', MANDANT: 'mandant',
  MANDATAIRE: 'mandataire', MENTIONS: 'mentions', MESURES: 'mesures', METHODE: 'méthode',
  MODE: 'mode', MOTIF: 'motif', NAISSANCE: 'naissance', NATIONALITE: 'nationalité',
  NATURE: 'nature', NOM: 'nom', NOMBRE: 'nombre', NUMERO: 'numéro',
  OBJET: 'objet', OBSERVATION: 'observation', OBSERVATIONS: 'observations',
  ORIGINE: 'origine', PARTS: 'parts', PAYS: 'pays', PERIODICITE: 'périodicité',
  PERSONNES: 'personnes', PIECE: 'pièce', PIECES: 'pièces', POURCENTAGE: 'pourcentage',
  POUVOIR: 'pouvoir', PRECISION: 'précision', PRENOM: 'prénom', PROPRIETE: 'propriété',
  QUALITE: 'qualité', RBE: 'registre des bénéficiaires effectifs', RECEPISSE: 'récépissé',
  REFERENCE: 'référence', REPRESENTANT: 'représentant', RESIDENCE: 'résidence',
  RESPONSABLE: 'responsable', RETRAIT: 'retrait', SECURITE: 'sécurité',
  SIEGE: 'siège', SIGNATAIRE: 'signataire', SIMPL: 'SIMPL', SOUSCRIPTION: 'souscription',
  STATUT: 'statut', SUPERFICIE: 'superficie', TELEPHONE: 'téléphone', TITRE: 'titre',
  TRAITEMENT: 'traitement', TRANSFERT: 'transfert', TYPE: 'type', VILLE: 'ville',
  VOTE: 'vote',
  ACQUISITION: 'acquisition', ACTE: 'acte', ACTES: 'actes', ACTIVITE: 'activité',
  ADHESION: 'adhésion', AFFILIATION: 'affiliation', ATTESTATION: 'attestation',
  AUTRE: 'autre', BLOCAGE: 'blocage', COMMUNE: 'commune', CONCERNEES: 'concernées',
  CONFORMITE: 'conformité', COMMERCIAL: 'commercial', DEFINITIF: 'définitif',
  EFFECTIF: 'effectif', EFFECTIFS: 'effectifs', ENGAGEMENT: 'engagement',
  EXISTE: 'existe', HABILITE: 'habilité', IMMATRICULATION: 'immatriculation',
  INTERROMPUES: 'interrompues', LIBERATION: 'libération', LOCAUX: 'locaux',
  MANQUANTES: 'manquantes', MODIFICATION: 'modification', OCCUPATION: 'occupation',
  OPTION: 'option', PERIODICITE: 'périodicité', PERSONNELLES: 'personnelles',
  PREMIER: 'premier', PREVISIONNEL: 'prévisionnel', PRINCIPAL: 'principal',
  PRODUITES: 'produites', PROFESSIONNELLE: 'professionnelle', QUOTITE: 'quotité',
  REGLEMENTEE: 'réglementée', REMISES: 'remises', REGULARISATION: 'régularisation',
  SALARIE: 'salarié', SOUSCRIPTEUR: 'souscripteur', SOUSCRIPTIONS: 'souscriptions',
  SUPERFICIE: 'superficie', TRAITEMENTS: 'traitements', VERSEMENT: 'versement',
  VERSE: 'versé',
  // Accents manquants releves en relisant les libelles a l'ecran : un champ
  // s'appelait « Bail loyer modalites ». Le libelle est ce que l'employe lit ;
  // il n'a pas a porter la trace du nom technique de la variable.
  ASSOCIE: 'associé', CARACTERE: 'caractère', CATEGORIES: 'catégories',
  DEMANDEES: 'demandées', DEPOTS: 'dépôts', DONNEES: 'données', ECHEANCE: 'échéance',
  ETAB: 'établissement', GENERATEUR: 'générateur', MODALITES: 'modalités',
  PREAVIS: 'préavis', REGIME: 'régime', REGIONALE: 'régionale', RESULTAT: 'résultat',
  TELESERVICES: 'téléservices',
};

/**
 * Libelle lisible, derive du nom de la variable. Le PREFIXE (l'entite dont il
 * s'agit) est retire quand il est deja porte par le groupe d'affichage : sous
 * « Bailleur », le champ s'appelle « Nom », pas « Bailleur nom ».
 */
function libelleDe(nom, prefixe) {
  let segments = nom.split('_');
  if (prefixe) {
    const p = prefixe.split('_');
    if (p.every((x, i) => segments[i] === x)) segments = segments.slice(p.length);
  }
  if (!segments.length) segments = nom.split('_');
  const mots = segments.map((s) => MOTS[s] ?? s.toLowerCase());
  const phrase = mots.join(' ');
  return phrase.charAt(0).toUpperCase() + phrase.slice(1);
}

/** `BAILLEUR_NOM` -> `bailleurNom`. Cle de payload cote front et backend. */
const cleDe = (nom) => nom.toLowerCase().replace(/_([a-z0-9])/g, (_, c) => c.toUpperCase());

/** Lit le dictionnaire : une entree par variable, avec sa section et sa ligne. */
function lireDictionnaire() {
  const texte = fs.readFileSync(DICTIONNAIRE, 'utf8');
  const parVariable = new Map();
  let section = '';
  let sousSection = '';
  for (const brute of texte.split('\n')) {
    const ligne = brute.trim();
    const mSec = /^##\s+(.*)$/.exec(ligne);
    if (mSec) { section = mSec[1].replace(/\*\*/g, '').trim(); sousSection = ''; continue; }
    const mSous = /^###\s+(.*)$/.exec(ligne);
    if (mSous) { sousSection = mSous[1].replace(/\*\*/g, '').trim(); continue; }
    if (!ligne.startsWith('-')) continue;

    // Nom de boucle : « Boucle `X` : » ou « boucle `X` ».
    const mBoucle = /[Bb]oucle\s+`([A-Z0-9_]+)`/.exec(ligne);

    // OPTIONS D'UNE CASE A COCHER — attachees a LA variable qu'elles suivent.
    //
    // Une ligne du dictionnaire peut enumerer vingt variables et ne porter des
    // options que pour l'une d'elles :
    //   « Boucle BENEFICIAIRES_EFFECTIFS : $BE_NUMERO, ..., $BE_GENRE (case a
    //     cocher « Masculin » / « Feminin »), $BE_DATE_NAISSANCE, ... »
    // Attribuer ces deux options a toutes les variables de la ligne rendrait la
    // date de naissance choisissable entre « Masculin » et « Feminin ».
    // On decoupe donc la ligne par variable et on ne lit que le SEGMENT qui suit
    // chacune, jusqu'a la variable suivante.
    const occurrences = [...ligne.matchAll(/`\$([A-Z0-9_]+)`/g)];
    for (let i = 0; i < occurrences.length; i++) {
      const nom = occurrences[i][1];
      if (parVariable.has(nom)) continue;
      const debut = occurrences[i].index + occurrences[i][0].length;
      const fin = i + 1 < occurrences.length ? occurrences[i + 1].index : ligne.length;
      const segment = ligne.slice(debut, fin);
      // Une ligne qui ne nomme QU'UNE variable porte ses options apres le tiret ;
      // au-dela, on n'accepte que ce qui suit immediatement la variable.
      const portee = occurrences.length === 1 ? ligne : segment;
      const estCase = /case à cocher|valeurs admises/i.test(portee);
      const options = [...portee.matchAll(/«\s*([^»]+?)\s*»/g)].map((m) => m[1]);
      parVariable.set(nom, {
        section, sousSection,
        aide: ligne.replace(/^-+\s*/, '').replace(/`/g, '').trim(),
        options: estCase && options.length >= 2 ? options : null,
        boucle: mBoucle ? mBoucle[1] : null,
      });
    }
  }
  return parVariable;
}

/** Lit l'analyse d'ecart du lot A : une categorie par variable. */
function lireEcart() {
  const texte = fs.readFileSync(ECART, 'utf8');
  const cat = new Map();
  let courante = null;
  for (const brute of texte.split('\n')) {
    const ligne = brute.trim();
    const mSec = /^##\s+(.+?)\s*\(\d+\)\s*$/.exec(ligne);
    if (mSec) {
      const t = mSec[1].toLowerCase();
      courante = t.includes('deja resolue') ? 'RESOLUE'
        : t.includes('derivable') ? 'DERIVABLE'
        : t.includes('a saisir') ? 'SAISIE'
        : t.includes('sans source') ? 'SANS_SOURCE'
        : t.includes('renomme') ? 'RENOMMEE' : null;
      continue;
    }
    if (!courante) continue;
    if (courante === 'RESOLUE') {
      if (ligne.startsWith('$')) {
        for (const n of ligne.split(',')) {
          const v = n.trim().replace(/^\$/, '');
          if (/^[A-Z0-9_]+$/.test(v)) cat.set(v, 'RESOLUE');
        }
      }
      continue;
    }
    const mVar = /^\|\s*`\$([A-Z0-9_]+)`\s*\|/.exec(ligne);
    if (mVar) cat.set(mVar[1], courante);
  }
  return cat;
}

function main() {
  const dico = lireDictionnaire();
  const optionsDesModeles = lireOptionsDesModeles();
  const categorie = lireEcart();
  const manifeste = JSON.parse(fs.readFileSync(MANIFESTE, 'utf8'));
  const modeles = new Map(
    manifeste.templates.filter((t) => t.workflow === 'CREATION_SARL').map((t) => [t.code, t]));

  return readWorkbook(CLASSEUR).then((wb) => {
    const parcours = Object.values(wb)[0];

    // --- 1. Document -> ligne du parcours -----------------------------------
    const documents = [];
    const vus = new Set();
    for (const r of parcours) {
      const num = (r?.[COL.numero] ?? '').trim();
      if (!/^\d+$/.test(num)) continue;
      const cellule = (r[COL.modele] ?? '').trim();
      if (!cellule || cellule === '—' || cellule.startsWith('—')) continue;

      // « STATUTS_SARL / STATUTS_SARL_AU (livrés) » -> deux codes.
      // « ANNONCE_LEGALE (constitution) (livré) »   -> un code, via l'alias.
      const codes = cellule
        .replace(/\([^)]*\)/g, ' ')
        .split('/')
        .map((x) => x.trim().replace(/\s+/g, '_').toUpperCase())
        .filter((x) => /^[A-Z0-9_]+$/.test(x) && x.length > 3)
        .map((x) => ALIAS_MODELE[x] ?? x);

      // « (livré — emploi « récapitulatif ») » : le bordereau sert trois emplois,
      // pilotes par $BORDEREAU_OBJET. On les porte au catalogue plutot que de les
      // coder en dur, puisque le parcours les nomme.
      const mEmploi = /emploi\s+«\s*([^»]+?)\s*»/.exec(cellule);
      const emploi = mEmploi ? mEmploi[1] : null;

      for (const code of codes) {
        if (!modeles.has(code)) {
          throw new Error('Ligne ' + num + ' : modele « ' + code
            + ' » absent du manifeste CREATION_SARL (cellule : « ' + cellule + ' »)');
        }
        const n = Number(num);
        const statut = (r[COL.statut] ?? '').trim();
        const existant = documents.find((d) => d.code === code);
        if (existant) {
          // Le document est deja connu : cette ligne est une REPRISE — un depot,
          // une mise a jour, un autre emploi. On ne cree pas un second document.
          existant.reprises.push({ ligne: n, statut, emploi,
            libelle: (r[COL.documentProduit] ?? '').trim() });
          continue;
        }
        vus.add(code);
        documents.push({
          code,
          ligneGeneration: n,
          statutGeneration: statut,
          libelle: (r[COL.documentProduit] ?? '').trim(),
          condition: (r[COL.condition] ?? '').trim(),
          cochePar_defaut: COCHES_PAR_DEFAUT.has(code),
          emploi,
          reprises: [],
        });
      }
    }
    for (const [code, info] of Object.entries(HORS_PARCOURS)) {
      if (documents.some((d) => d.code === code)) continue;
      documents.push({
        code, ligneGeneration: null, statutGeneration: info.statut,
        libelle: modeles.get(code)?.document_kind ?? code,
        condition: info.condition, cochePar_defaut: false,
        emploi: null, reprises: [], note: info.note,
      });
    }

    // --- 1 bis. Les DIX choix du statut 2 -----------------------------------
    //
    // Une ligne du parcours peut porter DEUX modeles : bail ou domiciliation,
    // SARL ou SARL AU. Ce n'est pas un choix de l'employe — c'est la voie retenue
    // pour le siege, et c'est $ASSOCIE_UNIQUE. L'employe coche donc DIX cases,
    // pas douze, et la variante se decide toute seule.
    const choixStatut2 = [];
    for (const r of parcours) {
      const num = (r?.[COL.numero] ?? '').trim();
      if (!/^\d+$/.test(num)) continue;
      const n = Number(num);
      if (n < 2 || n > 11) continue;
      const codes = documents
        .filter((d) => d.ligneGeneration === n)
        .map((d) => d.code)
        .sort();
      if (!codes.length) {
        throw new Error('Ligne ' + n + ' du statut 2 : aucun modele rattache.');
      }
      choixStatut2.push({
        ligne: n,
        libelle: (r[COL.libelle] ?? '').trim().replace(/^\d+\.\s*/, ''),
        documentProduit: (r[COL.documentProduit] ?? '').trim(),
        condition: (r[COL.condition] ?? '').trim(),
        cochePar_defaut: codes.some((c) => COCHES_PAR_DEFAUT.has(c)),
        codes,
      });
    }
    if (choixStatut2.length !== 10) {
      throw new Error('Dix documents attendus au statut 2, ' + choixStatut2.length + ' trouves.');
    }
    const nbDefaut = choixStatut2.filter((c) => c.cochePar_defaut).length;
    if (nbDefaut !== 2) {
      throw new Error('Deux documents coches par defaut attendus (statuts, annonce legale), '
        + nbDefaut + ' trouves.');
    }
    const orphelins = [...modeles.keys()].filter((c) => !documents.some((d) => d.code === c));
    if (orphelins.length) {
      throw new Error('Modeles du manifeste rattaches a aucune ligne : ' + orphelins.join(', '));
    }

    // --- 2. Variable -> documents qui la consomment -------------------------
    const consommateurs = new Map();   // variable -> Set(code document)
    const boucleDe = new Map();        // variable -> nom de boucle du manifeste
    for (const [code, t] of modeles) {
      for (const v of t.variables) {
        if (!consommateurs.has(v)) consommateurs.set(v, new Set());
        consommateurs.get(v).add(code);
      }
      for (const b of t.blocks ?? []) {
        for (const v of b.variables) {
          if (!consommateurs.has(v)) consommateurs.set(v, new Set());
          consommateurs.get(v).add(code);
          boucleDe.set(v, b.name);
        }
      }
    }

    // --- 3. Les champs : uniquement les « a saisir » ------------------------
    const champs = [];
    const sansSource = [];
    const derivables = [];
    const aSaisirSansConsommateur = [];

    for (const [variable, cat] of [...categorie.entries()].sort()) {
      if (cat === 'RENOMMEE' || cat === 'RESOLUE') continue;
      const docs = [...(consommateurs.get(variable) ?? [])].sort();
      const d = dico.get(variable) ?? {};
      if (cat === 'DERIVABLE') { derivables.push({ variable, documents: docs, ...d }); continue; }
      if (cat === 'SANS_SOURCE') { sansSource.push({ variable, documents: docs, ...d }); continue; }

      if (!docs.length) { aSaisirSansConsommateur.push(variable); continue; }

      const boucle = boucleDe.get(variable) ?? d.boucle ?? null;
      const prefixe = boucle ? prefixeDeBoucle(boucle, variable) : null;
      // Les options du MODELE priment sur celles du dictionnaire : ce sont elles
      // que le moteur compare, caractere pour caractere.
      const options = optionsDesModeles.get(variable) ?? d.options ?? null;
      champs.push({
        variable,
        cle: cleDe(variable),
        label: libelleDe(variable, prefixe),
        type: typeDe(variable, options),
        options,
        aide: d.aide ?? null,
        section: d.section ?? null,
        documents: docs,
        boucle,
      });
    }

    // --- 3 bis. Les saisies heritees du lot 5 -------------------------------
    //
    // Elles ne sont pas dans les 160 : le lot A les classe « deja resolues ».
    // Elles n'en restent pas moins des SAISIES, et sans champ personne ne les
    // renseigne. On les ouvre, avec la cle que le mapper lit deja.
    for (const [variable, cle] of Object.entries(SAISIES_HERITEES)) {
      const docs = [...(consommateurs.get(variable) ?? [])].sort();
      if (!docs.length) continue;
      if (champs.some((c) => c.variable === variable)) continue;
      const d = dico.get(variable) ?? {};
      const options = optionsDesModeles.get(variable) ?? d.options ?? null;
      champs.push({
        variable,
        cle,
        label: libelleDe(variable, null),
        type: typeDe(variable, options),
        options,
        aide: d.aide ?? null,
        section: d.section ?? null,
        documents: docs,
        boucle: null,
        heritee: true,
      });
    }
    champs.sort((a, b) => a.variable.localeCompare(b.variable));

    // --- 4. Boucles ---------------------------------------------------------
    const boucles = new Map();
    for (const c of champs) {
      if (!c.boucle) continue;
      if (!boucles.has(c.boucle)) {
        boucles.set(c.boucle, { nom: c.boucle, label: libelleDeBoucle(c.boucle),
                                documents: new Set(), champs: [] });
      }
      const b = boucles.get(c.boucle);
      c.documents.forEach((d) => b.documents.add(d));
      b.champs.push(c.cle);
    }

    const catalogue = {
      genere_le: '2026-09-11',
      genere_par: 'scripts/lotB/derive-champs-creation.mjs',
      source_parcours: '1. Parcours creation.xlsx (9 septembre 2026)',
      documents: documents.sort((a, b) =>
        (a.ligneGeneration ?? 999) - (b.ligneGeneration ?? 999)),
      choix_statut_2: choixStatut2,
      champs,
      boucles: [...boucles.values()].map((b) => ({ ...b, documents: [...b.documents].sort() })),
      sans_source: sansSource.map((x) => ({
        variable: x.variable, documents: x.documents,
        section: x.section ?? null, aide: x.aide ?? null })),
      derivables: derivables.map((x) => ({ variable: x.variable, documents: x.documents })),
    };

    fs.mkdirSync(path.dirname(SORTIE_JSON), { recursive: true });
    fs.writeFileSync(SORTIE_JSON, JSON.stringify(catalogue, null, 2) + '\n', 'utf8');
    ecrireTs(catalogue);
    ecrireRapports(catalogue, categorie, aSaisirSansConsommateur);

    const horsBoucle = champs.filter((c) => !c.boucle).length;
    console.log(documents.length + ' documents (' + choixStatut2.length
      + ' choix au statut 2), ' + champs.length + ' champs ouverts ('
      + horsBoucle + ' simples, ' + (champs.length - horsBoucle) + ' en boucle sur '
      + boucles.size + ' boucles)');
    console.log(sansSource.length + ' sans source, ' + derivables.length + ' derivables, '
      + aSaisirSansConsommateur.length + ' a saisir sans consommateur');
  });
}

/** `BENEFICIAIRES_EFFECTIFS` + `$BE_NOM` -> prefixe `BE` a retirer du libelle. */
function prefixeDeBoucle(boucle, variable) {
  const segments = variable.split('_');
  const initiales = boucle.split('_').map((m) => m[0]).join('');
  if (segments[0] === initiales) return segments[0];
  const singulier = boucle.replace(/S$/, '').split('_')[0];
  if (segments[0] === singulier) return segments[0];
  return null;
}

function libelleDeBoucle(nom) {
  const mots = nom.split('_').map((s) => MOTS[s] ?? s.toLowerCase());
  const phrase = mots.join(' ');
  return phrase.charAt(0).toUpperCase() + phrase.slice(1);
}

function ecrireTs(cat) {
  const l = [];
  l.push('/* eslint-disable */');
  l.push('/**');
  l.push(' * FICHIER GENERE — ne pas editer a la main.');
  l.push(' *   node scripts/lotB/derive-champs-creation.mjs');
  l.push(' *');
  l.push(' * Le catalogue des documents du parcours de creation et des champs que');
  l.push(" * chacun reclame. La regle qu'il sert, posee par l'utilisateur :");
  l.push(" * **un champ n'apparait que si le document qui le consomme est retenu.**");
  l.push(' *');
  l.push(' * Un champ servant PLUSIEURS documents est declare une seule fois, avec la');
  l.push(' * liste des documents qui le reclament, et ne s affiche qu une fois — sous le');
  l.push(' * premier document retenu qui en a besoin.');
  l.push(' *');
  l.push(' * Aucun champ n est marque obligatoire : le refus de generer sur un blanc au');
  l.push(' * milieu d une phrase est prononce par le SERVEUR, sur le document rendu');
  l.push(' * (ControleCompletude). Voir l en-tete du script pour le detail.');
  l.push(' */');
  l.push('');
  l.push("export type ChampType = 'text' | 'date' | 'select' | 'textarea' | 'number';");
  l.push('');
  l.push('export interface RepriseDocument {');
  l.push('  /** Ligne du parcours ou le document reparait. */');
  l.push('  ligne: number;');
  l.push('  statut: string;');
  l.push('  /** Emploi nomme par le parcours (bordereau : recapitulatif / remise / restitution). */');
  l.push('  emploi: string | null;');
  l.push('  libelle: string;');
  l.push('}');
  l.push('');
  l.push('export interface DocumentParcours {');
  l.push('  /** Code du modele au manifeste. */');
  l.push('  code: string;');
  l.push('  /** Ligne du parcours qui le GENERE, ou null (hors parcours). */');
  l.push('  ligneGeneration: number | null;');
  l.push('  statutGeneration: string;');
  l.push('  libelle: string;');
  l.push("  /** Condition d'application, telle qu'elle figure au parcours. */");
  l.push('  condition: string;');
  l.push('  /** Statuts et Annonce legale seulement — decision du cabinet. */');
  l.push('  cocheParDefaut: boolean;');
  l.push('  emploi: string | null;');
  l.push('  /**');
  l.push('   * Les autres lignes du parcours ou le MEME document reparait : son depot');
  l.push('   * aupres de l administration, sa mise a jour, ou un autre de ses emplois.');
  l.push('   * Ce ne sont pas des documents distincts — un seul fichier, plusieurs temps.');
  l.push('   */');
  l.push('  reprises: RepriseDocument[];');
  l.push('  note?: string;');
  l.push('}');
  l.push('');
  l.push('export interface ChoixDocument {');
  l.push('  ligne: number;');
  l.push('  libelle: string;');
  l.push('  documentProduit: string;');
  l.push('  condition: string;');
  l.push('  cocheParDefaut: boolean;');
  l.push('  /** Une ou deux variantes : bail / domiciliation, SARL / SARL AU. */');
  l.push('  codes: string[];');
  l.push('}');
  l.push('');
  l.push('export interface ChampCreation {');
  l.push('  /** Variable du corpus, sans le $. */');
  l.push('  variable: string;');
  l.push('  /** Cle envoyee au backend sous `payload.creation`. */');
  l.push('  cle: string;');
  l.push('  label: string;');
  l.push('  type: ChampType;');
  l.push('  /** Valeurs admises, reprises MOT POUR MOT des cases du modele. */');
  l.push('  options: string[] | null;');
  l.push('  /** La ligne du dictionnaire qui decrit la variable, verbatim. */');
  l.push('  aide: string | null;');
  l.push('  section: string | null;');
  l.push('  /** Codes des documents qui consomment ce champ. */');
  l.push('  documents: string[];');
  l.push('  /** Nom de la boucle du modele, si le champ se repete. */');
  l.push('  boucle: string | null;');
  l.push('  /**');
  l.push("   * Saisie HERITEE du lot 5 : le lot A la classe « deja resolue » — et elle");
  l.push("   * l'est, le mapper sait la lire — mais sa SOURCE est une saisie. Sans");
  l.push('   * champ, plus personne ne la renseigne.');
  l.push('   */');
  l.push('  heritee?: boolean;');
  l.push('}');
  l.push('');
  l.push('export interface BoucleCreation {');
  l.push('  nom: string;');
  l.push('  label: string;');
  l.push('  documents: string[];');
  l.push('  /** Cles des champs de la boucle, dans l ordre du catalogue. */');
  l.push('  champs: string[];');
  l.push('}');
  l.push('');
  l.push('export const DOCUMENTS_PARCOURS: DocumentParcours[] = '
    + JSON.stringify(cat.documents.map((d) => ({
        code: d.code, ligneGeneration: d.ligneGeneration,
        statutGeneration: d.statutGeneration,
        libelle: d.libelle, condition: d.condition,
        cocheParDefaut: d.cochePar_defaut, emploi: d.emploi, reprises: d.reprises,
        ...(d.note ? { note: d.note } : {}),
      })), null, 2) + ';');
  l.push('');
  l.push('/**');
  l.push(' * LES DIX DOCUMENTS DU STATUT 2, tels que le parcours les numerote de 1 a 10.');
  l.push(' * Statuts et Annonce legale sont coches par defaut ; les huit autres sont');
  l.push(" * decoches, et l'employe choisit — avec, sous les yeux, la condition");
  l.push(' * d application que le parcours enonce. Le systeme ne decide pas a sa place.');
  l.push(' */');
  l.push('export const CHOIX_STATUT_2: ChoixDocument[] = '
    + JSON.stringify(cat.choix_statut_2.map((c) => ({
        ligne: c.ligne, libelle: c.libelle, documentProduit: c.documentProduit,
        condition: c.condition, cocheParDefaut: c.cochePar_defaut, codes: c.codes,
      })), null, 2) + ';');
  l.push('');
  l.push('export const CHAMPS_CREATION: ChampCreation[] = '
    + JSON.stringify(cat.champs, null, 2) + ';');
  l.push('');
  l.push('export const BOUCLES_CREATION: BoucleCreation[] = '
    + JSON.stringify(cat.boucles, null, 2) + ';');
  l.push('');
  l.push('/**');
  l.push(' * Les variables du corpus qu AUCUNE donnee du dossier ne peut alimenter :');
  l.push(" * donnee d'un tiers (bailleur, domiciliataire, commissaire aux apports) ou");
  l.push(' * delivree par une administration apres depot. Aucun champ n est ouvert pour');
  l.push(' * elles — c est au cabinet de dire qui les releve, et quand. Elles remontent');
  l.push(' * au controle de completude, document par document.');
  l.push(' */');
  l.push('export const VARIABLES_SANS_SOURCE: string[] = '
    + JSON.stringify(cat.sans_source.map((x) => x.variable), null, 2) + ';');
  l.push('');
  l.push('/**');
  l.push(' * Champs reellement a afficher, compte tenu des documents retenus.');
  l.push(' * Un champ servant plusieurs documents n apparait qu une fois, sous le premier');
  l.push(' * document retenu qui le reclame. Les champs de boucle en sont exclus : ils');
  l.push(' * s affichent par la boucle, pas un par un.');
  l.push(' */');
  l.push('export function champsParDocument(');
  l.push('  codesRetenus: string[],');
  l.push('): { code: string; champs: ChampCreation[] }[] {');
  l.push('  const deja = new Set<string>();');
  l.push('  const out: { code: string; champs: ChampCreation[] }[] = [];');
  l.push('  for (const code of codesRetenus) {');
  l.push('    const champs = CHAMPS_CREATION.filter(');
  l.push('      (c) => !c.boucle && c.documents.includes(code) && !deja.has(c.cle),');
  l.push('    );');
  l.push('    champs.forEach((c) => deja.add(c.cle));');
  l.push('    if (champs.length) out.push({ code, champs });');
  l.push('  }');
  l.push('  return out;');
  l.push('}');
  l.push('');
  l.push('/** Boucles a afficher, compte tenu des documents retenus. */');
  l.push('export function bouclesParDocument(codesRetenus: string[]): BoucleCreation[] {');
  l.push('  const retenus = new Set(codesRetenus);');
  l.push('  return BOUCLES_CREATION.filter((b) => b.documents.some((d) => retenus.has(d)));');
  l.push('}');
  l.push('');
  fs.mkdirSync(path.dirname(SORTIE_TS), { recursive: true });
  fs.writeFileSync(SORTIE_TS, l.join('\n'), 'utf8');
}

function ecrireRapports(cat, categorie, sansConsommateur) {
  const total = [...categorie.values()].filter((c) => c === 'SAISIE').length;
  const horsBoucle = cat.champs.filter((c) => !c.boucle);
  const enBoucle = cat.champs.filter((c) => c.boucle);

  const parDocument = new Map();
  for (const c of cat.champs) {
    for (const d of c.documents) {
      if (!parDocument.has(d)) parDocument.set(d, 0);
      parDocument.set(d, parDocument.get(d) + 1);
    }
  }

  const l = [];
  l.push('# Lot B — les champs reellement ouverts au parcours');
  l.push('');
  l.push('Genere par `scripts/lotB/derive-champs-creation.mjs`.');
  l.push('');
  l.push('| | Nombre |');
  l.push('|---|---:|');
  const heritees = cat.champs.filter((c) => c.heritee).length;
  l.push('| Variables « a saisir » au lot A | **' + total + '** |');
  l.push('| Champs ouverts | **' + cat.champs.length + '** |');
  l.push('| dont issus des 160 | ' + (cat.champs.length - heritees) + ' |');
  l.push('| dont saisies heritees du lot 5 (« deja resolues », mais saisies) | ' + heritees + ' |');
  l.push('| dont champs simples | ' + horsBoucle.length + ' |');
  l.push('| dont champs de boucle | ' + enBoucle.length + ' (sur ' + cat.boucles.length + ' boucles) |');
  l.push('| Variables « a saisir » sans document consommateur | **' + sansConsommateur.length + '** |');
  l.push('| Variables derivables — aucun champ ouvert | **' + cat.derivables.length + '** |');
  l.push('| Variables sans source — aucun champ ouvert | **' + cat.sans_source.length + '** |');
  l.push('');
  if (sansConsommateur.length) {
    l.push('## Les « a saisir » qu aucun gabarit ne consomme');
    l.push('');
    l.push('Le lot A les classait a saisir, mais **aucun des 23 gabarits ne les emploie** :');
    l.push('ouvrir un champ pour elles produirait une question sans effet. Elles sont');
    l.push('signalees, pas ouvertes.');
    l.push('');
    for (const v of sansConsommateur) l.push('- `$' + v + '`');
    l.push('');
  }
  l.push('## Repartition par document');
  l.push('');
  l.push('| Document | Genere ligne | Reprises | Coche par defaut | Champs reclames |');
  l.push('|---|---:|---|---|---:|');
  for (const d of cat.documents) {
    l.push('| `' + d.code + '` | ' + (d.ligneGeneration ?? '—') + ' | '
      + (d.reprises.length ? d.reprises.map((r) => r.ligne
          + (r.emploi ? ' (' + r.emploi + ')' : '')).join(', ') : '—') + ' | '
      + (d.cochePar_defaut ? '**oui**' : 'non') + ' | '
      + (parDocument.get(d.code) ?? 0) + ' |');
  }
  l.push('');
  l.push('## Les dix documents du statut 2');
  l.push('');
  l.push('| # | Document | Condition d application | Coche par defaut |');
  l.push('|---:|---|---|---|');
  for (const c of cat.choix_statut_2) {
    l.push('| ' + c.ligne + ' | ' + c.libelle + ' | ' + (c.condition || '—') + ' | '
      + (c.cochePar_defaut ? '**oui**' : 'non') + ' |');
  }
  l.push('');
  l.push('## Les boucles');
  l.push('');
  l.push('Un champ de boucle ne se saisit pas une fois mais autant de fois qu il y a');
  l.push('d occurrences — un bailleur, trois beneficiaires effectifs, cinq traitements de');
  l.push('donnees. Le parcours les presente comme des groupes repetables.');
  l.push('');
  l.push('| Boucle | Documents | Champs |');
  l.push('|---|---|---:|');
  for (const b of cat.boucles) {
    l.push('| `' + b.nom + '` | ' + b.documents.map((d) => '`' + d + '`').join(', ')
      + ' | ' + b.champs.length + ' |');
  }
  l.push('');
  fs.mkdirSync(path.dirname(RAPPORT_CHAMPS), { recursive: true });
  fs.writeFileSync(RAPPORT_CHAMPS, l.join('\n'), 'utf8');

  const s = [];
  s.push('# Lot B — les ' + cat.sans_source.length
    + ' variables sans source, portees au cabinet');
  s.push('');
  s.push('**Aucun champ n a ete ouvert pour ces variables, et aucune valeur n a ete');
  s.push('inventee.** Elles n ont aucune donnee du dossier pour les alimenter : ce sont');
  s.push("des donnees d'un TIERS (bailleur, domiciliataire, commissaire aux apports) ou");
  s.push("delivrees par une ADMINISTRATION apres depot (recepisses, references de depot,");
  s.push('decisions).');
  s.push('');
  s.push('Elles restent non resolues, remontent par le controle de completude document');
  s.push('par document, et **c est au cabinet de dire si la donnee doit etre saisie ou si');
  s.push('la rubrique reste vide**.');
  s.push('');
  const parSection = new Map();
  for (const v of cat.sans_source) {
    const k = v.section ?? 'Sans section';
    if (!parSection.has(k)) parSection.set(k, []);
    parSection.get(k).push(v);
  }
  for (const [section, vars] of [...parSection.entries()].sort()) {
    s.push('## ' + section + ' — ' + vars.length);
    s.push('');
    s.push('| Variable | Documents concernes |');
    s.push('|---|---|');
    for (const v of vars) {
      s.push('| `$' + v.variable + '` | '
        + (v.documents.length ? v.documents.map((d) => '`' + d + '`').join(', ')
                              : '*aucun gabarit*') + ' |');
    }
    s.push('');
  }
  fs.writeFileSync(RAPPORT_SANS_SOURCE, s.join('\n'), 'utf8');
}

main().catch((e) => { console.error(e); process.exit(1); });
