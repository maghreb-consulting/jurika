import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { Role } from '../../../types/auth';

/**
 * Lot L6b (E11, point 8) : JURI. Visible selon le choix du compte, aide de l'ecran lue dans
 * la page, acces au chatbot selon le role, masquage enregistre, immobile si les animations
 * sont reduites, accessible.
 */
const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn(), delete: vi.fn() }));
vi.mock('../../../lib/api', () => ({ api }));

import { Juri } from '../Juri';
import { InfoBulle, TexteAide } from '../../ui/Aide';
import { useAuthStore } from '../../../store/authStore';
import { useAidesStore } from '../../../store/aidesStore';
import { useAssistantStore } from '../../../store/assistantStore';
import { useCommandesStore } from '../../../store/commandesStore';
import { usePreferencesStore } from '../../../store/preferencesStore';

function connecter(role: Role, juriMasque = false) {
  useAuthStore.setState({
    user: { role, email: 'x@cabinet.ma', userId: `u-${role}`, workspaceId: 'ws-1' } as never,
    isAuthenticated: true,
  } as never);
  usePreferencesStore.setState({ utilisateur: `u-${role}`, statut: 'pret', erreur: null, preferences: { theme: 'CLAIR', juriMasque } });
}

function Ecran({ avecAides = true }: { avecAides?: boolean }) {
  return (
    <main id="contenu" tabIndex={-1}>
      <h1>Mes tickets</h1>
      {avecAides && (
        <>
          <TexteAide cle="tickets-liste" titre="Vos tickets">
            <p>Chaque ticket suit une demande du client jusqu’au dépôt.</p>
          </TexteAide>
          <TexteAide cle="tickets-filtres" titre="Les filtres">
            <p>Filtrez par statut.</p>
          </TexteAide>
          <InfoBulle libelle="Aide : priorité" texte="La priorité ordonne la colonne." />
        </>
      )}
      <button type="button">Nouveau ticket</button>
    </main>
  );
}

function rendre(avecAides = true) {
  return render(
    <>
      <Ecran avecAides={avecAides} />
      <Juri />
    </>,
  );
}

const bouton = () => screen.getByRole('button', { name: /^Besoin d’aide \? Ouvrir JURI/ });

async function ouvrirPanneau() {
  fireEvent.click(bouton());
  const panneau = await screen.findByTestId('juri-panneau');
  return panneau;
}

beforeEach(() => {
  api.put.mockReset();
  api.put.mockResolvedValue({ data: {} });
  api.delete.mockReset();
  api.delete.mockResolvedValue({ data: {} });
  useCommandesStore.setState({ ouverte: null });
  useAssistantStore.setState({ ouvert: false });
  useAidesStore.setState({ masquees: new Set(['tickets-filtres']), utilisateur: 'u', chargee: true });
});

afterEach(() => {
  useAuthStore.setState({ user: null, isAuthenticated: false } as never);
  usePreferencesStore.getState().vider();
  vi.unstubAllGlobals();
});

