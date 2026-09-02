/**
 * Convention de nommage des documents générés par le workflow MODIFICATION
 * (Phase 7, 2026-08-10) :
 *
 *   `type_du_document - dénomination_entreprise - forme_juridique ( - v<n> )`
 *
 * Exemples :
 *   - `PV — Modification (AGE) - PARACOSME - SARL.docx`
 *   - `Statuts - PARACOSME - SARL - v2.docx`
 *
 * Le suffixe `- v<n>` n'est ajouté QUE lorsqu'un document de même type + nom existe
 * déjà (versioning maîtrisé) — `version` porte alors le n° de la NOUVELLE version.
 */

/** Nettoie une portion de nom de fichier (retire les caractères interdits par les OS). */
export function sanitizeNamePart(raw: string | null | undefined): string {
  return (raw || '')
    .replace(/[\\/:*?"<>|]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 120);
}

/** Libellé lisible de la forme juridique pour le nom de fichier. */
export function formeLabel(forme: string | null | undefined): string {
  const f = (forme || '').toUpperCase();
  if (f === 'SARL_AU' || f.includes('UNIQUE')) return 'SARL AU';
  if (f === 'SARL') return 'SARL';
  return sanitizeNamePart(forme) || 'SARL';
}

/**
 * Construit le nom de fichier `type - dénomination - forme (- v<n>)` + `.docx`.
 * @param version n° de la NOUVELLE version, présent uniquement si un document de
 *   même type+nom existe déjà (sinon `undefined` → pas de suffixe).
 */
export function buildDocFilename(
  docType: string,
  denomination: string | null | undefined,
  forme: string | null | undefined,
  version?: number | null,
  ext: string = '.docx',
): string {
  const denom = sanitizeNamePart(denomination) || 'Societe';
  const parts = [sanitizeNamePart(docType) || 'Document', denom, formeLabel(forme)];
  let name = parts.join(' - ');
  if (version != null && version > 1) name += ` - v${version}`;
  const dotExt = ext ? (ext.startsWith('.') ? ext : `.${ext}`) : '';
  return `${name}${dotExt}`;
}

/** Extension (avec le point) d'un nom de fichier, ou `.docx` par défaut. */
export function extensionOf(filename: string | null | undefined): string {
  const m = /\.([A-Za-z0-9]{1,8})$/.exec(filename || '');
  return m ? `.${m[1].toLowerCase()}` : '.docx';
}

/** Libellé « type de document » déduit du code template (pour le nommage). */
export function docTypeForTemplateCode(code: string): string {
  if (code.startsWith('PV_MODIFICATION')) return 'PV — Modification (AGE)';
  if (code.startsWith('STATUTS_')) return 'Statuts';
  if (code.startsWith('CONVOCATION')) return 'Convocation';
  if (code.startsWith('FEUILLE_PRESENCE')) return 'Feuille de présence';
  if (code.includes('DEFAUT_QUORUM')) return 'PV — Défaut de quorum';
  if (code.includes('IRREGULARITE')) return 'PV — Irrégularité de convocation';
  if (code.includes('JAL') || code.includes('ANNONCE')) return 'Annonce légale';
  return code;
}
