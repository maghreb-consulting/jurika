import { act, render, renderHook, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Lot L6 (E7) : outils communs des pages de parcours. Ils remplacent des effets qui
 * mettaient l'etat a jour de facon synchrone ; ces tests gardent le comportement d'origine.
 */

const getDossierParties = vi.fn();
const listSuccursales = vi.fn();

vi.mock('../../../services/workflow.service', () => ({
  workflowService: {
    getDossierParties: (id: string) => getDossierParties(id),
    listSuccursales: (id: string) => listSuccursales(id),
  },
}));

import { bornesDateSeance, useDossierParties, useEtatReinitialise, useSuccursalesActives } from '../parcoursHooks';
import { AideEtape } from '../AideEtape';
import { ETAPES_LIQUIDATION } from '../aidesParcours';

beforeEach(() => {
  getDossierParties.mockReset();
  listSuccursales.mockReset();
  try {
    window.localStorage.clear();
  } catch {
    // stockage indisponible : sans effet sur ces tests
  }
});

describe('useEtatReinitialise', () => {
  it('garde la valeur modifiee tant que la cle ne change pas, puis la remet a sa valeur initiale', () => {
    const { result, rerender } = renderHook(({ cle }) => useEtatReinitialise(cle, () => false), {
      initialProps: { cle: 1 },
    });
    expect(result.current[0]).toBe(false);
    act(() => result.current[1](true));
    expect(result.current[0]).toBe(true);
    rerender({ cle: 1 });
    expect(result.current[0]).toBe(true);
    rerender({ cle: 2 });
    expect(result.current[0]).toBe(false);
  });

  it('recalcule la valeur initiale avec les donnees du moment', () => {
    let saisie = false;
    const { result, rerender } = renderHook(({ cle }) => useEtatReinitialise(cle, () => cle === 1 && saisie), {
      initialProps: { cle: 2 },
    });
    expect(result.current[0]).toBe(false);
    saisie = true;
    rerender({ cle: 1 });
    expect(result.current[0]).toBe(true);
  });
});

describe('bornesDateSeance', () => {
  it('borne les dates de seance entre deux ans en arriere et trente jours en avant', () => {
    const { minDate, maxDate } = bornesDateSeance();
    const jour = 24 * 3600 * 1000;
    const maintenant = Date.now();
    expect(Math.abs(Date.parse(minDate) - (maintenant - 730 * jour))).toBeLessThan(2 * jour);
    expect(Math.abs(Date.parse(maxDate) - (maintenant + 30 * jour))).toBeLessThan(2 * jour);
  });
});

describe('useDossierParties', () => {
  it('ne lit rien sans societe', () => {
    const { result } = renderHook(() => useDossierParties(''));
    expect(result.current).toEqual({ dossierParties: null, partiesLoading: false });
    expect(getDossierParties).not.toHaveBeenCalled();
  });

  it('signale le chargement puis rend les parties de la societe choisie', async () => {
    getDossierParties.mockResolvedValue({ denomination: 'ALPHA' });
    const { result } = renderHook(() => useDossierParties('d1'));
    expect(result.current.partiesLoading).toBe(true);
    await waitFor(() => expect(result.current.partiesLoading).toBe(false));
    expect(result.current.dossierParties).toEqual({ denomination: 'ALPHA' });
  });

  it('un echec de lecture laisse la saisie possible (parties nulles, chargement termine)', async () => {
    getDossierParties.mockRejectedValue(new Error('indisponible'));
    const { result } = renderHook(() => useDossierParties('d1'));
    await waitFor(() => expect(result.current.partiesLoading).toBe(false));
    expect(result.current.dossierParties).toBeNull();
  });
});

describe('useSuccursalesActives', () => {
  it('ne garde que les succursales actives et relit a chaque changement de relecture', async () => {
    listSuccursales.mockResolvedValueOnce([
      { id: 's1', statut: 'ACTIVE' },
      { id: 's2', statut: 'FERMEE' },
    ]);
    const { result, rerender } = renderHook(({ etape }) => useSuccursalesActives('d1', etape), {
      initialProps: { etape: 1 },
    });
    await waitFor(() => expect(result.current.succursales).toHaveLength(1));
    listSuccursales.mockResolvedValueOnce([]);
    rerender({ etape: 2 });
    // La liste precedente reste affichee pendant la relecture d'une meme societe.
    expect(result.current.succursales).toHaveLength(1);
    await waitFor(() => expect(result.current.succursales).toHaveLength(0));
    expect(listSuccursales).toHaveBeenCalledTimes(2);
  });
});

describe('AideEtape', () => {
  it('dit ce qu il faut faire, ce qui manque et ce qui vient ensuite', () => {
    render(<AideEtape parcours="liquidation" etapes={ETAPES_LIQUIDATION} etape={2} />);
    const aide = screen.getByRole('note', { name: /Génération des actes/ });
    expect(aide).toHaveTextContent('À faire');
    expect(aide).toHaveTextContent('Ce qui manque');
    expect(aide).toHaveTextContent('Ensuite');
  });

  it('nomme les donnees manquantes calculees par la page', () => {
    render(
      <AideEtape
        parcours="liquidation"
        etapes={ETAPES_LIQUIDATION}
        etape={1}
        manquants={['Sélectionnez la société dissoute à liquider.', 'La date de l’AGE de clôture est obligatoire.']}
      />,
    );
    const aide = screen.getByRole('note');
    expect(aide).toHaveTextContent('il reste 2 données à compléter');
    expect(aide).toHaveTextContent('Sélectionnez la société dissoute à liquider.');
  });

  it('peut etre masquee puis reaffichee', () => {
    render(<AideEtape parcours="liquidation" etapes={ETAPES_LIQUIDATION} etape={3} />);
    act(() => screen.getByRole('button', { name: 'Masquer l’aide' }).click());
    expect(screen.queryByRole('note')).toBeNull();
    act(() => screen.getByRole('button', { name: /Afficher l’aide/ }).click());
    expect(screen.getByRole('note')).toBeInTheDocument();
  });

  it('ne rend rien pour une etape inconnue', () => {
    const { container } = render(<AideEtape parcours="liquidation" etapes={ETAPES_LIQUIDATION} etape={9} />);
    expect(container).toBeEmptyDOMElement();
  });
});
