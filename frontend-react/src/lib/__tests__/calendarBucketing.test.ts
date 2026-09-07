import { describe, expect, it } from 'vitest';
import { bucketTicketsByDay, dayKey } from '../calendarBucketing';
import type { Ticket } from '../../types/ticket';

// 2026-06-25 — Calendrier tickets : groupement par jour de `deadline` + priorite.

function mkTicket(p: Partial<Ticket>): Ticket {
  return {
    id: p.id ?? 't',
    workspaceId: 'w1',
    reference: p.reference ?? 'TCK-001',
    titre: p.titre ?? 'Ticket',
    type: 'CREATION',
    statut: p.statut ?? 'CREATION_TICKET',
    priorite: p.priorite ?? 'NORMALE',
    dossierId: null,
    assigneId: null,
    creeParId: 'u1',
    description: null,
    deadline: p.deadline ?? null,
    annulationMotif: null,
    clotureAt: null,
    annuleAt: null,
    createdAt: '2026-06-01T00:00:00Z',
  };
}

describe('bucketTicketsByDay', () => {
  it('groupe les tickets par jour de deadline', () => {
    const tickets = [
      mkTicket({ id: 'a', deadline: '2026-06-25T09:00:00Z' }),
      mkTicket({ id: 'b', deadline: '2026-06-25T15:00:00Z' }),
      mkTicket({ id: 'c', deadline: '2026-06-27T10:00:00Z' }),
    ];
    const map = bucketTicketsByDay(tickets);
    expect(map.size).toBe(2);
    expect(map.get(dayKey(new Date('2026-06-25T09:00:00Z')))?.total).toBe(2);
    expect(map.get(dayKey(new Date('2026-06-27T10:00:00Z')))?.total).toBe(1);
  });

  it('exclut les tickets sans deadline (et les dates invalides)', () => {
    const tickets = [
      mkTicket({ id: 'a', deadline: null }),
      mkTicket({ id: 'b', deadline: 'pas-une-date' }),
      mkTicket({ id: 'c', deadline: '2026-06-25T09:00:00Z' }),
    ];
    const map = bucketTicketsByDay(tickets);
    expect(map.size).toBe(1);
    const bucket = map.get(dayKey(new Date('2026-06-25T09:00:00Z')));
    expect(bucket?.total).toBe(1);
    expect(bucket?.tickets[0].id).toBe('c');
  });

  it('compte la repartition par priorite dans un meme jour', () => {
    const day = '2026-06-25T12:00:00Z';
    const tickets = [
      mkTicket({ id: 'a', deadline: day, priorite: 'URGENTE' }),
      mkTicket({ id: 'b', deadline: day, priorite: 'URGENTE' }),
      mkTicket({ id: 'c', deadline: day, priorite: 'NORMALE' }),
      mkTicket({ id: 'd', deadline: day, priorite: 'BASSE' }),
    ];
    const bucket = bucketTicketsByDay(tickets).get(dayKey(new Date(day)))!;
    expect(bucket.total).toBe(4);
    expect(bucket.counts).toEqual({ URGENTE: 2, HAUTE: 0, NORMALE: 1, BASSE: 1 });
  });

  it('trie intra-jour par priorite decroissante (URGENTE en premier)', () => {
    const day = '2026-06-25T12:00:00Z';
    const tickets = [
      mkTicket({ id: 'basse', deadline: day, priorite: 'BASSE' }),
      mkTicket({ id: 'urgente', deadline: day, priorite: 'URGENTE' }),
      mkTicket({ id: 'normale', deadline: day, priorite: 'NORMALE' }),
      mkTicket({ id: 'haute', deadline: day, priorite: 'HAUTE' }),
    ];
    const bucket = bucketTicketsByDay(tickets).get(dayKey(new Date(day)))!;
    expect(bucket.tickets.map((t) => t.id)).toEqual(['urgente', 'haute', 'normale', 'basse']);
  });

  it('a priorite egale, trie par heure d echeance croissante', () => {
    const tickets = [
      mkTicket({ id: 'pm', deadline: '2026-06-25T15:00:00Z', priorite: 'HAUTE' }),
      mkTicket({ id: 'am', deadline: '2026-06-25T08:00:00Z', priorite: 'HAUTE' }),
    ];
    const bucket = bucketTicketsByDay(tickets).get(dayKey(new Date('2026-06-25T08:00:00Z')))!;
    expect(bucket.tickets.map((t) => t.id)).toEqual(['am', 'pm']);
  });
});
