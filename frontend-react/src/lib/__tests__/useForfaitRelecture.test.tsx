import { describe, expect, it, vi, beforeEach } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';

/**
 * Recette (2026-10-17) : apres la souscription, le forfait choisi s'affiche partout. Avant, un
 * ecran deja monte gardait l'etat de l'essai jusqu'au rechargement de la page : oublierForfait()
 * vidait le cache sans prevenir les ecrans, et rien ne relisait l'etat au retour sur l'onglet.
 */
const getAbonnement = vi.fn();
vi.mock('../../services/billing.service', () => ({
  billingService: { getAbonnement: () => getAbonnement() },
}));

const { useForfait, oublierForfait } = await import('../useForfait');

function Forfait() {
  const { abonnement } = useForfait();
  return <p data-testid="forfait">{abonnement ? (abonnement as { libelle?: string }).libelle : '-'}</p>;
}

describe('useForfait : relecture apres un changement de forfait', () => {
  beforeEach(() => {
    getAbonnement.mockReset();
    oublierForfait();
  });

  it('un ecran deja monte affiche le nouveau forfait apres oublierForfait()', async () => {
    getAbonnement.mockResolvedValueOnce({ libelle: 'Essai gratuit (fonctions du forfait Professionnel)' });
    render(<Forfait />);
    await waitFor(() => expect(screen.getByTestId('forfait')).toHaveTextContent('Essai gratuit'));
    getAbonnement.mockResolvedValueOnce({ libelle: 'Essentiel' });
    act(() => oublierForfait());
    await waitFor(() => expect(screen.getByTestId('forfait')).toHaveTextContent('Essentiel'));
  });

  it('le retour sur l onglet relit le forfait (souscription faite ailleurs, ex. virement enregistre)', async () => {
    getAbonnement.mockResolvedValueOnce({ libelle: 'Essai gratuit (fonctions du forfait Professionnel)' });
    render(<Forfait />);
    await waitFor(() => expect(screen.getByTestId('forfait')).toHaveTextContent('Essai gratuit'));
    getAbonnement.mockResolvedValueOnce({ libelle: 'Essentiel' });
    act(() => {
      Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await waitFor(() => expect(screen.getByTestId('forfait')).toHaveTextContent('Essentiel'));
  });
});
