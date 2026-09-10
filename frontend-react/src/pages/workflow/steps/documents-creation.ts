/**
 * Lot 5 (2026-09-07) — LES SIX DOCUMENTS DE L'ÉTAPE 7, ET CE QU'ILS RÉCLAMENT.
 *
 * Principe posé par l'utilisateur : **un champ n'apparaît que si le document qui
 * le consomme est retenu.** Un employé qui ne génère pas la déclaration
 * d'existence ne doit jamais voir de question sur le régime de TVA.
 *
 * Ce fichier ne décrit QUE les données réellement nouvelles. Tout ce que les six
 * modèles savent déjà lire — dénomination, siège, capital, objet social, gérants,
 * associés, certificat négatif, sigle, ICE, IF, ville du greffe — vient des étapes
 * 1 à 6 et n'est jamais redemandé (cf. {@link REPRISES_AUTOMATIQUES}).
 *
 * Un champ peut servir PLUSIEURS documents (le téléphone de la société figure sur
 * les deux imprimés DGI). Il est alors déclaré une seule fois, avec la liste des
 * documents qu'il sert, et n'est affiché qu'une fois — sous le premier document
 * retenu qui en a besoin.
 */

/**
 * ⚠ LOT A (2026-09-10) — CE FICHIER DÉCRIT UN CORPUS QUI N'EXISTE PLUS.
 *
 * Les 7 modèles ci-dessous ont été retirés du manifeste et remplacés par les 23
 * gabarits livrés par le cabinet le 9 septembre. Trois codes ont survécu
 * (`DEMANDE_TAXE_PROFESSIONNELLE`, `DECLARATION_EXISTENCE`,
 * `DECLARATION_IMMATRICULATION_RC`), mais leurs gabarits sont neufs et bien plus
 * fournis : les champs décrits ici ne couvrent plus ce qu'ils réclament.
 *
 * Rien n'est cassé pour autant : le backend ne route plus le workflow
 * `CREATION_SARL`, l'étape 7 reçoit une liste de modèles vide, et aucun de ces
 * champs n'est donc affiché.
 *
 * **À REFAIRE AU LOT B**, sur l'analyse d'écart des 404 variables
 * (`output/2026-09-10_lotA_ecart_404_variables.md`) : 160 données sont à saisir,
 * contre les quelques-unes décrites ici. Le fichier est conservé pour son
 * principe — un champ n'apparaît que si le document qui le consomme est retenu —
 * et pour la liste des reprises automatiques, qui, elle, reste juste.
 */

/** Codes des six documents proposés à l'étape 7 du workflow CRÉATION. */
export const DOC_STATUTS_SARL = 'STATUTS_SARL_DIRECTEUR';
export const DOC_STATUTS_SARL_AU = 'STATUTS_SARL_AU_DIRECTEUR';
export const DOC_ACTE_NOMINATION = 'ACTE_NOMINATION_GERANT_DIRECTEUR';
export const DOC_ANNONCE_LEGALE = 'ANNONCE_LEGALE_DIRECTEUR';
export const DOC_DEMANDE_TP = 'DEMANDE_TAXE_PROFESSIONNELLE';
export const DOC_DECLARATION_EXISTENCE = 'DECLARATION_EXISTENCE';
export const DOC_DECLARATION_RC = 'DECLARATION_IMMATRICULATION_RC';

export type ChampType = 'text' | 'date' | 'select' | 'textarea';

export interface ChampComplementaire {
  /** Clé envoyée au backend sous `payload.formulaires`. */
  key: string;
  label: string;
  type: ChampType;
  /** Valeurs admises — reprises MOT POUR MOT des cases du modèle du directeur. */
  options?: string[];
  /** Codes des documents qui consomment ce champ. */
  documents: string[];
  aide?: string;
}

/**
 * Les champs complémentaires, dans l'ordre où on les demande.
 *
 * TOUS sont **optionnels** : aucun n'est imprimé au milieu d'une phrase, ils
 * alimentent uniquement des cases d'imprimés administratifs. Le contrôle de
 * complétude côté serveur applique la même règle et laisse passer une case vide —
 * il refuse en revanche tout blanc qui se lirait dans une phrase.
 */
