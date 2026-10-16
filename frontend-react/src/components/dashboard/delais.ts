import type { BadgeVariant } from '../ui/Badge';

/**
 * Lot L6b (E17) : lecture commune des delais affiches par les tableaux de bord (delais legaux
 * des demarches, dates limites des tickets). Le mot "echeance" est reserve a la facturation :
 * ici on parle de delai et de date limite.
 */

/** Ecart en jours entre une date ISO (AAAA-MM-JJ, ou instant ISO) et aujourd'hui (heure locale). */
export function joursRestants(date: string, aujourdHui: Date): number {
  const [a, m, j] = date.slice(0, 10).split('-').map(Number);
  const cible = Date.UTC(a, m - 1, j);
  const ref = Date.UTC(aujourdHui.getFullYear(), aujourdHui.getMonth(), aujourdHui.getDate());
  return Math.round((cible - ref) / 86_400_000);
}

/** Libelle et ton du badge d'un delai : en retard (danger), sous 3 jours (attention), plus tard. */
export function etatDelai(n: number): { texte: string; variante: BadgeVariant } {
  if (n < 0) return { texte: `En retard de ${-n} jour${n < -1 ? 's' : ''}`, variante: 'danger' };
  if (n === 0) return { texte: 'Aujourd’hui', variante: 'danger' };
  if (n <= 3) return { texte: `Dans ${n} jour${n > 1 ? 's' : ''}`, variante: 'warning' };
  return { texte: `Dans ${n} jours`, variante: 'neutral' };
}

/** Date longue en francais ("11 octobre 2026") d'une date ISO, sans decalage de fuseau. */
export function dateLongue(date: string): string {
  const [a, m, j] = date.slice(0, 10).split('-').map(Number);
  return new Date(a, m - 1, j).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
}

/** Date courte en francais ("11 oct.") d'une date ISO, sans decalage de fuseau. */
export function dateCourte(date: string): string {
  const [a, m, j] = date.slice(0, 10).split('-').map(Number);
  return new Date(a, m - 1, j).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
}
