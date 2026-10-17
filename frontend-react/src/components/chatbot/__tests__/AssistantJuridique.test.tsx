import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { Role } from '../../../types/auth';
import type { ChatbotConversationDetail, ChatbotConversationResume, ChatbotEchange } from '../../../types/chat';

/**
 * Lot L5 E8 (RG-COM-01, 03, 04, 05) : bulle flottante, panneau lateral, avertissement
 * permanent et blocage des donnees personnelles AVANT l'appel au serveur.
 *
 * Lot L5b E2 (RG-COM-12) : conversations conservees par le serveur (reprise, nouvelle,
 * renommage, archivage) ; plus rien n'est garde dans le navigateur.
 */

const etat: { user: { role: Role; userId: string; email: string; workspaceId: string } | null } = {
  user: null,
};

vi.mock('../../../store/authStore', () => ({
  useAuthStore: <T,>(selector: (s: typeof etat) => T) => selector(etat),
}));

const svc = {
  listerConversations: vi.fn(),
  creerConversation: vi.fn(),
  lireConversation: vi.fn(),
  poserQuestion: vi.fn(),
  renommerConversation: vi.fn(),
  archiverConversation: vi.fn(),
  desarchiverConversation: vi.fn(),
};
vi.mock('../../../services/chatbot.service', () => ({
  chatbotService: new Proxy(
    {},
    { get: (_t, nom: string) => (...args: unknown[]) => (svc as Record<string, (...a: unknown[]) => unknown>)[nom](...args) },
  ),
}));

import { AssistantJuridique } from '../AssistantJuridique';
import { purgerAnciennesConversationsLocales } from '../../../lib/chatbotConversationsLocales';
import { useAssistantStore } from '../../../store/assistantStore';

function resume(id: string, titre: string, archivee = false): ChatbotConversationResume {
  return {
    id,
    titre,
    archivee,
    archiveeLe: archivee ? '2026-10-12T10:00:00Z' : null,
    creeeLe: '2026-10-10T09:00:00Z',
    derniereActivite: '2026-10-12T09:00:00Z',
    nombreEchanges: 1,
  };
}

function echange(id: string, question: string, reponse: string): ChatbotEchange {
  return {
    id,
    question,
    reponse,
    sources: [{ reference: 'Loi 5-96', article: 'Article 58', extract: '...' }],
    ragMode: 'extraits',
    confidence: 0.8,
    creeLe: '2026-10-12T09:00:00Z',
  };
}

function detail(r: ChatbotConversationResume, echanges: ChatbotEchange[]): ChatbotConversationDetail {
  return { ...r, echanges };
}

function connecter(role: Role) {
  etat.user = { role, userId: `u-${role}`, email: `${role}@cabinet.ma`, workspaceId: 'ws-1' };
}

// Lot L6b (E11) : la bulle est remplacee par JURI, qui ouvre le panneau par le magasin
// partage (comme la palette et l'adresse /chatbot) ; JURI a ses propres tests.
function ouvrirPanneau() {
  render(<AssistantJuridique />);
  act(() => useAssistantStore.getState().ouvrir());
}

function saisir(texte: string) {
  fireEvent.change(screen.getByLabelText('Votre question juridique'), { target: { value: texte } });
}

