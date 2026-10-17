import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import type { Role } from '../../../types/auth';

/**
 * Lot L6b (E11) : palette de commandes (Ctrl+K) et raccourcis globaux.
 * Recherche par les appels existants (tickets : GET /tickets?q ; dossiers : GET
 * /dataroom/dossiers), anti-rebond, limites du role, clavier, etats vide et erreur.
 */
const tickets = vi.hoisted(() => ({ list: vi.fn() }));
const dataroom = vi.hoisted(() => ({ listDossiers: vi.fn() }));
const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn(), delete: vi.fn() }));
vi.mock('../../../services/ticket.service', () => ({ ticketService: tickets }));
vi.mock('../../../services/dataroom.service', () => ({ dataroomService: dataroom }));
vi.mock('../../../lib/api', () => ({ api, extractError: (e: { message?: string }) => ({ message: e?.message ?? 'Erreur', code: '' }) }));
vi.mock('../../../pages/tickets/NewTicketDrawer', () => ({
  NewTicketDrawer: ({ open }: { open: boolean }) => (open ? <div role="dialog" aria-modal="true" aria-label="Nouveau ticket" /> : null),
}));

import { CommandesGlobales } from '../CommandesGlobales';
import { useAuthStore } from '../../../store/authStore';
import { useCommandesStore } from '../../../store/commandesStore';
import { useThemeStore } from '../../../store/themeStore';
import { usePreferencesStore } from '../../../store/preferencesStore';

function Adresse() {
  const l = useLocation();
  return <p data-testid="adresse">{`${l.pathname}${l.search}`}</p>;
}

function connecter(role: Role) {
  useAuthStore.setState({
    user: { role, email: 'x@cabinet.ma', userId: `u-${role}`, workspaceId: 'ws-1' } as never,
    isAuthenticated: true,
  } as never);
}

