import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { InviteEmployeeDrawer } from '../InviteEmployeeDrawer';

// Famille "team" — prouve qu'un submit invalide (champs requis vides / email
// malformé) affiche l'erreur IN-APP (pas de bulle native) et N'APPELLE PAS l'API.

const inviteEmployee = vi.fn();

vi.mock('../../../services/auth.service', () => ({
  authService: {
    inviteEmployee: (...args: unknown[]) => inviteEmployee(...args),
  },
}));

function renderDrawer() {
  return render(
    <InviteEmployeeDrawer open onClose={() => {}} onInvited={() => {}} />,
  );
}

describe('InviteEmployeeDrawer — validation in-app', () => {
  beforeEach(() => {
    inviteEmployee.mockReset();
  });

  it('le formulaire porte noValidate', () => {
    const { container } = renderDrawer();
    expect(container.querySelector('form')).toHaveAttribute('novalidate');
  });

  it('submit avec champs vides : erreur in-app + pas d’appel API', async () => {
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: /Inviter$/i }));

    expect(await screen.findByText(/Le prenom est requis/i)).toBeInTheDocument();
    expect(inviteEmployee).not.toHaveBeenCalled();
  });

  it('submit avec email malformé : erreur in-app + pas d’appel API', async () => {
    const user = userEvent.setup();
    renderDrawer();

    await user.type(screen.getByLabelText('Prenom'), 'Amine');
    await user.type(screen.getByLabelText('Nom'), 'Benali');
    await user.type(screen.getByLabelText('Email professionnel'), 'pas-un-email');
    await user.click(screen.getByRole('button', { name: /Inviter$/i }));

    expect(await screen.findByText(/Adresse e-mail invalide/i)).toBeInTheDocument();
    expect(inviteEmployee).not.toHaveBeenCalled();
  });

  it('submit valide : appelle l’API une fois', async () => {
    inviteEmployee.mockResolvedValue({
      emailDelivered: true,
      tempPassword: null,
      message: 'Invitation envoyee',
    });
    const user = userEvent.setup();
    renderDrawer();

    await user.type(screen.getByLabelText('Prenom'), 'Amine');
    await user.type(screen.getByLabelText('Nom'), 'Benali');
    await user.type(screen.getByLabelText('Email professionnel'), 'amine.benali@cabinet.ma');
    await user.click(screen.getByRole('button', { name: /Inviter$/i }));

    await waitFor(() => expect(inviteEmployee).toHaveBeenCalledTimes(1));
  });
});
