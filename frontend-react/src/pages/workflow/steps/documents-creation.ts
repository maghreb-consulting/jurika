/**
 * LES DOCUMENTS DU PARCOURS DE CRÉATION, ET CE QU'ILS RÉCLAMENT.
 *
 * Principe posé par l'utilisateur, et qui n'a pas changé depuis le lot 5 :
 * **un champ n'apparaît que si le document qui le consomme est retenu.** Un
 * employé qui ne génère pas la déclaration CNDP ne doit jamais voir de question
 * sur la finalité d'un traitement de données.
 *
 * ---------------------------------------------------------------------------
 * LOT B (2026-09-11) — CE FICHIER NE DÉCRIT PLUS RIEN, IL RÉ-EXPORTE.
 *
 * Le lot 5 décrivait ici sept modèles et une quinzaine de champs, à la main.
 * Le corpus du 9 septembre en compte vingt-trois et **160 champs à ouvrir** :
 * les écrire à la main garantirait la dérive, puisque le cabinet livrera
 * d'autres versions du corpus.
 *
 * Le catalogue est donc **dérivé** — du parcours (quelle ligne produit quel
 * document, sous quelle condition), du manifeste (quel document consomme quelle
 * variable) et de l'analyse d'écart du lot A (quelle variable est à saisir) :
 *
 *     node scripts/lotB/derive-champs-creation.mjs
 *
 * Ce fichier reste le point d'entrée — c'est lui que l'étape 7 importe — et il
 * porte ce que la dérivation ne peut pas produire : la liste, écrite pour être
 * lue à l'écran, de ce que les modèles savent déjà et ne redemandent jamais.
 */

export type {
  BoucleCreation,
  ChampCreation,
  ChampType,
  ChoixDocument,
  DocumentParcours,
  RepriseDocument,
} from './documents-creation.generated';

export {
  BOUCLES_CREATION,
  bouclesParDocument,
  CHAMPS_CREATION,
  champsParDocument,
  CHOIX_STATUT_2,
  DOCUMENTS_PARCOURS,
  VARIABLES_SANS_SOURCE,
} from './documents-creation.generated';

/**
 * Ce que les modèles lisent SANS rien redemander. Affiché en lecture à l'étape
 * 7 : c'est la preuve, à l'écran, qu'aucune donnée déjà saisie n'est
 * redemandée — la règle que l'utilisateur rappelle depuis le début.
 *
 * La liste n'est pas dérivable : elle nomme des ENSEMBLES de variables par
 * l'étape qui les produit, là où le catalogue raisonne variable par variable.
 * Elle est tenue à jour avec les six premières étapes du workflow, pas avec le
 * corpus.
 */
export const REPRISES_AUTOMATIQUES: { libelle: string; source: string }[] = [
  { libelle: 'Dénomination, sigle, ICE, IF, certificat négatif', source: 'étape 1' },
  {
    libelle: 'Siège social, ville, ville du greffe (et le type de tribunal, qui s’en déduit)',
    source: 'étape 2',
  },
  { libelle: 'Capital, nombre de parts, durée (et la date de fin, qui se calcule)', source: 'étape 3' },
  { libelle: 'Objet social — activité principale et activités secondaires', source: 'étape 4' },
  {
    libelle: 'Gérants : identité, CIN, adresse, date ET lieu de naissance, qualité, déclarant',
    source: 'étape 5',
  },
  { libelle: 'Associés, apports, répartition des parts, associé principal (art. 26 CGI)', source: 'étape 6' },
  {
    libelle:
      'Souscriptions et versements — déduits des associés et de la valeur nominale, jamais ressaisis',
    source: 'calculé',
  },
  { libelle: 'Numéro de dossier, date d’ouverture, chargé de dossier', source: 'le ticket' },
];