describe('AssistantJuridique', () => {
  beforeEach(() => {
    Object.values(svc).forEach((f) => f.mockReset());
    svc.listerConversations.mockResolvedValue([]);
    localStorage.clear();
    useAssistantStore.setState({ ouvert: false });
  });

  it.each<Role>(['EMPLOYE', 'SUPERVISEUR', 'SUPER_ADMIN'])('le panneau s ouvre pour %s, et rien n est affiche tant qu il est ferme', (role) => {
    connecter(role);
    render(<AssistantJuridique />);
    expect(screen.queryByTestId('assistant-panneau')).not.toBeInTheDocument();
    expect(screen.queryByTestId('assistant-bulle')).not.toBeInTheDocument();
    act(() => useAssistantStore.getState().ouvrir());
    expect(screen.getByTestId('assistant-panneau')).toBeInTheDocument();
  });

  it('aucun panneau pour le client, meme demande', () => {
    connecter('CLIENT');
    render(<AssistantJuridique />);
    act(() => useAssistantStore.getState().ouvrir());
    expect(screen.queryByTestId('assistant-panneau')).not.toBeInTheDocument();
    expect(svc.listerConversations).not.toHaveBeenCalled();
  });

  it('Echap ferme le panneau ; Echap pendant un renommage l annule sans fermer', async () => {
    connecter('EMPLOYE');
    const r = resume('c-1', 'Cession de parts');
    svc.listerConversations.mockResolvedValue([r]);
    svc.lireConversation.mockResolvedValue(detail(r, []));
    ouvrirPanneau();
    await waitFor(() => expect(screen.getByLabelText('Renommer la conversation')).toBeInTheDocument());
    fireEvent.click(screen.getByLabelText('Renommer la conversation'));
    const champ = await screen.findByDisplayValue('Cession de parts');
    fireEvent.keyDown(champ, { key: 'Escape' });
    expect(screen.getByTestId('assistant-panneau')).toBeInTheDocument();
    expect(screen.queryByDisplayValue('Cession de parts')).not.toBeInTheDocument();
    fireEvent.keyDown(screen.getByLabelText('Votre question juridique'), { key: 'Escape' });
    expect(screen.queryByTestId('assistant-panneau')).not.toBeInTheDocument();
    expect(useAssistantStore.getState().ouvert).toBe(false);
  });

  it('le panneau lateral porte son avertissement permanent', async () => {
    connecter('EMPLOYE');
    ouvrirPanneau();
    expect(screen.getByRole('dialog', { name: 'Chatbot juridique' })).toBeInTheDocument();
    expect(screen.getByTestId('assistant-avertissement')).toHaveTextContent(
      'Ne saisissez aucune donnée de client',
    );
    // L'avertissement n'a pas de bouton pour le masquer (seule l'aide d'usage se masque).
    expect(screen.getByTestId('assistant-avertissement').querySelector('button')).toBeNull();
    await waitFor(() => expect(screen.getByText('Aucune question pour l’instant.')).toBeInTheDocument());
  });

  it('une donnee personnelle bloque l envoi : aucun appel, invitation a reformuler', () => {
    connecter('EMPLOYE');
    ouvrirPanneau();
    saisir('Le client joignable au 0612345678 peut-il ceder ses parts ?');

    expect(screen.getByTestId('assistant-blocage')).toHaveTextContent('numéro de téléphone');
    expect(screen.getByTestId('assistant-blocage')).toHaveTextContent('Reformulez');
    expect(screen.getByRole('button', { name: 'Envoyer la question' })).toBeDisabled();
    fireEvent.keyDown(screen.getByLabelText('Votre question juridique'), { key: 'Enter' });
    expect(svc.creerConversation).not.toHaveBeenCalled();
    expect(svc.poserQuestion).not.toHaveBeenCalled();
  });

  it('la premiere question ouvre une conversation sur le serveur ; la reponse cite ses sources', async () => {
    const r = resume('c-1', 'Quelle majorite pour modifier les statuts ?');
    svc.creerConversation.mockResolvedValue({
      conversation: r,
      echange: echange('e-1', 'Quelle majorite pour modifier les statuts ?', 'Les trois quarts du capital.'),
    });
    svc.poserQuestion.mockResolvedValue({
      conversation: { ...r, nombreEchanges: 2 },
      echange: echange('e-2', 'Et en SA ?', 'Les deux tiers des voix.'),
    });
    connecter('SUPERVISEUR');
    ouvrirPanneau();
    await waitFor(() => expect(svc.listerConversations).toHaveBeenCalledWith(false));
    saisir('Quelle majorite pour modifier les statuts ?');
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer la question' }));

    await waitFor(() => expect(screen.getByText(/trois quarts du capital/)).toBeInTheDocument());
    expect(svc.creerConversation).toHaveBeenCalledWith('Quelle majorite pour modifier les statuts ?');
    expect(screen.getByText('Loi 5-96')).toBeInTheDocument();
    expect(screen.getByTestId('assistant-entete-conversation')).toHaveTextContent(
      'Quelle majorite pour modifier les statuts ?',
    );

    // La question suivante va dans la meme conversation.
    saisir('Et en SA ?');
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer la question' }));
    await waitFor(() => expect(screen.getByText(/deux tiers des voix/)).toBeInTheDocument());
    expect(svc.poserQuestion).toHaveBeenCalledWith('c-1', 'Et en SA ?');
    expect(svc.creerConversation).toHaveBeenCalledTimes(1);
  });

  it('rien n est garde dans le navigateur et l ancienne conservation locale est purgee', async () => {
    localStorage.setItem('jurika:chatbot:conversations:u-EMPLOYE', '[{"id":"x","entries":[]}]');
    localStorage.setItem('jurika.aide.autre.masquee', '1');
    svc.creerConversation.mockResolvedValue({
      conversation: resume('c-1', 'Question'),
      echange: echange('e-1', 'Question', 'Réponse.'),
    });
    connecter('EMPLOYE');
    ouvrirPanneau();
    saisir('Question');
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer la question' }));
    await waitFor(() => expect(screen.getByText('Réponse.')).toBeInTheDocument());

    expect(localStorage.getItem('jurika:chatbot:conversations:u-EMPLOYE')).toBeNull();
    expect(localStorage.getItem('jurika.aide.autre.masquee')).toBe('1');
    for (let i = 0; i < localStorage.length; i += 1) {
      expect(localStorage.getItem(localStorage.key(i) ?? '')).not.toContain('Question');
    }
  });

  it('purge : sans effet quand le stockage est indisponible', () => {
    const espion = vi.spyOn(Storage.prototype, 'key').mockImplementation(() => {
      throw new Error('indisponible');
    });
    expect(() => purgerAnciennesConversationsLocales()).not.toThrow();
    espion.mockRestore();
  });

  it('le refus du serveur (422) est affiche', async () => {
    svc.creerConversation.mockRejectedValue({
      isAxiosError: true,
      response: { data: { code: 'DONNEE_PERSONNELLE', message: 'Votre question contient une donnée personnelle (RIB).' } },
    });
    connecter('EMPLOYE');
    ouvrirPanneau();
    saisir('Question neutre sur la cession de parts');
    fireEvent.click(screen.getByRole('button', { name: 'Envoyer la question' }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('personnelle (RIB)'));
  });

  it('a l ouverture, la conversation active la plus recente est reprise', async () => {
    const recente = resume('c-2', 'Cession de parts');
    svc.listerConversations.mockResolvedValue([recente, resume('c-1', 'Capital minimum')]);
    svc.lireConversation.mockResolvedValue(
      detail(recente, [echange('e-1', 'Comment ceder des parts ?', 'Agrément des associés requis.')]),
    );
    connecter('EMPLOYE');
    ouvrirPanneau();

    await waitFor(() => expect(screen.getByText('Agrément des associés requis.')).toBeInTheDocument());
    expect(svc.lireConversation).toHaveBeenCalledWith('c-2');
    expect(screen.getByText('Comment ceder des parts ?')).toBeInTheDocument();
    expect(screen.getByText('Loi 5-96')).toBeInTheDocument();

    // Nouvelle conversation : l'echange affiche disparait, la question suivante en ouvre une autre.
    fireEvent.click(screen.getByRole('button', { name: 'Nouvelle conversation' }));
    expect(screen.queryByText('Agrément des associés requis.')).not.toBeInTheDocument();
    expect(screen.getByTestId('assistant-entete-conversation')).toHaveTextContent('Nouvelle conversation');
  });

  it('mes conversations : liste, reprise et archivage', async () => {
    const c1 = resume('c-1', 'Capital minimum');
    const c2 = resume('c-2', 'Cession de parts');
    svc.listerConversations.mockImplementation(async (archivees: boolean) =>
      archivees ? [resume('c-9', 'Ancienne question', true)] : [c2, c1],
    );
    svc.lireConversation.mockImplementation(async (id: string) =>
      detail(id === 'c-1' ? c1 : c2, [echange(`e-${id}`, `Question ${id}`, `Réponse ${id}`)]),
    );
    svc.archiverConversation.mockResolvedValue({ ...c1, archivee: true });
    connecter('EMPLOYE');
    ouvrirPanneau();
    await waitFor(() => expect(screen.getByText('Réponse c-2')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Mes conversations' }));
    await waitFor(() => expect(screen.getAllByTestId('assistant-conversation')).toHaveLength(2));
    const lignes = screen.getAllByTestId('assistant-conversation');
    expect(lignes[0]).toHaveTextContent('Cession de parts');
    expect(lignes[1]).toHaveTextContent('Capital minimum');

    fireEvent.click(screen.getByRole('button', { name: 'Archiver « Capital minimum »' }));
    await waitFor(() => expect(screen.getAllByTestId('assistant-conversation')).toHaveLength(1));
    expect(svc.archiverConversation).toHaveBeenCalledWith('c-1');

    fireEvent.click(screen.getByRole('tab', { name: 'Archivées' }));
    await waitFor(() => expect(svc.listerConversations).toHaveBeenCalledWith(true));
    await waitFor(() => expect(screen.getByText('Ancienne question')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('tab', { name: 'En cours' }));
    await waitFor(() => expect(screen.getByText('Cession de parts')).toBeInTheDocument());
    fireEvent.click(screen.getByText('Cession de parts'));
    await waitFor(() => expect(screen.getByText('Réponse c-2')).toBeInTheDocument());
    expect(screen.queryByTestId('assistant-liste')).not.toBeInTheDocument();
  });

  it('une conversation archivee se consulte, sans saisie ; on la desarchive pour la poursuivre', async () => {
    const archivee = resume('c-9', 'Ancienne question', true);
    svc.listerConversations.mockImplementation(async (a: boolean) => (a ? [archivee] : []));
    svc.lireConversation.mockResolvedValue(detail(archivee, [echange('e-9', 'Ancienne ?', 'Ancienne réponse.')]));
    svc.desarchiverConversation.mockResolvedValue({ ...archivee, archivee: false, archiveeLe: null });
    connecter('EMPLOYE');
    ouvrirPanneau();
    fireEvent.click(screen.getByRole('button', { name: 'Mes conversations' }));
    fireEvent.click(screen.getByRole('tab', { name: 'Archivées' }));
    await waitFor(() => expect(screen.getByText('Ancienne question')).toBeInTheDocument());
    fireEvent.click(screen.getByText('Ancienne question'));

    await waitFor(() => expect(screen.getByText('Ancienne réponse.')).toBeInTheDocument());
    expect(screen.getByTestId('assistant-archivee')).toHaveTextContent('archivée');
    expect(screen.queryByLabelText('Votre question juridique')).not.toBeInTheDocument();

    fireEvent.click(within(screen.getByTestId('assistant-archivee')).getByRole('button', { name: 'Désarchiver' }));
    await waitFor(() => expect(screen.getByLabelText('Votre question juridique')).toBeInTheDocument());
    expect(svc.desarchiverConversation).toHaveBeenCalledWith('c-9');
  });

  it('renommage : le titre est enregistre ; un titre avec une donnee de client est refuse sans appel', async () => {
    const r = resume('c-1', 'Capital minimum');
    svc.listerConversations.mockResolvedValue([r]);
    svc.lireConversation.mockResolvedValue(detail(r, [echange('e-1', 'Capital ?', 'Libre.')]));
    svc.renommerConversation.mockResolvedValue({ ...r, titre: 'Capital des SARL' });
    connecter('EMPLOYE');
    ouvrirPanneau();
    await waitFor(() => expect(screen.getByText('Libre.')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Renommer la conversation' }));
    const champ = screen.getByLabelText('Titre de la conversation');
    fireEvent.change(champ, { target: { value: 'Dossier client.sarl@gmail.com' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer le titre' }));
    const refus = await screen.findByRole('alert');
    expect(refus).toHaveTextContent('adresse électronique');
    expect(svc.renommerConversation).not.toHaveBeenCalled();

    fireEvent.change(champ, { target: { value: '  Capital   des SARL ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer le titre' }));
    await waitFor(() =>
      expect(screen.getByTestId('assistant-entete-conversation')).toHaveTextContent('Capital des SARL'),
    );
    expect(svc.renommerConversation).toHaveBeenCalledWith('c-1', 'Capital des SARL');
  });
});
