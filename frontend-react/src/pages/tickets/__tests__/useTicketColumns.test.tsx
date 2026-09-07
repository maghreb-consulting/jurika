import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { PageResponse, Ticket, TicketStatut } from '../../../types/ticket';
import { ticketService } from '../../../services/ticket.service';
import {
  STATUTS_COLONNES,
  TAILLE_PAGE_COLONNE,
  useTicketColumns,
} from '../useTicketColumns';

/**
 * Lot 2 — le plafond de chargement.
 *
 * <p>L'ancienne page chargeait `limit: 200` tous statuts confondus. Le scenario
 * de reference : un cabinet installe, 137 tickets annules et 90 tickets actifs.
 * Les annulations sont les plus recentes (tri `createdAt desc`), elles
 * remplissaient donc la page a elles seules et le travail EN COURS disparaissait
 * du tableau. C'est ce que ces tests interdisent de reintroduire.
 */

vi.mock('../../../services/ticket.service', () => ({
  ticketService: { list: vi.fn() },
}));

const listMock = vi.mocked(ticketService.list);

/** Population de reference : 227 tickets au total, au-dela de l'ancien plafond. */
const POPULATION: Record<TicketStatut, number> = {
  CREATION_TICKET: 12,
  GENERATION_DOCUMENTS: 41,
  DEROULEMENT_DEMARCHE: 30,
  CLOTURE_DOSSIER: 7,
  ANNULE: 137,
};

function faux(statut: TicketStatut, i: number): Ticket {
  return {
    id: `${statut}-${i}`,
    workspaceId: 'ws-1',
    reference: `T-2026-${String(i).padStart(5, '0')}`,
    titre: `Ticket ${statut} ${i}`,
    type: 'CREATION',
    statut,
    priorite: 'NORMALE',
    dossierId: null,
    assigneId: null,
    creeParId: 'u-0',
    description: null,
    deadline: null,
    annulationMotif: null,
    clotureAt: null,
    annuleAt: null,
    createdAt: '2026-06-01T10:00:00Z',
    transferred: false,
  } as unknown as Ticket;
}

/** Backend fidele : il pagine, et il renvoie le total REEL, pas la taille de page. */
function backend(params: Parameters<typeof ticketService.list>[0] = {}) {
  const statut = (params.statuts?.[0] ?? 'CREATION_TICKET') as TicketStatut;
  const total = POPULATION[statut];
  const offset = params.offset ?? 0;
  const limit = params.limit ?? 20;
  const items = Array.from(
    { length: Math.max(0, Math.min(limit, total - offset)) },
    (_, i) => faux(statut, offset + i),
  );
  return Promise.resolve({ items, total, limit, offset } as PageResponse<Ticket>);
}

const SANS_FILTRE = { query: '', type: '', priorite: '', statut: '' } as const;

