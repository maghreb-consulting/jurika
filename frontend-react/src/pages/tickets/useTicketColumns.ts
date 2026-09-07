import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ticketService } from '../../services/ticket.service';
import type {
  Ticket,
  TicketPriorite,
  TicketStatut,
  TicketType,
} from '../../types/ticket';

/**
 * Lot 2 (2026-09-07) — CHARGEMENT PAR COLONNE.
 *
 * <p>Avant ce lot, la page chargeait `limit: 200` TOUS STATUTS CONFONDUS et
 * repartissait cote client. Les tickets annules — qui s'accumulent sans fin —
 * consommaient donc le budget des quatre colonnes actives : au-dela de 200
 * tickets, du travail EN COURS disparaissait du tableau sans aucun signal.
 * L'employe ne cherche pas ce qu'il ne sait pas manquant.
 *
 * <p>Chaque statut a desormais sa propre requete, sa propre pagination et son
 * propre total. Le compteur affiche en tete de colonne est le TOTAL EN BASE
 * (`PageResponse.total`), jamais le nombre d'elements rendus : « 12 sur 137 »
 * et non « 12 ».
 */

/** Nombre de tickets charges par colonne, et par « Afficher plus ». */
export const TAILLE_PAGE_COLONNE = 25;

/** Les cinq statuts, dans l'ordre du cycle de vie. ANNULE est une sortie laterale. */
export const STATUTS_COLONNES: TicketStatut[] = [
  'CREATION_TICKET',
  'GENERATION_DOCUMENTS',
  'DEROULEMENT_DEMARCHE',
  'CLOTURE_DOSSIER',
  'ANNULE',
];

export interface ColonneTickets {
  items: Ticket[];
  /** Total en base pour ce statut, filtres courants appliques. */
  total: number;
  /** Chargement de la premiere page. */
  loading: boolean;
  /** Chargement d'une page supplementaire (« Afficher plus »). */
  loadingMore: boolean;
  /** Vrai s'il reste des tickets non charges pour ce statut. */
  hasMore: boolean;
}

export interface FiltresTickets {
  query: string;
  type: TicketType | '';
  priorite: TicketPriorite | '';
  statut: TicketStatut | '';
}

const COLONNE_VIDE: ColonneTickets = {
  items: [],
  total: 0,
  loading: false,
  loadingMore: false,
  hasMore: false,
};

function colonnesVides(loading: boolean): Record<TicketStatut, ColonneTickets> {
  return STATUTS_COLONNES.reduce(
    (acc, s) => {
      acc[s] = { ...COLONNE_VIDE, loading };
      return acc;
    },
    {} as Record<TicketStatut, ColonneTickets>,
  );
}

export function useTicketColumns(filtres: FiltresTickets) {
  const [colonnes, setColonnes] = useState<Record<TicketStatut, ColonneTickets>>(
    () => colonnesVides(true),
  );

  /**
   * Miroir de `colonnes` lisible en dehors du rendu. `loadMore` a besoin de
   * l'offset courant AVANT de declencher la requete ; le lire depuis un
   * updater `setState` le rendrait impur (StrictMode l'appelle deux fois).
   */
  const colonnesRef = useRef(colonnes);
  colonnesRef.current = colonnes;

  const { query, type, priorite, statut } = filtres;

  /**
   * Jeton de course : une reponse arrivee apres un changement de filtre est
   * ignoree. Sans cela, un « Afficher plus » lent ecrase la liste filtree.
   */
  const generation = useRef(0);

  const load = useCallback(async () => {
    const gen = ++generation.current;
    setColonnes(colonnesVides(true));

    const resultats = await Promise.all(
      STATUTS_COLONNES.map(async (s) => {
        // Filtre de statut actif : les autres colonnes sont vides par
        // definition, on ne depense pas une requete pour l'apprendre.
        if (statut && statut !== s) {
          return [s, { ...COLONNE_VIDE }] as const;
        }
        try {
          const page = await ticketService.list({
            q: query || undefined,
            types: type ? [type] : undefined,
            priorites: priorite ? [priorite] : undefined,
            statuts: [s],
            limit: TAILLE_PAGE_COLONNE,
            offset: 0,
          });
          return [
            s,
            {
              items: page.items,
              total: page.total,
              loading: false,
              loadingMore: false,
              hasMore: page.items.length < page.total,
            },
          ] as const;
        } catch {
          return [s, { ...COLONNE_VIDE }] as const;
        }
      }),
    );

    if (gen !== generation.current) return;
    setColonnes(
      resultats.reduce(
        (acc, [s, c]) => {
          acc[s] = c;
          return acc;
        },
        {} as Record<TicketStatut, ColonneTickets>,
      ),
    );
  }, [query, type, priorite, statut]);

  useEffect(() => {
    void load();
  }, [load]);

  const loadMore = useCallback(
    async (s: TicketStatut) => {
      const gen = generation.current;
      const courante = colonnesRef.current[s];
      if (!courante || courante.loadingMore || !courante.hasMore) return;
      const offset = courante.items.length;
      setColonnes((prev) => ({ ...prev, [s]: { ...prev[s], loadingMore: true } }));

      try {
        const page = await ticketService.list({
          q: query || undefined,
          types: type ? [type] : undefined,
          priorites: priorite ? [priorite] : undefined,
          statuts: [s],
          limit: TAILLE_PAGE_COLONNE,
          offset,
        });
        if (gen !== generation.current) return;
        setColonnes((prev) => {
          // Deduplication defensive : deux « Afficher plus » concurrents, ou un
          // ticket deplace entre deux pages, ne doivent pas produire de doublon.
          const connus = new Set(prev[s].items.map((t) => t.id));
          const ajouts = page.items.filter((t) => !connus.has(t.id));
          const items = [...prev[s].items, ...ajouts];
          return {
            ...prev,
            [s]: {
              items,
              total: page.total,
              loading: false,
              loadingMore: false,
              hasMore: items.length < page.total,
            },
          };
        });
      } catch {
        if (gen !== generation.current) return;
        setColonnes((prev) => ({ ...prev, [s]: { ...prev[s], loadingMore: false } }));
      }
    },
    [query, type, priorite],
  );

  /** Totaux EN BASE, pour l'en-tete de page. */
  const totaux = useMemo(() => {
    const parStatut = (s: TicketStatut) => colonnes[s]?.total ?? 0;
    return {
      total: STATUTS_COLONNES.reduce((n, s) => n + parStatut(s), 0),
      enCours: parStatut('GENERATION_DOCUMENTS') + parStatut('DEROULEMENT_DEMARCHE'),
      nouveaux: parStatut('CREATION_TICKET'),
    };
  }, [colonnes]);

  const loading = STATUTS_COLONNES.some((s) => colonnes[s]?.loading);

  return { colonnes, totaux, loading, reload: load, loadMore };
}
