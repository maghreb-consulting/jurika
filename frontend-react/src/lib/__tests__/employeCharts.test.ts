import { describe, expect, it } from 'vitest';
import { ticketsByStatut, ticketsByType, echeancesByWeek } from '../employeCharts';
import type { Ticket } from '../../types/ticket';
import type { EcheanceItem } from '../../types/dashboard';

function ticket(partial: Partial<Ticket>): Ticket {
  return {
    id: Math.random().toString(36).slice(2),
    workspaceId: 'w1',
    reference: 'REF',
    titre: 'T',
    type: 'CREATION',
    statut: 'NOUVEAU',
    priorite: 'NORMALE',
    dossierId: null,
    assigneId: 'u1',
    creeParId: 'u1',
    description: null,
    deadline: null,
    annulationMotif: null,
    clotureAt: null,
    annuleAt: null,
    createdAt: '2026-07-01',
    ...partial,
  };
}

function echeance(dateEcheance: string | null): EcheanceItem {
  return { id: Math.random().toString(36).slice(2), dossierId: 'd1', typeEcheance: 'TVA', dateEcheance, statut: 'PLANIFIEE' };
}

describe('ticketsByStatut', () => {
  it('renvoie [] pour une liste vide (etat vide)', () => {
    expect(ticketsByStatut([])).toEqual([]);
  });

  it('compte par statut dans l ordre canonique, sans tranche a 0', () => {
    const res = ticketsByStatut([
      ticket({ statut: 'NOUVEAU' }),
      ticket({ statut: 'EN_COURS' }),
      ticket({ statut: 'EN_COURS' }),
      ticket({ statut: 'CLOTURE' }),
    ]);
    expect(res).toEqual([
      { key: 'NOUVEAU', label: 'Nouveau', count: 1 },
      { key: 'EN_COURS', label: 'En cours', count: 2 },
      { key: 'CLOTURE', label: 'Clôturé', count: 1 },
    ]);
    // ANNULE (0) est exclu.
    expect(res.find((s) => s.key === 'ANNULE')).toBeUndefined();
  });
});

describe('ticketsByType', () => {
  it('renvoie [] pour une liste vide', () => {
    expect(ticketsByType([])).toEqual([]);
  });

  it('compte par type, trie par count decroissant, avec libelles FR', () => {
    const res = ticketsByType([
      ticket({ type: 'CREATION' }),
      ticket({ type: 'MODIFICATION' }),
      ticket({ type: 'MODIFICATION' }),
    ]);
    expect(res[0]).toEqual({ key: 'MODIFICATION', label: 'Modification', count: 2 });
    expect(res[1]).toEqual({ key: 'CREATION', label: 'Création', count: 1 });
  });
});

describe('echeancesByWeek', () => {
  const now = new Date(2026, 6, 1); // 2026-07-01 (mois index 6)

  it('renvoie [] quand aucune echeance', () => {
    expect(echeancesByWeek([], now)).toEqual([]);
  });

  it('renvoie [] quand toutes les echeances sont hors fenetre 30j', () => {
    expect(echeancesByWeek([echeance('2026-06-01'), echeance('2026-09-01')], now)).toEqual([]);
  });

  it('regroupe par semaine sur 5 buckets et ignore le passe / >30j', () => {
    const res = echeancesByWeek(
      [
        echeance('2026-07-01'), // J0  -> Sem.1
        echeance('2026-07-05'), // J4  -> Sem.1
        echeance('2026-07-09'), // J8  -> Sem.2
        echeance('2026-07-20'), // J19 -> Sem.3
        echeance('2026-06-15'), // passe -> ignore
        echeance(null), // sans date -> ignore
      ],
      now,
    );
    expect(res).toHaveLength(5);
    expect(res.map((b) => b.count)).toEqual([2, 1, 1, 0, 0]);
    expect(res[0].label).toBe('Sem. 1');
  });
});
