/**
 * Lot L6b (E18) : derivations PURES des series des graphiques des tableaux de bord, a partir des
 * listes deja chargees (tickets, depots...). Aucun appel reseau, aucune valeur inventee : une
 * date absente ne compte nulle part, un mois sans evenement vaut 0 (c'est un comptage).
 */
import { TYPE_TICKET_LABELS, formatMonth, labelOf } from '../../lib/dashboardLabels';
import type { Ticket } from '../../types/ticket';

/** Cle AAAA-MM d'une date ISO, en heure locale. */
export function cleMois(dateIso: string): string {
  const d = new Date(dateIso);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

/** Les n derniers mois (AAAA-MM), du plus ancien au mois courant. */
export function moisRecents(n: number, aujourdHui: Date = new Date()): string[] {
  return Array.from({ length: n }, (_, i) => {
    const d = new Date(aujourdHui.getFullYear(), aujourdHui.getMonth() - (n - 1 - i), 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });
}

/**
 * Comptage par mois de plusieurs series de dates, sur les n derniers mois : une ligne par mois,
 * { mois: "oct. 26", [serie]: nombre }.
 */
export function comptesParMois(
  series: Record<string, Array<string | null | undefined>>,
  n: number,
  aujourdHui: Date = new Date(),
): Array<Record<string, string | number>> {
  const mois = moisRecents(n, aujourdHui);
  return mois.map((m) => {
    const ligne: Record<string, string | number> = { mois: formatMonth(m) };
    for (const [cle, dates] of Object.entries(series)) {
      ligne[cle] = dates.filter((d): d is string => !!d && cleMois(d) === m).length;
    }
    return ligne;
  });
}

/** Repartition de tickets par type d'operation, libelles francais, du plus frequent au moins. */
export function ticketsParType(tickets: Ticket[]): Array<{ key: string; label: string; count: number }> {
  const m = new Map<string, number>();
  for (const t of tickets) m.set(t.type, (m.get(t.type) ?? 0) + 1);
  return [...m.entries()]
    .map(([key, count]) => ({ key, label: labelOf(TYPE_TICKET_LABELS, key), count }))
    .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label));
}

/** Statuts d'un ticket ouvert, dans l'ordre du parcours. */
export const STATUTS_OUVERTS = ['CREATION_TICKET', 'GENERATION_DOCUMENTS', 'DEROULEMENT_DEMARCHE'] as const;

export function estOuvert(t: Pick<Ticket, 'statut'>): boolean {
  return (STATUTS_OUVERTS as readonly string[]).includes(t.statut);
}

/** Cumuls successifs a partir d'une base : cumuls(5, [2, 0, 3]) = [7, 7, 10]. */
export function cumuls(base: number, valeurs: number[]): number[] {
  return valeurs.reduce<number[]>((acc, v) => [...acc, (acc.length ? acc[acc.length - 1] : base) + v], []);
}
