/**
 * Lot L4d (E6, décision du directeur du 11 octobre 2026) : références lisibles.
 *
 * - document : type, société, version (« Statuts — PARACOSME — version 2 ») ;
 * - dossier : année, numéro, société (« 2026-0007 — PARACOSME »), calculée par le serveur
 *   (numéro attribué par cabinet et par année, ticket V38) et seulement affichée ici.
 */
export function referenceDocument(typeLibelle: string, societe: string | null | undefined, version: number): string {
  return [typeLibelle.trim(), (societe ?? '').trim(), `version ${version}`].filter(Boolean).join(' — ');
}

/** Nom de fichier tiré de la référence : sans caractère interdit, extension conservée. */
export function nomFichierDocument(reference: string, filename: string | null | undefined): string {
  const m = /\.[A-Za-z0-9]{1,5}$/.exec(filename ?? '');
  const base = reference.replace(/[\\/:*?"<>|]+/g, ' ').replace(/\s+/g, ' ').trim();
  return base + (m ? m[0] : '');
}
