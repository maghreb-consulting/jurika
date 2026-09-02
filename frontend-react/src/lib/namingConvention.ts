/**
 * Prompt G (2026-06-23) — convention de renommage UNIQUE des documents
 * importés / déposés dans la Data Room.
 *
 * Miroir TypeScript du helper Java {@code DocumentNamingConvention} côté
 * dataroom-service. Permet au front de générer le nom canonique avant upload
 * sans aller-retour réseau.
 *
 * Formats :
 *   JURIDIQUE : <DOCUMENT_TYPE>__<DENOMINATION_SLUG>__<YYYY-MM-DD|YYYY>.<ext>
 *   COMPTABLE : <ANNEE>/<CATEGORIE_COMPTABLE>__<DENOMINATION_SLUG>.<ext>
 *   FISCAL    : <ANNEE>/<CATEGORIE_FISCALE>__<DENOMINATION_SLUG>.<ext>
 */

/** Normalise une dénomination en slug MAJUSCULES sans accents (uniquement [A-Z0-9_]). */
export function slugDenomination(denomination: string | null | undefined): string {
  if (denomination == null) return 'SOCIETE';
  const trimmed = denomination.trim();
  if (!trimmed) return 'SOCIETE';
  // 1) Décomposer les accents (é → e + ́) puis retirer les marques.
  const stripped = trimmed.normalize('NFD').replace(/\p{M}+/gu, '');
  // 2) Majuscules.
  const upper = stripped.toUpperCase();
  // 3) Tout caractère hors [A-Z0-9] → underscore, compactage + trim.
  const cleaned = upper
    .replace(/[^A-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
  return cleaned || 'SOCIETE';
}

/** Extrait l'extension d'un nom de fichier (sans le point), fallback "bin". */
export function extensionOf(filename: string | null | undefined): string {
  if (!filename) return 'bin';
  const dot = filename.lastIndexOf('.');
  if (dot <= 0 || dot >= filename.length - 1) return 'bin';
  return normalizeExtension(filename.substring(dot + 1));
}

function normalizeExtension(extension: string | null | undefined): string {
  if (!extension) return 'bin';
  let e = extension.trim().toLowerCase();
  if (e.startsWith('.')) e = e.substring(1);
  if (!e) return 'bin';
  return e.replace(/[^a-z0-9]/g, '');
}

function sanitizeTokenUpper(s: string | null | undefined, fallback: string): string {
  if (!s || !s.trim()) return fallback;
  const t = s.trim().toUpperCase().replace(/[^A-Z0-9_]/g, '_').replace(/_+/g, '_').replace(/^_+|_+$/g, '');
  return t || fallback;
}

/** JURIDIQUE — `<TYPE>__<SLUG>__<DATE|YEAR>.<ext>`. */
export function forJuridique(opts: {
  documentType: string;
  denominationSlug: string;
  dateOrYear?: string | null;
  extension: string;
}): string {
  const type = sanitizeTokenUpper(opts.documentType, 'AUTRE');
  const slug = opts.denominationSlug?.trim() || 'SOCIETE';
  const ext = normalizeExtension(opts.extension);
  let name = `${type}__${slug}`;
  if (opts.dateOrYear && opts.dateOrYear.trim()) {
    name += `__${opts.dateOrYear.trim()}`;
  }
  return `${name}.${ext}`;
}

/** COMPTABLE — `<ANNEE>/<CAT>__<SLUG>.<ext>`. */
export function forComptable(opts: {
  annee: number | null;
  categorie: string;
  denominationSlug: string;
  extension: string;
}): string {
  return yearedPath(opts.annee, opts.categorie, opts.denominationSlug, opts.extension);
}

/** FISCAL — `<ANNEE>/<CAT>__<SLUG>.<ext>`. */
export function forFiscal(opts: {
  annee: number | null;
  categorie: string;
  denominationSlug: string;
  extension: string;
}): string {
  return yearedPath(opts.annee, opts.categorie, opts.denominationSlug, opts.extension);
}

function yearedPath(
  annee: number | null,
  categorie: string,
  denominationSlug: string,
  extension: string,
): string {
  const year = annee == null ? '0000' : String(annee).padStart(4, '0');
  const cat = sanitizeTokenUpper(categorie, 'AUTRE');
  const slug = denominationSlug?.trim() || 'SOCIETE';
  const ext = normalizeExtension(extension);
  return `${year}/${cat}__${slug}.${ext}`;
}
