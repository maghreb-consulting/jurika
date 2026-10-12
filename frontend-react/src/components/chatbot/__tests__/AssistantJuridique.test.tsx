import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { Role } from '../../../types/auth';

/**
 * Lot L5 E8 (RG-COM-01, 03, 04, 05) : bulle flottante, panneau lateral, avertissement
 * permanent et blocage des donnees personnelles AVANT l'appel au serveur.
 */

const etat: { user: { role: Role; userId: string; email: string; workspaceId: string } | null } = {
  user: null,
};

vi.mock('../../../store/authStore', () => ({
  useAuthStore: <T,>(selector: (s: typeof etat) => T) => selector(etat),
}));

const ask = vi.fn();
vi.mock('../../../services/chatbot.service', () => ({
  chatbotService: { ask: (q: string) => ask(q) },
}));

import { AssistantJuridique } from '../AssistantJuridique';
import { useAssistantStore } from '../../../store/assistantStore';

function connecter(role: Role) {
  etat.user = { role, userId: `u-${role}`, email: `${role}@cabinet.ma`, workspaceId: 'ws-1' };
}

function ouvrirPanneau() {
  render(<AssistantJuridique />);
  fireEvent.click(screen.getByTestId('assistant-bulle'));
}

function saisir(texte: string) {
  fireEvent.change(screen.getByLabelText('Votre question juridique'), { target: { value: texte } });
}

describe('AssistantJuridique', () => {
  beforeEach(() => {
    ask.mockReset();
    localStorage.clear();
    useAssistantStore.setState({ ouvert: false });
  });

  it.each<Role>(['EMPLOYE', 'SUPERVISEUR', 'SUPER_ADMIN'])('bulle presente pour %s', (role) => {
    connecter(role);
    render(<AssistantJuridique />);
    expect(screen.getByTestId('assistant-bulle')).toBeInTheDocument();
  });

  it('aucune bulle pour le client', () => {
    connecter('CLIENT');
    render(<AssistantJuridique />);
    expect(screen.queryByTestId('assistant-bulle')).not.toBeInTheDocument();
  });

  it('la bulle ouvre le panneau lateral avec son avertissement permanent', () => {
    connecter('EMPLOYE');
    ouvrirPanneau();
    expect(screen.getByRole('dialog', { name: 'Chatbot juridique' })).toBeInTheDocument();
    expect(screen.getByTestId('assistant-avertissement')).toHaveTextContent(
      'Ne saisissez aucune donnée de client',
    );
    // L'avertissement n'a pas de bouton pour le masquer (seule l'aide d'usage se masque).
    expect(screen.getByTestId('assistant-avertissement').querySelector('button')).toBeNull();
  });

  it('une donnee personnelle bloque l envoi : aucun appel, invitation a reformuler', () => {
    connecter('EMPLOYE');
    ouvrirPanneau();
    saisir('Le client joignable au 0612345678 peut-il ceder ses parts ?');

    expect(screen.getByTestId('assistant-blocage')).toHaveTextContent('numéro de téléphone');
    expect(screen.getByTestId('assistant-blocage')).toHaveTextContent('Reformulez');
    expect(screen.getByRole('button', { name: 'Envoyer la question' })).toBeDisabled();
    fireEvent.keyDown(screen.getByLabelText('Votre question juridique'), { key: 'Enter' });
    expect(ask).not.toHaveBeenCalled();
  });

  it('une question juridique normale est envoyee et la reponse cite ses sources', async () => {
    ask.mockResolvedValue({
      reponse: 'La modification des statuts requiert les trois quarts du capital.',
      sources: [{ reference: 'Loi 5-96', article: 'Passage 3', extract: '...' }],
      confidence: 0.8,
      ragMode: 'extraits',
    });
    connecter('SUPERVISEUR');
    ouvrirPanneau();
    saisir("Quelle majorite pour modifier l'article 86 de la loi 5-96 en 2026, capital de 10 000 DH ?");
    expect(screen.queryByTestId('assistant-blocage')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer la question' }));

    await waitFor(() => expect(screen.getByText(/trois quarts du capital/)).toBeInTheDocument());
    expect(ask).toHaveBeenCalledTimes(1);
    expect(screen.getByText('Loi 5-96')).toBeInTheDocument();
  });

  it('le refus du serveur (422) est affiche', async () => {
    ask.mockRejectedValue({
      isAxiosError: true,
      response: { data: { code: 'DONNEE_PERSONNELLE', message: 'Votre question contient une donnée personnelle (RIB).' } },
    });
    connecter('EMPLOYE');
    ouvrirPanneau();
    saisir('Question neutre sur la cession de parts');
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer la question' }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('personnelle (RIB)'));
  });
});
