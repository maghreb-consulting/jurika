import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

/**
 * Lot L6 (E10) : le bouton « Changer mon mot de passe » de l'ecran Securite mene au
 * changement VOLONTAIRE de « Mon compte », jamais a la page de changement OBLIGATOIRE
 * (/account/change-password, dette L5).
 */

// L6b (E8) : l'ecran porte la structure <Page>, qui lit le role dans le magasin (fil d'Ariane).
const UTILISATEUR = { userId: 'u1', workspaceId: 'w1', email: 'k@jurika.ma', role: 'EMPLOYE' };
vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => UTILISATEUR,
  useAuthStore: (selecteur: (etat: { user: typeof UTILISATEUR }) => unknown) => selecteur({ user: UTILISATEUR }),
}));
const getSetup2faOptions = vi.fn();
vi.mock('../../../services/auth.service', () => ({
  authService: { generateRecoveryCodes: vi.fn(), getSetup2faOptions: (...a: unknown[]) => getSetup2faOptions(...a) },
}));

import { ProfileSecurityPage } from '../ProfileSecurityPage';

function rendre(chemin: string) {
  render(
    <MemoryRouter initialEntries={[chemin]}>
      <Routes>
        <Route path="/settings/security" element={<ProfileSecurityPage />} />
        <Route path="/account/security" element={<ProfileSecurityPage />} />
        <Route path="/settings/profile" element={<p>mon compte (paramètres)</p>} />
        <Route path="/profile" element={<p>mon compte</p>} />
        <Route path="/account/change-password" element={<p>changement obligatoire</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('<ProfileSecurityPage>', () => {
  beforeEach(() => {
    getSetup2faOptions.mockReset();
    getSetup2faOptions.mockResolvedValue({
      totpAvailable: true, smsAvailable: false, smsSimule: false, maskedPhone: null, methodeActive: 'TOTP',
    });
  });

  // Recette R2 (2026-10-17) : la methode active et la disponibilite du SMS sont dites a l'ecran.
  it('affiche la méthode active (SMS) et son numéro masqué', async () => {
    getSetup2faOptions.mockResolvedValue({
      totpAvailable: true, smsAvailable: true, smsSimule: true, maskedPhone: '********3344', methodeActive: 'SMS',
    });
    rendre('/settings/security');
    expect(await screen.findByTestId('methode-2fa-active')).toHaveTextContent(/SMS.*3344/);
    expect(screen.getByTestId('methode-2fa-active')).toHaveTextContent(/simulé/i);
  });

  it('sans fournisseur de SMS : le dit, sans proposer le SMS', async () => {
    rendre('/settings/security');
    expect(await screen.findByTestId('methode-2fa-active')).toHaveTextContent(/application d’authentification/i);
    expect(screen.getByText(/SMS n’est pas disponible/i)).toBeInTheDocument();
  });

  it('depuis les paramètres, mène au bloc mot de passe de « Mon compte »', async () => {
    rendre('/settings/security');
    await userEvent.click(screen.getByRole('button', { name: 'Changer mon mot de passe' }));
    expect(screen.getByText('mon compte (paramètres)')).toBeInTheDocument();
    expect(screen.queryByText('changement obligatoire')).not.toBeInTheDocument();
  });

  it('hors des paramètres, mène à « Mon compte »', async () => {
    rendre('/account/security');
    await userEvent.click(screen.getByRole('button', { name: 'Changer mon mot de passe' }));
    expect(screen.getByText('mon compte')).toBeInTheDocument();
  });

  it('porte un titre clair et son aide', () => {
    rendre('/settings/security');
    expect(screen.getByRole('heading', { name: 'Sécurité du compte' })).toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'Protéger votre compte' })).toBeInTheDocument();
  });
});