describe('useTicketColumns — pagination par colonne', () => {
  beforeEach(() => {
    listMock.mockReset();
    listMock.mockImplementation(backend);
  });

  it('interroge chaque statut separement, jamais en une seule page globale', async () => {
    const { result } = renderHook(() => useTicketColumns({ ...SANS_FILTRE }));
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(listMock).toHaveBeenCalledTimes(STATUTS_COLONNES.length);
    const statutsDemandes = listMock.mock.calls.map((c) => c[0]?.statuts?.[0]);
    expect([...statutsDemandes].sort()).toEqual([...STATUTS_COLONNES].sort());
    // Aucun appel « tous statuts confondus » : c'etait la cause du plafond.
    for (const call of listMock.mock.calls) {
      expect(call[0]?.statuts).toHaveLength(1);
      expect(call[0]?.limit).toBe(TAILLE_PAGE_COLONNE);
    }
  });

  it('aucune colonne active ne perd de tickets a cause des 137 annules', async () => {
    const { result } = renderHook(() => useTicketColumns({ ...SANS_FILTRE }));
    await waitFor(() => expect(result.current.loading).toBe(false));

    // Le point du lot : le travail en cours reste VISIBLE et compte juste.
    expect(result.current.colonnes.CREATION_TICKET.items).toHaveLength(12);
    expect(result.current.colonnes.CREATION_TICKET.total).toBe(12);
    expect(result.current.colonnes.DEROULEMENT_DEMARCHE.total).toBe(30);
    expect(result.current.colonnes.GENERATION_DOCUMENTS.total).toBe(41);
    expect(result.current.colonnes.CLOTURE_DOSSIER.total).toBe(7);
  });

  it('le compteur est le total EN BASE, pas le nombre rendu', async () => {
    const { result } = renderHook(() => useTicketColumns({ ...SANS_FILTRE }));
    await waitFor(() => expect(result.current.loading).toBe(false));

    const annule = result.current.colonnes.ANNULE;
    expect(annule.items).toHaveLength(TAILLE_PAGE_COLONNE); // 25 rendus
    expect(annule.total).toBe(137); // 137 existants
    expect(annule.hasMore).toBe(true);

    // Une colonne entierement chargee ne propose plus « Afficher plus ».
    expect(result.current.colonnes.CLOTURE_DOSSIER.hasMore).toBe(false);
  });

  it('les totaux de l en-tete sont les totaux en base (227, pas 200)', async () => {
    const { result } = renderHook(() => useTicketColumns({ ...SANS_FILTRE }));
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.totaux.total).toBe(227);
    expect(result.current.totaux.enCours).toBe(71); // 41 + 30
    expect(result.current.totaux.nouveaux).toBe(12);
  });

  it('« Afficher plus » ajoute une page sans doublon et met a jour le restant', async () => {
    const { result } = renderHook(() => useTicketColumns({ ...SANS_FILTRE }));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.loadMore('ANNULE');
    });

    const annule = result.current.colonnes.ANNULE;
    expect(annule.items).toHaveLength(TAILLE_PAGE_COLONNE * 2);
    expect(new Set(annule.items.map((t) => t.id)).size).toBe(annule.items.length);
    expect(annule.total).toBe(137);
    expect(annule.hasMore).toBe(true);

    const dernier = listMock.mock.calls.at(-1)?.[0];
    expect(dernier?.offset).toBe(TAILLE_PAGE_COLONNE);
    expect(dernier?.statuts).toEqual(['ANNULE']);
  });

  it('un filtre de statut n interroge que la colonne concernee', async () => {
    const { result } = renderHook(() =>
      useTicketColumns({ ...SANS_FILTRE, statut: 'ANNULE' }),
    );
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(listMock).toHaveBeenCalledTimes(1);
    expect(listMock.mock.calls[0][0]?.statuts).toEqual(['ANNULE']);
    expect(result.current.colonnes.CREATION_TICKET.items).toHaveLength(0);
    expect(result.current.colonnes.ANNULE.total).toBe(137);
  });

  it('propage les filtres a chaque colonne', async () => {
    const { result } = renderHook(() =>
      useTicketColumns({
        query: 'PARACOSME',
        type: 'CREATION',
        priorite: 'HAUTE',
        statut: '',
      }),
    );
    await waitFor(() => expect(result.current.loading).toBe(false));

    for (const call of listMock.mock.calls) {
      expect(call[0]?.q).toBe('PARACOSME');
      expect(call[0]?.types).toEqual(['CREATION']);
      expect(call[0]?.priorites).toEqual(['HAUTE']);
    }
  });

  it('une colonne en echec reste vide sans faire tomber les autres', async () => {
    listMock.mockImplementation((params) =>
      params?.statuts?.[0] === 'ANNULE'
        ? Promise.reject(new Error('boom'))
        : backend(params),
    );

    const { result } = renderHook(() => useTicketColumns({ ...SANS_FILTRE }));
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.colonnes.ANNULE.items).toHaveLength(0);
    expect(result.current.colonnes.ANNULE.total).toBe(0);
    expect(result.current.colonnes.GENERATION_DOCUMENTS.total).toBe(41);
  });
});