describe('JURI', () => {
  it('visible quand le compte ne l a pas masque ; absent s il l a masque ou tant que le choix est inconnu', () => {
    connecter('EMPLOYE', true);
    const { unmount } = rendre();
    expect(screen.queryByTestId('juri')).not.toBeInTheDocument();
    unmount();

    connecter('EMPLOYE');
    usePreferencesStore.setState({ preferences: null, statut: 'chargement' });
    const r2 = rendre();
    expect(screen.queryByTestId('juri')).not.toBeInTheDocument();
    r2.unmount();

    // Lecture en echec : rien n'est suppose masque, JURI s'affiche.
    usePreferencesStore.setState({ preferences: null, statut: 'erreur' });
    rendre();
    expect(screen.getByTestId('juri')).toBeInTheDocument();
  });

  it('au repos a moitie cache ; au survol et au focus il se deploie et dit "Besoin d aide ?"', () => {
    connecter('EMPLOYE');
    rendre();
    expect(bouton()).toHaveAttribute('data-deploye', 'non');
    expect(bouton().className).toContain('translate-x-[55%]');
    expect(screen.getByTestId('juri-bulle')).toHaveClass('invisible');
    fireEvent.mouseEnter(bouton());
    expect(bouton()).toHaveAttribute('data-deploye', 'oui');
    expect(bouton().className).not.toContain('translate-x-[55%]');
    expect(screen.getByTestId('juri-bulle')).toHaveTextContent('Besoin d’aide ?');
    expect(screen.getByTestId('juri-bulle')).not.toHaveClass('invisible');
    fireEvent.mouseLeave(bouton());
    expect(bouton()).toHaveAttribute('data-deploye', 'non');
    fireEvent.focus(bouton());
    expect(bouton()).toHaveAttribute('data-deploye', 'oui');
  });

  it('animations reduites : immobile (aucun decalage ni transition), la bulle apparait seule', () => {
    vi.stubGlobal('matchMedia', (q: string) => ({
      matches: q.includes('reduced-motion'),
      media: q,
      addEventListener: () => {},
      removeEventListener: () => {},
    }));
    connecter('EMPLOYE');
    rendre();
    expect(bouton().className).not.toContain('translate-x');
    expect(bouton().className).not.toContain('transition');
    fireEvent.mouseEnter(bouton());
    expect(bouton().className).not.toContain('translate-x');
    expect(screen.getByTestId('juri-bulle')).not.toHaveClass('invisible');
    expect(screen.getByTestId('juri-bulle').className).not.toContain('transition');
  });

  it('le panneau liste l aide de l ecran : encadres (masques compris) et infobulles', async () => {
    connecter('EMPLOYE');
    rendre();
    const panneau = await ouvrirPanneau();
    expect(bouton()).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('dialog', { name: 'JURI, votre assistant' })).toBeInTheDocument();
    await waitFor(() => expect(panneau).toHaveTextContent('Vos tickets'));
    expect(screen.getByText('Aide de l’écran « Mes tickets »')).toBeInTheDocument();
    expect(panneau).toHaveTextContent('Chaque ticket suit une demande du client jusqu’au dépôt.');
    expect(panneau).toHaveTextContent('Les filtres');
    expect(panneau).toHaveTextContent('Vous avez masqué cette aide sur la page.');
    expect(panneau).toHaveTextContent('Priorité');
    expect(panneau).toHaveTextContent('La priorité ordonne la colonne.');
    // Le texte du bouton "Masquer l'aide" n'est pas repris comme contenu.
    expect(panneau).not.toHaveTextContent('Masquer l’aide');
  });

  it('une aide masquee se reaffiche sur la page depuis le panneau (choix du compte)', async () => {
    connecter('EMPLOYE');
    rendre();
    await ouvrirPanneau();
    fireEvent.click(await screen.findByRole('button', { name: 'Réafficher sur la page' }));
    expect(useAidesStore.getState().masquees.has('tickets-filtres')).toBe(false);
    expect(api.delete).toHaveBeenCalledWith('/auth/me/aides/tickets-filtres');
    expect(screen.queryByTestId('juri-panneau')).not.toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'Les filtres' })).toBeInTheDocument();
  });

  it('ecran sans aide : etat vide explicite, visite, raccourcis et palette restent proposes', async () => {
    connecter('EMPLOYE');
    rendre(false);
    const panneau = await ouvrirPanneau();
    await waitFor(() => expect(panneau).toHaveTextContent('Pas d’aide particulière sur cet écran'));
    fireEvent.click(within(panneau).getByRole('button', { name: 'Raccourcis clavier (touche ?)' }));
    expect(useCommandesStore.getState().ouverte).toBe('raccourcis');
  });

  it.each<Role>(['EMPLOYE', 'SUPERVISEUR', 'SUPER_ADMIN'])('%s : le panneau ouvre le chatbot et se ferme', async (role) => {
    connecter(role);
    rendre();
    const panneau = await ouvrirPanneau();
    fireEvent.click(within(panneau).getByRole('button', { name: 'Poser une question au chatbot juridique' }));
    expect(useAssistantStore.getState().ouvert).toBe(true);
    expect(useCommandesStore.getState().ouverte).toBeNull();
  });

  it('client : pas d acces au chatbot', async () => {
    connecter('CLIENT');
    rendre();
    const panneau = await ouvrirPanneau();
    expect(within(panneau).queryByRole('button', { name: 'Poser une question au chatbot juridique' })).not.toBeInTheDocument();
    expect(within(panneau).getByRole('button', { name: 'Revoir la visite guidée' })).toBeInTheDocument();
  });

  it('a la fermeture du chatbot ouvert depuis JURI, le focus revient a JURI', async () => {
    connecter('EMPLOYE');
    rendre();
    act(() => useAssistantStore.getState().ouvrir());
    act(() => useAssistantStore.getState().fermer());
    expect(bouton()).toHaveFocus();
  });

  it('Echap ferme le panneau', async () => {
    connecter('EMPLOYE');
    rendre();
    await ouvrirPanneau();
    fireEvent.keyDown(screen.getByRole('dialog', { name: 'JURI, votre assistant' }), { key: 'Escape' });
    expect(screen.queryByTestId('juri-panneau')).not.toBeInTheDocument();
    expect(bouton()).toHaveAttribute('aria-expanded', 'false');
  });

  it('Masquer JURI : choix enregistre sur le compte, JURI disparait', async () => {
    connecter('SUPERVISEUR');
    rendre();
    await ouvrirPanneau();
    fireEvent.click(screen.getByRole('button', { name: 'Masquer JURI' }));
    expect(api.put).toHaveBeenCalledWith('/users/me/preferences', { juriMasque: true });
    await waitFor(() => expect(screen.queryByTestId('juri')).not.toBeInTheDocument());
    expect(usePreferencesStore.getState().preferences?.juriMasque).toBe(true);
  });

  it('cible de la visite guidee et robot decoratif', () => {
    connecter('EMPLOYE');
    rendre();
    expect(screen.getByTestId('juri')).toHaveAttribute('data-visite', 'chatbot');
    expect(screen.getByTestId('juri-robot')).toHaveAttribute('aria-hidden', 'true');
  });
});
