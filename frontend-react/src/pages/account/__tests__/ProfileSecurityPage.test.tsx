import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

/**
 * Lot L6 (E10) : le bouton « Changer mon mot de passe » de l'ecran Securite mene au
 * changement VOLONTAIRE de « Mon compte », jamais a la page de changement OBLIGATOIRE
 * (/account/change-password, dette L5).
 */

vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => ({ userId: 'u1', workspaceId: 'w1', email: 'k@jurika.ma', role: 'EMPLOYE' }),
}));
vi.mock('../../../services/auth.service', () => ({ authService: { generateRecoveryCodes: vi.fn() } }));

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
