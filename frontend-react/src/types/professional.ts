/**
 * Type de profil declare a l'inscription (onboarding 2026-06-24).
 *
 * Miroir exact de l'enum backend `ProfessionalType` (auth-service). Les codes
 * sont envoyes tels quels (chaine majuscule) au POST /public/signup/cabinet ;
 * le backend valide contre la meme liste (CHECK constraint Postgres V29).
 */
export type ProfessionalType =
  | 'COMPTABLE_AGREE'
  | 'CONSEILLER_JURIDIQUE'
  | 'CENTRE_AFFAIRES'
  | 'EXPERT_COMPTABLE'
  | 'ENTREPRISE'
  | 'AVOCAT'
  | 'NOTAIRE'
  | 'AUTRE';

/** Libelles FR alignes sur l'enum backend (ProfessionalType.labelFr()). */
export const PROFESSIONAL_TYPE_LABELS: Record<ProfessionalType, string> = {
  COMPTABLE_AGREE: 'Comptable agree',
  CONSEILLER_JURIDIQUE: 'Conseiller juridique',
  CENTRE_AFFAIRES: "Centre d'affaires",
  EXPERT_COMPTABLE: 'Expert comptable',
  ENTREPRISE: 'Entreprise',
  AVOCAT: 'Avocat',
  NOTAIRE: 'Notaire',
  AUTRE: 'Autre',
};

/** Liste ordonnee pour l'affichage (cartes / liste deroulante). */
export const PROFESSIONAL_TYPES: { code: ProfessionalType; label: string }[] = (
  Object.keys(PROFESSIONAL_TYPE_LABELS) as ProfessionalType[]
).map((code) => ({ code, label: PROFESSIONAL_TYPE_LABELS[code] }));

/** True si la valeur appartient aux 8 types. */
export function isProfessionalType(value: string | null | undefined): value is ProfessionalType {
  return value != null && value in PROFESSIONAL_TYPE_LABELS;
}

/**
 * Libelle adaptatif de l'intitule "nom de l'entite" selon le type choisi.
 * Le champ sous-jacent reste `workspaceName` (raison sociale) — seul le libelle
 * change pour coller au vocabulaire metier de l'utilisateur.
 *  - Notaire           -> "Nom de l'etude"
 *  - Avocat / Expert comptable / Comptable agree -> "Nom du cabinet"
 *  - Entreprise / Conseiller juridique / Centre d'affaires / Autre / aucun -> "Raison sociale"
 */
export function entityNameLabel(type: ProfessionalType | null | undefined): string {
  switch (type) {
    case 'NOTAIRE':
      return "Nom de l'etude";
    case 'AVOCAT':
    case 'EXPERT_COMPTABLE':
    case 'COMPTABLE_AGREE':
      return 'Nom du cabinet';
    default:
      return 'Raison sociale';
  }
}

/**
 * Categorie du profil (onboarding 2026-07-27) : « structure » = personne morale
 * disposant necessairement d'une denomination propre ; « individuel » = personne
 * physique qui peut exercer en son nom propre.
 *
 * Seuls ENTREPRISE et CENTRE_AFFAIRES sont des structures. Tous les autres
 * (Avocat, Notaire, Conseiller juridique, Comptable agree, Expert comptable,
 * Autre) sont individuels et n'ont donc PAS forcement de denomination de
 * structure.
 */
const STRUCTURE_TYPES: ReadonlySet<ProfessionalType> = new Set<ProfessionalType>([
  'ENTREPRISE',
  'CENTRE_AFFAIRES',
]);

/**
 * True si le type impose une denomination de structure (raison sociale
 * obligatoire). False pour les profils individuels : le nom de structure est
 * alors optionnel et retombe sur « Prenom Nom » cote serveur si laisse vide.
 *
 * NB : un type null/inconnu est traite comme individuel (non bloquant) — la
 * selection du type reste requise en amont par le formulaire.
 */
export function entityNameRequired(type: ProfessionalType | null | undefined): boolean {
  return type != null && STRUCTURE_TYPES.has(type);
}
