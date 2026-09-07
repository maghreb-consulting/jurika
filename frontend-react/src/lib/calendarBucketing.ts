import type { Ticket, TicketPriorite } from '../types/ticket';

/**
 * Calendrier des tickets (2026-06-25) — utilitaires purs de groupement.
 *
 * Source de verite = `ticket.deadline` (l'echeance propre du ticket, source A),
 * PAS la table `deadlines` (source B, severite) ni les echeances fiscales
 * (source C). Ces helpers sont volontairement sans dependance React pour rester
 * testables unitairement (bucketing par jour + priorite).
 */

/** Couleurs + label par priorite — calque sur SEVERITY_TOKENS de la vue echeances. */
export const PRIORITE_TOKENS: Record<
  TicketPriorite,
  { dot: string; chip: string; ring: string; label: string }
> = {
  URGENTE: {
    dot: 'bg-rose-600',
    chip: 'bg-rose-600/15 text-rose-700 border-rose-200',
    ring: 'ring-rose-300',
    label: 'Urgente',
  },
  HAUTE: {
    dot: 'bg-amber-500',
    chip: 'bg-amber-500/15 text-amber-800 border-amber-200',
    ring: 'ring-amber-300',
    label: 'Haute',
  },
  NORMALE: {
    dot: 'bg-sky-500',
    chip: 'bg-sky-500/15 text-sky-700 border-sky-200',
    ring: 'ring-sky-300',
    label: 'Normale',
  },
  BASSE: {
    dot: 'bg-slate-400',
    chip: 'bg-slate-400/15 text-slate-600 border-slate-200',
    ring: 'ring-slate-300',
    label: 'Basse',
  },
};

/** Ordre d'affichage / tri : la plus urgente d'abord. */
export const PRIORITE_ORDER: TicketPriorite[] = ['URGENTE', 'HAUTE', 'NORMALE', 'BASSE'];

const PRIORITE_RANK: Record<TicketPriorite, number> = {
  URGENTE: 0,
  HAUTE: 1,
  NORMALE: 2,
  BASSE: 3,
};

/** Statuts terminaux : on les garde dans le calendrier mais grises (option du brief). */
export function isTicketTermine(t: Ticket): boolean {
  return t.statut === 'CLOTURE_DOSSIER' || t.statut === 'ANNULE';
}

/** Cle de jour locale stable (annee-mois-jour) a partir d'une date ISO. */
export function dayKey(d: Date): string {
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}

export interface DayBucket {
  /** Tickets de ce jour, tries par priorite decroissante puis heure d'echeance. */
  tickets: Ticket[];
  /** Compteur par priorite (toutes priorites presentes, ordre PRIORITE_ORDER). */
  counts: Record<TicketPriorite, number>;
  /** Nombre total de tickets dus ce jour. */
  total: number;
}

function emptyCounts(): Record<TicketPriorite, number> {
  return { URGENTE: 0, HAUTE: 0, NORMALE: 0, BASSE: 0 };
}

/**
 * Groupe les tickets par jour de `deadline`, puis par priorite.
 * Les tickets sans deadline sont exclus. Tri intra-jour : priorite (URGENTE
 * d'abord) puis heure d'echeance croissante.
 */
export function bucketTicketsByDay(tickets: Ticket[]): Map<string, DayBucket> {
  const map = new Map<string, DayBucket>();

  for (const t of tickets) {
    if (!t.deadline) continue;
    const d = new Date(t.deadline);
    if (Number.isNaN(d.getTime())) continue;
    const key = dayKey(d);
    const bucket = map.get(key) ?? { tickets: [], counts: emptyCounts(), total: 0 };
    bucket.tickets.push(t);
    bucket.counts[t.priorite] += 1;
    bucket.total += 1;
    map.set(key, bucket);
  }

  for (const bucket of map.values()) {
    bucket.tickets.sort((a, b) => {
      const r = PRIORITE_RANK[a.priorite] - PRIORITE_RANK[b.priorite];
      if (r !== 0) return r;
      const ta = a.deadline ? new Date(a.deadline).getTime() : 0;
      const tb = b.deadline ? new Date(b.deadline).getTime() : 0;
      return ta - tb;
    });
  }

  return map;
}
