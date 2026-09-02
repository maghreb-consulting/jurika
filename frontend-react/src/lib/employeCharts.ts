/**
 * Derivations PURES pour les graphiques du tableau de bord EMPLOYE.
 *
 * Ces fonctions ne font AUCUN appel reseau : elles transforment les donnees
 * DEJA chargees et scopees a l'employe connecte (ses tickets, ses echeances)
 * en series pretes pour Recharts. Aucune donnee factice : une entree vide
 * produit un tableau vide -> l'UI affiche un etat "Aucune donnee".
 */
import type { Ticket, TicketStatut } from '../types/ticket';
import type { EcheanceItem } from '../types/dashboard';
import { STATUT_TICKET_LABELS, TYPE_TICKET_LABELS, labelOf } from './dashboardLabels';

export interface Slice {
  /** Cle brute (statut/type backend) — pour le mapping couleur. */
  key: string;
  /** Libelle affiche (FR). */
  label: string;
  count: number;
}

/** Ordre canonique des statuts pour un rendu stable du donut. */
const STATUT_ORDER: TicketStatut[] = ['NOUVEAU', 'EN_COURS', 'CLOTURE', 'ANNULE'];

/**
 * Repartition de MES tickets par statut. Ordre canonique, statuts a 0 exclus
 * (un donut n'affiche pas de tranche vide). Retour [] si aucun ticket.
 */
export function ticketsByStatut(tickets: Ticket[]): Slice[] {
  if (!tickets || tickets.length === 0) return [];
  const counts = new Map<string, number>();
  for (const t of tickets) counts.set(t.statut, (counts.get(t.statut) ?? 0) + 1);
  return STATUT_ORDER.filter((s) => (counts.get(s) ?? 0) > 0).map((s) => ({
    key: s,
    label: labelOf(STATUT_TICKET_LABELS, s),
    count: counts.get(s) ?? 0,
  }));
}

/**
 * Repartition de MES tickets par type de workflow, triee par count decroissant.
 * Retour [] si aucun ticket.
 */
export function ticketsByType(tickets: Ticket[]): Slice[] {
  if (!tickets || tickets.length === 0) return [];
  const counts = new Map<string, number>();
  for (const t of tickets) counts.set(t.type, (counts.get(t.type) ?? 0) + 1);
  return [...counts.entries()]
    .map(([key, count]) => ({ key, label: labelOf(TYPE_TICKET_LABELS, key), count }))
    .sort((a, b) => b.count - a.count);
}

export interface WeekBucket {
  label: string;
  count: number;
}

/**
 * Regroupe MES echeances des 30 prochains jours par semaine (S1..S5).
 * - Les echeances deja passees (date < aujourd'hui) sont ignorees.
 * - `now` injectable pour testabilite (defaut = maintenant).
 * - Retourne toujours 5 buckets (S1..S5) DES QU'IL EXISTE au moins une
 *   echeance a venir dans la fenetre, afin de dessiner une frise coherente ;
 *   sinon [] (etat vide).
 */
export function echeancesByWeek(echeances: EcheanceItem[], now: Date = new Date()): WeekBucket[] {
  if (!echeances || echeances.length === 0) return [];
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const DAY = 24 * 60 * 60 * 1000;
  const buckets = [0, 0, 0, 0, 0];
  let any = false;
  for (const e of echeances) {
    if (!e.dateEcheance) continue;
    const d = new Date(e.dateEcheance).getTime();
    if (Number.isNaN(d)) continue;
    const diffDays = Math.floor((d - startOfToday) / DAY);
    if (diffDays < 0 || diffDays > 29) continue; // hors fenetre 30 jours
    const week = Math.min(4, Math.floor(diffDays / 7));
    buckets[week] += 1;
    any = true;
  }
  if (!any) return [];
  return buckets.map((count, i) => ({ label: `Sem. ${i + 1}`, count }));
}
