import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ToastProvider } from '../../../components/ui/Toast';

/** Lot L5 E8 : ecran du super-admin des sources communes du chatbot, et ancienne adresse /chatbot. */

const listSources = vi.fn();
const deleteSource = vi.fn();
vi.mock('../../../services/chatbot.service', () => ({
  chatbotService: {
    listSources: () => listSources(),
    deleteSource: (id: string) => deleteSource(id),
    addSource: vi.fn(),
  },
}));

const etat: { user: { role: string } | null } = { user: null };
vi.mock('../../../store/authStore', () => ({
  useAuthStore: <T,>(selector: (s: typeof etat) => T) => selector(etat),
}));

import { ChatbotSourcesPage } from '../ChatbotSourcesPage';
import { RedirectionChatbot } from '../../../components/chatbot/RedirectionChatbot';
import { useAssistantStore } from '../../../store/assistantStore';

describe('ChatbotSourcesPage', () => {
  beforeEach(() => {
    listSources.mockReset();
    deleteSource.mockReset();
    localStorage.clear();
  });

  function afficher() {
    render(
      <MemoryRouter>
        <ToastProvider>
          <ChatbotSourcesPage />
        </ToastProvider>
      </MemoryRouter>,
    );
  }

  it('liste les sources communes servies par le serveur, avec son aide', async () => {
    listSources.mockResolvedValue([
      { doc_id: 'd1', source: 'Loi 5-96.pdf', chunks: 12, uploaded_by: null, created_at: '2026-10-01T10:00:00Z' },
    ]);
    afficher();
    expect(await screen.findByText('Loi 5-96.pdf')).toBeInTheDocument();
    expect(screen.getByText('12 passages')).toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'Déposer et retirer les sources du chatbot' })).toBeInTheDocument();
  });

  it('sans source, dit ce que repondra le chatbot et ce qu il faut faire', async () => {
    listSources.mockResolvedValue([]);
    afficher();
    expect(await screen.findByText(/Aucune source déposée/)).toBeInTheDocument();
  });

  it('retire une source apres confirmation', async () => {
    listSources.mockResolvedValue([
      { doc_id: 'd1', source: 'Circulaire.txt', chunks: 1, uploaded_by: null, created_at: '2026-10-01T10:00:00Z' },
    ]);
    deleteSource.mockResolvedValue(undefined);
    afficher();
    fireEvent.click(await screen.findByRole('button', { name: 'Retirer Circulaire.txt' }));
    fireEvent.click(screen.getByRole('button', { name: 'Retirer' }));
    await waitFor(() => expect(deleteSource).toHaveBeenCalledWith('d1'));
    await waitFor(() => expect(screen.queryByText('Circulaire.txt')).not.toBeInTheDocument());
  });
});

describe('RedirectionChatbot (ancienne page /chatbot)', () => {
  beforeEach(() => useAssistantStore.setState({ ouvert: false }));

  function naviguer() {
    render(
      <MemoryRouter initialEntries={['/chatbot']}>
        <Routes>
          <Route path="/chatbot" element={<RedirectionChatbot />} />
          <Route path="/dashboard" element={<p>tableau de bord</p>} />
          <Route path="/admin/chatbot-sources" element={<p>sources communes</p>} />
        </Routes>
      </MemoryRouter>,
    );
  }

  it('le cabinet arrive au tableau de bord, panneau ouvert', () => {
    etat.user = { role: 'EMPLOYE' };
    naviguer();
    expect(screen.getByText('tableau de bord')).toBeInTheDocument();
    expect(useAssistantStore.getState().ouvert).toBe(true);
  });

  it('le super-admin arrive aux sources communes', () => {
    etat.user = { role: 'SUPER_ADMIN' };
    naviguer();
    expect(screen.getByText('sources communes')).toBeInTheDocument();
  });
});
