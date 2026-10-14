import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

const appels = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}));

vi.mock('../../../lib/api', () => ({ api: appels }));

import { InfoBulle, TexteAide } from '../Aide';
import { useAidesStore } from '../../../store/aidesStore';

/** Aide integree (CDC 15.4) : une aide deja vue peut etre masquee et reaffichee. */
describe('Aide', () => {
  beforeEach(() => {
    window.localStorage.clear();
    useAidesStore.setState({ masquees: new Set<string>(), utilisateur: null, chargee: false });
    appels.get.mockReset();
    appels.put.mockReset().mockResolvedValue({});
    appels.delete.mockReset().mockResolvedValue({});
  });

  it('une aide masquee le reste au retour sur l ecran, puis se reaffiche', () => {
    const { unmount } = render(<TexteAide cle="essai" titre="Titre">Texte</TexteAide>);
    fireEvent.click(screen.getByRole('button', { name: 'Masquer l’aide' }));
    unmount();
    render(<TexteAide cle="essai" titre="Titre">Texte</TexteAide>);
    expect(screen.queryByRole('note')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /Afficher l’aide/ }));
    expect(screen.getByRole('note', { name: 'Titre' })).toHaveTextContent('Texte');
  });

  it('le rendu d une aide ne fait aucun appel reseau ; sans session, masquer non plus', () => {
    render(<TexteAide cle="essai2" titre="Titre">Texte</TexteAide>);
    expect(screen.getByRole('note', { name: 'Titre' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Masquer l’aide' }));
    expect(appels.get).not.toHaveBeenCalled();
    expect(appels.put).not.toHaveBeenCalled();
  });

  it('avec une session, masquer et reafficher sont memorises cote serveur', () => {
    useAidesStore.setState({ utilisateur: 'u-1', chargee: true });
    render(<TexteAide cle="page-ticket" titre="Titre">Texte</TexteAide>);
    fireEvent.click(screen.getByRole('button', { name: 'Masquer l’aide' }));
    expect(appels.put).toHaveBeenCalledWith('/auth/me/aides/page-ticket');
    fireEvent.click(screen.getByRole('button', { name: /Afficher l’aide/ }));
    expect(appels.delete).toHaveBeenCalledWith('/auth/me/aides/page-ticket');
    expect(appels.get).not.toHaveBeenCalled();
  });

  it('le chargement lit le serveur une fois, reprend les aides masquees du navigateur puis les efface', async () => {
    window.localStorage.setItem('jurika.aide.ancienne.masquee', '1');
    window.localStorage.setItem('jurika.aide.serveur.masquee', '1');
    window.localStorage.setItem('autre.cle', '1');
    appels.get.mockResolvedValue({ data: { cles: ['serveur'] } });

    await useAidesStore.getState().charger('u-1');
    await useAidesStore.getState().charger('u-1');

    expect(appels.get).toHaveBeenCalledTimes(1);
    expect(appels.put).toHaveBeenCalledTimes(1);
    expect(appels.put).toHaveBeenCalledWith('/auth/me/aides/ancienne');
    expect(window.localStorage.getItem('jurika.aide.ancienne.masquee')).toBeNull();
    expect(window.localStorage.getItem('jurika.aide.serveur.masquee')).toBeNull();
    expect(window.localStorage.getItem('autre.cle')).toBe('1');
    const etat = useAidesStore.getState();
    expect(etat.chargee).toBe(true);
    expect([...etat.masquees].sort()).toEqual(['ancienne', 'serveur']);

    render(<TexteAide cle="serveur" titre="Titre">Texte</TexteAide>);
    await waitFor(() => expect(screen.queryByRole('note')).toBeNull());
  });

  it('serveur injoignable : rien n est masque et la visite n est pas proposee', async () => {
    appels.get.mockRejectedValue(new Error('reseau'));
    await useAidesStore.getState().charger('u-2');
    expect(useAidesStore.getState().chargee).toBe(false);
    render(<TexteAide cle="essai3" titre="Titre">Texte</TexteAide>);
    expect(screen.getByRole('note', { name: 'Titre' })).toBeInTheDocument();
  });

  it('reafficher toutes les aides vide le magasin et le serveur', async () => {
    useAidesStore.setState({ utilisateur: 'u-1', chargee: true, masquees: new Set(['a', 'b']) });
    await useAidesStore.getState().reafficherTout();
    expect(useAidesStore.getState().masquees.size).toBe(0);
    expect(appels.delete).toHaveBeenCalledWith('/auth/me/aides');
  });

  it('l infobulle s ouvre au clavier et se relie au bouton', () => {
    render(<InfoBulle libelle="Aide sur le champ" texte="Explication" />);
    const bouton = screen.getByRole('button', { name: 'Aide sur le champ' });
    fireEvent.focus(bouton);
    const bulle = screen.getByRole('tooltip');
    expect(bulle).toHaveTextContent('Explication');
    expect(bouton).toHaveAttribute('aria-describedby', bulle.id);
    fireEvent.blur(bouton);
    expect(screen.queryByRole('tooltip')).toBeNull();
  });
});