export const CHAMPS_COMPLEMENTAIRES: ChampComplementaire[] = [
  // — Coordonnées de la société : les deux imprimés DGI les réclament ————
  {
    key: 'directionRegionale',
    label: 'Direction régionale DGI',
    type: 'text',
    documents: [DOC_DEMANDE_TP, DOC_DECLARATION_EXISTENCE],
    aide: 'Direction régionale, provinciale ou (inter) préfectorale dont dépend le siège.',
  },
  {
    key: 'subdivision',
    label: 'Subdivision DGI',
    type: 'text',
    documents: [DOC_DEMANDE_TP, DOC_DECLARATION_EXISTENCE],
  },
  {
    key: 'telephone',
    label: 'Téléphone de la société',
    type: 'text',
    documents: [DOC_DEMANDE_TP, DOC_DECLARATION_EXISTENCE],
  },
  {
    key: 'fax',
    label: 'Fax de la société',
    type: 'text',
    documents: [DOC_DEMANDE_TP, DOC_DECLARATION_EXISTENCE],
  },
  {
    key: 'email',
    label: 'E-mail de la société',
    type: 'text',
    documents: [DOC_DEMANDE_TP, DOC_DECLARATION_EXISTENCE],
  },
  {
    key: 'dateDebutActivite',
    label: "Date de début d'activité",
    type: 'date',
    documents: [DOC_DEMANDE_TP, DOC_DECLARATION_RC],
    aide: "Sert aussi de « date de commencement d'exploitation » sur le modèle 2.",
  },

  // — Régime d'imposition (déclaration d'existence) ————————————————
  {
    key: 'regimeResultat',
    label: 'Régime de détermination du résultat',
    type: 'select',
    options: ['Résultat net réel', 'Résultat net simplifié', 'Contribution professionnelle unique'],
    documents: [DOC_DECLARATION_EXISTENCE],
    aide: "Société soumise à l'IS : le résultat net réel s'applique en principe.",
  },
  {
    key: 'tvaAssujettissement',
    label: 'Assujettissement à la TVA',
    type: 'select',
    options: [
      'De plein droit',
      'Sur option',
      'Hors champ',
      'Totalement exonéré sans droit à déduction',
      'Totalement exonéré avec droit à déduction',
    ],
    documents: [DOC_DECLARATION_EXISTENCE],
  },
  {
    key: 'tvaFaitGenerateur',
    label: 'Fait générateur de la TVA',
    type: 'select',
    options: ['Encaissement', 'Débit'],
    documents: [DOC_DECLARATION_EXISTENCE],
  },
  {
    key: 'tvaPeriodicite',
    label: 'Périodicité de déclaration de la TVA',
    type: 'select',
    options: ['Mensuelle', 'Trimestrielle'],
    documents: [DOC_DECLARATION_EXISTENCE],
  },
  {
    key: 'activiteNature',
    label: "Nature de l'activité",
    type: 'select',
    options: ['Permanente', 'Saisonnière', 'Périodique', 'Occasionnelle'],
    documents: [DOC_DECLARATION_EXISTENCE],
  },
  {
    key: 'associePrincipalIf',
    label: "N° d'identification fiscale de l'associé principal",
    type: 'text',
    documents: [DOC_DECLARATION_EXISTENCE],
    aide: "L'associé principal (art. 26 CGI) est déduit du nombre de parts — son nom, sa CIN et son adresse viennent de l'étape 6.",
  },
  {
    key: 'associePrincipalVille',
    label: "Ville de l'associé principal",
    type: 'text',
    documents: [DOC_DECLARATION_EXISTENCE],
  },
  {
    key: 'associePrincipalTel',
    label: "Téléphone de l'associé principal",
    type: 'text',
    documents: [DOC_DECLARATION_EXISTENCE],
  },
  {
    key: 'associePrincipalEmail',
    label: "E-mail de l'associé principal",
    type: 'text',
    documents: [DOC_DECLARATION_EXISTENCE],
  },

  // — Registre du commerce (modèle 2) ————————————————————————
  {
    key: 'enseigne',
    label: 'Enseigne commerciale',
    type: 'text',
    documents: [DOC_DECLARATION_RC],
    aide: "Facultative — le sigle, lui, vient de l'étape 1.",
  },
  {
    key: 'piecesProduites',
    label: 'Pièces produites au greffe',
    type: 'textarea',
    documents: [DOC_DECLARATION_RC],
    aide: 'Liste jointe à la déclaration (statuts enregistrés, certificat négatif, contrat de siège…).',
  },
];

/**
 * Ce que les six documents lisent SANS rien redemander. Affiché en lecture à
 * l'étape 7 : c'est la preuve, à l'écran, qu'aucune donnée déjà saisie n'est
 * redemandée — la règle que l'utilisateur rappelle depuis le début.
 */
export const REPRISES_AUTOMATIQUES: { libelle: string; source: string }[] = [
  { libelle: 'Dénomination, sigle, ICE, IF, certificat négatif', source: 'étape 1' },
  { libelle: 'Siège social, ville, ville du greffe (et le type de tribunal, qui s’en déduit)', source: 'étape 2' },
  { libelle: 'Capital, nombre de parts, durée (et la date de fin, qui se calcule)', source: 'étape 3' },
  { libelle: 'Objet social — activité principale et activités secondaires', source: 'étape 4' },
  { libelle: 'Gérants : identité, CIN, adresse, date ET lieu de naissance, qualité, déclarant', source: 'étape 5' },
  { libelle: 'Associé principal au sens de l’art. 26 CGI (le plus grand nombre de parts)', source: 'étape 6' },
];

/**
 * Champs complémentaires réellement à afficher, compte tenu des documents retenus.
 * Un champ servant plusieurs documents n'apparaît qu'une fois, sous le premier
 * document retenu qui le réclame.
 */
export function champsParDocument(
  codesRetenus: string[],
): { code: string; champs: ChampComplementaire[] }[] {
  const deja = new Set<string>();
  const out: { code: string; champs: ChampComplementaire[] }[] = [];
  for (const code of codesRetenus) {
    const champs = CHAMPS_COMPLEMENTAIRES.filter(
      (c) => c.documents.includes(code) && !deja.has(c.key),
    );
    champs.forEach((c) => deja.add(c.key));
    if (champs.length) out.push({ code, champs });
  }
  return out;
}
