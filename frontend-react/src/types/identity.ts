// Types miroir du DTO backend ExtractedIdentityDto.

/** Type métier de la pièce d'identité. */
export type IdentityType = 'ANCIENNE' | 'NOUVELLE' | 'CN';

/** Source de l'extraction côté backend. */
export type ExtractionSource = 'kie' | 'merged';

/**
 * Champs extraits. Tous sont optionnels — l'extraction peut être partielle
 * et l'employé reste libre de tout corriger.
 */
export interface IdentityFields {
  // Personne physique (CIN ancienne ou nouvelle)
  nom?: string;
  prenom?: string;
  cin?: string;
  date_naissance?: string;
  lieu_naissance?: string;
  date_validite?: string;
  sexe?: string;
  adresse?: string;
  nationalite?: string;
  // Carte / registre (CN)
  numero_cn?: string;
  denomination?: string;
  ice?: string;
  beneficiaire?: string;
  activite?: string;
  tribunal?: string;
  date_expiration?: string;
  date_delivrance?: string;
  // Garde l'écho du type, comme renvoyé par le backend
  doc_type?: string;
  // Permet d'absorber tout champ inattendu sans casser le typage strict
  [key: string]: string | undefined;
}

export interface ExtractedIdentity {
  type: IdentityType;
  fields: IdentityFields;
  source: ExtractionSource | string;
  warnings: string[];
  archivedDocumentId: string | null;
}

export interface ExtractIdentityParams {
  recto: File;
  /** Verso : ignoré si type === 'CN'. */
  verso?: File | null;
  type: IdentityType;
  /** Si fourni avec archive=true, le PDF recto+verso est archivé en Data Room. */
  dossierId?: string | null;
  archive?: boolean;
}

/**
 * Convertit une date du format FR du backend (`DD.MM.YYYY`, normalisé côté
 * Python par {@code validation.py}) vers le format ISO (`YYYY-MM-DD`)
 * attendu par les inputs HTML `<input type="date">`. Sans cette conversion
 * une valeur pré-remplie issue de l'extraction reste invisible dans le champ
 * date.
 *
 * - Si la valeur respecte déjà le format ISO (`YYYY-MM-DD`), elle est rendue telle quelle.
 * - Si elle respecte le format FR (`DD.MM.YYYY` ou `DD/MM/YYYY` ou `DD-MM-YYYY`),
 *   elle est convertie en ISO.
 * - Sinon (chaîne vide, format inattendu) la valeur est rendue telle quelle :
 *   on ne casse jamais une saisie manuelle existante.
 *
 * @param value chaîne à convertir.
 * @returns valeur ISO ou la valeur originale si non reconnue.
 */
export function toIsoDate(value: string | undefined): string {
  if (!value) return '';
  const trimmed = value.trim();
  // Format ISO déjà valide.
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) return trimmed;
  // Format FR : DD.MM.YYYY / DD/MM/YYYY / DD-MM-YYYY.
  const fr = trimmed.match(/^(\d{2})[./-](\d{2})[./-](\d{4})$/);
  if (fr) return `${fr[3]}-${fr[2]}-${fr[1]}`;
  // Format inattendu (ex. extraction incomplète) — on laisse tel quel.
  return trimmed;
}
