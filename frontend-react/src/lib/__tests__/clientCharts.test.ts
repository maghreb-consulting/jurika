import { describe, expect, it } from 'vitest';
import { demandesByStatut, requetesByStatut } from '../clientCharts';
import type { DemandeSummary, RequeteSummary } from '../../types/dataroom';

function demande(statut: string): DemandeSummary {
  return {
    id: Math.random().toString(36).slice(2), dossierId: 'd', soumisPar: 'c',
    sujet: 's', description: null, statut: statut as DemandeSummary['statut'],
    ticketId: null, prisEnChargePar: null, noteInterne: null, traiteAt: null,
    createdAt: '2026-07-01T00:00:00Z',
  };
}

function requete(statut: string): RequeteSummary {
  return {
    id: Math.random().toString(36).slice(2), dossierId: 'd', soumisPar: 'e',
    sujet: 's', description: null, statut: statut as RequeteSummary['statut'],
    ticketId: null, prisEnChargePar: null, noteInterne: null, traiteAt: null,
    createdAt: '2026-07-01T00:00:00Z', direction: 'EMPLOYE_TO_CLIENT',
    typeRequete: 'PIECE', reponduAt: null, clotureAt: null, noteClient: null,
  };
}

describe('demandesByStatut', () => {
  it('retourne [] pour une entree vide', () => {
    expect(demandesByStatut([])).toEqual([]);
  });

  it('compte par statut dans l ordre canonique, exclut les statuts a 0', () => {
    const slices = demandesByStatut([
      demande('NON_TRAITEE'), demande('NON_TRAITEE'), demande('TRAITEE'),
    ]);
    // EN_COURS (0) exclu ; ordre NON_TRAITEE puis TRAITEE.
    expect(slices.map((s) => s.key)).toEqual(['NON_TRAITEE', 'TRAITEE']);
    expect(slices.map((s) => s.count)).toEqual([2, 1]);
  });
});

describe('requetesByStatut', () => {
  it('retourne [] pour une entree vide', () => {
    expect(requetesByStatut([])).toEqual([]);
  });

  it('regroupe OUVERTE + A_COMPLETER dans « A faire »', () => {
    const slices = requetesByStatut([
      requete('OUVERTE'), requete('A_COMPLETER'), requete('REPONDUE'), requete('CLOTUREE'),
    ]);
    expect(slices.map((s) => s.key)).toEqual(['A_FAIRE', 'ATTENTE', 'TERMINE']);
    expect(slices.find((s) => s.key === 'A_FAIRE')?.count).toBe(2);
    expect(slices.find((s) => s.key === 'ATTENTE')?.count).toBe(1);
    expect(slices.find((s) => s.key === 'TERMINE')?.count).toBe(1);
  });

  it('exclut les groupes a 0', () => {
    const slices = requetesByStatut([requete('OUVERTE')]);
    expect(slices.map((s) => s.key)).toEqual(['A_FAIRE']);
  });
});
