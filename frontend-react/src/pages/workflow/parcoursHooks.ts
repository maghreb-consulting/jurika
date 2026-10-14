/**
 * Outils communs des pages de parcours (lot L6, E7).
 *
 * Ils remplacent des motifs que les regles react-hooks refusent (mise a jour d'etat
 * synchrone dans un effet, appel impur pendant le rendu), sans changer ce que voit
 * l'utilisateur :
 *
 * - `useEtatReinitialise` : un etat remis a sa valeur initiale quand une cle change
 *   (l'etape affichee, typiquement). L'ajustement se fait pendant le rendu, comme le
 *   recommande React, et non plus dans un effet qui rendait deux fois.
 * - `bornesDateSeance` : bornes des dates de seance (deux ans en arriere, trente
 *   jours en avant), calculees une fois par l'initialiseur paresseux d'un `useState`.
 * - `useDossierParties` / `useSuccursalesActives` : lectures par societe ; l'etat de
 *   chargement est deduit de la cle lue, et les mises a jour d'etat se font dans la
 *   promesse.
 */
import { useCallback, useEffect, useState, type Dispatch, type SetStateAction } from 'react';
import { extractError } from '../../lib/api';
import {
  workflowService,
  type DossierParties,
  type SuccursaleSummary,
} from '../../services/workflow.service';

export function useEtatReinitialise<T>(
  cle: unknown,
  initial: () => T,
): [T, Dispatch<SetStateAction<T>>] {
  const [etat, setEtat] = useState<{ cle: unknown; valeur: T }>(() => ({ cle, valeur: initial() }));
  let courant = etat;
  if (!Object.is(etat.cle, cle)) {
    courant = { cle, valeur: initial() };
    setEtat(courant);
  }
  const changer = useCallback<Dispatch<SetStateAction<T>>>((v) => {
    setEtat((e) => ({
      cle: e.cle,
      valeur: typeof v === 'function' ? (v as (p: T) => T)(e.valeur) : v,
    }));
  }, []);
  return [courant.valeur, changer];
}

const JOUR_MS = 24 * 3600 * 1000;

export function bornesDateSeance(): { minDate: string; maxDate: string } {
  const maintenant = Date.now();
  return {
    minDate: new Date(maintenant - 2 * 365 * JOUR_MS).toISOString().slice(0, 10),
    maxDate: new Date(maintenant + 30 * JOUR_MS).toISOString().slice(0, 10),
  };
}

/**
 * Parties prenantes (gerants, associes, faits de la societe) lues en base pour la
 * societe `dossierId`. Echec de lecture : `null` (best-effort, la saisie reste possible).
 */
export function useDossierParties(dossierId: string | null | undefined): {
  dossierParties: DossierParties | null;
  partiesLoading: boolean;
} {
  const [lu, setLu] = useState<{ id: string; parties: DossierParties | null } | null>(null);
  useEffect(() => {
    if (!dossierId) return;
    let cancelled = false;
    workflowService
      .getDossierParties(dossierId)
      .then((parties) => {
        if (!cancelled) setLu({ id: dossierId, parties });
      })
      .catch(() => {
        if (!cancelled) setLu({ id: dossierId, parties: null });
      });
    return () => {
      cancelled = true;
    };
  }, [dossierId]);
  if (!dossierId) return { dossierParties: null, partiesLoading: false };
  const aJour = lu?.id === dossierId;
  return { dossierParties: aJour ? lu.parties : null, partiesLoading: !aJour };
}

/**
 * Succursales au statut ACTIVE de la societe `dossierId`, relues a chaque changement de
 * `relecture` (l'etape affichee, par exemple). La liste precedente reste affichee pendant
 * la relecture d'une meme societe.
 */
export function useSuccursalesActives(
  dossierId: string | null | undefined,
  relecture?: unknown,
): {
  succursales: SuccursaleSummary[];
  chargement: boolean;
  erreur: string | null;
} {
  const [lu, setLu] = useState<{ id: string; items: SuccursaleSummary[]; erreur: string | null } | null>(null);
  useEffect(() => {
    if (!dossierId) return;
    let cancelled = false;
    workflowService
      .listSuccursales(dossierId)
      .then((items) => {
        if (!cancelled) setLu({ id: dossierId, items: items.filter((s) => s.statut === 'ACTIVE'), erreur: null });
      })
      .catch((e) => {
        if (!cancelled) setLu({ id: dossierId, items: [], erreur: extractError(e).message });
      });
    return () => {
      cancelled = true;
    };
  }, [dossierId, relecture]);
  if (!dossierId) return { succursales: [], chargement: false, erreur: null };
  const aJour = lu?.id === dossierId;
  return {
    succursales: aJour ? lu.items : [],
    chargement: !aJour,
    erreur: aJour ? lu.erreur : null,
  };
}