function rendre() {
  return render(
    <MemoryRouter initialEntries={['/dashboard']}>
      <Routes>
        <Route
          path="*"
          element={
            <>
              <input aria-label="Champ de la page" />
              <Adresse />
              <CommandesGlobales />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}

function ouvrirParClavier() {
  fireEvent.keyDown(window, { key: 'k', ctrlKey: true });
}

const champ = () => screen.getByTestId('palette-champ');

function taper(texte: string) {
  fireEvent.change(champ(), { target: { value: texte } });
}

const DOSSIERS = [
  { id: 'd-1', raisonSociale: 'ATLAS NEGOCE', reference: '2026-12 — ATLAS NEGOCE', formeJuridique: 'SARL', ice: '001234567000089', ville: 'Casablanca', statut: 'ACTIF' },
  { id: 'd-2', raisonSociale: 'Société Béni Mellal Agri', reference: '2026-13 — Béni Mellal Agri', formeJuridique: 'SARL AU', ice: null, ville: 'Béni Mellal', statut: 'ACTIF' },
];

beforeEach(() => {
  tickets.list.mockReset();
  dataroom.listDossiers.mockReset();
  api.put.mockReset();
  api.put.mockResolvedValue({ data: {} });
  dataroom.listDossiers.mockResolvedValue(DOSSIERS);
  tickets.list.mockResolvedValue({ items: [], total: 0, limit: 6, offset: 0 });
  useCommandesStore.setState({ ouverte: null });
  usePreferencesStore.getState().vider();
});

afterEach(() => {
  useAuthStore.setState({ user: null, isAuthenticated: false } as never);
});

describe('Palette de commandes', () => {
  it('Ctrl+K l ouvre, le focus est dans le champ ; Echap la ferme', () => {
    connecter('EMPLOYE');
    rendre();
    ouvrirParClavier();
    expect(screen.getByRole('dialog', { name: 'Palette de commandes' })).toBeInTheDocument();
    expect(champ()).toHaveFocus();
    expect(champ()).toHaveAttribute('role', 'combobox');
    fireEvent.keyDown(champ(), { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: 'Palette de commandes' })).not.toBeInTheDocument();
  });

  it('jamais pendant la saisie : Ctrl+K, ? et n dans un champ de la page ne font rien', () => {
    connecter('EMPLOYE');
    rendre();
    const c = screen.getByLabelText('Champ de la page');
    c.focus();
    fireEvent.keyDown(c, { key: 'k', ctrlKey: true });
    fireEvent.keyDown(c, { key: '?' });
    fireEvent.keyDown(c, { key: 'n' });
    expect(useCommandesStore.getState().ouverte).toBeNull();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('tickets cherches par le serveur apres l anti-rebond, un seul appel pour une frappe rapide', async () => {
    connecter('EMPLOYE');
    tickets.list.mockResolvedValue({
      items: [{ id: 't-9', reference: 'TCK-2026-0009', titre: 'Cession de parts ATLAS', statut: 'EN_COURS' }],
      total: 1,
      limit: 6,
      offset: 0,
    });
    rendre();
    ouvrirParClavier();
    taper('at');
    taper('atl');
    taper('atla');
    await waitFor(() => expect(screen.getByText('TCK-2026-0009 — Cession de parts ATLAS')).toBeInTheDocument());
    expect(tickets.list).toHaveBeenCalledTimes(1);
    expect(tickets.list).toHaveBeenCalledWith({ q: 'atla', limit: 6 });
    // Dossier trouve par la raison sociale (groupe Societes), par les dossiers du role.
    const liste = screen.getByRole('listbox');
    expect(within(liste).getByRole('group', { name: 'Sociétés' })).toHaveTextContent('ATLAS NEGOCE');
    expect(dataroom.listDossiers).toHaveBeenCalledTimes(1);
  });

  it('fleches et Entree : ouvre le ticket choisi', async () => {
    connecter('SUPERVISEUR');
    tickets.list.mockResolvedValue({
      items: [{ id: 't-9', reference: 'TCK-2026-0009', titre: 'Cession', statut: 'EN_COURS' }],
      total: 1,
      limit: 6,
      offset: 0,
    });
    dataroom.listDossiers.mockResolvedValue([{ ...DOSSIERS[0], id: 'd-7', raisonSociale: 'CESSION IMMO', reference: null }]);
    rendre();
    ouvrirParClavier();
    taper('cession');
    await waitFor(() => expect(screen.getByText('TCK-2026-0009 — Cession')).toBeInTheDocument());
    await waitFor(() => expect(screen.getByTestId('palette-option-societe-d-7')).toBeInTheDocument());
    const premiere = screen.getAllByRole('option')[0];
    expect(premiere).toHaveAttribute('aria-selected', 'true');
    expect(champ()).toHaveAttribute('aria-activedescendant', premiere.id);
    fireEvent.keyDown(champ(), { key: 'ArrowDown' });
    expect(screen.getAllByRole('option')[1]).toHaveAttribute('aria-selected', 'true');
    fireEvent.keyDown(champ(), { key: 'ArrowUp' });
    fireEvent.keyDown(champ(), { key: 'Enter' });
    expect(screen.getByTestId('adresse')).toHaveTextContent('/tickets/t-9');
    expect(screen.queryByRole('dialog', { name: 'Palette de commandes' })).not.toBeInTheDocument();
  });

  it('dossier trouve par sa reference : ouvre sa Data Room', async () => {
    connecter('EMPLOYE');
    rendre();
    ouvrirParClavier();
    taper('2026-13');
    const option = await screen.findByTestId('palette-option-dossier-d-2');
    expect(option).toHaveTextContent('2026-13 — Béni Mellal Agri');
    fireEvent.click(option);
    expect(screen.getByTestId('adresse')).toHaveTextContent('/data-rooms?dossier=d-2');
  });

  it('client : ses dossiers seulement (liste du serveur), aucune recherche de tickets, pas de nouveau ticket', async () => {
    connecter('CLIENT');
    rendre();
    ouvrirParClavier();
    expect(screen.queryByTestId('palette-option-nouveau-ticket')).not.toBeInTheDocument();
    expect(screen.queryByText('Poser une question au chatbot juridique')).not.toBeInTheDocument();
    taper('beni');
    await waitFor(() => expect(screen.getByTestId('palette-option-societe-d-2')).toBeInTheDocument());
    expect(tickets.list).not.toHaveBeenCalled();
    expect(dataroom.listDossiers).toHaveBeenCalledTimes(1);
  });

  it('super-admin : ni tickets ni dossiers (aucun appel), ses ecrans du menu', async () => {
    connecter('SUPER_ADMIN');
    rendre();
    ouvrirParClavier();
    taper('cabinets');
    await waitFor(() => expect(screen.getByTestId('palette-option-ecran-/admin/workspaces')).toBeInTheDocument());
    expect(tickets.list).not.toHaveBeenCalled();
    expect(dataroom.listDossiers).not.toHaveBeenCalled();
    expect(screen.queryByText('Mes tickets')).not.toBeInTheDocument();
  });

  it('erreur explicite par source ; les autres resultats restent', async () => {
    connecter('EMPLOYE');
    tickets.list.mockRejectedValue({ message: 'Service indisponible.' });
    rendre();
    ouvrirParClavier();
    taper('atlas');
    const alerte = await screen.findByRole('alert');
    expect(alerte).toHaveTextContent('La recherche des tickets n’a pas abouti : Service indisponible.');
    expect(screen.getByTestId('palette-option-societe-d-1')).toBeInTheDocument();
  });

  it('etat vide explicite', async () => {
    connecter('EMPLOYE');
    rendre();
    ouvrirParClavier();
    taper('zzzz introuvable');
    const vide = await screen.findByTestId('palette-vide');
    expect(vide).toHaveTextContent('Aucun résultat pour « zzzz introuvable »');
    expect(screen.getByRole('status')).toHaveTextContent('Aucun résultat.');
  });

  it('actions : changer de theme (enregistre sur le compte), raccourcis, nouveau ticket', async () => {
    connecter('EMPLOYE');
    useThemeStore.getState().appliquerChoix('CLAIR');
    usePreferencesStore.setState({ utilisateur: 'u-EMPLOYE', statut: 'pret', preferences: { theme: 'CLAIR', juriMasque: false } });
    rendre();
    ouvrirParClavier();
    taper('sombre');
    fireEvent.click(await screen.findByTestId('palette-option-theme-SOMBRE'));
    expect(useThemeStore.getState().choix).toBe('SOMBRE');
    expect(api.put).toHaveBeenCalledWith('/users/me/preferences', { theme: 'SOMBRE' });

    ouvrirParClavier();
    taper('raccourcis');
    fireEvent.click(await screen.findByTestId('palette-option-raccourcis'));
    expect(screen.getByRole('dialog', { name: 'Raccourcis clavier' })).toBeInTheDocument();
    act(() => useCommandesStore.getState().fermer());

    ouvrirParClavier();
    fireEvent.click(screen.getByTestId('palette-option-nouveau-ticket'));
    expect(await screen.findByRole('dialog', { name: 'Nouveau ticket' })).toBeInTheDocument();
  });
});

describe('Raccourcis globaux', () => {
  it('? ouvre la fenetre des raccourcis du role ; g puis t mene aux tickets ; n ouvre le nouveau ticket', async () => {
    connecter('EMPLOYE');
    rendre();
    fireEvent.keyDown(window, { key: '?' });
    const fenetre = screen.getByRole('dialog', { name: 'Raccourcis clavier' });
    expect(within(fenetre).getByTestId('raccourcis-liste')).toHaveTextContent('Créer un nouveau ticket.');
    expect(within(fenetre).getByTestId('raccourcis-liste')).toHaveTextContent('Échap');
    // Fenetre ouverte : les autres raccourcis sont suspendus.
    fireEvent.keyDown(window, { key: 'n' });
    expect(useCommandesStore.getState().ouverte).toBe('raccourcis');
    act(() => useCommandesStore.getState().fermer());

    fireEvent.keyDown(window, { key: 'g' });
    fireEvent.keyDown(window, { key: 't' });
    expect(screen.getByTestId('adresse')).toHaveTextContent('/tickets');

    fireEvent.keyDown(window, { key: 'n' });
    expect(await screen.findByRole('dialog', { name: 'Nouveau ticket' })).toBeInTheDocument();
  });

  it('client : la fenetre ne liste pas les raccourcis du cabinet', () => {
    connecter('CLIENT');
    rendre();
    fireEvent.keyDown(window, { key: '?' });
    const liste = screen.getByTestId('raccourcis-liste');
    expect(liste).not.toHaveTextContent('Créer un nouveau ticket.');
    expect(liste).toHaveTextContent('Aller aux dossiers (Data Rooms).');
  });
});
