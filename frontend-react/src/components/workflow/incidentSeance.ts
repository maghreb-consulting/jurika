/**
 * Incident de seance (lot L6) : fonction pure sortie d'IncidentSeancePanel.tsx, pour que ce
 * dernier n'exporte que des composants (rafraichissement a chaud de Vite).
 */
import { sanitizeFilename } from './seanceFormEtat';

/**
 * Point 3 (audit directeur) — nom de fichier lisible « Type + Dénomination » pour les
 * PV d'incident (au lieu du code technique backend). Pure &amp; testable.
 */
export function incidentPvFilename(
  code: string,
  denomination?: string | null,
): string | undefined {
  const denom = (denomination ?? '').toString().trim() || 'Société';
  const libelle = code.includes('DEFAUT_QUORUM')
    ? 'Défaut de quorum'
    : code.includes('IRREGULARITE_CONVOCATION')
      ? 'Irrégularité de convocation'
      : null;
  if (!libelle) return undefined;
  return sanitizeFilename(`PV — ${libelle} - ${denom}`) + '.docx';
}
