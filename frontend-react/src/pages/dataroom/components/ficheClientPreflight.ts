import type { DossierJuridiqueView } from '../../../types/dataroom';

/**
 * Fiche client (2026-07-14) — preflight des identifiants.
 *
 * Calcule les champs d'identite manquants PERTINENTS au vu du statut du dossier.
 * En EN_CONSTITUTION, les identifiants obtenus APRES immatriculation (RC, IF,
 * patente, CNSS) ne sont pas signales comme manquants (absence normale).
 * Le preflight ne bloque jamais la generation — il informe seulement.
 */
export interface IdentityField {
  key: keyof DossierJuridiqueView;
  label: string;
  /** true = identifiant obtenu apres immatriculation (ignore en EN_CONSTITUTION). */
  postImmat: boolean;
}

export const IDENTITY_FIELDS: IdentityField[] = [
  { key: 'ice', label: 'ICE', postImmat: false },
  { key: 'rcNumero', label: 'Numéro RC', postImmat: true },
  { key: 'rcTribunal', label: 'Tribunal du RC', postImmat: true },
  { key: 'identifiantFiscal', label: 'Identifiant fiscal', postImmat: true },
  { key: 'taxeProfessionnelle', label: 'Taxe professionnelle (patente)', postImmat: true },
  { key: 'cnss', label: 'CNSS', postImmat: true },
  { key: 'adresseSiege', label: 'Adresse du siège', postImmat: false },
  { key: 'ville', label: 'Ville', postImmat: false },
  { key: 'capitalSocialMad', label: 'Capital social', postImmat: false },
  { key: 'dateConstitution', label: 'Date de constitution', postImmat: false },
];

export function computeMissingIdentity(
  view: DossierJuridiqueView,
): IdentityField[] {
  const enConstitution = view.statut === 'EN_CONSTITUTION';
  return IDENTITY_FIELDS.filter((f) => {
    if (enConstitution && f.postImmat) return false; // absence normale
    const v = view[f.key];
    return v === null || v === undefined || v === '';
  });
}
