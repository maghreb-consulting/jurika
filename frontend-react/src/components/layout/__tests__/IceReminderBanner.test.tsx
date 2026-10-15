import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/** Lot L6b : rappel de l'ICE manquant, francais accentue (P11), superviseur seulement. */
const getProfile = vi.fn();
vi.mock('../../../services/workspace.service', () => ({
  workspaceService: { getProfile: (...a: unknown[]) => getProfile(...a) },
}));
let role = 'SUPERVISEUR';
vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => ({ userId: 'u-1', workspaceId: 'w-1', role }),
}));

const { IceReminderBanner } = await import('../IceReminderBanner');

describe('IceReminderBanner', () => {
  beforeEach(() => {
    sessionStorage.clear();
    getProfile.mockReset();
    getProfile.mockResolvedValue({ iceMissing: true });
    role = 'SUPERVISEUR';
  });

  it('dit au superviseur, en francais accentue, que l’ICE manque et ou le completer', async () => {
    render(<MemoryRouter><IceReminderBanner /></MemoryRouter>);
    const bandeau = await screen.findByTestId('ice-reminder-banner');
    expect(bandeau).toHaveTextContent('Votre ICE n’est pas encore renseigné.');
    expect(screen.getByRole('link', { name: 'Compléter maintenant' })).toHaveAttribute('href', '/settings/cabinet');
    expect(screen.getByRole('button', { name: 'Fermer le message' })).toBeInTheDocument();
  });

  it('ne sollicite pas un employe', async () => {
    role = 'EMPLOYE';
    render(<MemoryRouter><IceReminderBanner /></MemoryRouter>);
    await waitFor(() => expect(getProfile).not.toHaveBeenCalled());
    expect(screen.queryByTestId('ice-reminder-banner')).toBeNull();
  });
});
